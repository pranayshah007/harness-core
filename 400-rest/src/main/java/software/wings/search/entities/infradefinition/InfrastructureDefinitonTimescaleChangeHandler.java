/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.infradefinition;

import static java.util.Arrays.asList;

import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.timescaledb.TimeScaleDBService;

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
import software.wings.search.SQLOperationHelper;
import software.wings.search.framework.ChangeHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class InfrastructureDefinitonTimescaleChangeHandler implements ChangeHandler {
  @Inject private TimeScaleDBService timeScaleDBService;

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent) {
    String tableName = "cg_infra_definition";

    switch (changeEvent.getChangeType()) {
      case INSERT:
        dbOperation(SQLOperationHelper.insertSQL(tableName, getColumnValueMapping(changeEvent)));
        break;
      case UPDATE:
        dbOperation(SQLOperationHelper.updateSQL(tableName, getColumnValueMapping(changeEvent),
            Collections.singletonMap("id", changeEvent.getUuid()), getPrimaryKeys()));
        break;
      case DELETE:
        dbOperation(SQLOperationHelper.deleteSQL(tableName, Collections.singletonMap("id", changeEvent.getUuid())));
        break;
      default:
        return false;
    }
    return true;
  }

  public List<String> getPrimaryKeys() {
    return asList("id");
  }

  public boolean dbOperation(String query) {
    boolean successfulOperation = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulOperation && retryCount < 5) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(query)) {
          statement.execute();
          successfulOperation = true;
        } catch (SQLException e) {
          log.error("Failed to save/update/delete data Query = {},retryCount=[{}], Exception: ", query, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("TimeScale Down");
    }
    return successfulOperation;
  }

  public Map<String, Object> getColumnValueMapping(ChangeEvent<?> changeEvent) {
    Map<String, Object> columnValueMapping = new HashMap<>();
    InfrastructureDefinition infrastructureDefinition = (InfrastructureDefinition) changeEvent.getFullDocument();

    if (changeEvent == null) {
      return null;
    }

    if (infrastructureDefinition == null) {
      return columnValueMapping;
    }

    if (infrastructureDefinition.getUuid() != null) {
      columnValueMapping.put("id", infrastructureDefinition.getUuid());
    }

    // name
    if (infrastructureDefinition.getName() != null) {
      columnValueMapping.put("name", infrastructureDefinition.getName());
    }

    // account_id
    if (infrastructureDefinition.getAccountId() != null) {
      columnValueMapping.put("account_id", infrastructureDefinition.getAccountId());
    }

    // app_id
    if (infrastructureDefinition.getAppId() != null) {
      columnValueMapping.put("app_id", infrastructureDefinition.getAppId());
    }

    // created_at
    columnValueMapping.put("created_at", infrastructureDefinition.getCreatedAt());

    // last_updated_at
    columnValueMapping.put("last_updated_at", infrastructureDefinition.getLastUpdatedAt());

    // created_by
    if (infrastructureDefinition.getCreatedBy() != null) {
      columnValueMapping.put("created_by", infrastructureDefinition.getCreatedBy().getName());
    }

    // last_updated_by
    if (infrastructureDefinition.getLastUpdatedBy() != null) {
      columnValueMapping.put("last_updated_by", infrastructureDefinition.getLastUpdatedBy().getName());
    }

    // cloudproviderid
    if (infrastructureDefinition.getInfrastructure().getCloudProviderId() != null) {
      columnValueMapping.put("cloud_provider_id", infrastructureDefinition.getInfrastructure().getCloudProviderId());
    }

    // cloudprovidertype
    if (infrastructureDefinition.getInfrastructure().getCloudProviderType().name() != null) {
      columnValueMapping.put(
          "cloud_provider_type", infrastructureDefinition.getInfrastructure().getCloudProviderType().name());
    }
    // deployment_type
    if (infrastructureDefinition.getDeploymentType().name() != null) {
      columnValueMapping.put("deployment_type", infrastructureDefinition.getDeploymentType().getDisplayName());
    }
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
    // namespace
    if (namespace != null) {
      columnValueMapping.put("namespace", namespace);
    }
    // region
    if (region != null) {
      columnValueMapping.put("region", region);
    }
    // autoScaling_group_name
    if (autoScaling_group_name != null) {
      columnValueMapping.put("autoScaling_group_name", autoScaling_group_name);
    }
    // resource_group
    if (resource_group != null) {
      columnValueMapping.put("resource_group", resource_group);
    }
    // resource_group_name
    if (resource_group_name != null) {
      columnValueMapping.put("resource_group_name", resource_group_name);
    }
    // subscription_id
    if (subscription_id != null) {
      columnValueMapping.put("subscription_id", subscription_id);
    }
    // deployment_group
    if (deployment_group != null) {
      columnValueMapping.put("deployment_group", deployment_group);
    }
    // username
    if (username != null) {
      columnValueMapping.put("username", username);
    }
    // organization
    if (organization != null) {
      columnValueMapping.put("organization", organization);
    }
    // cluster_name
    if (cluster_name != null) {
      columnValueMapping.put("cluster_name", cluster_name);
    }

    return columnValueMapping;
  }
}
