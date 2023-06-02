/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder(buildMethodName = "unsafeBuild")
@FieldNameConstants(innerTypeName = "SLOHealthIndicatorKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "sloHealthIndicators", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class SLOHealthIndicator
    implements PersistentEntity, UuidAware, UpdatedAtAware, CreatedAtAware, PersistentRegularIterable {
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
  @FdIndex private long timescaleIteration;

  Boolean failedState;

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (SLOHealthIndicatorKeys.timescaleIteration.equals(fieldName)) {
      return this.timescaleIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (SLOHealthIndicatorKeys.timescaleIteration.equals(fieldName)) {
      this.timescaleIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public static class SLOHealthIndicatorBuilder {
    public SLOHealthIndicator build() {
      SLOHealthIndicator sloHealthIndicator = unsafeBuild();
      sloHealthIndicator.setErrorBudgetRisk(ErrorBudgetRisk.getFromPercentage(errorBudgetRemainingPercentage));
      sloHealthIndicator.setLastComputedAt(Instant.now());
      return sloHealthIndicator;
    }
  }

  public Boolean getFailedState() {
    return Objects.requireNonNullElse(failedState, false);
  }
}
