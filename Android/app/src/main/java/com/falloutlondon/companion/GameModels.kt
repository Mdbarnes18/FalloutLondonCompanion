package com.falloutlondon.companion

data class PlayerState(
    val hp: Double = 80.0,
    val maxHp: Double = 100.0,
    val ap: Double = 60.0,
    val maxAp: Double = 100.0,
    val radiation: Double = 0.0,
    val carryWeight: Double = 12.0,
    val maxWeight: Double = 200.0,
    val xpProgress: Double = 0.42,
    val perkPoints: Int = 0,
    val special: List<Int> = listOf(5, 5, 5, 5, 5, 5, 5),
    val limbConditions: Map<String, Double> = emptyMap()
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
                radiation = num("status.rads", fallback.radiation),
                limbConditions = mapOf(
                    "HEAD" to num("stats.headcondition", 1.0),
                    "TORSO" to num("stats.torsocondition", 1.0),
                    "L ARM" to num("stats.larmcondition", 1.0),
                    "R ARM" to num("stats.rarmcondition", 1.0),
                    "L LEG" to num("stats.llegcondition", 1.0),
                    "R LEG" to num("stats.rlegcondition", 1.0)
                )
            )
        }
    }
}

enum class MainTab(val label: String) { STAT("STAT"), INV("INV"), DATA("DATA"), MAP("MAP"), RADIO("RADIO") }
enum class InventoryCategory(val label: String) { WEAPONS("WEAPONS"), APPAREL("APPAREL"), AID("AID"), MISC("MISC"), JUNK("JUNK"), AMMO("AMMO") }

data class InventoryItem(
    val id: Long,
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
    val description: String?
)

class InventoryStore {
    var category = InventoryCategory.WEAPONS
    val items = mutableListOf<InventoryItem>()

    fun refresh(db: PipboyDatabase) {
        items.clear()
        val categoryKey = category.name.lowercase()
        val id = db.objectChildren("inventory").entries.firstOrNull { it.key.lowercase() == categoryKey }?.value ?: return
        db.nodes[id]?.arrayChildren.orEmpty().forEach { itemId ->
            fun str(key: String) = (db.objectValue(itemId, key) as? PipboyValue.StringValue)?.value
            fun num(key: String): Double? = when (val v = db.objectValue(itemId, key)) {
                is PipboyValue.Float32 -> v.value.toDouble()
                is PipboyValue.Int32 -> v.value.toDouble()
                is PipboyValue.UInt32 -> v.value.toDouble()
                is PipboyValue.UInt8 -> v.value.toDouble()
                is PipboyValue.Int8 -> v.value.toDouble()
                else -> null
            }
            fun int(key: String) = num(key)?.toInt() ?: 0
            fun bool(key: String) = (db.objectValue(itemId, key) as? PipboyValue.Bool)?.value ?: false
            val fav = int("favorite").takeIf { it in 0..11 }
            items += InventoryItem(
                id = itemId,
                name = str("text") ?: "UNKNOWN ITEM",
                count = maxOf(1, int("count")),
                equipped = bool("equipstate"),
                favoriteSlot = fav,
                legendary = bool("islegendary"),
                weight = num("weight") ?: 0.0,
                value = int("value"),
                damage = num("damagerating") ?: num("damage"),
                armor = num("damageresistance"),
                radiationResistance = num("radiationresistance"),
                energyResistance = num("energyresistance"),
                description = str("description")
            )
        }
    }
}

class MedicalController {
    var autoStimpakEnabled = true
    var threshold = 0.35
    private var lastUseMs = 0L

    suspend fun consume(player: PlayerState, db: PipboyDatabase, connection: PipboyConnection) {
        if (!autoStimpakEnabled || player.maxHp <= 0.0 || player.hp / player.maxHp > threshold) return
        if (System.currentTimeMillis() - lastUseMs < 2000L) return
        val valid = (db.value("inventory.stimpakobjectidisvalid") as? PipboyValue.Bool)?.value ?: false
        if (!valid) return
        val objectId = when (val v = db.value("inventory.stimpakobjectid")) {
            is PipboyValue.UInt32 -> v.value
            is PipboyValue.Int32 -> v.value.toLong()
            else -> return
        }
        val handle = (db.objectValue(objectId, "handleid") as? PipboyValue.UInt32)?.value ?: return
        val stack = (db.objectValue(objectId, "stackid") as? PipboyValue.UInt32)?.value ?: return
        val version = (db.value("inventory.version") as? PipboyValue.UInt32)?.value ?: return
        connection.sendRPC(0, listOf(handle, stack, version))
        lastUseMs = System.currentTimeMillis()
    }
}
