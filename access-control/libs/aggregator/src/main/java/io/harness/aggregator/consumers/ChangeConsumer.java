/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import io.harness.accesscontrol.AccessControlEntity;
import io.harness.aggregator.OpType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PL)
public interface ChangeConsumer<T extends AccessControlEntity> {
  boolean consumeUpdateEvent(String id, T updatedEntity);

  boolean consumeDeleteEvent(String id);

  boolean consumeCreateEvent(String id, T createdEntity);

  default boolean consumeEvent(OpType opType, String id, T entity) {
    boolean result = true;
    switch (opType) {
      case SNAPSHOT:
      case CREATE:
        result = consumeCreateEvent(id, entity);
        break;
      case UPDATE:
        result = consumeUpdateEvent(id, entity);
        break;
      case DELETE:
        result = consumeDeleteEvent(id);
        break;
      default:
        break;
    }
    return result;
  }
}
