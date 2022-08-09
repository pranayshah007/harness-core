/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.DX)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class AzureSshWinrmInstanceInfo extends InstanceInfo {
  @NotNull private String host;
  @NotNull private String serviceType;
  private String infrastructureKey;
}
