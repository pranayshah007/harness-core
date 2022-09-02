/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Value
@StoreIn(DbAliases.CDC)
@Entity(value = "cdcStateEntity", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "cdcStateEntityKeys")
@OwnedBy(HarnessTeam.CE)
public class CDCStateEntity implements PersistentEntity {
  @Id private String sourceEntityClass;
  private String lastSyncedToken;
}
