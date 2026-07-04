import SwiftUI
import PhotosUI
import AVKit

struct ProfileSettingsView: View {
    @Environment(AuthService.self) private var auth
    @Environment(SharingService.self) private var sharing
    @Environment(RecipeStore.self) private var recipes
    @Environment(AppState.self) private var appState
    @State private var displayName = ""
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
                .disabled(isSavingProfile)
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
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        ProgressView()
                    }
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
    @Environment(AuthService.self) private var auth
    @Environment(SharingService.self) private var sharing
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
        .onAppear { sharing.listen(for: auth.currentUser?.uid) }
        .fullScreenCover(item: $selectedShare) { share in SharedRecipePreviewView(share: share) }
    }

    private func inboxRow(_ share: ButteryRecipeShare) -> some View {
        VStack(alignment: .leading, spacing: 11) {
            HStack(alignment: .top, spacing: 10) {
                SenderAvatar(urlString: share.fromProfilePhotoUrl, size: 44)
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(share.fromDisplayName).fontWeight(.semibold)
                        if share.status == "pending" { Circle().fill(.red).frame(width: 9, height: 9) }
                    }
                    Text("@\(share.fromUsername)").font(.caption).foregroundStyle(.secondary)
                    Text("shared \(share.recipe.title) with you").font(.subheadline)
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
        .padding(14).background(.white.opacity(0.68), in: RoundedRectangle(cornerRadius: 14))
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
                                            AsyncImage(url: url) { phase in
                                                if let image = phase.image {
                                                    image.resizable().scaledToFill()
                                                } else {
                                                    Color.black.overlay(ProgressView().tint(.white))
                                                }
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
                AsyncImage(url: url) { phase in
                    if let image = phase.image { image.resizable().scaledToFill() }
                    else { Image(systemName: "person.fill") }
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
            .navigationTitle("Share \(recipe.title)")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } } }
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
    }
}
