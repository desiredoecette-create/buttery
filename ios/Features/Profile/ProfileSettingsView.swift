import SwiftUI
import PhotosUI
import AVKit
@preconcurrency import FirebaseFirestore
@preconcurrency import FirebaseStorage

struct ProfileSettingsView: View {
    @Environment(AuthService.self) private var auth
    @Environment(SharingService.self) private var sharing
    @Environment(RecipeStore.self) private var recipes
    @Environment(AppState.self) private var appState
    @Environment(SocialProfileService.self) private var social
    @State private var displayName = ""
    @State private var profileSummary = ""
    @State private var message: String?
    @State private var isSavingProfile = false
    @State private var isSavingPhoto = false
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var selectedPhotoPreview: UIImage?
    @State private var isShowingDeleteConfirmation = false
    @State private var isDeletingAccount = false

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Color(hex: 0x293A43), ButteryTheme.navy],
                center: .top, startRadius: 20, endRadius: 750
            ).ignoresSafeArea()

            ScrollView {
                profileCard
                .padding(18)
            }
        }
        .navigationTitle("Your Profile")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            displayName = auth.profile?.displayName ?? ""
            if let profile = auth.profile {
                profileSummary = social.loadedProfiles[profile.uid]?.bio ?? "A warm little recipe book from @\(profile.username)."
                Task { await social.loadProfile(userId: profile.uid) }
            }
        }
        .onChange(of: social.loadedProfiles[auth.profile?.uid ?? ""]?.bio) { _, value in
            if let value { profileSummary = value }
        }
    }

    private var profileCard: some View {
        VStack(spacing: 15) {
            avatar
            if !appState.isGuest {
                PhotosPicker(selection: $selectedPhoto, matching: .images) {
                    Label("Edit Profile Picture", systemImage: "camera")
                }
                .buttonStyle(.bordered)
                .disabled(isSavingPhoto)
                .onChange(of: selectedPhoto) { _, item in
                    guard let item else { return }
                    isSavingPhoto = true
                    Task {
                        do {
                            guard let data = try await item.loadTransferable(type: Data.self) else {
                                throw PhotoSelectionError.unreadable
                            }
                            guard let image = UIImage(data: data) else {
                                throw PhotoSelectionError.unreadable
                            }
                            selectedPhotoPreview = image
                            try await auth.updateProfilePhoto(data: data)
                            if let profile = auth.profile {
                                social.updateCachedProfilePhoto(
                                    userId: profile.uid,
                                    profilePhotoUrl: profile.profilePhotoUrl
                                )
                                try? await social.updateProfileSummary(
                                    userId: profile.uid,
                                    username: profile.username,
                                    displayName: profile.displayName,
                                    profilePhotoUrl: profile.profilePhotoUrl,
                                    bio: profileSummary
                                )
                            }
                            message = "Profile picture saved."
                        } catch {
                            selectedPhotoPreview = nil
                            message = error.localizedDescription
                        }
                        isSavingPhoto = false
                    }
                }
                if isSavingPhoto {
                    ProgressView("Saving profile picture…")
                        .font(.caption)
                }
            }
            if let profile = auth.profile {
                profileField("Username", value: "@\(profile.username)", help: "Usernames cannot be changed.")
                VStack(alignment: .leading, spacing: 6) {
                    Text("Display name").font(.subheadline.weight(.semibold))
                    TextField("Display name", text: $displayName)
                        .padding(13).background(.white.opacity(0.7), in: RoundedRectangle(cornerRadius: 11))
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("Short profile summary").font(.subheadline.weight(.semibold))
                    TextField("Tell people about your kitchen…", text: $profileSummary, axis: .vertical)
                        .lineLimit(3...5)
                        .padding(13)
                        .background(.white.opacity(0.7), in: RoundedRectangle(cornerRadius: 11))
                        .onChange(of: profileSummary) { _, value in
                            if value.count > 160 { profileSummary = String(value.prefix(160)) }
                        }
                    Text("\(profileSummary.count)/160")
                        .font(.caption)
                        .foregroundStyle(ButteryTheme.charcoal.opacity(0.56))
                }
                profileField("Email", value: profile.email)
                Button {
                    saveProfile()
                } label: {
                    Label("Save Profile", systemImage: "checkmark")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(ButteryTheme.butter)
                .foregroundStyle(ButteryTheme.navy)
                .disabled(isSavingProfile || isSavingPhoto)
                if isSavingProfile {
                    ProgressView("Saving display name…")
                        .font(.caption)
                }
                if let message {
                    Text(message).font(.footnote).foregroundStyle(ButteryTheme.charcoal.opacity(0.75))
                }
                Button("Sign Out", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) {
                    try? auth.signOut()
                    sharing.listen(for: nil)
                    appState.path.removeAll()
                }
                Button("Privacy & Support", systemImage: "hand.raised") {
                    appState.path.append(.privacySupport)
                }
                Divider()
                VStack(alignment: .leading, spacing: 8) {
                    Text("Delete Account")
                        .font(.headline)
                    Text("Permanently deletes your Buttery account, profile, recipes on this device, shared-recipe records, and uploaded media. This cannot be undone.")
                        .font(.caption)
                        .foregroundStyle(ButteryTheme.charcoal.opacity(0.68))
                    Button("Delete My Account", systemImage: "trash", role: .destructive) {
                        isShowingDeleteConfirmation = true
                    }
                    .disabled(isDeletingAccount)
                }
            } else if appState.isGuest {
                Text("Guest recipes stay on this device. Sign in to create a profile or share recipes.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(ButteryTheme.charcoal.opacity(0.7))
                Button("Sign In or Create Account") {
                    appState.isGuest = false
                    appState.path.removeAll()
                }
                .buttonStyle(.borderedProminent)
                .tint(ButteryTheme.butter)
                .foregroundStyle(ButteryTheme.navy)
                Button("Privacy & Support", systemImage: "hand.raised") {
                    appState.path.append(.privacySupport)
                }
            }
        }
        .foregroundStyle(ButteryTheme.charcoal)
        .padding(22)
        .background(ButteryTheme.cream, in: RoundedRectangle(cornerRadius: 22))
        .confirmationDialog(
            "Permanently delete your account?",
            isPresented: $isShowingDeleteConfirmation,
            titleVisibility: .visible
        ) {
            Button("Delete Account Permanently", role: .destructive) {
                deleteAccount()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Your account and associated Buttery data will be permanently deleted. This action cannot be undone.")
        }
    }

    private var avatar: some View {
        Group {
            if let selectedPhotoPreview {
                Image(uiImage: selectedPhotoPreview)
                    .resizable()
                    .scaledToFill()
            } else if let text = auth.profile?.profilePhotoUrl, let url = URL(string: text) {
                ButteryRemoteImage(url: url, maxPixelDimension: 360) {
                    ProgressView()
                        .tint(ButteryTheme.butter)
                }
            } else {
                Image(systemName: "person.fill")
                    .font(.system(size: 48)).foregroundStyle(ButteryTheme.navy)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(ButteryTheme.butter)
            }
        }
        .frame(width: 104, height: 104).clipShape(Circle())
    }

    private func profileField(_ label: String, value: String, help: String? = nil) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label).font(.subheadline.weight(.semibold))
            Text(value).frame(maxWidth: .infinity, alignment: .leading)
                .padding(13).background(.white.opacity(0.48), in: RoundedRectangle(cornerRadius: 11))
            if let help { Text(help).font(.caption).foregroundStyle(ButteryTheme.charcoal.opacity(0.6)) }
        }
    }

    private func saveProfile() {
        isSavingProfile = true
        Task {
            do {
                try await auth.updateProfile(displayName: displayName)
                if let profile = auth.profile {
                    try await social.updateProfileSummary(
                        userId: profile.uid,
                        username: profile.username,
                        displayName: displayName,
                        profilePhotoUrl: profile.profilePhotoUrl,
                        bio: profileSummary
                    )
                }
                message = "Profile saved."
            } catch { message = error.localizedDescription }
            isSavingProfile = false
        }
    }

    private func deleteAccount() {
        isDeletingAccount = true
        message = nil
        Task {
            do {
                try await auth.deleteAccount()
                recipes.deleteCurrentOwnerData()
                sharing.listen(for: nil)
                appState.path.removeAll()
            } catch {
                message = error.localizedDescription
            }
            isDeletingAccount = false
        }
    }

}

private enum PhotoSelectionError: LocalizedError {
    case unreadable
    var errorDescription: String? { "That picture could not be opened. Please choose another." }
}

enum ButteryProfileMode: Hashable {
    case own
    case publicUser(userId: String)
}

struct ButteryPublicProfile: Identifiable, Hashable {
    let id: String
    var username: String
    var displayName: String
    var profilePhotoUrl: String?
    var bio: String
    var websiteUrl: String?
    var subscriberCount: Int
    var recipeCount: Int
    var publicRecipeCount: Int
}

struct ButterySubscriberProfile: Identifiable, Hashable {
    let id: String
    var username: String
    var displayName: String
    var profilePhotoUrl: String?
}

@MainActor
@Observable
final class SocialProfileService {
    private let database = Firestore.firestore()
    private(set) var loadedProfiles: [String: ButteryPublicProfile] = [:]
    private(set) var publicRecipesByOwner: [String: [Recipe]] = [:]
    private(set) var communityPublicRecipes: [Recipe] = []
    private(set) var subscribedCreatorIds: Set<String> = []
    private(set) var subscribersByCreator: [String: [ButterySubscriberProfile]] = [:]
    private(set) var likedRecipeIds: Set<String> = []
    var lastError: String?

    func profile(for authProfile: ButteryUserProfile?, localRecipeCount: Int, localPublicRecipeCount: Int) -> ButteryPublicProfile? {
        guard let authProfile else { return nil }
        return ButteryPublicProfile(
            id: authProfile.uid,
            username: authProfile.username,
            displayName: authProfile.displayName,
            profilePhotoUrl: authProfile.profilePhotoUrl,
            bio: loadedProfiles[authProfile.uid]?.bio ?? "A warm little recipe book from @\(authProfile.username).",
            websiteUrl: loadedProfiles[authProfile.uid]?.websiteUrl,
            subscriberCount: loadedProfiles[authProfile.uid]?.subscriberCount ?? 0,
            recipeCount: localRecipeCount,
            publicRecipeCount: localPublicRecipeCount
        )
    }

    func loadProfile(userId: String) async {
        do {
            let document = try await database.collection("publicProfiles").document(userId).getDocument()
            guard document.exists else {
                if loadedProfiles[userId] == nil {
                    loadedProfiles[userId] = ButteryPublicProfile(
                        id: userId,
                        username: "chef",
                        displayName: "Buttery Chef",
                        profilePhotoUrl: nil,
                        bio: "A premium digital recipe book.",
                        websiteUrl: nil,
                        subscriberCount: 0,
                        recipeCount: 0,
                        publicRecipeCount: publicRecipesByOwner[userId]?.count ?? 0
                    )
                }
                return
            }
            let data = document.data() ?? [:]
            let profile = ButteryPublicProfile(
                id: userId,
                username: data["username"] as? String ?? "chef",
                displayName: data["displayName"] as? String ?? data["username"] as? String ?? "Buttery Chef",
                profilePhotoUrl: data["profilePhotoUrl"] as? String,
                bio: data["bio"] as? String ?? "A premium digital recipe book.",
                websiteUrl: data["websiteUrl"] as? String,
                subscriberCount: data["subscriberCount"] as? Int ?? 0,
                recipeCount: data["recipeCount"] as? Int ?? 0,
                publicRecipeCount: data["publicRecipeCount"] as? Int ?? 0
            )
            loadedProfiles[userId] = profile
        } catch {
            lastError = error.localizedDescription
            if loadedProfiles[userId] == nil {
                loadedProfiles[userId] = ButteryPublicProfile(
                    id: userId,
                    username: "chef",
                    displayName: "Buttery Chef",
                    profilePhotoUrl: nil,
                    bio: "A premium digital recipe book.",
                    websiteUrl: nil,
                    subscriberCount: 0,
                    recipeCount: 0,
                    publicRecipeCount: publicRecipesByOwner[userId]?.count ?? 0
                )
            }
        }
    }

    func cacheProfile(
        userId: String,
        username: String,
        displayName: String,
        profilePhotoUrl: String?
    ) {
        loadedProfiles[userId] = ButteryPublicProfile(
            id: userId,
            username: username,
            displayName: displayName,
            profilePhotoUrl: profilePhotoUrl,
            bio: "A warm little recipe book from @\(username).",
            websiteUrl: nil,
            subscriberCount: loadedProfiles[userId]?.subscriberCount ?? 0,
            recipeCount: loadedProfiles[userId]?.recipeCount ?? 0,
            publicRecipeCount: publicRecipesByOwner[userId]?.count ?? loadedProfiles[userId]?.publicRecipeCount ?? 0
        )
    }

    func updateProfileSummary(
        userId: String,
        username: String,
        displayName: String,
        profilePhotoUrl: String?,
        bio: String
    ) async throws {
        let cleanBio = bio.trimmingCharacters(in: .whitespacesAndNewlines)
        let summary = cleanBio.isEmpty ? "A warm little recipe book from @\(username)." : cleanBio
        try await database.collection("publicProfiles").document(userId).setData([
            "uid": userId,
            "username": username,
            "displayName": displayName,
            "profilePhotoUrl": profilePhotoUrl ?? "",
            "bio": summary,
            "updatedAt": FieldValue.serverTimestamp(),
            "schemaVersion": 1
        ], merge: true)
        loadedProfiles[userId] = ButteryPublicProfile(
            id: userId,
            username: username,
            displayName: displayName,
            profilePhotoUrl: profilePhotoUrl,
            bio: summary,
            websiteUrl: loadedProfiles[userId]?.websiteUrl,
            subscriberCount: loadedProfiles[userId]?.subscriberCount ?? 0,
            recipeCount: loadedProfiles[userId]?.recipeCount ?? 0,
            publicRecipeCount: loadedProfiles[userId]?.publicRecipeCount ?? 0
        )
    }

    func updateCachedProfilePhoto(userId: String, profilePhotoUrl: String?) {
        guard var profile = loadedProfiles[userId] else { return }
        profile.profilePhotoUrl = profilePhotoUrl
        loadedProfiles[userId] = profile
    }

    func loadPublicRecipes(ownerId: String) async {
        do {
            let snapshot = try await database.collection("recipes")
                .whereField("ownerId", isEqualTo: ownerId)
                .whereField("visibility", isEqualTo: RecipeVisibility.public.rawValue)
                .getDocuments()
            publicRecipesByOwner[ownerId] = snapshot.documents.compactMap(Self.decodeRecipe)
                .sorted(by: Self.publicRecipeSort)
        } catch {
            lastError = error.localizedDescription
            publicRecipesByOwner[ownerId] = []
        }
    }

    func loadCommunityPublicRecipes() async {
        do {
            let snapshot = try await database.collection("recipes")
                .whereField("visibility", isEqualTo: RecipeVisibility.public.rawValue)
                .getDocuments()
            communityPublicRecipes = snapshot.documents.compactMap(Self.decodeRecipe)
                .sorted(by: Self.publicRecipeSort)
            let ownerIds = Set(communityPublicRecipes.map(\.ownerId).filter { !$0.isEmpty })
            for ownerId in ownerIds where loadedProfiles[ownerId] == nil {
                await loadProfile(userId: ownerId)
            }
            lastError = nil
        } catch {
            lastError = error.localizedDescription
            communityPublicRecipes = []
        }
    }

    func refreshLikedRecipes(for userId: String?) async {
        guard let userId else {
            likedRecipeIds = []
            return
        }
        do {
            let snapshot = try await database.collection("recipeLikes")
                .whereField("userId", isEqualTo: userId)
                .getDocuments()
            likedRecipeIds = Set(snapshot.documents.compactMap { $0.data()["recipeId"] as? String })
        } catch {
            lastError = error.localizedDescription
        }
    }

    func isLiked(recipeId: String) -> Bool {
        likedRecipeIds.contains(recipeId)
    }

    func likeCount(for recipe: Recipe) -> Int {
        if let communityRecipe = communityPublicRecipes.first(where: { $0.id == recipe.id }) {
            return max(0, communityRecipe.likeCount ?? 0)
        }
        if let ownerRecipe = publicRecipesByOwner[recipe.ownerId].flatMap({ recipes in
            recipes.first(where: { $0.id == recipe.id })
        }) {
            return max(0, ownerRecipe.likeCount ?? 0)
        }
        return max(0, recipe.likeCount ?? 0)
    }

    func toggleLike(recipe: Recipe, userId: String) async {
        let wasLiked = likedRecipeIds.contains(recipe.id)
        let delta = wasLiked ? -1 : 1
        if wasLiked {
            likedRecipeIds.remove(recipe.id)
        } else {
            likedRecipeIds.insert(recipe.id)
        }
        updateCachedLikeCount(recipeId: recipe.id, by: delta)

        let likeId = "\(recipe.id)_\(userId)"
        do {
            if wasLiked {
                try await database.collection("recipeLikes").document(likeId).delete()
            } else {
                try await database.collection("recipeLikes").document(likeId).setData([
                    "recipeLikeId": likeId,
                    "recipeId": recipe.id,
                    "userId": userId,
                    "ownerId": recipe.ownerId,
                    "createdAt": FieldValue.serverTimestamp(),
                    "schemaVersion": 1
                ])
            }
        } catch {
            if wasLiked {
                likedRecipeIds.insert(recipe.id)
            } else {
                likedRecipeIds.remove(recipe.id)
            }
            updateCachedLikeCount(recipeId: recipe.id, by: -delta)
            lastError = error.localizedDescription
            return
        }

        do {
            try await database.collection("recipes").document(recipe.id).updateData([
                "likeCount": FieldValue.increment(Int64(delta))
            ])
        } catch {
            lastError = error.localizedDescription
        }
    }

    func refreshSubscriptions(for subscriberId: String?) async {
        guard let subscriberId else {
            subscribedCreatorIds = []
            return
        }
        do {
            let snapshot = try await database.collection("subscriptions")
                .whereField("subscriberUserId", isEqualTo: subscriberId)
                .getDocuments()
            subscribedCreatorIds = Set(snapshot.documents.compactMap { $0.data()["creatorUserId"] as? String })
            for creatorId in subscribedCreatorIds {
                if var profile = loadedProfiles[creatorId], profile.subscriberCount == 0 {
                    profile.subscriberCount = 1
                    loadedProfiles[creatorId] = profile
                }
            }
        } catch {
            lastError = error.localizedDescription
        }
    }

    func loadSubscribers(for creatorId: String) async {
        do {
            let snapshot = try await database.collection("subscriptions")
                .whereField("creatorUserId", isEqualTo: creatorId)
                .getDocuments()
            let subscribers = snapshot.documents.compactMap { document -> ButterySubscriberProfile? in
                let data = document.data()
                guard let id = data["subscriberUserId"] as? String else { return nil }
                return ButterySubscriberProfile(
                    id: id,
                    username: data["subscriberUsername"] as? String ?? "chef",
                    displayName: data["subscriberDisplayName"] as? String ?? data["subscriberUsername"] as? String ?? "Buttery Chef",
                    profilePhotoUrl: data["subscriberProfilePhotoUrl"] as? String
                )
            }
            subscribersByCreator[creatorId] = subscribers.sorted { $0.username < $1.username }
            if var profile = loadedProfiles[creatorId] {
                profile.subscriberCount = subscribers.count
                loadedProfiles[creatorId] = profile
            }
        } catch {
            lastError = error.localizedDescription
        }
    }

    func subscribe(currentUser: ButteryUserProfile, creator: ButteryPublicProfile) async {
        guard currentUser.uid != creator.id else { return }
        let id = "\(currentUser.uid)_\(creator.id)"
        let wasSubscribed = subscribedCreatorIds.contains(creator.id)
        if !wasSubscribed {
            subscribedCreatorIds.insert(creator.id)
            incrementSubscriberCount(for: creator.id, by: 1)
        }
        do {
            try await database.collection("subscriptions").document(id).setData([
                "subscriptionId": id,
                "subscriberUserId": currentUser.uid,
                "subscriberUsername": currentUser.username,
                "subscriberDisplayName": currentUser.displayName,
                "subscriberProfilePhotoUrl": currentUser.profilePhotoUrl ?? "",
                "creatorUserId": creator.id,
                "creatorUsername": creator.username,
                "createdAt": FieldValue.serverTimestamp(),
                "schemaVersion": 1
            ])
        } catch {
            if !wasSubscribed {
                subscribedCreatorIds.remove(creator.id)
                incrementSubscriberCount(for: creator.id, by: -1)
            }
            lastError = error.localizedDescription
        }
    }

    func unsubscribe(currentUserId: String, creator: ButteryPublicProfile) async {
        let id = "\(currentUserId)_\(creator.id)"
        let wasSubscribed = subscribedCreatorIds.contains(creator.id)
        if wasSubscribed {
            subscribedCreatorIds.remove(creator.id)
            incrementSubscriberCount(for: creator.id, by: -1)
        }
        do {
            try await database.collection("subscriptions").document(id).delete()
        } catch {
            if wasSubscribed {
                subscribedCreatorIds.insert(creator.id)
                incrementSubscriberCount(for: creator.id, by: 1)
            }
            lastError = error.localizedDescription
        }
    }

    private func incrementSubscriberCount(for creatorId: String, by amount: Int) {
        if var profile = loadedProfiles[creatorId] {
            profile.subscriberCount = max(0, profile.subscriberCount + amount)
            loadedProfiles[creatorId] = profile
        }
    }

    private func updateCachedLikeCount(recipeId: String, by amount: Int) {
        func updated(_ recipe: Recipe) -> Recipe {
            var copy = recipe
            copy.likeCount = max(0, (copy.likeCount ?? 0) + amount)
            return copy
        }

        communityPublicRecipes = communityPublicRecipes
            .map { $0.id == recipeId ? updated($0) : $0 }
            .sorted(by: Self.publicRecipeSort)

        for ownerId in publicRecipesByOwner.keys {
            publicRecipesByOwner[ownerId] = (publicRecipesByOwner[ownerId] ?? [])
                .map { $0.id == recipeId ? updated($0) : $0 }
                .sorted(by: Self.publicRecipeSort)
        }
    }

    private static func publicRecipeSort(_ lhs: Recipe, _ rhs: Recipe) -> Bool {
        let lhsLikes = lhs.likeCount ?? 0
        let rhsLikes = rhs.likeCount ?? 0
        if lhsLikes != rhsLikes { return lhsLikes > rhsLikes }
        return lhs.publicPublishedAt ?? lhs.updatedAt > rhs.publicPublishedAt ?? rhs.updatedAt
    }

    func publish(_ recipe: Recipe, profile: ButteryUserProfile) async throws {
        var data = Self.encodePublicRecipe(recipe)
        let media = try await uploadPublicMedia(for: recipe, ownerId: profile.uid)
        data["visibility"] = RecipeVisibility.public.rawValue
        data["ownerId"] = profile.uid
        data["ownerUsername"] = profile.username
        data["ownerDisplayName"] = profile.displayName
        data["ownerProfilePhotoUrl"] = profile.profilePhotoUrl ?? ""
        data["photoUrls"] = media.photoUrls
        data["thumbnailUrl"] = media.photoUrls.first ?? ""
        data["videoUrl"] = media.videoUrl ?? ""
        data["videoUrls"] = [media.videoUrl].compactMap { $0 }
        data["publicPublishedAt"] = FieldValue.serverTimestamp()
        data["schemaVersion"] = 1
        try await database.collection("recipes").document(recipe.id).setData(data, merge: true)
        try await database.collection("publicProfiles").document(profile.uid).setData([
            "uid": profile.uid,
            "username": profile.username,
            "displayName": profile.displayName,
            "profilePhotoUrl": profile.profilePhotoUrl ?? "",
            "bio": "A warm little recipe book from @\(profile.username).",
            "websiteUrl": "",
            "updatedAt": FieldValue.serverTimestamp(),
            "schemaVersion": 1
        ], merge: true)
        await loadPublicRecipes(ownerId: profile.uid)
    }

    private func uploadPublicMedia(
        for recipe: Recipe,
        ownerId: String
    ) async throws -> (photoUrls: [String], videoUrl: String?) {
        let folder = Storage.storage().reference()
            .child("users/\(ownerId)/recipes/\(recipe.id)/public")
        var photos: [String] = []
        for (index, url) in recipe.photoUrls.enumerated() {
            do {
                let data = try await mediaData(at: url, maximumSize: 15 * 1_024 * 1_024)
                let metadata = StorageMetadata()
                metadata.contentType = "image/jpeg"
                let item = folder.child("photo_\(index).jpg")
                _ = try await item.putDataAsync(data, metadata: metadata)
                photos.append((try await item.downloadURL()).absoluteString)
            } catch {
                continue
            }
        }

        var video: String?
        if let url = recipe.videoUrl {
            do {
                let data = try await mediaData(at: url, maximumSize: 50 * 1_024 * 1_024)
                let metadata = StorageMetadata()
                metadata.contentType = "video/mp4"
                let item = folder.child("video.mp4")
                _ = try await item.putDataAsync(data, metadata: metadata)
                video = (try await item.downloadURL()).absoluteString
            } catch {
                // Keep publishing text/photos even if a stale local video is no longer readable.
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
        guard data.count <= maximumSize else { throw URLError(.dataLengthExceedsMaximum) }
        return data
    }

    func unpublish(recipeId: String, ownerId: String) async throws {
        try await database.collection("recipes").document(recipeId).updateData([
            "visibility": RecipeVisibility.private.rawValue,
            "updatedAt": FieldValue.serverTimestamp()
        ])
        await loadPublicRecipes(ownerId: ownerId)
    }

    private static func decodeRecipe(_ document: QueryDocumentSnapshot) -> Recipe? {
        let data = document.data()
        guard let rawTitle = data["title"] as? String else { return nil }
        let title = RecipeImporter.cleanImportedRecipeField(rawTitle)
        let photoUrls = (data["photoUrls"] as? [String] ?? []).compactMap(URL.init(string:))
        let videoUrls = (data["videoUrls"] as? [String] ?? []).compactMap(URL.init(string:))
        let updatedAt = (data["updatedAt"] as? Timestamp)?.dateValue().millisecondsSince1970
            ?? data["updatedAt"] as? Int64 ?? Date.now.millisecondsSince1970
        let publishedAt = (data["publicPublishedAt"] as? Timestamp)?.dateValue().millisecondsSince1970
        return Recipe(
            id: document.documentID,
            ownerId: data["ownerId"] as? String ?? "",
            title: title,
            notes: RecipeImporter.cleanImportedRecipeField(data["notes"] as? String ?? ""),
            prepTime: data["prepTime"] as? String ?? "",
            cookTime: data["cookTime"] as? String ?? "",
            totalTime: data["totalTime"] as? String ?? "",
            servings: data["servings"] as? String ?? "",
            ingredients: RecipeImporter.cleanImportedRecipeField(data["ingredients"] as? String ?? ""),
            instructions: RecipeImporter.cleanImportedRecipeField(data["instructions"] as? String ?? ""),
            photoUrls: photoUrls,
            videoUrl: videoUrls.first ?? (data["videoUrl"] as? String).flatMap(URL.init(string:)),
            sourceUrl: (data["sourceUrl"] as? String).flatMap(URL.init(string:)),
            originalRawText: RecipeImporter.cleanImportedRecipeField(data["originalRawText"] as? String ?? ""),
            albumId: data["albumId"] as? String,
            isFavorite: false,
            createdAt: data["createdAt"] as? Int64 ?? updatedAt,
            updatedAt: updatedAt,
            visibility: .public,
            ownerUsername: data["ownerUsername"] as? String,
            ownerDisplayName: data["ownerDisplayName"] as? String,
            ownerProfilePhotoUrl: data["ownerProfilePhotoUrl"] as? String,
            videoUrls: videoUrls,
            thumbnailUrl: (data["thumbnailUrl"] as? String).flatMap(URL.init(string:)),
            publicPublishedAt: publishedAt,
            saveCount: data["saveCount"] as? Int,
            likeCount: data["likeCount"] as? Int,
            viewCount: data["viewCount"] as? Int,
            ratingAverage: data["ratingAverage"] as? Double,
            ratingCount: data["ratingCount"] as? Int,
            originalCreatorId: data["originalCreatorId"] as? String,
            originalCreatorUsername: data["originalCreatorUsername"] as? String
        )
    }

    private static func encodePublicRecipe(_ recipe: Recipe) -> [String: Any] {
        [
            "id": recipe.id,
            "title": RecipeImporter.cleanImportedRecipeField(recipe.title),
            "notes": RecipeImporter.cleanImportedRecipeField(recipe.notes),
            "prepTime": recipe.prepTime,
            "cookTime": recipe.cookTime,
            "totalTime": recipe.totalTime,
            "servings": recipe.servings,
            "ingredients": RecipeImporter.cleanImportedRecipeField(recipe.ingredients),
            "instructions": RecipeImporter.cleanImportedRecipeField(recipe.instructions),
            "photoUrls": recipe.photoUrls.map(\.absoluteString),
            "videoUrl": recipe.videoUrl?.absoluteString ?? "",
            "sourceUrl": recipe.sourceUrl?.absoluteString ?? "",
            "originalRawText": RecipeImporter.cleanImportedRecipeField(recipe.originalRawText),
            "albumId": recipe.albumId ?? "",
            "updatedAt": FieldValue.serverTimestamp(),
            "saveCount": recipe.saveCount ?? 0,
            "likeCount": recipe.likeCount ?? 0,
            "viewCount": recipe.viewCount ?? 0,
            "ratingAverage": recipe.ratingAverage ?? 0,
            "ratingCount": recipe.ratingCount ?? 0,
            "originalCreatorId": recipe.originalCreatorId ?? "",
            "originalCreatorUsername": recipe.originalCreatorUsername ?? ""
        ]
    }
}

struct ButteryProfileView: View {
    @Environment(AuthService.self) private var auth
    @Environment(RecipeStore.self) private var store
    @Environment(SocialProfileService.self) private var social
    @Environment(SharingService.self) private var sharing
    @Environment(AppState.self) private var appState
    let mode: ButteryProfileMode
    @State private var selectedTab: ProfileRecipeTab = .public
    @State private var selectedRecipe: Recipe?
    @State private var isManagingPublicRecipes = false
    @State private var isShowingSubscribers = false
    @State private var profileActionMessage: String?

    private var isOwn: Bool { mode == .own }
    private var profileUserId: String? {
        switch mode {
        case .own: auth.profile?.uid
        case .publicUser(let userId): userId
        }
    }
    private var profile: ButteryPublicProfile? {
        switch mode {
        case .own:
            social.profile(
                for: auth.profile,
                localRecipeCount: store.recipes.count,
                localPublicRecipeCount: store.recipes.filter { ($0.visibility ?? .private) == .public }.count
            )
        case .publicUser(let userId):
            social.loadedProfiles[userId]
        }
    }
    private var recipes: [Recipe] {
        if isOwn {
            switch selectedTab {
            case .public: store.recipes.filter { ($0.visibility ?? .private) == .public }
                    .sorted { (lhs, rhs) in
                        let lhsLikes = lhs.likeCount ?? 0
                        let rhsLikes = rhs.likeCount ?? 0
                        if lhsLikes != rhsLikes { return lhsLikes > rhsLikes }
                        return lhs.updatedAt > rhs.updatedAt
                    }
            case .private: store.recipes.filter { ($0.visibility ?? .private) == .private }
            }
        } else if let profileUserId {
            social.publicRecipesByOwner[profileUserId] ?? []
        } else {
            []
        }
    }

    var body: some View {
        ZStack {
            RadialGradient(colors: [Color(hex: 0x293A43), ButteryTheme.navy], center: .top, startRadius: 20, endRadius: 850)
                .ignoresSafeArea()
            ScrollView {
                VStack(spacing: 18) {
                    if let profile {
                        profileHeader(profile)
                        if isOwn { tabBar }
                        RecipeProfileGrid(recipes: recipes) { selectedRecipe = $0 }
                    } else {
                        ProgressView("Opening profile…").tint(ButteryTheme.butter).padding(40)
                    }
                }
                .padding(16)
            }
        }
        .navigationTitle(isOwn ? "My Profile" : "Profile")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if let profileUserId {
                if !isOwn { await social.loadProfile(userId: profileUserId) }
                await social.loadPublicRecipes(ownerId: profileUserId)
                if isOwn { await social.loadSubscribers(for: profileUserId) }
            }
            await social.refreshSubscriptions(for: auth.profile?.uid)
            await social.refreshLikedRecipes(for: auth.profile?.uid)
        }
        .sheet(item: $selectedRecipe) { recipe in
            let feed = recipes.isEmpty ? [recipe] : recipes
            RecipePreviewFeedView(
                recipes: feed,
                startRecipeId: recipe.id,
                creator: profile,
                isOwnProfile: isOwn
            )
        }
        .sheet(isPresented: $isManagingPublicRecipes) {
            ManagePublicRecipesView()
        }
        .sheet(isPresented: $isShowingSubscribers) {
            if let profileUserId {
                SubscriberListView(creatorId: profileUserId)
            }
        }
    }

    private func profileHeader(_ profile: ButteryPublicProfile) -> some View {
        VStack(spacing: 16) {
            HStack(alignment: .top, spacing: 16) {
                ProfilePhoto(urlString: profile.profilePhotoUrl, size: 86)
                VStack(alignment: .leading, spacing: 5) {
                    Text(profile.displayName).font(.system(size: 29, weight: .semibold, design: .serif))
                    Text("@\(profile.username)").foregroundStyle(ButteryTheme.butter)
                    Text(profile.bio).font(.subheadline).foregroundStyle(ButteryTheme.charcoal.opacity(0.72))
                    if let website = profile.websiteUrl, let url = URL(string: website) {
                        Link(website, destination: url).font(.caption).foregroundStyle(ButteryTheme.herb)
                    }
                }
                Spacer()
            }
            HStack {
                if isOwn {
                    Button { isShowingSubscribers = true } label: {
                        stat("\(profile.subscriberCount)", "Subscribers")
                    }
                    .buttonStyle(.plain)
                } else {
                    stat("\(profile.subscriberCount)", "Subscribers")
                }
                stat("\(profile.recipeCount)", "Recipes")
                stat("\(profile.publicRecipeCount)", "Public")
            }
            HStack {
                if isOwn {
                    VStack(spacing: 10) {
                        Button {
                            appState.path.append(.settings)
                        } label: {
                            Text("Edit Profile")
                                .frame(width: 250)
                        }
                        .tint(ButteryTheme.butter)
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    VStack(spacing: 10) {
                        subscribeButton(profile)
                            .tint(ButteryTheme.herb)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .foregroundStyle(ButteryTheme.navy)
            if let profileActionMessage {
                Text(profileActionMessage)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(ButteryTheme.herb)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(20)
        .background(ButteryTheme.cream, in: RoundedRectangle(cornerRadius: 26))
        .foregroundStyle(ButteryTheme.charcoal)
    }

    private var tabBar: some View {
        HStack {
            ForEach([ProfileRecipeTab.public, .private], id: \.self) { tab in
                Button(tab.title) { selectedTab = tab }
                    .font(.caption.weight(.semibold))
                    .padding(.vertical, 9)
                    .frame(maxWidth: .infinity)
                    .background(selectedTab == tab ? ButteryTheme.butter : ButteryTheme.cream.opacity(0.15), in: Capsule())
                    .foregroundStyle(selectedTab == tab ? ButteryTheme.navy : ButteryTheme.cream)
            }
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.headline)
            Text(label).font(.caption).foregroundStyle(ButteryTheme.charcoal.opacity(0.64))
        }
        .frame(maxWidth: .infinity)
    }

    private func subscribeButton(_ profile: ButteryPublicProfile) -> some View {
        Button {
            guard let currentUser = auth.profile else { return }
            let wasSubscribed = social.subscribedCreatorIds.contains(profile.id)
            profileActionMessage = wasSubscribed
                ? "Unsubscribed from @\(profile.username)."
                : "Subscribed to @\(profile.username)."
            Task {
                if wasSubscribed {
                    await social.unsubscribe(currentUserId: currentUser.uid, creator: profile)
                } else {
                    await social.subscribe(currentUser: currentUser, creator: profile)
                    await sharing.sendSubscriptionNotification(from: currentUser, to: profile)
                }
            }
        } label: {
            Text(social.subscribedCreatorIds.contains(profile.id) ? "Unsubscribe" : "Subscribe")
                .frame(width: 250)
                .foregroundStyle(.white)
        }
    }
}

private enum ProfileRecipeTab: CaseIterable {
    case `public`, `private`
    var title: String {
        switch self {
        case .public: "Public"
        case .private: "Private"
        }
    }
}

private struct RecipeProfileGrid: View {
    let recipes: [Recipe]
    let onSelect: (Recipe) -> Void
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 3)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 4) {
            ForEach(recipes) { recipe in
                Button { onSelect(recipe) } label: {
                    RecipeGridTile(recipe: recipe)
                }
                .buttonStyle(.plain)
            }
        }
        .overlay {
            if recipes.isEmpty {
                Text("No recipes here yet.")
                    .foregroundStyle(ButteryTheme.cream.opacity(0.72))
                    .padding(.top, 60)
            }
        }
    }
}

private struct RecipeGridTile: View {
    let recipe: Recipe

    var body: some View {
        Color.clear
            .aspectRatio(0.72, contentMode: .fit)
            .overlay {
                ZStack(alignment: .bottomLeading) {
                    RecipeMediaThumbnail(recipe: recipe)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()
                    if recipe.videoUrl != nil {
                        Image(systemName: "play.fill")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(7)
                            .background(.black.opacity(0.42), in: Circle())
                            .padding(7)
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .contentShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct SubscriberListView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppState.self) private var appState
    @Environment(SocialProfileService.self) private var social
    let creatorId: String

    private var subscribers: [ButterySubscriberProfile] {
        social.subscribersByCreator[creatorId] ?? []
    }

    var body: some View {
        NavigationStack {
            List {
                if subscribers.isEmpty {
                    Text("No subscribers yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(subscribers) { subscriber in
                        Button {
                            social.cacheProfile(
                                userId: subscriber.id,
                                username: subscriber.username,
                                displayName: subscriber.displayName,
                                profilePhotoUrl: subscriber.profilePhotoUrl
                            )
                            dismiss()
                            appState.path.append(.publicProfile(userId: subscriber.id))
                        } label: {
                            HStack(spacing: 12) {
                                ProfilePhoto(urlString: subscriber.profilePhotoUrl, size: 34)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(subscriber.displayName)
                                        .font(.subheadline.weight(.semibold))
                                    Text("@\(subscriber.username)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Subscribers")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { await social.loadSubscribers(for: creatorId) }
        }
    }
}

private struct RecipeMediaThumbnail: View {
    let recipe: Recipe
    var body: some View {
        if let url = recipe.thumbnailUrl ?? recipe.photoUrls.first {
            CachedRecipeImage(url: url)
        } else if let videoURL = recipe.videoUrl ?? recipe.videoUrls?.first {
            VideoPlayer(player: AVPlayer(url: videoURL))
                .disabled(true)
        } else {
            MissingRecipeMediaView()
        }
    }
}

private struct CachedRecipeImage: View {
    let url: URL

    var body: some View {
        ButteryRemoteImage(url: url, maxPixelDimension: 900) {
            MissingRecipeMediaView()
        }
    }
}

private struct FeedLikeSummary: View {
    @Environment(SocialProfileService.self) private var social
    let recipe: Recipe

    var body: some View {
        HStack(spacing: 5) {
            Text(Self.formattedCount(social.likeCount(for: recipe)))
            Image(systemName: "heart.fill")
                .font(.system(size: 10, weight: .semibold))
        }
        .font(.caption.weight(.semibold))
    }

    private static func formattedCount(_ count: Int) -> String {
        if count >= 1_000_000 {
            return String(format: "%.1fm", Double(count) / 1_000_000).replacingOccurrences(of: ".0", with: "")
        }
        if count >= 1_000 {
            return String(format: "%.1fk", Double(count) / 1_000).replacingOccurrences(of: ".0", with: "")
        }
        return "\(count)"
    }
}

private enum ExploreFeedMode: String, CaseIterable, Identifiable {
    case explore
    case following

    var id: String { rawValue }

    var title: String {
        switch self {
        case .explore: "Explore"
        case .following: "Following"
        }
    }
}

private struct RecipeMediaCarousel: View {
    let recipe: Recipe

    private var videoURLs: [URL] {
        if let urls = recipe.videoUrls, !urls.isEmpty { return urls }
        return [recipe.videoUrl].compactMap { $0 }
    }

    private var imageURLs: [URL] {
        recipe.photoUrls.isEmpty ? [recipe.thumbnailUrl].compactMap { $0 } : recipe.photoUrls
    }

    var body: some View {
        let videos = videoURLs
        let images = imageURLs
        if videos.isEmpty && images.isEmpty {
            MissingRecipeMediaView()
        } else {
            TabView {
                ForEach(images, id: \.absoluteString) { url in
                    ButteryRemoteImage(url: url, maxPixelDimension: 1200) {
                        MissingRecipeMediaView()
                    }
                    .clipped()
                }
                ForEach(videos, id: \.absoluteString) { url in
                    VideoPlayer(player: AVPlayer(url: url))
                }
            }
            .tabViewStyle(.page(indexDisplayMode: images.count + videos.count > 1 ? .automatic : .never))
        }
    }
}

private struct MissingRecipeMediaView: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [ButteryTheme.cream.opacity(0.9), ButteryTheme.charcoal.opacity(0.18)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Image(systemName: "fork.knife.circle.fill")
                .font(.system(size: 36, weight: .semibold))
                .foregroundStyle(ButteryTheme.butter.opacity(0.9))
        }
    }
}

private struct PublicRecipeActionRail: View {
    @Environment(AuthService.self) private var auth
    @Environment(SocialProfileService.self) private var social
    let recipe: Recipe
    let creator: ButteryPublicProfile?
    let isOwnProfile: Bool
    @State private var isSharing = false
    @State private var isSaving = false

    var body: some View {
        VStack(spacing: 7) {
            Button {
                guard let userId = auth.profile?.uid else { return }
                Task { await social.toggleLike(recipe: recipe, userId: userId) }
            } label: {
                Image(systemName: social.isLiked(recipeId: recipe.id) ? "heart.fill" : "heart")
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(social.isLiked(recipeId: recipe.id) ? Color.red.opacity(0.94) : Color.white.opacity(0.82))
                    .frame(width: 28, height: 24)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(social.isLiked(recipeId: recipe.id) ? "Unlike recipe" : "Like recipe")

            Text(Self.formattedCount(social.likeCount(for: recipe)))
                .font(.caption2.weight(.bold))
                .foregroundStyle(.white.opacity(0.88))
                .shadow(color: .black.opacity(0.75), radius: 5, y: 2)
                .padding(.top, -6)

            Button {
                isSharing = true
            } label: {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(.white.opacity(0.88))
                    .frame(width: 28, height: 24)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Share recipe")

            if !isOwnProfile {
                Button {
                    isSaving = true
                } label: {
                    Image(systemName: "arrow.down.circle")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(.white.opacity(0.88))
                        .frame(width: 28, height: 24)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Save to my recipes")
            }
        }
        .padding(.vertical, 7)
        .padding(.horizontal, 5)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay {
            Capsule()
                .stroke(
                    LinearGradient(
                        colors: [
                            .white.opacity(0.48),
                            ButteryTheme.butter.opacity(0.22),
                            .white.opacity(0.12)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1
                )
        }
        .shadow(color: .black.opacity(0.22), radius: 18, y: 8)
        .sheet(isPresented: $isSharing) {
            ShareRecipeView(recipe: recipe)
                .preferredColorScheme(.light)
        }
        .sheet(isPresented: $isSaving) {
            PublicRecipeSaveSheet(recipe: recipe, creator: creator)
                .preferredColorScheme(.light)
        }
    }

    private static func formattedCount(_ count: Int) -> String {
        if count >= 1_000_000 {
            return String(format: "%.1fm", Double(count) / 1_000_000).replacingOccurrences(of: ".0", with: "")
        }
        if count >= 1_000 {
            return String(format: "%.1fk", Double(count) / 1_000).replacingOccurrences(of: ".0", with: "")
        }
        return "\(count)"
    }
}

private struct RecipePreviewFeedView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthService.self) private var auth
    @Environment(RecipeStore.self) private var store
    @Environment(SocialProfileService.self) private var social
    let recipes: [Recipe]
    let startRecipeId: String
    let creator: ButteryPublicProfile?
    let isOwnProfile: Bool
    @State private var fullRecipe: Recipe?
    @State private var saveRecipe: Recipe?

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                GeometryReader { geometry in
                    ScrollView(.vertical) {
                        LazyVStack(spacing: 18) {
                            ForEach(recipes) { recipe in
                                RecipePreviewCard(
                                    recipe: recipe,
                                    creator: creator,
                                    isOwnProfile: isOwnProfile,
                                    onExpand: { fullRecipe = recipe }
                                )
                                .id(recipe.id)
                                .frame(height: max(560, geometry.size.height * 0.82))
                                .padding(.horizontal, 14)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 18)
                    }
                    .scrollIndicators(.hidden)
                    .background(ButteryTheme.navy)
                    .onAppear {
                        DispatchQueue.main.async {
                            proxy.scrollTo(startRecipeId, anchor: .top)
                        }
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.left")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(ButteryTheme.butter)
                    }
                    .accessibilityLabel("Back")
                }
                ToolbarItem(placement: .principal) {
                    HStack(spacing: 0) {
                        Text("@\(creator?.username ?? recipes.first?.ownerUsername ?? "chef")")
                            .font(.headline.weight(.bold))
                            .foregroundStyle(ButteryTheme.butter)
                        Text("'s Recipe Feed")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(ButteryTheme.cream)
                    }
                }
            }
            .sheet(item: $fullRecipe) { recipe in
                FullPublicRecipeView(
                    recipe: recipe,
                    creator: creator,
                    isOwnProfile: isOwnProfile,
                    onToggleVisibility: { newVisibility in toggleVisibility(recipe, to: newVisibility) },
                    onSave: { savePublicRecipe(recipe) }
                )
            }
            .sheet(item: $saveRecipe) { recipe in PublicRecipeSaveSheet(recipe: recipe, creator: creator) }
        }
    }

    private func savePublicRecipe(_ recipe: Recipe) {
        _ = store.savePublicCopy(recipe, albumId: nil, creatorUsername: creator?.username)
    }

    private func toggleVisibility(_ recipe: Recipe, to visibility: RecipeVisibility) {
        guard isOwnProfile else { return }
        store.updateVisibility(id: recipe.id, visibility: visibility)
        guard let profile = auth.profile,
              let updated = store.recipes.first(where: { $0.id == recipe.id }) else { return }
        Task {
            do {
                if visibility == .public {
                    try await social.publish(updated, profile: profile)
                } else {
                    try await social.unpublish(recipeId: updated.id, ownerId: profile.uid)
                }
                await social.loadPublicRecipes(ownerId: profile.uid)
            } catch {
                social.lastError = error.localizedDescription
            }
        }
    }
}

private struct RecipePreviewCard: View {
    let recipe: Recipe
    let creator: ButteryPublicProfile?
    let isOwnProfile: Bool
    let onExpand: () -> Void
    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                ZStack(alignment: .bottom) {
                    RecipeMediaThumbnail(recipe: recipe)
                        .frame(height: geometry.size.height * 0.56)
                        .clipped()
                        .allowsHitTesting(false)
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.94)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .frame(height: geometry.size.height * 0.24)
                    .allowsHitTesting(false)
                }
                VStack(alignment: .leading, spacing: 12) {
                    Text(recipe.title)
                        .font(.system(size: 31, weight: .semibold, design: .serif))
                        .lineLimit(2)
                        .minimumScaleFactor(0.78)
                    Text("@\(creator?.username ?? recipe.ownerUsername ?? "chef")")
                        .foregroundStyle(ButteryTheme.butter)
                    FeedLikeSummary(recipe: recipe)
                        .foregroundStyle(ButteryTheme.charcoal.opacity(0.64))
                    Text(recipe.notes.isEmpty ? "A cozy Buttery recipe card." : recipe.notes)
                        .lineLimit(3)
                    HStack {
                        Label(recipe.cookTime.isEmpty ? recipe.totalTime : recipe.cookTime, systemImage: "clock")
                        Label(recipe.servings, systemImage: "person.2")
                        Spacer()
                        Text("★ \(recipe.ratingAverage ?? 0, specifier: "%.1f")")
                    }
                    .font(.caption)
                    Text("Tap to view full recipe")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(ButteryTheme.charcoal.opacity(0.58))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .padding(18)
                .background(ButteryTheme.cream)
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .foregroundStyle(ButteryTheme.charcoal)
            .overlay(alignment: .bottom) {
                LinearGradient(
                    colors: [.clear, .black.opacity(0.38)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 90)
                .allowsHitTesting(false)
            }
            .clipShape(RoundedRectangle(cornerRadius: 28))
            .shadow(color: .black.opacity(0.26), radius: 18, y: 10)
            .contentShape(RoundedRectangle(cornerRadius: 28))
            .onTapGesture(perform: onExpand)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct FullPublicRecipeView: View {
    let recipe: Recipe
    let creator: ButteryPublicProfile?
    let isOwnProfile: Bool
    let onToggleVisibility: (RecipeVisibility) -> Void
    let onSave: () -> Void
    @State private var currentVisibility: RecipeVisibility = .private
    @State private var visibilityMessage: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    ZStack(alignment: .topTrailing) {
                        RecipeMediaCarousel(recipe: recipe)
                            .frame(height: 260)
                            .clipShape(RoundedRectangle(cornerRadius: 22))
                        PublicRecipeActionRail(recipe: recipe, creator: creator, isOwnProfile: isOwnProfile)
                            .padding(12)
                    }
                    Text(recipe.title).font(.system(size: 34, design: .serif))
                    infoRow
                    section("Ingredients", recipe.ingredients)
                    section("Instructions", recipe.instructions)
                    if !recipe.notes.isEmpty { section("Notes", recipe.notes) }
                    if isOwnProfile {
                        Button(currentVisibility == .public ? "Make Private" : "Make Public") {
                            currentVisibility = currentVisibility == .public ? .private : .public
                            onToggleVisibility(currentVisibility)
                            visibilityMessage = currentVisibility == .public
                                ? "Recipe is now public."
                                : "Recipe is now private."
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(ButteryTheme.butter)
                        .foregroundStyle(ButteryTheme.navy)
                    }
                    if let visibilityMessage {
                        Text(visibilityMessage)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(ButteryTheme.herb)
                    }
                }
                .padding(18)
            }
            .background(ButteryTheme.cream)
            .foregroundStyle(ButteryTheme.charcoal)
        }
        .onAppear {
            currentVisibility = recipe.visibility ?? .private
        }
    }
    private var infoRow: some View {
        HStack {
            Label(recipe.prepTime, systemImage: "timer")
            Label(recipe.cookTime, systemImage: "flame")
            Label(recipe.servings, systemImage: "person.2")
        }.font(.caption)
    }
    private func section(_ title: String, _ body: String) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title).font(.system(size: 24, design: .serif))
            Text(body.isEmpty ? "None provided." : body)
        }
    }
}

private struct PublicRecipeSaveSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(RecipeStore.self) private var store
    let recipe: Recipe
    let creator: ButteryPublicProfile?
    @State private var newAlbum = ""
    @State private var message: String?
    var body: some View {
        NavigationStack {
            List {
                Section("Choose an album") {
                    Button("All Recipes") { save(albumId: nil) }
                    ForEach(store.albums) { album in Button(album.name) { save(albumId: album.id) } }
                }
                Section("Create new album") {
                    TextField("Album name", text: $newAlbum)
                    Button("Create and Save") {
                        let id = store.createAlbum(name: newAlbum)
                        save(albumId: id)
                    }.disabled(newAlbum.trimmed.isEmpty)
                }
                if let message { Text(message).foregroundStyle(ButteryTheme.herb) }
            }
            .scrollContentBackground(.hidden)
            .background(ButteryTheme.cream)
            .foregroundStyle(ButteryTheme.charcoal)
            .navigationTitle("Save Recipe")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .foregroundStyle(ButteryTheme.charcoal)
                }
            }
        }
        .background(ButteryTheme.cream)
        .preferredColorScheme(.light)
        .presentationBackground(ButteryTheme.cream)
    }
    private func save(albumId: String?) {
        _ = store.savePublicCopy(recipe, albumId: albumId, creatorUsername: creator?.username)
        message = "Saved privately to your recipes."
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { dismiss() }
    }
}

struct ExploreView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthService.self) private var auth
    @Environment(RecipeStore.self) private var store
    @Environment(SocialProfileService.self) private var social
    @State private var selectedCategory = ExploreCategory.all
    @State private var selectedRecipe: Recipe?
    @State private var saveMessage: String?
    @State private var isShowingSearch = false
    @State private var searchQuery = ""
    @State private var submittedSearchQuery = ""
    @State private var feedMode: ExploreFeedMode = .explore

    private var visibleRecipes: [Recipe] {
        social.communityPublicRecipes.filter { recipe in
            if feedMode == .following && !social.subscribedCreatorIds.contains(recipe.ownerId) {
                return false
            }
            return selectedCategory.matches(recipe)
        }
        .sorted { lhs, rhs in
            let lhsLikes = lhs.likeCount ?? 0
            let rhsLikes = rhs.likeCount ?? 0
            if lhsLikes != rhsLikes { return lhsLikes > rhsLikes }
            return lhs.publicPublishedAt ?? lhs.updatedAt > rhs.publicPublishedAt ?? rhs.updatedAt
        }
    }

    private var searchResults: [Recipe] {
        let terms = submittedSearchQuery
            .lowercased()
            .split(whereSeparator: { $0.isWhitespace || $0.isPunctuation })
            .map(String.init)
            .filter { !$0.isEmpty }
        guard !terms.isEmpty else { return [] }
        return social.communityPublicRecipes.filter { recipe in
            let searchable = ExploreCategory.searchableText(for: recipe)
            return terms.allSatisfy { searchable.contains($0) }
        }
        .sorted { lhs, rhs in
            let lhsLikes = lhs.likeCount ?? 0
            let rhsLikes = rhs.likeCount ?? 0
            if lhsLikes != rhsLikes { return lhsLikes > rhsLikes }
            return lhs.publicPublishedAt ?? lhs.updatedAt > rhs.publicPublishedAt ?? rhs.updatedAt
        }
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                BundledImage(name: "home_background")
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .clipped()
                    .ignoresSafeArea()

                LinearGradient(
                    colors: [
                        Color(hex: 0x15130F, alpha: 0.96),
                        Color(hex: 0x211B13, alpha: 0.88),
                        Color(hex: 0x2F2418, alpha: 0.78)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                VStack(spacing: 16) {
                    header
                        .padding(.horizontal, 20)
                        .padding(.top, 18)
                        .zIndex(3)

                    categoryScroller
                        .padding(.leading, 20)
                        .zIndex(2)

                    ScrollView {
                        VStack(alignment: .leading, spacing: 18) {
                            if let saveMessage {
                                Text(saveMessage)
                                    .font(.footnote.weight(.semibold))
                                    .foregroundStyle(ButteryTheme.herb)
                                    .frame(maxWidth: .infinity, alignment: .center)
                                    .transition(.opacity)
                            }

                            if auth.profile == nil {
                                emptyState(
                                    title: "Sign in to explore",
                                    message: "Community recipes are available after signing in."
                                )
                            } else if visibleRecipes.isEmpty {
                                emptyState(
                                    title: emptyTitle,
                                    message: emptyMessage
                                )
                            } else {
                                LazyVStack(spacing: 14) {
                                    ForEach(visibleRecipes) { recipe in
                                        ExploreRecipeCard(
                                            recipe: recipe,
                                            creator: creator(for: recipe),
                                            onTap: { selectedRecipe = recipe }
                                        )
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 42)
                    }
                    .scrollIndicators(.hidden)
                    .refreshable {
                        await social.loadCommunityPublicRecipes()
                        await social.refreshSubscriptions(for: auth.profile?.uid)
                        await social.refreshLikedRecipes(for: auth.profile?.uid)
                    }
                }
            }
        }
        .background(Color(hex: 0x15130F))
        .toolbar(.hidden, for: .navigationBar)
        .task {
            await social.loadCommunityPublicRecipes()
            await social.refreshSubscriptions(for: auth.profile?.uid)
            await social.refreshLikedRecipes(for: auth.profile?.uid)
        }
        .sheet(isPresented: $isShowingSearch) {
            ExploreSearchSheet(
                query: $searchQuery,
                submittedQuery: $submittedSearchQuery,
                results: searchResults,
                creator: creator(for:),
                onSelect: { recipe in
                    isShowingSearch = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        selectedRecipe = recipe
                    }
                }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $selectedRecipe) { recipe in
            FullPublicRecipeView(
                recipe: recipe,
                creator: creator(for: recipe),
                isOwnProfile: recipe.ownerId == auth.profile?.uid,
                onToggleVisibility: { newVisibility in
                    store.updateVisibility(id: recipe.id, visibility: newVisibility)
                    if let profile = auth.profile,
                       let updated = store.recipes.first(where: { $0.id == recipe.id }) {
                        Task {
                            if newVisibility == .public {
                                try? await social.publish(updated, profile: profile)
                            } else {
                                try? await social.unpublish(recipeId: updated.id, ownerId: profile.uid)
                            }
                            await social.loadCommunityPublicRecipes()
                            await social.refreshLikedRecipes(for: auth.profile?.uid)
                        }
                    }
                },
                onSave: { save(recipe) }
            )
        }
    }

    private var header: some View {
        ZStack {
            HStack {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "house.fill")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(ButteryTheme.cream)
                        .frame(width: 38, height: 38)
                        .background(Color.white.opacity(0.08), in: Circle())
                        .overlay(Circle().stroke(ButteryTheme.butter.opacity(0.24), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Home")

                Spacer()
                Button {
                    submittedSearchQuery = ""
                    isShowingSearch = true
                } label: {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 25, weight: .medium))
                        .foregroundStyle(ButteryTheme.butter)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Search recipes")
            }

            HStack(spacing: 24) {
                ForEach(ExploreFeedMode.allCases) { mode in
                    Button {
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                            feedMode = mode
                        }
                    } label: {
                        VStack(spacing: 5) {
                            Text(mode.title)
                                .font(.headline.weight(feedMode == mode ? .bold : .semibold))
                                .foregroundStyle(feedMode == mode ? ButteryTheme.cream : ButteryTheme.cream.opacity(0.46))
                            Capsule()
                                .fill(feedMode == mode ? ButteryTheme.butter : .clear)
                                .frame(width: 24, height: 3)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 52)
        }
    }

    private var emptyTitle: String {
        if feedMode == .following {
            return selectedCategory == .all ? "No followed recipes yet" : "No followed \(selectedCategory.title.lowercased()) yet"
        }
        return selectedCategory == .all ? "No public recipes yet" : "Nothing in \(selectedCategory.title) yet"
    }

    private var emptyMessage: String {
        if feedMode == .following {
            return "Subscribe to creators to see their public recipes here."
        }
        return "As chefs publish recipes, they’ll appear here."
    }

    private var categoryScroller: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 10) {
                ForEach(ExploreCategory.allCases) { category in
                    Button {
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.82)) {
                            selectedCategory = category
                        }
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: category.symbol)
                                .font(.system(size: 14, weight: .semibold))
                            Text(category.title)
                                .font(.system(size: 11, weight: .semibold))
                        }
                        .foregroundStyle(selectedCategory == category ? ButteryTheme.navy : ButteryTheme.cream.opacity(0.82))
                        .frame(minWidth: category == .all ? 56 : 76, minHeight: 48)
                        .background(
                            selectedCategory == category
                            ? ButteryTheme.butter
                            : Color.white.opacity(0.07),
                            in: Capsule()
                        )
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .id(category.id)
                }
            }
            .padding(.vertical, 4)
            .padding(.trailing, 40)
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
        .frame(height: 56)
    }

    private func creator(for recipe: Recipe) -> ButteryPublicProfile? {
        let ownerId = recipe.ownerId
        if let profile = social.loadedProfiles[ownerId] { return profile }
        guard !ownerId.isEmpty else { return nil }
        return ButteryPublicProfile(
            id: ownerId,
            username: recipe.ownerUsername ?? "chef",
            displayName: recipe.ownerDisplayName ?? recipe.ownerUsername ?? "Buttery Chef",
            profilePhotoUrl: recipe.ownerProfilePhotoUrl,
            bio: "A warm little recipe book.",
            websiteUrl: nil,
            subscriberCount: 0,
            recipeCount: 0,
            publicRecipeCount: 0
        )
    }

    private func save(_ recipe: Recipe) {
        _ = store.savePublicCopy(recipe, albumId: nil, creatorUsername: recipe.ownerUsername)
        withAnimation { saveMessage = "Saved to your recipes." }
        Task {
            try? await Task.sleep(nanoseconds: 1_600_000_000)
            await MainActor.run { withAnimation { saveMessage = nil } }
        }
    }

    private func emptyState(title: String, message: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: "sparkles")
                .font(.system(size: 30))
                .foregroundStyle(ButteryTheme.butter)
            Text(title)
                .font(.headline)
                .foregroundStyle(ButteryTheme.cream)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(ButteryTheme.cream.opacity(0.7))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(30)
        .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 24))
    }
}

private struct ExploreRecipeCard: View {
    let recipe: Recipe
    let creator: ButteryPublicProfile?
    let onTap: () -> Void

    var body: some View {
        ZStack {
            RecipeMediaThumbnail(recipe: recipe)
                .frame(height: 220)
                .frame(maxWidth: .infinity)
                .clipped()
                .allowsHitTesting(false)

            LinearGradient(
                colors: [.black.opacity(0.02), .black.opacity(0.14), .black.opacity(0.86)],
                startPoint: .top,
                endPoint: .bottom
            )
            .allowsHitTesting(false)

            VStack(alignment: .leading, spacing: 6) {
                Spacer()
                Text(recipe.title)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(ButteryTheme.cream)
                    .lineLimit(2)
                Text("by @\(creator?.username ?? recipe.ownerUsername ?? "chef")")
                    .font(.subheadline)
                    .foregroundStyle(ButteryTheme.cream.opacity(0.72))
                FeedLikeSummary(recipe: recipe)
                    .foregroundStyle(ButteryTheme.cream.opacity(0.78))
            }
            .padding(16)
            .padding(.trailing, 58)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            .allowsHitTesting(false)

        }
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(ButteryTheme.butter.opacity(0.12), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.36), radius: 10, y: 6)
        .contentShape(RoundedRectangle(cornerRadius: 18))
        .onTapGesture(perform: onTap)
    }
}

private struct ExploreSearchSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var query: String
    @Binding var submittedQuery: String
    let results: [Recipe]
    let creator: (Recipe) -> ButteryPublicProfile?
    let onSelect: (Recipe) -> Void

    var body: some View {
        NavigationStack {
            ZStack {
                ButteryTheme.cream.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Text("Search Recipes")
                            .font(.system(size: 26, weight: .regular, design: .serif))
                            .foregroundStyle(.black)
                        Spacer()
                        Button("Close") { dismiss() }
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(ButteryTheme.navy)
                    }

                    HStack(spacing: 10) {
                        TextField("Search recipes or ingredients", text: $query)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .foregroundStyle(.black)
                            .tint(.black)
                            .submitLabel(.search)
                            .onSubmit { submittedQuery = query.trimmed }
                            .padding(13)
                            .background(.white, in: RoundedRectangle(cornerRadius: 16))
                        Button("Search") {
                            submittedQuery = query.trimmed
                        }
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(ButteryTheme.navy)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 13)
                        .background(ButteryTheme.butter, in: Capsule())
                    }

                    if submittedQuery.trimmed.isEmpty {
                        Text("Type a keyword like chicken, pasta, breakfast, cake, or any ingredient.")
                            .font(.subheadline)
                            .foregroundStyle(ButteryTheme.charcoal.opacity(0.65))
                    } else if results.isEmpty {
                        VStack(spacing: 10) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 28))
                                .foregroundStyle(ButteryTheme.butter)
                            Text("No matching recipes yet.")
                                .font(.headline)
                            Text("Try another keyword or ingredient.")
                                .font(.subheadline)
                                .foregroundStyle(ButteryTheme.charcoal.opacity(0.65))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 30)
                    } else {
                        Text("\(results.count) result\(results.count == 1 ? "" : "s")")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(ButteryTheme.charcoal.opacity(0.62))
                        ScrollView {
                            LazyVStack(spacing: 12) {
                                ForEach(results) { recipe in
                                    Button {
                                        onSelect(recipe)
                                    } label: {
                                        HStack(spacing: 12) {
                                            RecipeMediaThumbnail(recipe: recipe)
                                                .frame(width: 74, height: 74)
                                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                            VStack(alignment: .leading, spacing: 5) {
                                                Text(recipe.title)
                                                    .font(.headline)
                                                    .foregroundStyle(ButteryTheme.charcoal)
                                                    .lineLimit(2)
                                                Text("by @\(creator(recipe)?.username ?? recipe.ownerUsername ?? "chef")")
                                                    .font(.caption)
                                                    .foregroundStyle(ButteryTheme.charcoal.opacity(0.62))
                                            }
                                            Spacer()
                                            Image(systemName: "chevron.right")
                                                .foregroundStyle(ButteryTheme.charcoal.opacity(0.45))
                                        }
                                        .padding(10)
                                        .background(.white.opacity(0.78), in: RoundedRectangle(cornerRadius: 18))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.bottom, 16)
                        }
                        .scrollIndicators(.hidden)
                    }
                    Spacer(minLength: 0)
                }
                .padding(18)
            }
            .toolbar(.hidden, for: .navigationBar)
        }
    }
}

private enum ExploreCategory: String, CaseIterable, Identifiable {
    case all
    case breakfast
    case lunch
    case dinner
    case desserts
    case drinks

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: "All"
        case .breakfast: "Breakfast"
        case .lunch: "Lunch"
        case .dinner: "Dinner"
        case .desserts: "Desserts"
        case .drinks: "Drinks"
        }
    }

    var symbol: String {
        switch self {
        case .all: "sparkles"
        case .breakfast: "sun.max.fill"
        case .lunch: "takeoutbag.and.cup.and.straw.fill"
        case .dinner: "fork.knife.circle.fill"
        case .desserts: "birthday.cake.fill"
        case .drinks: "mug.fill"
        }
    }

    var keywords: [String] {
        switch self {
        case .all: []
        case .breakfast: ["pancake", "waffle", "breakfast", "egg", "oat", "muffin", "toast", "bacon", "brunch", "cereal"]
        case .lunch: ["sandwich", "salad", "wrap", "soup", "lunch", "bowl", "panini"]
        case .dinner: ["dinner", "pasta", "chicken", "salmon", "steak", "rice", "casserole", "roast", "garlic", "butter", "noodle"]
        case .desserts: ["cake", "cookie", "brownie", "pie", "dessert", "chocolate", "frosting", "sweet", "pudding", "cupcake"]
        case .drinks: ["smoothie", "juice", "cocktail", "mocktail", "coffee", "tea", "lemonade", "drink", "latte"]
        }
    }

    func matches(_ recipe: Recipe) -> Bool {
        self == .all || Self.searchableText(for: recipe).containsAny(keywords)
    }

    static func searchableText(for recipe: Recipe) -> String {
        [
            recipe.title,
            recipe.notes,
            recipe.prepTime,
            recipe.cookTime,
            recipe.totalTime,
            recipe.servings,
            recipe.ingredients,
            recipe.instructions,
            recipe.ownerUsername ?? "",
            recipe.ownerDisplayName ?? "",
            recipe.originalRawText
        ]
        .joined(separator: " ")
        .lowercased()
    }
}

private extension String {
    func containsAny(_ needles: [String]) -> Bool {
        needles.contains { contains($0) }
    }
}

private struct ManagePublicRecipesView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthService.self) private var auth
    @Environment(RecipeStore.self) private var store
    @Environment(SocialProfileService.self) private var social
    @State private var message: String?
    var body: some View {
        NavigationStack {
            List {
                Section("Public Recipe Visibility") {
                    ForEach(store.recipes) { recipe in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(recipe.title)
                                Text((recipe.visibility ?? .private) == .public ? "Public" : "Private")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button((recipe.visibility ?? .private) == .public ? "Make Private" : "Publish") {
                                toggle(recipe)
                            }
                        }
                    }
                }
                if let message { Text(message) }
            }
            .navigationTitle("Manage Public Recipes")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
    private func toggle(_ recipe: Recipe) {
        guard let profile = auth.profile else { return }
        Task {
            do {
                if (recipe.visibility ?? .private) == .public {
                    store.updateVisibility(id: recipe.id, visibility: .private)
                    try await social.unpublish(recipeId: recipe.id, ownerId: profile.uid)
                    message = "\(recipe.title) is private."
                } else {
                    store.updateVisibility(id: recipe.id, visibility: .public)
                    if let updated = store.recipes.first(where: { $0.id == recipe.id }) {
                        try await social.publish(updated, profile: profile)
                    }
                    message = "\(recipe.title) is public."
                }
            } catch {
                message = error.localizedDescription
            }
        }
    }
}

private struct ProfilePhoto: View {
    let urlString: String?
    let size: CGFloat
    var body: some View {
        if let urlString, let url = URL(string: urlString) {
            ButteryRemoteImage(url: url, maxPixelDimension: max(220, size * 3)) {
                Image(systemName: "person.crop.circle.fill")
                    .resizable()
                    .foregroundStyle(ButteryTheme.butter)
            }
            .frame(width: size, height: size).clipShape(Circle())
        } else {
            Image(systemName: "person.crop.circle.fill")
                .resizable()
                .foregroundStyle(ButteryTheme.butter)
                .frame(width: size, height: size)
        }
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}

private extension Date {
    var millisecondsSince1970: Int64 { Int64(timeIntervalSince1970 * 1_000) }
}

struct PrivacySupportView: View {
    private let supportURL = URL(string: "https://github.com/desiredoecette-create/buttery/issues")!

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Color(hex: 0x293A43), ButteryTheme.navy],
                center: .top,
                startRadius: 20,
                endRadius: 750
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Text("Privacy Policy")
                        .font(.system(size: 30, design: .serif))
                    Text("Effective July 3, 2026")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    policySection(
                        "Information Buttery Uses",
                        "When you create an account, Buttery stores your email address, username, display name, optional profile picture, sign-in provider, and account identifier. Recipes, albums, favorites, grocery lists, and cooking progress are stored on your device. When you share a recipe, its content and selected media are uploaded so the recipient can receive it."
                    )
                    policySection(
                        "How Information Is Used",
                        "This information is used to authenticate your account, display your profile, keep users’ content separated, and deliver recipes you intentionally share. Buttery does not sell personal information and does not use advertising or cross-app tracking."
                    )
                    policySection(
                        "Service Providers",
                        "Buttery uses Google Firebase for authentication, profiles, recipe sharing, database services, and uploaded media. Google Sign-In and Sign in with Apple are available when you choose those providers. Their processing is governed by their respective privacy policies."
                    )
                    policySection(
                        "Your Choices",
                        "You can edit your display name and profile picture in Settings. You can delete recipes and other content in the app. Delete My Account permanently removes your account and associated Buttery cloud data. Guest data remains only on this device."
                    )
                    policySection(
                        "Data Retention and Security",
                        "Account information remains until you delete your account. Shared data remains until an involved account deletes it. Buttery uses authenticated Firebase access controls, but no system can guarantee absolute security."
                    )
                    policySection(
                        "Children",
                        "Buttery is not directed to children under 13 and does not knowingly collect their personal information."
                    )
                    policySection(
                        "Policy Changes",
                        "This policy may be updated as Buttery’s features change. The effective date above will be revised when material changes are made."
                    )

                    Divider()
                    Text("Support")
                        .font(.system(size: 27, design: .serif))
                    Text("Need help with your account, recipes, sharing, or deletion? Open the Buttery support page and create a support request.")
                    Link(destination: supportURL) {
                        Label("Open Buttery Support", systemImage: "arrow.up.right.square")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(ButteryTheme.butter)
                    .foregroundStyle(ButteryTheme.navy)
                }
                .foregroundStyle(ButteryTheme.charcoal)
                .padding(22)
                .background(ButteryTheme.cream, in: RoundedRectangle(cornerRadius: 22))
                .padding(18)
            }
        }
        .navigationTitle("Privacy & Support")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func policySection(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title)
                .font(.headline)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(ButteryTheme.charcoal.opacity(0.78))
        }
    }
}

struct RecipeInboxView: View {
    @Environment(AppState.self) private var appState
    @Environment(AuthService.self) private var auth
    @Environment(SharingService.self) private var sharing
    @Environment(SocialProfileService.self) private var social
    @Environment(RecipeStore.self) private var recipes
    @State private var selectedShare: ButteryRecipeShare?

    var body: some View {
        ZStack {
            RadialGradient(colors: [Color(hex: 0x293A43), ButteryTheme.navy],
                           center: .top, startRadius: 20, endRadius: 750).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Label("Recipes Shared With You", systemImage: "tray.full")
                            .font(.system(size: 25, design: .serif))
                        Spacer()
                        Text("\(sharing.inbox.count) received").font(.caption).foregroundStyle(.secondary)
                    }
                    if sharing.isLoading {
                        ProgressView("Checking for shared recipes…")
                    } else if sharing.inbox.isEmpty {
                        Text("Shared recipes will appear here.")
                            .frame(maxWidth: .infinity).padding(.vertical, 60).foregroundStyle(.secondary)
                    } else {
                        ForEach(sharing.inbox) { share in inboxRow(share) }
                    }
                    if let error = sharing.lastError {
                        Text(error).font(.footnote).foregroundStyle(Color(hex: 0x9B493D))
                    }
                }
                .foregroundStyle(ButteryTheme.charcoal).padding(20)
                .background(ButteryTheme.cream, in: RoundedRectangle(cornerRadius: 22))
                .padding(18)
            }
        }
        .navigationTitle("Inbox").navigationBarTitleDisplayMode(.inline)
        .onAppear {
            sharing.listen(for: auth.currentUser?.uid)
            Task { await sharing.markSubscriptionNotificationsViewed(for: auth.currentUser?.uid) }
        }
        .fullScreenCover(item: $selectedShare) { share in SharedRecipePreviewView(share: share) }
    }

    private func inboxRow(_ share: ButteryRecipeShare) -> some View {
        let senderPhoto = social.loadedProfiles[share.fromUserId]?.profilePhotoUrl ?? share.fromProfilePhotoUrl
        return VStack(alignment: .leading, spacing: 11) {
            HStack(alignment: .top, spacing: 10) {
                if share.type == "subscriptionNotification" {
                    SenderAvatar(urlString: senderPhoto, size: 44)
                } else {
                    Button {
                        social.cacheProfile(
                            userId: share.fromUserId,
                            username: share.fromUsername,
                            displayName: share.fromDisplayName,
                            profilePhotoUrl: senderPhoto
                        )
                        appState.path.append(.publicProfile(userId: share.fromUserId))
                    } label: {
                        SenderAvatar(urlString: senderPhoto, size: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("View @\(share.fromUsername)'s profile")
                }
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(share.fromDisplayName).fontWeight(.semibold)
                        if share.status == "pending" { Circle().fill(.red).frame(width: 9, height: 9) }
                    }
                    Text("@\(share.fromUsername)").font(.caption).foregroundStyle(.secondary)
                    if share.type == "subscriptionNotification" {
                        Text(share.notificationMessage ?? "@\(share.fromUsername) subscribed to you.")
                            .font(.subheadline)
                    } else {
                        Text("shared \(share.recipe.title) with you").font(.subheadline)
                    }
                }
                Spacer()
                Button { Task { try? await sharing.mark(share, status: "dismissed") } } label: {
                    Image(systemName: "xmark")
                }
            }
            if share.status == "added" {
                Label("Added to your recipes", systemImage: "checkmark.circle.fill")
                    .font(.caption).foregroundStyle(ButteryTheme.herb)
            }
            if share.type != "subscriptionNotification" {
                HStack {
                    Button("View") {
                        selectedShare = share
                        if share.status == "pending" {
                            Task { try? await sharing.mark(share, status: "viewed") }
                        }
                    }.buttonStyle(.bordered)
                    Button("Add to My Recipes") {
                        _ = recipes.addSharedRecipe(share.recipe)
                        Task { try? await sharing.mark(share, status: "added") }
                    }
                    .buttonStyle(.borderedProminent).tint(ButteryTheme.butter)
                    .foregroundStyle(ButteryTheme.navy).disabled(share.status == "added")
                }
            }
        }
        .padding(14).background(.white.opacity(0.68), in: RoundedRectangle(cornerRadius: 14))
        .task { await social.loadProfile(userId: share.fromUserId) }
    }
}

private struct SharedRecipePreviewView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(RecipeStore.self) private var recipes
    @Environment(SharingService.self) private var sharing
    let share: ButteryRecipeShare
    private var currentShare: ButteryRecipeShare {
        sharing.inbox.first { $0.id == share.id } ?? share
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ButteryTheme.navy.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        HStack(spacing: 12) {
                            SenderAvatar(urlString: share.fromProfilePhotoUrl, size: 54)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(share.fromDisplayName).font(.headline)
                                Text("@\(share.fromUsername)").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        Divider()
                        Text(share.recipe.title).font(.system(size: 34, design: .serif))
                        if !share.recipe.photoUrls.isEmpty {
                            ScrollView(.horizontal) {
                                HStack(spacing: 12) {
                                    ForEach(share.recipe.photoUrls, id: \.self) { value in
                                        if let url = URL(string: value) {
                                            ButteryRemoteImage(url: url, maxPixelDimension: 700) {
                                                Color.black.overlay(ProgressView().tint(.white))
                                            }
                                            .frame(width: 285, height: 185)
                                            .clipShape(RoundedRectangle(cornerRadius: 15))
                                        }
                                    }
                                }
                            }
                            .scrollIndicators(.hidden)
                        }
                        section("Ingredients", share.recipe.ingredients)
                        section("Instructions", share.recipe.instructions)
                        if !share.recipe.notes.isEmpty { section("Notes", share.recipe.notes) }
                        if let value = share.recipe.videoUrl, let url = URL(string: value) {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Video").font(.system(size: 23, design: .serif))
                                VideoPlayer(player: AVPlayer(url: url))
                                    .frame(height: 210)
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                            }
                        }
                    }
                    .padding(22).background(ButteryTheme.cream, in: RoundedRectangle(cornerRadius: 22))
                    .padding(16)
                }
            }
            .foregroundStyle(ButteryTheme.charcoal)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Inbox") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add to My Recipes") {
                        _ = recipes.addSharedRecipe(share.recipe)
                        Task { try? await sharing.mark(share, status: "added") }
                    }
                    .disabled(currentShare.status == "added")
                }
            }
        }
    }

    private func section(_ title: String, _ body: String) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title).font(.system(size: 23, design: .serif))
            Text(body.isEmpty ? "None provided." : body)
        }
    }
}

private struct SenderAvatar: View {
    let urlString: String?
    let size: CGFloat
    var body: some View {
        Group {
            if let urlString, let url = URL(string: urlString) {
                ButteryRemoteImage(url: url, maxPixelDimension: max(180, size * 3)) {
                    Image(systemName: "person.fill")
                }
            } else {
                Image(systemName: "person.fill")
            }
        }
        .frame(width: size, height: size).background(ButteryTheme.butter)
        .clipShape(Circle())
    }
}

struct ShareRecipeView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthService.self) private var auth
    @Environment(SharingService.self) private var sharing
    let recipe: Recipe
    @State private var username = ""
    @State private var message: String?
    @State private var isSharing = false
    @State private var suggestions: [String] = []
    @State private var isSearching = false
    @State private var searchTask: Task<Void, Never>?

    var body: some View {
        NavigationStack {
            Form {
                Section("Recipient") {
                    TextField("Username", text: $username)
                        .foregroundStyle(ButteryTheme.charcoal)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    if isSearching {
                        HStack {
                            ProgressView()
                            Text("Finding users…").foregroundStyle(.secondary)
                        }
                    }
                    ForEach(suggestions, id: \.self) { suggestion in
                        Button {
                            username = suggestion
                            suggestions = []
                        } label: {
                            Label("@\(suggestion)", systemImage: "person.crop.circle")
                        }
                    }
                }
                if let message { Text(message) }
                Button {
                    guard let profile = auth.profile else { return }
                    isSharing = true
                    Task {
                        do {
                            try await sharing.share(recipe, from: profile, toUsername: username)
                            message = "Recipe shared with \(username)."
                        } catch { message = error.localizedDescription }
                        isSharing = false
                    }
                } label: {
                    HStack {
                        if isSharing { ProgressView().tint(.white) }
                        Text(isSharing ? "Sharing Recipe…" : "Share Recipe")
                    }
                    .frame(maxWidth: .infinity)
                }
                .disabled(isSharing || username.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .scrollContentBackground(.hidden)
            .background(ButteryTheme.cream)
            .foregroundStyle(ButteryTheme.charcoal)
            .navigationTitle("Share \(recipe.title)")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                        .foregroundStyle(ButteryTheme.charcoal)
                }
            }
            .onChange(of: username) { _, value in
                searchTask?.cancel()
                let clean = value.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !clean.isEmpty else {
                    suggestions = []
                    isSearching = false
                    return
                }
                isSearching = true
                searchTask = Task {
                    try? await Task.sleep(for: .milliseconds(250))
                    guard !Task.isCancelled else { return }
                    do {
                        let matches = try await sharing.matchingUsernames(prefix: clean)
                        guard !Task.isCancelled else { return }
                        suggestions = matches.filter { $0 != clean.lowercased() }
                    } catch {
                        suggestions = []
                    }
                    isSearching = false
                }
            }
            .onDisappear { searchTask?.cancel() }
        }
        .background(ButteryTheme.cream)
        .preferredColorScheme(.light)
        .presentationBackground(ButteryTheme.cream)
    }
}
