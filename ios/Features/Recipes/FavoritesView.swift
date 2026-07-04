import SwiftUI
import UIKit

struct FavoritesView: View {
    @Environment(AppState.self) private var appState
    @Environment(RecipeStore.self) private var store
    @State private var search = ""

    private let cream = Color(hex: 0xF4EFE3)
    private let muted = Color(hex: 0xC5BBA9)
    private let gold = Color(hex: 0xC4A46B)

    private var favoriteCount: Int {
        store.recipes.count { $0.isFavorite }
    }

    private var favorites: [Recipe] {
        store.recipes.filter { recipe in
            guard recipe.isFavorite else { return false }
            let query = search.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !query.isEmpty else { return true }
            let collection = recipe.albumId.flatMap { id in
                store.albums.first { $0.id == id }?.name
            } ?? "All Recipes"
            return recipe.title.localizedCaseInsensitiveContains(query)
                || recipe.ingredients.localizedCaseInsensitiveContains(query)
                || recipe.instructions.localizedCaseInsensitiveContains(query)
                || recipe.notes.localizedCaseInsensitiveContains(query)
                || collection.localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Color(hex: 0x3A2A18), Color(hex: 0x11110E)],
                center: .center,
                startRadius: 10,
                endRadius: 700
            )
            .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Favorite Recipes")
                        .font(.system(size: 34, design: .serif))
                        .foregroundStyle(cream)
                    Text("\(favoriteCount) \(favoriteCount == 1 ? "favorite" : "favorites")")
                        .foregroundStyle(muted)
                }

                favoritesSearch

                if favoriteCount == 0 {
                    emptyFavorites
                } else if favorites.isEmpty {
                    noResults
                } else {
                    ScrollView {
                        LazyVGrid(
                            columns: [GridItem(.flexible()), GridItem(.flexible())],
                            spacing: 14
                        ) {
                            ForEach(favorites) { recipe in
                                NavigationLink {
                                    RecipeDetailView(recipeId: recipe.id)
                                } label: {
                                    FavoriteRecipeCard(
                                        recipe: recipe,
                                        collectionName: collectionName(for: recipe)
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.bottom, 30)
                    }
                    .scrollIndicators(.hidden)
                }
            }
            .padding(18)
        }
        .navigationTitle("Favorites")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var favoritesSearch: some View {
        HStack {
            Image(systemName: "magnifyingglass")
            TextField("Search favorites…", text: $search)
            if !search.isEmpty {
                Button { search = "" } label: { Image(systemName: "xmark.circle.fill") }
            }
        }
        .foregroundStyle(cream)
        .padding(13)
        .background(.black.opacity(0.3), in: Capsule())
        .overlay(Capsule().stroke(gold.opacity(0.65)))
    }

    private var emptyFavorites: some View {
        VStack(spacing: 14) {
            Spacer()
            Image(systemName: "heart")
                .font(.system(size: 54))
                .foregroundStyle(ButteryTheme.butter)
            Text("No favorite recipes yet.")
                .font(.system(size: 29, design: .serif))
                .foregroundStyle(cream)
                .multilineTextAlignment(.center)
            Text("Tap the heart on any recipe to save it here.")
                .foregroundStyle(muted)
                .multilineTextAlignment(.center)
            VStack(spacing: 11) {
                Button("Browse Recipes") { replaceDestination(with: .recipes) }
                    .buttonStyle(.borderedProminent)
                    .tint(ButteryTheme.paprika)
                Button("Home") { appState.path.removeAll() }
                    .buttonStyle(.borderedProminent)
                    .tint(ButteryTheme.herb)
            }
            .padding(.top, 6)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var noResults: some View {
        VStack(spacing: 14) {
            Spacer()
            Image(systemName: "magnifyingglass")
                .font(.system(size: 44))
                .foregroundStyle(gold)
            Text("No favorite recipes match your search.")
                .font(.system(size: 25, design: .serif))
                .foregroundStyle(cream)
                .multilineTextAlignment(.center)
            Button("Clear Search") { search = "" }
                .buttonStyle(.bordered)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private func collectionName(for recipe: Recipe) -> String {
        recipe.albumId.flatMap { id in store.albums.first { $0.id == id }?.name } ?? "All Recipes"
    }

    private func replaceDestination(with destination: AppDestination) {
        if !appState.path.isEmpty { appState.path.removeLast() }
        appState.path.append(destination)
    }
}

private struct FavoriteRecipeCard: View {
    @Environment(RecipeStore.self) private var store
    let recipe: Recipe
    let collectionName: String

    var body: some View {
        VStack(spacing: 7) {
            ZStack(alignment: .topTrailing) {
                FavoriteCardBackground(url: recipe.photoUrls.first)
                    .frame(maxWidth: .infinity)
                    .frame(height: 130)
                    .clipped()
                Button {
                    store.toggleFavorite(id: recipe.id)
                } label: {
                    Image(systemName: "heart.fill")
                        .foregroundStyle(Color(hex: 0xD97B68))
                        .padding(10)
                        .background(.black.opacity(0.5), in: Circle())
                }
                .accessibilityLabel("Remove favorite")
                .padding(8)
            }
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color(hex: 0xC4A46B).opacity(0.58))
            )
            VStack(spacing: 4) {
                Text(recipe.title)
                    .font(.system(size: 18, design: .serif))
                    .lineLimit(2)
                    .minimumScaleFactor(0.55)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                Text(collectionName)
                    .font(.caption)
                    .foregroundStyle(Color(hex: 0xC4A46B))
                    .lineLimit(1)
                HStack(spacing: 10) {
                    if !recipe.cookTime.isEmpty { Text("Cook \(recipe.cookTime)") }
                    if !recipe.servings.isEmpty { Text("\(recipe.servings) servings") }
                }
                .font(.caption2)
                .foregroundStyle(Color(hex: 0xC5BBA9))
            }
        }
        .foregroundStyle(Color(hex: 0xF4EFE3))
        .frame(minHeight: 205, alignment: .top)
        .shadow(color: .black.opacity(0.32), radius: 5, y: 3)
    }
}

private struct FavoriteCardBackground: View {
    let url: URL?

    var body: some View {
        if let url, url.isFileURL, let image = UIImage(contentsOfFile: url.path) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
        } else if let url, !url.isFileURL {
            AsyncImage(url: url) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    fallback
                }
            }
        } else {
            fallback
        }
    }

    private var fallback: some View {
        LinearGradient(
            colors: [Color(hex: 0x70512E), Color(hex: 0x2E291F), Color(hex: 0x151510)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .overlay(
            Image(systemName: "fork.knife")
                .font(.largeTitle)
                .foregroundStyle(Color(hex: 0xC4A46B).opacity(0.72))
        )
    }
}
