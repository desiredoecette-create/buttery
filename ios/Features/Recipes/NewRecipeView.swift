import PhotosUI
import SwiftUI

struct NewRecipeView: View {
    let defaultAlbumId: String?
    @State private var showManual = false
    @State private var showImport = false
    @State private var savedRecipeId: String?
    @State private var showSavedRecipe = false

    init(defaultAlbumId: String? = nil) {
        self.defaultAlbumId = defaultAlbumId
    }

    var body: some View {
        ZStack {
            ButteryTheme.cream.ignoresSafeArea()
            VStack(spacing: 22) {
                Text("Add a New Recipe")
                    .font(.system(size: 34, design: .serif))
                    .foregroundStyle(ButteryTheme.charcoal)
                Text("How would you like to begin?")
                    .foregroundStyle(ButteryTheme.charcoal.opacity(0.65))

                ChoiceCard(
                    title: "Create Manually",
                    subtitle: "Enter your own ingredients, instructions, timing, and pictures.",
                    symbol: "square.and.pencil"
                ) { showManual = true }

                ChoiceCard(
                    title: "Import Recipe",
                    subtitle: "Use a recipe URL or a picture of a recipe.",
                    symbol: "square.and.arrow.down"
                ) { showImport = true }
            }
            .padding(20)
        }
        .sheet(isPresented: $showManual) {
            RecipeEditorView(defaultAlbumId: defaultAlbumId, onSaved: openSavedRecipe)
        }
        .sheet(isPresented: $showImport) {
            ImportRecipeView(defaultAlbumId: defaultAlbumId, onSaved: openSavedRecipe)
        }
        .navigationDestination(isPresented: $showSavedRecipe) {
            if let savedRecipeId { RecipeDetailView(recipeId: savedRecipeId) }
        }
    }

    private func openSavedRecipe(_ id: String) {
        savedRecipeId = id
        showManual = false
        showImport = false
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(350))
            showSavedRecipe = true
        }
    }
}

private struct ChoiceCard: View {
    let title: String
    let subtitle: String
    let symbol: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 18) {
                Image(systemName: symbol)
                    .font(.system(size: 30))
                    .frame(width: 60, height: 60)
                    .foregroundStyle(.white)
                    .background(ButteryTheme.paprika, in: Circle())
                VStack(alignment: .leading, spacing: 6) {
                    Text(title).font(.system(size: 23, design: .serif))
                    Text(subtitle).font(.subheadline).foregroundStyle(ButteryTheme.charcoal.opacity(0.65))
                }
                Spacer()
                Image(systemName: "chevron.right")
            }
            .foregroundStyle(ButteryTheme.charcoal)
            .padding(18)
            .background(.white.opacity(0.7), in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(ButteryTheme.paprika.opacity(0.3)))
        }
        .buttonStyle(.plain)
    }
}

private struct ImportRecipeView: View {
    @Environment(\.dismiss) private var dismiss
    let defaultAlbumId: String?
    let onSaved: (String) -> Void
    @State private var mode: ImportMode = .url
    @State private var url = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var importedDraft = RecipeDraft()
    @State private var showReview = false
    @State private var isWorking = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            ZStack {
                ButteryTheme.cream.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        Text("Import a Recipe")
                            .font(.system(size: 32, design: .serif))
                            .foregroundStyle(ButteryTheme.charcoal)

                        Picker("Import method", selection: $mode) {
                            ForEach(ImportMode.allCases) {
                                Label($0.title, systemImage: $0.symbol).tag($0)
                            }
                        }
                        .pickerStyle(.segmented)

                        switch mode {
                        case .url:
                            VStack(alignment: .leading, spacing: 14) {
                                Text("Recipe URL").font(.system(size: 23, design: .serif))
                                TextField("", text: $url)
                                    .textInputAutocapitalization(.never)
                                    .keyboardType(.URL)
                                    .padding(13)
                                    .background(.white.opacity(0.7), in: RoundedRectangle(cornerRadius: 10))
                                Button("Import from URL") {
                                    runImport { try await RecipeImporter.importURL(url) }
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(ButteryTheme.herb)
                                .disabled(url.trimmingCharacters(in: .whitespaces).isEmpty || isWorking)
                            }
                            .importCard()
                        case .photo:
                            VStack(alignment: .leading, spacing: 14) {
                                Text("Recipe Picture").font(.system(size: 23, design: .serif))
                                PhotosPicker(selection: $photoItem, matching: .images) {
                                    Label("Choose Recipe Picture", systemImage: "text.viewfinder")
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(ButteryTheme.herb)
                                Text("Buttery uses on-device text recognition, then lets you review every field.")
                                    .font(.footnote)
                                    .foregroundStyle(ButteryTheme.charcoal.opacity(0.65))
                            }
                            .importCard()
                        }

                        if isWorking {
                            HStack { ProgressView(); Text("Reading recipe…") }
                        }
                        if let errorMessage {
                            Text(errorMessage).foregroundStyle(.red)
                        }
                    }
                    .padding(20)
                }
            }
            .foregroundStyle(ButteryTheme.charcoal)
            .preferredColorScheme(.light)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .onChange(of: photoItem) { _, item in
                guard let item else { return }
                runImport {
                    guard let data = try await item.loadTransferable(type: Data.self) else {
                        throw RecipeImporter.ImportError.invalidImage
                    }
                    let text = try await RecipeImporter.recognizeText(in: data)
                    return RecipeImporter.parseText(text)
                }
            }
            .sheet(isPresented: $showReview) {
                RecipeEditorView(defaultAlbumId: defaultAlbumId, initialDraft: importedDraft) { id in
                    showReview = false
                    dismiss()
                    onSaved(id)
                }
            }
        }
    }

    private func runImport(_ operation: @escaping () async throws -> RecipeDraft) {
        isWorking = true
        errorMessage = nil
        Task {
            do {
                importedDraft = try await operation()
                showReview = true
            } catch {
                errorMessage = error.localizedDescription
            }
            isWorking = false
        }
    }
}

private extension View {
    func importCard() -> some View {
        padding(17)
            .background(Color.white.opacity(0.56), in: RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(ButteryTheme.paprika.opacity(0.25))
            )
    }
}

private enum ImportMode: String, CaseIterable, Identifiable {
    case url, photo
    var id: String { rawValue }
    var title: String {
        switch self { case .url: "URL"; case .photo: "Photo" }
    }
    var symbol: String {
        switch self { case .url: "link"; case .photo: "camera" }
    }
}
