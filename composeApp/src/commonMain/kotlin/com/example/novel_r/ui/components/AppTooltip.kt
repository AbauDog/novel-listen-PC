package com.example.novel_r.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.ExperimentalComposeUiApi

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppTooltip(
    text: String,
    content: @Composable () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
    ) {
        content()
        
        if (isHovered) {
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        // Place popup below the anchor, centered horizontally
                        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                        val y = anchorBounds.bottom + 4
                        // Constrain to window bounds
                        val constrainedX = x.coerceIn(0, windowSize.width - popupContentSize.width)
                        val constrainedY = if (y + popupContentSize.height > windowSize.height) {
                            anchorBounds.top - popupContentSize.height - 4
                        } else {
                            y
                        }
                        return IntOffset(constrainedX, constrainedY)
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2B2B2B), RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
