/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.userprofile.event;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.core.event.AbstractEntityCleanupListener;
import io.harness.ng.userprofile.services.api.SourceCodeManagerService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class SourceCodeManagerEventListener extends AbstractEntityCleanupListener {
  private final SourceCodeManagerService sourceCodeManagerService;

  @Inject
  public SourceCodeManagerEventListener(SourceCodeManagerService sourceCodeManagerService) {
    this.sourceCodeManagerService = sourceCodeManagerService;
  }

  @Override
  public boolean handleMessage(Message message) {
    return super.cleanupAccountScope(message);
  }

  @Override
  public boolean processDeleteEvent(Scope scope) {
    try {
      sourceCodeManagerService.deleteByAccount(scope.getAccountIdentifier());
    } catch (Exception e) {
      log.error("Could not process scope delete event for source code manager. Exception", e);
      return false;
    }
    return true;
  }
}
