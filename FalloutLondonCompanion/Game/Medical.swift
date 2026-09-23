import Foundation

@MainActor
final class MedicalController {
    var autoStimpakEnabled = true
    var threshold: Double = 0.35
    private var lastUse = Date.distantPast
    private let cooldown: TimeInterval = 2.0

    func consume(player: PlayerState, database: PipboyDatabase, connection: ConnectionService) {
        guard autoStimpakEnabled, player.maxHP > 0, player.hp > 0 else { return }
        guard player.hp / player.maxHP <= threshold else { return }
        guard Date().timeIntervalSince(lastUse) >= cooldown else { return }

        guard let itemPath = database.firstObjectPath(
            in: "inventory.48",
            where: "text",
            equals: .string("Stimpak")
        ) else { return }

        guard let handle = uint32(database.value(at: itemPath + ".handleid")),
              let stack = firstArrayUInt32(database, path: itemPath + ".stackid"),
              let version = uint32(database.value(at: "inventory.version")) else { return }

        connection.sendRPC(type: 0, args: [handle, stack, version])
        lastUse = Date()
    }

    private func uint32(_ value: PipboyValue?) -> UInt32? {
        switch value {
        case .uint32(let value): return value
        case .int32(let value) where value >= 0: return UInt32(value)
        case .uint8(let value): return UInt32(value)
        default: return nil
        }
    }

    private func firstArrayUInt32(_ database: PipboyDatabase, path: String) -> UInt32? {
        guard let value = database.value(at: path + "[0]") else { return nil }
        return uint32(value)
    }
}
