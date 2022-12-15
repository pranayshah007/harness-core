/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.request;

import static io.harness.delegate.task.azure.arm.AzureARMTaskType.BLUEPRINT_DEPLOYMENT;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.azure.arm.AzureARMTaskParameters;
import io.harness.expression.Expression;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
public class AzureBlueprintDeploymentParameters extends AzureARMTaskParameters {
  @Expression(ALLOW_SECRETS) private String blueprintJson;
  @Expression(ALLOW_SECRETS) private Map<String, String> artifacts;
  @Expression(ALLOW_SECRETS) private String assignmentJson;
  private final String assignmentName;

  @Builder
  public AzureBlueprintDeploymentParameters(String appId, String accountId, String activityId, String blueprintJson,
      Map<String, String> artifacts, String assignmentJson, String assignmentName, String commandName,
      Integer timeoutIntervalInMin) {
    super(appId, accountId, activityId, null, commandName, timeoutIntervalInMin, BLUEPRINT_DEPLOYMENT);
    this.blueprintJson = blueprintJson;
    this.artifacts = artifacts;
    this.assignmentJson = assignmentJson;
    this.assignmentName = assignmentName;
  }
}
