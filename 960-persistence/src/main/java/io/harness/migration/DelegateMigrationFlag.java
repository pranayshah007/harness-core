/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migration;

import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@FieldNameConstants(innerTypeName = "DelegateMigrationFlagKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "delegateMigrationFlag", noClassnameStored = true)
public class DelegateMigrationFlag implements PersistentEntity {
  @Id private String className;
  private boolean enabled;
}
