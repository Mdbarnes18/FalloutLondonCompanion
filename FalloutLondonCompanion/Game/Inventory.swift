import Foundation

struct InventoryItem: Identifiable, Equatable {
    let id: String
    var name: String
    var count: Int
    var weight: Double
    var value: Int
    var favoriteSlot: Int?
    var equipped: Bool
    var legendary: Bool
}
