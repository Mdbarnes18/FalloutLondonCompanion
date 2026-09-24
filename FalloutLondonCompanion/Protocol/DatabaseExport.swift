import Foundation

extension PipboyDatabase {
    func exportText() -> String {
        var output = "FALLOUT: LONDON ATTA-BOY DATA EXPORT\n"
        output += "Generated from the current live database\n\n"

        func valueText(_ value: PipboyValue) -> String {
            switch value {
            case .bool(let v): return v ? "true" : "false"
            case .int8(let v): return String(v)
            case .uint8(let v): return String(v)
            case .int32(let v): return String(v)
            case .uint32(let v): return String(v)
            case .float32(let v): return String(format: "%.6g", v)
            case .string(let v): return v.replacingOccurrences(of: "\n", with: "\\n")
            case .null: return "null"
            }
        }
        func walk(id: UInt32, path: String, depth: Int) {
            guard let node = nodes[id] else { return }
            if let value = node.value {
                output += String(repeating: "  ", count: depth) + "\(path) = \(valueText(value))\n"
            }
            for (key, childID) in node.objectChildren.sorted(by: { $0.key < $1.key }) {
                walk(id: childID, path: path.isEmpty ? key : "\(path).\(key)", depth: depth + 1)
            }
            for (index, childID) in node.arrayChildren.enumerated() {
                walk(id: childID, path: path.isEmpty ? "[\(index)]" : "\(path)[\(index)]", depth: depth + 1)
            }
        }
        walk(id: rootID, path: "", depth: 0)
        if let map = localMap {
            output += "\nLOCAL MAP SNAPSHOT\n"
            output += "  size = \(map.width) x \(map.height)\n"
            output += "  northWest = \(map.northWest)\n"
            output += "  northEast = \(map.northEast)\n"
            output += "  southWest = \(map.southWest)\n"
            output += "  pixels = \(map.pixels.count) bytes\n"
        }
        return output
    }
}
