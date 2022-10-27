/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.exception.FailureType;

import java.util.EnumSet;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Singular;
import lombok.Value;

/**
 * Describes response of an execution.
 */
@OwnedBy(CDC)
@Value
@Builder(toBuilder = true)
@TargetModule(HarnessModule._957_CG_BEANS)
public class ExecutionResponse {
  private boolean async;
  @Singular private List<String> correlationIds;
  @Default private ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;
  private EnumSet<FailureType> failureTypes;
  private String errorMessage;
  private StateExecutionData stateExecutionData;
  @Singular private List<ContextElement> notifyElements;
  @Singular private List<ContextElement> contextElements;

  @Deprecated
  /**
   * @deprecated {@link software.wings.service.intfc.StateExecutionService#appendDelegateTaskDetails(String,
   *     DelegateTaskDetails)} should be used instead. Check {@link
   *     software.wings.sm.states.ShellScriptState#executeInternal(ExecutionContext, String)} for details. )
   * */
  private String delegateTaskId;

  @Singular private List<StateExecutionInstance> stateExecutionInstances;
}
