/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal actual class PlatformDateFormat actual constructor(private val locale: CalendarLocale) {
    init {
        TODO()
    }

    actual val firstDayOfWeek: Int get() = TODO()

    actual fun formatWithPattern(
        utcTimeMillis: Long,
        pattern: String,
    ): String {
        TODO()
    }

    actual fun formatWithSkeleton(
        utcTimeMillis: Long,
        skeleton: String,
    ): String {
        TODO()
    }

    actual fun parse(
        date: String,
        pattern: String
    ): CalendarDate? {
        TODO()
    }

    actual fun getDateInputFormat(): DateInputFormat {
        TODO()
    }

    actual val weekdayNames: List<Pair<String, String>> get() {
        TODO()
    }

    actual fun is24HourFormat() = false
}
