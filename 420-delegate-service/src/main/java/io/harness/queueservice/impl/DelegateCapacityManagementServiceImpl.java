/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.queueservice.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateCapacity;
import io.harness.persistence.HPersistence;
import io.harness.queueservice.infc.DelegateCapacityManagementService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateCapacityManagementServiceImpl implements DelegateCapacityManagementService {
  @Inject private HPersistence persistence;

  @Override
  public DelegateCapacity getDelegateCapacity(String delegateId, String accountId) {
    Query<Delegate> query = persistence.createQuery(Delegate.class)
                                .filter(DelegateKeys.accountId, accountId)
                                .filter(DelegateKeys.uuid, delegateId);
    return query.get() != null ? query.get().getDelegateCapacity() : null;
  }

  @Override
  public void registerDelegateCapacity(
      String accountId, String delegateId, @NotNull DelegateCapacity delegateCapacity) {
    Query<Delegate> query = persistence.createQuery(Delegate.class)
                                .filter(DelegateKeys.accountId, accountId)
                                .filter(DelegateKeys.uuid, delegateId);
    UpdateOperations<Delegate> update =
        persistence.createUpdateOperations(Delegate.class).set(DelegateKeys.delegateCapacity, delegateCapacity);
    persistence.update(query, update);
  }
}
