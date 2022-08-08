/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
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
@Builder(buildMethodName = "unsafeBuild")
@FieldNameConstants(innerTypeName = "SLOHealthIndicatorKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "sloHealthIndicators", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
@StoreIn(DbAliases.CVNG)
public class SLOHealthIndicator implements PersistentEntity, UuidAware, UpdatedAtAware, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_query_idx")
                 .unique(true)
                 .field(SLOHealthIndicatorKeys.accountId)
                 .field(SLOHealthIndicatorKeys.orgIdentifier)
                 .field(SLOHealthIndicatorKeys.projectIdentifier)
                 .field(SLOHealthIndicatorKeys.serviceLevelObjectiveIdentifier)
                 .build())
        .build();
  }

  @Id private String uuid;
  private long lastUpdatedAt;
  private long createdAt;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String serviceLevelObjectiveIdentifier;
  String monitoredServiceIdentifier;
  double errorBudgetRemainingPercentage;
  int errorBudgetRemainingMinutes;
  double errorBudgetBurnRate;
  ErrorBudgetRisk errorBudgetRisk;
  Instant lastComputedAt;

  public static class SLOHealthIndicatorBuilder {
    public SLOHealthIndicator build() {
      SLOHealthIndicator sloHealthIndicator = unsafeBuild();
      sloHealthIndicator.setErrorBudgetRisk(ErrorBudgetRisk.getFromPercentage(errorBudgetRemainingPercentage));
      sloHealthIndicator.setLastComputedAt(Instant.now());
      return sloHealthIndicator;
    }
  }
}
