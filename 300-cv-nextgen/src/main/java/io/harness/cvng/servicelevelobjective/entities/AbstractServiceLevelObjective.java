/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.servicelevelobjective.entities;

import static java.time.temporal.ChronoUnit.DAYS;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.notification.beans.NotificationRuleRef;
import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

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
  @NotNull String orgIdentifier;
  String projectIdentifier;
  @NotNull @Id private String uuid;
  @NotNull String identifier;
  @NotNull String name;
  String desc;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;
  List<String> userJourneyIdentifiers;
  List<NotificationRuleRef> notificationRuleRefs;
  @NotNull SLOTarget sloTarget;
  private boolean enabled;
  private long lastUpdatedAt;
  private long createdAt;
  @NotNull private Double sloTargetPercentage;
  @FdIndex private long nextNotificationIteration;

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
            .mapToLong(sloErrorBudgetResetDTO -> sloErrorBudgetResetDTO.getErrorBudgetIncrementMinutes())
            .sum();
    return Math.toIntExact(Math.min(getCurrentTimeRange(currentDateTime).totalMinutes(),
        totalErrorBudgetMinutes + totalErrorBudgetIncrementMinutesFromReset));
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (ServiceLevelObjectiveV2Keys.nextNotificationIteration.equals(fieldName)) {
      return this.nextNotificationIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (ServiceLevelObjectiveV2Keys.nextNotificationIteration.equals(fieldName)) {
      this.nextNotificationIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Value
  public static class TimePeriod {
    LocalDateTime startTime;
    LocalDateTime endTime;
    @Builder
    public TimePeriod(LocalDate startDate, LocalDate endDate) {
      this(startDate.atStartOfDay(), endDate.atStartOfDay());
    }
    public static TimePeriod createWithLocalTime(LocalDateTime startTime, LocalDateTime endTime) {
      return new TimePeriod(startTime, endTime);
    }

    private TimePeriod(LocalDateTime startTime, LocalDateTime endTime) {
      this.startTime = startTime;
      this.endTime = endTime;
    }

    public int getRemainingDays(LocalDateTime currentDateTime) {
      return (int) ChronoUnit.DAYS.between(currentDateTime.toLocalDate(), endTime.toLocalDate());
    }
    public int getTotalDays() {
      return (int) DAYS.between(getStartTime(), getEndTime());
    }
    public int totalMinutes() {
      return (int) Duration.between(getStartTime(), getEndTime()).toMinutes();
    }

    /**
     * Start time is inclusive.
     */
    public Instant getStartTime(ZoneOffset zoneId) {
      return getStartTime().toInstant(zoneId);
    }

    /**
     * End time is exclusive.
     */
    public Instant getEndTime(ZoneOffset zoneId) {
      return getEndTime().toInstant(zoneId);
    }
  }

  @Data
  @SuperBuilder
  public abstract static class SLOTarget {
    public abstract TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime);
    public abstract SLOTargetType getType();
    public abstract List<SLODashboardDetail.TimeRangeFilter> getTimeRangeFilters();
  }

  @Data
  @SuperBuilder
  public abstract static class CalenderSLOTarget extends SLOTarget {
    private final SLOTargetType type = SLOTargetType.CALENDER;
    public abstract SLOCalenderType getCalenderType();
  }

  @Value
  @SuperBuilder
  public static class WeeklyCalenderTarget extends CalenderSLOTarget {
    private DayOfWeek dayOfWeek;
    private final SLOCalenderType calenderType = SLOCalenderType.WEEKLY;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      LocalDate nextDayOfWeek = dayOfWeek.getNextDayOfWeek(currentDateTime.toLocalDate());
      return TimePeriod.builder().startDate(nextDayOfWeek.minusDays(6)).endDate(nextDayOfWeek.plusDays(1)).build();
    }

    @Override
    public List<SLODashboardDetail.TimeRangeFilter> getTimeRangeFilters() {
      List<SLODashboardDetail.TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_DAY_FILTER);
      return timeRangeFilterList;
    }
  }

  @SuperBuilder
  @Data
  public static class MonthlyCalenderTarget extends CalenderSLOTarget {
    int windowEndDayOfMonth;
    private final SLOCalenderType calenderType = SLOCalenderType.MONTHLY;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      LocalDate windowStart =
          getWindowEnd(currentDateTime.toLocalDate().minusMonths(1), windowEndDayOfMonth).plusDays(1);
      LocalDate windowEnd = getWindowEnd(currentDateTime.toLocalDate(), windowEndDayOfMonth).plusDays(1);
      return TimePeriod.builder().startDate(windowStart).endDate(windowEnd).build();
    }

    @Override
    public List<SLODashboardDetail.TimeRangeFilter> getTimeRangeFilters() {
      List<SLODashboardDetail.TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_DAY_FILTER);
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_WEEK_FILTER);
      return timeRangeFilterList;
    }

    private LocalDate getWindowEnd(LocalDate currentDateTime, int windowEndDayOfMonth) {
      LocalDate windowEnd;
      if (windowEndDayOfMonth > 28) {
        windowEnd = currentDateTime.with(TemporalAdjusters.lastDayOfMonth());
      } else if (currentDateTime.getDayOfMonth() <= windowEndDayOfMonth) {
        windowEnd = getWindowEnd(currentDateTime);
      } else {
        windowEnd = getWindowEnd(currentDateTime.plusMonths(1));
      }
      return windowEnd;
    }

    private LocalDate getWindowEnd(LocalDate date) {
      return date.plusDays(windowEndDayOfMonth - date.getDayOfMonth());
    }
  }

  @SuperBuilder
  @Data
  public static class QuarterlyCalenderTarget extends CalenderSLOTarget {
    private final SLOCalenderType calenderType = SLOCalenderType.QUARTERLY;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      LocalDate firstDayOfQuarter = currentDateTime.toLocalDate()
                                        .with(currentDateTime.toLocalDate().getMonth().firstMonthOfQuarter())
                                        .with(TemporalAdjusters.firstDayOfMonth());

      LocalDate lastDayOfQuarter = firstDayOfQuarter.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
      return TimePeriod.builder().startDate(firstDayOfQuarter).endDate(lastDayOfQuarter.plusDays(1)).build();
    }

    @Override
    public List<SLODashboardDetail.TimeRangeFilter> getTimeRangeFilters() {
      List<SLODashboardDetail.TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_DAY_FILTER);
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_WEEK_FILTER);
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_MONTH_FILTER);
      return timeRangeFilterList;
    }
  }

  @SuperBuilder
  @Data
  public static class RollingSLOTarget extends SLOTarget {
    int periodLengthDays;
    private final SLOTargetType type = SLOTargetType.ROLLING;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      currentDateTime = currentDateTime.truncatedTo(ChronoUnit.MINUTES);
      return TimePeriod.createWithLocalTime(
          currentDateTime.minusMinutes(TimeUnit.DAYS.toMinutes(periodLengthDays)), currentDateTime);
    }

    @Override
    public List<SLODashboardDetail.TimeRangeFilter> getTimeRangeFilters() {
      List<SLODashboardDetail.TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_DAY_FILTER);
      if (this.periodLengthDays > 7) {
        timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_WEEK_FILTER);
      }
      return timeRangeFilterList;
    }
  }
}
