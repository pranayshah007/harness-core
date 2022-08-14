/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ci;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.beans.executioncapability.CIVmConnectionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;

import java.util.Collections;
import java.util.List;
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

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public String getharnessImageConnectorRef() {
    return "";
  }

  @Override
  public List<ExecutionCapability> fetchExecutionCapabilities() {
    return Collections.singletonList(CIVmConnectionCapability.builder()
                                         .stageRuntimeId(stageRuntimeId)
                                         .infraInfo(VmInfraInfo.builder().stageRuntimeId(stageRuntimeId).build())
                                         .build());
  }

  @Override
  public String fetchCapabilityBasis() {
    return String.format("%s", stageRuntimeId);
  }
}