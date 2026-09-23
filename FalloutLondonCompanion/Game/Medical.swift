import Foundation

@MainActor
final class MedicalController {
    var autoStimpakEnabled = true
    var threshold: Double = 0.35

    private var lastUse = Date.distantPast
    private let cooldown: TimeInterval = 2.0

    func consume(player: PlayerState, database: PipboyDatabase, connection: ConnectionService) {
        guard autoStimpakEnabled,
              player.maxHP > 0,
              player.hp > 0,
              player.hp / player.maxHP <= threshold else { return }

        useItem(
            objectIDPath: "inventory.stimpakobjectid",
            validPath: "inventory.stimpakobjectidisvalid",
            database: database,
            connection: connection
        )
    }

    func useStimpak(database: PipboyDatabase, connection: ConnectionService) {
        useItem(
            objectIDPath: "inventory.stimpakobjectid",
            validPath: "inventory.stimpakobjectidisvalid",
            database: database,
            connection: connection,
            bypassCooldown: true
        )
    }

    func useRadAway(database: PipboyDatabase, connection: ConnectionService) {
        useItem(
            objectIDPath: "inventory.radawayobjectid",
            validPath: "inventory.radawayobjectidisvalid",
            database: database,
            connection: connection,
            bypassCooldown: true
        )
    }

    private func useItem(
        objectIDPath: String,
        validPath: String,
        database: PipboyDatabase,
        connection: ConnectionService,
        bypassCooldown: Bool = false
    ) {
        guard bypassCooldown || Date().timeIntervalSince(lastUse) >= cooldown else { return }
        guard database.value(validPath, as: Bool.self) == true,
              let objectID = uint32(database.value(at: objectIDPath)),
              let handle = uint32(database.objectValue(atNode: objectID, key: "handleid")),
              let stackNodeID = database.objectNodeID(atNode: objectID, key: "stackid"),
              let stack = firstArrayUInt32(database, nodeID: stackNodeID),
              let version = uint32(database.value(at: "inventory.version")) else { return }

        connection.sendRPC(type: 0, args: [handle, stack, version])
        lastUse = Date()
    }

    private func firstArrayUInt32(_ database: PipboyDatabase, nodeID: UInt32) -> UInt32? {
        guard let childID = database.arrayChildren(at: nodeID)?.first else { return nil }
        return uint32(database.value(atNode: childID))
    }

    private func uint32(_ value: PipboyValue?) -> UInt32? {
        switch value {
        case .uint32(let value): return value
        case .int32(let value) where value >= 0: return UInt32(value)
        case .uint8(let value): return UInt32(value)
        case .int8(let value) where value >= 0: return UInt32(value)
        default: return nil
        }
    }
}
