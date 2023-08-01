/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.steps.executables;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.tasks.Task;

import java.util.Map;
import java.util.Optional;

/**
 * Use this interface when your task spawns a task. The queuing of the task will be taken care by the framework itself.
 * For this interface the abort etc. for task will also be handled by the framework
 * <p>
 * InterfaceDefinition:
 * <p>
 * obtainTask: In return we expect a {@link Task}. It can spawn any task which conforms to the defined interface.
 * <p>
 * handleTaskResult : The result of the task will be supplied in the responseDataMap. The key will be the waitId, See
 * {@link Task} for details on this
 */
@OwnedBy(PIPELINE)
public interface TaskExecutable<T extends StepParameters, R extends ResponseData>
    extends Step<T>, Abortable<T, TaskExecutableResponse>, Failable<T>, Expirable<T, TaskExecutableResponse>,
            Progressable<T> {
  default Optional<TaskRequest> obtainTaskOptional(Ambiance ambiance, T stepParameters, StepInputPackage inputPackage) {
    return Optional.ofNullable(obtainTask(ambiance, stepParameters, inputPackage));
  }
  TaskRequest obtainTask(Ambiance ambiance, T stepParameters, StepInputPackage inputPackage);

  StepResponse handleTaskResult(Ambiance ambiance, T stepParameters, ThrowingSupplier<R> responseDataSupplier)
      throws Exception;

  default void handleAbort(Ambiance ambiance, T stepParameters, TaskExecutableResponse executableResponse) {
    // NOOP : By default this is noop as task abortion is handled by the PMS but you are free to override it
  }

  @Override
  default void handleFailureInterrupt(Ambiance ambiance, T stepParameters, Map<String, String> metadata) {
    // NOOP : By default this is noop as task failure is handled by the PMS but you are free to override it
  }

  @Override
  default void handleExpire(Ambiance ambiance, T stepParameters, TaskExecutableResponse executableResponse) {
    // NOOP : By default this is noop as task expire is handled by the PMS but you are free to override it
  }
}
