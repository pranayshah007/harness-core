/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.events;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.core.event.AbstractEntityCleanupListener;
import io.harness.ng.core.invites.api.InviteService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class InviteEventListener extends AbstractEntityCleanupListener {
  private final InviteService inviteService;

  @Inject
  public InviteEventListener(InviteService inviteService) {
    this.inviteService = inviteService;
  }

  @Override
  public boolean handleMessage(Message message) {
    return super.cleanupAllScopes(message);
  }

  @Override
  public boolean processDeleteEvent(Scope scope) {
    try {
      inviteService.deleteAtAllScopes(scope);
    } catch (Exception e) {
      log.error("Could not process scope delete event for invites. Exception", e);
      return false;
    }
    return true;
  }
}
