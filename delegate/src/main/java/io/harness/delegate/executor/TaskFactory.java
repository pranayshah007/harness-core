/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor;

import static org.joor.Reflect.on;

import com.google.inject.Injector;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.executor.common.DelegateTokenUtils;
import io.harness.delegate.executor.config.Configuration;
import io.harness.delegate.task.common.DelegateRunnableTask;
import io.harness.delegate.taskagent.client.delegate.DelegateCoreClient;
import io.harness.delegate.taskagent.client.delegate.DelegateCoreClientFactory;

import io.harness.delegate.taskagent.servicediscovery.ServiceEndpoint;
import io.harness.exception.WingsException;
import io.harness.security.TokenGenerator;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.kryo.DelegateKryoConverterFactory;
import software.wings.beans.TaskType;

import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.DEL)
public class TaskFactory {
  private final TokenGenerator tokenGenerator;
  private final DelegateCoreClient delegateCoreClient;
  private final Configuration configuration;

  public TaskFactory(final String accountId,
                     final Configuration configuration,
                     final KryoSerializer serializer)
  {
    this.configuration = configuration;
    tokenGenerator =
        new TokenGenerator(accountId, DelegateTokenUtils.getDecodedTokenString(configuration.getDelegateToken()));
    DelegateKryoConverterFactory delegateKryoConverterFactory = new DelegateKryoConverterFactory(serializer);
    delegateCoreClient =
        (new DelegateCoreClientFactory(delegateKryoConverterFactory, tokenGenerator)).createDelegateCoreClient(
            new ServiceEndpoint(configuration.getDelegateHost(), configuration.getDelegatePort()));
  }

  public DelegateRunnableTask getDelegateRunnableTask(
      final Map<TaskType, Class<? extends DelegateRunnableTask>> classMap,
      DelegateTaskPackage delegateTaskPackage,
      Injector injector) {
    DelegateRunnableTask delegateRunnableTask = on(classMap.get(TaskType.valueOf(delegateTaskPackage.getData().getTaskType())))
            .create(delegateTaskPackage,
                /* TBD add stream logger */ null,
                getPostExecutionFunction(
                    delegateTaskPackage.getDelegateTaskId(),
                    configuration),
                getPreExecutor())
            .get();
    injector.injectMembers(delegateRunnableTask);
    return delegateRunnableTask;
  }

  public Consumer<DelegateTaskResponse> getPostExecutionFunction(
      final String taskId,  Configuration configuration) {

    return taskResponse -> {
      if (!configuration.isShouldSendResponse()) {
        return;
      }

      try {
        delegateCoreClient.taskResponse(taskId, taskResponse).execute();
      } catch (IOException e) {
        log.error("Send task response failed.", e);
        throw new WingsException(e);
      }
    };
  }

  private BooleanSupplier getPreExecutor() {
    return () -> true;
  }
}
