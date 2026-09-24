import Foundation
import SwiftUI

@MainActor
final class AppState: ObservableObject {
    @Published var connection = ConnectionState.disconnected
    @Published var player = PlayerState.demo
    @Published var bootPhase: BootPhase = .off
    @Published var selectedTab: MainTab = .stat
    @Published var demoMode = false

    let connectionService = ConnectionService()
    let database = PipboyDatabase()
    let medical = MedicalController()
    let inventory = InventoryStore()

    init() {
        demoMode = UserDefaults.standard.bool(forKey: "demoMode")
        medical.autoStimpakEnabled = UserDefaults.standard.object(forKey: "autoStimpakEnabled") as? Bool ?? true
        medical.threshold = UserDefaults.standard.object(forKey: "autoStimpakThreshold") as? Double ?? 0.35
        if let cached = UserDefaults.standard.data(forKey: "pipboyCache") {
            _ = database.restoreSnapshot(cached)
            player = PlayerState.from(database: database, fallback: .demo)
            inventory.refresh(from: database)
        }
        connectionService.onStateChange = { [weak self] state in
            Task { @MainActor in
                guard let self else { return }
                self.connection = state
                if state == .connected && !self.demoMode {
                    // A fresh TCP session starts a fresh database stream. Do not
                    // let values from a previous save/session survive a reconnect.
                    self.database.reset()
                    self.player = PlayerState.demo
                    self.inventory.refresh(from: self.database)
                }
            }
        }
        connectionService.onUpdate = { [weak self] update in
            Task { @MainActor in self?.apply(update) }
        }
    }

    func setAutoStimpakEnabled(_ enabled: Bool) {
        medical.autoStimpakEnabled = enabled
        UserDefaults.standard.set(enabled, forKey: "autoStimpakEnabled")
    }

    func setAutoStimpakThreshold(_ value: Double) {
        medical.threshold = min(max(value, 0.10), 0.90)
        UserDefaults.standard.set(medical.threshold, forKey: "autoStimpakThreshold")
    }

    func useStimpak() {
        medical.useStimpak(database: database, connection: connectionService)
    }

    func useRadAway() {
        medical.useRadAway(database: database, connection: connectionService)
    }

    func setDemoMode(_ enabled: Bool) {
        demoMode = enabled
        UserDefaults.standard.set(enabled, forKey: "demoMode")
        if enabled {
            connectionService.disconnect()
            if let cached = UserDefaults.standard.data(forKey: "pipboyCache") {
                _ = database.restoreSnapshot(cached)
                player = PlayerState.from(database: database, fallback: .demo)
                inventory.refresh(from: database)
            }
        } else {
            connectionService.discoverAndConnect()
        }
    }

    func boot() async {
        bootPhase = .off
        try? await Task.sleep(for: .milliseconds(450))
        bootPhase = .positioning
        try? await Task.sleep(for: .milliseconds(700))
        bootPhase = .crt
        try? await Task.sleep(for: .milliseconds(650))
        bootPhase = .initializing
        try? await Task.sleep(for: .milliseconds(900))
        bootPhase = .hardware
        try? await Task.sleep(for: .milliseconds(750))
        bootPhase = .credit
        try? await Task.sleep(for: .milliseconds(1400))
        bootPhase = .ready
        if !demoMode { connectionService.discoverAndConnect() }
    }

    private func apply(_ update: PipboyUpdate) {
        database.apply(update)
        if let snapshot = database.snapshotData() {
            UserDefaults.standard.set(snapshot, forKey: "pipboyCache")
        }
        player = PlayerState.from(database: database, fallback: player)
        inventory.refresh(from: database)
        medical.consume(player: player, database: database, connection: connectionService)
    }
}

enum ConnectionState: Equatable { case disconnected, discovering, connecting, connected, reconnecting, failed(String) }
enum BootPhase { case off, positioning, crt, initializing, hardware, credit, ready }
enum MainTab: String, CaseIterable { case stat = "STAT", inv = "INV", data = "DATA", map = "MAP", radio = "RADIO" }
