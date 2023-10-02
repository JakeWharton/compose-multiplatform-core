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

package androidx.compose.foundation.text

import kotlin.text.CharCategory.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Sets the cursor offset when the text field is focused in a Cupertino-style text input field.
 *
 * The rules for determining position of the caret are as follows:
 *
 * - When you make a single tap on a word, the caret moves to the end of this word.
 * - If there’s a punctuation mark after the word, the caret is between the word and the punctuation mark.
 * - If you tap on a whitespace, the caret is placed before the word. Same for many whitespaces in a row. (and punctuation marks)
 * - If there’s a punctuation mark before the word, the caret is between the punctuation mark and the word.
 * - When you make a single tap on the first letter of the word, the caret is placed before this word.
 * - If you tap on the left edge of the TextField, the caret is placed before the first word on this line. The same is for the right edge.
 * - If you tap at the caret, that is placed in the middle of the word, it will jump to the end of the word.
 *
 * @param position The position at which the cursor is tapped.
 * @param textLayoutResult The result of the text layout for the text field.
 * @param editProcessor The edit processor for the text field.
 * @param offsetMapping The offset mapping for the text field.
 * @param onValueChange The callback function to be invoked when the text field value changes.
 */
internal fun TextFieldDelegate.Companion.cupertinoSetCursorOffsetFocused(
    position: Offset,
    textLayoutResult: TextLayoutResultProxy,
    editProcessor: EditProcessor,
    offsetMapping: OffsetMapping,
    showContextMenu: (Rect) -> Unit,
    onValueChange: (TextFieldValue) -> Unit
) {
    val offset =
        offsetMapping.transformedToOriginal(textLayoutResult.getOffsetForPosition(position))
    val currentValue = editProcessor.toTextFieldValue()
    val currentText = currentValue.text

    val caretOffsetPosition: Int
    if (textLayoutResult.isCaretTapped(offset, currentValue.selection.start)) {
        /* Menu with context actions should be opened by tap on the caret,
         * caret should remain on the same position.
         */
        showContextMenu(textLayoutResult.value.getCursorRect(offset))
        caretOffsetPosition = offset
    } else if (textLayoutResult.isLeftEdgeTapped(offset)) {
        val lineNumber = textLayoutResult.value.getLineForOffset(offset)
        caretOffsetPosition = textLayoutResult.value.getLineStart(lineNumber)
    } else if (textLayoutResult.isRightEdgeTapped(offset)) {
        val lineNumber = textLayoutResult.value.getLineForOffset(offset)
        caretOffsetPosition = textLayoutResult.value.getLineEnd(lineNumber)
    } else if (textLayoutResult.isPunctuationOrSpaceBeforeWordTapped(offset, currentText)) {
        val nextWordBoundary = findNextWordBoundary(offset, currentText, textLayoutResult)
        caretOffsetPosition = nextWordBoundary.start
    } else if (textLayoutResult.isFirstLetterOfWordTapped(offset)) {
        val wordBoundary = textLayoutResult.value.getWordBoundary(offset)
        caretOffsetPosition = wordBoundary.start
    } else {
        val wordBoundary = textLayoutResult.value.getWordBoundary(offset)
        caretOffsetPosition = wordBoundary.end
    }

    onValueChange(editProcessor.toTextFieldValue().copy(selection = TextRange(caretOffsetPosition)))
}

private fun TextLayoutResultProxy.isPunctuationOrSpaceBeforeWordTapped(
    caretOffset: Int,
    currentText: String
): Boolean {
    /* From TextLayoutResultProxy.value.getWordBoundary(caretOffset) it is clear
    * that for whitespace or punctuation mark method will return empty range.
    * Thus, if empty range was returned, and the caretOffset greater than start and less than last index of text
    * then offset should be somewhere between words.
    * */
    val char = currentText[caretOffset]
    val isGreaterThanFirst = caretOffset >= 0
    val isLessThanLast = caretOffset <= currentText.lastIndex
    return char.isPunctuationOrWhitespace() && isGreaterThanFirst && isLessThanLast
}

private fun TextLayoutResultProxy.isFirstLetterOfWordTapped(caretOffset: Int): Boolean {
    return value.getWordBoundary(caretOffset).start == caretOffset
}

private fun TextLayoutResultProxy.isCaretTapped(
    caretOffset: Int,
    previousCaretOffset: Int
): Boolean {
    return previousCaretOffset == caretOffset
}

private fun TextLayoutResultProxy.isLeftEdgeTapped(caretOffset: Int): Boolean {
    val lineNumber = value.getLineForOffset(caretOffset)
    val lineStartOffset = value.getLineStart(lineNumber)
    return lineStartOffset == caretOffset
}

private fun TextLayoutResultProxy.isRightEdgeTapped(caretOffset: Int): Boolean {
    val lineNumber = value.getLineForOffset(caretOffset)
    val lineStartOffset = value.getLineEnd(lineNumber)
    return lineStartOffset == caretOffset
}

private fun findNextWordBoundary(
    caretOffset: Int,
    currentText: String,
    textLayoutResult: TextLayoutResultProxy
): TextRange {
    val wordRange = textLayoutResult.value.getWordBoundary(caretOffset)
    val currentChar = currentText[caretOffset]
    return if (!currentChar.isPunctuationOrWhitespace()) {
        wordRange
    } else if (caretOffset >= currentText.lastIndex) {
        TextRange.Zero
    } else {
        findNextWordBoundary(caretOffset + 1, currentText, textLayoutResult)
    }
}

private fun Char.isPunctuationOrWhitespace(): Boolean {
    return this.isPunctuation() || this.isWhitespace()
}

private fun Char.isPunctuation(): Boolean {
    val punctuationSet = setOf(
        DASH_PUNCTUATION,
        START_PUNCTUATION,
        END_PUNCTUATION,
        CONNECTOR_PUNCTUATION,
        OTHER_PUNCTUATION
    )
    punctuationSet.forEach {
        if (it.contains(this)) {
            return true
        }
    }
    return false
}