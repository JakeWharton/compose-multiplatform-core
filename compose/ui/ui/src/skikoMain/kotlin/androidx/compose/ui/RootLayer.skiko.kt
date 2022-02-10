/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

@Composable
@ExperimentalComposeUiApi
fun RootLayer(content: @Composable () -> Unit) {
    val currentContent by rememberUpdatedState(content)
    val compositionLocalContext by rememberUpdatedState(currentCompositionLocalContext)
    val scene = LocalComposeScene.current
    DisposableEffect(Unit) {
        val layer = ComposeScene.Layer {
            CompositionLocalProvider(compositionLocalContext) {
                currentContent()
            }
        }
        scene.addLayer(layer)
        onDispose {
            scene.removeLayer(layer)
        }
    }
}
