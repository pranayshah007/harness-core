/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;

import software.wings.beans.EntityType;

public class EntityTypeLogContext extends AutoLogContext {
  public static final String ENTITY_TYPE = "entityType";
  public static final String ENTITY_ID = "entityId";

  public EntityTypeLogContext(EntityType entityType, String entityId, String accountId, OverrideBehavior behavior) {
    super(NullSafeImmutableMap.<String, String>builder()
              .put(ENTITY_TYPE, entityType.name())
              .putIfNotNull(ENTITY_ID, entityId)
              .putIfNotNull(AccountLogContext.ID, accountId)
              .build(),
        behavior);
  }
}
