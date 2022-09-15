package io.harness.cvng.migration.list;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WriteServiceLevelObjectivesToV2 implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for reading from ServiceLevelObjectives and write to ServiceLevelObjectivesV2");
    List<ServiceLevelObjective> serviceLevelObjectives =
        hPersistence.createQuery(ServiceLevelObjective.class, excludeAuthority).asList();
    for (ServiceLevelObjective serviceLevelObjective : serviceLevelObjectives) {
      hPersistence.insert(getNewSLOFromOldSLO(serviceLevelObjective));
    }
  }

  private AbstractServiceLevelObjective getNewSLOFromOldSLO(ServiceLevelObjective serviceLevelObjective) {
    return SimpleServiceLevelObjective.builder()
        .accountId(serviceLevelObjective.getAccountId())
        .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
        .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
        .uuid(serviceLevelObjective.getUuid())
        .identifier(serviceLevelObjective.getIdentifier())
        .name(serviceLevelObjective.getName())
        .desc(serviceLevelObjective.getDesc())
        .tags(serviceLevelObjective.getTags() == null ? Collections.emptyList() : serviceLevelObjective.getTags())
        .userJourneyIdentifiers(Collections.singletonList(serviceLevelObjective.getUserJourneyIdentifier()))
        .notificationRuleRefs(serviceLevelObjective.getNotificationRuleRefs())
        .sloTarget(getNewSLOTargetFromOldSLOTarget(serviceLevelObjective.getSloTarget()))
        .enabled(serviceLevelObjective.isEnabled())
        .lastUpdatedAt(serviceLevelObjective.getLastUpdatedAt())
        .createdAt(serviceLevelObjective.getCreatedAt())
        .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
        .nextNotificationIteration(serviceLevelObjective.getNextNotificationIteration())
        .serviceLevelIndicatorType(serviceLevelObjective.getType())
        .healthSourceIdentifier(serviceLevelObjective.getHealthSourceIdentifier())
        .monitoredServiceIdentifier(serviceLevelObjective.getMonitoredServiceIdentifier())
        .serviceLevelIndicators(serviceLevelObjective.getServiceLevelIndicators())
        .build();
  }

  private AbstractServiceLevelObjective.SLOTarget getNewSLOTargetFromOldSLOTarget(
      ServiceLevelObjective.SLOTarget sloTarget) {
    switch (sloTarget.getType()) {
      case CALENDER:
        ServiceLevelObjective.CalenderSLOTarget calenderSLOTarget = (ServiceLevelObjective.CalenderSLOTarget) sloTarget;
        switch (calenderSLOTarget.getCalenderType()) {
          case WEEKLY:
            ServiceLevelObjective.WeeklyCalenderTarget weeklyCalenderTarget =
                (ServiceLevelObjective.WeeklyCalenderTarget) calenderSLOTarget;
            return AbstractServiceLevelObjective.WeeklyCalenderTarget.builder()
                .dayOfWeek(weeklyCalenderTarget.getDayOfWeek())
                .build();
          case MONTHLY:
            ServiceLevelObjective.MonthlyCalenderTarget monthlyCalenderTarget =
                (ServiceLevelObjective.MonthlyCalenderTarget) calenderSLOTarget;
            return AbstractServiceLevelObjective.MonthlyCalenderTarget.builder()
                .windowEndDayOfMonth(monthlyCalenderTarget.getWindowEndDayOfMonth())
                .build();
          case QUARTERLY:
            ServiceLevelObjective.QuarterlyCalenderTarget quarterlyCalenderTarget =
                (ServiceLevelObjective.QuarterlyCalenderTarget) calenderSLOTarget;
            return AbstractServiceLevelObjective.QuarterlyCalenderTarget.builder().build();
          default:
            throw new IllegalStateException(
                "calendar type: " + calenderSLOTarget.getCalenderType() + " is not handled");
        }
      case ROLLING:
        ServiceLevelObjective.RollingSLOTarget rollingSLOTarget = (ServiceLevelObjective.RollingSLOTarget) sloTarget;
        return AbstractServiceLevelObjective.RollingSLOTarget.builder()
            .periodLengthDays(rollingSLOTarget.getPeriodLengthDays())
            .build();
      default:
        throw new IllegalStateException("type: " + sloTarget.getType() + " is not handled");
    }
  }
  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
