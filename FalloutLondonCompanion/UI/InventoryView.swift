import SwiftUI

private enum InventorySort: String, CaseIterable {
    case name = "NAME"
    case count = "COUNT"
    case value = "VALUE"
    case weight = "WEIGHT"
}

private enum InventoryFilter: String, CaseIterable {
    case all = "ALL"
    case equipped = "EQUIPPED"
    case favorite = "FAVORITE"
    case legendary = "LEGENDARY"
}

struct InventoryView: View {
    @EnvironmentObject private var app: AppState
    @AppStorage("folon.inventory.sort") private var sortRaw = InventorySort.name.rawValue
    @AppStorage("folon.inventory.filter") private var filterRaw = InventoryFilter.all.rawValue
    @State private var search = ""

    private var sort: InventorySort { InventorySort(rawValue: sortRaw) ?? .name }
    private var filter: InventoryFilter { InventoryFilter(rawValue: filterRaw) ?? .all }

    private var visibleItems: [InventoryItem] {
        var result = app.inventory.items(in: app.inventory.category)
        if !search.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let query = search.lowercased()
            result = result.filter { $0.name.lowercased().contains(query) }
        }
        switch filter {
        case .all: break
        case .equipped: result = result.filter(\.equipped)
        case .favorite: result = result.filter(\.isFavorited)
        case .legendary: result = result.filter(\.legendary)
        }
        switch sort {
        case .name: return result.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        case .count: return result.sorted { $0.count == $1.count ? $0.name < $1.name : $0.count > $1.count }
        case .value: return result.sorted { $0.value == $1.value ? $0.name < $1.name : $0.value > $1.value }
        case .weight: return result.sorted { $0.weight == $1.weight ? $0.name < $1.name : $0.weight < $1.weight }
        }
    }

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Text("INV").font(.system(size: 20, design: .monospaced))
                Spacer()
                Text("\(visibleItems.count) / \(app.inventory.items(in: app.inventory.category).count) ITEMS")
                    .font(.system(size: 11, design: .monospaced))
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(InventoryCategory.allCases) { category in
                        Button(category.rawValue) {
                            app.inventory.category = category
                            app.inventory.selectedItemID = nil
                        }
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundStyle(app.inventory.category == category ? .black : .green)
                        .padding(.horizontal, 7).padding(.vertical, 5)
                        .background(app.inventory.category == category ? Color.green : Color.clear)
                        .overlay(Rectangle().stroke(.green.opacity(0.7), lineWidth: 1))
                    }
                }
            }

            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                TextField("SEARCH ITEMS", text: $search)
                    .textInputAutocapitalization(.never)
                    .font(.system(size: 10, design: .monospaced))
                if !search.isEmpty {
                    Button("CLEAR") { search = "" }.font(.system(size: 8, design: .monospaced))
                }
            }
            .foregroundStyle(.green)
            .padding(6)
            .overlay(Rectangle().stroke(.green.opacity(0.35)))

            HStack(spacing: 5) {
                Menu("FILTER: \(filter.rawValue)") {
                    ForEach(InventoryFilter.allCases, id: \.self) { option in
                        Button(option.rawValue) { filterRaw = option.rawValue }
                    }
                }
                .font(.system(size: 8, design: .monospaced))
                Menu("SORT: \(sort.rawValue)") {
                    ForEach(InventorySort.allCases, id: \.self) { option in
                        Button(option.rawValue) { sortRaw = option.rawValue }
                    }
                }
                .font(.system(size: 8, design: .monospaced))
                Spacer()
            }

            HStack(spacing: 10) {
                itemList
                itemDetail
            }
            .frame(maxHeight: .infinity)
        }
        .foregroundStyle(.green)
        .font(.system(size: 12, design: .monospaced))
    }

    private var itemList: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 4) {
                ForEach(visibleItems) { item in
                    Button {
                        app.inventory.selectedItemID = item.id
                    } label: {
                        HStack(spacing: 6) {
                            Text(item.name).lineLimit(1)
                            Spacer(minLength: 2)
                            if item.equipped { Text("E") }
                            if item.isFavorited { Text("★") }
                            if item.legendary { Text("L") }
                            if item.count > 1 { Text("x\(item.count)") }
                        }
                        .font(.system(size: 11, design: .monospaced))
                        .padding(.vertical, 4).padding(.horizontal, 5)
                        .background(app.inventory.selectedItemID == item.id ? Color.green.opacity(0.16) : .clear)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay {
            if visibleItems.isEmpty {
                Text(search.isEmpty ? "NO MATCHING ITEMS" : "NO SEARCH RESULTS")
                    .font(.system(size: 10, design: .monospaced))
            }
        }
    }

    private var itemDetail: some View {
        Group {
            if let id = app.inventory.selectedItemID,
               let item = app.inventory.items.first(where: { $0.id == id }) {
                VStack(alignment: .leading, spacing: 7) {
                    Text(item.name).font(.system(size: 16, weight: .bold, design: .monospaced))
                    if item.legendary { Text("★ LEGENDARY") }
                    if item.equipped { Text("EQUIPPED") }
                    Text("COUNT  \(item.count)")
                    Text(String(format: "WEIGHT %.1f", item.weight))
                    Text("VALUE  \(item.value)")
                    if let damage = item.damage { Text(String(format: "DMG    %.0f", damage)) }
                    if let armor = item.armor { Text(String(format: "ARMOR  %.0f", armor)) }
                    if let rr = item.radiationResistance { Text(String(format: "RAD RES %.0f", rr)) }
                    if let er = item.energyResistance { Text(String(format: "ENG RES %.0f", er)) }
                    if let description = item.description, !description.isEmpty { Text(description).lineLimit(5) }
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .padding(7)
                .overlay(Rectangle().stroke(.green.opacity(0.45), lineWidth: 1))
            } else {
                Text("SELECT ITEM").frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(minWidth: 150)
    }
}
