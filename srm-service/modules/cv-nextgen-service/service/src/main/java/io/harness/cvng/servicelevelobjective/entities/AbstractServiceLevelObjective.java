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
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.notification.beans.NotificationRuleRef;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.data.structure.CollectionUtils;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.query.UpdateOperations;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "ServiceLevelObjectiveV2Keys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "serviceLevelObjectivesV2")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public abstract class AbstractServiceLevelObjective
    implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware, PersistentRegularIterable {
  @NotNull String accountId;
  String orgIdentifier;
  String projectIdentifier;
  @NotNull @Id private String uuid;
  @NotNull String identifier;
  @NotNull String name;
  String desc;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;
  List<String> userJourneyIdentifiers;
  List<NotificationRuleRef> notificationRuleRefs;
  @NotNull ServiceLevelObjective.SLOTarget sloTarget;
  private boolean enabled;
  private long lastUpdatedAt;
  private long createdAt;
  private long startedAt;
  @NotNull private Double sloTargetPercentage;
  @FdIndex private long nextNotificationIteration;
  @FdIndex private long nextVerificationIteration;
  @FdIndex private long createNextTaskIteration;
  @FdIndex private long recordMetricIteration;
  @FdIndex private long sloHistoryTimescaleIteration;
  @NotNull ServiceLevelObjectiveType type;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_query_idx")
                 .unique(true)
                 .field(ServiceLevelObjectiveV2Keys.accountId)
                 .field(ServiceLevelObjectiveV2Keys.orgIdentifier)
                 .field(ServiceLevelObjectiveV2Keys.projectIdentifier)
                 .field(ServiceLevelObjectiveV2Keys.identifier)
                 .build())
        .build();
  }

  public ZoneOffset getZoneOffset() {
    return ZoneOffset.UTC; // hardcoding it to UTC for now. We need to ask it from user.
  }

  public List<NotificationRuleRef> getNotificationRuleRefs() {
    if (notificationRuleRefs == null) {
      return Collections.emptyList();
    }
    return notificationRuleRefs;
  }

  public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
    return sloTarget.getCurrentTimeRange(currentDateTime);
  }

  public List<SLODashboardDetail.TimeRangeFilter> getTimeRangeFilters() {
    return sloTarget.getTimeRangeFilters();
  }

  public int getTotalErrorBudgetMinutes(LocalDateTime currentDateTime) {
    int currentWindowMinutes = getCurrentTimeRange(currentDateTime).totalMinutes();
    Double errorBudgetPercentage = getSloTargetPercentage();
    return (int) Math.round(((100 - errorBudgetPercentage) * currentWindowMinutes) / 100);
  }

  public int getActiveErrorBudgetMinutes(
      List<SLOErrorBudgetResetDTO> sloErrorBudgetResets, LocalDateTime currentDateTime) {
    int totalErrorBudgetMinutes = getTotalErrorBudgetMinutes(currentDateTime);
    long totalErrorBudgetIncrementMinutesFromReset =
        CollectionUtils.emptyIfNull(sloErrorBudgetResets)
            .stream()
            .mapToLong(SLOErrorBudgetResetDTO::getErrorBudgetIncrementMinutes)
            .sum();
    return Math.toIntExact(Math.min(getCurrentTimeRange(currentDateTime).totalMinutes(),
        totalErrorBudgetMinutes + totalErrorBudgetIncrementMinutesFromReset));
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (ServiceLevelObjectiveV2Keys.nextNotificationIteration.equals(fieldName)) {
      return this.nextNotificationIteration;
    }
    if (ServiceLevelObjectiveV2Keys.nextVerificationIteration.equals(fieldName)) {
      return this.nextVerificationIteration;
    }
    if (ServiceLevelObjectiveV2Keys.createNextTaskIteration.equals(fieldName)) {
      return this.createNextTaskIteration;
    }
    if (ServiceLevelObjectiveV2Keys.recordMetricIteration.equals(fieldName)) {
      return this.recordMetricIteration;
    }
    if (ServiceLevelObjectiveV2Keys.sloHistoryTimescaleIteration.equals(fieldName)) {
      return this.sloHistoryTimescaleIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (ServiceLevelObjectiveV2Keys.nextNotificationIteration.equals(fieldName)) {
      this.nextNotificationIteration = nextIteration;
      return;
    }
    if (ServiceLevelObjectiveV2Keys.nextVerificationIteration.equals(fieldName)) {
      this.nextVerificationIteration = nextIteration;
      return;
    }
    if (ServiceLevelObjectiveV2Keys.createNextTaskIteration.equals(fieldName)) {
      this.createNextTaskIteration = nextIteration;
      return;
    }
    if (ServiceLevelObjectiveV2Keys.recordMetricIteration.equals(fieldName)) {
      this.recordMetricIteration = nextIteration;
      return;
    }
    if (ServiceLevelObjectiveV2Keys.sloHistoryTimescaleIteration.equals(fieldName)) {
      this.sloHistoryTimescaleIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public abstract Optional<String> mayBeGetMonitoredServiceIdentifier();

  public abstract static class AbstractServiceLevelObjectiveUpdatableEntity<T extends AbstractServiceLevelObjective>
      implements UpdatableEntity<T, T> {
    protected void setCommonOperations(UpdateOperations<T> updateOperations, T abstractServiceLevelObjective) {
      updateOperations.set(ServiceLevelObjectiveV2Keys.name, abstractServiceLevelObjective.getName())
          .set(ServiceLevelObjectiveV2Keys.tags, abstractServiceLevelObjective.getTags())
          .set(ServiceLevelObjectiveV2Keys.userJourneyIdentifiers,
              abstractServiceLevelObjective.getUserJourneyIdentifiers())
          .set(ServiceLevelObjectiveV2Keys.type, abstractServiceLevelObjective.getType())
          .set(ServiceLevelObjectiveV2Keys.sloTargetPercentage, abstractServiceLevelObjective.getSloTargetPercentage());
      if (abstractServiceLevelObjective.getDesc() != null) {
        updateOperations.set(ServiceLevelObjectiveV2Keys.desc, abstractServiceLevelObjective.getDesc());
      }
      if (abstractServiceLevelObjective.getOrgIdentifier() != null) {
        updateOperations.set(
            ServiceLevelObjectiveV2Keys.orgIdentifier, abstractServiceLevelObjective.getOrgIdentifier());
      }
      if (abstractServiceLevelObjective.getProjectIdentifier() != null) {
        updateOperations.set(
            ServiceLevelObjectiveV2Keys.projectIdentifier, abstractServiceLevelObjective.getProjectIdentifier());
      }
    }
  }
}
