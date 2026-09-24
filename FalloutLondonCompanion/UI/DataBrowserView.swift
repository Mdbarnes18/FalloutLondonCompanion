import Foundation
import SwiftUI
import UniformTypeIdentifiers
import UIKit

struct DataBrowserView: View {
    @EnvironmentObject private var app: AppState
    @State private var path = ""
    @State private var search = ""
    @State private var copied = false
    @State private var showExporter = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("DATA BROWSER").font(.system(size: 18, design: .monospaced))
                Spacer()
                Button(copied ? "COPIED" : "COPY ALL") {
                    UIPasteboard.general.string = app.database.exportText()
                    copied = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { copied = false }
                }.font(.system(size: 9, design: .monospaced))
                Button("SAVE TXT") { showExporter = true }.font(.system(size: 9, design: .monospaced))
            }
            HStack {
                TextField("SEARCH PATH / VALUE", text: $search)
                    .textFieldStyle(.roundedBorder)
                    .font(.system(size: 10, design: .monospaced))
                if !path.isEmpty {
                    Button("ROOT") { path = "" }.font(.system(size: 9, design: .monospaced))
                }
            }
            if search.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                ScrollView {
                    DataBrowserTree(db: app.database, path: path, depth: 0) { path = $0 }
                }
            } else {
                SearchResultsView(db: app.database, query: search)
            }
        }
        .foregroundStyle(.green)
        .padding(12)
        .fileExporter(
            isPresented: $showExporter,
            document: TextExportDocument(text: app.database.exportText()),
            contentType: .plainText,
            defaultFilename: "Fallout-London-Data.txt"
        ) { _ in }
    }
}

private struct DataBrowserTree: View {
    let db: PipboyDatabase
    let path: String
    let depth: Int
    let open: (String) -> Void

    var body: some View {
        let children = db.objectChildren(at: path)
        let arrayChildren = db.nodeID(at: path).flatMap { db.arrayChildren(at: $0) } ?? []
        LazyVStack(alignment: .leading, spacing: 2) {
            if children.isEmpty && arrayChildren.isEmpty {
                if let value = db.value(at: path) {
                    Text("\(path.isEmpty ? "ROOT" : path) = \(value.displayText)")
                        .font(.system(size: 9, design: .monospaced)).textSelection(.enabled)
                } else {
                    Text("NO CHILD NODES").font(.system(size: 9, design: .monospaced)).opacity(0.5)
                }
            } else {
                ForEach(children.keys.sorted(), id: \.self) { key in
                    let childPath = path.isEmpty ? key : "\(path).\(key)"
                    BrowserRow(db: db, title: key, path: childPath, depth: depth, open: open)
                }
                ForEach(Array(arrayChildren.enumerated()), id: \.offset) { index, _ in
                    BrowserRow(db: db, title: "[\(index)]", path: "\(path)[\(index)]", depth: depth, open: open)
                }
            }
        }
    }
}

private struct BrowserRow: View {
    let db: PipboyDatabase
    let title: String
    let path: String
    let depth: Int
    let open: (String) -> Void

    var body: some View {
        Button { open(path) } label: {
            HStack(spacing: 5) {
                Text(String(repeating: "  ", count: depth))
                Text("›")
                Text(title.uppercased())
                Spacer()
                if let value = db.value(at: path) {
                    Text(value.displayText).lineLimit(1).opacity(0.7)
                } else {
                    let count = db.objectChildren(at: path).count
                    let arrayCount = db.nodeID(at: path).flatMap { db.arrayChildren(at: $0)?.count } ?? 0
                    Text("\(count + arrayCount)").opacity(0.5)
                }
            }.font(.system(size: 9, design: .monospaced))
        }.buttonStyle(.plain)
    }
}

private struct SearchResultsView: View {
    let db: PipboyDatabase
    let query: String

    var body: some View {
        let q = query.lowercased()
        let matches = db.flattenedValues().filter { path, value in
            path.lowercased().contains(q) || value.displayText.lowercased().contains(q)
        }.sorted { $0.key < $1.key }
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 3) {
                Text("\(matches.count) MATCHES").font(.system(size: 8, design: .monospaced)).opacity(0.6)
                ForEach(matches, id: \.key) { path, value in
                    Text("\(path) = \(value.displayText)")
                        .font(.system(size: 8, design: .monospaced)).textSelection(.enabled)
                }
            }
        }
    }
}

private struct TextExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.plainText] }
    var text: String
    init(text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws {
        text = String(data: configuration.file.regularFileContents ?? Data(), encoding: .utf8) ?? ""
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: text.data(using: .utf8) ?? Data())
    }
}

private extension PipboyValue {
    var displayText: String {
        switch self {
        case .bool(let v): return v ? "true" : "false"
        case .int8(let v): return String(v)
        case .uint8(let v): return String(v)
        case .int32(let v): return String(v)
        case .uint32(let v): return String(v)
        case .float32(let v): return String(format: "%.6g", v)
        case .string(let v): return v.replacingOccurrences(of: "\n", with: "\\n")
        case .null: return "null"
        }
    }
}
