/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.mongo;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAccess;
import io.harness.persistence.CreatedByAware;
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

import java.util.Set;

public class PersistenceBeansMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(AccountAccess.class);
    set.add(CreatedAtAccess.class);
    set.add(CreatedAtAware.class);
    set.add(CreatedByAccess.class);
    set.add(CreatedByAware.class);
    set.add(NameAccess.class);
    set.add(PersistentEntity.class);
    set.add(UpdatedAtAccess.class);
    set.add(UpdatedAtAware.class);
    set.add(UpdatedByAccess.class);
    set.add(UpdatedByAware.class);
    set.add(UuidAccess.class);
    set.add(UuidAware.class);
    set.add(ValidUntilAccess.class);
    set.add(NameAndValueAccess.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // no classes to register
  }
}
