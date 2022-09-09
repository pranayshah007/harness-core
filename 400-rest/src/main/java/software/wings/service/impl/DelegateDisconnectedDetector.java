/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.DelegateConnection.EXPIRY_TIME;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueueController;
import io.harness.service.intfc.DelegateCache;
import io.harness.version.VersionInfoManager;

import software.wings.service.intfc.DelegateService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(DEL)
public class DelegateDisconnectedDetector implements Runnable {
  @Inject private DelegateService delegateService;
  @Inject private QueueController queueController;
  @Inject private HPersistence persistence;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private DelegateCache delegateCache;

  @Override
  public void run() {
    if (queueController.isNotPrimary()) {
      return;
    }

    Query<Delegate> delegateConnectionQuery =
        persistence.createQuery(Delegate.class, excludeAuthority)
            .filter(Delegate.DelegateKeys.disconnected, Boolean.FALSE)
            .field(Delegate.DelegateKeys.lastHeartBeat)
            .lessThanOrEq(currentTimeMillis() - EXPIRY_TIME.toMillis());

    try (HIterator<Delegate> delegateConnections = new HIterator<>(delegateConnectionQuery.fetch())) {
      for (Delegate delegateConnection : delegateConnections) {
        // ??? when do we clean up disconnected deletes. How does version matter before marking it as disconnected?
        if (versionInfoManager.getVersionInfo().getVersion().equals(delegateConnection.getVersion())) {
          disconnectedDetected(delegateConnection.isPolllingModeEnabled(), delegateConnection);
          continue;
        }
      }
    }
  }

  @VisibleForTesting
  public void disconnectedDetected(boolean polllingModeEnabled, Delegate delegateConnection) {
    try (DelegateLogContext ignore = new DelegateLogContext(
             delegateConnection.getAccountId(), delegateConnection.getUuid(), null, OVERRIDE_ERROR)) {
      log.info("Delegate was detected as disconnected");
      if (!polllingModeEnabled) {
        log.error("Non-polling delegate was detected as disconnected");
      }
      delegateService.delegateDisconnected(
          delegateConnection.getAccountId(), delegateConnection.getUuid());
    }
  }
}
