/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ci;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@Value
@Builder
public class DliteVmInfraInfo implements InfraInfo {
  @NotNull @Getter private String poolId;
  @Builder.Default @NotNull private Type type = Type.DLITE_VM;
  private String stageRuntimeId;
  private String harnessImageConnectorRef;

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public String getharnessImageConnectorRef() {
    return harnessImageConnectorRef;
  }

  @Override
  public String fetchCapabilityBasis() {
    return String.format("%s", stageRuntimeId);
  }
}