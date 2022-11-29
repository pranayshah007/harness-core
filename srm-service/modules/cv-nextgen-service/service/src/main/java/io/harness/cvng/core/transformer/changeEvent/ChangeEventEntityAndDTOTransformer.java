/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;

import com.google.inject.Inject;
import java.util.Map;

public class ChangeEventEntityAndDTOTransformer {
  @Inject private Map<ChangeSourceType, ChangeEventMetaDataTransformer> changeTypeMetaDataTransformerMap;

  public Activity getEntity(ChangeEventDTO changeEventDTO) {
    ChangeEventMetaDataTransformer changeEventMetaDataTransformer =
        changeTypeMetaDataTransformerMap.get(changeEventDTO.getType());
    return changeEventMetaDataTransformer.getEntity(changeEventDTO);
  }

  public ChangeEventDTO getDto(Activity changeEvent) {
    ChangeEventMetaDataTransformer changeEventMetaDataTransformer =
        changeTypeMetaDataTransformerMap.get(ChangeSourceType.ofActivityType(changeEvent.getType()));
    return changeEventMetaDataTransformer.getDTO(changeEvent);
  }
}
