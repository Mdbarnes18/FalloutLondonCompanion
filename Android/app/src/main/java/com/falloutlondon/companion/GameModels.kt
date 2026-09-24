package com.falloutlondon.companion

data class PlayerState(
    val hp: Double = 110.0,
    val maxHp: Double = 110.0,
    val ap: Double = 100.0,
    val maxAp: Double = 100.0,
    val radiation: Double = 0.0,
    val carryWeight: Double = 42.0,
    val maxWeight: Double = 185.0,
    val xpProgress: Double = 0.62,
    val perkPoints: Int = 1,
    val level: Int = 1,
    val special: List<Int> = listOf(5, 5, 5, 5, 5, 5, 5),
    val limbConditions: Map<String, Double> = healthyLimbs(),
    val activeEffects: List<String> = emptyList()
) {
    companion object {
        fun from(db: PipboyDatabase, fallback: PlayerState): PlayerState {
            fun num(path: String, fallbackValue: Double): Double = when (val v = db.value(path)) {
                is PipboyValue.Float32 -> v.value.toDouble()
                is PipboyValue.Int32 -> v.value.toDouble()
                is PipboyValue.UInt32 -> v.value.toDouble()
                is PipboyValue.UInt8 -> v.value.toDouble()
                is PipboyValue.Int8 -> v.value.toDouble()
                else -> fallbackValue
            }
            fun int(path: String, fallbackValue: Int) = num(path, fallbackValue.toDouble()).toInt()
            return fallback.copy(
                hp = num("playerinfo.currhp", fallback.hp),
                maxHp = num("playerinfo.maxhp", fallback.maxHp),
                ap = num("playerinfo.currap", fallback.ap),
                maxAp = num("playerinfo.maxap", fallback.maxAp),
                carryWeight = num("playerinfo.currweight", fallback.carryWeight),
                maxWeight = num("playerinfo.maxweight", fallback.maxWeight),
                xpProgress = num("playerinfo.xpprogresspct", fallback.xpProgress),
                perkPoints = int("playerinfo.perkpoints", fallback.perkPoints),
                level = int("playerinfo.xplevel", fallback.level),
                radiation = num("status.rads", fallback.radiation),
                special = (0 until 7).map { index -> int("special[${index}].value", fallback.special.getOrElse(index) { 5 }) },
                limbConditions = mapOf(
                    "HEAD" to num("stats.headcondition", 100.0) / 100.0,
                    "TORSO" to num("stats.torsocondition", 100.0) / 100.0,
                    "L ARM" to num("stats.larmcondition", 100.0) / 100.0,
                    "R ARM" to num("stats.rarmcondition", 100.0) / 100.0,
                    "L LEG" to num("stats.llegcondition", 100.0) / 100.0,
                    "R LEG" to num("stats.rlegcondition", 100.0) / 100.0
                )
            )
        }
        private fun healthyLimbs() = mapOf(
            "HEAD" to 1.0, "TORSO" to 1.0, "L ARM" to 1.0, "R ARM" to 1.0,
            "L LEG" to 1.0, "R LEG" to 1.0
        )
    }
}

enum class MainTab(val label: String) { STAT("STAT"), INV("INV"), DATA("DATA"), MAP("MAP"), RADIO("RADIO") }
enum class InventoryCategory(val label: String) { WEAPONS("WEAPONS"), APPAREL("APPAREL"), AID("AID"), MISC("MISC"), JUNK("JUNK"), AMMO("AMMO") }

data class InventoryItem(
    val id: String,
    val name: String,
    val count: Int,
    val equipped: Boolean,
    val favoriteSlot: Int?,
    val legendary: Boolean,
    val weight: Double,
    val value: Int,
    val damage: Double?,
    val armor: Double?,
    val radiationResistance: Double?,
    val energyResistance: Double?,
    val description: String?,
    val formId: Long?,
    val handleId: Long?,
    val stackIds: List<Long>,
    val category: InventoryCategory
)

class InventoryStore {
    var category = InventoryCategory.WEAPONS
    var selectedItemId: String? = null
    val items = mutableListOf<InventoryItem>()

    fun refresh(db: PipboyDatabase) {
        val root = db.nodeId("inventory") ?: return
        val found = mutableListOf<InventoryItem>()
        scan(db, root, "inventory", null, 0, found)
        items.clear()
        items.addAll(found.associateBy { it.id }.values.sortedBy { it.name.lowercase() })
        if (selectedItemId != null && items.none { it.id == selectedItemId }) selectedItemId = null
    }

    fun items(category: InventoryCategory) = items.filter { it.category == category }

    private fun scan(db: PipboyDatabase, nodeId: Long, path: String, inherited: InventoryCategory?, depth: Int, out: MutableList<InventoryItem>) {
        if (depth >= 8) return
        makeItem(db, path, nodeId, inherited)?.let { out += it; return }
        db.objectChildren(path).forEach { (key, child) -> scan(db, child, path + "." + key, categoryFromKey(key) ?: inherited, depth + 1, out) }
        db.arrayChildren(nodeId).forEachIndexed { index, child -> scan(db, child, path + "[" + index + "]", inherited, depth + 1, out) }
    }

    private fun makeItem(db: PipboyDatabase, path: String, nodeId: Long, inherited: InventoryCategory?): InventoryItem? {
        fun str(key: String) = (db.value(path + "." + key) as? PipboyValue.StringValue)?.value
        fun num(key: String): Double? = when (val v = db.value(path + "." + key)) {
            is PipboyValue.Float32 -> v.value.toDouble()
            is PipboyValue.Int32 -> v.value.toDouble()
            is PipboyValue.UInt32 -> v.value.toDouble()
            is PipboyValue.UInt8 -> v.value.toDouble()
            is PipboyValue.Int8 -> v.value.toDouble()
            else -> null
        }
        fun uint(key: String): Long? = num(key)?.takeIf { it >= 0 }?.toLong()
        val name = str("text") ?: str("name") ?: return null
        if (name.isBlank()) return null
        val form = uint("formid") ?: uint("formID")
        val handle = uint("handleid") ?: uint("handleID")
        val favoriteRaw = (num("favorite") ?: -2.0).toInt()
        val favorite = favoriteRaw.takeIf { it in 0..11 }
        val equipped = (num("equipstate") ?: num("equipped") ?: 0.0) != 0.0
        val stackNode = db.nodeId(path + ".stackid")
        val stacks = stackNode?.let { db.arrayChildren(it).mapNotNull { child -> asUInt(db.valueAtNode(child)) } } ?: emptyList()
        val resolved = inherited ?: categoryFromFilterFlag(num("filterflag")) ?: categoryFromItem(db, path)
        return InventoryItem(
            id = (handle ?: form ?: nodeId).toString() + "-" + resolved.label,
            name = name,
            count = (num("count") ?: num("quantity") ?: 1.0).toInt(),
            weight = num("weight") ?: 0.0,
            value = (num("value") ?: 0.0).toInt(),
            favoriteSlot = favorite,
            equipped = equipped,
            legendary = (num("islegendary") ?: 0.0) != 0.0,
            damage = num("damage") ?: num("damagerating"),
            armor = num("armorrating") ?: num("damageresistance"),
            radiationResistance = num("radiationresistance"),
            energyResistance = num("energyresistance"),
            description = str("description"),
            formId = form, handleId = handle, stackIds = stacks, category = resolved
        )
    }

    private fun categoryFromKey(key: String): InventoryCategory? = when (key.lowercase()) {
        "weapons", "weapon" -> InventoryCategory.WEAPONS
        "apparel", "armor", "armour" -> InventoryCategory.APPAREL
        "aid", "medical" -> InventoryCategory.AID
        "misc", "miscellaneous" -> InventoryCategory.MISC
        "junk" -> InventoryCategory.JUNK
        "ammo", "ammunition" -> InventoryCategory.AMMO
        else -> null
    }

    private fun categoryFromFilterFlag(value: Double?): InventoryCategory? = when (value?.toInt()) {
        2 -> InventoryCategory.WEAPONS
        4 -> InventoryCategory.APPAREL
        8 -> InventoryCategory.AID
        512, 1024 -> InventoryCategory.JUNK
        4096 -> InventoryCategory.AMMO
        640 -> InventoryCategory.MISC
        else -> null
    }

    private fun categoryFromItem(db: PipboyDatabase, path: String): InventoryCategory =
        when {
            db.nodeId(path + ".paperdollsection") != null -> InventoryCategory.APPAREL
            db.nodeId(path + ".currenthpgain") != null -> InventoryCategory.AID
            db.nodeId(path + ".damagerating") != null || db.nodeId(path + ".damage") != null -> InventoryCategory.WEAPONS
            else -> InventoryCategory.MISC
        }

    private fun asUInt(value: PipboyValue?): Long? = when (value) {
        is PipboyValue.UInt32 -> value.value
        is PipboyValue.Int32 -> value.value.toLong().takeIf { it >= 0 }
        is PipboyValue.UInt8 -> value.value.toLong()
        is PipboyValue.Int8 -> value.value.toLong().takeIf { it >= 0 }
        else -> null
    }
}

class MedicalController {
    var autoStimpakEnabled = true
    var threshold = 0.35
    private var lastUseMs = 0L

    suspend fun consume(player: PlayerState, db: PipboyDatabase, connection: PipboyConnection) {
        if (!autoStimpakEnabled || player.maxHp <= 0.0 || player.hp <= 0.0 || player.hp / player.maxHp > threshold) return
        useItem("inventory.stimpakobjectid", "inventory.stimpakobjectidisvalid", db, connection)
    }

    suspend fun useStimpak(db: PipboyDatabase, connection: PipboyConnection) =
        useItem("inventory.stimpakobjectid", "inventory.stimpakobjectidisvalid", db, connection, true)

    suspend fun useRadAway(db: PipboyDatabase, connection: PipboyConnection) =
        useItem("inventory.radawayobjectid", "inventory.radawayobjectidisvalid", db, connection, true)

    private suspend fun useItem(objectPath: String, validPath: String, db: PipboyDatabase, connection: PipboyConnection, bypassCooldown: Boolean = false) {
        if (!bypassCooldown && System.currentTimeMillis() - lastUseMs < 2000L) return
        if ((db.value(validPath) as? PipboyValue.Bool)?.value != true) return
        val objectId = asUInt(db.value(objectPath)) ?: return
        val handle = asUInt(db.objectValue(objectId, "handleid") ?: db.objectValue(objectId, "handleID")) ?: return
        val stackNode = db.nodes[objectId]?.objectChildren?.get("stackid") ?: return
        val stack = db.arrayChildren(stackNode).firstOrNull()?.let { asUInt(db.valueAtNode(it)) } ?: return
        val version = asUInt(db.value("inventory.version")) ?: return
        connection.sendRPC(0, listOf(handle, stack, version))
        lastUseMs = System.currentTimeMillis()
    }

    private fun asUInt(value: PipboyValue?): Long? = when (value) {
        is PipboyValue.UInt32 -> value.value
        is PipboyValue.Int32 -> value.value.toLong().takeIf { it >= 0 }
        is PipboyValue.UInt8 -> value.value.toLong()
        is PipboyValue.Int8 -> value.value.toLong().takeIf { it >= 0 }
        else -> null
    }
}
