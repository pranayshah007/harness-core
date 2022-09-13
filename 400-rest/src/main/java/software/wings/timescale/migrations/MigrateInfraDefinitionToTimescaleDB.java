/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.dl.WingsPersistence;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.AwsLambdaInfrastructure;
import software.wings.infra.AzureInstanceInfrastructure;
import software.wings.infra.AzureKubernetesService;
import software.wings.infra.AzureVMSSInfra;
import software.wings.infra.AzureWebAppInfra;
import software.wings.infra.CodeDeployInfrastructure;
import software.wings.infra.DirectKubernetesInfrastructure;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PcfInfraStructure;
import software.wings.infra.RancherKubernetesInfrastructure;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;

@Slf4j
@Singleton
public class MigrateInfraDefinitionToTimescaleDB {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;
  //  private static final int
  private static final String insert_statement =
      "INSERT INTO CG_INFRA_DEFINITION (ID,NAME,ACCOUNT_ID,APP_ID,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY,CLOUD_PROVIDER_ID,CLOUD_PROVIDER_TYPE,DEPLOYMENT_TYPE,NAMESPACE,REGION,AUTOSCALING_GROUP_NAME,RESOURCE_GROUP,RESOURCE_GROUP_NAME,SUBSCRIPTION_ID,DEPLOYMENT_GROUP,USERNAME,ORGANIZATION,CLUSTER_NAME) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  private static final String update_statement =
      "UPDATE CG_INFRA_DEFINITION SET NAME=?, ACCOUNT_ID=?, APP_ID=?, CREATED_AT=?, LAST_UPDATED_AT=?, CREATED_BY=?, LAST_UPDATED_BY=? ,CLOUD_PROVIDER_ID=? ,CLOUD_PROVIDER_TYPE=? ,DEPLOYMENT_TYPE=? ,NAMESPACE=? ,REGION=? ,AUTOSCALING_GROUP_NAME=? ,RESOURCE_GROUP=? ,RESOURCE_GROUP_NAME=? ,SUBSCRIPTION_ID=? ,DEPLOYMENT_GROUP=? ,USERNAME=? ,ORGANIZATION=? ,CLUSTER_NAME=? WHERE ID=?";

  private static final String query_statement = "SELECT * FROM CG_INFRA_DEFINITION WHERE ID=?";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_infra_definitions = new FindOptions();
      findOptions_infra_definitions.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<InfrastructureDefinition> iterator =
               new HIterator<>(wingsPersistence.createQuery(InfrastructureDefinition.class, excludeAuthority)
                                   .field(InfrastructureDefinition.InfrastructureDefinitionKeys.accountId)
                                   .equal(accountId)
                                   .fetch(findOptions_infra_definitions))) {
        while (iterator.hasNext()) {
          InfrastructureDefinition infrastructureDefinition = iterator.next();
          prepareTimeScaleQueries(infrastructureDefinition);
          count++;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to complete migration", e);
      return false;
    } finally {
      log.info("Completed migrating [{}] records", count);
    }
    return true;
  }

  private void prepareTimeScaleQueries(InfrastructureDefinition infrastructureDefinition) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;

      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement);
           PreparedStatement insertStatement = connection.prepareStatement(insert_statement)) {
        queryStatement.setString(1, infrastructureDefinition.getUuid());
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("Infrastructure Definition found in the timescaleDB:[{}],updating it",
              infrastructureDefinition.getUuid());
          updateDataInTimeScaleDB(infrastructureDefinition, connection, updateStatement);
        } else {
          log.info("Infrastructure Definition not found in the timescaleDB:[{}],inserting it",
              infrastructureDefinition.getUuid());
          insertDataInTimeScaleDB(infrastructureDefinition, connection, insertStatement);
        }
        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save Infrastructure Definition,[{}]", infrastructureDefinition.getUuid(), e);
        } else {
          log.info("Failed to save Infrastructure Definition,[{}],retryCount=[{}]", infrastructureDefinition.getUuid(),
              retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save Infrastructure Definition,[{}]", infrastructureDefinition.getUuid(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info("Total time =[{}] for Infrastructure Definition:[{}]", System.currentTimeMillis() - startTime,
            infrastructureDefinition.getUuid());
      }
    }
  }

  private void insertDataInTimeScaleDB(InfrastructureDefinition infrastructureDefinition, Connection connection,
      PreparedStatement insertPreparedStatement) throws SQLException {
    insertPreparedStatement.setString(1, infrastructureDefinition.getUuid());
    insertPreparedStatement.setString(2, infrastructureDefinition.getName());
    insertPreparedStatement.setString(3, infrastructureDefinition.getAccountId());
    insertPreparedStatement.setString(4, infrastructureDefinition.getAppId());
    insertPreparedStatement.setLong(5, infrastructureDefinition.getCreatedAt());
    insertPreparedStatement.setLong(6, infrastructureDefinition.getLastUpdatedAt());

    String created_by = null;
    if (infrastructureDefinition.getCreatedBy() != null) {
      created_by = infrastructureDefinition.getCreatedBy().getName();
    }
    insertPreparedStatement.setString(7, created_by);

    String last_updated_by = null;
    if (infrastructureDefinition.getLastUpdatedBy() != null) {
      last_updated_by = infrastructureDefinition.getLastUpdatedBy().getName();
    }
    insertPreparedStatement.setString(8, last_updated_by);
    insertPreparedStatement.setString(9, infrastructureDefinition.getInfrastructure().getCloudProviderId());
    insertPreparedStatement.setString(10, infrastructureDefinition.getInfrastructure().getCloudProviderType().name());
    insertPreparedStatement.setString(11, infrastructureDefinition.getDeploymentType().name());
    String mappingInfraClass = infrastructureDefinition.getInfrastructure().getClass().getName();
    String infraClass = mappingInfraClass.substring(mappingInfraClass.lastIndexOf('.') + 1);
    String namespace = null;
    String region = null;
    String autoScaling_group_name = null;
    String resource_group = null;
    String resource_group_name = null;
    String subscription_id = null;
    String deployment_group = null;
    String username = null;
    String organization = null;
    String cluster_name = null;
    switch (infraClass) {
      case "AzureKubernetesService":
        namespace
        = ((AzureKubernetesService) infrastructureDefinition.getInfrastructure()).getNamespace();
        resource_group = ((AzureKubernetesService) infrastructureDefinition.getInfrastructure()).getResourceGroup();
        subscription_id = ((AzureKubernetesService) infrastructureDefinition.getInfrastructure()).getSubscriptionId();
        cluster_name = ((AzureKubernetesService) infrastructureDefinition.getInfrastructure()).getClusterName();
        break;
      case "DirectKubernetesInfrastructure":
        namespace
        = ((DirectKubernetesInfrastructure) infrastructureDefinition.getInfrastructure()).getNamespace();
        cluster_name = ((DirectKubernetesInfrastructure) infrastructureDefinition.getInfrastructure()).getClusterName();
        break;
      case "GoogleKubernetesEngine":
        namespace
        = ((GoogleKubernetesEngine) infrastructureDefinition.getInfrastructure()).getNamespace();
        cluster_name = ((GoogleKubernetesEngine) infrastructureDefinition.getInfrastructure()).getClusterName();
        break;
      case "RancherKubernetesInfrastructure":
        namespace
        = ((RancherKubernetesInfrastructure) infrastructureDefinition.getInfrastructure()).getNamespace();
        break;
      case "PcfInfraStructure":
        organization = ((PcfInfraStructure) infrastructureDefinition.getInfrastructure()).getOrganization();
        break;
      case "CodeDeployInfrastructure":
        region = ((CodeDeployInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion();
        deployment_group =
            ((CodeDeployInfrastructure) infrastructureDefinition.getInfrastructure()).getDeploymentGroup();
        break;
      case "AzureWebAppInfra":
        resource_group = ((AzureWebAppInfra) infrastructureDefinition.getInfrastructure()).getResourceGroup();
        subscription_id = ((AzureWebAppInfra) infrastructureDefinition.getInfrastructure()).getSubscriptionId();
        break;
      case "AzureVMSSInfra":
        resource_group_name = ((AzureVMSSInfra) infrastructureDefinition.getInfrastructure()).getResourceGroupName();
        subscription_id = ((AzureVMSSInfra) infrastructureDefinition.getInfrastructure()).getSubscriptionId();
        username = ((AzureVMSSInfra) infrastructureDefinition.getInfrastructure()).getUserName();
        break;
      case "AzureInstanceInfrastructure":
        resource_group =
            ((AzureInstanceInfrastructure) infrastructureDefinition.getInfrastructure()).getResourceGroup();
        subscription_id =
            ((AzureInstanceInfrastructure) infrastructureDefinition.getInfrastructure()).getSubscriptionId();
        break;
      case "AwsLambdaInfrastructure":
        region = ((AwsLambdaInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion();
        break;
      case "AwsInstanceInfrastructure":
        region = ((AwsInstanceInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion();
        break;
      case "AwsEcsInfrastructure":
        region = ((AwsEcsInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion();
        cluster_name = ((AwsEcsInfrastructure) infrastructureDefinition.getInfrastructure()).getClusterName();
        break;
      case "AwsAmiInfrastructure":
        region = ((AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion();
        autoScaling_group_name =
            ((AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure()).getAutoScalingGroupName();
        break;
      default:
        break;
    }
    insertPreparedStatement.setString(12, namespace);
    insertPreparedStatement.setString(13, region);
    insertPreparedStatement.setString(14, autoScaling_group_name);
    insertPreparedStatement.setString(15, resource_group);
    insertPreparedStatement.setString(16, resource_group_name);
    insertPreparedStatement.setString(17, subscription_id);
    insertPreparedStatement.setString(18, deployment_group);
    insertPreparedStatement.setString(19, username);
    insertPreparedStatement.setString(20, organization);
    insertPreparedStatement.setString(21, cluster_name);

    insertPreparedStatement.execute();
  }
  private void updateDataInTimeScaleDB(InfrastructureDefinition infrastructureDefinition, Connection connection,
      PreparedStatement updateStatement) throws SQLException {
    updateStatement.setString(1, infrastructureDefinition.getUuid());
    updateStatement.setString(2, infrastructureDefinition.getName());
    updateStatement.setString(3, infrastructureDefinition.getAccountId());
    updateStatement.setString(4, infrastructureDefinition.getAppId());
    updateStatement.setLong(5, infrastructureDefinition.getCreatedAt());
    updateStatement.setLong(6, infrastructureDefinition.getLastUpdatedAt());

    String created_by = null;
    if (infrastructureDefinition.getCreatedBy() != null) {
      created_by = infrastructureDefinition.getCreatedBy().getName();
    }
    updateStatement.setString(7, created_by);

    String last_updated_by = null;
    if (infrastructureDefinition.getLastUpdatedBy() != null) {
      last_updated_by = infrastructureDefinition.getLastUpdatedBy().getName();
    }
    updateStatement.setString(8, last_updated_by);
    updateStatement.setString(9, infrastructureDefinition.getInfrastructure().getCloudProviderId());
    updateStatement.setString(10, infrastructureDefinition.getInfrastructure().getCloudProviderType().name());
    updateStatement.setString(11, infrastructureDefinition.getDeploymentType().name());
    String mappingInfraClass = infrastructureDefinition.getInfrastructure().getClass().getName();
    String infraClass = mappingInfraClass.substring(mappingInfraClass.lastIndexOf('.') + 1);
    String namespace = null;
    String region = null;
    String autoScaling_group_name = null;
    String resource_group = null;
    String resource_group_name = null;
    String subscription_id = null;
    String deployment_group = null;
    String username = null;
    String organization = null;
    String cluster_name = null;
    switch (infraClass) {
      case "AzureKubernetesService":
        namespace
        = ((AzureKubernetesService) infrastructureDefinition.getInfrastructure()).getNamespace();
        resource_group = ((AzureKubernetesService) infrastructureDefinition.getInfrastructure()).getResourceGroup();
        subscription_id = ((AzureKubernetesService) infrastructureDefinition.getInfrastructure()).getSubscriptionId();
        cluster_name = ((AzureKubernetesService) infrastructureDefinition.getInfrastructure()).getClusterName();
        break;
      case "DirectKubernetesInfrastructure":
        namespace
        = ((DirectKubernetesInfrastructure) infrastructureDefinition.getInfrastructure()).getNamespace();
        cluster_name = ((DirectKubernetesInfrastructure) infrastructureDefinition.getInfrastructure()).getClusterName();
        break;
      case "GoogleKubernetesEngine":
        namespace
        = ((GoogleKubernetesEngine) infrastructureDefinition.getInfrastructure()).getNamespace();
        cluster_name = ((GoogleKubernetesEngine) infrastructureDefinition.getInfrastructure()).getClusterName();
        break;
      case "RancherKubernetesInfrastructure":
        namespace
        = ((RancherKubernetesInfrastructure) infrastructureDefinition.getInfrastructure()).getNamespace();
        break;
      case "PcfInfraStructure":
        organization = ((PcfInfraStructure) infrastructureDefinition.getInfrastructure()).getOrganization();
        break;
      case "CodeDeployInfrastructure":
        region = ((CodeDeployInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion();
        deployment_group =
            ((CodeDeployInfrastructure) infrastructureDefinition.getInfrastructure()).getDeploymentGroup();
        break;
      case "AzureWebAppInfra":
        resource_group = ((AzureWebAppInfra) infrastructureDefinition.getInfrastructure()).getResourceGroup();
        subscription_id = ((AzureWebAppInfra) infrastructureDefinition.getInfrastructure()).getSubscriptionId();
        break;
      case "AzureVMSSInfra":
        resource_group_name = ((AzureVMSSInfra) infrastructureDefinition.getInfrastructure()).getResourceGroupName();
        subscription_id = ((AzureVMSSInfra) infrastructureDefinition.getInfrastructure()).getSubscriptionId();
        username = ((AzureVMSSInfra) infrastructureDefinition.getInfrastructure()).getUserName();
        break;
      case "AzureInstanceInfrastructure":
        resource_group =
            ((AzureInstanceInfrastructure) infrastructureDefinition.getInfrastructure()).getResourceGroup();
        subscription_id =
            ((AzureInstanceInfrastructure) infrastructureDefinition.getInfrastructure()).getSubscriptionId();
        break;
      case "AwsLambdaInfrastructure":
        region = ((AwsLambdaInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion();
        break;
      case "AwsInstanceInfrastructure":
        region = ((AwsInstanceInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion();
        break;
      case "AwsEcsInfrastructure":
        region = ((AwsEcsInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion();
        cluster_name = ((AwsEcsInfrastructure) infrastructureDefinition.getInfrastructure()).getClusterName();
        break;
      case "AwsAmiInfrastructure":
        region = ((AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure()).getRegion();
        autoScaling_group_name =
            ((AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure()).getAutoScalingGroupName();
        break;
      default:
        break;
    }
    updateStatement.setString(12, namespace);
    updateStatement.setString(13, region);
    updateStatement.setString(14, autoScaling_group_name);
    updateStatement.setString(15, resource_group);
    updateStatement.setString(16, resource_group_name);
    updateStatement.setString(17, subscription_id);
    updateStatement.setString(18, deployment_group);
    updateStatement.setString(19, username);
    updateStatement.setString(20, organization);
    updateStatement.setString(21, cluster_name);
    updateStatement.execute();
  }
}
