/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.build.metalava

import androidx.build.checkapi.ApiLocation
import com.google.common.io.Files
import java.io.File
import java.security.MessageDigest
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Updates API signature text files.
 * In practice, the values they will be updated to will match the APIs defined by the source code.
 */
@CacheableTask
abstract class UpdateApiTask : DefaultTask() {

    /** Text file from which API signatures will be read. */
    @get:Input
    abstract val inputApiLocation: Property<ApiLocation>

    /** Text files to which API signatures will be written. */
    @get:Internal // outputs are declared in getTaskOutputs()
    abstract val outputApiLocations: ListProperty<ApiLocation>

    /** If overriding policy, Buganizer ID where the override was approved. */
    @get:Option(
        option = "overridePolicyWithApproval",
        description = "Buganizer ID where overriding Semantic Versioning policy was approved."
    )
    @get:Input
    @get:Optional
    abstract val overridePolicyWithApproval: Property<String>

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getTaskInputs(): List<File> {
        val inputApi = inputApiLocation.get()
        return listOf(
            inputApi.publicApiFile,
            inputApi.restrictedApiFile,
            inputApi.experimentalApiFile,
            inputApi.removedApiFile
        )
    }

    @Suppress("unused")
    @OutputFiles
    fun getTaskOutputs(): List<File> {
        return outputApiLocations.get().flatMap { outputApiLocation ->
            listOf(
                outputApiLocation.publicApiFile,
                outputApiLocation.restrictedApiFile,
                outputApiLocation.experimentalApiFile,
                outputApiLocation.removedApiFile
            )
        }
    }

    @TaskAction
    fun exec() {
        // Always validate overridePolicyWithApproval, even if it's not needed.
        val canOverridePolicy = verifyCanOverridePolicy()

        var permitOverwriting = true
        for (outputApi in outputApiLocations.get()) {
            val version = outputApi.version()
            if (version != null && version.isFinalApi() &&
                outputApi.publicApiFile.exists() &&
                !canOverridePolicy
            ) {
                permitOverwriting = false
            }
        }
        for (outputApi in outputApiLocations.get()) {
            val inputApi = inputApiLocation.get()
            copy(
                source = inputApi.publicApiFile,
                dest = outputApi.publicApiFile,
                permitOverwriting = permitOverwriting,
                logger = logger
            )
            copy(
                source = inputApi.removedApiFile,
                dest = outputApi.removedApiFile,
                permitOverwriting = permitOverwriting,
                logger = logger
            )
            copy(
                source = inputApi.experimentalApiFile,
                dest = outputApi.experimentalApiFile,
                // Experimental APIs are never locked down,
                // so it's always okay to overwrite them.
                permitOverwriting = true,
                logger = logger
            )
            copy(
                source = inputApi.restrictedApiFile,
                dest = outputApi.restrictedApiFile,
                permitOverwriting = permitOverwriting,
                logger = logger
            )
        }
    }

    private fun verifyCanOverridePolicy(): Boolean {
        if (!overridePolicyWithApproval.isPresent) return false

        val buganizerId = overridePolicyWithApproval.get().toLongOrNull()
        if (buganizerId == null || buganizerId < 250000000) {
            throw GradleException(
                "Invalid value for argument 'overridePolicyWithApproval'. To override Semantic " +
                    "Versioning policy, you must specify a valid Buganizer ID indicating where " +
                    "the policy override was approved."
            )
        }

        return true
    }
}

fun copy(
    source: File,
    dest: File,
    permitOverwriting: Boolean,
    logger: Logger? = null
) {
    val sourceText = if (source.exists()) {
        source.readText()
    } else {
        ""
    }
    val overwriting = (dest.exists() && sourceText != dest.readText())
    val changing = overwriting || (dest.exists() != source.exists())
    if (changing) {
        if (overwriting && !permitOverwriting) {
            val diff = summarizeDiff(source, dest, maxDiffLines + 1)
            val diffMsg = if (compareLineCount(diff, maxDiffLines) > 0) {
                "Diff is greater than $maxDiffLines lines, use diff tool to compare.\n\n"
            } else {
                "Diff:\n$diff\n\n"
            }
            val message = "Modifying the API definition for a previously released artifact " +
                "having a final API version (version not ending in '-alpha') is not " +
                "allowed.\n\n" +
                "Previously declared definition is $dest\n" +
                "Current generated   definition is $source\n\n" +
                diffMsg +
                "Did you mean to increment the library version first?\n\n" +
                "If you have a valid reason to override Semantic Versioning policy, see " +
                "go/androidx/versioning#beta-api-change for information on obtaining approval."
            throw GradleException(message)
        }
        if (source.exists()) {
            @Suppress("UnstableApiUsage")
            Files.copy(source, dest)
            logger?.lifecycle("Copied $source to $dest")
        } else {
            dest.delete()
            logger?.lifecycle("Deleted $dest because $source does not exist")
        }
    }
}

fun copyDir(
    source: File,
    dest: File,
    permitOverwriting: Boolean,
    logger: Logger
) {
    val sourceHash = if (source.exists()) {
        hashDir(source)
    } else {
        null
    }
    val overwriting = dest.exists() && !sourceHash.contentEquals(hashDir(dest))
    val changing = overwriting || (dest.exists() != source.exists())
    if (changing) {
        if (overwriting && !permitOverwriting) {
            val message = "Modifying the API definition for a previously released artifact " +
                "having a final API version (version not ending in '-alpha') is not " +
                "allowed.\n\n" +
                "Previously declared definition is $dest\n" +
                "Current generated   definition is $source\n\n" +
                "Did you mean to increment the library version first?\n\n" +
                "If you have reason to overwrite the API files for the previous release " +
                "anyway, you can run `./gradlew updateApi -Pforce` to ignore this message"
            throw GradleException(message)
        }
        FileUtils.deleteDirectory(dest)
        if (source.exists()) {
            FileUtils.copyDirectory(source, dest)
            logger.lifecycle("Copied $source to $dest")
        } else {
            logger.lifecycle("Deleted $dest because $source does not exist")
        }
    }
}

fun hashDir(dir: File): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    dir.listFiles()?.forEach { file ->
        val fileBytes = if (file.isDirectory) {
            hashDir(file)
        } else {
            file.readBytes()
        }
        digest.update(fileBytes)
    }
    return digest.digest()
}

/**
 * Returns -1 if [text] has fewer than [count] newline characters, 0 if equal, and 1 if greater
 * than.
 */
fun compareLineCount(text: String, count: Int): Int {
    var found = 0
    var index = 0
    while (found < count) {
        index = text.indexOf('\n', index)
        if (index < 0) {
            break
        }
        found++
        index++
    }
    return if (found < count) {
        -1
    } else if (found == count) {
        0
    } else {
        1
    }
}

/** Maximum number of diff lines to include in output. */
internal const val maxDiffLines = 8
