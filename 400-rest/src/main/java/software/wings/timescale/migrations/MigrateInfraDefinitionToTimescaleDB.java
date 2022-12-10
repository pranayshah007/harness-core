/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.timescale.migrations.TimescaleEntityMigrationHelper.deleteFromTimescaleDB;

import io.harness.persistence.HIterator;
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
import software.wings.infra.InfrastructureDefinition.InfrastructureDefinitionKeys;
import software.wings.infra.PcfInfraStructure;
import software.wings.infra.RancherKubernetesInfrastructure;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;

@Slf4j
@Singleton
public class MigrateInfraDefinitionToTimescaleDB implements TimeScaleEntityMigrationInterface {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String upsert_statement =
      "INSERT INTO CG_INFRA_DEFINITION (ID,NAME,ACCOUNT_ID,APP_ID,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY,CLOUD_PROVIDER_ID,CLOUD_PROVIDER_TYPE,DEPLOYMENT_TYPE,NAMESPACE,REGION,AUTOSCALING_GROUP_NAME,RESOURCE_GROUP,RESOURCE_GROUP_NAME,SUBSCRIPTION_ID,DEPLOYMENT_GROUP,USERNAME,ORGANIZATION,CLUSTER_NAME,ENV_ID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(ID) DO UPDATE  SET NAME = excluded.NAME,ACCOUNT_ID = excluded.ACCOUNT_ID,APP_ID = excluded.APP_ID,CREATED_AT = excluded.CREATED_AT,LAST_UPDATED_AT = excluded.LAST_UPDATED_AT,CREATED_BY = excluded.CREATED_BY,LAST_UPDATED_BY = excluded.LAST_UPDATED_BY,CLOUD_PROVIDER_ID = excluded.CLOUD_PROVIDER_ID,CLOUD_PROVIDER_TYPE = excluded.CLOUD_PROVIDER_TYPE,DEPLOYMENT_TYPE = excluded.DEPLOYMENT_TYPE,NAMESPACE = excluded.NAMESPACE,REGION = excluded.REGION,AUTOSCALING_GROUP_NAME = excluded.AUTOSCALING_GROUP_NAME,RESOURCE_GROUP = excluded.RESOURCE_GROUP,RESOURCE_GROUP_NAME = excluded.RESOURCE_GROUP_NAME,SUBSCRIPTION_ID = excluded.SUBSCRIPTION_ID,DEPLOYMENT_GROUP = excluded.DEPLOYMENT_GROUP,USERNAME = excluded.USERNAME,ORGANIZATION = excluded.ORGANIZATION,CLUSTER_NAME = excluded.CLUSTER_NAME,ENV_ID = excluded.ENV_ID;";

  private static final String TABLE_NAME = "CG_INFRA_DEFINITION";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB for CG_INFRA_DEFINITION");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_infra_definitions = new FindOptions();
      findOptions_infra_definitions.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<InfrastructureDefinition> iterator =
               new HIterator<>(wingsPersistence.createAnalyticsQuery(InfrastructureDefinition.class, excludeAuthority)
                                   .field(InfrastructureDefinitionKeys.accountId)
                                   .equal(accountId)
                                   .fetch(findOptions_infra_definitions))) {
        while (iterator.hasNext()) {
          InfrastructureDefinition infrastructureDefinition = iterator.next();
          saveToTimeScale(infrastructureDefinition);
          count++;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to complete migration for CG_INFRA_DEFINITION", e);
      return false;
    } finally {
      log.info("Completed migrating [{}] records for CG_INFRA_DEFINITION", count);
    }
    return true;
  }

  @Override
  public String getTimescaleDBClass() {
    return TABLE_NAME;
  }

  @Override
  public void deleteFromTimescale(String id) {
    deleteFromTimescaleDB(id, timeScaleDBService, MAX_RETRY, TABLE_NAME);
  }

  public void saveToTimeScale(InfrastructureDefinition infrastructureDefinition) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement upsertStatement = connection.prepareStatement(upsert_statement)) {
        upsertDataInTimeScaleDB(infrastructureDefinition, connection, upsertStatement);
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
        log.info("Total time =[{}] for Infrastructure Definition:[{}]", System.currentTimeMillis() - startTime,
            infrastructureDefinition.getUuid());
      }
    }
  }

  private void upsertDataInTimeScaleDB(InfrastructureDefinition infrastructureDefinition, Connection connection,
      PreparedStatement upsertPreparedStatement) throws SQLException {
    upsertPreparedStatement.setString(1, infrastructureDefinition.getUuid());
    upsertPreparedStatement.setString(2, infrastructureDefinition.getName());
    upsertPreparedStatement.setString(3, infrastructureDefinition.getAccountId());
    upsertPreparedStatement.setString(4, infrastructureDefinition.getAppId());
    upsertPreparedStatement.setLong(5, infrastructureDefinition.getCreatedAt());
    upsertPreparedStatement.setLong(6, infrastructureDefinition.getLastUpdatedAt());

    String created_by = null;
    if (infrastructureDefinition.getCreatedBy() != null) {
      created_by = infrastructureDefinition.getCreatedBy().getName();
    }
    upsertPreparedStatement.setString(7, created_by);

    String last_updated_by = null;
    if (infrastructureDefinition.getLastUpdatedBy() != null) {
      last_updated_by = infrastructureDefinition.getLastUpdatedBy().getName();
    }
    upsertPreparedStatement.setString(8, last_updated_by);
    upsertPreparedStatement.setString(9, infrastructureDefinition.getInfrastructure().getCloudProviderId());
    upsertPreparedStatement.setString(10, infrastructureDefinition.getInfrastructure().getCloudProviderType().name());
    upsertPreparedStatement.setString(11, infrastructureDefinition.getDeploymentType().name());
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
    upsertPreparedStatement.setString(12, namespace);
    upsertPreparedStatement.setString(13, region);
    upsertPreparedStatement.setString(14, autoScaling_group_name);
    upsertPreparedStatement.setString(15, resource_group);
    upsertPreparedStatement.setString(16, resource_group_name);
    upsertPreparedStatement.setString(17, subscription_id);
    upsertPreparedStatement.setString(18, deployment_group);
    upsertPreparedStatement.setString(19, username);
    upsertPreparedStatement.setString(20, organization);
    upsertPreparedStatement.setString(21, cluster_name);
    upsertPreparedStatement.setString(22, infrastructureDefinition.getEnvId());

    upsertPreparedStatement.execute();
  }
}
