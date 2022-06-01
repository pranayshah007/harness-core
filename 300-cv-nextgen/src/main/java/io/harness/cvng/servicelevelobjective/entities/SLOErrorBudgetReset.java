/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.Date;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "SLOErrorBudgetResetKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "sloErrorBudgetResets", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
@StoreIn(DbAliases.CVNG)
public class SLOErrorBudgetReset implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("slo_query_idx")
                 .field(SLOErrorBudgetResetKeys.accountId)
                 .field(SLOErrorBudgetResetKeys.orgIdentifier)
                 .field(SLOErrorBudgetResetKeys.projectIdentifier)
                 .field(SLOErrorBudgetResetKeys.serviceLevelObjectiveIdentifier)
                 .build())
        .build();
  }

  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  @Id String uuid;
  String serviceLevelObjectiveIdentifier;
  @Deprecated Double errorBudgetIncrementPercentage;
  Integer errorBudgetIncrementMinutes;
  Integer remainingErrorBudgetAtReset;
  Integer errorBudgetAtReset;
  String reason;

  @FdTtlIndex Date validUntil;

  long lastUpdatedAt;
  long createdAt;

  public Integer getErrorBudgetIncrementMinutes() {
    if (errorBudgetIncrementMinutes != null) {
      return errorBudgetIncrementMinutes;
    }
    return (errorBudgetAtReset * errorBudgetIncrementPercentage.intValue()) / 100;
  }
}
