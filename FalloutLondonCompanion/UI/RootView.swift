import SwiftUI

struct RootView: View {
    @EnvironmentObject private var app: AppState
    var body: some View {
        Group {
            if app.bootPhase != .ready { BootView() } else { ATTABoyShell { MainInterface() } }
        }
        .task { if app.bootPhase == .off { await app.boot() } }
    }
}

struct MainInterface: View {
    @EnvironmentObject private var app: AppState
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
            CRTFrame {
                switch app.selectedTab {
                case .stat: StatusView()
                case .inv: InventoryView()
                case .data: DataView()
                case .map: MapStatusView()
                case .radio: RadioView()
                }
            }
            HStack {
                ForEach(MainTab.allCases, id: \.self) { tab in
                    Button(tab.rawValue) { app.selectedTab = tab }
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(app.selectedTab == tab ? .green : .gray)
                        .frame(maxWidth: .infinity)
                }
            }.padding(.vertical, 10)
            }
        }
    }
}

struct CRTFrame<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 18).fill(Color(red: 0.01, green: 0.04, blue: 0.015))
            content.padding(20)
            Scanlines()
        }
        .aspectRatio(1.45, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct Scanlines: View {
    var body: some View {
        GeometryReader { geometry in
            Path { path in
                stride(from: 0, to: geometry.size.height, by: 4).forEach { y in
                    path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: geometry.size.width, y: y))
                }
            }.stroke(.green.opacity(0.035), lineWidth: 1)
        }.allowsHitTesting(false)
    }
}

struct PlaceholderScreen: View {
    let title: String
    var body: some View {
        VStack {
            Text(title).font(.system(size: 22, design: .monospaced))
            Text("LIVE MODULE UNDER CONSTRUCTION").font(.system(size: 11, design: .monospaced)).padding(.top, 8)
        }.foregroundStyle(.green)
    }
}


struct DataView: View {
    @EnvironmentObject private var app: AppState

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text("DATA")
                    .font(.system(size: 22, design: .monospaced))
                HStack {
                    Text("LIVE DATABASE")
                        .font(.system(size: 9, design: .monospaced))
                        .opacity(0.7)
                    Spacer()
                    NavigationLink("BROWSE ALL") { DataBrowserView() }
                        .font(.system(size: 9, design: .monospaced))
                }

                DataSection(title: "QUESTS", path: "quests")
                DataSection(title: "LOG", path: "log")
                DataSection(title: "WORKSHOP", path: "workshop")
                DataSection(title: "PLAYER", path: "playerinfo")
            }
            .foregroundStyle(.green)
            .padding(16)
        }
    }
}

struct DataSection: View {
    @EnvironmentObject private var app: AppState
    let title: String
    let path: String

    var body: some View {
        let children = app.database.objectChildren(at: path)
        VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text(title)
                    .font(.system(size: 13, design: .monospaced))
                Spacer()
                Text("\(children.count) NODES")
                    .font(.system(size: 8, design: .monospaced))
                    .opacity(0.65)
            }

            if children.isEmpty {
                Text("NO DATA RECEIVED")
                    .font(.system(size: 8, design: .monospaced))
                    .opacity(0.5)
            } else {
                ForEach(children.keys.sorted().prefix(8), id: \.self) { key in
                    Text("• \(key.uppercased())")
                        .font(.system(size: 9, design: .monospaced))
                        .opacity(0.8)
                }
            }
        }
        .padding(10)
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(.green.opacity(0.25)))
    }
}

struct RadioView: View {
    @EnvironmentObject private var app: AppState

    var body: some View {
        let stations = radioStations()
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text("RADIO")
                    .font(.system(size: 22, design: .monospaced))
                Text("LIVE STATION DATA")
                    .font(.system(size: 9, design: .monospaced))
                    .opacity(0.7)

                if stations.isEmpty {
                    Text("NO RADIO DATA RECEIVED")
                        .font(.system(size: 9, design: .monospaced))
                        .opacity(0.6)
                        .padding(.top, 12)
                } else {
                    ForEach(stations) { station in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(station.name)
                                    .font(.system(size: 12, design: .monospaced))
                                Spacer()
                                if station.active {
                                    Text("ON AIR")
                                        .font(.system(size: 8, design: .monospaced))
                                }
                            }
                            if let frequency = station.frequency {
                                Text(frequency)
                                    .font(.system(size: 9, design: .monospaced))
                                    .opacity(0.7)
                            }
                            if let text = station.text {
                                Text(text)
                                    .font(.system(size: 9, design: .monospaced))
                                    .opacity(0.8)
                            }
                        }
                        .padding(10)
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(station.active ? .green.opacity(0.55) : .green.opacity(0.2)))
                    }
                }
            }
            .foregroundStyle(.green)
            .padding(16)
        }
    }

    private func radioStations() -> [RadioStation] {
        let children = app.database.objectChildren(at: "radio")
        return children.keys.sorted().compactMap { key in
            guard let nodeID = children[key] else { return nil }
            let name = string(at: nodeID, key: "name") ?? key
            let frequency = string(at: nodeID, key: "frequency")
            let text = string(at: nodeID, key: "text")
            let active = bool(at: nodeID, key: "active") ?? false
            let inRange = bool(at: nodeID, key: "inrange") ?? true
            guard inRange || active else { return nil }
            return RadioStation(id: key, name: name, frequency: frequency, text: text, active: active)
        }
    }

    private func string(at nodeID: UInt32, key: String) -> String? {
        guard case .string(let value) = app.database.objectValue(atNode: nodeID, key: key) else { return nil }
        return value
    }

    private func bool(at nodeID: UInt32, key: String) -> Bool? {
        guard case .bool(let value) = app.database.objectValue(atNode: nodeID, key: key) else { return nil }
        return value
    }
}

private struct RadioStation: Identifiable {
    let id: String
    let name: String
    let frequency: String?
    let text: String?
    let active: Bool
}

struct MapStatusView: View {
    @EnvironmentObject private var app: AppState

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("MAP")
                .font(.system(size: 22, design: .monospaced))
            Text("WORLDSPACE: \(app.database.value("map.currworldspace", as: String.self) ?? "UNKNOWN")")
                .font(.system(size: 9, design: .monospaced))
            Text("PLAYER POSITION")
                .font(.system(size: 10, design: .monospaced))
                .padding(.top, 8)
            Text("X  \(number("map.world.player.x"))")
                .font(.system(size: 9, design: .monospaced))
            Text("Y  \(number("map.world.player.y"))")
                .font(.system(size: 9, design: .monospaced))
            Text("ROT \(number("map.world.player.rotation"))")
                .font(.system(size: 9, design: .monospaced))
            if let local = app.database.localMap {
                Text("LOCAL SNAPSHOT  \(local.width) × \(local.height)")
                    .font(.system(size: 9, design: .monospaced))
                    .padding(.top, 8)
            } else {
                Text("NO LOCAL MAP SNAPSHOT")
                    .font(.system(size: 9, design: .monospaced))
                    .opacity(0.6)
                    .padding(.top, 8)
            }
        }
        .foregroundStyle(.green)
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func number(_ path: String) -> String {
        switch app.database.value(at: path) {
        case .float32(let value): return String(format: "%.2f", value)
        case .int32(let value): return String(value)
        case .uint32(let value): return String(value)
        default: return "--"
        }
    }
}
