import Foundation

final class PipboyDatabase {
    private(set) var values: [String: AnyHashable] = [:]
    func apply(_ update: PipboyUpdate) {
        if case .values(let values) = update { self.values.merge(values) { _, new in new } }
    }
    func value<T>(_ path: String, as type: T.Type = T.self) -> T? { values[path] as? T }
}
