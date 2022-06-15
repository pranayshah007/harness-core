/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.APPLICATION;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.APPLICATION_RELATED_COSTS;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.AVAILABLE;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.CLUSTER;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.CLUSTER_RELATED_COSTS;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.COST;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.COST_AVAILABLE;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.COST_CHANGE_PERCENT;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.COST_DIFF_AMOUNT;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.COST_TREND;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.DECREASE;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.ENVIRONMENT;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.FROM;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.ID;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.INCREASE;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.NAME;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.NAMESPACE;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.NOT_AVAILABLE;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.PARENT;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.SERVICE;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.TO;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.TOTAL_CLUSTER_COST;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.TOTAL_CLUSTER_IDLE_COST;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.TOTAL_CLUSTER_UNALLOCATED_COST;
import static io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper.WORKLOAD;

import static software.wings.sm.states.ApprovalState.JSON;

import io.harness.batch.processing.billing.timeseries.data.WeeklyReportEntityData;
import io.harness.batch.processing.billing.timeseries.helper.WeeklyReportTemplateHelper;
import io.harness.batch.processing.mail.CEMailNotificationService;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.ccm.communication.CECommunicationsServiceImpl;
import io.harness.ccm.communication.CESlackWebhookService;
import io.harness.ccm.communication.entities.CESlackWebhook;
import io.harness.ccm.communication.entities.CommunicationMedium;
import io.harness.ccm.communication.entities.CommunicationType;
import io.harness.rest.RestResponse;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.service.impl.instance.CloudToHarnessMappingServiceImpl;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.text.StrSubstitutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class WeeklyReportServiceImpl {
  @Autowired private TimeScaleDBService timeScaleDBService;
  @Autowired private CloudToHarnessMappingServiceImpl cloudToHarnessMappingService;
  @Autowired private WeeklyReportTemplateHelper templateHelper;
  @Autowired private CEMailNotificationService emailNotificationService;
  @Autowired private CECommunicationsServiceImpl ceCommunicationsService;
  @Autowired private AccountShardService accountShardService;
  @Autowired private CESlackWebhookService ceSlackWebhookService;

  private static final int MAX_RETRY_COUNT = 4;
  private static final long WEEK_IN_MILLISECONDS = 604800000L;
  private static final String DEFAULT_TIMEZONE = "GMT";
  private static final String DEFAULT_PLACEHOLDER = "-";
  private static final String DATE_PATTERN = "MM/dd";
  private static final String DATE_PATTERN_FOR_FILTER = "yyyy-MM-dd";

  private static final String TOTAL_CLUSTER_COST_PER_ACCOUNT =
      "SELECT CURRENT_WEEK.accountid AS ACCOUNT_ID, CURRENT_WEEK.accountid AS ENTITY, CURRENT_WEEK.accountid AS PARENT, 100*(CURRENT_WEEK.cost - PREV_WEEK.cost)/ PREV_WEEK.cost as COST_CHANGE, ABS(CURRENT_WEEK.cost - PREV_WEEK.cost) as COST_DIFF, CURRENT_WEEK.cost as CURRENT_WEEK_COST, PREV_WEEK.cost as PREV_WEEK_COST FROM ((SELECT SUM(t0.billingamount) AS COST, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE', 'K8S_POD_FARGATE') ) AND (t0.accountid IN %s) GROUP BY t0.accountid) CURRENT_WEEK INNER JOIN (SELECT SUM(t0.billingamount) AS COST, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE','K8S_POD_FARGATE') ) AND (t0.accountid IN %s) GROUP BY t0.accountid) PREV_WEEK ON PREV_WEEK.cost != 0 AND CURRENT_WEEK.accountid = PREV_WEEK.accountid );";

  private static final String TOTAL_CLUSTER_IDLE_COST_PER_ACCOUNT =
      "SELECT CURRENT_WEEK.accountid AS ACCOUNT_ID, CURRENT_WEEK.accountid AS ENTITY, CURRENT_WEEK.accountid AS PARENT, 100*(CURRENT_WEEK.idlecost - PREV_WEEK.idlecost)/ PREV_WEEK.idlecost as COST_CHANGE, ABS(CURRENT_WEEK.idlecost - PREV_WEEK.idlecost) as COST_DIFF, CURRENT_WEEK.idlecost as CURRENT_WEEK_COST, PREV_WEEK.idlecost as PREV_WEEK_COST FROM ((SELECT SUM(t0.actualidlecost) as IDLECOST, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE', 'K8S_POD_FARGATE') ) AND (t0.accountid IN %s) GROUP BY t0.accountid) CURRENT_WEEK INNER JOIN (SELECT SUM(t0.actualidlecost) as IDLECOST, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE','K8S_POD_FARGATE') ) AND (t0.accountid IN %s) GROUP BY t0.accountid) PREV_WEEK ON PREV_WEEK.idlecost != 0 AND CURRENT_WEEK.accountid = PREV_WEEK.accountid );";

  private static final String TOTAL_CLUSTER_UNALLOCATED_COST_PER_ACCOUNT =
      "SELECT CURRENT_WEEK.accountid AS ACCOUNT_ID, CURRENT_WEEK.accountid AS ENTITY, CURRENT_WEEK.accountid AS PARENT, 100*(CURRENT_WEEK.unallocatedcost - PREV_WEEK.unallocatedcost)/ PREV_WEEK.unallocatedcost as COST_CHANGE, ABS(CURRENT_WEEK.unallocatedcost - PREV_WEEK.unallocatedcost) as COST_DIFF, CURRENT_WEEK.unallocatedcost as CURRENT_WEEK_COST, PREV_WEEK.unallocatedcost as PREV_WEEK_COST FROM ((SELECT SUM(t0.unallocatedcost) as UNALLOCATEDCOST, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE', 'K8S_POD_FARGATE') ) AND (t0.accountid IN %s) GROUP BY t0.accountid) CURRENT_WEEK INNER JOIN (SELECT SUM(t0.unallocatedcost) as UNALLOCATEDCOST, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE','K8S_POD_FARGATE') ) AND (t0.accountid IN %s) GROUP BY t0.accountid) PREV_WEEK ON PREV_WEEK.unallocatedcost != 0 AND CURRENT_WEEK.accountid = PREV_WEEK.accountid );";

  /* The queries perform inner join of tables containing prev week and current week cost per entity(for example,
   cluster) per account and then selects the entity with highest cost change for each account */
  private static final String CLUSTER_COST_CHANGE_PER_ACCOUNT =
      "SELECT COST_CHANGE, COST_DIFF, CURRENT_WEEK_COST, PREV_WEEK_COST, clusterid AS ENTITY, clusterid AS PARENT, accountid AS ACCOUNT_ID FROM (SELECT ROW_NUMBER() OVER (PARTITION BY accountid ORDER BY COST_DIFF DESC, COST_CHANGE DESC) AS r,t.* FROM (SELECT CURRENT_WEEK.clusterid, CURRENT_WEEK.accountid, 100*(CURRENT_WEEK.cost - PREV_WEEK.cost)/ PREV_WEEK.cost as COST_CHANGE, ABS(CURRENT_WEEK.cost - PREV_WEEK.cost) as COST_DIFF, CURRENT_WEEK.cost as CURRENT_WEEK_COST, PREV_WEEK.cost as PREV_WEEK_COST FROM (SELECT SUM(t0.billingamount) AS COST, t0.clusterid, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE','K8S_POD_FARGATE') ) AND (t0.accountid IN %s) GROUP BY t0.clusterid, t0.accountid) CURRENT_WEEK INNER JOIN (SELECT SUM(t0.billingamount) AS COST, t0.clusterid, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE', 'K8S_POD_FARGATE') ) AND (t0.accountid IN %s) GROUP BY t0.clusterid, t0.accountid) PREV_WEEK ON PREV_WEEK.cost != 0 AND CURRENT_WEEK.clusterid = PREV_WEEK.clusterid AND CURRENT_WEEK.accountid = PREV_WEEK.accountid ) t) x where x.r <=1;";

  private static final String NAMESPACE_COST_CHANGE_PER_ACCOUNT =
      "SELECT COST_CHANGE, COST_DIFF, CURRENT_WEEK_COST, PREV_WEEK_COST, namespace AS ENTITY, clusterid AS PARENT, accountid AS ACCOUNT_ID FROM (SELECT ROW_NUMBER() OVER (PARTITION BY accountid ORDER BY COST_DIFF DESC, COST_CHANGE DESC) AS r,t.* FROM (SELECT CURRENT_WEEK.namespace, CURRENT_WEEK.clusterid, CURRENT_WEEK.accountid, 100*(CURRENT_WEEK.cost - PREV_WEEK.cost)/ PREV_WEEK.cost as COST_CHANGE, ABS(CURRENT_WEEK.cost - PREV_WEEK.cost) as COST_DIFF, CURRENT_WEEK.cost as CURRENT_WEEK_COST, PREV_WEEK.cost as PREV_WEEK_COST FROM (SELECT SUM(t0.billingamount) AS COST, t0.namespace, t0.clusterid, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.namespace IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.namespace NOT IN ('Unallocated') ) AND (t0.accountid IN %s) GROUP BY t0.namespace, t0.clusterid, t0.accountid) CURRENT_WEEK INNER JOIN (SELECT SUM(t0.billingamount) AS COST, t0.namespace, t0.clusterid, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.namespace IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.namespace NOT IN ('Unallocated')) AND (t0.accountid IN %s) GROUP BY t0.namespace, t0.clusterid, t0.accountid) PREV_WEEK ON PREV_WEEK.cost != 0 AND CURRENT_WEEK.namespace = PREV_WEEK.namespace AND CURRENT_WEEK.clusterid = PREV_WEEK.clusterid AND CURRENT_WEEK.accountid = PREV_WEEK.accountid ) t) x where x.r <=1;";

  private static final String WORKLOAD_COST_CHANGE_PER_ACCOUNT =
      "SELECT COST_CHANGE, COST_DIFF, CURRENT_WEEK_COST, PREV_WEEK_COST, workloadname AS ENTITY, clusterid AS PARENT, accountid AS ACCOUNT_ID FROM (SELECT ROW_NUMBER() OVER (PARTITION BY accountid ORDER BY COST_DIFF DESC, COST_CHANGE DESC) AS r,t.* FROM (SELECT CURRENT_WEEK.workloadname, CURRENT_WEEK.clusterid, CURRENT_WEEK.accountid, 100*(CURRENT_WEEK.cost - PREV_WEEK.cost)/ PREV_WEEK.cost as COST_CHANGE, ABS(CURRENT_WEEK.cost - PREV_WEEK.cost) as COST_DIFF, CURRENT_WEEK.cost as CURRENT_WEEK_COST, PREV_WEEK.cost as PREV_WEEK_COST FROM (SELECT SUM(t0.billingamount) AS COST, t0.workloadname, t0.namespace, t0.clusterid, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.workloadname IS NOT NULL) AND (t0.namespace IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.namespace NOT IN ('Unallocated') ) AND (t0.accountid IN %s) GROUP BY t0.workloadname, t0.namespace, t0.clusterid, t0.accountid) CURRENT_WEEK INNER JOIN (SELECT SUM(t0.billingamount) AS COST, t0.workloadname, t0.namespace, t0.clusterid, t0.accountid  FROM billing_data t0 WHERE (t0.clusterid IS NOT NULL) AND (t0.workloadname IS NOT NULL) AND (t0.namespace IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.namespace NOT IN ('Unallocated')) AND (t0.accountid IN %s) GROUP BY t0.workloadname, t0.namespace, t0.clusterid, t0.accountid) PREV_WEEK ON PREV_WEEK.cost != 0 AND CURRENT_WEEK.workloadname = PREV_WEEK.workloadname AND CURRENT_WEEK.namespace = PREV_WEEK.namespace AND CURRENT_WEEK.clusterid = PREV_WEEK.clusterid AND CURRENT_WEEK.accountid = PREV_WEEK.accountid ) t) x where x.r <=1;";

  private static final String APPLICATION_COST_CHANGE_PER_ACCOUNT =
      "SELECT COST_CHANGE, COST_DIFF, CURRENT_WEEK_COST, PREV_WEEK_COST, appid AS ENTITY, appid AS PARENT, accountid AS ACCOUNT_ID FROM (SELECT ROW_NUMBER() OVER (PARTITION BY accountid ORDER BY COST_DIFF DESC, COST_CHANGE DESC) AS r,t.* FROM (SELECT CURRENT_WEEK.appid, CURRENT_WEEK.accountid, 100*(CURRENT_WEEK.cost - PREV_WEEK.cost)/ PREV_WEEK.cost as COST_CHANGE, ABS(CURRENT_WEEK.cost - PREV_WEEK.cost) as COST_DIFF, CURRENT_WEEK.cost as CURRENT_WEEK_COST, PREV_WEEK.cost as PREV_WEEK_COST FROM (SELECT SUM(t0.billingamount) AS COST, t0.appid, t0.accountid  FROM billing_data t0 WHERE (t0.appid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.accountid IN %s) GROUP BY t0.appid, t0.accountid) CURRENT_WEEK INNER JOIN (SELECT SUM(t0.billingamount) AS COST, t0.appid, t0.accountid  FROM billing_data t0 WHERE (t0.appid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.accountid IN %s) GROUP BY t0.appid, t0.accountid) PREV_WEEK ON PREV_WEEK.cost != 0 AND CURRENT_WEEK.appid = PREV_WEEK.appid AND CURRENT_WEEK.accountid = PREV_WEEK.accountid ) t) x where x.r <=1;";

  private static final String SERVICE_COST_CHANGE_PER_ACCOUNT =
      "SELECT COST_CHANGE, COST_DIFF, CURRENT_WEEK_COST, PREV_WEEK_COST, serviceid AS ENTITY, appid as PARENT, accountid AS ACCOUNT_ID FROM (SELECT ROW_NUMBER() OVER (PARTITION BY accountid ORDER BY COST_DIFF DESC, COST_CHANGE DESC) AS r,t.* FROM (SELECT CURRENT_WEEK.serviceid, CURRENT_WEEK.appid, CURRENT_WEEK.accountid, 100*(CURRENT_WEEK.cost - PREV_WEEK.cost)/ PREV_WEEK.cost as COST_CHANGE, ABS(CURRENT_WEEK.cost - PREV_WEEK.cost) as COST_DIFF, CURRENT_WEEK.cost as CURRENT_WEEK_COST, PREV_WEEK.cost as PREV_WEEK_COST FROM (SELECT SUM(t0.billingamount) AS COST, t0.serviceid, t0.appid, t0.accountid  FROM billing_data t0 WHERE (t0.appid IS NOT NULL) AND (t0.serviceid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.accountid IN %s) GROUP BY t0.serviceid, t0.appid, t0.accountid) CURRENT_WEEK INNER JOIN (SELECT SUM(t0.billingamount) AS COST, t0.serviceid, t0.appid, t0.accountid  FROM billing_data t0 WHERE (t0.appid IS NOT NULL) AND (t0.serviceid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.accountid IN %s) GROUP BY t0.serviceid, t0.appid, t0.accountid) PREV_WEEK ON PREV_WEEK.cost != 0 AND CURRENT_WEEK.serviceid = PREV_WEEK.serviceid AND CURRENT_WEEK.appid = PREV_WEEK.appid AND CURRENT_WEEK.accountid = PREV_WEEK.accountid ) t) x where x.r <=1;";

  private static final String ENVIRONMENT_COST_CHANGE_PER_ACCOUNT =
      "SELECT COST_CHANGE, COST_DIFF, CURRENT_WEEK_COST, PREV_WEEK_COST, envid AS ENTITY, appid as PARENT, accountid AS ACCOUNT_ID FROM (SELECT ROW_NUMBER() OVER (PARTITION BY accountid ORDER BY COST_DIFF DESC, COST_CHANGE DESC) AS r,t.* FROM (SELECT CURRENT_WEEK.envid, CURRENT_WEEK.appid, CURRENT_WEEK.accountid, 100*(CURRENT_WEEK.cost - PREV_WEEK.cost)/ PREV_WEEK.cost as COST_CHANGE, ABS(CURRENT_WEEK.cost - PREV_WEEK.cost) as COST_DIFF, CURRENT_WEEK.cost as CURRENT_WEEK_COST, PREV_WEEK.cost as PREV_WEEK_COST FROM (SELECT SUM(t0.billingamount) AS COST, t0.envid, t0.appid, t0.accountid  FROM billing_data t0 WHERE (t0.appid IS NOT NULL) AND (t0.envid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.accountid IN %s) GROUP BY t0.envid, t0.appid, t0.accountid) CURRENT_WEEK INNER JOIN (SELECT SUM(t0.billingamount) AS COST, t0.envid, t0.appid, t0.accountid  FROM billing_data t0 WHERE (t0.appid IS NOT NULL) AND (t0.envid IS NOT NULL) AND (t0.starttime >= '%s') AND (t0.starttime <= '%s') AND (t0.accountid IN %s) GROUP BY t0.envid, t0.appid, t0.accountid) PREV_WEEK ON PREV_WEEK.cost != 0 AND CURRENT_WEEK.envid = PREV_WEEK.envid AND CURRENT_WEEK.appid = PREV_WEEK.appid AND CURRENT_WEEK.accountid = PREV_WEEK.accountid ) t) x where x.r <=1;";

  // Result set fields
  private static final String COST_CHANGE = "COST_CHANGE";
  private static final String COST_DIFF = "COST_DIFF";
  private static final String CURRENT_WEEK_COST = "CURRENT_WEEK_COST";
  private static final String ENTITY = "ENTITY";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String OVERVIEW_URL = "/account/%s/continuous-efficiency/overview";
  private static final String UNSUBSCRIBE_URL = "/api/ceMailUnsubscribe/%s";

  public static final String COMMUNICATION_MEDIUM = "utm_medium";

  public void generateAndSendWeeklyReport() {
    List<String> accountIds = accountShardService.getCeEnabledAccountIds();

    String currentWeekStartTime = getStartTime(getStartOfDayTimestamp(0)).toString();
    String currentWeekEndTime = getEndTime(getStartOfDayTimestamp(0)).toString();
    String prevWeekStartTime = getStartTime(getStartOfDayTimestamp(WEEK_IN_MILLISECONDS)).toString();
    String prevWeekEndTime = getEndTime(getStartOfDayTimestamp(WEEK_IN_MILLISECONDS)).toString();
    String accountIdFilterValue = getAccountIdFilterValue(accountIds);
    String from = getStartTime(getStartOfDayTimestamp(0))
                      .atZone(ZoneId.of(DEFAULT_TIMEZONE))
                      .format(DateTimeFormatter.ofPattern(DATE_PATTERN_FOR_FILTER));
    String to = getEndTime(getStartOfDayTimestamp(0))
                    .atZone(ZoneId.of(DEFAULT_TIMEZONE))
                    .format(DateTimeFormatter.ofPattern(DATE_PATTERN_FOR_FILTER));

    Map<String, WeeklyReportEntityData> totalClusterCostData =
        executeQuery(String.format(TOTAL_CLUSTER_COST_PER_ACCOUNT, currentWeekStartTime, currentWeekEndTime,
            accountIdFilterValue, prevWeekStartTime, prevWeekEndTime, accountIdFilterValue));

    Map<String, WeeklyReportEntityData> totalClusterIdleCostData =
        executeQuery(String.format(TOTAL_CLUSTER_IDLE_COST_PER_ACCOUNT, currentWeekStartTime, currentWeekEndTime,
            accountIdFilterValue, prevWeekStartTime, prevWeekEndTime, accountIdFilterValue));

    Map<String, WeeklyReportEntityData> totalClusterUnallocatedCostData =
        executeQuery(String.format(TOTAL_CLUSTER_UNALLOCATED_COST_PER_ACCOUNT, currentWeekStartTime, currentWeekEndTime,
            accountIdFilterValue, prevWeekStartTime, prevWeekEndTime, accountIdFilterValue));

    Map<String, WeeklyReportEntityData> clusterData =
        executeQuery(String.format(CLUSTER_COST_CHANGE_PER_ACCOUNT, currentWeekStartTime, currentWeekEndTime,
            accountIdFilterValue, prevWeekStartTime, prevWeekEndTime, accountIdFilterValue));

    Map<String, WeeklyReportEntityData> namespaceData =
        executeQuery(String.format(NAMESPACE_COST_CHANGE_PER_ACCOUNT, currentWeekStartTime, currentWeekEndTime,
            accountIdFilterValue, prevWeekStartTime, prevWeekEndTime, accountIdFilterValue));

    Map<String, WeeklyReportEntityData> workloadData =
        executeQuery(String.format(WORKLOAD_COST_CHANGE_PER_ACCOUNT, currentWeekStartTime, currentWeekEndTime,
            accountIdFilterValue, prevWeekStartTime, prevWeekEndTime, accountIdFilterValue));

    Map<String, WeeklyReportEntityData> applicationData =
        executeQuery(String.format(APPLICATION_COST_CHANGE_PER_ACCOUNT, currentWeekStartTime, currentWeekEndTime,
            accountIdFilterValue, prevWeekStartTime, prevWeekEndTime, accountIdFilterValue));

    Map<String, WeeklyReportEntityData> serviceData =
        executeQuery(String.format(SERVICE_COST_CHANGE_PER_ACCOUNT, currentWeekStartTime, currentWeekEndTime,
            accountIdFilterValue, prevWeekStartTime, prevWeekEndTime, accountIdFilterValue));

    Map<String, WeeklyReportEntityData> environmentData =
        executeQuery(String.format(ENVIRONMENT_COST_CHANGE_PER_ACCOUNT, currentWeekStartTime, currentWeekEndTime,
            accountIdFilterValue, prevWeekStartTime, prevWeekEndTime, accountIdFilterValue));

    String reportDateRange = getReportDateRange();

    accountIds.forEach(accountId -> {
      Map<String, String> enabledUsers =
          ceCommunicationsService.getUniqueIdPerUser(accountId, CommunicationType.WEEKLY_REPORT);
      List<String> emailIds = new ArrayList<>(enabledUsers.keySet());
      if (emailIds.isEmpty()) {
        return;
      }
      Map<String, String> templateModel = new HashMap<>();
      Map<String, String> costValues = new HashMap<>();
      costValues.put(ACCOUNT_ID, accountId);
      costValues.put(FROM, from);
      costValues.put(TO, to);
      costValues.put(APPLICATION_RELATED_COSTS, NOT_AVAILABLE);
      costValues.put(CLUSTER_RELATED_COSTS, NOT_AVAILABLE);
      // Total Cluster cost
      if (totalClusterCostData.containsKey(accountId)) {
        populateEntityPlaceholderValues(costValues, totalClusterCostData.get(accountId), TOTAL_CLUSTER_COST,
            BillingDataQueryMetadata.BillingDataMetaDataFields.CLUSTERID, false);
      } else {
        populateDefaultPlaceholderValues(costValues, TOTAL_CLUSTER_COST);
      }

      // Total Cluster idle cost
      if (totalClusterIdleCostData.containsKey(accountId)) {
        populateEntityPlaceholderValues(costValues, totalClusterIdleCostData.get(accountId), TOTAL_CLUSTER_IDLE_COST,
            BillingDataQueryMetadata.BillingDataMetaDataFields.CLUSTERID, false);
      } else {
        populateDefaultPlaceholderValues(costValues, TOTAL_CLUSTER_IDLE_COST);
      }

      // Total Cluster unallocated cost
      if (totalClusterUnallocatedCostData.containsKey(accountId)) {
        populateEntityPlaceholderValues(costValues, totalClusterUnallocatedCostData.get(accountId),
            TOTAL_CLUSTER_UNALLOCATED_COST, BillingDataQueryMetadata.BillingDataMetaDataFields.CLUSTERID, false);
      } else {
        populateDefaultPlaceholderValues(costValues, TOTAL_CLUSTER_UNALLOCATED_COST);
      }

      // Cluster cost
      if (clusterData.containsKey(accountId)) {
        populateEntityPlaceholderValues(costValues, clusterData.get(accountId), CLUSTER,
            BillingDataQueryMetadata.BillingDataMetaDataFields.CLUSTERID, true);
        costValues.put(CLUSTER_RELATED_COSTS, AVAILABLE);
      } else {
        populateDefaultPlaceholderValues(costValues, CLUSTER);
      }

      // Namespace cost
      if (namespaceData.containsKey(accountId)) {
        populateEntityPlaceholderValues(costValues, namespaceData.get(accountId), NAMESPACE,
            BillingDataQueryMetadata.BillingDataMetaDataFields.NAMESPACE, true);
        costValues.put(CLUSTER_RELATED_COSTS, AVAILABLE);
      } else {
        populateDefaultPlaceholderValues(costValues, NAMESPACE);
      }

      // Workload cost
      if (workloadData.containsKey(accountId)) {
        populateEntityPlaceholderValues(costValues, workloadData.get(accountId), WORKLOAD,
            BillingDataQueryMetadata.BillingDataMetaDataFields.WORKLOADNAME, true);
        costValues.put(CLUSTER_RELATED_COSTS, AVAILABLE);
      } else {
        populateDefaultPlaceholderValues(costValues, WORKLOAD);
      }

      // Application cost
      if (applicationData.containsKey(accountId)) {
        populateEntityPlaceholderValues(costValues, applicationData.get(accountId), APPLICATION,
            BillingDataQueryMetadata.BillingDataMetaDataFields.APPID, true);
        costValues.put(APPLICATION_RELATED_COSTS, AVAILABLE);
      } else {
        populateDefaultPlaceholderValues(costValues, APPLICATION);
      }

      // Service cost
      if (serviceData.containsKey(accountId)) {
        populateEntityPlaceholderValues(costValues, serviceData.get(accountId), SERVICE,
            BillingDataQueryMetadata.BillingDataMetaDataFields.SERVICEID, true);
        costValues.put(APPLICATION_RELATED_COSTS, AVAILABLE);
      } else {
        populateDefaultPlaceholderValues(costValues, SERVICE);
      }

      // Environment cost
      if (environmentData.containsKey(accountId)) {
        populateEntityPlaceholderValues(costValues, environmentData.get(accountId), ENVIRONMENT,
            BillingDataQueryMetadata.BillingDataMetaDataFields.ENVID, true);
        costValues.put(APPLICATION_RELATED_COSTS, AVAILABLE);
      } else {
        populateDefaultPlaceholderValues(costValues, ENVIRONMENT);
      }

      if (costValues.get(APPLICATION_RELATED_COSTS).equals(NOT_AVAILABLE)
          && costValues.get(CLUSTER_RELATED_COSTS).equals(NOT_AVAILABLE)) {
        return;
      }

      costValues.put(COMMUNICATION_MEDIUM, CommunicationMedium.EMAIL.getName());

      templateHelper.populateCostDataForTemplate(templateModel, costValues);

      templateModel.put("DATE", reportDateRange);
      costValues.put("DATE", reportDateRange);

      String explorerUrl = "";
      try {
        explorerUrl = templateHelper.buildAbsoluteUrl(String.format(OVERVIEW_URL, accountId));
      } catch (URISyntaxException e) {
        log.error("Error in forming Explorer URL for Weekly Report", e);
      }

      templateModel.put("url", explorerUrl);
      costValues.put("url", explorerUrl);

      emailIds.forEach(emailId -> {
        try {
          templateModel.put("UNSUBSCRIBE_URL",
              templateHelper.buildAbsoluteUrl(String.format(UNSUBSCRIBE_URL, enabledUsers.get(emailId))));
        } catch (URISyntaxException e) {
          log.error("Error in forming Explorer URL for Weekly Report", e);
        }

        EmailData emailData = EmailData.builder()
                                  .to(Collections.singletonList(emailId))
                                  .templateName("ce_weekly_report")
                                  .templateModel(templateModel)
                                  .accountId(accountId)
                                  .build();
        emailData.setCc(Collections.emptyList());
        emailData.setRetries(2);
        emailNotificationService.send(emailData);
      });

      // Sending report on Slack
      CESlackWebhook webhook = ceSlackWebhookService.getByAccountId(accountId);
      if (webhook.isSendCostReport()) {
        templateHelper.populateSlackUrls(costValues);
        try {
          sendReportOnSlack(costValues, webhook.getWebhookUrl());
        } catch (IOException e) {
          log.error("Error in sending report on slack", e);
        }
      }
    });
  }

  private Map<String, WeeklyReportEntityData> executeQuery(String query) {
    boolean successful = false;
    ResultSet resultSet;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successful && retryCount < MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             Statement statement = dbConnection.createStatement()) {
          log.info("Executing the following query in WeeklyReportServiceImpl : {} ", query);
          resultSet = statement.executeQuery(query);
          successful = true;
          return generateData(resultSet);
        } catch (SQLException e) {
          log.error("Failed to execute query in WeeklyReportServiceImpl ,[{}],retryCount=[{}], Exception: ", query,
              retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("Failed to execute query in WeeklyReportServiceImpl :[{}]", query);
    }
    return null;
  }

  private Map<String, WeeklyReportEntityData> generateData(ResultSet resultSet) throws SQLException {
    Map<String, WeeklyReportEntityData> data = new HashMap<>();
    String accountId;
    while (resultSet != null && resultSet.next()) {
      accountId = resultSet.getString(ACCOUNT_ID);
      Double costChange = resultSet.getDouble(COST_CHANGE);
      boolean costDecreased = costChange <= 0;
      if (costDecreased) {
        costChange *= -1;
      }
      data.put(accountId,
          WeeklyReportEntityData.builder()
              .id(resultSet.getString(ENTITY))
              .parent(resultSet.getString(PARENT))
              .cost(getRoundedDoubleValue(resultSet.getDouble(CURRENT_WEEK_COST)))
              .costChange(getRoundedDoubleValue(costChange))
              .costDiff(getRoundedDoubleValue(resultSet.getDouble(COST_DIFF)))
              .costDecreased(costDecreased)
              .build());
    }
    return data;
  }

  private Instant getStartTime(long timestamp) {
    return Instant.ofEpochMilli(timestamp - WEEK_IN_MILLISECONDS);
  }

  private Instant getEndTime(long timestamp) {
    return Instant.ofEpochMilli(timestamp - 1000);
  }

  private long getStartOfDayTimestamp(long offset) {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
    return (zdtStart.toEpochSecond() * 1000) - offset;
  }

  private String getAccountIdFilterValue(List<String> accountIds) {
    StringBuilder strBuilder = new StringBuilder("('");
    String prefix = "";
    for (String accountId : accountIds) {
      strBuilder.append(prefix);
      prefix = "','";
      strBuilder.append(accountId);
    }
    strBuilder.append("')");
    return strBuilder.toString();
  }

  private void populateEntityPlaceholderValues(Map<String, String> templateModel, WeeklyReportEntityData entityData,
      String entity, BillingDataQueryMetadata.BillingDataMetaDataFields field, boolean populateName) {
    NumberFormat formatter = NumberFormat.getInstance(Locale.US);
    String sign = entityData.isCostDecreased() ? "-" : "+";
    if (populateName) {
      templateModel.put(entity + NAME, cloudToHarnessMappingService.getEntityName(field, entityData.getId()));
    }
    templateModel.put(entity + ID, entityData.getId());
    templateModel.put(entity + PARENT, entityData.getParent());
    templateModel.put(entity + COST, "$" + formatter.format(entityData.getCost()));
    templateModel.put(entity + COST_CHANGE_PERCENT, sign + formatter.format(entityData.getCostChange()) + "%");
    templateModel.put(entity + COST_DIFF_AMOUNT, sign + "$" + formatter.format(entityData.getCostDiff()));
    templateModel.put(entity + COST_AVAILABLE, AVAILABLE);
    templateModel.put(entity + COST_TREND, entityData.isCostDecreased() ? DECREASE : INCREASE);
  }

  private void populateDefaultPlaceholderValues(Map<String, String> templateModel, String entity) {
    templateModel.put(entity + ID, DEFAULT_PLACEHOLDER);
    templateModel.put(entity + NAME, DEFAULT_PLACEHOLDER);
    templateModel.put(entity + PARENT, DEFAULT_PLACEHOLDER);
    templateModel.put(entity + COST, DEFAULT_PLACEHOLDER);
    templateModel.put(entity + COST_CHANGE_PERCENT, DEFAULT_PLACEHOLDER);
    templateModel.put(entity + COST_DIFF_AMOUNT, DEFAULT_PLACEHOLDER);
    templateModel.put(entity + COST_AVAILABLE, NOT_AVAILABLE);
    templateModel.put(entity + COST_TREND, DEFAULT_PLACEHOLDER);
  }

  private double getRoundedDoubleValue(double value) {
    return Math.round(value * 100D) / 100D;
  }

  private String getReportDateRange() {
    Instant startInstant = getStartTime(getStartOfDayTimestamp(0));
    Instant endInstant = getEndTime(getStartOfDayTimestamp(0));
    return startInstant.atZone(ZoneId.of(DEFAULT_TIMEZONE)).format(DateTimeFormatter.ofPattern(DATE_PATTERN)) + " to "
        + endInstant.atZone(ZoneId.of(DEFAULT_TIMEZONE)).format(DateTimeFormatter.ofPattern(DATE_PATTERN));
  }

  private boolean sendReportOnSlack(Map<String, String> values, String webhookUrl) throws IOException {
    URL templateUrl = this.getClass().getResource("/slack/weekly-report.json");
    String loadedTemplate = "";
    StrSubstitutor sub = new StrSubstitutor(values);
    try {
      loadedTemplate = Resources.toString(templateUrl, Charsets.UTF_8);
    } catch (IOException e) {
      log.error("Error in loading given template ", e);
    }
    RequestBody confirmationBody = RequestBody.create(JSON, sub.replace(loadedTemplate));
    slackPostRequest(confirmationBody, webhookUrl);
    return true;
  }

  private static RestResponse<Boolean> slackPostRequest(RequestBody body, String responseUrl) throws IOException {
    OkHttpClient client = new OkHttpClient();
    Request request1 = new Request.Builder()
                           .url(responseUrl)
                           .post(body)
                           .addHeader("Content-Type", "application/json")
                           .addHeader("Accept", "*/*")
                           .addHeader("Cache-Control", "no-cache")
                           .addHeader("Host", "hooks.slack.com")
                           .addHeader("accept-encoding", "gzip, deflate")
                           .addHeader("content-length", "798")
                           .addHeader("Connection", "keep-alive")
                           .addHeader("cache-control", "no-cache")
                           .build();

    try (Response response = client.newCall(request1).execute()) {
      if (!response.isSuccessful()) {
        log.error("Slack post request failed. Response code: {}", response.code());
      }
    }
    return new RestResponse<>();
  }
}
