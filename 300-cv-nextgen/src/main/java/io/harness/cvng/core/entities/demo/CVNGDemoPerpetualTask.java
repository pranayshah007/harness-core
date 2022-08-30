/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.demo;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "CVNGDemoPerpetualTaskKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "cvngDemoPerpetualTasks")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class CVNGDemoPerpetualTask
    implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware, PersistentRegularIterable {
  @Id private String uuid;
  String accountId;
  String dataCollectionWorkerId;
  long lastUpdatedAt;
  long createdAt;
  @FdIndex private Long createNextTaskIteration;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (fieldName.equals(CVNGDemoPerpetualTaskKeys.createNextTaskIteration)) {
      this.createNextTaskIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (fieldName.equals(CVNGDemoPerpetualTaskKeys.createNextTaskIteration)) {
      return createNextTaskIteration;
    }

    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }
}
