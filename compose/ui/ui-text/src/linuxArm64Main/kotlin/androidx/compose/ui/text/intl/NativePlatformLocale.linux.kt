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

package androidx.compose.ui.text.intl

internal object EnUsLocale : PlatformLocale {
    override val language: String get() = "en"
    override val script: String get() = ""
    override val region: String get() = "US"
    override fun toLanguageTag(): String = "en-US"
}

internal actual fun createPlatformLocaleDelegate(): PlatformLocaleDelegate =
    object : PlatformLocaleDelegate {
        override val current get() = LocaleList(listOf(Locale(EnUsLocale)))
        override fun parseLanguageTag(languageTag: String) = EnUsLocale
    }

internal actual fun PlatformLocale.isRtl(): Boolean = false
