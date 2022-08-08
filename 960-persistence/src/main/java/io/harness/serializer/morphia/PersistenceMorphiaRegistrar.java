/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.cache.CacheEntity;
import io.harness.cache.SpringCacheEntity;
import io.harness.dataretention.AccountDataRetentionEntity;
import io.harness.iterator.PersistentCronIterable;
import io.harness.iterator.PersistentFibonacciIterable;
import io.harness.iterator.PersistentIrregularIterable;
import io.harness.iterator.PersistentIterable;
import io.harness.iterator.PersistentNGCronIterable;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.migration.MigrationJobInstance;
import io.harness.mongo.MorphiaMove;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAccess;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.NameAccess;
import io.harness.persistence.NameAndValueAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAccess;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAccess;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAccess;
import io.harness.persistence.UuidAware;
import io.harness.persistence.ValidUntilAccess;
import io.harness.persistence.gitaware.GitAware;
import io.harness.queue.Queuable;
import io.harness.queue.WithMonitoring;

import java.util.Set;

public class PersistenceMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(AccountAccess.class);
    set.add(AccountDataRetentionEntity.class);
    set.add(CacheEntity.class);
    set.add(CreatedAtAccess.class);
    set.add(CreatedAtAware.class);
    set.add(CreatedByAccess.class);
    set.add(CreatedByAware.class);
    set.add(GoogleDataStoreAware.class);
    set.add(MigrationJobInstance.class);
    set.add(MorphiaMove.class);
    set.add(NameAccess.class);
    set.add(PersistentCronIterable.class);
    set.add(PersistentEntity.class);
    set.add(PersistentFibonacciIterable.class);
    set.add(PersistentIrregularIterable.class);
    set.add(PersistentIterable.class);
    set.add(PersistentRegularIterable.class);
    set.add(Queuable.class);
    set.add(WithMonitoring.class);
    set.add(SpringCacheEntity.class);
    set.add(UpdatedAtAccess.class);
    set.add(UpdatedAtAware.class);
    set.add(UpdatedByAccess.class);
    set.add(UpdatedByAware.class);
    set.add(UuidAccess.class);
    set.add(UuidAware.class);
    set.add(ValidUntilAccess.class);
    set.add(PersistentNGCronIterable.class);
    set.add(NameAndValueAccess.class);
    set.add(GitAware.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // no classes to register
  }
}
