/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.collect.artifacts;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureArtifactsCollectionTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  @NotNull private String accountId;
  @NotNull private AzureArtifactsConfig azureArtifactsConfig;
  @NotNull private List<EncryptedDataDetail> encryptedDataDetails;
  @NotNull private ArtifactStreamAttributes artifactStreamAttributes;
  private Map<String, String> artifactMetadata;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (azureArtifactsConfig instanceof AzureArtifactsPATConfig) {
      return CapabilityHelper.generateCapabilities(
          (AzureArtifactsPATConfig) azureArtifactsConfig, artifactStreamAttributes, maskingEvaluator);
    } else {
      throw new InvalidRequestException("Invalid Azure Artifacts Server config");
    }
  }
}
