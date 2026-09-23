import Foundation

enum PipboyUpdate { case values([String: AnyHashable]) }

struct PipboyPacketDecoder {
    static func decodeDataUpdate(_ data: Data) -> PipboyUpdate {
        .values(["rawUpdateBytes": AnyHashable(data.count)])
    }
}
