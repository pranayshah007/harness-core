/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.event;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.eventsframework.consumer.Message;
import io.harness.gitsync.core.service.GitCommitService;
import io.harness.ng.core.event.AbstractEntityCleanupListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class GitCommitEventListener extends AbstractEntityCleanupListener {
  private final GitCommitService gitCommitService;
  @Inject
  public GitCommitEventListener(GitCommitService gitCommitService) {
    this.gitCommitService = gitCommitService;
  }

  @Override
  public boolean handleMessage(Message message) {
    return super.cleanupAccountScope(message);
  }
  @Override
  public boolean processDeleteEvent(Scope scope) {
    try {
      gitCommitService.deleteByAccount(scope.getAccountIdentifier());
    } catch (Exception e) {
      log.error("Could not process scope delete event for user group. Exception", e);
      return false;
    }
    return true;
  }
}
