/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh.rollback.copyconfig;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.rollback.RollbackDeploymentInfoKey;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CopyConfigUnitRollbackDeploymentInfoKey implements RollbackDeploymentInfoKey {
  @NotNull String serviceId;
  @NotNull String hostname;
  @NotNull String hostConfigDst;

  @Override
  public String getKey() {
    return format("%s_%s_%s", serviceId, hostname, hostConfigDst);
  }
}
