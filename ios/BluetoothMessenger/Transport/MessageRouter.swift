import Foundation

final class MessageRouter: ObservableObject {
    @Published private(set) var outbox: [OutboxEntry] = []
    @Published private(set) var delivered: Set<String> = []

    private let ackTimeout: TimeInterval = 15
    private let retryDelay: TimeInterval = 10

    func enqueueWithAck(msgId: String, toId: String, payload: String) {
        let entry = OutboxEntry(
            msgId: msgId,
            toId: toId,
            payload: payload,
            status: .pending,
            attempts: 0,
            lastAttemptAt: nil,
            nextAttemptAt: Date()
        )
        outbox.append(entry)
        attemptSend(msgId: msgId)
    }

    func handleAck(ackFor: String) {
        delivered.insert(ackFor)
        updateStatus(msgId: ackFor, status: .delivered, nextAttemptAt: nil)
    }

    private func attemptSend(msgId: String) {
        guard let idx = outbox.firstIndex(where: { $0.msgId == msgId }) else { return }
        var entry = outbox[idx]
        entry.attempts += 1
        entry.lastAttemptAt = Date()
        entry.status = .awaitingAck
        entry.nextAttemptAt = Date().addingTimeInterval(ackTimeout)
        outbox[idx] = entry

        // Transport hook (BLE/Wi‑Fi Direct/Mesh) goes here.
    }

    func tick() {
        let now = Date()
        for entry in outbox where entry.status != .delivered {
            if let next = entry.nextAttemptAt, next <= now {
                updateStatus(msgId: entry.msgId, status: .pending, nextAttemptAt: Date().addingTimeInterval(retryDelay))
                attemptSend(msgId: entry.msgId)
            }
        }
    }

    private func updateStatus(msgId: String, status: OutboxStatus, nextAttemptAt: Date?) {
        guard let idx = outbox.firstIndex(where: { $0.msgId == msgId }) else { return }
        var entry = outbox[idx]
        entry.status = status
        entry.nextAttemptAt = nextAttemptAt
        outbox[idx] = entry
    }
}

struct OutboxEntry: Identifiable, Hashable {
    let id = UUID()
    let msgId: String
    let toId: String
    let payload: String
    var status: OutboxStatus
    var attempts: Int
    var lastAttemptAt: Date?
    var nextAttemptAt: Date?
}

enum OutboxStatus: String {
    case pending
    case awaitingAck
    case delivered
}
