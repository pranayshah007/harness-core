/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.eventsframework.consumer.Message;
import io.harness.freeze.service.FreezeCRUDService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Singleton
public class FreezeEventListener extends AbstractEntityCleanupListener {
  private final FreezeCRUDService freezeCRUDService;

  @Inject
  public FreezeEventListener(FreezeCRUDService freezeCRUDService) {
    this.freezeCRUDService = freezeCRUDService;
  }

  @Override
  public boolean handleMessage(Message message) {
    return super.cleanupAllScopes(message);
  }

  @Override
  public boolean processDeleteEvent(Scope scope) {
    try {
      freezeCRUDService.deleteByScope(scope);
    } catch (Exception e) {
      log.error("Could not process scope delete event for freeze configs. Exception", e);
      return false;
    }
    return true;
  }
}
