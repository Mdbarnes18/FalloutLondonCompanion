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
        guard let handle: Int = database.value("Inventory.stimpak.handleID") else { return }
        guard let stack: Int = database.value("Inventory.stimpak.stackID") else { return }
        guard let version: Int = database.value("Inventory.version") else { return }
        connection.sendRPC(type: 0, args: [handle, stack, version])
        lastUse = Date()
    }
}
