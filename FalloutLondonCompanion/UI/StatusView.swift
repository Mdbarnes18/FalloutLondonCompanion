import SwiftUI

struct StatusView: View {
    @EnvironmentObject private var app: AppState
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("STAT").font(.system(size: 20, design: .monospaced))
                HStack {
                    Gauge(label: "HP", value: app.player.hp, max: app.player.maxHP)
                    Gauge(label: "AP", value: app.player.ap, max: app.player.maxAP)
                }
                RadiationMeter(value: app.player.radiation)
                Text(String(format: "WT   %05.1f / %05.1f", app.player.carryWeight, app.player.maxWeight))
                    .font(.system(size: 14, design: .monospaced))
                ProgressView(value: app.player.xpProgress).tint(.green)
                Text("LEVEL (app.player.level)   PERK POINTS (app.player.perkPoints)")
                    .font(.system(size: 11, design: .monospaced))
                Text("SPECIAL   " + app.player.special.map(String.init).joined(separator: " "))
                    .font(.system(size: 12, design: .monospaced))
                EffectsPanel(effects: app.player.activeEffects)
                PerkScaffold(perkPoints: app.player.perkPoints)
                CharacterReactionHost(player: app.player)
                VStack(alignment: .leading, spacing: 7) {
                    Text("MEDICAL").font(.system(size: 11, design: .monospaced))
                    HStack(spacing: 8) {
                        Button("STIMPAK") { app.useStimpak() }
                        Button("RADAWAY") { app.useRadAway() }
                    }.buttonStyle(.bordered).font(.system(size: 9, design: .monospaced))
                    Toggle("AUTO-STIMPAK", isOn: Binding(get: { app.medical.autoStimpakEnabled }, set: { app.setAutoStimpakEnabled($0) }))
                        .font(.system(size: 9, design: .monospaced))
                    HStack {
                        Text("THRESHOLD (Int(app.medical.threshold * 100))%")
                        Slider(value: Binding(get: { app.medical.threshold }, set: { app.setAutoStimpakThreshold($0) }), in: 0.10...0.90, step: 0.05)
                    }.font(.system(size: 8, design: .monospaced))
                }
            }
        }.foregroundStyle(.green).padding()
    }
}

private struct RadiationMeter: View {
    let value: Double
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack {
                Text(String(format: "RAD  %05.1f", value))
                Spacer()
                Text(value >= 80 ? "CRITICAL" : value >= 40 ? "ELEVATED" : "NOMINAL")
            }
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Rectangle().fill(.green.opacity(0.08))
                    Rectangle().fill(.green.opacity(0.65)).frame(width: geometry.size.width * min(max(value / 100.0, 0), 1))
                }
            }.frame(height: 6)
        }.font(.system(size: 10, design: .monospaced))
    }
}

private struct EffectsPanel: View {
    let effects: [String]
    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text("ACTIVE EFFECTS").font(.system(size: 10, design: .monospaced))
            if effects.isEmpty {
                Text("NO ACTIVE EFFECTS RECEIVED").font(.system(size: 8, design: .monospaced)).opacity(0.55)
            } else {
                ForEach(effects, id: .self) { Text("• ($0)").font(.system(size: 9, design: .monospaced)) }
            }
        }.padding(8).overlay(Rectangle().stroke(.green.opacity(0.25)))
    }
}

private struct PerkScaffold: View {
    let perkPoints: Int
    @State private var showingLevelUp = false
    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text("PERKS").font(.system(size: 10, design: .monospaced))
                Spacer()
                Text("(perkPoints) AVAILABLE").font(.system(size: 8, design: .monospaced))
            }
            Button(perkPoints > 0 ? "OPEN PERK SELECTION" : "NO LEVEL-UP AVAILABLE") { showingLevelUp = true }
                .disabled(perkPoints == 0)
                .font(.system(size: 9, design: .monospaced))
                .sheet(isPresented: $showingLevelUp) {
                    VStack(spacing: 12) {
                        Text("LEVEL-UP / PERK").font(.system(size: 18, design: .monospaced))
                        Text("PERK DATA WILL APPEAR HERE WHEN PROVIDED BY THE LIVE DATABASE.")
                            .font(.system(size: 9, design: .monospaced)).multilineTextAlignment(.center)
                        Text("NO PERK ACTIONS ARE ENABLED UNTIL THEIR DATA/RPC CAPABILITIES ARE VERIFIED.")
                            .font(.system(size: 8, design: .monospaced)).multilineTextAlignment(.center)
                        Button("CLOSE") { showingLevelUp = false }
                    }.foregroundStyle(.green).padding(24).background(Color.black)
                }
        }.padding(8).overlay(Rectangle().stroke(.green.opacity(0.25)))
    }
}

struct CharacterReactionHost: View {
    let player: PlayerState
    enum ReactionState: String { case normal = "NORMAL", warning = "WARNING", critical = "CRITICAL" }
    private var state: ReactionState {
        guard player.maxHP > 0 else { return .normal }
        let ratio = player.hp / player.maxHP
        return ratio <= 0.2 ? .critical : ratio <= 0.5 ? .warning : .normal
    }
    var body: some View {
        HStack {
            Text("CHARACTER REACTION")
            Spacer()
            Text(state.rawValue)
        }.font(.system(size: 8, design: .monospaced)).padding(7)
         .overlay(Rectangle().stroke(.green.opacity(0.2)))
    }
}

struct Gauge: View {
    let label: String; let value: Double; let max: Double
    var body: some View {
        VStack(alignment: .leading) {
            Text(label); ProgressView(value: max > 0 ? value / max : 0).tint(.green)
            Text(String(format: "%03.0f / %03.0f", value, max))
        }.font(.system(size: 12, design: .monospaced)).frame(maxWidth: .infinity, alignment: .leading)
    }
}
