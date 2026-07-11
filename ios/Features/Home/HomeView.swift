import SwiftUI

struct HomeView: View {
    @Environment(AppState.self) private var appState
    @Environment(RecipeStore.self) private var recipeStore
    @Environment(CookingSessionStore.self) private var cookingSessionStore
    @Environment(AuthService.self) private var authService
    @Environment(SharingService.self) private var sharingService
    @State private var inactivityTask: Task<Void, Never>?
    @State private var tileImages = Array(Self.foodImagePool.prefix(6))

    private static let foodImagePool = [
        "ambient_pancakes", "ambient_salad", "ambient_salmon", "ambient_bread",
        "ambient_extra_01", "ambient_extra_02", "ambient_extra_03", "ambient_extra_04",
        "ambient_extra_05", "ambient_extra_06", "ambient_extra_07", "ambient_extra_08",
        "ambient_extra_09", "ambient_extra_10"
    ]

    private var activeRecipe: Recipe? {
        guard let id = cookingSessionStore.session?.recipeId else { return nil }
        return recipeStore.recipes.first { $0.id == id }
    }

    private var tiles: [HomeTile] {
        [
            .init(.recipes, image: tileImage(at: 0), symbol: "magnifyingglass", title: "My Recipes"),
            .init(.explore, image: tileImage(at: 5), symbol: "globe.americas.fill", title: "Explore Recipes"),
            .init(
                .continueRecipe,
                image: tileImage(at: 2),
                symbol: "play.fill",
                title: activeRecipe?.title,
                photo: activeRecipe?.photoUrls.first,
                eyebrow: activeRecipe == nil ? nil : "CONTINUE RECIPE",
                footer: activeRecipe == nil ? nil : "\(relativeLastOpened)  •  Resume →"
            ),
            .init(.favorites, image: tileImage(at: 3), symbol: "heart"),
            .init(.grocery, image: tileImage(at: 4), symbol: "cart"),
            .init(.newRecipe, image: tileImage(at: 1), symbol: "plus")
        ]
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                BundledImage(name: "home_background")
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .clipped()

                LinearGradient(
                    colors: [
                        Color(hex: 0x15130F, alpha: 0.96),
                        Color(hex: 0x211B13, alpha: 0.88),
                        Color(hex: 0x2F2418, alpha: 0.78)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 18) {
                        header

                        LazyVGrid(
                            columns: [
                                GridItem(.flexible(), spacing: 12),
                                GridItem(.flexible(), spacing: 12)
                            ],
                            spacing: 12
                        ) {
                            ForEach(tiles) { tile in
                                HomeTileView(tile: tile) {
                                    appState.path.append(tile.destination)
                                }
                            }
                        }
                        .padding(.top, 14)
                    }
                    .frame(width: max(0, geometry.size.width - 32))
                    .padding(.horizontal, 16)
                    .padding(.top, geometry.size.height * 0.18)
                    .padding(.bottom, 112)
                }
                .scrollIndicators(.hidden)

                brandFooter
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .padding(.bottom, 8)
                    .allowsHitTesting(false)
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
        }
        .background(Color(hex: 0x15130F))
        .ignoresSafeArea()
        .safeAreaPadding(.top, 10)
        .toolbar(.hidden, for: .navigationBar)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in restartInactivityTimer() }
        )
        .onAppear {
            tileImages = Array(Self.foodImagePool.shuffled().prefix(6))
            restartInactivityTimer()
        }
        .onDisappear {
            inactivityTask?.cancel()
            inactivityTask = nil
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 5) {
                Text("Welcome back!")
                    .font(.system(size: 31, weight: .regular, design: .serif))
                    .foregroundStyle(ButteryTheme.cream)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text("What would you like to cook today?")
                    .font(.system(size: 14))
                    .foregroundStyle(ButteryTheme.cream.opacity(0.8))
                    .lineLimit(2)
            }

            Spacer(minLength: 8)

            Menu {
                if !appState.isGuest {
                    if let profile = authService.profile {
                        Button("@\(profile.username)", systemImage: "person.crop.circle.fill") {
                            appState.path.append(.myProfile)
                        }
                    }
                    Button("Inbox", systemImage: sharingService.hasUnread ? "tray.full.fill" : "tray.full") {
                        appState.path.append(.inbox)
                    }
                    Button("Settings", systemImage: "gearshape") {
                        appState.path.append(.settings)
                    }
                    Button("Sign Out", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) {
                        try? authService.signOut()
                        appState.path.removeAll()
                    }
                } else {
                    Button("Sign In or Create Account", systemImage: "person.crop.circle.badge.plus") {
                        appState.isGuest = false
                        appState.path.removeAll()
                    }
                }
            } label: {
                ZStack(alignment: .topTrailing) {
                    dashboardAvatar
                    if sharingService.hasUnread {
                        Circle().fill(.red).frame(width: 12, height: 12)
                            .overlay(Circle().stroke(.white, lineWidth: 2))
                    }
                }
            }
            .accessibilityLabel("Profile")
        }
    }

    @ViewBuilder
    private var dashboardAvatar: some View {
        if let value = authService.profile?.profilePhotoUrl,
           let url = URL(string: value) {
            ButteryRemoteImage(url: url, maxPixelDimension: 220) {
                genericDashboardAvatar
            }
            .frame(width: 40, height: 40)
            .clipShape(Circle())
            .overlay(Circle().stroke(ButteryTheme.cream.opacity(0.8), lineWidth: 1))
        } else {
            genericDashboardAvatar
        }
    }

    private var genericDashboardAvatar: some View {
        Image(systemName: "person.crop.circle.fill")
            .font(.system(size: 40))
            .foregroundStyle(ButteryTheme.cream)
            .frame(width: 40, height: 40)
    }

    private var brandFooter: some View {
        VStack(alignment: .center, spacing: 0) {
            BundledImage(name: "buttery_wordmark", contentMode: .fit)
                .frame(width: 150, height: 70)
            Text("your personal kitchen dashboard")
                .font(.caption2)
                .tracking(1.1)
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
        }
    }

    private func restartInactivityTimer() {
        inactivityTask?.cancel()
        inactivityTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(20))
            guard !Task.isCancelled, appState.path.isEmpty else { return }
            withAnimation(.easeInOut(duration: 0.45)) {
                appState.isShowingAmbient = true
            }
        }
    }

    private func tileImage(at index: Int) -> String {
        tileImages.indices.contains(index) ? tileImages[index] : Self.foodImagePool[index]
    }

    private var relativeLastOpened: String {
        guard let milliseconds = cookingSessionStore.session?.lastOpenedTimestamp else { return "Recently" }
        let date = Date(timeIntervalSince1970: TimeInterval(milliseconds) / 1_000)
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: .now)
    }
}

private struct HomeTile: Identifiable {
    let destination: AppDestination
    let image: String
    let symbol: String
    let title: String?
    let photo: URL?
    let eyebrow: String?
    let footer: String?

    init(
        _ destination: AppDestination,
        image: String,
        symbol: String,
        title: String? = nil,
        photo: URL? = nil,
        eyebrow: String? = nil,
        footer: String? = nil
    ) {
        self.destination = destination
        self.image = image
        self.symbol = symbol
        self.title = title
        self.photo = photo
        self.eyebrow = eyebrow
        self.footer = footer
    }

    var id: AppDestination { destination }
}

private struct HomeTileView: View {
    let tile: HomeTile
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            GeometryReader { geometry in
                ZStack {
                    if let photo = tile.photo {
                        ButteryRemoteImage(url: photo, maxPixelDimension: 650) {
                            BundledImage(name: tile.image)
                        }
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                    } else {
                        BundledImage(name: tile.image)
                            .frame(width: geometry.size.width, height: geometry.size.height)
                            .clipped()
                    }

                    LinearGradient(
                        colors: [.black.opacity(0.03), .black.opacity(0.16), .black.opacity(0.92)],
                        startPoint: .top,
                        endPoint: .bottom
                    )

                    if tile.eyebrow == nil {
                        Image(systemName: tile.symbol)
                            .font(.system(size: 19, weight: .semibold))
                            .foregroundStyle(ButteryTheme.cream)
                            .frame(width: 48, height: 48)
                            .background(.black.opacity(0.42), in: Circle())
                            .overlay(Circle().stroke(ButteryTheme.cream.opacity(0.78)))
                            .offset(y: -8)
                    }

                    VStack(alignment: tile.eyebrow == nil ? .center : .leading, spacing: 2) {
                        if let eyebrow = tile.eyebrow {
                            Text(eyebrow).font(.system(size: 9, weight: .bold)).tracking(1)
                        }
                        Text(tile.title ?? tile.destination.title)
                            .font(.system(size: 15, weight: .semibold))
                            .lineLimit(2)
                            .minimumScaleFactor(0.78)
                        if let footer = tile.footer {
                            Text(footer).font(.system(size: 9)).opacity(0.82).lineLimit(1)
                        }
                    }
                    .foregroundStyle(ButteryTheme.cream)
                    .multilineTextAlignment(tile.eyebrow == nil ? .center : .leading)
                    .padding(.horizontal, 10)
                    .padding(.bottom, 10)
                    .frame(
                        maxWidth: .infinity,
                        maxHeight: .infinity,
                        alignment: tile.eyebrow == nil ? .bottom : .bottomLeading
                    )
                }
                .frame(width: geometry.size.width, height: geometry.size.height)
                .clipped()
            }
            .frame(height: 132)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .contentShape(RoundedRectangle(cornerRadius: 10))
            .shadow(color: .black.opacity(0.35), radius: 6, y: 4)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(tile.destination.title)
    }
}
