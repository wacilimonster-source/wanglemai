package com.wanglemai.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun padCount(n: Int): String = String.format(Locale.US, "%02d", n.coerceAtLeast(0))

/**
 * Draws the Hobonichi 3-layer stacked paper effect behind a card/sheet.
 */
@Composable
fun Modifier.stackedPaperEffect(
    bottomStartRadius: Dp = 18.dp
): Modifier {
    val palette = LocalEditorialPalette.current
    return this
        .padding(bottom = 7.dp)
        .drawBehind {
            val rBottom = bottomStartRadius.toPx()
            val strokePx = 1.dp.toPx()
            val offset1 = 3.dp.toPx()
            val offset2 = 6.dp.toPx()
            val inset1 = 2.dp.toPx()
            val inset2 = 4.dp.toPx()

            // Third sheet layer (deepest)
            drawRoundRect(
                color = palette.bg,
                topLeft = Offset(inset2, offset2),
                size = Size(size.width - inset2 * 2, size.height),
                cornerRadius = CornerRadius(rBottom, rBottom)
            )
            drawRoundRect(
                color = palette.lineStrong,
                topLeft = Offset(inset2, offset2),
                size = Size(size.width - inset2 * 2, size.height),
                cornerRadius = CornerRadius(rBottom, rBottom),
                style = Stroke(width = strokePx)
            )

            // Second sheet layer (middle)
            drawRoundRect(
                color = palette.surfaceWarm,
                topLeft = Offset(inset1, offset1),
                size = Size(size.width - inset1 * 2, size.height),
                cornerRadius = CornerRadius(rBottom, rBottom)
            )
            drawRoundRect(
                color = palette.lineStrong,
                topLeft = Offset(inset1, offset1),
                size = Size(size.width - inset1 * 2, size.height),
                cornerRadius = CornerRadius(rBottom, rBottom),
                style = Stroke(width = strokePx)
            )
        }
}

@Composable
fun StackedPaperCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = LocalEditorialPalette.current.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalEditorialPalette.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .stackedPaperEffect()
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = Color(0x262A231D),
                spotColor = Color(0x262A231D)
            ),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, palette.lineStrong)
    ) {
        Column(content = content)
    }
}

@Composable
fun TactileButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    compact: Boolean = false
) {
    val palette = LocalEditorialPalette.current
    val shape = RoundedCornerShape(if (compact) 8.dp else 11.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 30.dp else 36.dp),
        shape = shape,
        color = palette.surfaceWarm,
        contentColor = palette.text,
        border = BorderStroke(1.dp, palette.lineStrong),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 11.dp,
                vertical = if (compact) 5.dp else 7.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EditorialBackButton(
    text: String = "← 返回手账",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalEditorialPalette.current
    Surface(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "返回"
        },
        shape = RoundedCornerShape(10.dp),
        color = palette.surface,
        contentColor = palette.primary,
        border = BorderStroke(1.dp, palette.lineStrong),
        shadowElevation = 1.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = palette.primary
        )
    }
}

@Composable
fun StampBadge(
    text: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false
) {
    val palette = LocalEditorialPalette.current
    val borderColor = if (muted) palette.lineStrong else palette.primary.copy(alpha = 0.42f)
    val bgColor = if (muted) palette.soft else palette.primarySoft
    val textColor = if (muted) palette.muted else palette.primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = textColor,
            lineHeight = 13.sp
        )
    }
}

@Composable
fun DiamondCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    contentDesc: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalEditorialPalette.current
    Box(
        modifier = modifier
            .width(44.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        val diamondShape = RoundedCornerShape(4.dp)
        Box(
            modifier = Modifier
                .size(17.dp)
                .rotate(45f)
                .clip(diamondShape)
                .background(if (checked) palette.primary else palette.surface)
                .border(1.5.dp, palette.primary, diamondShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    modifier = Modifier.rotate(-45f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.onPrimary,
                    lineHeight = 10.sp
                )
            }
        }
    }
}

@Composable
fun EditorialChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalEditorialPalette.current
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
        shape = shape,
        color = if (selected) palette.primarySoft else palette.soft,
        contentColor = if (selected) palette.primary else palette.muted,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) palette.primary else palette.lineStrong.copy(alpha = 0.6f)
        ),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) palette.primary else palette.muted
            )
        }
    }
}

@Composable
fun DashedHorizontalDivider(
    modifier: Modifier = Modifier,
    color: Color = LocalEditorialPalette.current.lineStrong,
    strokeWidth: Dp = 1.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(strokeWidth)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    }
}

@Composable
private fun Canvas(modifier: Modifier, onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    androidx.compose.foundation.Canvas(modifier = modifier, onDraw = onDraw)
}
