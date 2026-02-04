import Foundation

struct Peer: Identifiable, Hashable {
    let id: String
    let name: String
    let address: String
}

struct ChatMessage: Identifiable, Hashable {
    let id: String
    let type: String
    let fromId: String
    let toId: String
    let timestamp: Date
    let body: String?
    let status: String
    let isIncoming: Bool
}
