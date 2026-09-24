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
        if let cached = UserDefaults.standard.data(forKey: "pipboyCache") {
            _ = database.restoreSnapshot(cached)
            player = PlayerState.from(database: database, fallback: .demo)
            inventory.refresh(from: database)
        }
        connectionService.onStateChange = { [weak self] state in
            Task { @MainActor in self?.connection = state }
        }
        connectionService.onUpdate = { [weak self] update in
            Task { @MainActor in self?.apply(update) }
        }
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
