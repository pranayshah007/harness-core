/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.events;

import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class EntityCrudEventListener extends EventListener {
  @Inject
  public EntityCrudEventListener(
      @Named(ENTITY_CRUD) Consumer redisConsumer, @Named(ENTITY_CRUD) Set<EventConsumer> eventConsumers) {
    super(redisConsumer, eventConsumers);
  }

  @Override
  public String getListenerName() {
    return ENTITY_CRUD;
  }
}
