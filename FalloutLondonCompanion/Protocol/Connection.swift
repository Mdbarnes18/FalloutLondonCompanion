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

    func discoverAndConnect() {
        onStateChange?(.discovering)
        // Fallout 4 / Fallout: London companion protocol uses UDP 28000
        // autodiscovery, followed by TCP 27000. The UDP probe is isolated
        // here so discovery can be tested independently from the TCP parser.
        discoverUDP()
    }

    func connect(host: String) {
        connect(NWEndpoint.hostPort(host: NWEndpoint.Host(host), port: 27000))
    }

    func connect(_ endpoint: NWEndpoint) {
        onStateChange?(.connecting)
        receiveBuffer.removeAll(keepingCapacity: true)

        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        let c = NWConnection(to: endpoint, using: parameters)
        connection = c

        c.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                self?.onStateChange?(.connected)
                self?.startHeartbeat()
                self?.receiveLoop()
            case .waiting:
                self?.onStateChange?(.reconnecting)
            case .failed(let error):
                self?.stopHeartbeat()
                self?.onStateChange?(.failed(error.localizedDescription))
            case .cancelled:
                self?.stopHeartbeat()
                self?.onStateChange?(.disconnected)
            default:
                break
            }
        }
        c.start(queue: queue)
    }

    func disconnect() {
        stopHeartbeat()
        connection?.cancel()
        connection = nil
    }

    func sendRPC(type: Int, args: [Any]) {
        guard let connection else { return }
        let payload: [String: Any] = ["id": nextRPCID, "type": type, "args": args]
        nextRPCID &+= 1
        guard let json = try? JSONSerialization.data(withJSONObject: payload) else { return }
        sendFrame(type: 5, payload: json, over: connection)
    }

    private func discoverUDP() {
        // UDP broadcast discovery is intentionally kept separate from the
        // TCP transport. iOS may require Local Network permission before the
        // broadcast can complete.
        let parameters = NWParameters.udp
        parameters.allowLocalEndpointReuse = true

        let c = NWConnection(
            host: "255.255.255.255",
            port: 28000,
            using: parameters
        )
        connection = c

        c.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                let payload = Data(#"{"cmd":"autodiscover"}"#.utf8)
                c.send(content: payload, completion: .contentProcessed { [weak self] error in
                    if let error {
                        self?.onStateChange?(.failed(error.localizedDescription))
                        return
                    }
                    self?.receiveDiscovery(c)
                })
            case .failed(let error):
                self?.onStateChange?(.failed(error.localizedDescription))
            default:
                break
            }
        }
        c.start(queue: queue)
    }

    private func receiveDiscovery(_ udp: NWConnection) {
        udp.receiveMessage { [weak self] data, _, _, error in
            guard let self else { return }
            if let error {
                self.onStateChange?(.failed(error.localizedDescription))
                return
            }
            guard let data, !data.isEmpty else {
                self.onStateChange?(.failed("Empty autodiscovery response"))
                return
            }

            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let candidates = ["ip", "host", "address", "gameIP", "gameIp"]
                if let host = candidates.compactMap({ json[$0] as? String }).first {
                    udp.cancel()
                    self.connect(host: host)
                    return
                }
            }

            if let text = String(data: data, encoding: .utf8),
               let host = text.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: ":").first {
                udp.cancel()
                self.connect(host: String(host))
                return
            }

            self.onStateChange?(.failed("Unrecognized autodiscovery response"))
        }
    }

    private func sendFrame(type: UInt8, payload: Data, over connection: NWConnection) {
        var size = UInt32(payload.count).littleEndian
        var packet = Data(bytes: &size, count: 4)
        packet.append(type)
        packet.append(payload)
        connection.send(content: packet, completion: .contentProcessed { [weak self] error in
            if let error { self?.onStateChange?(.failed(error.localizedDescription)) }
        })
    }

    private func receiveLoop() {
        connection?.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data, !data.isEmpty {
                self.receiveBuffer.append(data)
                self.decodeFrames()
            }
            if !isComplete && error == nil { self.receiveLoop() }
        }
    }

    private func decodeFrames() {
        while receiveBuffer.count >= 5 {
            let size = receiveBuffer.withUnsafeBytes { raw in
                UInt32(littleEndian: raw.loadUnaligned(as: UInt32.self))
            }
            let total = 5 + Int(size)
            guard size <= 16 * 1024 * 1024, receiveBuffer.count >= total else { return }

            let type = receiveBuffer[4]
            let payload = receiveBuffer.subdata(in: 5..<total)
            receiveBuffer.removeSubrange(0..<total)

            if type == 0 {
                sendFrame(type: 0, payload: Data(), over: connection!)
            } else {
                onUpdate?(PipboyPacketDecoder.decode(type: type, payload: payload))
            }
        }
    }

    private func startHeartbeat() {
        stopHeartbeat()
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + 20, repeating: 20)
        timer.setEventHandler { [weak self] in
            guard let connection = self?.connection else { return }
            self?.sendFrame(type: 0, payload: Data(), over: connection)
        }
        heartbeatTimer = timer
        timer.resume()
    }

    private func stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = nil
    }
}
