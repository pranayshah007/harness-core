/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos.deploymentinfo;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.helper.K8sCloudConfigMetadata;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.util.InstanceSyncKey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashSet;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
public class K8sDeploymentInfoDTO extends DeploymentInfoDTO {
  @NotNull private LinkedHashSet<String> namespaces;
  @NotNull private String releaseName;
  private String blueGreenStageColor;
  @EqualsAndHashCode.Exclude private K8sCloudConfigMetadata cloudConfigMetadata;

  @Override
  public String getType() {
    return ServiceSpecType.KUBERNETES;
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    if (isNotEmpty(blueGreenStageColor)) {
      return InstanceSyncKey.builder().part(releaseName).part(blueGreenStageColor).build().toString();
    }
    return InstanceSyncKey.builder().part(releaseName).build().toString();
  }
}
