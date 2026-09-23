import Foundation

enum InventoryCategory: String, CaseIterable, Identifiable {
    case weapons = "WEAPONS"
    case apparel = "APPAREL"
    case aid = "AID"
    case misc = "MISC"
    case junk = "JUNK"
    case ammo = "AMMO"
    var id: String { rawValue }
}

struct InventoryItem: Identifiable, Equatable {
    let id: String
    var name: String
    var count: Int
    var weight: Double
    var value: Int
    var favoriteSlot: Int?
    var equipped: Bool
    var legendary: Bool
    var damage: Double?
    var armor: Double?
    var radiationResistance: Double?
    var energyResistance: Double?
    var description: String?
    var formID: UInt32?
    var handleID: UInt32?
    var stackIDs: [UInt32]
    var category: InventoryCategory
    var isFavorited: Bool { favoriteSlot != nil }
}

@MainActor
final class InventoryStore: ObservableObject {
    @Published private(set) var items: [InventoryItem] = []
    @Published var category: InventoryCategory = .weapons
    @Published var selectedItemID: String?

    func refresh(from database: PipboyDatabase) {
        var found: [InventoryItem] = []
        scan(database, nodeID: database.nodeID(at: "inventory") ?? 0, path: "inventory", inheritedCategory: nil, depth: 0, into: &found)

        var unique: [String: InventoryItem] = [:]
        for item in found {
            unique[item.id] = item
        }
        items = unique.values.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }

        if let selectedItemID, !items.contains(where: { $0.id == selectedItemID }) {
            self.selectedItemID = nil
        }
    }

    func items(in category: InventoryCategory) -> [InventoryItem] {
        items.filter { $0.category == category }
    }

    private func scan(
        _ database: PipboyDatabase,
        nodeID: UInt32,
        path: String,
        inheritedCategory: InventoryCategory?,
        depth: Int,
        into output: inout [InventoryItem]
    ) {
        guard depth < 8 else { return }

        if let item = makeItem(database: database, path: path, nodeID: nodeID, inheritedCategory: inheritedCategory) {
            output.append(item)
            return
        }

        for (key, childID) in database.objectChildren(atNode: nodeID) {
            let childCategory = category(from: key) ?? inheritedCategory
            scan(database, nodeID: childID, path: "(path).(key)", inheritedCategory: childCategory, depth: depth + 1, into: &output)
        }

        for (index, childID) in (database.arrayChildren(at: nodeID) ?? []).enumerated() {
            scan(database, nodeID: childID, path: "(path)[(index)]", inheritedCategory: inheritedCategory, depth: depth + 1, into: &output)
        }
    }

    private func makeItem(
        database: PipboyDatabase,
        path: String,
        nodeID: UInt32,
        inheritedCategory: InventoryCategory?
    ) -> InventoryItem? {
        func string(_ key: String) -> String? {
            guard case .string(let value) = database.value(at: "(path).(key)") else { return nil }
            return value
        }

        func number(_ key: String) -> Double? {
            switch database.value(at: "(path).(key)") {
            case .float32(let v): return Double(v)
            case .int32(let v): return Double(v)
            case .uint32(let v): return Double(v)
            case .uint8(let v): return Double(v)
            case .int8(let v): return Double(v)
            default: return nil
            }
        }

        func uint(_ key: String) -> UInt32? {
            guard let value = number(key), value >= 0 else { return nil }
            return UInt32(value)
        }

        guard let name = string("text") ?? string("name"), !name.isEmpty else { return nil }

        let handle = uint("handleid") ?? uint("handleID")
        let form = uint("formid") ?? uint("formID")
        let count = Int(number("count") ?? number("quantity") ?? 1)
        let favoriteRaw = Int(number("favorite") ?? -2)
        let favorite = (0...11).contains(favoriteRaw) ? favoriteRaw : nil
        let equipped = (number("equipstate") ?? number("equipped") ?? 0) != 0
        let stackIDs = readStackIDs(database, path: path)

        let resolvedCategory =
            inheritedCategory ??
            categoryFromFilterFlag(number("filterflag")) ??
            categoryFromItem(database, path: path)

        return InventoryItem(
            id: "(handle ?? form ?? nodeID)-(resolvedCategory.rawValue)",
            name: name,
            count: count,
            weight: number("weight") ?? 0,
            value: Int(number("value") ?? 0),
            favoriteSlot: favorite,
            equipped: equipped,
            legendary: (number("islegendary") ?? 0) != 0,
            damage: number("damage"),
            armor: number("armorrating"),
            radiationResistance: number("radiationresistance"),
            energyResistance: number("energyresistance"),
            description: string("description"),
            formID: form,
            handleID: handle,
            stackIDs: stackIDs,
            category: resolvedCategory
        )
    }

    private func readStackIDs(_ database: PipboyDatabase, path: String) -> [UInt32] {
        guard let node = database.nodeID(at: "(path).stackid") else { return [] }
        return (database.arrayChildren(at: node) ?? []).compactMap {
            switch database.value(atNode: $0) {
            case .uint32(let value): return value
            case .int32(let value) where value >= 0: return UInt32(value)
            default: return nil
            }
        }
    }

    private func category(from key: String) -> InventoryCategory? {
        switch key.lowercased() {
        case "weapons", "weapon": return .weapons
        case "apparel", "armor", "armour": return .apparel
        case "aid", "medical": return .aid
        case "misc", "miscellaneous": return .misc
        case "junk": return .junk
        case "ammo", "ammunition": return .ammo
        default: return nil
        }
    }

    private func categoryFromFilterFlag(_ value: Double?) -> InventoryCategory? {
        guard let value else { return nil }
        switch Int(value) {
        case 2: return .weapons
        case 4: return .apparel
        case 8: return .aid
        case 512, 1024: return .junk
        case 4096: return .ammo
        case 640: return .misc
        default: return nil
        }
    }

    private func categoryFromItem(_ database: PipboyDatabase, path: String) -> InventoryCategory {
        if database.nodeID(at: "(path).paperdollsection") != nil { return .apparel }
        if database.nodeID(at: "(path).currenthpgain") != nil { return .aid }
        if database.nodeID(at: "(path).damagerating") != nil || database.nodeID(at: "(path).damage") != nil { return .weapons }
        return .misc
    }
}
