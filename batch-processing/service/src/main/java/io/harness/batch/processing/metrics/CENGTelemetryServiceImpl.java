/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.metrics;

import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.commons.beans.recommendation.RecommendationTelemetryStats;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.utils.CCMLicenseUsageHelper;
import io.harness.ccm.views.dao.CEReportScheduleDao;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.entities.ViewType;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.remote.client.NGRestUtils;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit2.Call;

@Slf4j
@Singleton
public class CENGTelemetryServiceImpl implements CENGTelemetryService {
  @Autowired @Inject private ConnectorResourceClient connectorResourceClient;
  @Autowired @Inject private K8sRecommendationDAO k8sRecommendationDAO;
  @Autowired @Inject private CEViewDao ceViewDao;
  @Autowired @Inject private BudgetDao budgetDao;
  @Autowired @Inject private BudgetUtils budgetUtils;
  @Autowired @Inject private CEReportScheduleDao ceReportScheduleDao;
  @Autowired @Inject private NGConnectorHelper ngConnectorHelper;
  @Autowired @Inject private BigQueryService bigQueryService;
  @Autowired @Inject private BatchMainConfig config;
  @Autowired @Inject private NgLicenseHttpClient ngLicenseHttpClient;

  public static final String DATA_SET_NAME = "CE_INTERNAL";
  public static final String TABLE_NAME = "costAggregated";
  public static final String QUERY_TEMPLATE =
      "SELECT SUM(cost) AS cost, TIMESTAMP_TRUNC(day, month) AS month, cloudProvider FROM `%s` "
      + "WHERE day >= TIMESTAMP_MILLIS(%s) AND day <= TIMESTAMP_MILLIS(%s) AND accountId = '%s' GROUP BY month, cloudProvider";

  public HashMap<String, Object> getReportMetrics(String accountId) {
    HashMap<String, Object> properties = new HashMap<>();
    Set<String> uniquePerspectiveIds = new HashSet<>();
    List<CEReportSchedule> ceReportSchedules = ceReportScheduleDao.getAllByAccount(accountId);
    for (CEReportSchedule reportSchedule : ceReportSchedules) {
      uniquePerspectiveIds.addAll(Arrays.asList(reportSchedule.getViewsId()));
    }
    properties.put("ccm_total_report_schedules_count", ceReportSchedules.size());
    properties.put("ccm_perspective_count_with_report_enabled", uniquePerspectiveIds.size());
    return properties;
  }

  public HashMap<String, Object> getBudgetMetrics(String accountId) {
    HashMap<String, Object> properties = new HashMap<>();
    List<Budget> budgets = budgetDao.list(accountId);
    Set<String> uniquePerspectiveIds = new HashSet<>();

    for (Budget budget : budgets) {
      String perspectiveIdForBudget = budgetUtils.getPerspectiveIdForBudget(budget);
      if (perspectiveIdForBudget != budgetUtils.UNDEFINED_PERSPECTIVE) {
        uniquePerspectiveIds.add(perspectiveIdForBudget);
      }
    }

    properties.put("ccm_total_budget_count", budgets.size());
    properties.put("ccm_perspective_count_with_budget_enabled", uniquePerspectiveIds.size());

    return properties;
  }

  public HashMap<String, Object> getLicenseUtil(String accountIdentifier) {
    String gcpProjectId = config.getGcpConfig().getGcpProjectId();
    String cloudProviderTableName = format("%s.%s.%s", gcpProjectId, DATA_SET_NAME, TABLE_NAME);
    long endOfDay = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).toEpochMilli();
    long licenseStartTime = getLicenseStartTime(accountIdentifier);
    String query = format(QUERY_TEMPLATE, cloudProviderTableName, licenseStartTime, endOfDay, accountIdentifier);

    BigQuery bigQuery = bigQueryService.get();
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getActiveSpend for Account:{}, {}", accountIdentifier, e);
      Thread.currentThread().interrupt();
      return null;
    }
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("ccm_license_cloud_spend_used", CCMLicenseUsageHelper.computeDeduplicatedActiveSpend(result));
    properties.put("ccm_license_startTime", licenseStartTime);
    return properties;
  }

  private long getLicenseStartTime(String accountId) {
    try {
      Call<ResponseDTO<AccountLicenseDTO>> accountLicensesCall = ngLicenseHttpClient.getAccountLicensesDTO(accountId);
      AccountLicenseDTO accountLicenseDTO = NGRestUtils.getResponse(accountLicensesCall);
      return accountLicenseDTO.getAllModuleLicenses().get(ModuleType.CE).get(0).getStartTime();
    } catch (Exception ex) {
      log.error("Exception in fetching license startTime for accountId: {}", accountId, ex);
    }
    return 0L;
  }

  public HashMap<String, Object> getPerspectivesMetrics(String accountId) {
    HashMap<String, Object> properties = new HashMap<>();
    List<CEView> viewList = ceViewDao.findByAccountId(accountId, null);
    viewList = viewList.stream()
                   .filter(view -> ImmutableSet.of(ViewType.SAMPLE, ViewType.CUSTOMER).contains(view.getViewType()))
                   .filter(view -> ImmutableSet.of(ViewState.COMPLETED).contains(view.getViewState()))
                   .collect(Collectors.toList());
    int awsPerspectives = 0, gcpPerspectives = 0, azurePerspectives = 0, clusterPerspectives = 0;
    for (CEView view : viewList) {
      if (view.getDataSources() != null) {
        for (ViewFieldIdentifier datasource : view.getDataSources()) {
          if (datasource == ViewFieldIdentifier.AWS) {
            awsPerspectives += 1;
          } else if (datasource == ViewFieldIdentifier.GCP) {
            gcpPerspectives += 1;
          } else if (datasource == ViewFieldIdentifier.AZURE) {
            azurePerspectives += 1;
          } else if (datasource == ViewFieldIdentifier.CLUSTER) {
            clusterPerspectives += 1;
          }
        }
      }
    }

    properties.put("ccm_total_perspective_count", viewList.size());
    properties.put("ccm_aws_perspective_count", awsPerspectives);
    properties.put("ccm_gcp_perspective_count", gcpPerspectives);
    properties.put("ccm_azure_perspective_count", azurePerspectives);
    properties.put("ccm_cluster_perspective_count", clusterPerspectives);
    return properties;
  }

  public HashMap<String, Object> getRecommendationMetrics(String accountId) {
    HashMap<String, Object> properties = new HashMap<>();
    List<RecommendationTelemetryStats> recommendationTelemetryStatsList =
        k8sRecommendationDAO.fetchRecommendationsTelemetry(accountId);
    for (RecommendationTelemetryStats telemetryStat : recommendationTelemetryStatsList) {
      String recommendationType = telemetryStat.getType().toLowerCase();
      properties.put("ccm" + recommendationType + "_recommendation_count", telemetryStat.getCount());
      properties.put("ccm" + recommendationType + "_recommendation_spend", telemetryStat.getTotalMonthlyCost());
      properties.put("ccm" + recommendationType + "_recommendation_saving", telemetryStat.getTotalMonthlySaving());
    }
    List<RecommendationTelemetryStats> appliedRecommendationTelemetryStats =
        k8sRecommendationDAO.fetchAppliedRecommendationsTelemetry(accountId);
    for (RecommendationTelemetryStats telemetryStat : appliedRecommendationTelemetryStats) {
      String recommendationType = telemetryStat.getType().toLowerCase();
      properties.put("ccm" + recommendationType + "_applied_recommendation_count", telemetryStat.getCount());
      properties.put("ccm" + recommendationType + "_applied_recommendation_spend", telemetryStat.getTotalMonthlyCost());
      properties.put(
          "ccm" + recommendationType + "_applied_recommendation_saving", telemetryStat.getTotalMonthlySaving());
    }
    return properties;
  }

  public HashMap<String, Object> getNextGenConnectorsCountByType(String accountId) {
    List<ConnectorType> connectorTypes = Arrays.asList(ConnectorType.CE_KUBERNETES_CLUSTER, ConnectorType.CE_AWS,
        ConnectorType.CE_AZURE, ConnectorType.GCP_CLOUD_COST, ConnectorType.KUBERNETES_CLUSTER);
    List<ConnectorResponseDTO> nextGenConnectors = ngConnectorHelper.getNextGenConnectors(
        accountId, connectorTypes, Collections.emptyList(), Collections.emptyList());

    HashMap<String, Object> properties = new HashMap<>();

    // Total Count
    properties.put("ccm_aws_connector_count", getConnectorCount(nextGenConnectors, ConnectorType.CE_AWS));
    properties.put("ccm_azure_connector_count", getConnectorCount(nextGenConnectors, ConnectorType.CE_AZURE));
    properties.put("ccm_gcp_connector_count", getConnectorCount(nextGenConnectors, ConnectorType.GCP_CLOUD_COST));
    properties.put(
        "ccm_base_k8s_connector_count", getConnectorCount(nextGenConnectors, ConnectorType.KUBERNETES_CLUSTER));
    properties.put("ccm_cloud_cost_k8s_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.CE_KUBERNETES_CLUSTER));

    // Failed Connector Count
    properties.put("ccm_aws_failed_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.CE_AWS, ConnectivityStatus.FAILURE));
    properties.put("ccm_azure_failed_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.CE_AZURE, ConnectivityStatus.FAILURE));
    properties.put("ccm_gcp_failed_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.GCP_CLOUD_COST, ConnectivityStatus.FAILURE));
    properties.put("ccm_base_k8s_failed_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.KUBERNETES_CLUSTER, ConnectivityStatus.FAILURE));
    properties.put("ccm_cloud_cost_k8s_failed_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.CE_KUBERNETES_CLUSTER, ConnectivityStatus.FAILURE));
    return properties;
  }

  private long getConnectorCount(
      List<ConnectorResponseDTO> nextGenConnectors, ConnectorType connectorType, ConnectivityStatus status) {
    return nextGenConnectors.stream()
        .filter(connector
            -> connector.getConnector().getConnectorType() == connectorType
                && connector.getStatus().getStatus() == status)
        .count();
  }

  private long getConnectorCount(List<ConnectorResponseDTO> nextGenConnectors, ConnectorType connectorType) {
    return nextGenConnectors.stream()
        .filter(connector -> connector.getConnector().getConnectorType() == connectorType)
        .count();
  }
}
