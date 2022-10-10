/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner;

import static org.joor.Reflect.on;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.runner.config.Configuration;
import io.harness.delegate.task.DelegateRunnableTask;
import io.harness.delegate.taskagent.client.delegate.DelegateCoreClient;
import io.harness.exception.WingsException;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.DEL)
public class TaskFactory {
  @Inject private Injector injector;
  @Inject private Map<TaskType, Class<? extends DelegateRunnableTask>> classMap;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer kryoSerializer;
  @Inject private Configuration configuration;
  @Inject private DelegateCoreClient delegateCoreClient;

  public DelegateRunnableTask getDelegateRunnableTask(TaskType type, DelegateTaskPackage delegateTaskPackage) {
    DelegateRunnableTask delegateRunnableTask =
        on(classMap.get(type))
            .create(delegateTaskPackage,
                /* TBD add stream logger */ null, getPostExecutionFunction(delegateTaskPackage.getDelegateTaskId()),
                getPreExecutor())
            .get();
    injector.injectMembers(delegateRunnableTask);
    return delegateRunnableTask;
  }

  private Consumer<DelegateTaskResponse> getPostExecutionFunction(final String taskId) {
    return taskResponse -> {
      log.info("debug: writing result to file: {}", configuration.getResultFilePath());
      byte[] data = kryoSerializer.asBytes(taskResponse);
      File output = new File(configuration.getResultFilePath());
      try {
        FileUtils.writeByteArrayToFile(output, data);
      } catch (IOException e) {
        log.error("Writing result to file failed", e);
        throw new WingsException(e);
      }
      delegateCoreClient.taskResponse(taskResponse.getAccountId(), taskId, taskResponse);
    };
  }

  private BooleanSupplier getPreExecutor() {
    return () -> true;
  }
}
