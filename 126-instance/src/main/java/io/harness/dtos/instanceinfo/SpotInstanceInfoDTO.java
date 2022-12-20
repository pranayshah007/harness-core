/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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
public class SpotInstanceInfoDTO extends InstanceInfoDTO {
  @NotNull private String infrastructureKey;
  @NotNull private String ec2InstanceId;
  @NotNull private String elastigroupId;

  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder().clazz(getClass()).part(infrastructureKey).part(ec2InstanceId).build().toString();
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(infrastructureKey).build().toString();
  }

  @Override
  public String getPodName() {
    return StringUtils.EMPTY;
  }

  @Override
  public String getType() {
    return "Spot";
  }
}
