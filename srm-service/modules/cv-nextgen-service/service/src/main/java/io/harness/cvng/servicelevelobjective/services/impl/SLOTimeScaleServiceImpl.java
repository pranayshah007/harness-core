/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.SLOTimeScaleService;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Calendar;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLOTimeScaleServiceImpl implements SLOTimeScaleService {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;

  private static final String UPSERT_SERVICE_LEVEL_OBJECTIVE =
      "INSERT INTO SERVICE_LEVEL_OBJECTIVE (REPORTEDAT,UPDATEDAT,ACCOUNTID,ORGID,PROJECTID,SLOID,SLONAME,USERJOURNEY,PERIODLENGTH,SLITYPE,PERIODTYPE,SLOPERCENTAGE,TOTALERRORBUDGET,SERVICE,ENV) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
      + "ON CONFLICT(ACCOUNTID,ORGID,PROJECTID,SLOID) DO UPDATE SET UPDATEDAT = EXCLUDED.UPDATEDAT, USERJOURNEY = EXCLUDED.USERJOURNEY, PERIODLENGTH = EXCLUDED.PERIODLENGTH, SLITYPE = EXCLUDED.SLITYPE,  "
      + "PERIODTYPE = EXCLUDED.PERIODTYPE, SLOPERCENTAGE = EXCLUDED.SLOPERCENTAGE, TOTALERRORBUDGET = EXCLUDED.TOTALERRORBUDGET, SERVICE = EXCLUDED.SERVICE, ENV = EXCLUDED.ENV";

  private static final String UPSERT_SLO_HEALTH_INDICATOR =
      "INSERT INTO SLO_HEALTH_INDICATOR (ACCOUNTID,ORGID,PROJECTID,SLOID,STATUS,ERRORBUDGETPERCENTAGE,ERRORBUDGETREMAINING,SLIVALUE) VALUES (?,?,?,?,?,?,?,?) "
      + "ON CONFLICT(ACCOUNTID,ORGID,PROJECTID,SLOID) DO UPDATE SET STATUS = EXCLUDED.STATUS, ERRORBUDGETPERCENTAGE = EXCLUDED.ERRORBUDGETPERCENTAGE, ERRORBUDGETREMAINING = EXCLUDED.ERRORBUDGETREMAINING, SLIVALUE = EXCLUDED.SLIVALUE,  "
      + "PERIODTYPE = EXCLUDED.PERIODTYPE, SLOPERCENTAGE = EXCLUDED.SLOPERCENTAGE, TOTALERRORBUDGET = EXCLUDED.TOTALERRORBUDGET, SERVICE = EXCLUDED.SERVICE, ENV = EXCLUDED.ENV";

  @Override
  public void upsertServiceLevelObjective(AbstractServiceLevelObjective serviceLevelObjective) {
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement upsertStatement = connection.prepareStatement(UPSERT_SERVICE_LEVEL_OBJECTIVE);) {
      if (serviceLevelObjective instanceof SimpleServiceLevelObjective) {
        SimpleServiceLevelObjective simpleServiceLevelObjective = (SimpleServiceLevelObjective) serviceLevelObjective;
        ProjectParams projectParams = ProjectParams.builder()
                                          .accountIdentifier(serviceLevelObjective.getAccountId())
                                          .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                          .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                          .build();
        MonitoredService monitoredService = monitoredServiceService.getMonitoredService(
            projectParams, simpleServiceLevelObjective.getMonitoredServiceIdentifier());
        SLOHealthIndicator sloHealthIndicator =
            sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());
        upsertStatement.setTimestamp(
            1, new Timestamp(simpleServiceLevelObjective.getCreatedAt()), Calendar.getInstance());
        upsertStatement.setTimestamp(
            2, new Timestamp(simpleServiceLevelObjective.getLastUpdatedAt()), Calendar.getInstance());
        upsertStatement.setString(3, simpleServiceLevelObjective.getAccountId());
        upsertStatement.setString(4, simpleServiceLevelObjective.getOrgIdentifier());
        upsertStatement.setString(5, simpleServiceLevelObjective.getProjectIdentifier());
        upsertStatement.setString(6, simpleServiceLevelObjective.getIdentifier());
        upsertStatement.setString(7, simpleServiceLevelObjective.getName());
        upsertStatement.setString(8, simpleServiceLevelObjective.getUserJourneyIdentifiers().get(0));
        upsertStatement.setInt(9, getPeriodDays(simpleServiceLevelObjective.getSloTarget()));
        upsertStatement.setString(10, simpleServiceLevelObjective.getServiceLevelIndicatorType().toString());
        upsertStatement.setString(11, simpleServiceLevelObjective.getSloTarget().getType().toString());
        upsertStatement.setDouble(12, sloHealthIndicator.getErrorBudgetRemainingPercentage());
        upsertStatement.setDouble(13, simpleServiceLevelObjective.getSloTargetPercentage());
        upsertStatement.setString(14, monitoredService.getServiceIdentifier());
        upsertStatement.setString(15, monitoredService.getEnvironmentIdentifier());
        upsertStatement.execute();
      }
    } catch (Exception ex) {
      log.error("error while upserting slo data.");
    }
  }

  @Override
  public void upsertSloHealthIndicator(AbstractServiceLevelObjective serviceLevelObjective) {
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement upsertStatement = connection.prepareStatement(UPSERT_SLO_HEALTH_INDICATOR)) {
      ProjectParams projectParams = ProjectParams.builder()
                                        .accountIdentifier(serviceLevelObjective.getAccountId())
                                        .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                        .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                        .build();
      SLOHealthIndicator sloHealthIndicator =
          sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());
      SLODashboardWidget.SLOGraphData sloGraphData =
          sloHealthIndicatorService.getGraphData(projectParams, serviceLevelObjective);
      upsertStatement.setString(1, sloHealthIndicator.getAccountId());
      upsertStatement.setString(2, sloHealthIndicator.getOrgIdentifier());
      upsertStatement.setString(3, sloHealthIndicator.getProjectIdentifier());
      upsertStatement.setString(4, sloHealthIndicator.getServiceLevelObjectiveIdentifier());
      upsertStatement.setString(
          5, ErrorBudgetRisk.getFromPercentage(sloGraphData.getErrorBudgetRemainingPercentage()).toString());
      upsertStatement.setDouble(6, sloGraphData.getErrorBudgetRemainingPercentage());
      upsertStatement.setInt(7, sloGraphData.getErrorBudgetRemaining());
      upsertStatement.setDouble(8, sloGraphData.getSliStatusPercentage());
      upsertStatement.execute();
    } catch (Exception ex) {
      log.error("error while upserting slo data.");
    }
  }

  private int getPeriodDays(ServiceLevelObjective.SLOTarget sloTarget) {
    if (sloTarget.getType().equals(SLOTargetType.ROLLING)) {
      return ((ServiceLevelObjective.RollingSLOTarget) sloTarget).getPeriodLengthDays();
    } else {
      SLOCalenderType calenderType = ((ServiceLevelObjective.CalenderSLOTarget) sloTarget).getCalenderType();
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
