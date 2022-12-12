package io.harness.queueservice.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateCapacity;
import io.harness.persistence.HPersistence;
import io.harness.queueservice.infc.DelegateCapacityManagementService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateCapacityManagementServiceImpl implements DelegateCapacityManagementService {
  @Inject private HPersistence persistence;

  @Override
  public DelegateCapacity getDelegateCapacity(String delegateId, String accountId) {
    Query<Delegate> query = persistence.createQuery(Delegate.class)
                                .filter(DelegateKeys.accountId, accountId)
                                .filter(DelegateKeys.uuid, delegateId);
    return getDelegateCapacity(query.get());
  }

  @Override
  public DelegateCapacity getDelegateCapacity(Delegate delegate) {
    return delegate.getDelegateCapacity();
  }

  @Override
  public void registerDelegateCapacity(String accountId, String delegateId, DelegateCapacity delegateCapacity) {
    Query<Delegate> query = persistence.createQuery(Delegate.class)
                                .filter(DelegateKeys.accountId, accountId)
                                .filter(DelegateKeys.uuid, delegateId);
    UpdateOperations<Delegate> update =
        persistence.createUpdateOperations(Delegate.class).set(DelegateKeys.delegateCapacity, delegateCapacity);
    persistence.update(query, update);
  }

  @Override
  public String getDefaultCapacityForTaskGroup(TaskType taskType) {
    // tbd
    return null;
  }

  @Override
  public boolean hasCapacity(Delegate delegate) {
    return delegate.getDelegateCapacity() != null;
  }
}
