/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor;

import static org.joor.Reflect.on;

import com.google.inject.Inject;
import com.google.inject.Injector;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.executor.taskloader.TaskPackageReader;
import io.harness.delegate.executor.config.Configuration;
import io.harness.delegate.executor.response.ResponseSender;
import io.harness.delegate.task.common.DelegateRunnableTask;
import io.harness.delegate.taskagent.client.delegate.DelegateCoreClientFactory;
import io.harness.delegate.taskagent.servicediscovery.ServiceDiscovery;

import io.harness.delegate.taskagent.servicediscovery.ServiceEndpoint;
import io.harness.security.TokenGenerator;
import software.wings.beans.TaskType;

import com.google.inject.Singleton;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.DEL)
public class TaskFactory {
  private TaskPackageReader taskPackageReader;
  @Inject private Injector injector;

  public DelegateRunnableTask getDelegateRunnableTask(
      final Map<TaskType, Class<? extends DelegateRunnableTask>> classMap,
      DelegateTaskPackage delegateTaskPackage,
      Configuration configuration) {
    DelegateRunnableTask delegateRunnableTask = on(classMap.get(TaskType.valueOf(delegateTaskPackage.getData().getTaskType())))
            .create(delegateTaskPackage,
                /* TBD add stream logger */ null,
                getPostExecutionFunction(
                    delegateTaskPackage.getAccountId(),
                    delegateTaskPackage.getDelegateTaskId(),
                    configuration),
                getPreExecutor())
            .get();
    injector.injectMembers(delegateRunnableTask);
    return delegateRunnableTask;
  }

  private Consumer<DelegateTaskResponse> getPostExecutionFunction(
      final String accountId, final String taskId,  Configuration configuration) {

    return taskResponse -> {
      if (!configuration.isShouldSendResponse()) {
        return;
      }

      final var tokenGenerator = new TokenGenerator(accountId, configuration.getDelegateToken());
      var delegateCoreClient =
          (new DelegateCoreClientFactory(tokenGenerator)).createDelegateCoreClient(
              new ServiceEndpoint(configuration.getDelegateHost(), configuration.getDelegatePort()));

      (new ResponseSender(delegateCoreClient)).sendResponse(accountId, taskId, taskResponse);
    };
  }

  private BooleanSupplier getPreExecutor() {
    return () -> true;
  }
}
