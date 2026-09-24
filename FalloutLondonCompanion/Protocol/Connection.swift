import Foundation
import Network

final class ConnectionService {
    var onStateChange: ((ConnectionState) -> Void)?
    var onUpdate: ((PipboyUpdate) -> Void)?

    private var connection: NWConnection?
    private let queue = DispatchQueue(label: "com.falloutlondon.attaboy.network")
    private var receiveBuffer = Data()
    private var nextRPCID: UInt32 = 1
    private var heartbeatTimer: DispatchSourceTimer?
    private var reconnectWork: DispatchWorkItem?
    private var reconnectAttempt = 0
    private var manualDisconnect = false
    private var lastReceive = Date()
    private var discoveryGeneration = UUID()

    func discoverAndConnect() {
        queue.async { [weak self] in
            guard let self else { return }
            self.manualDisconnect = false
            self.reconnectWork?.cancel()
            self.reconnectAttempt = 0
            self.startDiscovery()
        }
    }

    func connect(host: String) {
        queue.async { [weak self] in
            self?.manualDisconnect = false
            self?.reconnectWork?.cancel()
            self?.reconnectAttempt = 0
            self?.connectInternal(NWEndpoint.hostPort(host: NWEndpoint.Host(host), port: 27000))
        }
    }

    func connect(_ endpoint: NWEndpoint) {
        queue.async { [weak self] in
            self?.manualDisconnect = false
            self?.reconnectWork?.cancel()
            self?.reconnectAttempt = 0
            self?.connectInternal(endpoint)
        }
    }

    func disconnect() {
        queue.async { [weak self] in
            guard let self else { return }
            self.manualDisconnect = true
            self.reconnectWork?.cancel()
            self.reconnectWork = nil
            self.stopHeartbeat()
            self.connection?.cancel()
            self.connection = nil
            self.receiveBuffer.removeAll(keepingCapacity: true)
            self.onStateChange?(.disconnected)
        }
    }

    func sendRPC(type: Int, args: [Any]) {
        queue.async { [weak self] in
            guard let self, let connection = self.connection else { return }
            let payload: [String: Any] = ["id": self.nextRPCID, "type": type, "args": args]
            self.nextRPCID &+= 1
            guard let json = try? JSONSerialization.data(withJSONObject: payload) else { return }
            self.sendFrame(type: 5, payload: json, over: connection)
        }
    }

    private func startDiscovery() {
        guard !manualDisconnect else { return }
        reconnectWork?.cancel()
        reconnectWork = nil
        onStateChange?(.discovering)

        let generation = UUID()
        discoveryGeneration = generation

        let parameters = NWParameters.udp
        parameters.allowLocalEndpointReuse = true
        let udp = NWConnection(host: "255.255.255.255", port: 28000, using: parameters)
        connection = udp

        udp.stateUpdateHandler = { [weak self] state in
            guard let self, self.discoveryGeneration == generation else { return }
            switch state {
            case .ready:
                let payload = Data(#"{"cmd":"autodiscover"}"#.utf8)
                udp.send(content: payload, completion: .contentProcessed { [weak self] error in
                    guard let self, self.discoveryGeneration == generation else { return }
                    if let error {
                        self.failAndScheduleReconnect(error.localizedDescription)
                    } else {
                        self.receiveDiscovery(udp, generation: generation)
                    }
                })
                self.queue.asyncAfter(deadline: .now() + 5) { [weak self, weak udp] in
                    guard let self, self.discoveryGeneration == generation else { return }
                    if let udp, self.connection === udp {
                        udp.cancel()
                        self.failAndScheduleReconnect("Autodiscovery timed out")
                    }
                }
            case .failed(let error):
                self.failAndScheduleReconnect(error.localizedDescription)
            case .cancelled:
                break
            default:
                break
            }
        }
        udp.start(queue: queue)
    }

    private func receiveDiscovery(_ udp: NWConnection, generation: UUID) {
        udp.receiveMessage { [weak self] data, _, _, error in
            guard let self, self.discoveryGeneration == generation else { return }
            if let error {
                self.failAndScheduleReconnect(error.localizedDescription)
                return
            }
            guard let data, !data.isEmpty else {
                self.failAndScheduleReconnect("Empty autodiscovery response")
                return
            }

            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let candidates = ["ip", "host", "address", "gameIP", "gameIp"]
                if let host = candidates.compactMap({ json[$0] as? String }).first, !host.isEmpty {
                    udp.cancel()
                    connectInternal(NWEndpoint.hostPort(host: NWEndpoint.Host(host), port: 27000))
                    return
                }
            }

            if let text = String(data: data, encoding: .utf8) {
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                let host = trimmed.split(separator: ":").first.map(String.init) ?? ""
                if !host.isEmpty {
                    udp.cancel()
                    connectInternal(NWEndpoint.hostPort(host: NWEndpoint.Host(host), port: 27000))
                    return
                }
            }

            self.failAndScheduleReconnect("Unrecognized autodiscovery response")
        }
    }

    private func connectInternal(_ endpoint: NWEndpoint) {
        guard !manualDisconnect else { return }
        reconnectWork?.cancel()
        reconnectWork = nil
        stopHeartbeat()
        connection?.cancel()
        receiveBuffer.removeAll(keepingCapacity: true)
        onStateChange?(.connecting)

        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        let c = NWConnection(to: endpoint, using: parameters)
        connection = c

        c.stateUpdateHandler = { [weak self] state in
            guard let self else { return }
            switch state {
            case .ready:
                self.reconnectAttempt = 0
                self.lastReceive = Date()
                self.onStateChange?(.connected)
                self.startHeartbeat()
                self.receiveLoop(for: c)
            case .waiting:
                self.onStateChange?(.reconnecting)
            case .failed(let error):
                self.stopHeartbeat()
                self.failAndScheduleReconnect(error.localizedDescription)
            case .cancelled:
                self.stopHeartbeat()
                if !self.manualDisconnect { self.scheduleReconnect(reason: "Connection closed") }
                else { self.onStateChange?(.disconnected) }
            default:
                break
            }
        }
        c.start(queue: queue)
    }

    private func receiveLoop(for c: NWConnection) {
        c.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, isComplete, error in
            guard let self, self.connection === c else { return }
            if let data, !data.isEmpty {
                self.lastReceive = Date()
                self.receiveBuffer.append(data)
                self.decodeFrames(using: c)
            }
            if !isComplete && error == nil {
                self.receiveLoop(for: c)
            } else if !self.manualDisconnect {
                self.stopHeartbeat()
                self.scheduleReconnect(reason: error?.localizedDescription ?? "Connection closed")
            }
        }
    }

    private func decodeFrames(using c: NWConnection) {
        while receiveBuffer.count >= 5 {
            let size = receiveBuffer.withUnsafeBytes { raw in
                UInt32(littleEndian: raw.loadUnaligned(as: UInt32.self))
            }
            guard size <= 16 * 1024 * 1024 else {
                c.cancel()
                failAndScheduleReconnect("Invalid packet size")
                return
            }
            let total = 5 + Int(size)
            guard receiveBuffer.count >= total else { return }

            let type = receiveBuffer[4]
            let payload = receiveBuffer.subdata(in: 5..<total)
            receiveBuffer.removeSubrange(0..<total)

            if type == 0 {
                sendFrame(type: 0, payload: Data(), over: c)
            } else {
                onUpdate?(PipboyPacketDecoder.decode(type: type, payload: payload))
            }
        }
    }

    private func sendFrame(type: UInt8, payload: Data, over connection: NWConnection) {
        var size = UInt32(payload.count).littleEndian
        var packet = Data(bytes: &size, count: 4)
        packet.append(type)
        packet.append(payload)
        connection.send(content: packet, completion: .contentProcessed { [weak self] error in
            guard let self else { return }
            if let error, !self.manualDisconnect {
                self.failAndScheduleReconnect(error.localizedDescription)
            }
        })
    }

    private func startHeartbeat() {
        stopHeartbeat()
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + 20, repeating: 20)
        timer.setEventHandler { [weak self] in
            guard let self, let connection = self.connection, !self.manualDisconnect else { return }
            if Date().timeIntervalSince(self.lastReceive) > 60 {
                connection.cancel()
                self.failAndScheduleReconnect("Heartbeat timeout")
                return
            }
            self.sendFrame(type: 0, payload: Data(), over: connection)
        }
        heartbeatTimer = timer
        timer.resume()
    }

    private func stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = nil
    }

    private func failAndScheduleReconnect(_ reason: String) {
        stopHeartbeat()
        onStateChange?(.failed(reason))
        scheduleReconnect(reason: reason)
    }

    private func scheduleReconnect(reason: String) {
        guard !manualDisconnect, reconnectWork == nil else { return }
        reconnectAttempt += 1
        let delaySeconds = min(pow(2.0, Double(min(reconnectAttempt - 1, 5))), 30.0)
        onStateChange?(.reconnecting)
        let work = DispatchWorkItem { [weak self] in
            guard let self, !self.manualDisconnect else { return }
            self.reconnectWork = nil
            self.startDiscovery()
        }
        reconnectWork = work
        queue.asyncAfter(deadline: .now() + delaySeconds, execute: work)
    }
}
