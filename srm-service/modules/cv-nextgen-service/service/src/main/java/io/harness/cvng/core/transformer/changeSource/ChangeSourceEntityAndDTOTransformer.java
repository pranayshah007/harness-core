/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.changeSource.ChangeSource;

import com.google.inject.Inject;
import java.util.Map;

public class ChangeSourceEntityAndDTOTransformer {
  @Inject private Map<ChangeSourceType, ChangeSourceSpecTransformer> changeSourceTypeChangeSourceSpecTransformerMap;

  public ChangeSource getEntity(MonitoredServiceParams monitoredServiceParams, ChangeSourceDTO changeSourceDTO) {
    ChangeSourceSpecTransformer changeSourceSpecTransformer =
        changeSourceTypeChangeSourceSpecTransformerMap.get(changeSourceDTO.getSpec().getType());
    return changeSourceSpecTransformer.getEntity(monitoredServiceParams, changeSourceDTO);
  }

  public ChangeSourceDTO getDto(ChangeSource changeSource) {
    ChangeSourceSpecTransformer changeSourceSpecTransformer =
        changeSourceTypeChangeSourceSpecTransformerMap.get(changeSource.getType());
    return changeSourceSpecTransformer.getDTO(changeSource);
  }
}
