/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities.deploymentinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.helper.K8sCloudConfigMetadata;
import io.harness.k8s.model.HelmVersion;

import java.util.LinkedHashSet;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
public class NativeHelmDeploymentInfo extends DeploymentInfo {
  @NotNull private LinkedHashSet<String> namespaces;
  @NotNull private String releaseName;
  private HelmChartInfo helmChartInfo;
  @NotNull private HelmVersion helmVersion;
  @EqualsAndHashCode.Exclude private K8sCloudConfigMetadata cloudConfigMetadata;
}
