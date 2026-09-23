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
        VStack(spacing: 0) {
            CRTFrame {
                switch app.selectedTab {
                case .stat: StatusView()
                case .inv: InventoryView()
                case .data: PlaceholderScreen(title: "DATA")
                case .map: PlaceholderScreen(title: "MAP")
                case .radio: PlaceholderScreen(title: "RADIO")
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
