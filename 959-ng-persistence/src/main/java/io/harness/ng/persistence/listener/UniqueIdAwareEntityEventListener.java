/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.persistence.listener;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.persistence.UniqueIdAware;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;

@OwnedBy(PL)
public class UniqueIdAwareEntityEventListener extends AbstractMongoEventListener<UniqueIdAware> {
  @Override
  public void onBeforeConvert(@NotNull BeforeConvertEvent<UniqueIdAware> event) {
    super.onBeforeConvert(event);
    UniqueIdAware entity = event.getSource();
    if (isEmpty(entity.getUniqueId())) {
      entity.setUniqueId(UUIDGenerator.generateUuid());
    }
  }
}
