package com.falloutlondon.companion

fun PipboyValue.displayText(): String = when (this) {
    is PipboyValue.Bool -> value.toString()
    is PipboyValue.Int8 -> value.toString()
    is PipboyValue.UInt8 -> value.toString()
    is PipboyValue.Int32 -> value.toString()
    is PipboyValue.UInt32 -> value.toString()
    is PipboyValue.Float32 -> value.toString()
    is PipboyValue.StringValue -> value.replace("\n", "\\n")
    PipboyValue.Null -> "null"
}

fun PipboyDatabase.exportText(): String {
    val out = StringBuilder()
    out.appendLine("FALLOUT: LONDON ATTA-BOY DATA EXPORT")
    out.appendLine("Generated from the current live database")
    out.appendLine()

    fun valueText(value: PipboyValue?): String = when (value) {
        null -> ""
        is PipboyValue.Bool -> value.value.toString()
        is PipboyValue.Int8 -> value.value.toString()
        is PipboyValue.UInt8 -> value.value.toString()
        is PipboyValue.Int32 -> value.value.toString()
        is PipboyValue.UInt32 -> value.value.toString()
        is PipboyValue.Float32 -> value.value.toString()
        is PipboyValue.StringValue -> value.value.replace("\n", "\\n")
        PipboyValue.Null -> "null"
    }

    fun walk(id: Long, path: String, depth: Int) {
        val node = nodes[id] ?: return
        if (node.value != null) {
            out.append("  ".repeat(depth)).append(path).append(" = ").append(valueText(node.value)).appendLine()
        }
        node.objectChildren.toSortedMap().forEach { (key, child) ->
            walk(child, if (path.isEmpty()) key else path + "." + key, depth + 1)
        }
        node.arrayChildren.forEachIndexed { index, child ->
            walk(child, if (path.isEmpty()) "[$index]" else path + "[" + index + "]", depth + 1)
        }
    }

    walk(0L, "", 0)
    localMap?.let {
        out.appendLine()
        out.appendLine("LOCAL MAP SNAPSHOT")
        out.appendLine("  size = " + it.width + " x " + it.height)
        out.appendLine("  northWest = " + it.northWest)
        out.appendLine("  northEast = " + it.northEast)
        out.appendLine("  southWest = " + it.southWest)
        out.appendLine("  pixels = " + it.pixels.size + " bytes")
    }
    return out.toString()
}
