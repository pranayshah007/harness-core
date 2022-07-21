/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.docker;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.delegate.beans.executioncapability.CIVmConnectionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CIDockerExecuteStepTaskParams implements CIExecuteStepTaskParams, ExecutionCapabilityDemander {
  @NotNull private String ipAddress;
  @NotNull private String poolId;
  @NotNull private String stageRuntimeId;
  @NotNull private String stepRuntimeId;
  @NotNull private String stepId;
  @Expression(ALLOW_SECRETS) @NotNull private VmStepInfo stepInfo;
  @Expression(ALLOW_SECRETS) private List<String> secrets;
  @NotNull private String logKey;
  @NotNull private String workingDir;
  private Map<String, String> volToMountPath;

  @Builder.Default private static final Type type = Type.DOCKER;

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(CIVmConnectionCapability.builder().poolId(poolId).build());
  }
}
