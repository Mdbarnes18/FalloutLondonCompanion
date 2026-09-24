package com.falloutlondon.companion

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface PipboyUpdate {
    data class Data(val records: List<PipboyRecord>) : PipboyUpdate
    data class LocalMap(val map: PipboyLocalMapUpdate) : PipboyUpdate
    data class CommandResponse(val json: String) : PipboyUpdate
}

data class PipboyLocalMapUpdate(
    val width: Int,
    val height: Int,
    val northWest: Pair<Float, Float>,
    val northEast: Pair<Float, Float>,
    val southWest: Pair<Float, Float>,
    val pixels: ByteArray
)

sealed interface PipboyRecord {
    data class Value(val nodeId: Long, val value: PipboyValue) : PipboyRecord
    data class Array(val nodeId: Long, val children: List<Long>) : PipboyRecord
    data class Object(val nodeId: Long, val added: List<Pair<String, Long>>, val removed: List<Long>) : PipboyRecord
}

sealed interface PipboyValue {
    data object Null : PipboyValue
    data class Bool(val value: Boolean) : PipboyValue
    data class Int8(val value: Byte) : PipboyValue
    data class UInt8(val value: Int) : PipboyValue
    data class Int32(val value: Int) : PipboyValue
    data class UInt32(val value: Long) : PipboyValue
    data class Float32(val value: Float) : PipboyValue
    data class StringValue(val value: String) : PipboyValue
}

object PipboyPacketDecoder {
    fun decode(type: Int, payload: ByteArray): PipboyUpdate? = when (type) {
        3 -> PipboyUpdate.Data(decodeDataUpdate(payload))
        4 -> decodeLocalMap(payload)?.let(PipboyUpdate::LocalMap)
        6 -> PipboyUpdate.CommandResponse(payload.toString(Charsets.UTF_8))
        else -> null
    }

    private fun decodeDataUpdate(bytes: ByteArray): List<PipboyRecord> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val records = mutableListOf<PipboyRecord>()
        fun u8() = input.readUnsignedByte()
        fun u16() = u8() or (u8() shl 8)
        fun u32() = u8().toLong() or (u8().toLong() shl 8) or (u8().toLong() shl 16) or (u8().toLong() shl 24)
        fun i32() = u32().toInt()
        fun f32() = Float.fromBits(i32())
        fun cstring(): String {
            val out = ByteArrayOutputStream()
            while (true) { val b = u8(); if (b == 0) break; out.write(b) }
            return out.toByteArray().toString(Charsets.UTF_8)
        }

        while (input.available() > 0) {
            try {
                when (val type = u8()) {
                    0 -> records += PipboyRecord.Value(u32(), PipboyValue.Bool(u8() != 0))
                    1 -> records += PipboyRecord.Value(u32(), PipboyValue.Int8(u8().toByte()))
                    2 -> records += PipboyRecord.Value(u32(), PipboyValue.UInt8(u8()))
                    3 -> records += PipboyRecord.Value(u32(), PipboyValue.Int32(i32()))
                    4 -> records += PipboyRecord.Value(u32(), PipboyValue.UInt32(u32()))
                    5 -> records += PipboyRecord.Value(u32(), PipboyValue.Float32(f32()))
                    6 -> records += PipboyRecord.Value(u32(), PipboyValue.StringValue(cstring()))
                    7 -> {
                        val id = u32()
                        records += PipboyRecord.Array(id, List(u16()) { u32() })
                    }
                    8 -> {
                        val id = u32()
                        val added = List(u16()) { u32() to cstring() }.map { it.second to it.first }
                        val removed = List(u16()) { u32() }
                        records += PipboyRecord.Object(id, added, removed)
                    }
                    9 -> records += PipboyRecord.Value(u32(), PipboyValue.Null)
                    else -> break
                }
            } catch (_: Exception) { break }
        }
        return records
    }

    private fun decodeLocalMap(bytes: ByteArray): PipboyLocalMapUpdate? = runCatching {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val width = b.int
        val height = b.int
        val nw = b.float to b.float
        val ne = b.float to b.float
        val sw = b.float to b.float
        val pixels = ByteArray(b.remaining()).also(b::get)
        PipboyLocalMapUpdate(width, height, nw, ne, sw, pixels)
    }.getOrNull()
}

class PipboyDatabase {
    data class Node(
        val id: Long,
        var value: PipboyValue? = null,
        val objectChildren: MutableMap<String, Long> = mutableMapOf(),
        var arrayChildren: List<Long> = emptyList()
    )

    val nodes = mutableMapOf(0L to Node(0L))
    var localMap: PipboyLocalMapUpdate? = null
        private set

    fun apply(update: PipboyUpdate) {
        when (update) {
            is PipboyUpdate.Data -> update.records.forEach(::apply)
            is PipboyUpdate.LocalMap -> localMap = update.map
            is PipboyUpdate.CommandResponse -> Unit
        }
    }

    private fun node(id: Long) = nodes.getOrPut(id) { Node(id) }

    private fun apply(record: PipboyRecord) {
        when (record) {
            is PipboyRecord.Value -> node(record.nodeId).value = record.value
            is PipboyRecord.Array -> node(record.nodeId).apply { arrayChildren = record.children; value = null }
            is PipboyRecord.Object -> {
                val n = node(record.nodeId)
                record.added.forEach { n.objectChildren[it.first] = it.second }
                record.removed.forEach { id -> n.objectChildren.entries.removeIf { it.value == id } }
                n.value = null
            }
        }
    }

    fun value(path: String): PipboyValue? = nodeId(path)?.let { nodes[it]?.value }

    fun objectChildren(path: String = ""): Map<String, Long> {
        val id = if (path.isEmpty()) 0L else nodeId(path) ?: return emptyMap()
        return nodes[id]?.objectChildren?.toMap() ?: emptyMap()
    }

    fun objectValue(id: Long, key: String): PipboyValue? =
        nodes[id]?.objectChildren?.get(key)?.let { nodes[it]?.value }

    fun nodeId(path: String): Long? {
        if (path.isBlank()) return 0L
        var current = 0L
        for (token in path.split(".")) {
            var key = token
            while (key.contains("[")) {
                val open = key.indexOf('[')
                val base = key.substring(0, open)
                if (base.isNotEmpty()) current = nodes[current]?.objectChildren?.get(base) ?: return null
                val close = key.indexOf(']', open)
                val index = key.substring(open + 1, close).toIntOrNull() ?: return null
                current = nodes[current]?.arrayChildren?.getOrNull(index) ?: return null
                key = key.substring(close + 1)
            }
            if (key.isNotEmpty()) current = nodes[current]?.objectChildren?.get(key) ?: return null
        }
        return current
    }
}

class PipboyConnection {
    var onState: ((String) -> Unit)? = null
    var onUpdate: ((PipboyUpdate) -> Unit)? = null
    private var socket: Socket? = null
    private var rpcId = 1L

    suspend fun discoverAndConnect() = withContext(Dispatchers.IO) {
        onState?.invoke("DISCOVERING")
        DatagramSocket().use { udp ->
            udp.broadcast = true
            udp.soTimeout = 5000
            val request = """{"cmd":"autodiscover"}""".toByteArray()
            udp.send(DatagramPacket(request, request.size, InetAddress.getByName("255.255.255.255"), 28000))
            val buffer = ByteArray(8192)
            val packet = DatagramPacket(buffer, buffer.size)
            udp.receive(packet)
            val response = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
            val host = runCatching {
                val json = JSONObject(response)
                listOf("ip", "host", "address", "gameIP", "gameIp")
                    .firstNotNullOfOrNull { json.optString(it).takeIf(String::isNotBlank) }
            }.getOrNull() ?: response.trim().substringBefore(":")
            connect(host)
        }
    }

    suspend fun connect(host: String) = withContext(Dispatchers.IO) {
        onState?.invoke("CONNECTING")
        socket = Socket(host, 27000)
        onState?.invoke("CONNECTED")
        receiveLoop(socket!!)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
        onState?.invoke("DISCONNECTED")
    }

    suspend fun sendRPC(type: Int, args: List<Any?>) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", rpcId++)
            put("type", type)
            put("args", JSONArray(args))
        }.toString().toByteArray()
        sendFrame(5, json)
    }

    private fun sendFrame(type: Int, payload: ByteArray) {
        val out = socket?.getOutputStream() ?: return
        val header = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size).put(type.toByte()).array()
        out.write(header); out.write(payload); out.flush()
    }

    private fun receiveLoop(s: Socket) {
        val input = s.getInputStream()
        while (!s.isClosed) {
            val header = input.readNBytes(5)
            if (header.size < 5) break
            val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val size = h.int
            val type = h.get().toInt() and 0xFF
            if (size !in 0..(16 * 1024 * 1024)) break
            val payload = input.readNBytes(size)
            if (payload.size != size) break
            if (type == 0) sendFrame(0, ByteArray(0))
            else PipboyPacketDecoder.decode(type, payload)?.let { onUpdate?.invoke(it) }
        }
    }
}
