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
        connectionService.onStateChange = { [weak self] state in
            Task { @MainActor in self?.connection = state }
        }
        connectionService.onUpdate = { [weak self] update in
            Task { @MainActor in self?.apply(update) }
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
        connectionService.discoverAndConnect()
    }

    func toggleQuest(formID: UInt32, instance: UInt32, type: UInt32) {
        connectionService.sendRPC(type: 5, args: [formID, instance, type])
    }

    func toggleRadioStation(pipID: UInt32) {
        connectionService.sendRPC(type: 12, args: [pipID])
    }

    private func apply(_ update: PipboyUpdate) {
        database.apply(update)
        player = PlayerState.from(database: database, fallback: player)
        inventory.refresh(from: database)
        medical.consume(player: player, database: database, connection: connectionService)
    }
}

enum ConnectionState: Equatable { case disconnected, discovering, connecting, connected, reconnecting, failed(String) }
enum BootPhase { case off, positioning, crt, initializing, hardware, credit, ready }
enum MainTab: String, CaseIterable { case stat = "STAT", inv = "INV", data = "DATA", map = "MAP", radio = "RADIO" }
