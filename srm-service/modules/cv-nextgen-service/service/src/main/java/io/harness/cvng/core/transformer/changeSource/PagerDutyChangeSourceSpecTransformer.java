/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.PagerDutyChangeSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource;

@OwnedBy(CV)
public class PagerDutyChangeSourceSpecTransformer
    extends ChangeSourceSpecTransformer<PagerDutyChangeSource, PagerDutyChangeSourceSpec> {
  @Override
  public PagerDutyChangeSource getEntity(
      MonitoredServiceParams monitoredServiceParams, ChangeSourceDTO changeSourceDTO) {
    PagerDutyChangeSourceSpec pagerDutyChangeSourceSpec = (PagerDutyChangeSourceSpec) changeSourceDTO.getSpec();
    return PagerDutyChangeSource.builder()
        .accountId(monitoredServiceParams.getAccountIdentifier())
        .orgIdentifier(monitoredServiceParams.getOrgIdentifier())
        .projectIdentifier(monitoredServiceParams.getProjectIdentifier())
        .monitoredServiceIdentifier(monitoredServiceParams.getMonitoredServiceIdentifier())
        .identifier(changeSourceDTO.getIdentifier())
        .name(changeSourceDTO.getName())
        .enabled(changeSourceDTO.isEnabled())
        .type(ChangeSourceType.PAGER_DUTY)
        .connectorIdentifier(pagerDutyChangeSourceSpec.getConnectorRef())
        .pagerDutyServiceId(pagerDutyChangeSourceSpec.getPagerDutyServiceId())
        .build();
  }

  @Override
  protected PagerDutyChangeSourceSpec getSpec(PagerDutyChangeSource changeSource) {
    return PagerDutyChangeSourceSpec.builder()
        .connectorRef(changeSource.getConnectorIdentifier())
        .pagerDutyServiceId(changeSource.getPagerDutyServiceId())
        .build();
  }
}
