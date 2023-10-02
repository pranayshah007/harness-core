/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.aggregator.ACLEventProcessingConstants.CREATE_ACTION;
import static io.harness.aggregator.ACLEventProcessingConstants.DELETE_ACTION;
import static io.harness.aggregator.ACLEventProcessingConstants.UPDATE_ACTION;

public interface AccessControlChangeConsumer<T extends AccessControlChangeEventData> {
  default void consumeEvent(String eventType, String id, T changeEventData) {
    switch (eventType) {
      case CREATE_ACTION:
        consumeCreateEvent(id, changeEventData);
        break;
      case UPDATE_ACTION:
        consumeUpdateEvent(id, changeEventData);
        break;
      case DELETE_ACTION:
        consumeDeleteEvent(id);
        break;
      default:
        break;
    }
  }

  boolean consumeUpdateEvent(String id, T changeEventData);

  boolean consumeDeleteEvent(String id);

  boolean consumeCreateEvent(String id, T changeEventData);
}
