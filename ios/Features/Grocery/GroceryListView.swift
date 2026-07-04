import SwiftUI

private let groceryNavy = Color(hex: 0x111A23)
private let groceryPanel = Color(hex: 0x1B2732)
private let groceryCream = Color(hex: 0xF5EEDC)
private let groceryInk = Color(hex: 0x332D26)
private let groceryButter = Color(hex: 0xFFC857)
private let grocerySage = Color(hex: 0x71815B)
private let paperLine = Color(hex: 0x9AAFC0, alpha: 0.46)
private let marginRed = Color(hex: 0xB66B67, alpha: 0.56)

struct GroceryListView: View {
    @Environment(AppState.self) private var appState
    @Environment(GroceryListStore.self) private var store
    @State private var displayedText = ""
    @State private var strokes: [GroceryStroke] = []
    @State private var redoStrokes: [GroceryStroke] = []
    @State private var showClearConfirmation = false
    @State private var ink = InkChoice.choices[0]
    @State private var stroke = StrokeChoice.choices[1]

    private var mode: GroceryListMode { store.state.mode }

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Color(hex: 0x293A43), groceryNavy],
                center: .center,
                startRadius: 10,
                endRadius: 650
            )
            .ignoresSafeArea()

            VStack(spacing: 14) {
                header

                VStack(spacing: 0) {
                    if mode == .draw {
                        DrawingToolbar(
                            ink: $ink,
                            stroke: $stroke,
                            canUndo: !strokes.isEmpty,
                            canRedo: !redoStrokes.isEmpty,
                            undo: undo,
                            redo: redo
                        )
                    }

                    LinedNotebook(
                        mode: mode,
                        displayedText: $displayedText,
                        strokes: $strokes,
                        ink: ink,
                        strokeChoice: stroke,
                        textChanged: persistTypedText,
                        strokeFinished: addStroke
                    )
                }
                .background(groceryCream)
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .shadow(color: .black.opacity(0.4), radius: 12, y: 7)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            displayedText = bulletize(store.state.typedText)
            strokes = store.state.strokes
        }
        .confirmationDialog(
            mode == .type ? "Clear typed list?" : "Clear drawing?",
            isPresented: $showClearConfirmation,
            titleVisibility: .visible
        ) {
            Button("Clear", role: .destructive, action: clearCurrentMode)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(
                mode == .type
                    ? "This only clears the typed list. Your drawing will be kept."
                    : "This only clears the drawing. Your typed list will be kept."
            )
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            HStack {
                Button {
                    appState.path.removeAll()
                } label: {
                    Image(systemName: "house.fill")
                        .frame(width: 38, height: 38)
                        .background(groceryPanel, in: Circle())
                }

                VStack(alignment: .leading, spacing: 1) {
                    Text("Grocery List")
                        .font(.system(size: 29, design: .serif))
                    Text(mode == .type ? "Typed notebook" : "Handwritten notebook")
                        .font(.caption)
                        .foregroundStyle(Color(hex: 0xC2C8C5))
                }
                Spacer()
                Button(role: .destructive) {
                    showClearConfirmation = true
                } label: {
                    Image(systemName: "trash")
                        .frame(width: 38, height: 38)
                        .background(Color(hex: 0x6F3D39), in: Circle())
                }
            }

            Picker("Notebook mode", selection: modeBinding) {
                Label("Type", systemImage: "keyboard").tag(GroceryListMode.type)
                Label("Draw", systemImage: "pencil.tip").tag(GroceryListMode.draw)
            }
            .pickerStyle(.segmented)
        }
        .foregroundStyle(groceryCream)
    }

    private var modeBinding: Binding<GroceryListMode> {
        Binding(
            get: { store.state.mode },
            set: {
                store.setMode($0)
                redoStrokes.removeAll()
                hideKeyboard()
            }
        )
    }

    private func persistTypedText(_ displayed: String) {
        store.setTypedText(plainText(from: displayed))
    }

    private func addStroke(_ newStroke: GroceryStroke) {
        strokes.append(newStroke)
        redoStrokes.removeAll()
        store.setStrokes(strokes)
    }

    private func undo() {
        guard let removed = strokes.popLast() else { return }
        redoStrokes.append(removed)
        store.setStrokes(strokes)
    }

    private func redo() {
        guard let restored = redoStrokes.popLast() else { return }
        strokes.append(restored)
        store.setStrokes(strokes)
    }

    private func clearCurrentMode() {
        if mode == .type {
            displayedText = ""
            store.clearTypedList()
        } else {
            strokes.removeAll()
            redoStrokes.removeAll()
            store.clearDrawing()
        }
    }

    private func bulletize(_ plain: String) -> String {
        guard !plain.isEmpty else { return "" }
        return plain.components(separatedBy: "\n").map { "• \($0)" }.joined(separator: "\n")
    }

    private func plainText(from displayed: String) -> String {
        displayed.components(separatedBy: "\n").map { line in
            line.hasPrefix("• ") ? String(line.dropFirst(2)) :
                line.hasPrefix("•") ? String(line.dropFirst()) : line
        }.joined(separator: "\n")
    }

    private func hideKeyboard() {
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
    }
}

private struct DrawingToolbar: View {
    @Binding var ink: InkChoice
    @Binding var stroke: StrokeChoice
    let canUndo: Bool
    let canRedo: Bool
    let undo: () -> Void
    let redo: () -> Void

    var body: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 12) {
                Label("TOOLS", systemImage: "pencil")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(groceryInk.opacity(0.65))

                ForEach(InkChoice.choices) { choice in
                    Button { ink = choice } label: {
                        Circle()
                            .fill(choice.color)
                            .frame(width: 25, height: 25)
                            .overlay(
                                Circle().stroke(
                                    ink == choice ? groceryInk : .clear,
                                    lineWidth: 3
                                )
                            )
                    }
                    .accessibilityLabel(choice.label)
                }

                Divider().frame(height: 28)

                ForEach(StrokeChoice.choices) { choice in
                    Button(choice.label) { stroke = choice }
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(stroke == choice ? .white : groceryInk)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(stroke == choice ? grocerySage : .white.opacity(0.55), in: Capsule())
                }

                Divider().frame(height: 28)

                Button(action: undo) { Image(systemName: "arrow.uturn.backward") }
                    .disabled(!canUndo)
                    .accessibilityLabel("Undo")
                Button(action: redo) { Image(systemName: "arrow.uturn.forward") }
                    .disabled(!canRedo)
                    .accessibilityLabel("Redo")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
        .scrollIndicators(.hidden)
        .foregroundStyle(groceryInk)
        .background(Color(hex: 0xE8DECA))
    }
}

private struct LinedNotebook: View {
    let mode: GroceryListMode
    @Binding var displayedText: String
    @Binding var strokes: [GroceryStroke]
    let ink: InkChoice
    let strokeChoice: StrokeChoice
    let textChanged: (String) -> Void
    let strokeFinished: (GroceryStroke) -> Void
    @State private var currentStroke: GroceryStroke?

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                notebookLines

                if mode == .type {
                    ZStack(alignment: .topLeading) {
                        if displayedText.isEmpty {
                            Text("Type your grocery list…")
                                .font(.system(size: 21))
                                .foregroundStyle(groceryInk.opacity(0.42))
                                .padding(.top, 17)
                                .padding(.leading, 64)
                        }
                        TextEditor(text: $displayedText)
                            .font(.system(size: 21))
                            .lineSpacing(12)
                            .foregroundStyle(groceryInk)
                            .scrollContentBackground(.hidden)
                            .padding(.leading, 55)
                            .padding(.trailing, 12)
                            .padding(.vertical, 8)
                            .onChange(of: displayedText) { oldValue, newValue in
                                if oldValue.isEmpty, !newValue.isEmpty, !newValue.hasPrefix("•") {
                                    displayedText = "• \(newValue)"
                                } else if newValue.count == oldValue.count + 1,
                                   newValue.hasSuffix("\n") {
                                    displayedText += "• "
                                }
                                textChanged(displayedText)
                            }
                    }
                } else {
                    Canvas { context, size in
                        for stroke in strokes { draw(stroke, in: &context, size: size) }
                        if let currentStroke { draw(currentStroke, in: &context, size: size) }
                    }
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                let point = normalized(value.location, in: geometry.size)
                                if currentStroke == nil {
                                    currentStroke = GroceryStroke(
                                        points: [point],
                                        colorARGB: ink.argb,
                                        width: strokeChoice.width
                                    )
                                } else {
                                    currentStroke?.points.append(point)
                                }
                            }
                            .onEnded { _ in
                                if let currentStroke { strokeFinished(currentStroke) }
                                currentStroke = nil
                            }
                    )
                }
            }
            .background(groceryCream)
        }
    }

    private var notebookLines: some View {
        Canvas { context, size in
            var y = 48.0
            while y < size.height {
                var line = Path()
                line.move(to: CGPoint(x: 0, y: y))
                line.addLine(to: CGPoint(x: size.width, y: y))
                context.stroke(line, with: .color(paperLine), lineWidth: 1)
                y += 38
            }
            var margin = Path()
            margin.move(to: CGPoint(x: 52, y: 0))
            margin.addLine(to: CGPoint(x: 52, y: size.height))
            context.stroke(margin, with: .color(marginRed), lineWidth: 1.5)
        }
        .allowsHitTesting(false)
    }

    private func normalized(_ point: CGPoint, in size: CGSize) -> GroceryPoint {
        GroceryPoint(
            x: min(max(point.x / max(size.width, 1), 0), 1),
            y: min(max(point.y / max(size.height, 1), 0), 1)
        )
    }

    private func draw(_ stroke: GroceryStroke, in context: inout GraphicsContext, size: CGSize) {
        let points = stroke.points.map {
            CGPoint(x: $0.x * size.width, y: $0.y * size.height)
        }
        guard let first = points.first else { return }
        let color = stroke.isEraser ? groceryCream : Color(argb: stroke.colorARGB)
        if points.count == 1 {
            let radius = stroke.width / 2
            context.fill(
                Path(ellipseIn: CGRect(
                    x: first.x - radius,
                    y: first.y - radius,
                    width: stroke.width,
                    height: stroke.width
                )),
                with: .color(color)
            )
            return
        }
        var path = Path()
        path.move(to: first)
        for point in points.dropFirst() { path.addLine(to: point) }
        context.stroke(
            path,
            with: .color(color),
            style: StrokeStyle(lineWidth: stroke.width, lineCap: .round, lineJoin: .round)
        )
    }
}

private struct InkChoice: Identifiable, Equatable {
    let label: String
    let color: Color
    let argb: UInt32
    var id: String { label }

    static let choices = [
        InkChoice(label: "Black", color: Color(hex: 0x24211E), argb: 0xFF24211E),
        InkChoice(label: "Navy", color: Color(hex: 0x173B57), argb: 0xFF173B57),
        InkChoice(label: "Butter", color: Color(hex: 0xD6A72F), argb: 0xFFD6A72F),
        InkChoice(label: "Red", color: Color(hex: 0xA33F38), argb: 0xFFA33F38),
        InkChoice(label: "Green", color: Color(hex: 0x4E6A45), argb: 0xFF4E6A45)
    ]
}

private struct StrokeChoice: Identifiable, Equatable {
    let label: String
    let width: Double
    var id: String { label }

    static let choices = [
        StrokeChoice(label: "Thin", width: 3.5),
        StrokeChoice(label: "Medium", width: 7),
        StrokeChoice(label: "Thick", width: 13)
    ]
}

private extension Color {
    init(argb: UInt32) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255,
            opacity: Double((argb >> 24) & 0xFF) / 255
        )
    }
}
