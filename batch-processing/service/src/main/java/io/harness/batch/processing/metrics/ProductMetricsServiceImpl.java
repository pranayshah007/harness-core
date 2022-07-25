/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.metrics;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.persistence.HQuery.excludeValidate;

import static software.wings.beans.SettingAttribute.SettingCategory.CE_CONNECTOR;
import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;
import static software.wings.settings.SettingVariableTypes.AWS;
import static software.wings.settings.SettingVariableTypes.CE_AWS;
import static software.wings.settings.SettingVariableTypes.KUBERNETES_CLUSTER;

import static java.lang.Math.toIntExact;
import static java.lang.String.format;

import io.harness.ccm.config.GcpBillingAccount;
import io.harness.ccm.config.GcpBillingAccount.GcpBillingAccountKeys;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Singleton
@Slf4j
public class ProductMetricsServiceImpl implements ProductMetricsService {
  private static final String TOTAL_CLUSTER_COST_QUERY =
      "SELECT SUM(billingamount) AS COST FROM billing_data t0 WHERE (t0.accountid = '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE','K8S_POD_FARGATE') ) AND starttime >= '%s' and starttime <'%s'";
  private static final String TOTAL_UNALLOCATED_COST_QUERY =
      "SELECT SUM(unallocatedcost) AS UNALLOCATEDCOST FROM billing_data t0 WHERE (t0.accountid = '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE','K8S_POD_FARGATE') ) AND starttime >= '%s' and starttime < '%s'";
  private static final String TOTAL_IDLE_COST_QUERY =
      "SELECT SUM(actualIdleCost) AS IDLECOST FROM billing_data t0 WHERE (t0.accountid = '%s') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE','K8S_NODE','K8S_POD_FARGATE') ) AND starttime >= '%s' and starttime < '%s'";
  private static final String TOTAL_K8S_SPEND_IN_CE_QUERY =
      "SELECT SUM(billingamount) AS COST FROM billing_data t0 WHERE (t0.accountid = '%s') AND (t0.clustertype = 'K8S') AND (t0.instancetype IN ('K8S_NODE','K8S_POD_FARGATE') ) AND starttime >= '%s' and starttime < '%s'";
  private static final String TOTAL_ECS_SPEND_IN_CE_QUERY =
      "SELECT SUM(billingamount) AS COST FROM billing_data t0 WHERE (t0.accountid = '%s') AND (t0.clustertype = 'AWS') AND (t0.instancetype IN ('ECS_TASK_FARGATE','ECS_CONTAINER_INSTANCE') ) AND starttime >= '%s' and starttime < '%s'";
  private static final String TOTAL_K8S_NAMESPACES_QUERY =
      "SELECT COUNT(DISTINCT \"namespace\") AS NUM FROM billing_data t0 WHERE (t0.accountid = '%s') AND (t0.clustertype = 'K8S') AND starttime >= '%s' and starttime < '%s'";
  private static final String TOTAL_K8S_WORKFLOWS_QUERY =
      "SELECT COUNT(DISTINCT \"workloadname\") AS NUM FROM billing_data t0 WHERE (t0.accountid = '%s') AND (t0.clustertype = 'K8S') AND starttime >= '%s' and starttime <'%s'";
  private static final String TOTAL_K8S_NODES_QUERY =
      "SELECT COUNT(DISTINCT \"instanceid\") AS NUM FROM billing_data t0 WHERE instancetype='K8S_NODE' AND (t0.accountid = '%s') AND (t0.clustertype = 'K8S') AND starttime >= '%s' and starttime < '%s'";
  private static final String TOTAL_K8S_PODS_QUERY =
      "SELECT COUNT(DISTINCT \"instanceid\") AS NUM FROM billing_data t0 WHERE instancetype IN ('K8S_POD', 'K8S_POD_FARGATE') AND (t0.accountid = '%s') AND (t0.clustertype = 'K8S') AND starttime >= '%s' and starttime < '%s'";

  private static final String TOTAL_ECS_CLUSTERS_QUERY =
      "SELECT COUNT(DISTINCT \"clusterid\") AS NUM FROM billing_data t0 WHERE (t0.accountid = '%s') AND (t0.clustertype = 'AWS') AND starttime >= '%s' and starttime < '%s'";
  private static final String TOTAL_ECS_TASKS_QUERY =
      "SELECT COUNT(DISTINCT \"taskid\") AS NUM FROM billing_data t0 WHERE (t0.accountid = '%s') AND (t0.clustertype = 'AWS') AND starttime >= '%s' and starttime < '%s'";

  private static final int MAX_RETRY = 3;

  @Autowired @Inject private HPersistence persistence;
  @Autowired @Inject private TimeScaleDBService timeScaleDBService;

  @Override
  public int countGcpBillingAccounts(String accountId) {
    return toIntExact(persistence.createQuery(GcpBillingAccount.class, excludeAuthority)
                          .filter(GcpBillingAccountKeys.accountId, accountId)
                          .count());
  }

  @Override
  public int countAwsBillingAccounts(String accountId) {
    return toIntExact(persistence.createQuery(SettingAttribute.class, excludeValidate)
                          .filter(SettingAttributeKeys.accountId, accountId)
                          .filter(SettingAttributeKeys.category, CE_CONNECTOR)
                          .filter(SettingAttributeKeys.value_type, CE_AWS)
                          .count());
  }

  @Override
  public int countAwsCloudProviderInCd(String accountId) {
    return toIntExact(persistence.createQuery(SettingAttribute.class, excludeValidate)
                          .filter(SettingAttributeKeys.accountId, accountId)
                          .filter(SettingAttributeKeys.category, CLOUD_PROVIDER)
                          .filter(SettingAttributeKeys.value_type, AWS)
                          .count());
  }

  @Override
  public int countAwsCloudProviderInCe(String accountId) {
    return toIntExact(persistence.createQuery(SettingAttribute.class, excludeValidate)
                          .filter(SettingAttributeKeys.accountId, accountId)
                          .filter(SettingAttributeKeys.category, CLOUD_PROVIDER)
                          .filter(SettingAttributeKeys.value_type, AWS)
                          .filter(SettingAttributeKeys.isCEEnabled, Boolean.TRUE)
                          .count());
  }

  @Override
  public int countK8sClusterInCd(String accountId) {
    return toIntExact(persistence.createQuery(SettingAttribute.class, excludeAuthority)
                          .filter(SettingAttributeKeys.accountId, accountId)
                          .filter(SettingAttributeKeys.value_type, KUBERNETES_CLUSTER)
                          .filter(SettingAttributeKeys.category, CLOUD_PROVIDER)
                          .count());
  }

  @Override
  public int countK8sClusterInCe(String accountId) {
    return toIntExact(persistence.createQuery(SettingAttribute.class, excludeValidate)
                          .filter(SettingAttributeKeys.accountId, accountId)
                          .filter(SettingAttributeKeys.category, CLOUD_PROVIDER)
                          .filter(SettingAttributeKeys.value_type, KUBERNETES_CLUSTER)
                          .filter(SettingAttributeKeys.isCEEnabled, Boolean.TRUE)
                          .count());
  }

  @Override
  public double getTotalClusterCost(String accountId, Instant start, Instant end) {
    double totalClusterCost = 0;
    String query = format(TOTAL_CLUSTER_COST_QUERY, accountId, start, end);
    ResultSet resultSet = null;

    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query);
        successful = true;
        while (resultSet.next()) {
          totalClusterCost = resultSet.getDouble("COST");
        }
      } catch (SQLException e) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query to getTotalClusterCost , max retry count reached, query=[{}], accountId=[{}]",
              query, accountId, e);
        } else {
          log.error("Error while fetching total cluster cost : exception", e);
        }
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return totalClusterCost;
  }

  @Override
  public double getTotalUnallocatedCost(String accountId, Instant start, Instant end) {
    double totalUnallocatedCost = 0;
    String query = format(TOTAL_UNALLOCATED_COST_QUERY, accountId, start, end);
    ResultSet resultSet = null;

    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query);
        successful = true;
        while (resultSet.next()) {
          totalUnallocatedCost = resultSet.getDouble("UNALLOCATEDCOST");
        }
      } catch (SQLException e) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query to getTotalUnallocatedCost , max retry count reached, query=[{}], accountId=[{}]",
              query, accountId, e);
        } else {
          log.error("Error while fetching total unallocated cost : exception", e);
        }
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return totalUnallocatedCost;
  }

  @Override
  public double getOverallUnallocatedCostPercentage(String accountId, Instant start, Instant end) {
    double totalClusterCost = getTotalClusterCost(accountId, start, end);
    double overallUnallocatedCostPercentage = 0;
    if (totalClusterCost != 0) {
      overallUnallocatedCostPercentage = BigDecimal.valueOf(getTotalUnallocatedCost(accountId, start, end))
                                             .divide(BigDecimal.valueOf(totalClusterCost), 4, RoundingMode.HALF_UP)
                                             .multiply(BigDecimal.valueOf(100))
                                             .doubleValue();
    }
    return overallUnallocatedCostPercentage;
  }

  @Override
  public double getOverallIdleCostPercentage(String accountId, Instant start, Instant end) {
    double totalClusterCost = getTotalClusterCost(accountId, start, end);
    double overallIdleCostPercentage = 0;
    if (totalClusterCost != 0) {
      overallIdleCostPercentage = BigDecimal.valueOf(getTotalIdleCost(accountId, start, end))
                                      .divide(BigDecimal.valueOf(totalClusterCost), 4, RoundingMode.HALF_UP)
                                      .multiply(BigDecimal.valueOf(100))
                                      .doubleValue();
    }
    return overallIdleCostPercentage;
  }

  @Override
  public double getTotalIdleCost(String accountId, Instant start, Instant end) {
    double totalIdleCost = 0;
    String query = format(TOTAL_IDLE_COST_QUERY, accountId, start, end);
    ResultSet resultSet = null;

    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query);
        successful = true;
        while (resultSet.next()) {
          totalIdleCost = resultSet.getDouble("IDLECOST");
        }
      } catch (SQLException e) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query to getTotalUnallocatedCost , max retry count reached, query=[{}], accountId=[{}]",
              query, accountId, e);
        } else {
          log.error("Error while fetching total idle cost : exception", e);
        }
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return totalIdleCost;
  }

  @Override
  public double getTotalK8sSpendInCe(String accountId, Instant start, Instant end) {
    double totalK8sSpendInCe = 0;
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      String query = format(TOTAL_K8S_SPEND_IN_CE_QUERY, accountId, start, end);
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        totalK8sSpendInCe = resultSet.getDouble("COST");
      }
    } catch (SQLException e) {
      log.error("Error while fetching Common Fields : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return totalK8sSpendInCe;
  }

  @Override
  public double getTotalEcsSpendInCe(String accountId, Instant start, Instant end) {
    double totalEcsSpendInCe = 0;
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      String query = format(TOTAL_ECS_SPEND_IN_CE_QUERY, accountId, start, end);
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        totalEcsSpendInCe = resultSet.getDouble("COST");
      }
    } catch (SQLException e) {
      log.error("Error while fetching Common Fields : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return totalEcsSpendInCe;
  }

  @Override
  public double countTotalK8sNamespaces(String accountId, Instant start, Instant end) {
    double totalK8sNamespaces = 0;
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      String query = format(TOTAL_K8S_NAMESPACES_QUERY, accountId, start, end);
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        totalK8sNamespaces = resultSet.getDouble("NUM");
      }
    } catch (SQLException e) {
      log.error("Error while fetching Common Fields : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return totalK8sNamespaces;
  }

  @Override
  public double countTotalK8sWorkloads(String accountId, Instant start, Instant end) {
    double totalK8sWorkflows = 0;
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      String query = format(TOTAL_K8S_WORKFLOWS_QUERY, accountId, start, end);
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        totalK8sWorkflows = resultSet.getDouble("NUM");
      }
    } catch (SQLException e) {
      log.error("Error while fetching Common Fields : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return totalK8sWorkflows;
  }

  @Override
  public double countTotalK8sNodes(String accountId, Instant start, Instant end) {
    double totalK8sWorkflows = 0;
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      String query = format(TOTAL_K8S_NODES_QUERY, accountId, start, end);
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        totalK8sWorkflows = resultSet.getDouble("NUM");
      }
    } catch (SQLException e) {
      log.error("Error while fetching Common Fields : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return totalK8sWorkflows;
  }

  @Override
  public double countTotalK8sPods(String accountId, Instant start, Instant end) {
    double totalK8sWorkflows = 0;
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      String query = format(TOTAL_K8S_PODS_QUERY, accountId, start, end);
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        totalK8sWorkflows = resultSet.getDouble("NUM");
      }
    } catch (SQLException e) {
      log.error("Error while fetching Common Fields : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return totalK8sWorkflows;
  }

  @Override
  public double countTotalEcsClusters(String accountId, Instant start, Instant end) {
    double totalEcsClusters = 0;
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      String query = format(TOTAL_ECS_CLUSTERS_QUERY, accountId, start, end);
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        totalEcsClusters = resultSet.getDouble("NUM");
      }
    } catch (SQLException e) {
      log.error("Error while fetching Common Fields : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return totalEcsClusters;
  }

  @Override
  public double countTotalEcsTasks(String accountId, Instant start, Instant end) {
    double totalEcsTasks = 0;
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      String query = format(TOTAL_ECS_TASKS_QUERY, accountId, start, end);
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        totalEcsTasks = resultSet.getDouble("NUM");
      }
    } catch (SQLException e) {
      log.error("Error while fetching Common Fields : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return totalEcsTasks;
  }
}
