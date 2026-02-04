import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var router: MessageRouter
    @State private var peers: [Peer] = [
        Peer(id: "peer-1", name: "Nearby Device A", address: "AA:BB:CC:DD:EE:01"),
        Peer(id: "peer-2", name: "Nearby Device B", address: "AA:BB:CC:DD:EE:02")
    ]
    @State private var selectedPeer: Peer?
    @State private var messages: [ChatMessage] = []
    @State private var inputText: String = ""

    var body: some View {
        NavigationSplitView {
            List(peers, selection: $selectedPeer) { peer in
                VStack(alignment: .leading, spacing: 4) {
                    Text(peer.name).font(.headline)
                    Text(peer.address).font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Nearby")
        } detail: {
            if let peer = selectedPeer {
                chatView(peer: peer)
            } else {
                ContentUnavailableView("Select a peer", systemImage: "antenna.radiowaves.left.and.right")
            }
        }
        .onAppear { selectedPeer = peers.first }
    }

    private func chatView(peer: Peer) -> some View {
        VStack(spacing: 12) {
            List(messages) { msg in
                HStack {
                    if msg.isIncoming { Spacer() }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(msg.body ?? "(empty)")
                        Text(msg.status).font(.caption).foregroundStyle(.secondary)
                    }
                    .padding(8)
                    .background(msg.isIncoming ? Color.blue.opacity(0.15) : Color.gray.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    if !msg.isIncoming { Spacer() }
                }
            }

            HStack(spacing: 8) {
                TextField("Message", text: $inputText)
                    .textFieldStyle(.roundedBorder)
                Button("Send") { sendText(to: peer) }
                    .disabled(inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            HStack(spacing: 12) {
                Button("Simulate Receive") { simulateReceive(from: peer) }
                Button("Tick Router") { router.tick() }
            }
        }
        .padding()
        .navigationTitle(peer.name)
    }

    private func sendText(to peer: Peer) {
        let msgId = UUID().uuidString
        let payload = ProtocolCodec.createTextMessage(msgId: msgId, from: "ios-device", to: peer.id, body: inputText)
        router.enqueueWithAck(msgId: msgId, toId: peer.id, payload: payload)
        messages.append(
            ChatMessage(
                id: msgId,
                type: ProtocolType.typeText,
                fromId: "ios-device",
                toId: peer.id,
                timestamp: Date(),
                body: inputText,
                status: "pending",
                isIncoming: false
            )
        )
        inputText = ""
    }

    private func simulateReceive(from peer: Peer) {
        let msgId = UUID().uuidString
        let incoming = ChatMessage(
            id: msgId,
            type: ProtocolType.typeText,
            fromId: peer.id,
            toId: "ios-device",
            timestamp: Date(),
            body: "Hello from \(peer.name)",
            status: "received",
            isIncoming: true
        )
        messages.append(incoming)

        let ackPayload = ProtocolCodec.createAckMessage(msgId: UUID().uuidString, from: "ios-device", to: peer.id, ackFor: msgId)
        if let ack = ProtocolCodec.parse(ackPayload) {
            router.handleAck(ackFor: ack.ackFor ?? "")
        }
    }
}
