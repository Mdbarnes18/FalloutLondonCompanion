import SwiftUI

struct BootView: View {
    @EnvironmentObject private var app: AppState
    @State private var scanline = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 12) {
                Spacer()
                CRTText(text: text)
                Spacer()
            }
            .padding(28)
        }
        .overlay(alignment: .top) {
            if scanline { Rectangle().fill(Color.white.opacity(0.025)).frame(height: 2).offset(y: 40) }
        }
        .task {
            if app.bootPhase == .off { await app.boot() }
            withAnimation(.linear(duration: 1).repeatForever(autoreverses: false)) { scanline = true }
        }
    }

    private var text: String {
        switch app.bootPhase {
        case .off: return ""
        case .positioning: return "ATTA-BOY\nPOSITIONING..."
        case .crt: return "DISPLAY INITIALIZING..."
        case .initializing: return "ATTA-BOY SYSTEM\nINITIALIZING"
        case .hardware: return "SYSTEM CHECK ........ OK\nMEMORY ............. OK\nLINK ............... OK"
        case .credit: return "SYSTEM READY\n\nWITH THANKS TO TEAM FOLON"
        case .ready: return ""
        }
    }
}

struct CRTText: View {
    let text: String
    var body: some View {
        Text(text).font(.system(size: 18, design: .monospaced)).foregroundStyle(.green).multilineTextAlignment(.center).shadow(color: .green.opacity(0.7), radius: 7)
    }
}
