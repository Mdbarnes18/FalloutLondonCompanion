import SwiftUI

struct InventoryView: View {
    @EnvironmentObject private var app: AppState

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Text("INV")
                    .font(.system(size: 20, design: .monospaced))
                Spacer()
                Text("(app.inventory.items(in: app.inventory.category).count) ITEMS")
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
                        .padding(.horizontal, 7)
                        .padding(.vertical, 5)
                        .background(app.inventory.category == category ? Color.green : Color.clear)
                        .overlay(Rectangle().stroke(.green.opacity(0.7), lineWidth: 1))
                    }
                }
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
                ForEach(app.inventory.items(in: app.inventory.category)) { item in
                    Button {
                        app.inventory.selectedItemID = item.id
                    } label: {
                        HStack(spacing: 6) {
                            Text(item.name)
                                .lineLimit(1)
                            Spacer(minLength: 2)
                            if item.equipped { Text("E") }
                            if item.favoriteSlot != nil { Text("★") }
                            if item.count > 1 { Text("x(item.count)") }
                        }
                        .font(.system(size: 11, design: .monospaced))
                        .padding(.vertical, 4)
                        .padding(.horizontal, 5)
                        .background(app.inventory.selectedItemID == item.id ? Color.green.opacity(0.16) : .clear)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay {
            if app.inventory.items(in: app.inventory.category).isEmpty {
                Text(app.demoMode ? "NO LIVE INVENTORY" : "NO ITEMS")
                    .font(.system(size: 10, design: .monospaced))
            }
        }
    }

    private var itemDetail: some View {
        Group {
            if let id = app.inventory.selectedItemID,
               let item = app.inventory.items.first(where: { $0.id == id }) {
                VStack(alignment: .leading, spacing: 7) {
                    Text(item.name)
                        .font(.system(size: 16, weight: .bold, design: .monospaced))
                    if item.legendary { Text("★ LEGENDARY") }
                    if item.equipped { Text("EQUIPPED") }
                    Text("COUNT  (item.count)")
                    Text(String(format: "WEIGHT %.1f", item.weight))
                    Text("VALUE  (item.value)")
                    if let damage = item.damage { Text(String(format: "DMG    %.0f", damage)) }
                    if let armor = item.armor { Text(String(format: "ARMOR  %.0f", armor)) }
                    if let rr = item.radiationResistance { Text(String(format: "RAD RES %.0f", rr)) }
                    if let er = item.energyResistance { Text(String(format: "ENG RES %.0f", er)) }
                    if let description = item.description, !description.isEmpty {
                        Text(description).lineLimit(5)
                    }
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .padding(7)
                .overlay(Rectangle().stroke(.green.opacity(0.45), lineWidth: 1))
            } else {
                Text("SELECT ITEM")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(minWidth: 150)
    }
}
