/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.HarnessCDChangeSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.changeSource.HarnessCDChangeSource;

public class HarnessCDChangeSourceSpecTransformer
    extends ChangeSourceSpecTransformer<HarnessCDChangeSource, HarnessCDChangeSourceSpec> {
  @Override
  public HarnessCDChangeSource getEntity(
      MonitoredServiceParams monitoredServiceParams, ChangeSourceDTO changeSourceDTO) {
    return HarnessCDChangeSource.builder()
        .accountId(monitoredServiceParams.getAccountIdentifier())
        .orgIdentifier(monitoredServiceParams.getOrgIdentifier())
        .projectIdentifier(monitoredServiceParams.getProjectIdentifier())
        .monitoredServiceIdentifier(monitoredServiceParams.getMonitoredServiceIdentifier())
        .identifier(changeSourceDTO.getIdentifier())
        .name(changeSourceDTO.getName())
        .enabled(changeSourceDTO.isEnabled())
        .type(ChangeSourceType.HARNESS_CD)
        .build();
  }

  @Override
  protected HarnessCDChangeSourceSpec getSpec(HarnessCDChangeSource changeSource) {
    return new HarnessCDChangeSourceSpec();
  }
}
