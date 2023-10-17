/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.azure.webapps;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.execution.ExecutionDetails;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
@Data
@Builder
@FieldNameConstants(innerTypeName = "AzureWebAppsStageExecutionDetailsKeys")
@JsonTypeName("AzureWebAppsStageExecutionDetails")
public class AzureWebAppsStageExecutionDetails implements ExecutionDetails {
  String pipelineExecutionId;
  String targetSlot;
  private AzureArtifactConfig artifactConfig;
  private Set<String> userAddedAppSettingNames;
  private Set<String> userAddedConnStringNames;
  private Boolean userChangedStartupCommand;
  private Boolean cleanDeployment;
}
