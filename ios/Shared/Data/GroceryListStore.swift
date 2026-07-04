import Foundation
import Observation

enum GroceryListMode: String, Codable, CaseIterable, Identifiable {
    case type = "TYPE"
    case draw = "DRAW"

    var id: String { rawValue }
}

struct GroceryPoint: Codable, Equatable {
    let x: Double
    let y: Double
}

struct GroceryStroke: Codable, Equatable, Identifiable {
    let id: UUID
    var points: [GroceryPoint]
    let colorARGB: UInt32
    let width: Double
    let isEraser: Bool

    init(
        id: UUID = UUID(),
        points: [GroceryPoint],
        colorARGB: UInt32,
        width: Double,
        isEraser: Bool = false
    ) {
        self.id = id
        self.points = points
        self.colorARGB = colorARGB
        self.width = width
        self.isEraser = isEraser
    }
}

struct GroceryListState: Codable, Equatable {
    var mode: GroceryListMode = .type
    var typedText = ""
    var strokes: [GroceryStroke] = []
}

@MainActor
@Observable
final class GroceryListStore {
    private(set) var state: GroceryListState
    private let defaults: UserDefaults
    private let key = "buttery.groceryList"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: key),
           let decoded = try? JSONDecoder().decode(GroceryListState.self, from: data) {
            state = decoded
        } else {
            state = GroceryListState()
        }
    }

    func setMode(_ mode: GroceryListMode) {
        state.mode = mode
        persist()
    }

    func setTypedText(_ text: String) {
        state.typedText = text
        persist()
    }

    func setStrokes(_ strokes: [GroceryStroke]) {
        state.strokes = strokes
        persist()
    }

    func clearTypedList() {
        state.typedText = ""
        persist()
    }

    func clearDrawing() {
        state.strokes = []
        persist()
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(state) else { return }
        defaults.set(data, forKey: key)
    }
}
