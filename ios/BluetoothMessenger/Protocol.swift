import Foundation

enum ProtocolType {
    static let typeText = "TEXT"
    static let typeImageOffer = "IMAGE_OFFER"
    static let typeImageAccept = "IMAGE_ACCEPT"
    static let typeImageReject = "IMAGE_REJECT"
    static let typeImageChunk = "IMAGE_CHUNK"
    static let typeImageComplete = "IMAGE_COMPLETE"
    static let typeAudioOffer = "AUDIO_OFFER"
    static let typeAudioAccept = "AUDIO_ACCEPT"
    static let typeAudioReject = "AUDIO_REJECT"
    static let typeAudioChunk = "AUDIO_CHUNK"
    static let typeAudioComplete = "AUDIO_COMPLETE"
    static let typePing = "PING"
    static let typePong = "PONG"
    static let typeRegister = "REGISTER"
    static let typeGroupJoin = "GROUP_JOIN"
    static let typeGroupLeave = "GROUP_LEAVE"
    static let typeGroupMessage = "GROUP_MESSAGE"
    static let typeAck = "ACK"
}

struct ProtocolMessage: Codable {
    let v: Int
    let type: String
    let msgId: String
    let from: String
    let to: String?
    let groupId: String?
    let ts: Int64
    let body: String?
    let fileName: String?
    let fileSize: Int64?
    let mime: String?
    let duration: Int?
    let chunkIndex: Int?
    let totalChunks: Int?
    let data: String?
    let ackFor: String?
    let hopCount: Int?
    let ttl: Int?
}

enum ProtocolCodec {
    static func createTextMessage(msgId: String, from: String, to: String, body: String) -> String {
        let message = ProtocolMessage(
            v: 1,
            type: ProtocolType.typeText,
            msgId: msgId,
            from: from,
            to: to,
            groupId: nil,
            ts: Int64(Date().timeIntervalSince1970 * 1000),
            body: body,
            fileName: nil,
            fileSize: nil,
            mime: nil,
            duration: nil,
            chunkIndex: nil,
            totalChunks: nil,
            data: nil,
            ackFor: nil,
            hopCount: nil,
            ttl: nil
        )
        return encode(message)
    }

    static func createAckMessage(msgId: String, from: String, to: String, ackFor: String) -> String {
        let message = ProtocolMessage(
            v: 1,
            type: ProtocolType.typeAck,
            msgId: msgId,
            from: from,
            to: to,
            groupId: nil,
            ts: Int64(Date().timeIntervalSince1970 * 1000),
            body: nil,
            fileName: nil,
            fileSize: nil,
            mime: nil,
            duration: nil,
            chunkIndex: nil,
            totalChunks: nil,
            data: nil,
            ackFor: ackFor,
            hopCount: nil,
            ttl: nil
        )
        return encode(message)
    }

    static func parse(_ json: String) -> ProtocolMessage? {
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(ProtocolMessage.self, from: data)
    }

    private static func encode(_ message: ProtocolMessage) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.withoutEscapingSlashes]
        guard let data = try? encoder.encode(message) else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }
}
