/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.arm.deployment.context;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(CDP)
public class DeploymentContext {
  private String deploymentName;
  private AzureDeploymentMode mode;
  private String templateJson;
  private String parametersJson;
  private AzureLogCallbackProvider logStreamingTaskClient;
  private int steadyStateTimeoutInMin;
  private String runningCommandUnit;
}
