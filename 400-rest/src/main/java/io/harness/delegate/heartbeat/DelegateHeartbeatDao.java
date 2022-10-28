/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Date;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
@OwnedBy(DEL)
public class DelegateHeartbeatDao {
  @Inject private HPersistence persistence;
  @Inject private DelegateCache delegateCache;

  public void updateDelegateWithHeartbeatTime(@NotNull final String accountId, @NotNull final String delegateId,
      @NotNull final long lastHeartbeatTimestamp, @NotNull final Date validUntil) {
    final UpdateOperations<Delegate> updateOperations = persistence.createUpdateOperations(Delegate.class);
    setUnset(updateOperations, DelegateKeys.lastHeartBeat, lastHeartbeatTimestamp);
    setUnset(updateOperations, DelegateKeys.validUntil, validUntil);
    persistence.update(persistence.createQuery(Delegate.class)
                           .filter(DelegateKeys.accountId, accountId)
                           .filter(DelegateKeys.uuid, delegateId),
        updateOperations);
    // TODO: unify cache and DB operation
    delegateCache.get(accountId, delegateId, true);
  }
}
