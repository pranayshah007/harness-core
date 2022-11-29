/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.KubernetesChangeSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;

public class KubernetesChangeSourceSpecTransformer
    extends ChangeSourceSpecTransformer<KubernetesChangeSource, KubernetesChangeSourceSpec> {
  @Override
  public KubernetesChangeSource getEntity(
      MonitoredServiceParams monitoredServiceParams, ChangeSourceDTO changeSourceDTO) {
    KubernetesChangeSourceSpec k8ChangeSourceSpec = (KubernetesChangeSourceSpec) changeSourceDTO.getSpec();
    return KubernetesChangeSource.builder()
        .accountId(monitoredServiceParams.getAccountIdentifier())
        .orgIdentifier(monitoredServiceParams.getOrgIdentifier())
        .projectIdentifier(monitoredServiceParams.getProjectIdentifier())
        .monitoredServiceIdentifier(monitoredServiceParams.getMonitoredServiceIdentifier())
        .identifier(changeSourceDTO.getIdentifier())
        .name(changeSourceDTO.getName())
        .enabled(changeSourceDTO.isEnabled())
        .type(ChangeSourceType.KUBERNETES)
        .dataCollectionRequired(true)
        .connectorIdentifier(k8ChangeSourceSpec.getConnectorRef())
        .build();
  }

  @Override
  protected KubernetesChangeSourceSpec getSpec(KubernetesChangeSource changeSource) {
    return KubernetesChangeSourceSpec.builder().connectorRef(changeSource.getConnectorIdentifier()).build();
  }
}
