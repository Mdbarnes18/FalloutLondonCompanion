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
    var limbs: LimbCondition
    var special: [Int]
    var activeEffects: [String]

    static let demo = PlayerState(hp: 110, maxHP: 110, ap: 100, maxAP: 100, radiation: 0, carryWeight: 42, maxWeight: 185, xpProgress: 0.62, perkPoints: 1, limbs: .healthy, special: [5,5,5,5,5,5,5], activeEffects: [])

    static func from(database: PipboyDatabase, fallback: PlayerState) -> PlayerState { fallback }
}

struct LimbCondition: Equatable {
    var head: Double; var torso: Double; var leftArm: Double; var rightArm: Double; var leftLeg: Double; var rightLeg: Double
    static let healthy = LimbCondition(head: 1, torso: 1, leftArm: 1, rightArm: 1, leftLeg: 1, rightLeg: 1)
}
