/*
 * Copyright 2022 Harness Inc. All rights reserved.
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

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class TasInstanceInfoDTO extends InstanceInfoDTO {
  @NotNull private String id;
  @NotNull private String organization;
  @NotNull private String space;
  @NotNull private String tasApplicationName;
  private String tasApplicationGuid;
  private String instanceIndex;
  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder().clazz(TasInstanceInfoDTO.class).part(id).build().toString();
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(tasApplicationName).build().toString();
  }

  @Override
  public String getPodName() {
    return id;
  }

  @Override
  public String getType() {
    return "Tas";
  }
}
