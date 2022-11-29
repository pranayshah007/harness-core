/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeSource;

import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.ChangeSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.changeSource.ChangeSource;

public abstract class ChangeSourceSpecTransformer<E extends ChangeSource, S extends ChangeSourceSpec> {
  public abstract E getEntity(MonitoredServiceParams monitoredServiceParams, ChangeSourceDTO changeSourceDTO);

  public final ChangeSourceDTO getDTO(E changeSource) {
    return ChangeSourceDTO.builder()
        .spec(getSpec(changeSource))
        .enabled(changeSource.isEnabled())
        .name(changeSource.getName())
        .identifier(changeSource.getIdentifier())
        .type(changeSource.getType())
        .build();
  }

  protected abstract S getSpec(E changeSource);
}
