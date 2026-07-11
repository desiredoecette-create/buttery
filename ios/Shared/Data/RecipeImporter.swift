import Foundation
import UIKit
import Vision

enum RecipeImporter {
    static func recognizeText(in data: Data) async throws -> String {
        guard let image = UIImage(data: data)?.cgImage else { throw ImportError.invalidImage }
        return try await withCheckedThrowingContinuation { continuation in
            let request = VNRecognizeTextRequest { request, error in
                if let error { continuation.resume(throwing: error); return }
                let text = (request.results as? [VNRecognizedTextObservation] ?? [])
                    .compactMap { $0.topCandidates(1).first?.string }
                    .joined(separator: "\n")
                continuation.resume(returning: text)
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            DispatchQueue.global(qos: .userInitiated).async {
                do { try VNImageRequestHandler(cgImage: image).perform([request]) }
                catch { continuation.resume(throwing: error) }
            }
        }
    }

    static func importURL(_ enteredURL: String) async throws -> RecipeDraft {
        let value = enteredURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = value.lowercased().hasPrefix("http") ? value : "https://\(value)"
        guard let url = URL(string: normalized), ["http", "https"].contains(url.scheme?.lowercased())
        else { throw ImportError.invalidURL }

        var request = URLRequest(url: url)
        request.timeoutInterval = 15
        request.setValue("Mozilla/5.0 (iPhone) Buttery/1.0", forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode),
              http.mimeType?.contains("html") != false,
              data.count <= 2_000_000,
              let html = String(data: data, encoding: .utf8) else { throw ImportError.noRecipe }

        if var draft = structuredRecipe(in: html) {
            guard !draft.title.isEmpty, !draft.ingredients.isEmpty || !draft.instructions.isEmpty
            else { throw ImportError.noRecipe }
            draft.sourceUrl = url
            draft.originalRawText = String(html.prefix(100_000))
            return draft
        }

        let isolated = isolateRecipeText(html)
        var draft = parseText(isolated)
        guard !draft.title.isEmpty, !draft.ingredients.isEmpty || !draft.instructions.isEmpty
        else { throw ImportError.noRecipe }
        draft.sourceUrl = url
        draft.originalRawText = isolated
        return draft
    }

    static func importText(_ raw: String) async throws -> RecipeDraft {
        let normalized = normalizePastedRecipeText(raw)
        guard normalized.count >= 20 else { throw ImportError.noRecipe }
        let draft = parseText(normalized)
        guard isUsableImportedDraft(draft)
        else { throw ImportError.noRecipe }
        return draft
    }

    static func cleanImportedRecipeField(_ value: String) -> String {
        value
            .replacingOccurrences(
                of: #"(?im)^\s*(absolutely|sure|of course|certainly|yes)[!.]?\s*(here(?:'|’)s|here is|below is|i(?:'|’)ve got|let(?:'|’)s make|this is)\b.*$"#,
                with: "",
                options: .regularExpression
            )
            .replacingOccurrences(
                of: #"(?im)^\s*(here(?:'|’)s|here is|below is)\s+(?:the|a|an)\s+.*\b(recipe|version)\b.*$"#,
                with: "",
                options: .regularExpression
            )
            .replacingOccurrences(
                of: #"(?im)^\s*(i think you(?:'|’)re.*|hope you enjoy.*|let me know if.*|want me to.*|i can also.*)\s*$"#,
                with: "",
                options: .regularExpression
            )
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !isAssistantPreamble($0) && !isAssistantClosing($0) }
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func parseText(_ raw: String) -> RecipeDraft {
        let lines = raw.replacingOccurrences(of: "\r", with: "")
            .components(separatedBy: "\n")
            .flatMap(splitDenseRecipeLine)
            .map(cleanMarkdownLine)
            .filter { !$0.isEmpty && !isClutter($0) }
        var draft = RecipeDraft()
        guard !lines.isEmpty else { return draft }
        draft.title = lines.first(where: isProbableTitle) ?? lines.first ?? ""
        applyEmbeddedMetadata(in: raw, to: &draft)
        var section = Section.unknown
        var ingredients: [String] = [], instructions: [String] = [], notes: [String] = []

        for line in lines where line != draft.title {
            if isIngredientHeading(line) { section = .ingredients; continue }
            if isInstructionHeading(line) {
                section = .instructions; continue
            }
            if isNotesHeading(line) || isDescriptionHeading(line) {
                section = .notes; continue
            }
            if let metadata = metadata(line) {
                switch metadata.key {
                case "prep": draft.prepTime = metadata.value
                case "cook": draft.cookTime = metadata.value
                case "total": draft.totalTime = metadata.value
                case "servings": draft.servings = metadata.value
                default: break
                }
                continue
            }
            let clean = cleanListMarker(line)
            switch section {
            case .ingredients: if line.count <= 500 { ingredients.append(clean) }
            case .instructions: if line.count <= 500 { instructions.append(clean) }
            case .notes: if line.count <= 500 { notes.append(line) }
            case .unknown:
                if ingredientPattern.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)) != nil {
                    ingredients.append(clean)
                } else if instructionPattern.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)) != nil {
                    instructions.append(clean)
                } else if notes.joined(separator: "\n").count < 1_500 {
                    notes.append(line)
                }
            }
        }
        draft.ingredients = ingredients.joined(separator: "\n")
        draft.instructions = instructions.joined(separator: "\n")
        draft.notes = String(notes.joined(separator: "\n").prefix(1_500))
        draft.originalRawText = raw
        recoverLikelySections(from: lines, into: &draft)
        draft.instructions = numberedInstructionText(draft.instructions)
        return draft
    }

    private static func normalizePastedRecipeText(_ raw: String) -> String {
        let decoded = cleanImportedRecipeField(decodeForPastedText(raw))
            .replacingOccurrences(of: "\u{00a0}", with: " ")
            .replacingOccurrences(of: #"\r\n?"#, with: "\n", options: .regularExpression)
            .replacingOccurrences(of: #"[ \t]{2,}"#, with: " ", options: .regularExpression)
        let lines = decoded.components(separatedBy: .newlines)
            .flatMap(splitDenseRecipeLine)
            .map(cleanMarkdownLine)
            .filter { !$0.isEmpty && $0.count <= 700 && !isWebClutter($0) && !isPasteClutter($0) }
        var unique: [String] = []
        for line in lines where !unique.contains(line) {
            unique.append(line)
        }
        return unique.joined(separator: "\n")
    }

    private static func decodeForPastedText(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.range(of: #"<[A-Za-z][^>]*>"#, options: .regularExpression) != nil {
            return cleanHTML(trimmed)
        }
        return trimmed
    }

    private static func splitDenseRecipeLine(_ line: String) -> [String] {
        guard line.count > 90 else { return [line] }
        let sectioned = line
            .replacingOccurrences(of: #"(?i)(?:^|\s)(#{0,6}\s*\*{0,2}(?:Recipe\s+)?(?:Title|Description|Ingredients?|Instructions?|Directions?|Method|Steps?|Notes?|Tips?)\*{0,2}\s*:?)"#, with: "\n$1\n", options: .regularExpression)
            .replacingOccurrences(of: #"\s+(\d+[.)]\s+)(?=[A-Z])"#, with: "\n$1", options: .regularExpression)
            .replacingOccurrences(of: #"\s+([•*-]\s+)(?=[A-Za-z0-9])"#, with: "\n$1", options: .regularExpression)
        return sectioned.components(separatedBy: .newlines)
    }

    private static func recoverLikelySections(from lines: [String], into draft: inout RecipeDraft) {
        guard draft.ingredients.isEmpty || draft.instructions.isEmpty else { return }
        guard let ingredientStart = lines.firstIndex(where: isIngredientHeading) else { return }
        let instructionStart = lines.firstIndex(where: isInstructionHeading)
        let notesStart = lines.firstIndex(where: { isNotesHeading($0) || isDescriptionHeading($0) })
        if draft.ingredients.isEmpty {
            let ingredientEnd = [instructionStart, notesStart].compactMap { $0 }.filter { $0 > ingredientStart }.min() ?? lines.count
            draft.ingredients = Array(lines[(ingredientStart + 1)..<ingredientEnd])
                .map(cleanListMarker)
                .filter { !$0.isEmpty && metadata($0) == nil }
                .joined(separator: "\n")
        }
        if draft.instructions.isEmpty, let instructionStart {
            let instructionEnd = [notesStart].compactMap { $0 }.filter { $0 > instructionStart }.min() ?? lines.count
            draft.instructions = Array(lines[(instructionStart + 1)..<instructionEnd])
                .map(cleanListMarker)
                .filter { !$0.isEmpty && metadata($0) == nil }
                .joined(separator: "\n")
        }
    }

    private static func numberedInstructionText(_ value: String) -> String {
        let steps = value
            .replacingOccurrences(of: "\r", with: "")
            .components(separatedBy: .newlines)
            .flatMap(splitDenseInstructionLine)
            .map(cleanMarkdownLine)
            .map(cleanListMarker)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !isInstructionHeading($0) }

        return steps.enumerated()
            .map { "\($0.offset + 1). \($0.element)" }
            .joined(separator: "\n\n")
    }

    private static func splitDenseInstructionLine(_ line: String) -> [String] {
        guard line.count > 120 else { return [line] }
        return line
            .replacingOccurrences(
                of: #"\s+((?:step\s*)?\d+[.):]\s+)(?=[A-Z])"#,
                with: "\n$1",
                options: [.regularExpression, .caseInsensitive]
            )
            .components(separatedBy: .newlines)
    }

    private static func structuredRecipe(in html: String) -> RecipeDraft? {
        let pattern = #"<script\b(?=[^>]*\btype\s*=\s*["']application/ld\+json["'])[^>]*>([\s\S]*?)</script>"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return nil }
        for match in regex.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
            guard let range = Range(match.range(at: 1), in: html) else { continue }
            let jsonText = String(html[range]).trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "<!--", with: "")
                .replacingOccurrences(of: "-->", with: "")
            guard let data = jsonText.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data),
                  let recipe = findRecipe(json) else { continue }
            var draft = RecipeDraft()
            draft.title = cleanText(string(recipe["name"]))
            draft.notes = String(cleanText(string(recipe["description"])).prefix(600))
            draft.prepTime = formatDuration(string(recipe["prepTime"]))
            draft.cookTime = formatDuration(string(recipe["cookTime"]))
            draft.totalTime = formatDuration(string(recipe["totalTime"]))
            draft.servings = yieldString(recipe["recipeYield"])
            draft.ingredients = stringArray(recipe["recipeIngredient"]).map(cleanText).filter { !$0.isEmpty }.joined(separator: "\n")
            draft.instructions = numberedInstructionText(
                instructionStrings(recipe["recipeInstructions"])
                    .map(cleanText)
                    .filter { !$0.isEmpty }
                    .joined(separator: "\n")
            )
            if !draft.title.isEmpty { return draft }
        }
        return nil
    }

    private static func isolateRecipeText(_ html: String) -> String {
        let title = metaContent("og:title", in: html) ?? capture(#"<title\b[^>]*>([\s\S]*?)</title>"#, in: html) ?? ""
        let description = String((metaContent("og:description", in: html) ?? "").prefix(600))
        let stripped = html
            .replacingOccurrences(of: #"<(script|style|nav|footer|aside|form|iframe|noscript)\b[^>]*>[\s\S]*?</\1>"#, with: " ", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: #"<(br|p|div|li|h[1-6]|section|article)\b[^>]*>"#, with: "\n", options: [.regularExpression, .caseInsensitive])
        let visible = cleanHTML(stripped)
        let lines = visible.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0.count <= 500 && !isWebClutter($0) }
            .reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
        guard let ingredientStart = lines.firstIndex(where: isIngredientHeading),
              let instructionStart = lines.firstIndex(where: isInstructionHeading) else { return "" }
        let notesStart = lines.firstIndex(where: isNotesHeading)
        let ingredientEnd = [instructionStart, notesStart].compactMap { $0 }.filter { $0 > ingredientStart }.min() ?? lines.count
        let instructionEnd = [notesStart].compactMap { $0 }.filter { $0 > instructionStart }.min() ?? lines.count
        var result = [cleanText(title)]
        if !description.isEmpty { result += ["Notes", cleanText(description)] }
        result += Array(lines[ingredientStart..<min(ingredientEnd, ingredientStart + 81)])
        result += Array(lines[instructionStart..<min(instructionEnd, instructionStart + 81)])
        if let notesStart { result += Array(lines[notesStart..<min(lines.count, notesStart + 21)]) }
        return result.filter { !$0.isEmpty }.joined(separator: "\n")
    }

    private static func findRecipe(_ value: Any) -> [String: Any]? {
        if let object = value as? [String: Any] {
            let types = stringArray(object["@type"])
            if types.contains(where: { $0.lowercased() == "recipe" || $0.lowercased().hasSuffix("/recipe") }) { return object }
            for child in object.values { if let found = findRecipe(child) { return found } }
        } else if let array = value as? [Any] {
            for child in array { if let found = findRecipe(child) { return found } }
        }
        return nil
    }

    private static func instructionStrings(_ value: Any?) -> [String] {
        if let text = value as? String { return text.components(separatedBy: .newlines) }
        if let array = value as? [Any] { return array.flatMap(instructionStrings) }
        if let object = value as? [String: Any] {
            if let items = object["itemListElement"] { return instructionStrings(items) }
            if let text = object["text"] { return instructionStrings(text) }
            if let name = object["name"] { return instructionStrings(name) }
        }
        return []
    }

    private static func string(_ value: Any?) -> String { value as? String ?? "" }
    private static func stringArray(_ value: Any?) -> [String] {
        if let value = value as? String { return [value] }
        return (value as? [Any] ?? []).compactMap { $0 as? String }
    }
    private static func yieldString(_ value: Any?) -> String {
        if let number = value as? NSNumber { return number.stringValue }
        return stringArray(value).map(cleanText).joined(separator: ", ")
    }
    private static func cleanHTML(_ html: String) -> String {
        guard let data = html.data(using: .utf8),
              let attributed = try? NSAttributedString(
                data: data,
                options: [.documentType: NSAttributedString.DocumentType.html, .characterEncoding: String.Encoding.utf8.rawValue],
                documentAttributes: nil
              ) else { return html.replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression) }
        return attributed.string
    }
    private static func cleanText(_ value: String) -> String {
        cleanHTML(value).replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
    private static func cleanMarkdownLine(_ value: String) -> String {
        value
            .replacingOccurrences(of: #"^\s*#{1,6}\s*"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"^\s*>+\s*"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"^\s*[*_]{1,3}(.+?)[*_]{1,3}\s*$"#, with: "$1", options: .regularExpression)
            .replacingOccurrences(of: #"[*_]{2,}"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"^\s*(Recipe\s+Title|Title)\s*:?\s*"#, with: "", options: [.regularExpression, .caseInsensitive])
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
    private static func capture(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range(at: 1), in: text) else { return nil }
        return String(text[range])
    }
    private static func metaContent(_ property: String, in html: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: #"<meta\b[^>]*>"#, options: .caseInsensitive) else { return nil }
        for match in regex.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
            guard let range = Range(match.range, in: html) else { continue }
            let tag = String(html[range])
            let key = capture(#"(?:property|name)\s*=\s*["']([^"']+)["']"#, in: tag)
            if key?.caseInsensitiveCompare(property) == .orderedSame {
                return capture(#"content\s*=\s*["']([^"']*)["']"#, in: tag)
            }
        }
        return nil
    }
    private static func metadata(_ line: String) -> (key: String, value: String)? {
        let pattern = #"^\s*(prep(?:\s*time)?|cook(?:\s*time)?|total(?:\s*time)?|servings?|serves|yield)\s*:?\s*(.+)$"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
              let match = regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
              let keyRange = Range(match.range(at: 1), in: line), let valueRange = Range(match.range(at: 2), in: line) else { return nil }
        let rawKey = line[keyRange].lowercased()
        let key = rawKey.hasPrefix("prep") ? "prep" : rawKey.hasPrefix("cook") ? "cook" : rawKey.hasPrefix("total") ? "total" : "servings"
        return (key, String(line[valueRange]).trimmingCharacters(in: .whitespaces))
    }
    private static func applyEmbeddedMetadata(in text: String, to draft: inout RecipeDraft) {
        if draft.prepTime.isEmpty, let value = capture(#"\bprep(?:\s*time)?\s*:?\s*([^\n|•,]{1,40})"#, in: text) {
            draft.prepTime = cleanText(value)
        }
        if draft.cookTime.isEmpty, let value = capture(#"\bcook(?:\s*time)?\s*:?\s*([^\n|•,]{1,40})"#, in: text) {
            draft.cookTime = cleanText(value)
        }
        if draft.totalTime.isEmpty, let value = capture(#"\btotal(?:\s*time)?\s*:?\s*([^\n|•,]{1,40})"#, in: text) {
            draft.totalTime = cleanText(value)
        }
        if draft.servings.isEmpty, let value = capture(#"\b(?:servings?|serves|yield)\s*:?\s*([^\n|•,]{1,40})"#, in: text) {
            draft.servings = cleanText(value)
        }
    }
    private static func formatDuration(_ value: String) -> String {
        guard value.uppercased().hasPrefix("P"),
              let regex = try? NSRegularExpression(pattern: #"P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?"#, options: .caseInsensitive),
              let match = regex.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)) else { return cleanText(value) }
        func number(_ index: Int) -> Int? {
            guard match.range(at: index).location != NSNotFound, let range = Range(match.range(at: index), in: value) else { return nil }
            return Int(value[range])
        }
        return [(number(1).map { "\($0) days" }), (number(2).map { "\($0) hr" }), (number(3).map { "\($0) min" })]
            .compactMap { $0 }.joined(separator: " ")
    }
    private static func cleanListMarker(_ line: String) -> String {
        line.replacingOccurrences(of: #"^\s*((step\s*)?\d+[.):]|[-*•])\s*"#, with: "", options: [.regularExpression, .caseInsensitive])
    }
    private static func isUsableImportedDraft(_ draft: RecipeDraft) -> Bool {
        !draft.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        (
            !draft.ingredients.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !draft.instructions.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !draft.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        )
    }
    private static func isProbableTitle(_ line: String) -> Bool {
        line.count >= 2 &&
        line.count <= 90 &&
        !isIngredientHeading(line) &&
        !isInstructionHeading(line) &&
        !isNotesHeading(line) &&
        !isDescriptionHeading(line) &&
        metadata(line) == nil
    }
    private static func isIngredientHeading(_ line: String) -> Bool {
        line.range(of: #"^\s*(?:[*_#>\s-]*)ingredients?\s*:?\s*(?:[*_]*)\s*$"#, options: [.regularExpression, .caseInsensitive]) != nil
    }
    private static func isInstructionHeading(_ line: String) -> Bool {
        line.range(of: #"^\s*(?:[*_#>\s-]*)(instructions?|directions?|method|steps?)\s*:?\s*(?:[*_]*)\s*$"#, options: [.regularExpression, .caseInsensitive]) != nil
    }
    private static func isNotesHeading(_ line: String) -> Bool {
        line.range(of: #"^\s*(?:[*_#>\s-]*)(recipe\s+)?(notes?|tips?)\s*:?\s*(?:[*_]*)\s*$"#, options: [.regularExpression, .caseInsensitive]) != nil
    }
    private static func isDescriptionHeading(_ line: String) -> Bool {
        line.range(of: #"^\s*(?:[*_#>\s-]*)(description|summary|short\s+description)\s*:?\s*(?:[*_]*)\s*$"#, options: [.regularExpression, .caseInsensitive]) != nil
    }
    private static func isClutter(_ line: String) -> Bool {
        line.range(of: #"^\s*(like|share|follow|comment|save this recipe|full recipe below|join my group)(\s+.*)?$"#, options: [.regularExpression, .caseInsensitive]) != nil
    }
    private static func isPasteClutter(_ line: String) -> Bool {
        line.range(of: #"\b(copied from|download our app|open in app|pin this|rate this recipe|table of contents|skip to content|all rights reserved|©|http[s]?://|www\.)\b"#, options: [.regularExpression, .caseInsensitive]) != nil
    }
    private static func isAssistantPreamble(_ line: String) -> Bool {
        line.range(
            of: #"^\s*(absolutely|sure|of course|certainly|yes)[!.]?\s*(here(?:'|’)s|here is|below is|i(?:'|’)ve got|let(?:'|’)s make|this is)\b"#,
            options: [.regularExpression, .caseInsensitive]
        ) != nil ||
        line.range(
            of: #"^\s*(here(?:'|’)s|here is|below is)\s+(?:the|a|an)\s+.*\b(recipe|version)\b"#,
            options: [.regularExpression, .caseInsensitive]
        ) != nil
    }
    private static func isAssistantClosing(_ line: String) -> Bool {
        line.range(
            of: #"^\s*(hope you enjoy|let me know if|want me to|i can also|would you like me to|enjoy!)\b"#,
            options: [.regularExpression, .caseInsensitive]
        ) != nil
    }
    private static func isWebClutter(_ line: String) -> Bool {
        line.range(of: #"\b(jump to recipe|print recipe|newsletter|subscribe|sign up|advertisement|related posts?|comments?|leave a reply|share on|author bio|frequently asked questions?|privacy policy|cookie policy)\b"#, options: [.regularExpression, .caseInsensitive]) != nil
    }
    private static let ingredientPattern = try! NSRegularExpression(pattern: #"(\d|[½¼¾⅓⅔⅛]|\b(one|two|three)\b).*\b(cups?|tbsp|tablespoons?|tsp|teaspoons?|oz|ounces?|lbs?|pounds?|grams?|g|kg|ml|liters?|cloves?|cans?|packages?|pinch)\b"#, options: .caseInsensitive)
    private static let instructionPattern = try! NSRegularExpression(pattern: #"^\s*((step\s*)?\d+[.):]|[-*•])?\s*(add|mix|stir|bake|cook|heat|simmer|whisk|combine|pour|chop|slice|season|preheat|serve|place|bring|remove|fold|blend|roast)\b"#, options: .caseInsensitive)
    private enum Section { case unknown, notes, ingredients, instructions }
    enum ImportError: LocalizedError {
        case invalidImage, invalidURL, noRecipe
        var errorDescription: String? {
            switch self {
            case .invalidImage: "That image could not be read."
            case .invalidURL: "Enter a valid recipe URL."
            case .noRecipe: "This recipe could not be imported cleanly. Try a clearer URL, photo, or pasted recipe text."
            }
        }
    }
}
