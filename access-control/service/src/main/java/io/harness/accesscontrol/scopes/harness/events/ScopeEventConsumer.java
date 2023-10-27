/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes.harness.events;

import io.harness.accesscontrol.commons.events.EventConsumer;
import io.harness.accesscontrol.commons.events.EventFilter;
import io.harness.accesscontrol.commons.events.EventHandler;
import io.harness.accesscontrol.scopes.HarnessScopeLevel;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PL)
public class ScopeEventConsumer implements EventConsumer {
  private final ScopeEventFilter eventFilter;
  private final ScopeEventHandler eventHandler;
  public static final Set<String> SCOPE_EVENT_ENTITY_TYPES =
      Arrays.stream(HarnessScopeLevel.values()).map(HarnessScopeLevel::getEventEntityName).collect(Collectors.toSet());

  @Inject
  public ScopeEventConsumer(ScopeEventFilter eventFilter, ScopeEventHandler eventHandler) {
    this.eventFilter = eventFilter;
    this.eventHandler = eventHandler;
  }

  @Override
  public EventFilter getEventFilter() {
    return eventFilter;
  }

  @Override
  public EventHandler getEventHandler() {
    return eventHandler;
  }
}
