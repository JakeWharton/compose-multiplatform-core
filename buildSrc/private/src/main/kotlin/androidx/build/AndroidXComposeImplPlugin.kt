/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.build

import androidx.build.Multiplatform.Companion.openExpectLiteMode
import androidx.build.MultiplatformUtils.disableCompilationsOfTarget
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.lint.VariantInputs
import kotlin.reflect.KFunction
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.commonizer.util.transitiveClosure
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import java.io.File
import kotlin.reflect.full.memberProperties

const val composeSourceOption =
    "plugin:androidx.compose.compiler.plugins.kotlin:sourceInformation=true"
const val composeMetricsOption =
    "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination"
const val enableMetricsArg = "androidx.enableComposeCompilerMetrics"

/**
 * Plugin to apply common configuration for Compose projects.
 */
class AndroidXComposeImplPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val f: KFunction<Unit> = AndroidXComposeImplPlugin.Companion::applyAndConfigureKotlinPlugin
        project.extensions.add("applyAndConfigureKotlinPlugin", f)
        project.plugins.all { plugin ->
            when (plugin) {
                is LibraryPlugin -> {
                    val library = project.extensions.findByType(LibraryExtension::class.java)
                        ?: throw Exception("Failed to find Android extension")

                    project.configureAndroidCommonOptions(library)
                }
                is AppPlugin -> {
                    val app = project.extensions.findByType(AppExtension::class.java)
                        ?: throw Exception("Failed to find Android extension")

                    project.configureAndroidCommonOptions(app)
                }
                is KotlinBasePluginWrapper -> {
                    project.configureComposeImplPluginForAndroidx()

                    if (plugin is KotlinMultiplatformPluginWrapper) {
                        project.configureForMultiplatform()
                    }
                }
            }
        }
    }

    companion object {

        /**
         * @param isMultiplatformEnabled whether this module has a corresponding
         * multiplatform configuration, or whether it is Android only
         */
        fun applyAndConfigureKotlinPlugin(
            project: Project,
            isMultiplatformEnabled: Boolean
        ) {
            if (isMultiplatformEnabled) {
                project.apply(plugin = "kotlin-multiplatform")
            } else {
                project.apply(plugin = "org.jetbrains.kotlin.android")
            }

            project.configureManifests()
            if (isMultiplatformEnabled) {
                project.configureForMultiplatform()
            } else {
                project.configureForKotlinMultiplatformSourceStructure()
            }

            project.tasks.withType(KotlinCompile::class.java).configureEach { compile ->
                // Needed to enable `expect` and `actual` keywords
                compile.kotlinOptions.freeCompilerArgs += "-Xmulti-platform"
            }

            project.tasks.withType(KotlinJsCompile::class.java).configureEach { compile ->
                compile.kotlinOptions.freeCompilerArgs += listOf(
                    "-P", "plugin:androidx.compose.compiler.plugins.kotlin:generateDecoys=true"
                )
            }
        }

        private fun Project.androidxExtension(): AndroidXExtension? {
            return extensions.findByType(AndroidXExtension::class.java)
        }

        private fun Project.configureAndroidCommonOptions(testedExtension: TestedExtension) {
            testedExtension.defaultConfig.minSdk = 21

            @Suppress("UnstableApiUsage")
            extensions.findByType(AndroidComponentsExtension::class.java)!!.finalizeDsl {
                val isPublished = androidxExtension()?.type == LibraryType.PUBLISHED_LIBRARY

                @Suppress("DEPRECATION") // lintOptions methods
                testedExtension.lintOptions.apply {
                    // Too many Kotlin features require synthetic accessors - we want to rely on R8 to
                    // remove these accessors
                    disable("SyntheticAccessor")
                    // These lint checks are normally a warning (or lower), but we ignore (in AndroidX)
                    // warnings in Lint, so we make it an error here so it will fail the build.
                    // Note that this causes 'UnknownIssueId' lint warnings in the build log when
                    // Lint tries to apply this rule to modules that do not have this lint check, so
                    // we disable that check too
                    disable("UnknownIssueId")
                    error("ComposableNaming")
                    error("ComposableLambdaParameterNaming")
                    error("ComposableLambdaParameterPosition")
                    error("CompositionLocalNaming")
                    error("ComposableModifierFactory")
                    error("InvalidColorHexValue")
                    error("MissingColorAlphaChannel")
                    error("ModifierFactoryReturnType")
                    error("ModifierFactoryExtensionFunction")
                    error("ModifierParameter")
                    error("UnnecessaryComposedModifier")

                    // Paths we want to enable ListIterator checks for - for higher level
                    // libraries it won't have a noticeable performance impact, and we don't want
                    // developers reading high level library code to worry about this.
                    val listIteratorPaths = listOf(
                        "compose:foundation",
                        "compose:runtime",
                        "compose:ui",
                        "text"
                    )

                    // Paths we want to disable ListIteratorChecks for - these are not runtime
                    // libraries and so Iterator allocation is not relevant.
                    val ignoreListIteratorFilter = listOf(
                        "compose:ui:ui-test",
                        "compose:ui:ui-tooling",
                        "compose:ui:ui-inspection",
                    )

                    // Disable ListIterator if we are not in a matching path, or we are in an
                    // unpublished project
                    if (
                        listIteratorPaths.none { path.contains(it) } ||
                        ignoreListIteratorFilter.any { path.contains(it) } ||
                        !isPublished
                    ) {
                        disable("ListIterator")
                    }
                }
            }

            // TODO(148540713): remove this exclusion when Lint can support using multiple lint jars
            configurations.getByName("lintChecks").exclude(
                mapOf("module" to "lint-checks")
            )
            // TODO: figure out how to apply this to multiplatform modules
            dependencies.add(
                "lintChecks",
                project.dependencies.project(
                    mapOf(
                        "path" to ":compose:lint:internal-lint-checks",
                        "configuration" to "shadow"
                    )
                )
            )
        }

        private fun Project.configureManifests() {
            val libraryExtension = project.extensions.findByType<LibraryExtension>() ?: return
            libraryExtension.apply {
                sourceSets.findByName("main")!!.manifest
                    .srcFile("src/androidMain/AndroidManifest.xml")
                sourceSets.findByName("androidTest")!!.manifest
                    .srcFile("src/androidAndroidTest/AndroidManifest.xml")
            }
        }

        /**
         * General configuration for MPP projects. In the future, these workarounds should either be
         * generified and added to AndroidXPlugin, or removed as/when the underlying issues have been
         * resolved.
         */
        private fun Project.configureForKotlinMultiplatformSourceStructure() {
            val libraryExtension = project.extensions.findByType<LibraryExtension>() ?: return

            // TODO: b/148416113: AGP doesn't know about Kotlin-MPP's sourcesets yet, so add
            // them to its source directories (this fixes lint, and code completion in
            // Android Studio on versions >= 4.0canary8)
            libraryExtension.apply {
                sourceSets.findByName("main")?.apply {
                    java.srcDirs(
                        "src/commonMain/kotlin", "src/jvmMain/kotlin",
                        "src/androidMain/kotlin"
                    )
                    res.srcDirs(
                        "src/commonMain/resources",
                        "src/androidMain/res"
                    )
                    assets.srcDirs("src/androidMain/assets")

                    // Keep Kotlin files in java source sets so the source set is not empty when
                    // running unit tests which would prevent the tests from running in CI.
                    java.includes.add("**/*.kt")
                }
                sourceSets.findByName("test")?.apply {
                    java.srcDirs(
                        "src/test/kotlin", "src/commonTest/kotlin", "src/jvmTest/kotlin"
                    )
                    res.srcDirs("src/test/res")

                    // Keep Kotlin files in java source sets so the source set is not empty when
                    // running unit tests which would prevent the tests from running in CI.
                    java.includes.add("**/*.kt")
                }
                sourceSets.findByName("androidTest")?.apply {
                    java.srcDirs("src/androidAndroidTest/kotlin")
                    res.srcDirs("src/androidAndroidTest/res")
                    assets.srcDirs("src/androidAndroidTest/assets")

                    // Keep Kotlin files in java source sets so the source set is not empty when
                    // running unit tests which would prevent the tests from running in CI.
                    java.includes.add("**/*.kt")
                }
            }
        }

        /**
         * General configuration for MPP projects. In the future, these workarounds should either be
         * generified and added to AndroidXPlugin, or removed as/when the underlying issues have been
         * resolved.
         */
        private fun Project.configureForMultiplatform() {
            val multiplatformExtension = checkNotNull(multiplatformExtension) {
                "Unable to configureForMultiplatform() when " +
                    "multiplatformExtension is null (multiplatform plugin not enabled?)"
            }

            /*
            The following configures source sets - note:

            1. The common unit test source set, commonTest, is included by default in both android
            unit and instrumented tests. This causes unnecessary duplication, so we explicitly do
            _not_ use commonTest, instead choosing to just use the unit test variant.
            TODO: Consider using commonTest for unit tests if a usable feature is added for
            https://youtrack.jetbrains.com/issue/KT-34662.

            2. The default (android) unit test source set is named 'androidTest', which conflicts / is
            confusing as this shares the same name / expected directory as AGP's 'androidTest', which
            represents _instrumented_ tests.
            TODO: Consider changing unitTest to androidLocalTest and androidAndroidTest to
            androidDeviceTest when https://github.com/JetBrains/kotlin/pull/2829 rolls in.
            */
            multiplatformExtension.sourceSets.all {
                // Allow all experimental APIs, since MPP projects are themselves experimental
                it.languageSettings.apply {
                    optIn("kotlin.Experimental")
                    optIn("kotlin.ExperimentalMultiplatform")
                }
            }

            configureLintForMultiplatformLibrary(multiplatformExtension)

            afterEvaluate {
                if (multiplatformExtension.targets.findByName("jvm") != null) {
                    tasks.named("jvmTestClasses").also(::addToBuildOnServer)
                }
                if (multiplatformExtension.targets.findByName("desktop") != null) {
                    tasks.named("desktopTestClasses").also(::addToBuildOnServer)
                }
            }

            when (project.openExpectLiteMode()) {
                null -> Unit
                OpenExpectLiteMode.ANDROIDX -> configureOpenExpectLiteAndroidX()
                OpenExpectLiteMode.ORG_JETBRAINS -> configureOpenExpectLiteOrgJetbrains()
            }
        }

        private fun isTargetBuiltByAndroidX(target: KotlinTarget): Boolean =
            target.platformType !in arrayOf(KotlinPlatformType.jvm, KotlinPlatformType.js)

        private fun isTargetBuiltByOrgJetbrains(target: KotlinTarget): Boolean =
            target !is KotlinAndroidTarget

        // FIXME: reflection access! Some API in Kotlin is needed
        @Suppress("unchecked_cast")
        private val KotlinTarget.kotlinComponents: Iterable<KotlinTargetComponent>
            get() = javaClass.kotlin.memberProperties
                .single { it.name == "kotlinComponents" }
                .get(this) as Iterable<KotlinTargetComponent>

        private fun Project.configureOpenExpectLiteAndroidX() {
            val ext = project.multiplatformExtension ?: error("expected a multiplatform project")
            ext.targets.all { target ->
                if (!isTargetBuiltByAndroidX(target)) {
                    disableCompilationsOfTarget(target)
                    publishWithoutPlatformModule(target)
                }
            }
        }

        private fun Project.configureOpenExpectLiteOrgJetbrains() {
            val ext = project.multiplatformExtension ?: error("expected a multiplatform project")
            ext.targets.all { target ->
                if (!isTargetBuiltByOrgJetbrains(target)) {
                    disableCompilationsOfTarget(target)
                    publishWithoutPlatformModule(target)
                }
            }
            disableCompilationsOfTarget(ext.metadata())
        }

        private fun publishWithoutPlatformModule(target: KotlinTarget) {
            val project = target.project

            //FIXME: this code is called twice because of shared-dependencies.gradle.
            //  So we have to add a check, because otherwise Gradle fails on non-unique entities
            val singleCallPropertyName = "compose.publishWithoutPlatformModule.done.${target.name}"
            with(project.extensions.extraProperties) {
                if (!has(singleCallPropertyName)) {
                    set(singleCallPropertyName, "true")
                } else return
            }

            // Disable a separate platform module publication for Android targets,
            // and exclude the variants of this target from the root module:
            if (target is KotlinAndroidTarget)
                target.publishLibraryVariants = emptyList()

            target.project.afterEvaluate {
                target.kotlinComponents.forEach { platformComponent ->
                    project.publishInRootModuleWithoutArtifacts(platformComponent)
                }
            }
        }

        private fun Project.publishInRootModuleWithoutArtifacts(component: KotlinTargetComponent) {
            val multiplatformExtension =
                project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                    ?: error("Expected a multiplatform project")

            val componentName = component.name

            // Exclude the original variants of this target from the root module, as below we
            // add them to the root module in a different way that doesn't lead to 'available-at'
            // entries in the Gradle *.module metadata:
            if (component is KotlinVariant)
                component.publishable = false

            val usages = when (component) {
                is KotlinVariant -> component.usages
                is JointAndroidKotlinTargetComponent -> component.usages
                else -> emptyList()
            }

            extensions.getByType(PublishingExtension::class.java)
                .publications.withType(DefaultMavenPublication::class.java)
                // isAlias is needed for Gradle to ignore the fact that there's a
                // publication that is not referenced as an available-at variant of the root module
                // and has the Maven coordinates that are different from those of the root module
                // FIXME: internal Gradle API! We would rather not create the publications,
                //        but some API for that is needed in the Kotlin Gradle plugin
                .all { if (it.name == componentName) it.isAlias = true }

            tasks.withType(PublishToMavenRepository::class.java)
                .matching { it.publication?.name == componentName }
                .configureEach { it.enabled = false }

            usages.forEach { kotlinUsageContext ->
                // FIXME: an assumption is made here about the configuration name related to
                //        the usage component
                // This configuration models the published platform variant; we are going to add
                // it to the root module, so that it is present there with its original name,
                // original attributes, and original dependencies, but we don't want its artifact:
                val configurationName = kotlinUsageContext.name + "-published"
                // The configuration might not have been created at this point
                configurations.matching { it.name == configurationName }.all { configuration ->
                    configuration.artifacts.clear()
                }

                val rootComponent = components.withType(KotlinSoftwareComponent::class.java)
                    .getByName("kotlin")

                // Register the artifact-less variant in the root module:
                // FIXME: stable API is needed for adding a variant to the root component
                (rootComponent.usages as MutableSet).add(
                    DefaultKotlinUsageContext(
                        multiplatformExtension.metadata().compilations.getByName("main"),
                        objects.named(Usage::class.java, "kotlin-api"),
                        configurationName
                    )
                )
            }
        }
    }
}

fun Project.configureComposeImplPluginForAndroidx() {

    val conf = project.configurations.create("kotlinPlugin")
    val kotlinPlugin = conf.incoming.artifactView { view ->
        view.attributes { attributes ->
            attributes.attribute(
                Attribute.of("artifactType", String::class.java),
                ArtifactTypeDefinition.JAR_TYPE
            )
        }
    }.files

    val isTipOfTreeComposeCompilerProvider = project.provider({
        conf.dependencies.first() !is ExternalModuleDependency
    })

    project.tasks.withType(KotlinCompile::class.java).configureEach { compile ->
        // TODO(b/157230235): remove when this is enabled by default
        compile.kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        compile.inputs.files({ kotlinPlugin })
            .withPropertyName("composeCompilerExtension")
            .withNormalizer(ClasspathNormalizer::class.java)
        compile.doFirst {
            if (!kotlinPlugin.isEmpty) {
                compile.kotlinOptions.freeCompilerArgs +=
                    "-Xplugin=${kotlinPlugin.first()}"

                val enableMetrics = project
                    .findProperty(enableMetricsArg) == "true"

                // since metrics reports in compose compiler are a new feature, we only want to
                // pass in this parameter for modules that are using the tip of tree compose
                // compiler, or else we will run into an exception since the parameter will not
                // be recognized.
                if (isTipOfTreeComposeCompilerProvider.get() && enableMetrics) {
                    val libMetrics = project
                        .rootProject
                        .getLibraryMetricsDirectory()
                    val metricsDest = File(libMetrics, "compose")
                        .absolutePath
                    compile.kotlinOptions.freeCompilerArgs +=
                        listOf(
                            "-P",
                            "$composeMetricsOption=$metricsDest"
                        )
                }
            }
        }
    }

    project.afterEvaluate {
        val androidXExtension =
            project.extensions.findByType(AndroidXExtension::class.java)
        if (androidXExtension != null) {
            if (androidXExtension.publish.shouldPublish()) {
                project.tasks.withType(KotlinCompile::class.java)
                    .configureEach { compile ->
                        compile.doFirst {
                            if (!kotlinPlugin.isEmpty) {
                                compile.kotlinOptions.freeCompilerArgs +=
                                    listOf("-P", composeSourceOption)
                            }
                        }
                    }
            }
        }
    }
}

/**
 * Adds missing MPP sourcesets (such as commonMain) to the Lint tasks
 *
 * TODO: b/195329463
 * Lint is not aware of MPP, and MPP doesn't configure Lint. There is no built-in
 * API to adjust the default Lint task's sources, so we use this hack to manually
 * add sources for MPP source sets. In the future with the new Kotlin Project Model
 * (https://youtrack.jetbrains.com/issue/KT-42572) and an AGP / MPP integration
 * plugin this will no longer be needed.
 */
private fun Project.configureLintForMultiplatformLibrary(
    multiplatformExtension: KotlinMultiplatformExtension
) {
    afterEvaluate {
        // This workaround only works for libraries (apps would require changes to a different
        // task). Given that we currently do not have any MPP app projects, this should never
        // happen.
        project.extensions.findByType<LibraryExtension>()
            ?: return@afterEvaluate
        val androidMain = multiplatformExtension.sourceSets.findByName("androidMain")
            ?: return@afterEvaluate
        // Get all the sourcesets androidMain transitively / directly depends on
        val dependencies = transitiveClosure(androidMain, KotlinSourceSet::dependsOn)

        /**
         * Helper function to add the missing sourcesets to this [VariantInputs]
         */
        fun VariantInputs.addSourceSets() {
            // Each variant has a source provider for the variant (such as debug) and the 'main'
            // variant. The actual files that Lint will run on is both of these providers
            // combined - so we can just add the dependencies to the first we see.
            val sourceProvider = sourceProviders.get().firstOrNull() ?: return
            dependencies.forEach { sourceSet ->
                sourceProvider.javaDirectories.withChangesAllowed {
                    from(sourceSet.kotlin.sourceDirectories)
                }
            }
        }

        // Lint for libraries is split into two tasks - analysis, and reporting. We need to
        // add the new sources to both, so all parts of the pipeline are aware.
        project.tasks.withType<AndroidLintAnalysisTask>().configureEach {
            it.variantInputs.addSourceSets()
        }

        project.tasks.withType<AndroidLintTask>().configureEach {
            it.variantInputs.addSourceSets()
        }

        // Also configure the model writing task, so that we don't run into mismatches between
        // analyzed sources in one module and a downstream module
        project.tasks.withType<LintModelWriterTask>().configureEach {
            it.variantInputs.addSourceSets()
        }
    }
}

/**
 * Lint uses [ConfigurableFileCollection.disallowChanges] during initialization, which prevents
 * modifying the file collection separately (there is no time to configure it before AGP has
 * initialized and disallowed changes). This uses reflection to temporarily allow changes, and
 * apply [block].
 */
private fun ConfigurableFileCollection.withChangesAllowed(
    block: ConfigurableFileCollection.() -> Unit
) {
    val disallowChanges = this::class.java.getDeclaredField("disallowChanges")
    disallowChanges.isAccessible = true
    disallowChanges.set(this, false)
    block()
    disallowChanges.set(this, true)
}
