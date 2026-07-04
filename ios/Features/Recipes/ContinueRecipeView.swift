import SwiftUI

struct ContinueRecipeView: View {
    @Environment(AppState.self) private var appState
    @Environment(RecipeStore.self) private var recipeStore
    @Environment(CookingSessionStore.self) private var cookingSessionStore

    private var recipe: Recipe? {
        guard let id = cookingSessionStore.session?.recipeId else { return nil }
        return recipeStore.recipes.first { $0.id == id }
    }

    var body: some View {
        Group {
            if let recipe {
                RecipeDetailView(recipeId: recipe.id)
            } else {
                ZStack {
                    RadialGradient(
                        colors: [Color(hex: 0x49331E), Color(hex: 0x0A0A08)],
                        center: .center,
                        startRadius: 20,
                        endRadius: 500
                    )
                    .ignoresSafeArea()

                    VStack(spacing: 20) {
                        Image(systemName: "fork.knife")
                            .font(.system(size: 50))
                            .foregroundStyle(ButteryTheme.butter)
                        Text("No recipe in progress yet.")
                            .font(.system(size: 30, design: .serif))
                            .multilineTextAlignment(.center)
                        Text("Open a recipe and Buttery will remember it here.")
                            .foregroundStyle(ButteryTheme.cream.opacity(0.72))
                            .multilineTextAlignment(.center)

                        VStack(spacing: 12) {
                            Button("Browse Recipes") {
                                replaceDestination(with: .recipes)
                            }
                            .buttonStyle(.bordered)

                            Button("Add Your First Recipe") {
                                replaceDestination(with: .newRecipe)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(ButteryTheme.paprika)
                        }
                    }
                    .foregroundStyle(ButteryTheme.cream)
                    .padding(28)
                }
                .onAppear {
                    if cookingSessionStore.session != nil { cookingSessionStore.clear() }
                }
            }
        }
        .navigationTitle("Continue Recipe")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func replaceDestination(with destination: AppDestination) {
        if !appState.path.isEmpty { appState.path.removeLast() }
        appState.path.append(destination)
    }
}
