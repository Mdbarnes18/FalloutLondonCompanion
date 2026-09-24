import SwiftUI

struct StatusView: View {
    @EnvironmentObject private var app: AppState
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("STAT").font(.system(size: 20, design: .monospaced))
            HStack { Gauge(label: "HP", value: app.player.hp, max: app.player.maxHP); Gauge(label: "AP", value: app.player.ap, max: app.player.maxAP) }
            Text(String(format: "RAD  %05.1f", app.player.radiation)).font(.system(size: 14, design: .monospaced))
            Text(String(format: "WT   %05.1f / %05.1f", app.player.carryWeight, app.player.maxWeight)).font(.system(size: 14, design: .monospaced))
            ProgressView(value: app.player.xpProgress).tint(.green)
            Text("SPECIAL   " + app.player.special.map(String.init).joined(separator: " ")).font(.system(size: 12, design: .monospaced))
            VStack(alignment: .leading, spacing: 7) {
                Text("MEDICAL").font(.system(size: 11, design: .monospaced))
                HStack(spacing: 8) {
                    Button("STIMPAK") { app.useStimpak() }
                    Button("RADAWAY") { app.useRadAway() }
                }
                .buttonStyle(.bordered)
                .font(.system(size: 9, design: .monospaced))
                Toggle("AUTO-STIMPAK", isOn: Binding(
                    get: { app.medical.autoStimpakEnabled },
                    set: { app.setAutoStimpakEnabled($0) }
                ))
                .font(.system(size: 9, design: .monospaced))
                HStack {
                    Text("THRESHOLD \(Int(app.medical.threshold * 100))%")
                    Slider(value: Binding(
                        get: { app.medical.threshold },
                        set: { app.setAutoStimpakThreshold($0) }
                    ), in: 0.10...0.90, step: 0.05)
                }
                .font(.system(size: 8, design: .monospaced))
            }
            Spacer()
        }.foregroundStyle(.green).padding()
    }
}

struct Gauge: View {
    let label: String; let value: Double; let max: Double
    var body: some View {
        VStack(alignment: .leading) { Text(label); ProgressView(value: max > 0 ? value / max : 0).tint(.green); Text(String(format: "%03.0f / %03.0f", value, max)) }
            .font(.system(size: 12, design: .monospaced)).frame(maxWidth: .infinity, alignment: .leading)
    }
}
