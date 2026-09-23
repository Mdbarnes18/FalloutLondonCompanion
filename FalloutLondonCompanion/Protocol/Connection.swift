import Foundation
import Network

final class ConnectionService {
    var onStateChange: ((ConnectionState) -> Void)?
    var onUpdate: ((PipboyUpdate) -> Void)?
    private var browser: NWBrowser?
    private var connection: NWConnection?
    private let queue = DispatchQueue(label: "com.falloutlondon.attaboy.network")
    private var receiveBuffer = Data()
    private var nextRPCID: UInt32 = 1

    func discoverAndConnect() {
        onStateChange?(.discovering)
        let browser = NWBrowser(for: .bonjour(type: "_pipboy._tcp", domain: nil), using: .udp)
        self.browser = browser
        browser.stateUpdateHandler = { [weak self] state in
            if case .failed(let error) = state { self?.onStateChange?(.failed(error.localizedDescription)) }
        }
        browser.browseResultsChangedHandler = { [weak self] results, _ in
            guard let endpoint = results.first?.endpoint else { return }
            self?.connect(endpoint)
        }
        browser.start(queue: queue)
    }

    func connect(_ endpoint: NWEndpoint) {
        browser?.cancel()
        onStateChange?(.connecting)
        let c = NWConnection(to: endpoint, using: .tcp)
        connection = c
        c.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready: self?.onStateChange?(.connected); self?.receiveLoop()
            case .waiting: self?.onStateChange?(.reconnecting)
            case .failed(let e): self?.onStateChange?(.failed(e.localizedDescription))
            case .cancelled: self?.onStateChange?(.disconnected)
            default: break
            }
        }
        c.start(queue: queue)
    }

    func disconnect() { connection?.cancel(); connection = nil }

    func sendRPC(type: Int, args: [Any]) {
        guard let connection else { return }
        let payload: [String: Any] = ["id": nextRPCID, "type": type, "args": args]
        nextRPCID &+= 1
        guard let json = try? JSONSerialization.data(withJSONObject: payload) else { return }
        var size = UInt32(json.count).littleEndian
        var packet = Data(bytes: &size, count: 4)
        packet.append(5)
        packet.append(json)
        connection.send(content: packet, completion: .contentProcessed { _ in })
    }

    private func receiveLoop() {
        connection?.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data, !data.isEmpty { self.receiveBuffer.append(data); self.decodeFrames() }
            if !isComplete && error == nil { self.receiveLoop() }
        }
    }

    private func decodeFrames() {
        while receiveBuffer.count >= 5 {
            let size = receiveBuffer.withUnsafeBytes { $0.load(as: UInt32.self).littleEndian }
            let total = 5 + Int(size)
            guard receiveBuffer.count >= total else { return }
            let type = receiveBuffer[4]
            let payload = receiveBuffer.subdata(in: 5..<total)
            receiveBuffer.removeSubrange(0..<total)
            if type == 3 { onUpdate?(PipboyPacketDecoder.decodeDataUpdate(payload)) }
        }
    }
}
