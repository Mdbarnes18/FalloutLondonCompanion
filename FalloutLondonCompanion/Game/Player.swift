import Foundation

struct PlayerState: Equatable {
    var hp: Double
    var maxHP: Double
    var ap: Double
    var maxAP: Double
    var radiation: Double
    var carryWeight: Double
    var maxWeight: Double
    var xpProgress: Double
    var perkPoints: Int
    var level: Int
    var limbs: LimbCondition
    var special: [Int]
    var activeEffects: [String]

    static let demo = PlayerState(
        hp: 110, maxHP: 110, ap: 100, maxAP: 100,
        radiation: 0, carryWeight: 42, maxWeight: 185,
        xpProgress: 0.62, perkPoints: 1, level: 1,
        limbs: .healthy, special: [5,5,5,5,5,5,5], activeEffects: []
    )

    static func from(database: PipboyDatabase, fallback: PlayerState) -> PlayerState {
        func number(_ path: String, _ fallback: Double) -> Double {
            switch database.value(at: path) {
            case .float32(let value): return Double(value)
            case .int32(let value): return Double(value)
            case .uint32(let value): return Double(value)
            case .int8(let value): return Double(value)
            case .uint8(let value): return Double(value)
            default: return fallback
            }
        }

        func integer(_ path: String, _ fallback: Int) -> Int {
            Int(number(path, Double(fallback)))
        }

        let special = (0..<7).map { index in
            integer("special[\(index)].value", fallback.special[safe: index] ?? 5)
        }

        return PlayerState(
            hp: number("playerinfo.currhp", fallback.hp),
            maxHP: number("playerinfo.maxhp", fallback.maxHP),
            ap: number("playerinfo.currap", fallback.ap),
            maxAP: number("playerinfo.maxap", fallback.maxAP),
            radiation: fallback.radiation,
            carryWeight: number("playerinfo.currweight", fallback.carryWeight),
            maxWeight: number("playerinfo.maxweight", fallback.maxWeight),
            xpProgress: number("playerinfo.xpprogresspct", fallback.xpProgress),
            perkPoints: integer("playerinfo.perkpoints", fallback.perkPoints),
            level: integer("playerinfo.xplevel", fallback.level),
            limbs: LimbCondition(
                head: number("stats.headcondition", fallback.limbs.head) / 100,
                torso: number("stats.torsocondition", fallback.limbs.torso) / 100,
                leftArm: number("stats.larmcondition", fallback.limbs.leftArm) / 100,
                rightArm: number("stats.rarmcondition", fallback.limbs.rightArm) / 100,
                leftLeg: number("stats.llegcondition", fallback.limbs.leftLeg) / 100,
                rightLeg: number("stats.rlegcondition", fallback.limbs.rightLeg) / 100
            ),
            special: special,
            activeEffects: fallback.activeEffects
        )
    }
}

struct LimbCondition: Equatable {
    var head: Double
    var torso: Double
    var leftArm: Double
    var rightArm: Double
    var leftLeg: Double
    var rightLeg: Double
    static let healthy = LimbCondition(head: 1, torso: 1, leftArm: 1, rightArm: 1, leftLeg: 1, rightLeg: 1)
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
