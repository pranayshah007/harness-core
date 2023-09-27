/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CalenderSLOTarget;
import io.harness.cvng.servicelevelobjective.entities.RollingSLOTarget;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.SLOTarget;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.SLOTimeScaleService;
import io.harness.cvng.utils.ScopedInformation;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLOTimeScaleServiceImpl implements SLOTimeScaleService {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject private ServiceLevelObjectiveV2ServiceImpl serviceLevelObjectiveV2Service;
  @Inject Clock clock;
  private static final String UPSERT_SERVICE_LEVEL_OBJECTIVE =
      "INSERT INTO SERVICE_LEVEL_OBJECTIVE (REPORTEDAT,UPDATEDAT,ACCOUNTID,ORGID,PROJECTID,SLOID,SLONAME,USERJOURNEY,PERIODLENGTH,SLITYPE,PERIODTYPE,SLOPERCENTAGE,TOTALERRORBUDGET,SERVICE,ENV,SLOTYPE,SLIEVALUATIONTYPE,CUSTOM_ACCOUNT_ORG_PROJECT) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
      + "ON CONFLICT(CUSTOM_ACCOUNT_ORG_PROJECT,SLOID) DO UPDATE SET UPDATEDAT = EXCLUDED.UPDATEDAT, USERJOURNEY = EXCLUDED.USERJOURNEY, PERIODLENGTH = EXCLUDED.PERIODLENGTH, SLOTYPE = EXCLUDED.SLOTYPE, "
      + "SLIEVALUATIONTYPE = EXCLUDED.SLIEVALUATIONTYPE, PERIODTYPE = EXCLUDED.PERIODTYPE, SLOPERCENTAGE = EXCLUDED.SLOPERCENTAGE, TOTALERRORBUDGET = EXCLUDED.TOTALERRORBUDGET, SERVICE = EXCLUDED.SERVICE, ENV = EXCLUDED.ENV, SLONAME = EXCLUDED.SLONAME";

  private static final String DELETE_SERVICE_LEVEL_OBJECTIVE =
      "DELETE FROM SERVICE_LEVEL_OBJECTIVE WHERE CUSTOM_ACCOUNT_ORG_PROJECT = ? AND SLOID = ?";

  private static final String UPSERT_SLO_HEALTH_INDICATOR =
      "INSERT INTO SLO_HEALTH_INDICATOR (ACCOUNTID,ORGID,PROJECTID,SLOID,STATUS,ERRORBUDGETPERCENTAGE,ERRORBUDGETREMAINING,SLIVALUE,CUSTOM_ACCOUNT_ORG_PROJECT,SLOHEALTH) VALUES (?,?,?,?,?,?,?,?,?,?) "
      + "ON CONFLICT(CUSTOM_ACCOUNT_ORG_PROJECT,SLOID) DO UPDATE SET STATUS = EXCLUDED.STATUS, ERRORBUDGETPERCENTAGE = EXCLUDED.ERRORBUDGETPERCENTAGE, ERRORBUDGETREMAINING = EXCLUDED.ERRORBUDGETREMAINING, SLIVALUE = EXCLUDED.SLIVALUE, SLOHEALTH = EXCLUDED.SLOHEALTH";

  private static final String INSERT_SLO_HISTORY =
      "INSERT INTO SLO_HISTORY (STARTTIME,ENDTIME,ACCOUNTID,ORGID,PROJECTID,SLOID,ERRORBUDGETREMAINING,TARGETPERCENTAGE,SLIATEND,TARGETMET,PERIODLENGTH,TOTALERRORBUDGET,CUSTOM_ACCOUNT_ORG_PROJECT) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
      + "ON CONFLICT(CUSTOM_ACCOUNT_ORG_PROJECT,SLOID,STARTTIME,ENDTIME) DO NOTHING";

  @Override
  public void upsertServiceLevelObjective(AbstractServiceLevelObjective serviceLevelObjective) {
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement upsertStatement = connection.prepareStatement(UPSERT_SERVICE_LEVEL_OBJECTIVE);) {
      ProjectParams projectParams = ProjectParams.builder()
                                        .accountIdentifier(serviceLevelObjective.getAccountId())
                                        .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                        .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                        .build();
      LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset());
      upsertStatement.setTimestamp(1, new Timestamp(serviceLevelObjective.getCreatedAt()), Calendar.getInstance());
      upsertStatement.setTimestamp(2, new Timestamp(serviceLevelObjective.getLastUpdatedAt()), Calendar.getInstance());
      upsertStatement.setString(3, serviceLevelObjective.getAccountId());
      upsertStatement.setString(4, serviceLevelObjective.getOrgIdentifier());
      upsertStatement.setString(5, serviceLevelObjective.getProjectIdentifier());
      upsertStatement.setString(6, serviceLevelObjective.getIdentifier());
      upsertStatement.setString(7, serviceLevelObjective.getName());
      upsertStatement.setString(8, serviceLevelObjective.getUserJourneyIdentifiers().get(0));
      upsertStatement.setInt(9, getPeriodDays(serviceLevelObjective.getTarget()));
      upsertStatement.setString(10, null);
      upsertStatement.setString(11, serviceLevelObjective.getTarget().getType().toString());
      upsertStatement.setDouble(13, serviceLevelObjective.getTotalErrorBudgetMinutes(currentLocalDate));
      upsertStatement.setDouble(12, serviceLevelObjective.getSloTargetPercentage());
      if (serviceLevelObjective instanceof SimpleServiceLevelObjective) {
        SimpleServiceLevelObjective simpleServiceLevelObjective = (SimpleServiceLevelObjective) serviceLevelObjective;
        MonitoredService monitoredService = monitoredServiceService.getMonitoredService(
            projectParams, simpleServiceLevelObjective.getMonitoredServiceIdentifier());
        upsertStatement.setString(14, monitoredService.getServiceIdentifier());
        upsertStatement.setString(15, monitoredService.getEnvironmentIdentifier());
      } else {
        upsertStatement.setString(14, null);
        upsertStatement.setString(15, null);
      }
      upsertStatement.setString(16, serviceLevelObjective.getType().toString());
      upsertStatement.setString(17, serviceLevelObjective.getSliEvaluationType().toString());
      upsertStatement.setString(18,
          ScopedInformation.getScopedInformation(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
              projectParams.getProjectIdentifier()));
      upsertStatement.execute();
    } catch (Exception ex) {
      log.error("error while upserting slo data {}", ex);
    }
  }

  @Override
  public void deleteServiceLevelObjective(ProjectParams projectParams, String identifier) {
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement deleteStatement = connection.prepareStatement(DELETE_SERVICE_LEVEL_OBJECTIVE)) {
      deleteStatement.setString(1,
          ScopedInformation.getScopedInformation(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
              projectParams.getProjectIdentifier()));
      deleteStatement.setString(2, identifier);
      deleteStatement.execute();
    } catch (Exception ex) {
      log.error("error while deleting slo data. {}", ex);
    }
  }

  @Override
  public void upsertSloHealthIndicator(SLOHealthIndicator sloHealthIndicator) {
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement upsertStatement = connection.prepareStatement(UPSERT_SLO_HEALTH_INDICATOR)) {
      ProjectParams projectParams = ProjectParams.builder()
                                        .accountIdentifier(sloHealthIndicator.getAccountId())
                                        .orgIdentifier(sloHealthIndicator.getOrgIdentifier())
                                        .projectIdentifier(sloHealthIndicator.getProjectIdentifier())
                                        .build();
      AbstractServiceLevelObjective serviceLevelObjective = serviceLevelObjectiveV2Service.getEntity(
          projectParams, sloHealthIndicator.getServiceLevelObjectiveIdentifier());
      upsertStatement.setString(1, projectParams.getAccountIdentifier());
      upsertStatement.setString(2, projectParams.getOrgIdentifier());
      upsertStatement.setString(3, projectParams.getProjectIdentifier());
      upsertStatement.setString(4, sloHealthIndicator.getServiceLevelObjectiveIdentifier());
      upsertStatement.setString(5, sloHealthIndicator.getErrorBudgetRisk().getDisplayName());
      upsertStatement.setDouble(6, sloHealthIndicator.getErrorBudgetRemainingPercentage());
      upsertStatement.setInt(7, sloHealthIndicator.getErrorBudgetRemainingMinutes());
      upsertStatement.setDouble(8,
          sloHealthIndicatorService.getGraphData(projectParams, serviceLevelObjective, null).getSliStatusPercentage());
      upsertStatement.setString(9,
          ScopedInformation.getScopedInformation(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
              projectParams.getProjectIdentifier()));
      if (sloHealthIndicator.getFailedState()) {
        upsertStatement.setString(10, "UNHEALTHY");
      } else {
        upsertStatement.setString(10, "HEALTHY");
      }
      upsertStatement.execute();
    } catch (Exception ex) {
      log.error("error while upserting slo health indicator data. {}", ex);
    }
  }

  @Override
  public void insertSLOHistory(AbstractServiceLevelObjective serviceLevelObjective) {
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement insertStatement = connection.prepareStatement(INSERT_SLO_HISTORY);) {
      ProjectParams projectParams = ProjectParams.builder()
                                        .accountIdentifier(serviceLevelObjective.getAccountId())
                                        .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                        .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                        .build();
      LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset());
      TimePeriod timePeriod = serviceLevelObjective.getTarget().getTimeRangeForHistory(currentLocalDate);
      SLODashboardWidget.SLOGraphData sloGraphData =
          sloHealthIndicatorService.getGraphData(projectParams, serviceLevelObjective,
              TimeRangeParams.builder()
                  .startTime(timePeriod.getStartTime(ZoneOffset.UTC))
                  .endTime(timePeriod.getEndTime(ZoneOffset.UTC))
                  .build());
      if (!sloGraphData.getSloPerformanceTrend().isEmpty()) {
        insertStatement.setTimestamp(1,
            new Timestamp(timePeriod.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli()), Calendar.getInstance());
        insertStatement.setTimestamp(
            2, new Timestamp(timePeriod.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli()), Calendar.getInstance());
        insertStatement.setString(3, serviceLevelObjective.getAccountId());
        insertStatement.setString(4, serviceLevelObjective.getOrgIdentifier());
        insertStatement.setString(5, serviceLevelObjective.getProjectIdentifier());
        insertStatement.setString(6, serviceLevelObjective.getIdentifier());
        insertStatement.setString(7, String.valueOf(sloGraphData.getErrorBudgetRemaining()));
        insertStatement.setString(8, String.valueOf(serviceLevelObjective.getSloTargetPercentage()));
        insertStatement.setString(9, String.valueOf(sloGraphData.getSliStatusPercentage()));
        if (sloGraphData.getErrorBudgetRemaining() > 0) {
          insertStatement.setString(10, "YES");
        } else {
          insertStatement.setString(10, "NO");
        }
        insertStatement.setInt(11, getPeriodDays(serviceLevelObjective.getTarget()));
        insertStatement.setInt(12, serviceLevelObjective.getTotalErrorBudgetMinutes(currentLocalDate));
        insertStatement.setString(13,
            ScopedInformation.getScopedInformation(projectParams.getAccountIdentifier(),
                projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
        insertStatement.execute();
      }
    } catch (Exception ex) {
      log.error("error while upserting slo history data. {}", ex);
    }
  }

  private int getPeriodDays(SLOTarget sloTarget) {
    if (sloTarget.getType().equals(SLOTargetType.ROLLING)) {
      return ((RollingSLOTarget) sloTarget).getPeriodLengthDays();
    } else {
      SLOCalenderType calenderType = ((CalenderSLOTarget) sloTarget).getCalenderType();
      switch (calenderType) {
        case WEEKLY:
          return 7;
        case MONTHLY:
          return 30;
        case QUARTERLY:
          return 90;
        default:
          return 0;
      }
    }
  }
}
