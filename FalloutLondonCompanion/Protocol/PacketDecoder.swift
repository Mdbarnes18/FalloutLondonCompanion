import Foundation

enum PipboyUpdate {
    case data(PipboyDataUpdate)
    case localMap(PipboyLocalMapUpdate)
    case commandResponse(PipboyCommandResponse)
    case unknown(type: UInt8, payload: Data)
}

struct PipboyDataUpdate {
    let records: [PipboyRecord]
}

struct PipboyLocalMapUpdate {
    let width: Int
    let height: Int
    let northWest: SIMD2<Float>
    let northEast: SIMD2<Float>
    let southWest: SIMD2<Float>
    let pixels: Data
}

struct PipboyCommandResponse {
    let json: Any
}

enum PipboyRecord {
    case value(nodeID: UInt32, value: PipboyValue)
    case array(nodeID: UInt32, children: [UInt32])
    case object(nodeID: UInt32, added: [(key: String, nodeID: UInt32)], removed: [UInt32])
}

enum PipboyValue: Equatable {
    case null
    case bool(Bool)
    case int8(Int8)
    case uint8(UInt8)
    case int32(Int32)
    case uint32(UInt32)
    case float32(Float)
    case string(String)
}

struct PipboyPacketDecoder {
    static func decode(type: UInt8, payload: Data) -> PipboyUpdate {
        switch type {
        case 3:
            return .data(PipboyDataUpdate(records: decodeDataUpdate(payload)))
        case 4:
            if let map = decodeLocalMap(payload) { return .localMap(map) }
            return .unknown(type: type, payload: payload)
        case 6:
            if let json = try? JSONSerialization.jsonObject(with: payload) {
                return .commandResponse(PipboyCommandResponse(json: json))
            }
            return .unknown(type: type, payload: payload)
        default:
            return .unknown(type: type, payload: payload)
        }
    }

    private static func decodeDataUpdate(_ data: Data) -> [PipboyRecord] {
        var reader = BinaryReader(data: data)
        var records: [PipboyRecord] = []

        while reader.remaining > 0 {
            do {
                let type = try reader.readUInt8()
                let nodeID = try reader.readUInt32()

                switch type {
                case 0:
                    records.append(.value(nodeID: nodeID, value: .bool(try reader.readUInt8() != 0)))
                case 1:
                    records.append(.value(nodeID: nodeID, value: .int8(try reader.readInt8())))
                case 2:
                    records.append(.value(nodeID: nodeID, value: .uint8(try reader.readUInt8())))
                case 3:
                    records.append(.value(nodeID: nodeID, value: .int32(try reader.readInt32())))
                case 4:
                    records.append(.value(nodeID: nodeID, value: .uint32(try reader.readUInt32())))
                case 5:
                    records.append(.value(nodeID: nodeID, value: .float32(try reader.readFloat32())))
                case 6:
                    records.append(.value(nodeID: nodeID, value: .string(try reader.readCString())))
                case 7:
                    let count = Int(try reader.readUInt16())
                    var children: [UInt32] = []
                    children.reserveCapacity(count)
                    for _ in 0..<count { children.append(try reader.readUInt32()) }
                    records.append(.array(nodeID: nodeID, children: children))
                case 8:
                    let addedCount = Int(try reader.readUInt16())
                    var added: [(String, UInt32)] = []
                    added.reserveCapacity(addedCount)
                    for _ in 0..<addedCount {
                        let childID = try reader.readUInt32()
                        let key = try reader.readCString()
                        added.append((key, childID))
                    }
                    let removedCount = Int(try reader.readUInt16())
                    var removed: [UInt32] = []
                    removed.reserveCapacity(removedCount)
                    for _ in 0..<removedCount { removed.append(try reader.readUInt32()) }
                    records.append(.object(nodeID: nodeID, added: added.map { (key: $0.0, nodeID: $0.1) }, removed: removed))
                case 9:
                    records.append(.value(nodeID: nodeID, value: .null))
                default:
                    return records
                }
            } catch {
                break
            }
        }

        return records
    }

    private static func decodeLocalMap(_ data: Data) -> PipboyLocalMapUpdate? {
        var reader = BinaryReader(data: data)
        do {
            let width = Int(try reader.readUInt32())
            let height = Int(try reader.readUInt32())
            let nw = SIMD2<Float>(try reader.readFloat32(), try reader.readFloat32())
            let ne = SIMD2<Float>(try reader.readFloat32(), try reader.readFloat32())
            let sw = SIMD2<Float>(try reader.readFloat32(), try reader.readFloat32())
            let pixels = try reader.readData(count: reader.remaining)
            return PipboyLocalMapUpdate(width: width, height: height, northWest: nw, northEast: ne, southWest: sw, pixels: pixels)
        } catch {
            return nil
        }
    }
}

private struct BinaryReader {
    private let data: Data
    private var offset: Int = 0

    init(data: Data) { self.data = data }

    var remaining: Int { data.count - offset }

    mutating func readUInt8() throws -> UInt8 {
        guard remaining >= 1 else { throw BinaryReaderError.truncated }
        defer { offset += 1 }
        return data[offset]
    }

    mutating func readInt8() throws -> Int8 {
        Int8(bitPattern: try readUInt8())
    }

    mutating func readUInt16() throws -> UInt16 {
        let bytes = try readBytes(count: 2)
        return UInt16(bytes[0]) | (UInt16(bytes[1]) << 8)
    }

    mutating func readUInt32() throws -> UInt32 {
        let bytes = try readBytes(count: 4)
        return UInt32(bytes[0])
            | (UInt32(bytes[1]) << 8)
            | (UInt32(bytes[2]) << 16)
            | (UInt32(bytes[3]) << 24)
    }

    mutating func readInt32() throws -> Int32 {
        Int32(bitPattern: try readUInt32())
    }

    mutating func readFloat32() throws -> Float {
        Float(bitPattern: try readUInt32())
    }

    mutating func readCString() throws -> String {
        let start = offset
        while offset < data.count {
            if data[offset] == 0 {
                let value = String(decoding: data[start..<offset], as: UTF8.self)
                offset += 1
                return value
            }
            offset += 1
        }
        throw BinaryReaderError.truncated
    }

    mutating func readData(count: Int) throws -> Data {
        guard count >= 0, remaining >= count else { throw BinaryReaderError.truncated }
        let value = data.subdata(in: offset..<(offset + count))
        offset += count
        return value
    }

    private mutating func readBytes(count: Int) throws -> [UInt8] {
        Array(try readData(count: count))
    }
}

private enum BinaryReaderError: Error {
    case truncated
}
