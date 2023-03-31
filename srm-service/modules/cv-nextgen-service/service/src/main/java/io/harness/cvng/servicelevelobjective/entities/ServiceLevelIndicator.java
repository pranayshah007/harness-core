/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import static io.harness.cvng.CVConstants.DATA_COLLECTION_TIME_RANGE_FOR_SLI;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "ServiceLevelIndicatorKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "serviceLevelIndicators")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public abstract class ServiceLevelIndicator
    implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware, PersistentRegularIterable {
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  @Id private String uuid;
  String identifier;
  String name;
  private long lastUpdatedAt;
  private long createdAt;
  private String healthSourceIdentifier;
  private String monitoredServiceIdentifier;
  private boolean enabled;
  private SLIEvaluationType sliEvaluationType;
  private SLIMetricType sliMetricType;
  private SLIMissingDataType sliMissingDataType;
  private int version;
  public Map<String, String> getVerificationTaskTags() {
    Map<String, String> tags = new HashMap<>();
    tags.put("sliIdentifier", identifier);
    tags.put("healthSourceIdentifier", healthSourceIdentifier);
    tags.put("monitoredServiceIdentifier", monitoredServiceIdentifier);
    return tags;
  }

  public static String getEvaluationAndMetricType(SLIEvaluationType sliEvaluationType, SLIMetricType sliMetricType) {
    if (sliMetricType != null) {
      return sliEvaluationType.name() + "_" + sliMetricType.name();
    }
    return sliEvaluationType.name();
  }

  public abstract SLIMetricType getSLIMetricType();

  public abstract SLIEvaluationType getSLIEvaluationType();

  public abstract List<String> getMetricNames();

  public abstract Integer getConsiderConsecutiveMinutes();

  public abstract Boolean getConsiderAllConsecutiveMinutesFromStartAsBad();

  public abstract boolean isUpdatable(ServiceLevelIndicator serviceLevelIndicator);

  public abstract boolean shouldReAnalysis(ServiceLevelIndicator serviceLevelIndicator);

  public boolean shouldRecalculateReferencedCompositeSLOs(ServiceLevelIndicator serviceLevelIndicator) {
    try {
      if (this.shouldReAnalysis(serviceLevelIndicator)) {
        return true;
      }
      if (serviceLevelIndicator.getSLIEvaluationType() == SLIEvaluationType.WINDOW) {
        Preconditions.checkArgument(this.getSliMissingDataType().equals(serviceLevelIndicator.getSliMissingDataType()));
      }
      return false;
    } catch (IllegalArgumentException ex) {
      return true;
    }
  }

  protected boolean isCoreUpdatable(ServiceLevelIndicator serviceLevelIndicator) {
    try {
      Preconditions.checkNotNull(serviceLevelIndicator);
      Preconditions.checkArgument(
          this.getHealthSourceIdentifier().equals(serviceLevelIndicator.getHealthSourceIdentifier()));
      Preconditions.checkArgument(
          this.getMonitoredServiceIdentifier().equals(serviceLevelIndicator.getMonitoredServiceIdentifier()));
      Preconditions.checkArgument(this.getSLIMetricType() == serviceLevelIndicator.getSLIMetricType());
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  public abstract static class ServiceLevelIndicatorUpdatableEntity<T extends ServiceLevelIndicator, D
                                                                        extends ServiceLevelIndicator>
      implements UpdatableEntity<T, D> {
    protected void setCommonOperations(UpdateOperations<T> updateOperations, D serviceLevelIndicator) {}
  }
  @FdIndex Long createNextTaskIteration;
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_query_idx")
                 .unique(true)
                 .field(ServiceLevelIndicatorKeys.accountId)
                 .field(ServiceLevelIndicatorKeys.orgIdentifier)
                 .field(ServiceLevelIndicatorKeys.projectIdentifier)
                 .field(ServiceLevelIndicatorKeys.identifier)
                 .build())
        .build();
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (fieldName.equals(ServiceLevelIndicatorKeys.createNextTaskIteration)) {
      this.createNextTaskIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (fieldName.equals(ServiceLevelIndicatorKeys.createNextTaskIteration)) {
      return createNextTaskIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public TimeRange getFirstTimeDataCollectionTimeRange() {
    Instant startTime = Instant.ofEpochMilli(getCreatedAt());
    Instant endTime = DateTimeUtils.roundDownTo5MinBoundary(startTime);
    return TimeRange.builder().startTime(endTime.minus(DATA_COLLECTION_TIME_RANGE_FOR_SLI)).endTime(endTime).build();
  }
}
