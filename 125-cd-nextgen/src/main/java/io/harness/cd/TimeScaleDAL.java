/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cd;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.timescaledb.Tables.ENVIRONMENTS;
import static io.harness.timescaledb.Tables.NG_INSTANCE_STATS;
import static io.harness.timescaledb.Tables.PIPELINE_EXECUTION_SUMMARY_CD;
import static io.harness.timescaledb.Tables.SERVICES;
import static io.harness.timescaledb.Tables.SERVICES_LICENSE_DAILY_REPORT;
import static io.harness.timescaledb.Tables.SERVICE_INFRA_INFO;
import static io.harness.timescaledb.Tables.SERVICE_INSTANCES_LICENSE_DAILY_REPORT;

import io.harness.aggregates.AggregateProjectInfo;
import io.harness.aggregates.AggregateServiceInfo;
import io.harness.aggregates.TimeWiseExecutionSummary;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.usage.pojos.LicenseDailyUsage;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.dashboards.GroupBy;
import io.harness.timescaledb.tables.pojos.PipelineExecutionSummaryCd;
import io.harness.timescaledb.tables.pojos.ServiceInfraInfo;
import io.harness.timescaledb.tables.pojos.ServiceInstancesLicenseDailyReport;
import io.harness.timescaledb.tables.pojos.Services;
import io.harness.timescaledb.tables.pojos.ServicesLicenseDailyReport;
import io.harness.timescaledb.tables.records.ServiceInstancesLicenseDailyReportRecord;
import io.harness.timescaledb.tables.records.ServicesLicenseDailyReportRecord;

import com.google.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStep3;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Table;
import org.jooq.impl.DSL;

@Slf4j
@OwnedBy(PIPELINE)
public class TimeScaleDAL {
  public static final int RECORDS_LIMIT = 100;
  public static final String ORG_ID = "orgId";
  public static final String PROJECT_ID = "projectId";
  public static final String SERVICE_ID = "serviceId";
  public static final int BULK_INSERT_LIMIT = 100;

  @Inject private DSLContext dsl;

  public List<ServiceInfraInfo> getDistinctServiceWithExecutionInTimeRange(
      @NotNull final String accountId, Long startIntervalInMillis, Long endIntervalInMillis) {
    try {
      return dsl
          .selectDistinct(
              SERVICE_INFRA_INFO.ORGIDENTIFIER, SERVICE_INFRA_INFO.PROJECTIDENTIFIER, SERVICE_INFRA_INFO.SERVICE_ID)
          .from(SERVICE_INFRA_INFO)
          .where(SERVICE_INFRA_INFO.ACCOUNTID.eq(accountId)
                     .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.greaterOrEqual(startIntervalInMillis))
                     .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.lessOrEqual(endIntervalInMillis)))
          .fetchInto(ServiceInfraInfo.class);
    } catch (Exception e) {
      log.error(
          "Exception while fetching Distinct Services which are present in the executions in the specified time-range for account {}",
          accountId, e);
      throw e;
    }
  }

  public List<AggregateServiceInfo> getTopServicesByDeploymentCount(@NotNull String accountIdentifier,
      Long startInterval, Long endInterval, Table<Record2<String, String>> orgProjectTable, List<String> statusList) {
    try {
      return dsl
          .select(SERVICE_INFRA_INFO.ORGIDENTIFIER, SERVICE_INFRA_INFO.PROJECTIDENTIFIER, SERVICE_INFRA_INFO.SERVICE_ID,
              DSL.count().as("count"))
          .from(SERVICE_INFRA_INFO)
          .where(SERVICE_INFRA_INFO.ACCOUNTID.eq(accountIdentifier)
                     .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.greaterOrEqual(startInterval))
                     .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.lessThan(endInterval))
                     .and(SERVICE_INFRA_INFO.SERVICE_STATUS.in(statusList)))
          .andExists(dsl.selectOne()
                         .from(orgProjectTable)
                         .where(SERVICE_INFRA_INFO.ORGIDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                                    .and(SERVICE_INFRA_INFO.PROJECTIDENTIFIER.eq(
                                        (Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .groupBy(
              SERVICE_INFRA_INFO.ORGIDENTIFIER, SERVICE_INFRA_INFO.PROJECTIDENTIFIER, SERVICE_INFRA_INFO.SERVICE_ID)
          .orderBy(DSL.inline(4).desc())
          .limit(RECORDS_LIMIT)
          .fetchInto(AggregateServiceInfo.class);
    } catch (Exception e) {
      log.error("Exception while fetching most used services by executions for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public List<AggregateServiceInfo> getTopServicesByInstanceCount(
      String accountIdentifier, long startInterval, long endInterval, Table<Record2<String, String>> orgProjectTable) {
    Field<Long> reportedDateEpoch = DSL.epoch(NG_INSTANCE_STATS.REPORTEDAT).cast(Long.class).mul(1000);
    try {
      return dsl
          .select(NG_INSTANCE_STATS.ORGID, NG_INSTANCE_STATS.PROJECTID, NG_INSTANCE_STATS.SERVICEID,
              DSL.count().as("count"))
          .from(NG_INSTANCE_STATS)
          .where(NG_INSTANCE_STATS.ACCOUNTID.eq(accountIdentifier)
                     .and(reportedDateEpoch.greaterOrEqual(startInterval))
                     .and(reportedDateEpoch.lessThan(endInterval)))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectTable)
                  .where(NG_INSTANCE_STATS.ORGID.eq((Field<String>) orgProjectTable.field(ORG_ID))
                             .and(NG_INSTANCE_STATS.PROJECTID.eq((Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .groupBy(NG_INSTANCE_STATS.ORGID, NG_INSTANCE_STATS.PROJECTID, NG_INSTANCE_STATS.SERVICEID)
          .orderBy(DSL.inline(4).desc())
          .limit(RECORDS_LIMIT)
          .fetchInto(AggregateServiceInfo.class);
    } catch (Exception e) {
      log.error("Exception while fetching most used services by instanceCount for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public List<AggregateServiceInfo> getInstanceCountForGivenServices(
      Table<Record3<String, String, String>> orgProjectServiceTable, String accountIdentifier, long startInterval,
      long endInterval) {
    Field<Long> reportedDateEpoch = DSL.epoch(NG_INSTANCE_STATS.REPORTEDAT).cast(Long.class).mul(1000);

    try {
      return dsl
          .select(NG_INSTANCE_STATS.ORGID, NG_INSTANCE_STATS.PROJECTID, NG_INSTANCE_STATS.SERVICEID,
              DSL.count().as("count"))
          .from(NG_INSTANCE_STATS)
          .where(NG_INSTANCE_STATS.ACCOUNTID.eq(accountIdentifier)
                     .and(reportedDateEpoch.greaterOrEqual(startInterval))
                     .and(reportedDateEpoch.lessThan(endInterval)))
          .andExists(dsl.selectOne()
                         .from(orgProjectServiceTable)
                         .where(NG_INSTANCE_STATS.ORGID.eq((Field<String>) orgProjectServiceTable.field(ORG_ID))
                                    .and(NG_INSTANCE_STATS.PROJECTID.eq(
                                        (Field<String>) orgProjectServiceTable.field(PROJECT_ID)))
                                    .and(NG_INSTANCE_STATS.SERVICEID.eq(
                                        (Field<String>) orgProjectServiceTable.field(SERVICE_ID)))))
          .groupBy(NG_INSTANCE_STATS.ORGID, NG_INSTANCE_STATS.PROJECTID, NG_INSTANCE_STATS.SERVICEID)
          .orderBy(DSL.inline(4).desc())
          .limit(RECORDS_LIMIT)
          .fetchInto(AggregateServiceInfo.class);
    } catch (Exception e) {
      log.error("Exception while fetching instance count for given services for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public List<Services> getNamesForServiceIds(
      String accountIdentifier, Table<Record3<String, String, String>> orgProjectServiceTable) {
    try {
      return dsl.select(SERVICES.ORG_IDENTIFIER, SERVICES.PROJECT_IDENTIFIER, SERVICES.IDENTIFIER, SERVICES.NAME)
          .from(SERVICES)
          .where(SERVICES.ACCOUNT_ID.eq(accountIdentifier))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectServiceTable)
                  .where(
                      SERVICES.ORG_IDENTIFIER.eq((Field<String>) orgProjectServiceTable.field(ORG_ID))
                          .and(SERVICES.PROJECT_IDENTIFIER.eq((Field<String>) orgProjectServiceTable.field(PROJECT_ID)))
                          .and(SERVICES.IDENTIFIER.eq((Field<String>) orgProjectServiceTable.field(SERVICE_ID)))))
          .fetchInto(Services.class);
    } catch (Exception e) {
      log.error("Exception while fetching services for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public List<AggregateServiceInfo> getStatusWiseDeploymentCountForGivenServices(
      Table<Record3<String, String, String>> orgProjectServiceTable, String accountIdentifier, long startInterval,
      long endInterval, List<String> statusList) {
    try {
      return dsl
          .select(SERVICE_INFRA_INFO.ORGIDENTIFIER, SERVICE_INFRA_INFO.PROJECTIDENTIFIER, SERVICE_INFRA_INFO.SERVICE_ID,
              SERVICE_INFRA_INFO.SERVICE_STATUS, DSL.count().as("count"))
          .from(SERVICE_INFRA_INFO)
          .where(SERVICE_INFRA_INFO.ACCOUNTID.eq(accountIdentifier)
                     .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.greaterOrEqual(startInterval))
                     .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.lessThan(endInterval))
                     .and(SERVICE_INFRA_INFO.SERVICE_STATUS.in(statusList)))

          .andExists(
              dsl.selectOne()
                  .from(orgProjectServiceTable)
                  .where(SERVICE_INFRA_INFO.ORGIDENTIFIER.eq((Field<String>) orgProjectServiceTable.field(ORG_ID))
                             .and(SERVICE_INFRA_INFO.PROJECTIDENTIFIER.eq(
                                 (Field<String>) orgProjectServiceTable.field(PROJECT_ID)))
                             .and(SERVICE_INFRA_INFO.SERVICE_ID.eq(
                                 (Field<String>) orgProjectServiceTable.field(SERVICE_ID)))))
          .groupBy(SERVICE_INFRA_INFO.ORGIDENTIFIER, SERVICE_INFRA_INFO.PROJECTIDENTIFIER,
              SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.SERVICE_STATUS)
          .limit(RECORDS_LIMIT)
          .fetchInto(AggregateServiceInfo.class);
    } catch (Exception e) {
      log.error("Exception while fetching status wise deployment count with given services for account {}",
          accountIdentifier, e);
      throw e;
    }
  }

  public List<AggregateServiceInfo> getDeploymentCountForGivenServices(
      Table<Record3<String, String, String>> orgProjectServiceTable, String accountIdentifier, long startInterval,
      long endInterval, List<String> statusList) {
    try {
      return dsl
          .select(SERVICE_INFRA_INFO.ORGIDENTIFIER, SERVICE_INFRA_INFO.PROJECTIDENTIFIER, SERVICE_INFRA_INFO.SERVICE_ID,
              DSL.count().as("count"))
          .from(SERVICE_INFRA_INFO)
          .where(SERVICE_INFRA_INFO.ACCOUNTID.eq(accountIdentifier)
                     .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.greaterOrEqual(startInterval))
                     .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.lessThan(endInterval))
                     .and(SERVICE_INFRA_INFO.SERVICE_STATUS.in(statusList)))

          .andExists(
              dsl.selectOne()
                  .from(orgProjectServiceTable)
                  .where(SERVICE_INFRA_INFO.ORGIDENTIFIER.eq((Field<String>) orgProjectServiceTable.field(ORG_ID))
                             .and(SERVICE_INFRA_INFO.PROJECTIDENTIFIER.eq(
                                 (Field<String>) orgProjectServiceTable.field(PROJECT_ID)))
                             .and(SERVICE_INFRA_INFO.SERVICE_ID.eq(
                                 (Field<String>) orgProjectServiceTable.field(SERVICE_ID)))))
          .groupBy(
              SERVICE_INFRA_INFO.ORGIDENTIFIER, SERVICE_INFRA_INFO.PROJECTIDENTIFIER, SERVICE_INFRA_INFO.SERVICE_ID)
          .limit(RECORDS_LIMIT)
          .fetchInto(AggregateServiceInfo.class);
    } catch (Exception e) {
      log.error("Exception while fetching deployment count for given services for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public List<AggregateProjectInfo> getTopProjectsByDeploymentCount(String accountIdentifier, long startInterval,
      long endInterval, Table<Record2<String, String>> orgProjectTable, List<String> statusList) {
    try {
      return dsl
          .select(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER,
              DSL.count().as("count"))
          .from(PIPELINE_EXECUTION_SUMMARY_CD)
          .where(PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID.eq(accountIdentifier)
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.greaterOrEqual(startInterval))
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lessThan(endInterval))
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(statusList)))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectTable)
                  .where(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                             .and(PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(
                                 (Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .groupBy(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER)
          .orderBy(DSL.inline(3).desc())
          .limit(RECORDS_LIMIT)
          .fetchInto(AggregateProjectInfo.class);
    } catch (Exception e) {
      log.error("Exception while fetching top projects by deployment count for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public List<PipelineExecutionSummaryCd> getPipelineExecutionsForGivenExecutionStatus(
      String accountIdentifier, Table<Record2<String, String>> orgProjectTable, List<String> requiredStatuses) {
    try {
      return dsl
          .select(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER,
              PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.NAME,
              PIPELINE_EXECUTION_SUMMARY_CD.STARTTS, PIPELINE_EXECUTION_SUMMARY_CD.STATUS,
              PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID)
          .from(PIPELINE_EXECUTION_SUMMARY_CD)
          .where(PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID.eq(accountIdentifier)
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(requiredStatuses)))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectTable)
                  .where(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                             .and(PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(
                                 (Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .fetchInto(PipelineExecutionSummaryCd.class);
    } catch (Exception e) {
      log.error("Exception while fetching pipeline executions for given execution status for account {}",
          accountIdentifier, e);
      throw e;
    }
  }

  public List<TimeWiseExecutionSummary> getTimeExecutionStatusWiseDeploymentCount(String accountIdentifier,
      long startInterval, long endInterval, GroupBy groupBy, Table<Record2<String, String>> orgProjectTable,
      List<String> statusList) {
    Field<Long> epoch = DSL.field("time_bucket_gapfill(" + groupBy.getNoOfMilliseconds() + ", {0})", Long.class,
        PIPELINE_EXECUTION_SUMMARY_CD.STARTTS);

    try {
      return dsl.select(epoch, PIPELINE_EXECUTION_SUMMARY_CD.STATUS, DSL.count().as("count"))
          .from(PIPELINE_EXECUTION_SUMMARY_CD)
          .where(PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID.eq(accountIdentifier)
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.greaterOrEqual(startInterval))
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lessThan(endInterval))
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(statusList)))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectTable)
                  .where(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                             .and(PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(
                                 (Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .groupBy(DSL.one(), PIPELINE_EXECUTION_SUMMARY_CD.STATUS)
          .orderBy(DSL.one())
          .fetchInto(TimeWiseExecutionSummary.class);
    } catch (Exception e) {
      log.error(
          "Exception while fetching Time execution status wise deployment count for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public Integer getNewServicesCount(
      String accountIdentifier, Long startInterval, Long endInterval, Table<Record2<String, String>> orgProjectTable) {
    try {
      return dsl.select(DSL.count())
          .from(SERVICES)
          .where(SERVICES.ACCOUNT_ID.eq(accountIdentifier))
          .and(SERVICES.CREATED_AT.greaterOrEqual(startInterval))
          .and(SERVICES.CREATED_AT.lessThan(endInterval))
          .and(SERVICES.DELETED.eq(false))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectTable)
                  .where(SERVICES.ORG_IDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                             .and(SERVICES.PROJECT_IDENTIFIER.eq((Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .fetchInto(Integer.class)
          .get(0);
    } catch (Exception e) {
      log.error("Exception while getting new services count for account {}", accountIdentifier, e);
      throw e;
    }
  }

  // Environments Created Previously but deleted in given time range
  public Integer getDeletedEnvCount(
      String accountIdentifier, Long startInterval, Long endInterval, Table<Record2<String, String>> orgProjectTable) {
    try {
      return dsl.select(DSL.count())
          .from(ENVIRONMENTS)
          .where(ENVIRONMENTS.ACCOUNT_ID.eq(accountIdentifier))
          .and(ENVIRONMENTS.LAST_MODIFIED_AT.greaterOrEqual(startInterval))
          .and(ENVIRONMENTS.LAST_MODIFIED_AT.lessThan(endInterval))
          .and(ENVIRONMENTS.CREATED_AT.lessThan(startInterval))
          .and(ENVIRONMENTS.DELETED.eq(true))
          .andExists(dsl.selectOne()
                         .from(orgProjectTable)
                         .where(ENVIRONMENTS.ORG_IDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                                    .and(ENVIRONMENTS.PROJECT_IDENTIFIER.eq(
                                        (Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .fetchInto(Integer.class)
          .get(0);
    } catch (Exception e) {
      log.error("Exception while getting deleted env count for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public Integer getTotalServicesCount(String accountIdentifier, Table<Record2<String, String>> orgProjectTable) {
    try {
      return dsl.select(DSL.count())
          .from(SERVICES)
          .where(SERVICES.ACCOUNT_ID.eq(accountIdentifier))
          .and(SERVICES.DELETED.eq(false))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectTable)
                  .where(SERVICES.ORG_IDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                             .and(SERVICES.PROJECT_IDENTIFIER.eq((Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .fetchInto(Integer.class)
          .get(0);
    } catch (Exception e) {
      log.error("Exception caught while getting total services count for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public Integer getDeletedServiceCount(
      String accountIdentifier, Long startInterval, Long endInterval, Table<Record2<String, String>> orgProjectTable) {
    try {
      return dsl.select(DSL.count())
          .from(SERVICES)
          .where(SERVICES.ACCOUNT_ID.eq(accountIdentifier))
          .and(SERVICES.LAST_MODIFIED_AT.greaterOrEqual(startInterval))
          .and(SERVICES.LAST_MODIFIED_AT.lessThan(endInterval))
          .and(SERVICES.DELETED.eq(true))
          .and(SERVICES.CREATED_AT.lessThan(startInterval))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectTable)
                  .where(SERVICES.ORG_IDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                             .and(SERVICES.PROJECT_IDENTIFIER.eq((Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .fetchInto(Integer.class)
          .get(0);
    } catch (Exception e) {
      log.error("Exception while getting deleted service count for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public Integer getTotalEnvCount(String accountIdentifier, Table<Record2<String, String>> orgProjectTable) {
    try {
      return dsl.select(DSL.count())
          .from(ENVIRONMENTS)
          .where(ENVIRONMENTS.ACCOUNT_ID.eq(accountIdentifier))
          .and(ENVIRONMENTS.DELETED.eq(false))
          .andExists(dsl.selectOne()
                         .from(orgProjectTable)
                         .where(ENVIRONMENTS.ORG_IDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                                    .and(ENVIRONMENTS.PROJECT_IDENTIFIER.eq(
                                        (Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .fetchInto(Integer.class)
          .get(0);
    } catch (Exception e) {
      log.error("Exception caught while getting total env count for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public Integer getNewEnvCount(
      String accountIdentifier, Long startInterval, Long endInterval, Table<Record2<String, String>> orgProjectTable) {
    try {
      return dsl.select(DSL.count())
          .from(ENVIRONMENTS)
          .where(ENVIRONMENTS.ACCOUNT_ID.eq(accountIdentifier))
          .and(ENVIRONMENTS.CREATED_AT.greaterOrEqual(startInterval))
          .and(ENVIRONMENTS.CREATED_AT.lessThan(endInterval))
          .and(ENVIRONMENTS.DELETED.eq(false))
          .andExists(dsl.selectOne()
                         .from(orgProjectTable)
                         .where(ENVIRONMENTS.ORG_IDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                                    .and(ENVIRONMENTS.PROJECT_IDENTIFIER.eq(
                                        (Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .fetchInto(Integer.class)
          .get(0);
    } catch (Exception e) {
      log.error("Exception while getting new env count for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public List<PipelineExecutionSummaryCd> getFailedExecutionsForGivenTimeRange(
      String accountIdentifier, Table<Record2<String, String>> orgProjectTable, Long endTime, Long startTime) {
    try {
      return dsl
          .select(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER,
              PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.NAME,
              PIPELINE_EXECUTION_SUMMARY_CD.STARTTS, PIPELINE_EXECUTION_SUMMARY_CD.STATUS,
              PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID)
          .from(PIPELINE_EXECUTION_SUMMARY_CD)
          .where(PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID.eq(accountIdentifier)
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(CDDashboardServiceHelper.failedStatusList))
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.greaterOrEqual(startTime))
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lessThan(endTime)))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectTable)
                  .where(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                             .and(PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(
                                 (Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .fetchInto(PipelineExecutionSummaryCd.class);
    } catch (Exception e) {
      log.error(
          "Exception while fetching failed executions in the given time range for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public List<AggregateProjectInfo> getProjectWiseStatusWiseDeploymentCount(
      Table<Record2<String, String>> orgProjectTable, String accountIdentifier, long startInterval, long endInterval,
      List<String> statusList) {
    try {
      return dsl
          .select(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER,
              PIPELINE_EXECUTION_SUMMARY_CD.STATUS, DSL.count().as("count"))
          .from(PIPELINE_EXECUTION_SUMMARY_CD)
          .where(PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID.eq(accountIdentifier)
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.greaterOrEqual(startInterval))
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lessThan(endInterval))
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(statusList)))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectTable)
                  .where(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                             .and(PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(
                                 (Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .groupBy(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER,
              PIPELINE_EXECUTION_SUMMARY_CD.STATUS)
          .fetchInto(AggregateProjectInfo.class);
    } catch (Exception e) {
      log.error(
          "Exception while getting project wise and status wise deployment count for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public List<AggregateProjectInfo> getProjectWiseDeploymentCount(Table<Record2<String, String>> orgProjectTable,
      String accountIdentifier, long startInterval, long endInterval, List<String> statusList) {
    try {
      return dsl
          .select(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER,
              DSL.count().as("count"))
          .from(PIPELINE_EXECUTION_SUMMARY_CD)
          .where(PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID.eq(accountIdentifier)
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.greaterOrEqual(startInterval))
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lessThan(endInterval))
                     .and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(statusList)))
          .andExists(
              dsl.selectOne()
                  .from(orgProjectTable)
                  .where(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                             .and(PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(
                                 (Field<String>) orgProjectTable.field(PROJECT_ID)))))
          .groupBy(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER)
          .fetchInto(AggregateProjectInfo.class);
    } catch (Exception e) {
      log.error("Exception while getting project wise deployment count for account {}", accountIdentifier, e);
      throw e;
    }
  }

  public List<ServiceInstancesLicenseDailyReport> getLatestServiceInstancesLicenseDailyReportByAccountId(
      @NotNull String accountId) {
    try {
      return dsl.select()
          .from(SERVICE_INSTANCES_LICENSE_DAILY_REPORT)
          .where(SERVICE_INSTANCES_LICENSE_DAILY_REPORT.ACCOUNT_ID.eq(accountId))
          .orderBy(DSL.field(SERVICE_INSTANCES_LICENSE_DAILY_REPORT.REPORTED_DAY).desc())
          .limit(1)
          .fetchInto(ServiceInstancesLicenseDailyReport.class);
    } catch (Exception e) {
      log.warn("Exception while latest service instances license daily report for account {}", accountId, e);
      throw e;
    }
  }

  public List<ServicesLicenseDailyReport> getLatestServicesLicenseDailyReportByAccountId(@NotNull String accountId) {
    try {
      return dsl.select()
          .from(SERVICES_LICENSE_DAILY_REPORT)
          .where(SERVICES_LICENSE_DAILY_REPORT.ACCOUNT_ID.eq(accountId))
          .orderBy(DSL.field(SERVICES_LICENSE_DAILY_REPORT.REPORTED_DAY).desc())
          .limit(1)
          .fetchInto(ServicesLicenseDailyReport.class);
    } catch (Exception e) {
      log.warn("Exception while latest services license daily report for account {}", accountId, e);
      throw e;
    }
  }

  public List<ServiceInstancesLicenseDailyReport> listServiceInstancesLicenseDailyReportByAccountId(
      @NotNull String accountId, LocalDate fromDate, LocalDate toDate) {
    try {
      return dsl.select()
          .from(SERVICE_INSTANCES_LICENSE_DAILY_REPORT)
          .where(SERVICE_INSTANCES_LICENSE_DAILY_REPORT.ACCOUNT_ID.eq(accountId)
                     .and(SERVICE_INSTANCES_LICENSE_DAILY_REPORT.REPORTED_DAY.greaterOrEqual(fromDate))
                     .and(SERVICE_INSTANCES_LICENSE_DAILY_REPORT.REPORTED_DAY.lessOrEqual(toDate)))
          .orderBy(DSL.field(SERVICE_INSTANCES_LICENSE_DAILY_REPORT.REPORTED_DAY).asc())
          .fetchInto(ServiceInstancesLicenseDailyReport.class);
    } catch (Exception e) {
      log.warn("Exception while listing service instances license daily report for account {}", accountId, e);
      throw e;
    }
  }

  public List<ServicesLicenseDailyReport> listServicesLicenseDailyReportByAccountId(
      @NotNull String accountId, LocalDate fromDate, LocalDate toDate) {
    try {
      return dsl.select()
          .from(SERVICES_LICENSE_DAILY_REPORT)
          .where(SERVICES_LICENSE_DAILY_REPORT.ACCOUNT_ID.eq(accountId)
                     .and(SERVICES_LICENSE_DAILY_REPORT.REPORTED_DAY.greaterOrEqual(fromDate))
                     .and(SERVICES_LICENSE_DAILY_REPORT.REPORTED_DAY.lessOrEqual(toDate)))
          .orderBy(DSL.field(SERVICES_LICENSE_DAILY_REPORT.REPORTED_DAY).asc())
          .fetchInto(ServicesLicenseDailyReport.class);
    } catch (Exception e) {
      log.warn("Exception while listing services license daily report for account {}", accountId, e);
      throw e;
    }
  }

  public int insertBulkServiceInstancesLicenseDailyReport(
      String accountId, List<LicenseDailyUsage> licenseDailyReport) {
    if (isEmpty(licenseDailyReport)) {
      return 0;
    }
    if (licenseDailyReport.size() > BULK_INSERT_LIMIT) {
      throw new InvalidArgumentsException(String.format("Bulk insert records limit exceeded, %s", BULK_INSERT_LIMIT));
    }

    try {
      InsertValuesStep3<ServiceInstancesLicenseDailyReportRecord, String, LocalDate, Integer> bulkInsert =
          dsl.insertInto(SERVICE_INSTANCES_LICENSE_DAILY_REPORT, SERVICE_INSTANCES_LICENSE_DAILY_REPORT.ACCOUNT_ID,
              SERVICE_INSTANCES_LICENSE_DAILY_REPORT.REPORTED_DAY,
              SERVICE_INSTANCES_LICENSE_DAILY_REPORT.LICENSE_COUNT);

      licenseDailyReport.forEach(licenseDailyUsage -> {
        bulkInsert.values(
            licenseDailyUsage.getAccountId(), licenseDailyUsage.getReportedDay(), licenseDailyUsage.getLicenseCount());
      });

      return bulkInsert.onConflictOnConstraint(SERVICE_INSTANCES_LICENSE_DAILY_REPORT.getPrimaryKey())
          .doUpdate()
          .set(SERVICE_INSTANCES_LICENSE_DAILY_REPORT.LICENSE_COUNT,
              SERVICE_INSTANCES_LICENSE_DAILY_REPORT.as("excluded").LICENSE_COUNT)
          .execute();
    } catch (Exception e) {
      log.warn("Exception while bulk insert service instances license daily report for account {}", accountId, e);
      throw e;
    }
  }

  public int insertBulkServicesLicenseDailyReport(String accountId, List<LicenseDailyUsage> licenseDailyReport) {
    if (isEmpty(licenseDailyReport)) {
      return 0;
    }
    if (licenseDailyReport.size() > BULK_INSERT_LIMIT) {
      throw new InvalidArgumentsException(String.format("Balk insert records limit exceeded, %s", BULK_INSERT_LIMIT));
    }

    try {
      InsertValuesStep3<ServicesLicenseDailyReportRecord, String, LocalDate, Integer> bulkInsert =
          dsl.insertInto(SERVICES_LICENSE_DAILY_REPORT, SERVICES_LICENSE_DAILY_REPORT.ACCOUNT_ID,
              SERVICES_LICENSE_DAILY_REPORT.REPORTED_DAY, SERVICES_LICENSE_DAILY_REPORT.LICENSE_COUNT);

      licenseDailyReport.forEach(licenseDailyUsage -> {
        bulkInsert.values(
            licenseDailyUsage.getAccountId(), licenseDailyUsage.getReportedDay(), licenseDailyUsage.getLicenseCount());
      });

      return bulkInsert.onConflictOnConstraint(SERVICES_LICENSE_DAILY_REPORT.getPrimaryKey())
          .doUpdate()
          .set(SERVICES_LICENSE_DAILY_REPORT.LICENSE_COUNT, SERVICES_LICENSE_DAILY_REPORT.as("excluded").LICENSE_COUNT)
          .execute();
    } catch (Exception e) {
      log.warn("Exception while bulk insert services license daily report for account {}", accountId, e);
      throw e;
    }
  }
}
