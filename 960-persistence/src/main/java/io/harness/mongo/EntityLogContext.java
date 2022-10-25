/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EntityLogContext extends AutoLogContext {
  public static final String ENTITY_CLASS = "entityClass";

  public EntityLogContext(PersistentEntity entity, OverrideBehavior behavior) {
    super(NullSafeImmutableMap.<String, String>builder()
              .put(ENTITY_CLASS, entity.getClass().getName())
              .putIfNotNull(entity instanceof UuidAccess ? ((UuidAccess) entity).logKeyForId() : "",
                  entity instanceof UuidAccess ? ((UuidAccess) entity).getUuid() : null)
              .putIfNotNull(AccountLogContext.ID,
                  entity instanceof AccountAccess ? ((AccountAccess) entity).getAccountId() : null)
              .build(),
        behavior);
  }
}
