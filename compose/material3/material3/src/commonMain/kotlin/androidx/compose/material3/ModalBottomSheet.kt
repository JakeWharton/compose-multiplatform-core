/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material3

import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.SheetValue.PartiallyExpanded
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * <a href="https://m3.material.io/components/bottom-sheets/overview" class="external" target="_blank">Material Design modal bottom sheet</a>.
 *
 * Modal bottom sheets are used as an alternative to inline menus or simple dialogs on mobile,
 * especially when offering a long list of action items, or when items require longer descriptions
 * and icons. Like dialogs, modal bottom sheets appear in front of app content, disabling all other
 * app functionality when they appear, and remaining on screen until confirmed, dismissed, or a
 * required action has been taken.
 *
 * ![Bottom sheet image](https://developer.android.com/images/reference/androidx/compose/material3/bottom_sheet.png)
 *
 * A simple example of a modal bottom sheet looks like this:
 *
 * @sample androidx.compose.material3.samples.ModalBottomSheetSample
 *
 * @param onDismissRequest Executes when the user clicks outside of the bottom sheet, after sheet
 * animates to [Hidden].
 * @param modifier Optional [Modifier] for the bottom sheet.
 * @param sheetState The state of the bottom sheet.
 * @param shape The shape of the bottom sheet.
 * @param containerColor The color used for the background of this bottom sheet
 * @param contentColor The preferred color for content inside this bottom sheet. Defaults to either
 * the matching content color for [containerColor], or to the current [LocalContentColor] if
 * [containerColor] is not a color from the theme.
 * @param tonalElevation The tonal elevation of this bottom sheet.
 * @param scrimColor Color of the scrim that obscures content when the bottom sheet is open.
 * @param dragHandle Optional visual marker to swipe the bottom sheet.
 * @param windowInsets window insets to be passed to the bottom sheet window via [PaddingValues]
 * params.
 * @param content The content to be displayed inside the bottom sheet.
 */
@Composable
@ExperimentalMaterial3Api
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    windowInsets: WindowInsets = BottomSheetDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val animateToDismiss: () -> Unit = {
        if (sheetState.swipeableState.confirmValueChange(Hidden)) {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    onDismissRequest()
                }
            }
        }
    }
    val settleToDismiss: (velocity: Float) -> Unit = {
        scope.launch { sheetState.settle(it) }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismissRequest()
        }
    }

    // Callback that is invoked when the anchors have changed.
    val anchorChangeHandler = remember(sheetState, scope) {
        ModalBottomSheetAnchorChangeHandler(
            state = sheetState,
            animateTo = { target, velocity ->
                scope.launch { sheetState.animateTo(target, velocity = velocity) }
            },
            snapTo = { target ->
                val didSnapImmediately = sheetState.trySnapTo(target)
                if (!didSnapImmediately) {
                    scope.launch { sheetState.snapTo(target) }
                }
            }
        )
    }

    ModalBottomSheetPopup(
        onDismissRequest = {
            if (sheetState.currentValue == Expanded && sheetState.hasPartiallyExpandedState) {
                scope.launch { sheetState.partialExpand() }
            } else { // Is expanded without collapsed state or is collapsed.
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
            }
        },
        windowInsets = windowInsets,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fullHeight = constraints.maxHeight
            Scrim(
                color = scrimColor,
                onDismissRequest = animateToDismiss,
                visible = sheetState.targetValue != Hidden
            )
            val bottomSheetPaneTitle = getString(string = Strings.BottomSheetPaneTitle)
            Surface(
                modifier = modifier
                    .widthIn(max = BottomSheetMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .semantics { paneTitle = bottomSheetPaneTitle }
                    .offset {
                        IntOffset(
                            0,
                            sheetState
                                .requireOffset()
                                .toInt()
                        )
                    }
                    .nestedScroll(
                        remember(sheetState) {
                            ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection(
                                sheetState = sheetState,
                                orientation = Orientation.Vertical,
                                onFling = settleToDismiss
                            )
                        }
                    )
                    .modalBottomSheetSwipeable(
                        sheetState = sheetState,
                        anchorChangeHandler = anchorChangeHandler,
                        screenHeight = fullHeight.toFloat(),
                        onDragStopped = {
                            settleToDismiss(it)
                        },
                    ),
                shape = shape,
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = tonalElevation,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    if (dragHandle != null) {
                        val collapseActionLabel =
                            getString(Strings.BottomSheetPartialExpandDescription)
                        val dismissActionLabel = getString(Strings.BottomSheetDismissDescription)
                        val expandActionLabel = getString(Strings.BottomSheetExpandDescription)
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .semantics(mergeDescendants = true) {
                                    // Provides semantics to interact with the bottomsheet based on its
                                    // current value.
                                    with(sheetState) {
                                        dismiss(dismissActionLabel) {
                                            animateToDismiss()
                                            true
                                        }
                                        if (currentValue == PartiallyExpanded) {
                                            expand(expandActionLabel) {
                                                if (swipeableState.confirmValueChange(Expanded)) {
                                                    scope.launch { sheetState.expand() }
                                                }
                                                true
                                            }
                                        } else if (hasPartiallyExpandedState) {
                                            collapse(collapseActionLabel) {
                                                if (
                                                    swipeableState.confirmValueChange(
                                                        PartiallyExpanded
                                                    )
                                                ) {
                                                    scope.launch { partialExpand() }
                                                }
                                                true
                                            }
                                        }
                                    }
                                }
                        ) {
                            dragHandle()
                        }
                    }
                    content()
                }
            }
        }
    }
    if (sheetState.hasExpandedState) {
        LaunchedEffect(sheetState) {
            sheetState.show()
        }
    }
}

/**
 * Create and [remember] a [SheetState] for [ModalBottomSheet].
 *
 * @param skipPartiallyExpanded Whether the partially expanded state, if the sheet is tall enough,
 * should be skipped. If true, the sheet will always expand to the [Expanded] state and move to the
 * [Hidden] state when hiding the sheet, either programmatically or by user interaction.
 * @param confirmValueChange Optional callback invoked to confirm or veto a pending state change.
 */
@Composable
@ExperimentalMaterial3Api
fun rememberModalBottomSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true },
) = rememberSheetState(skipPartiallyExpanded, confirmValueChange, Hidden)

@Composable
private fun Scrim(
    color: Color,
    onDismissRequest: () -> Unit,
    visible: Boolean
) {
    if (color.isSpecified) {
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = TweenSpec()
        )
        val dismissSheet = if (visible) {
            Modifier
                .pointerInput(onDismissRequest) {
                    detectTapGestures {
                        onDismissRequest()
                    }
                }
                .clearAndSetSemantics {}
        } else {
            Modifier
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .then(dismissSheet)
        ) {
            drawRect(color = color, alpha = alpha)
        }
    }
}

@ExperimentalMaterial3Api
private fun Modifier.modalBottomSheetSwipeable(
    sheetState: SheetState,
    anchorChangeHandler: AnchorChangeHandler<SheetValue>,
    screenHeight: Float,
    onDragStopped: CoroutineScope.(velocity: Float) -> Unit,
) = draggable(
        state = sheetState.swipeableState.swipeDraggableState,
        orientation = Orientation.Vertical,
        enabled = sheetState.isVisible,
        startDragImmediately = sheetState.swipeableState.isAnimationRunning,
        onDragStopped = onDragStopped
    )
    .swipeAnchors(
        state = sheetState.swipeableState,
        anchorChangeHandler = anchorChangeHandler,
        possibleValues = setOf(Hidden, PartiallyExpanded, Expanded),
    ) { value, sheetSize ->
        when (value) {
            Hidden -> screenHeight
            PartiallyExpanded -> when {
                sheetSize.height < screenHeight / 2 -> null
                sheetState.skipPartiallyExpanded -> null
                else -> screenHeight / 2f
            }
            Expanded -> if (sheetSize.height != 0) {
                max(0f, screenHeight - sheetSize.height)
            } else null
        }
    }

@ExperimentalMaterial3Api
private fun ModalBottomSheetAnchorChangeHandler(
    state: SheetState,
    animateTo: (target: SheetValue, velocity: Float) -> Unit,
    snapTo: (target: SheetValue) -> Unit,
) = AnchorChangeHandler<SheetValue> { previousTarget, previousAnchors, newAnchors ->
    val previousTargetOffset = previousAnchors[previousTarget]
    val newTarget = when (previousTarget) {
        Hidden -> Hidden
        PartiallyExpanded, Expanded -> {
            val hasPartiallyExpandedState = newAnchors.containsKey(PartiallyExpanded)
            val newTarget = if (hasPartiallyExpandedState) PartiallyExpanded
            else if (newAnchors.containsKey(Expanded)) Expanded else Hidden
            newTarget
        }
    }
    val newTargetOffset = newAnchors.getValue(newTarget)
    if (newTargetOffset != previousTargetOffset) {
        if (state.swipeableState.isAnimationRunning || previousAnchors.isEmpty()) {
            // Re-target the animation to the new offset if it changed
            animateTo(newTarget, state.swipeableState.lastVelocity)
        } else {
            // Snap to the new offset value of the target if no animation was running
            snapTo(newTarget)
        }
    }
}

/**
 * Popup specific for modal bottom sheet.
 */
@Composable
internal expect fun ModalBottomSheetPopup(
    onDismissRequest: () -> Unit,
    windowInsets: WindowInsets,
    content: @Composable () -> Unit,
)
