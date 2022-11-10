/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos.deploymentinfo;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.util.InstanceSyncKey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@OwnedBy(CDP)
public class SpotDeploymentInfoDTO extends DeploymentInfoDTO {
  @NotNull private String infrastructureKey;
  @NotNull private Map<String, Set<String>> elastigroupEc2InstancesMap;

  @Override
  public String getType() {
    return ServiceSpecType.ELASTIGROUP;
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(infrastructureKey).build().toString();
  }
}
