/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.util.InstanceSyncKey;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class AsgInstanceInfoDTO extends InstanceInfoDTO {
  @NotNull private String region;
  @NotNull private String infrastructureKey;
  @NotNull private String asgNameWithoutSuffix;
  @NotNull private String asgName;
  private String instanceId;
  private String executionStrategy;
  private Boolean production;

  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder()
        .part(infrastructureKey)
        .part(asgNameWithoutSuffix)
        .part(executionStrategy)
        .part(production)
        .part(instanceId)
        .build()
        .toString();
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder()
        .part(infrastructureKey)
        .part(asgNameWithoutSuffix)
        .part(executionStrategy)
        .build()
        .toString();
  }

  @Override
  public String getPodName() {
    return StringUtils.EMPTY;
  }

  @Override
  public String getType() {
    return InfrastructureKind.ASG;
  }
}
