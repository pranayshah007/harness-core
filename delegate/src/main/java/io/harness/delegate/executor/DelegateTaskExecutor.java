/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.executor.config.Configuration;
import io.harness.delegate.executor.config.ConfigurationProvider;
import io.harness.delegate.executor.shutdownhook.ShutdownHook;
import io.harness.delegate.executor.bundle.BootstrapBundle;
import io.harness.delegate.executor.taskloader.TaskPackageReader;
import io.harness.delegate.task.common.DelegateRunnableTask;
import io.harness.serializer.KryoSerializer;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.TaskType;

import java.util.Map;

import static io.harness.delegate.clienttools.InstallUtils.setupClientTools;

@Slf4j
public abstract class DelegateTaskExecutor {
    public abstract void init(BootstrapBundle taskBundle);

    public void run(final String[] args) {
        BootstrapBundle bundle = new BootstrapBundle();
        init(bundle);
        setupClientTools(DelegateConfiguration.builder().isImmutable(true).clientToolsDownloadDisabled(true).build());
        final Injector injector = Guice.createInjector(bundle);
        Configuration configuration = ConfigurationProvider.getExecutorConfiguration(args.length > 1 ? args[1] : null);

        DelegateTaskPackage delegateTaskPackage =
            TaskPackageReader.readTask(configuration.getTaskInputPath(), injector.getInstance(KryoSerializer.class));
        DelegateRunnableTask runnableTask = injector.getInstance(TaskFactory.class).getDelegateRunnableTask(
            injector.getInstance(Key.get(new TypeLiteral<Map<TaskType, Class<? extends DelegateRunnableTask>>>() {})),
            delegateTaskPackage,
            configuration);
        runnableTask.run();
    }

    public void addShutdownHook(ShutdownHook shutdownHook) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Executor shutting down...");
            shutdownHook.shutdown();
        }));
    }
}
