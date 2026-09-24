import Foundation

final class PipboyDatabase {
    final class Node {
        let id: UInt32
        var value: PipboyValue?
        var objectChildren: [String: UInt32] = [:]
        var arrayChildren: [UInt32] = []

        init(id: UInt32) { self.id = id }
    }

    private(set) var nodes: [UInt32: Node] = [:]
    private(set) var rootID: UInt32 = 0
    private(set) var localMap: PipboyLocalMapUpdate?

    func reset() {
        nodes.removeAll()
        rootID = 0
        localMap = nil
    }

    func apply(_ update: PipboyUpdate) {
        switch update {
        case .data(let data):
            for record in data.records { apply(record) }
        case .localMap(let map):
            localMap = map
        case .commandResponse:
            break
        case .unknown:
            break
        }
    }

    func apply(_ record: PipboyRecord) {
        switch record {
        case .value(let id, let value):
            node(id).value = value
        case .array(let id, let children):
            let n = node(id)
            n.arrayChildren = children
            n.value = nil
        case .object(let id, let added, let removed):
            let n = node(id)
            for item in added { n.objectChildren[item.key] = item.nodeID }
            for childID in removed {
                n.objectChildren = n.objectChildren.filter { $0.value != childID }
            }
            n.value = nil
        }
    }

    func value<T>(_ path: String, as type: T.Type = T.self) -> T? {
        guard let value = value(at: path) else { return nil }
        switch value {
        case .bool(let v): return v as? T
        case .int8(let v): return v as? T
        case .uint8(let v): return v as? T
        case .int32(let v): return v as? T
        case .uint32(let v): return v as? T
        case .float32(let v): return v as? T
        case .string(let v): return v as? T
        case .null: return nil
        }
    }

    func value(at path: String) -> PipboyValue? {
        guard let id = nodeID(at: path) else { return nil }
        return nodes[id]?.value
    }

    func arrayChildren(at nodeID: UInt32) -> [UInt32]? { nodes[nodeID]?.arrayChildren }

    func objectChildren(atNode nodeID: UInt32) -> [String: UInt32] { nodes[nodeID]?.objectChildren ?? [:] }

    func value(atNode nodeID: UInt32) -> PipboyValue? { nodes[nodeID]?.value }

    func objectValue(atNode nodeID: UInt32, key: String) -> PipboyValue? {
        guard let childID = nodes[nodeID]?.objectChildren[key] else { return nil }
        return nodes[childID]?.value
    }

    func objectNodeID(atNode nodeID: UInt32, key: String) -> UInt32? {
        nodes[nodeID]?.objectChildren[key]
    }

    func objectChildren(at path: String = "") -> [String: UInt32] {
        let id: UInt32
        if path.isEmpty {
            id = rootID
        } else {
            guard let nodeID = nodeID(at: path) else { return [:] }
            id = nodeID
        }
        return nodes[id]?.objectChildren ?? [:]
    }

    func nodeID(at path: String) -> UInt32? {
        let tokens = path.split(separator: ".").map(String.init)
        guard !tokens.isEmpty else { return rootID }

        var current = rootID
        for token in tokens {
            var key = token
            while let open = key.firstIndex(of: "[") {
                let base = String(key[..<open])
                if !base.isEmpty {
                    guard let child = nodes[current]?.objectChildren[base] else { return nil }
                    current = child
                }
                guard let close = key.firstIndex(of: "]"),
                      let index = Int(key[key.index(after: open)..<close]),
                      index >= 0,
                      let child = nodes[current]?.arrayChildren[safe: index] else { return nil }
                current = child
                key = String(key[key.index(after: close)...])
            }
            if !key.isEmpty {
                guard let child = nodes[current]?.objectChildren[key] else { return nil }
                current = child
            }
        }
        return current
    }

    func firstObjectPath(in arrayPath: String, where key: String, equals expected: PipboyValue) -> String? {
        guard let arrayID = nodeID(at: arrayPath), let array = nodes[arrayID] else { return nil }
        for (index, childID) in array.arrayChildren.enumerated() {
            guard let child = nodes[childID],
                  let valueID = child.objectChildren[key],
                  let value = nodes[valueID]?.value,
                  value == expected else { continue }
            return "\(arrayPath)[\(index)]"
        }
        return nil
    }

    func flattenedValues(prefix: String = "") -> [String: PipboyValue] {
        var output: [String: PipboyValue] = [:]
        walk(id: rootID, path: prefix, output: &output)
        return output
    }

    private func walk(id: UInt32, path: String, output: inout [String: PipboyValue]) {
        guard let n = nodes[id] else { return }
        if let value = n.value, !path.isEmpty { output[path] = value }
        for (key, childID) in n.objectChildren {
            let childPath = path.isEmpty ? key : "\(path).\(key)"
            walk(id: childID, path: childPath, output: &output)
        }
        for (index, childID) in n.arrayChildren.enumerated() {
            let childPath = path.isEmpty ? "[\(index)]" : "\(path)[\(index)]"
            walk(id: childID, path: childPath, output: &output)
        }
    }

    private func node(_ id: UInt32) -> Node {
        if let existing = nodes[id] { return existing }
        let created = Node(id: id)
        nodes[id] = created
        return created
    }
}


private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
