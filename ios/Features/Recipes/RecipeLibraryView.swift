import AVKit
import PhotosUI
import SwiftUI
import UIKit

private let libraryBackground = Color(hex: 0x11110E)
private let libraryPanel = Color(hex: 0x1B1A15)
private let libraryCream = Color(hex: 0xF4EFE3)
private let libraryMuted = Color(hex: 0xC5BBA9)
private let libraryGold = Color(hex: 0xC4A46B)
private let paper = Color(hex: 0xF4EDDE)
private let paperInk = Color(hex: 0x292218)

struct RecipeLibraryView: View {
    @Environment(RecipeStore.self) private var store
    @State private var search = ""
    @State private var editingAlbum: RecipeAlbum?
    @State private var creatingAlbum = false
    @State private var showingNewRecipeChoices = false

    private var albumCards: [AlbumCardModel] {
        let all = AlbumCardModel(
            id: nil,
            name: "All Recipes",
            count: store.recipes.count,
            cover: store.recipes.first?.photoUrls.first
        )
        let albums = store.albums.map { album in
            let recipes = store.recipes.filter { $0.albumId == album.id }
            return AlbumCardModel(
                id: album.id,
                name: album.name,
                count: recipes.count,
                cover: album.customCoverImageUrl ?? recipes.first?.photoUrls.first
            )
        }
        return ([all] + albums).filter {
            search.isEmpty || $0.name.localizedCaseInsensitiveContains(search)
        }
    }

    var body: some View {
        LibraryBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Your Recipe Albums")
                        .font(.system(size: 34, design: .serif))
                        .foregroundStyle(libraryCream)
                    Text("Select an album to explore")
                        .foregroundStyle(libraryMuted)
                    SearchField(text: $search, prompt: "Search albums or recipes…")

                    if store.recipes.isEmpty && store.albums.isEmpty {
                        EmptyLibraryView { showingNewRecipeChoices = true }
                    } else {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
                            ForEach(albumCards) { album in
                                NavigationLink {
                                    AlbumRecipesView(albumId: album.id)
                                } label: {
                                    AlbumCardView(album: album)
                                }
                                .buttonStyle(.plain)
                                .contextMenu {
                                    if let id = album.id,
                                       let stored = store.albums.first(where: { $0.id == id }) {
                                        Button("Edit Album", systemImage: "pencil") {
                                            editingAlbum = stored
                                        }
                                        Button("Delete Album", systemImage: "trash", role: .destructive) {
                                            store.deleteAlbum(id: id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(18)
                .padding(.bottom, 40)
            }
        }
        .navigationTitle("Recipes")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button("Album", systemImage: "folder.badge.plus") { creatingAlbum = true }
                Button("Recipe", systemImage: "plus") { showingNewRecipeChoices = true }
            }
        }
        .sheet(isPresented: $creatingAlbum) { AlbumEditorView() }
        .sheet(item: $editingAlbum) { AlbumEditorView(album: $0) }
        .sheet(isPresented: $showingNewRecipeChoices) {
            NavigationStack { NewRecipeView() }
        }
    }
}

private struct AlbumRecipesView: View {
    @Environment(RecipeStore.self) private var store
    let albumId: String?
    @State private var search = ""
    @State private var showingNewRecipeChoices = false
    @State private var editingRecipe: Recipe?

    private var name: String {
        albumId.flatMap { id in store.albums.first(where: { $0.id == id })?.name } ?? "All Recipes"
    }

    private var recipes: [Recipe] {
        store.recipes.filter {
            (albumId == nil || $0.albumId == albumId) &&
            (search.isEmpty ||
             $0.title.localizedCaseInsensitiveContains(search) ||
             $0.ingredients.localizedCaseInsensitiveContains(search) ||
             $0.notes.localizedCaseInsensitiveContains(search))
        }
    }

    private var albumCover: URL? {
        guard let albumId else { return nil }
        return store.albums.first(where: { $0.id == albumId })?.customCoverImageUrl
            ?? store.recipes.first(where: { $0.albumId == albumId })?.photoUrls.first
    }

    var body: some View {
        LibraryBackground {
            ScrollView {
                VStack(spacing: 18) {
                    if let albumCover {
                        LocalPhoto(url: albumCover)
                            .frame(maxWidth: .infinity)
                            .frame(height: 185)
                            .clipped()
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(libraryGold.opacity(0.6))
                            )
                    }
                    SearchField(text: $search, prompt: "Search in \(name)…")
                    if recipes.isEmpty {
                        VStack(spacing: 14) {
                            Image(systemName: "fork.knife")
                                .font(.system(size: 44))
                                .foregroundStyle(libraryGold)
                            Text(search.isEmpty ? "No recipes in this album yet." : "No recipes match your search.")
                                .font(.system(size: 22, design: .serif))
                                .foregroundStyle(libraryCream)
                            if search.isEmpty {
                                Button("Add Recipe") { showingNewRecipeChoices = true }
                                    .buttonStyle(.borderedProminent)
                                    .tint(ButteryTheme.herb)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 100)
                    } else {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
                            ForEach(recipes) { recipe in
                                NavigationLink {
                                    RecipeDetailView(recipeId: recipe.id)
                                } label: {
                                    RecipeCardView(recipe: recipe)
                                }
                                .buttonStyle(.plain)
                                .contextMenu {
                                    Button("Edit Recipe", systemImage: "pencil") {
                                        editingRecipe = recipe
                                    }
                                    Button("Delete Recipe", systemImage: "trash", role: .destructive) {
                                        store.deleteRecipe(id: recipe.id)
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(18)
            }
        }
        .navigationTitle(name)
        .toolbar {
            Button("Add Recipe", systemImage: "plus") { showingNewRecipeChoices = true }
        }
        .sheet(isPresented: $showingNewRecipeChoices) {
            NavigationStack { NewRecipeView(defaultAlbumId: albumId) }
        }
        .sheet(item: $editingRecipe) {
            RecipeEditorView(recipe: $0)
        }
    }
}

struct RecipeDetailView: View {
    @Environment(RecipeStore.self) private var store
    @Environment(CookingSessionStore.self) private var cookingSessionStore
    @Environment(AppState.self) private var appState
    let recipeId: String
    @State private var editing = false
    @State private var sharing = false

    private var recipe: Recipe? { store.recipes.first { $0.id == recipeId } }

    var body: some View {
        LibraryBackground {
            ScrollView {
                if let recipe {
                    VStack(alignment: .leading, spacing: 20) {
                        if !recipe.photoUrls.isEmpty {
                            ScrollView(.horizontal) {
                                HStack(spacing: 12) {
                                    ForEach(recipe.photoUrls, id: \.self) {
                                        LocalPhoto(url: $0)
                                            .frame(width: 290, height: 190)
                                            .clipShape(RoundedRectangle(cornerRadius: 16))
                                    }
                                }
                            }
                            .scrollIndicators(.hidden)
                        }

                        VStack(alignment: .leading, spacing: 18) {
                            HStack(spacing: 7) {
                                Image(systemName: "frying.pan.fill")
                                Text("Cooking Mode Active")
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(ButteryTheme.herb, in: Capsule())

                            HStack(alignment: .top) {
                                Text(recipe.title)
                                    .font(.system(size: 34, design: .serif))
                                Spacer()
                                Button {
                                    store.toggleFavorite(id: recipe.id)
                                } label: {
                                    Image(systemName: recipe.isFavorite ? "heart.fill" : "heart")
                                        .font(.title2)
                                        .foregroundStyle(recipe.isFavorite ? Color(hex: 0xB95143) : paperInk)
                                }
                            }
                            HStack(spacing: 18) {
                                Fact(label: "Prep", value: recipe.prepTime)
                                Fact(label: "Cook", value: recipe.cookTime)
                                Fact(label: "Serves", value: recipe.servings)
                            }
                            if !recipe.notes.isEmpty { Text(recipe.notes).foregroundStyle(paperInk.opacity(0.75)) }
                            PaperSection(title: "Ingredients", text: recipe.ingredients)
                            PaperSection(title: "Instructions", text: recipe.instructions)
                            if let videoURL = recipe.videoUrl {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text("Video").font(.system(size: 24, design: .serif))
                                    VideoPlayer(player: AVPlayer(url: videoURL))
                                        .frame(height: 210)
                                        .clipShape(RoundedRectangle(cornerRadius: 14))
                                }
                            }
                            Button(role: .destructive) {
                                cookingSessionStore.clear()
                                appState.path.removeAll()
                            } label: {
                                Label("Finish Cooking", systemImage: "checkmark.circle")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.bordered)
                        }
                        .foregroundStyle(paperInk)
                        .padding(.horizontal, 22)
                        .padding(.top, 50)
                        .padding(.bottom, 22)
                        .background(paper, in: RoundedRectangle(cornerRadius: 20))
                        .overlay(alignment: .top) { BinderHoles() }
                    }
                    .padding(18)
                }
            }
        }
        .navigationTitle("Recipe")
        .onAppear {
            cookingSessionStore.recordRecipeOpened(recipeId)
            UIApplication.shared.isIdleTimerDisabled = true
        }
        .onDisappear {
            UIApplication.shared.isIdleTimerDisabled = false
        }
        .toolbar {
            if let recipe, !appState.isGuest {
                Button("Share", systemImage: "square.and.arrow.up") { sharing = true }
            }
            Button("Edit", systemImage: "pencil") { editing = true }
        }
        .sheet(isPresented: $editing) {
            if let recipe { RecipeEditorView(recipe: recipe) }
        }
        .sheet(isPresented: $sharing) {
            if let recipe { ShareRecipeView(recipe: recipe) }
        }
    }
}

struct RecipeEditorView: View {
    @Environment(RecipeStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let recipeId: String?
    let onSaved: ((String) -> Void)?
    @State private var draft: RecipeDraft
    @State private var selectedPhotos: [PhotosPickerItem] = []
    @State private var selectedVideo: PhotosPickerItem?
    @State private var newAlbumName = ""
    @State private var mediaMessage: String?

    init(
        recipe: Recipe? = nil,
        defaultAlbumId: String? = nil,
        initialDraft: RecipeDraft? = nil,
        onSaved: ((String) -> Void)? = nil
    ) {
        recipeId = recipe?.id
        self.onSaved = onSaved
        var draft = initialDraft ?? recipe.map(RecipeDraft.init) ?? RecipeDraft()
        if recipe == nil { draft.albumId = defaultAlbumId }
        _draft = State(initialValue: draft)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ButteryTheme.cream.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                    EditorSectionTitle("Recipe")
                    EditorField("Recipe title", text: $draft.title)
                        .onChange(of: draft.title) { _, value in
                            if value.count > 60 { draft.title = String(value.prefix(60)) }
                        }
                    Text("\(draft.title.count)/60")
                        .font(.caption)
                        .foregroundStyle(ButteryTheme.charcoal.opacity(0.55))
                    VStack(alignment: .leading, spacing: 10) {
                        Picker("Collection", selection: $draft.albumId) {
                            Text("All Recipes").tag(nil as String?)
                            ForEach(store.albums) { Text($0.name).tag($0.id as String?) }
                        }
                        .pickerStyle(.menu)
                        HStack {
                            TextField(
                                "",
                                text: $newAlbumName,
                                prompt: Text("New album name")
                                    .foregroundStyle(ButteryTheme.charcoal.opacity(0.72))
                                    .italic()
                            )
                            .onChange(of: newAlbumName) { _, value in
                                if value.count > 40 { newAlbumName = String(value.prefix(40)) }
                            }
                            Button("Create") {
                                draft.albumId = store.createAlbum(name: newAlbumName)
                                newAlbumName = ""
                            }
                            .buttonStyle(.bordered)
                            .tint(ButteryTheme.paprika)
                            .disabled(newAlbumName.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                    }
                    .padding(14)
                    .editorBox()

                    EditorSectionTitle("Timing")
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        EditorField("Prep time", text: $draft.prepTime)
                        EditorField("Cook time", text: $draft.cookTime)
                        EditorField("Total time", text: $draft.totalTime)
                        EditorField("Servings", text: $draft.servings)
                    }

                    EditorSectionTitle("Recipe Details")
                    EditorField("Ingredients", text: $draft.ingredients, axis: .vertical, lines: 5...12)
                    EditorField("Instructions", text: $draft.instructions, axis: .vertical, lines: 5...16)

                    EditorSectionTitle("Pictures")
                    PhotosPicker(
                        selection: $selectedPhotos,
                        maxSelectionCount: 8,
                        matching: .images
                    ) {
                        Label("Choose Pictures", systemImage: "photo.on.rectangle")
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(ButteryTheme.herb)
                    if !draft.photoUrls.isEmpty {
                        ScrollView(.horizontal) {
                            HStack {
                                ForEach(draft.photoUrls, id: \.self) { photoURL in
                                    ZStack(alignment: .topTrailing) {
                                        LocalPhoto(url: photoURL)
                                            .frame(width: 100, height: 80)
                                            .clipShape(RoundedRectangle(cornerRadius: 10))
                                        Button {
                                            draft.photoUrls.removeAll { $0 == photoURL }
                                        } label: {
                                            Image(systemName: "xmark.circle.fill")
                                                .foregroundStyle(.white, .black.opacity(0.72))
                                        }
                                        .padding(4)
                                    }
                                }
                            }
                        }
                    }
                    EditorSectionTitle("Video")
                    PhotosPicker(selection: $selectedVideo, matching: .videos) {
                        Label(draft.videoUrl == nil ? "Choose Video" : "Replace Video",
                              systemImage: "video")
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(ButteryTheme.herb)
                    Text("One video, 50 MB maximum.")
                        .font(.footnote).foregroundStyle(.secondary)
                    if let videoURL = draft.videoUrl {
                        VideoPlayer(player: AVPlayer(url: videoURL)).frame(height: 190)
                        Button("Remove Video", role: .destructive) { draft.videoUrl = nil }
                    }
                    if let mediaMessage {
                        Text(mediaMessage).foregroundStyle(.red)
                    }
                    }
                    .padding(20)
                    .padding(.bottom, 30)
                }
            }
            .foregroundStyle(ButteryTheme.charcoal)
            .navigationTitle(recipeId == nil ? "" : "Edit Recipe")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let savedId = store.saveRecipe(draft, id: recipeId)
                        onSaved?(savedId)
                        dismiss()
                    }
                    .disabled(draft.title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onChange(of: selectedPhotos) { _, items in
                Task {
                    for item in items {
                        if let data = try? await item.loadTransferable(type: Data.self),
                           let url = try? store.storePhoto(data: data) {
                            draft.photoUrls.append(url)
                        }
                    }
                    selectedPhotos = []
                }
            }
            .onChange(of: selectedVideo) { _, item in
                guard let item else { return }
                Task {
                    do {
                        guard let data = try await item.loadTransferable(type: Data.self) else {
                            throw MediaError.videoTooLarge
                        }
                        draft.videoUrl = try store.storeVideo(data: data)
                        mediaMessage = nil
                    } catch {
                        mediaMessage = error.localizedDescription
                    }
                    selectedVideo = nil
                }
            }
        }
    }
}

private struct AlbumEditorView: View {
    @Environment(RecipeStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let album: RecipeAlbum?
    @State private var name: String
    @State private var coverItem: PhotosPickerItem?
    @State private var coverURL: URL?
    @FocusState private var isNameFocused: Bool

    init(album: RecipeAlbum? = nil) {
        self.album = album
        _name = State(initialValue: album?.name ?? "")
        _coverURL = State(initialValue: album?.customCoverImageUrl)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ButteryTheme.cream.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        Text(album == nil ? "Create an Album" : "Edit Album")
                            .font(.system(size: 32, design: .serif))
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Album name")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(ButteryTheme.charcoal.opacity(0.62))
                            TextField("Enter album name", text: $name)
                                .focused($isNameFocused)
                                .textInputAutocapitalization(.words)
                                .submitLabel(.done)
                                .contentShape(Rectangle())
                                .onChange(of: name) { _, value in
                                    if value.count > 40 { name = String(value.prefix(40)) }
                                }
                            Text("\(name.count)/40")
                                .font(.caption)
                                .foregroundStyle(ButteryTheme.charcoal.opacity(0.55))
                        }
                        .padding(14)
                        .editorBox()

                        VStack(alignment: .leading, spacing: 14) {
                            Text("Cover Picture")
                                .font(.system(size: 24, design: .serif))
                            PhotosPicker(selection: $coverItem, matching: .images) {
                                Label(coverURL == nil ? "Choose Cover Picture" : "Change Cover Picture",
                                      systemImage: "photo")
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(ButteryTheme.herb)
                            if let coverURL {
                                LocalPhoto(url: coverURL)
                                    .frame(height: 210)
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                                Button("Remove Cover", role: .destructive) { self.coverURL = nil }
                            }
                        }
                        .padding(17)
                        .editorBox()
                    }
                    .padding(20)
                }
            }
            .foregroundStyle(ButteryTheme.charcoal)
            .preferredColorScheme(.light)
            .navigationTitle("")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if let album {
                            store.updateAlbum(id: album.id, name: name, cover: coverURL)
                        } else {
                            _ = store.createAlbum(name: name, cover: coverURL)
                        }
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onChange(of: coverItem) { _, item in
                Task {
                    if let data = try? await item?.loadTransferable(type: Data.self) {
                        coverURL = try? store.storePhoto(data: data)
                    }
                }
            }
            .onChange(of: coverURL) { _, _ in
                // Return keyboard focus to the name field when a cover is chosen first.
                if name.isEmpty {
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(150))
                        isNameFocused = true
                    }
                }
            }
        }
    }
}

private struct AlbumCardModel: Identifiable {
    let id: String?
    let name: String
    let count: Int
    let cover: URL?
}

private struct AlbumCardView: View {
    let album: AlbumCardModel
    var body: some View {
        VStack(spacing: 7) {
            CardBackground(url: album.cover)
                .frame(maxWidth: .infinity)
                .frame(height: 125)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(libraryGold.opacity(0.6)))
            Text(album.name)
                .font(.system(size: 19, design: .serif))
                .lineLimit(2)
                .minimumScaleFactor(0.55)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity)
            Text("\(album.count) \(album.count == 1 ? "recipe" : "recipes")")
                .font(.caption)
                .foregroundStyle(libraryMuted)
            }
        .foregroundStyle(libraryCream)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 180, alignment: .top)
    }
}

private struct RecipeCardView: View {
    @Environment(RecipeStore.self) private var store
    let recipe: Recipe
    var body: some View {
        VStack(spacing: 7) {
            ZStack(alignment: .topTrailing) {
                CardBackground(url: recipe.photoUrls.first)
                    .frame(maxWidth: .infinity)
                    .frame(height: 130)
                    .clipped()
                Button {
                    store.toggleFavorite(id: recipe.id)
                } label: {
                    Image(systemName: recipe.isFavorite ? "heart.fill" : "heart")
                        .foregroundStyle(recipe.isFavorite ? Color(hex: 0xD97B68) : libraryCream)
                        .padding(9).background(.black.opacity(0.45), in: Circle())
                }
                .padding(8)
            }
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(libraryGold.opacity(0.55)))
            Text(recipe.title)
                .font(.system(size: 18, design: .serif))
                .lineLimit(2)
                .minimumScaleFactor(0.55)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity)
            if !recipe.cookTime.isEmpty { Text("Cook \(recipe.cookTime)").font(.caption) }
        }
        .foregroundStyle(libraryCream)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 190, alignment: .top)
    }
}

private struct LibraryBackground<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        ZStack {
            RadialGradient(colors: [Color(hex: 0x3A2A18), libraryBackground],
                           center: .center, startRadius: 10, endRadius: 700)
                .ignoresSafeArea()
            content
        }
    }
}

private struct SearchField: View {
    @Binding var text: String
    let prompt: String
    var body: some View {
        HStack {
            Image(systemName: "magnifyingglass")
            TextField(prompt, text: $text)
            if !text.isEmpty { Button { text = "" } label: { Image(systemName: "xmark.circle.fill") } }
        }
        .foregroundStyle(libraryCream)
        .padding(13)
        .background(.black.opacity(0.3), in: Capsule())
        .overlay(Capsule().stroke(libraryGold.opacity(0.65)))
    }
}

private struct EmptyLibraryView: View {
    let add: () -> Void
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "fork.knife").font(.system(size: 54)).foregroundStyle(libraryGold)
            Text("No recipes saved yet.").font(.system(size: 26, design: .serif)).foregroundStyle(libraryCream)
            Button("Add Your First Recipe", action: add).buttonStyle(.borderedProminent).tint(ButteryTheme.herb)
        }
        .frame(maxWidth: .infinity).padding(.top, 70)
    }
}

private struct CardBackground: View {
    let url: URL?
    var body: some View {
        if let url {
            LocalPhoto(url: url)
        } else {
            LinearGradient(colors: [Color(hex: 0x70512E), Color(hex: 0x151510)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
                .overlay(Image(systemName: "fork.knife").font(.largeTitle).foregroundStyle(libraryGold))
        }
    }
}

private struct LocalPhoto: View {
    let url: URL
    var body: some View {
        if url.isFileURL, let image = UIImage(contentsOfFile: url.path) {
            Image(uiImage: image).resizable().scaledToFill()
        } else if !url.isFileURL {
            AsyncImage(url: url) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else if phase.error != nil {
                    Color.black.overlay(Image(systemName: "photo").foregroundStyle(.white.opacity(0.7)))
                } else {
                    Color.black.overlay(ProgressView().tint(.white))
                }
            }
        } else {
            Color.black.overlay(Image(systemName: "photo").foregroundStyle(.white.opacity(0.7)))
        }
    }
}

private struct Fact: View {
    let label: String
    let value: String
    var body: some View {
        if !value.isEmpty {
            VStack(alignment: .leading) {
                Text(label).font(.caption).foregroundStyle(paperInk.opacity(0.55))
                Text(value).fontWeight(.semibold)
            }
        }
    }
}

private struct PaperSection: View {
    let title: String
    let text: String
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.system(size: 24, design: .serif))
            Text(text.isEmpty ? "None added." : text).lineSpacing(5)
        }
    }
}

private struct BinderHoles: View {
    var body: some View {
        HStack(spacing: 76) {
            ForEach(0..<3, id: \.self) { _ in
                Circle()
                    .fill(Color(hex: 0x16130F))
                    .frame(width: 16, height: 16)
                    .overlay(Circle().stroke(Color(hex: 0x5A4630), lineWidth: 4))
            }
        }
        .padding(.top, 11)
        .allowsHitTesting(false)
    }
}

private struct EditorField: View {
    let label: String
    @Binding var text: String
    let axis: Axis
    let lines: ClosedRange<Int>?

    init(
        _ label: String,
        text: Binding<String>,
        axis: Axis = .horizontal,
        lines: ClosedRange<Int>? = nil
    ) {
        self.label = label
        _text = text
        self.axis = axis
        self.lines = lines
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(ButteryTheme.charcoal.opacity(0.62))
            if let lines {
                TextField("", text: $text, axis: axis)
                    .lineLimit(lines)
            } else {
                TextField("", text: $text, axis: axis)
                    .lineLimit(1)
            }
        }
        .padding(14)
        .editorBox()
    }
}

@ViewBuilder
private func EditorSectionTitle(_ title: String) -> some View {
    Text(title)
        .font(.system(size: 25, design: .serif))
        .foregroundStyle(ButteryTheme.charcoal)
}

private extension View {
    func editorBox() -> some View {
        background(Color.white.opacity(0.72), in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(ButteryTheme.paprika.opacity(0.24), lineWidth: 1)
            )
    }
}
