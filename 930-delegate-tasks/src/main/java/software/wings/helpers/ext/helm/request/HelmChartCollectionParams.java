/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.helm.request;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.helpers.ext.helm.request.ArtifactoryHelmTaskHelper.shouldFetchHelmChartsFromArtifactory;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.CapabilityType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;
import io.harness.expression.ExpressionEvaluator;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDC)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class HelmChartCollectionParams implements ManifestCollectionParams {
  private String accountId;
  private String appId;
  private String appManifestId;
  private String serviceId;
  private HelmChartConfigParams helmChartConfigParams;
  private Set<String> publishedVersions;
  private boolean useRepoFlags;
  private HelmChartCollectionType collectionType;
  private boolean isRegex;

  public enum HelmChartCollectionType { ALL, SPECIFIC_VERSION }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities =
        helmChartConfigParams.fetchRequiredExecutionCapabilities(maskingEvaluator);
    if (shouldFetchHelmChartsFromArtifactory(helmChartConfigParams)) {
      executionCapabilities.removeIf(capability -> CapabilityType.HELM_INSTALL.equals(capability.getCapabilityType()));
    }
    return executionCapabilities;
  }
}
