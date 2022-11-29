/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.HarnessCDCurrentGenChangeSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource;

public class HarnessCDCurrentGenChangeSourceSpecTransformer
    extends ChangeSourceSpecTransformer<HarnessCDCurrentGenChangeSource, HarnessCDCurrentGenChangeSourceSpec> {
  @Override
  public HarnessCDCurrentGenChangeSource getEntity(
      MonitoredServiceParams monitoredServiceParams, ChangeSourceDTO changeSourceDTO) {
    HarnessCDCurrentGenChangeSourceSpec harnessCDCurrentGenChangeSourceSpec =
        (HarnessCDCurrentGenChangeSourceSpec) changeSourceDTO.getSpec();
    return HarnessCDCurrentGenChangeSource.builder()
        .accountId(monitoredServiceParams.getAccountIdentifier())
        .orgIdentifier(monitoredServiceParams.getOrgIdentifier())
        .projectIdentifier(monitoredServiceParams.getProjectIdentifier())
        .monitoredServiceIdentifier(monitoredServiceParams.getMonitoredServiceIdentifier())
        .identifier(changeSourceDTO.getIdentifier())
        .name(changeSourceDTO.getName())
        .enabled(changeSourceDTO.isEnabled())
        .type(ChangeSourceType.HARNESS_CD_CURRENT_GEN)
        .harnessApplicationId(harnessCDCurrentGenChangeSourceSpec.getHarnessApplicationId())
        .harnessServiceId(harnessCDCurrentGenChangeSourceSpec.getHarnessServiceId())
        .harnessEnvironmentId(harnessCDCurrentGenChangeSourceSpec.getHarnessEnvironmentId())
        .build();
  }

  @Override
  protected HarnessCDCurrentGenChangeSourceSpec getSpec(HarnessCDCurrentGenChangeSource changeSource) {
    return HarnessCDCurrentGenChangeSourceSpec.builder()
        .harnessApplicationId(changeSource.getHarnessApplicationId())
        .harnessServiceId(changeSource.getHarnessServiceId())
        .harnessEnvironmentId(changeSource.getHarnessEnvironmentId())
        .build();
  }
}
