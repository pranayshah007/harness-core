/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.DX)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class ReferenceInstanceInfoDTO extends InstanceInfoDTO {
  String podName;
  String namespace;
  String releaseName;

  @Override
  public String prepareInstanceKey() {
    return podName;
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return podName;
  }

  @Override
  public String getType() {
    return "Reference";
  }
}
