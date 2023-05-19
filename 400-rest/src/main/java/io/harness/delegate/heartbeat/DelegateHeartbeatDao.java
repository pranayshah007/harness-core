/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATE_CACHE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateHeartbeatParams;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.UpdateOperations;
import java.util.Date;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLocalCachedMap;

@Slf4j
@Singleton
@OwnedBy(DEL)
public class DelegateHeartbeatDao {
  @Inject private HPersistence persistence;
  @Inject private DelegateCache delegateCache;
  @Inject @Named("enableRedisForDelegateService") private boolean enableRedisForDelegateService;
  @Inject @Named(DELEGATE_CACHE) RLocalCachedMap<String, Delegate> delegateRedisCache;

  public void updateDelegateWithHeartbeatAndConnectionInfo(@NotNull final String accountId,
      @NotNull final String delegateId, @NotNull final long lastHeartbeatTimestamp, @NotNull final Date validUntil,
      @NotNull final @NotNull DelegateHeartbeatParams params) {
    if (enableRedisForDelegateService) {
      Delegate delegate = delegateCache.get(accountId, delegateId);
      if (delegate == null) {
        delegate = persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegateId).get();
        if (delegate == null) {
          log.warn("Unable to find delegate {} in DB.", delegateId);
          return;
        }
      }
      delegate.setLastHeartBeat(lastHeartbeatTimestamp);
      delegate.setDisconnected(false);
      delegate.setVersion(params.getVersion());
      delegate.setLocation(params.getLocation());
      delegate.setDelegateConnectionId(params.getDelegateConnectionId());
      delegateRedisCache.put(delegateId, delegate);
      return;
    }
    final UpdateOperations<Delegate> updateOperations = persistence.createUpdateOperations(Delegate.class);
    setUnset(updateOperations, DelegateKeys.lastHeartBeat, lastHeartbeatTimestamp);
    setUnset(updateOperations, DelegateKeys.validUntil, validUntil);
    setUnset(updateOperations, DelegateKeys.version, params.getVersion());
    setUnset(updateOperations, DelegateKeys.location, params.getLocation());
    setUnset(updateOperations, DelegateKeys.delegateConnectionId, params.getDelegateConnectionId());
    setUnset(updateOperations, DelegateKeys.disconnected, false);

    persistence.update(persistence.createQuery(Delegate.class)
                           .filter(DelegateKeys.accountId, accountId)
                           .filter(DelegateKeys.uuid, delegateId),
        updateOperations);
    // TODO: unify cache and DB operation
    delegateCache.get(accountId, delegateId, true);
  }
}
