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
public class PdcInstanceInfoDTO extends InstanceInfoDTO {
  @NotNull private String host;

  @NotNull private String serviceType;
  private String infrastructureKey;

  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder()
        .clazz(PdcInstanceInfoDTO.class)
        .part(host)
        .part(infrastructureKey)
        .build()
        .toString();
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(host).part(infrastructureKey).build().toString();
  }

  @Override
  public String getPodName() {
    return StringUtils.EMPTY;
  }

  @Override
  public String getType() {
    return "Pdc";
  }
}
