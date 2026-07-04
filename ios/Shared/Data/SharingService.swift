@preconcurrency import FirebaseAuth
@preconcurrency import FirebaseFirestore
@preconcurrency import FirebaseStorage
import Foundation
import Observation

struct SharedRecipeSnapshot: Hashable {
    let title: String
    let notes: String
    let prepTime: String
    let cookTime: String
    let totalTime: String
    let servings: String
    let ingredients: String
    let instructions: String
    let photoUrls: [String]
    let videoUrl: String?
    let sourceUrl: String?
    let originalRawText: String
}

struct ButteryRecipeShare: Identifiable, Hashable {
    let id: String
    let fromUserId: String
    let fromUsername: String
    let fromDisplayName: String
    let fromProfilePhotoUrl: String?
    let toUserId: String
    let toUsername: String
    let recipe: SharedRecipeSnapshot
    var status: String
    let createdAt: Date?
}

@MainActor
@Observable
final class SharingService {
    private(set) var inbox: [ButteryRecipeShare] = []
    private(set) var isLoading = false
    var lastError: String?
    private var listener: ListenerRegistration?
    private var listeningUserId: String?

    var hasUnread: Bool { inbox.contains { $0.status == "pending" } }

    func listen(for userId: String?) {
        guard listeningUserId != userId else { return }
        listener?.remove()
        listener = nil
        listeningUserId = userId
        inbox = []
        guard let userId else { return }
        isLoading = true
        listener = Firestore.firestore().collection("recipeShares")
            .whereField("toUserId", isEqualTo: userId)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    guard let self else { return }
                    self.isLoading = false
                    if let error {
                        self.lastError = error.localizedDescription
                        return
                    }
                    self.inbox = snapshot?.documents.compactMap(Self.decode)
                        .filter { $0.status != "dismissed" }
                        .sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) } ?? []
                }
            }
    }

    func share(_ recipe: Recipe, from profile: ButteryUserProfile, toUsername rawUsername: String) async throws {
        let username = rawUsername.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !username.isEmpty else { throw SharingError.usernameRequired }
        let database = Firestore.firestore()
        let usernameDocument = try await database.collection("usernames").document(username).getDocument()
        guard let recipientId = (usernameDocument.data()?["userId"] as? String)
                ?? (usernameDocument.data()?["uid"] as? String) else {
            throw SharingError.userNotFound
        }
        guard recipientId != profile.uid else { throw SharingError.cannotShareWithSelf }
        let duplicate = try await database.collection("recipeShares")
            .whereField("fromUserId", isEqualTo: profile.uid)
            .getDocuments()
        let hasActiveDuplicate = duplicate.documents.contains {
            let data = $0.data()
            return data["toUserId"] as? String == recipientId
                && data["sourceRecipeId"] as? String == recipe.id
                && data["status"] as? String != "dismissed"
        }
        guard !hasActiveDuplicate else {
            throw SharingError.alreadyShared
        }
        let reference = database.collection("recipeShares").document()
        let media = try await uploadMedia(
            for: recipe,
            ownerId: profile.uid,
            shareId: reference.documentID
        )
        let snapshot: [String: Any] = [
            "title": recipe.title, "notes": recipe.notes, "prepTime": recipe.prepTime,
            "cookTime": recipe.cookTime, "totalTime": recipe.totalTime,
            "servings": recipe.servings, "ingredients": recipe.ingredients,
            "instructions": recipe.instructions,
            "photoUrls": media.photoUrls,
            "videoUrl": media.videoUrl ?? NSNull(),
            "sourceUrl": recipe.sourceUrl?.absoluteString ?? NSNull(),
            "originalRawText": recipe.originalRawText
        ]
        try await reference.setData([
            "shareId": reference.documentID, "sourceRecipeId": recipe.id,
            "fromUserId": profile.uid, "fromUsername": profile.username,
            "fromDisplayName": profile.displayName,
            "fromProfilePhotoUrl": profile.profilePhotoUrl ?? NSNull(),
            "toUserId": recipientId, "toUsername": username,
            "recipeSnapshot": snapshot, "message": "", "status": "pending",
            "createdAt": FieldValue.serverTimestamp(), "updatedAt": FieldValue.serverTimestamp(),
            "schemaVersion": 1
        ])
    }

    func matchingUsernames(prefix rawPrefix: String) async throws -> [String] {
        let prefix = rawPrefix.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !prefix.isEmpty else { return [] }
        let snapshot = try await Firestore.firestore().collection("usernames")
            .order(by: FieldPath.documentID())
            .start(at: [prefix])
            .end(at: [prefix + "\u{f8ff}"])
            .limit(to: 8)
            .getDocuments()
        return snapshot.documents.map(\.documentID)
    }

    private func uploadMedia(
        for recipe: Recipe,
        ownerId: String,
        shareId: String
    ) async throws -> (photoUrls: [String], videoUrl: String?) {
        let folder = Storage.storage().reference()
            .child("users/\(ownerId)/recipes/\(recipe.id)/shares/\(shareId)")
        var photos: [String] = []
        for (index, url) in recipe.photoUrls.enumerated() {
            let data: Data
            do {
                data = try await mediaData(at: url, maximumSize: 15 * 1_024 * 1_024)
            } catch {
                // Old recipes can retain a URL for media that has since been removed. A missing
                // attachment should not prevent the recipe text and remaining media from sharing.
                continue
            }
            // Upload failures are intentionally not swallowed. The sender needs to know when
            // Firebase Storage or its rules are not configured, rather than send a blank share.
            let metadata = StorageMetadata()
            metadata.contentType = "image/jpeg"
            let item = folder.child("photo_\(index).jpg")
            _ = try await item.putDataAsync(data, metadata: metadata)
            photos.append((try await item.downloadURL()).absoluteString)
        }
        var video: String?
        if let url = recipe.videoUrl {
            let data: Data?
            do {
                data = try await mediaData(at: url, maximumSize: 50 * 1_024 * 1_024)
            } catch SharingError.videoTooLarge {
                throw SharingError.videoTooLarge
            } catch {
                data = nil
            }
            if let data {
                let metadata = StorageMetadata()
                metadata.contentType = "video/mp4"
                let item = folder.child("video.mp4")
                _ = try await item.putDataAsync(data, metadata: metadata)
                video = (try await item.downloadURL()).absoluteString
            }
        }
        return (photos, video)
    }

    private func mediaData(at url: URL, maximumSize: Int64) async throws -> Data {
        if url.isFileURL {
            return try Data(contentsOf: url)
        }
        if url.scheme?.lowercased() == "gs" {
            return try await Storage.storage().reference(forURL: url.absoluteString)
                .data(maxSize: maximumSize)
        }
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        guard data.count <= maximumSize else { throw SharingError.videoTooLarge }
        return data
    }

    func mark(_ share: ButteryRecipeShare, status: String) async throws {
        try await Firestore.firestore().collection("recipeShares").document(share.id).updateData([
            "status": status, "updatedAt": FieldValue.serverTimestamp()
        ])
    }

    private static func decode(_ document: QueryDocumentSnapshot) -> ButteryRecipeShare? {
        let data = document.data()
        guard let fromId = data["fromUserId"] as? String,
              let fromUsername = data["fromUsername"] as? String,
              let toId = data["toUserId"] as? String,
              let toUsername = data["toUsername"] as? String,
              let value = data["recipeSnapshot"] as? [String: Any],
              let title = value["title"] as? String else { return nil }
        let snapshot = SharedRecipeSnapshot(
            title: title, notes: value["notes"] as? String ?? "",
            prepTime: value["prepTime"] as? String ?? "", cookTime: value["cookTime"] as? String ?? "",
            totalTime: value["totalTime"] as? String ?? "", servings: value["servings"] as? String ?? "",
            ingredients: value["ingredients"] as? String ?? "", instructions: value["instructions"] as? String ?? "",
            photoUrls: value["photoUrls"] as? [String] ?? [], videoUrl: value["videoUrl"] as? String,
            sourceUrl: value["sourceUrl"] as? String, originalRawText: value["originalRawText"] as? String ?? ""
        )
        return ButteryRecipeShare(
            id: document.documentID, fromUserId: fromId, fromUsername: fromUsername,
            fromDisplayName: data["fromDisplayName"] as? String ?? fromUsername,
            fromProfilePhotoUrl: data["fromProfilePhotoUrl"] as? String,
            toUserId: toId, toUsername: toUsername, recipe: snapshot,
            status: data["status"] as? String ?? "pending",
            createdAt: (data["createdAt"] as? Timestamp)?.dateValue()
        )
    }
}

enum SharingError: LocalizedError {
    case usernameRequired, userNotFound, cannotShareWithSelf, alreadyShared, videoTooLarge
    var errorDescription: String? {
        switch self {
        case .usernameRequired: "Enter a recipient username."
        case .userNotFound: "No Buttery user was found with that username."
        case .cannotShareWithSelf: "Choose another user."
        case .alreadyShared: "This recipe has already been shared with that user."
        case .videoTooLarge: "The shared video must be 50 MB or smaller."
        }
    }
}
