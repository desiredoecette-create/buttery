package com.buttery.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buttery.app.data.GroceryListMode
import com.buttery.app.data.GroceryListState
import com.buttery.app.data.GroceryListStore
import com.buttery.app.data.GroceryPoint
import com.buttery.app.data.GroceryStroke

private val GroceryNavy = Color(0xFF111A23)
private val GroceryNavyPanel = Color(0xFF1B2732)
private val GroceryCream = Color(0xFFF5EEDC)
private val GroceryInk = Color(0xFF332D26)
private val GroceryButter = Color(0xFFFFC857)
private val GrocerySage = Color(0xFF71815B)
private val PaperLine = Color(0xFF9AAFC0).copy(alpha = 0.46f)
private val MarginRed = Color(0xFFB66B67).copy(alpha = 0.56f)

private data class InkChoice(
    val label: String,
    val color: Color,
    val argb: Long
)

private val InkChoices = listOf(
    InkChoice("Black", Color(0xFF24211E), 0xFF24211E),
    InkChoice("Navy", Color(0xFF173B57), 0xFF173B57),
    InkChoice("Butter", Color(0xFFD6A72F), 0xFFD6A72F),
    InkChoice("Red", Color(0xFFA33F38), 0xFFA33F38),
    InkChoice("Green", Color(0xFF4E6A45), 0xFF4E6A45)
)

private data class StrokeChoice(
    val label: String,
    val width: Float
)

private val StrokeChoices = listOf(
    StrokeChoice("Thin", 3.5f),
    StrokeChoice("Medium", 7f),
    StrokeChoice("Thick", 13f)
)

@Composable
fun GroceryListScreen(
    store: GroceryListStore,
    onHome: () -> Unit
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    val initialState = remember(store) { store.load() }
    var mode by remember { mutableStateOf(initialState.mode) }
    var typedText by remember { mutableStateOf(initialState.typedText) }
    val strokes = remember { mutableStateListOf<GroceryStroke>().apply { addAll(initialState.strokes) } }
    val redoStrokes = remember { mutableStateListOf<GroceryStroke>() }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var inkChoice by remember { mutableStateOf(InkChoices.first()) }
    var strokeChoice by remember { mutableStateOf(StrokeChoices[1]) }

    fun persist(
        savedMode: GroceryListMode = mode,
        savedTypedText: String = typedText,
        savedStrokes: List<GroceryStroke> = strokes.toList()
    ) {
        store.save(
            GroceryListState(
                mode = savedMode,
                typedText = savedTypedText,
                strokes = savedStrokes
            )
        )
    }

    fun switchMode(newMode: GroceryListMode) {
        mode = newMode
        persist(savedMode = newMode)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF293A43), GroceryNavy),
                    radius = 1_500f
                )
            )
            .padding(
                start = if (isPhone) 18.dp else 30.dp,
                end = if (isPhone) 18.dp else 30.dp,
                top = if (isPhone) 56.dp else 22.dp,
                bottom = if (isPhone) 18.dp else 22.dp
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)
        ) {
            GroceryHeader(
                mode = mode,
                onModeSelected = ::switchMode,
                onHome = onHome,
                onClear = { showClearConfirmation = true }
            )

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                color = GroceryCream,
                shadowElevation = 14.dp
            ) {
                if (isPhone && mode == GroceryListMode.DRAW) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        DrawingToolbar(
                            inkChoice = inkChoice,
                            onInkSelected = { inkChoice = it },
                            strokeChoice = strokeChoice,
                            onStrokeSelected = { strokeChoice = it },
                            canUndo = strokes.isNotEmpty(),
                            canRedo = redoStrokes.isNotEmpty(),
                            onUndo = {
                                if (strokes.isNotEmpty()) {
                                    redoStrokes += strokes.removeAt(strokes.lastIndex)
                                    persist()
                                }
                            },
                            onRedo = {
                                if (redoStrokes.isNotEmpty()) {
                                    strokes += redoStrokes.removeAt(redoStrokes.lastIndex)
                                    persist()
                                }
                            },
                            compact = true
                        )
                        LinedPaper(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            mode = mode,
                            typedText = typedText,
                            onTypedTextChanged = { value ->
                                typedText = value
                                persist(savedTypedText = value)
                            },
                            strokes = strokes,
                            color = inkChoice,
                            strokeSize = strokeChoice,
                            onStrokeFinished = { stroke ->
                                strokes += stroke
                                redoStrokes.clear()
                                persist()
                            }
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                    if (mode == GroceryListMode.DRAW) {
                        DrawingToolbar(
                            inkChoice = inkChoice,
                            onInkSelected = { inkChoice = it },
                            strokeChoice = strokeChoice,
                            onStrokeSelected = { strokeChoice = it },
                            canUndo = strokes.isNotEmpty(),
                            canRedo = redoStrokes.isNotEmpty(),
                            onUndo = {
                                if (strokes.isNotEmpty()) {
                                    redoStrokes += strokes.removeAt(strokes.lastIndex)
                                    persist()
                                }
                            },
                            onRedo = {
                                if (redoStrokes.isNotEmpty()) {
                                    strokes += redoStrokes.removeAt(redoStrokes.lastIndex)
                                    persist()
                                }
                            }
                        )
                    }
                    LinedPaper(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        mode = mode,
                        typedText = typedText,
                        onTypedTextChanged = { value ->
                            typedText = value
                            persist(savedTypedText = value)
                        },
                        strokes = strokes,
                        color = inkChoice,
                        strokeSize = strokeChoice,
                        onStrokeFinished = { stroke ->
                            strokes += stroke
                            redoStrokes.clear()
                            persist()
                        }
                    )
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        val clearingTypedList = mode == GroceryListMode.TYPE
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = {
                Text(if (clearingTypedList) "Clear typed list?" else "Clear drawing?")
            },
            text = {
                Text(
                    if (clearingTypedList) {
                        "This only clears the typed list. Your drawing will be kept."
                    } else {
                        "This only clears the drawing. Your typed list will be kept."
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (clearingTypedList) {
                            typedText = ""
                            store.clearTypedList()
                        } else {
                            strokes.clear()
                            redoStrokes.clear()
                            store.clearDrawing()
                        }
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B493D))
                ) {
                    Text("Clear")
                }
            }
        )
    }
}

@Composable
private fun GroceryHeader(
    mode: GroceryListMode,
    onModeSelected: (GroceryListMode) -> Unit,
    onHome: () -> Unit,
    onClear: () -> Unit
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 700
    if (isPhone) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onHome,
                    modifier = Modifier.heightIn(min = 46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GroceryNavyPanel)
                ) {
                    Icon(Icons.Rounded.Home, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Grocery List", color = GroceryCream, fontFamily = FontFamily.Serif, fontSize = 32.sp)
                    Text(
                        if (mode == GroceryListMode.TYPE) "Typed notebook" else "Handwritten notebook",
                        color = Color(0xFFC2C8C5),
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = onClear,
                    modifier = Modifier.heightIn(min = 46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6F3D39))
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(
                    text = "Type",
                    selected = mode == GroceryListMode.TYPE,
                    onClick = { onModeSelected(GroceryListMode.TYPE) },
                    modifier = Modifier.weight(1f)
                )
                ModeChip(
                    text = "Draw",
                    selected = mode == GroceryListMode.DRAW,
                    onClick = { onModeSelected(GroceryListMode.DRAW) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Button(
            onClick = onHome,
            modifier = Modifier.heightIn(min = 54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GroceryNavyPanel)
        ) {
            Icon(Icons.Rounded.Home, contentDescription = null)
            Text("  Home", fontSize = 17.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Grocery List",
                color = GroceryCream,
                fontFamily = FontFamily.Serif,
                fontSize = 38.sp
            )
            Text(
                if (mode == GroceryListMode.TYPE) "Typed notebook" else "Handwritten notebook",
                color = Color(0xFFC2C8C5),
                fontSize = 15.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(
                text = "Type",
                selected = mode == GroceryListMode.TYPE,
                onClick = { onModeSelected(GroceryListMode.TYPE) }
            )
            ModeChip(
                text = "Draw",
                selected = mode == GroceryListMode.DRAW,
                onClick = { onModeSelected(GroceryListMode.DRAW) }
            )
        }
        Button(
            onClick = onClear,
            modifier = Modifier.heightIn(min = 54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6F3D39))
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = null)
            Text("  Clear", fontSize = 17.sp)
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(text, fontSize = 17.sp, modifier = Modifier.padding(vertical = 7.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = GroceryNavyPanel,
            labelColor = GroceryCream,
            selectedContainerColor = GroceryButter,
            selectedLabelColor = GroceryNavy
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.White.copy(alpha = 0.2f),
            selectedBorderColor = GroceryButter
        )
    )
}

@Composable
private fun DrawingToolbar(
    inkChoice: InkChoice,
    onInkSelected: (InkChoice) -> Unit,
    strokeChoice: StrokeChoice,
    onStrokeSelected: (StrokeChoice) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    compact: Boolean = false
) {
    val toolbarModifier = if (compact) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.width(174.dp).fillMaxHeight()
    }
    Column(
        modifier = toolbarModifier
            .background(Color(0xFFE8DECA))
            .padding(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
    ) {
        Text("TOOLS", color = GroceryInk.copy(alpha = 0.64f), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton(
                selected = true,
                icon = { Icon(Icons.Rounded.Edit, "Pen") },
                onClick = {}
            )
        }
        Text("INK", color = GroceryInk.copy(alpha = 0.64f), fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InkChoices.forEach { choice ->
                IconButton(
                    onClick = { onInkSelected(choice) },
                    modifier = Modifier
                        .size(24.dp)
                        .background(choice.color, CircleShape)
                        .then(
                            if (inkChoice == choice) {
                                Modifier.border(3.dp, GroceryInk, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                ) {}
            }
        }
        Text("STROKE", color = GroceryInk.copy(alpha = 0.64f), fontSize = 12.sp)
        StrokeChoices.forEach { choice ->
            FilterChip(
                selected = strokeChoice == choice,
                onClick = { onStrokeSelected(choice) },
                label = { Text(choice.label) },
                modifier = Modifier.fillMaxWidth(),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GrocerySage,
                    selectedLabelColor = Color.White
                )
            )
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.54f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Rounded.Undo, "Undo")
                Text("  Undo")
            }
            TextButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.54f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Rounded.Redo, "Redo")
                Text("  Redo")
            }
        }
    }
}

@Composable
private fun ToolButton(
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(
                if (selected) GroceryButter else Color.White.copy(alpha = 0.58f),
                RoundedCornerShape(14.dp)
            )
    ) {
        icon()
    }
}

@Composable
private fun LinedPaper(
    modifier: Modifier,
    mode: GroceryListMode,
    typedText: String,
    onTypedTextChanged: (String) -> Unit,
    strokes: List<GroceryStroke>,
    color: InkChoice,
    strokeSize: StrokeChoice,
    onStrokeFinished: (GroceryStroke) -> Unit
) {
    var currentStroke by remember { mutableStateOf<GroceryStroke?>(null) }
    val paperModifier = modifier.background(GroceryCream)

    Box(modifier = paperModifier) {
        Canvas(Modifier.fillMaxSize()) {
            val lineSpacing = 38.dp.toPx()
            var y = 58.dp.toPx()
            while (y < size.height) {
                drawLine(
                    color = PaperLine,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += lineSpacing
            }
            drawLine(
                color = MarginRed,
                start = Offset(58.dp.toPx(), 0f),
                end = Offset(58.dp.toPx(), size.height),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        if (mode == GroceryListMode.TYPE) {
            BasicTextField(
                value = typedText,
                onValueChange = onTypedTextChanged,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 78.dp, end = 34.dp, top = 23.dp, bottom = 28.dp),
                textStyle = TextStyle(
                    color = GroceryInk,
                    fontSize = 23.sp,
                    lineHeight = 38.sp
                ),
                cursorBrush = Brush.verticalGradient(listOf(GroceryInk, GroceryInk)),
                visualTransformation = GroceryBulletTransformation,
                decorationBox = { innerTextField ->
                    if (typedText.isEmpty()) {
                        Text(
                            "Type your grocery list...",
                            color = GroceryInk.copy(alpha = 0.42f),
                            fontSize = 23.sp,
                            lineHeight = 38.sp
                        )
                    }
                    innerTextField()
                }
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(color, strokeSize) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = GroceryStroke(
                                    points = listOf(offset.toNormalized(size.width, size.height)),
                                    colorArgb = color.argb,
                                    width = strokeSize.width,
                                    isEraser = false
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val point = change.position.toNormalized(size.width, size.height)
                                currentStroke = currentStroke?.copy(
                                    points = currentStroke!!.points + point
                                )
                            },
                            onDragEnd = {
                                currentStroke?.let(onStrokeFinished)
                                currentStroke = null
                            },
                            onDragCancel = { currentStroke = null }
                        )
                    }
            ) {
                strokes.forEach { drawGroceryStroke(it) }
                currentStroke?.let { drawGroceryStroke(it) }
            }
        }
    }
}

private fun DrawScope.drawGroceryStroke(stroke: GroceryStroke) {
    val points = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
    if (points.isEmpty()) return
    val strokeColor = if (stroke.isEraser) GroceryCream else Color(stroke.colorArgb)
    if (points.size == 1) {
        drawCircle(
            color = strokeColor,
            radius = stroke.width / 2f,
            center = points.first()
        )
        return
    }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point -> lineTo(point.x, point.y) }
    }
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(width = stroke.width, cap = StrokeCap.Round)
    )
}

private fun Offset.toNormalized(width: Int, height: Int): GroceryPoint =
    GroceryPoint(
        x = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
        y = (y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
    )

private object GroceryBulletTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val original = text.text
        val transformed = buildString {
            append("• ")
            original.forEach { character ->
                append(character)
                if (character == '\n') append("• ")
            }
        }
        val transformedOffsets = IntArray(original.length + 1)
        var addedCharacters = 2
        for (offset in 0..original.length) {
            transformedOffsets[offset] = offset + addedCharacters
            if (offset < original.length && original[offset] == '\n') {
                addedCharacters += 2
            }
        }

        return TransformedText(
            text = AnnotatedString(transformed),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    transformedOffsets[offset.coerceIn(0, original.length)]

                override fun transformedToOriginal(offset: Int): Int {
                    val target = offset.coerceIn(0, transformed.length)
                    var low = 0
                    var high = original.length
                    while (low < high) {
                        val middle = (low + high + 1) / 2
                        if (transformedOffsets[middle] <= target) {
                            low = middle
                        } else {
                            high = middle - 1
                        }
                    }
                    return low
                }
            }
        )
    }
}
