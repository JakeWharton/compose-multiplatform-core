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

package androidx.work.testing;

import static androidx.work.testing.TestWorkManagerImplKt.createTestWorkManagerImpl;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Configuration;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.utils.SerialExecutorImpl;
import androidx.work.impl.utils.taskexecutor.SerialExecutor;


/**
 * Helps initialize {@link androidx.work.WorkManager} for testing.
 */
public final class WorkManagerTestInitHelper {
    /**
     * Initializes a test {@link androidx.work.WorkManager} with a {@link SynchronousExecutor}.
     *
     * @param context The application {@link Context}
     */
    public static void initializeTestWorkManager(@NonNull Context context) {
        SynchronousExecutor synchronousExecutor = new SynchronousExecutor();
        Configuration configuration = new Configuration.Builder()
                .setExecutor(synchronousExecutor)
                .setTaskExecutor(synchronousExecutor)
                .build();
        initializeTestWorkManager(context, configuration);
    }

    /**
     * Initializes a test {@link androidx.work.WorkManager} with a user-specified
     * {@link androidx.work.Configuration}, but using
     * {@link SynchronousExecutor} instead of main thread.
     *
     * @param context The application {@link Context}
     * @param configuration The {@link androidx.work.Configuration}
     */
    public static void initializeTestWorkManager(
            @NonNull Context context,
            @NonNull Configuration configuration) {

        // Check if the configuration being used has overridden the task executor. If not,
        // swap to SynchronousExecutor. This is to preserve existing behavior.
        SerialExecutor serialExecutor;
        if (configuration.isUsingDefaultTaskExecutor()) {
            Configuration.Builder builder = new Configuration.Builder(configuration)
                    .setTaskExecutor(new SynchronousExecutor());
            configuration = builder.build();
            serialExecutor = new SynchronousSerialExecutor();
        } else {
            serialExecutor = new SerialExecutorImpl(configuration.getTaskExecutor());
        }

        WorkManagerImpl.setDelegate(
                createTestWorkManagerImpl(context, configuration, serialExecutor)
        );
    }

    /**
     * Initializes a test {@link androidx.work.WorkManager} with a default configuration and
     * real threading unlike {@link #initializeTestWorkManager(Context)} that uses a
     * {@link SynchronousExecutor} as main thread and both executors
     * (see {@link Configuration#getTaskExecutor()} and {@link Configuration#getExecutor()}).
     *
     * @param context The application {@link Context}
     */
    public static void initializeTestWorkManagerWithRealExecutors(@NonNull Context context) {
        Configuration configuration = new Configuration.Builder().build();
        WorkManagerImpl.setDelegate(createTestWorkManagerImpl(context, configuration));
    }

    /**
     * Initializes a test {@link androidx.work.WorkManager} with a default configuration and
     * real threading unlike {@link #initializeTestWorkManager(Context, Configuration)} that uses a
     * {@link SynchronousExecutor} as main thread.
     *
     * @param context The application {@link Context}
     * @param configuration The {@link androidx.work.Configuration}
     */
    public static void initializeTestWorkManagerWithRealExecutors(
            @NonNull Context context, @NonNull Configuration configuration) {
        WorkManagerImpl.setDelegate(createTestWorkManagerImpl(context, configuration));
    }

    /**
     * @return An instance of {@link TestDriver}. This exposes additional functionality that is
     * useful in the context of testing when using WorkManager.
     * @deprecated Call {@link WorkManagerTestInitHelper#getTestDriver(Context)} instead.
     */
    @Deprecated
    public static @Nullable TestDriver getTestDriver() {
        WorkManagerImpl workManager = WorkManagerImpl.getInstance();
        if (workManager == null) {
            return null;
        } else {
            return TestWorkManagerImplKt.getTestDriver(workManager);
        }
    }

    /**
     * @return An instance of {@link TestDriver}. This exposes additional functionality that is
     * useful in the context of testing when using WorkManager.
     */
    public static @Nullable TestDriver getTestDriver(@NonNull Context context) {
        try {
            return TestWorkManagerImplKt.getTestDriver(WorkManagerImpl.getInstance(context));
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private WorkManagerTestInitHelper() {
    }
}
