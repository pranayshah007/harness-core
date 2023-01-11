/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.setup.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.health.CEClusterHealth;
import io.harness.ccm.health.CEHealthStatus;
import io.harness.ccm.health.HealthStatusServiceImpl;
import io.harness.ccm.setup.graphql.QLCEConnector.QLCEConnectorBuilder;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.beans.ce.CEAzureConfig;
import software.wings.beans.ce.CEGcpConfig;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class CeConnectorDataFetcher
    extends AbstractConnectionV2DataFetcher<QLCESetupFilter, QLNoOpSortCriteria, QLCEConnectorData> {
  @Inject private CESetupQueryHelper ceSetupQueryHelper;
  @Inject private HealthStatusServiceImpl healthStatusService;

  private static final String ERROR_FETCHING_HEALTH_STATUS = "Error Fetching Health Status";

  @Override
  @AuthRule(permissionType = LOGGED_IN)
  protected QLCEConnectorData fetchConnection(
      List<QLCESetupFilter> filters, QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<SettingAttribute> query = populateFilters(wingsPersistence, filters, SettingAttribute.class, true)
                                        .filter(SettingAttributeKeys.category, SettingCategory.CE_CONNECTOR);

    final List<SettingAttribute> settingAttributes = query.asList();
    List<QLCEConnector> connectorList = new ArrayList<>();
    for (SettingAttribute settingAttribute : settingAttributes) {
      connectorList.add(populateCEConnector(settingAttribute));
    }

    return QLCEConnectorData.builder().ceConnectors(connectorList).build();
  }

  private QLCEConnector populateCEConnector(SettingAttribute settingAttribute) {
    CEHealthStatus healthStatus = CEHealthStatus.builder()
                                      .isHealthy(false)
                                      .clusterHealthStatusList(Collections.singletonList(
                                          CEClusterHealth.builder()
                                              .isHealthy(false)
                                              .messages(Arrays.asList(ERROR_FETCHING_HEALTH_STATUS))
                                              .build()))
                                      .build();
    try {
      healthStatus = healthStatusService.getHealthStatus(settingAttribute.getUuid());
    } catch (Exception ex) {
      log.error("Error Fetching Health Status: {}", ex);
    }
    String accountName = settingAttribute.getName();
    QLCEConnectorBuilder qlCEConnectorBuilder = QLCEConnector.builder()
                                                    .settingId(settingAttribute.getUuid())
                                                    .accountName(accountName)
                                                    .ceHealthStatus(healthStatus);
    if (settingAttribute.getValue() instanceof CEAwsConfig) {
      CEAwsConfig ceAwsConfig = (CEAwsConfig) settingAttribute.getValue();
      qlCEConnectorBuilder.curReportName(ceAwsConfig.getCurReportName())
          .s3BucketName(ceAwsConfig.getS3BucketDetails().getS3BucketName())
          .crossAccountRoleArn(ceAwsConfig.getAwsCrossAccountAttributes().getCrossAccountRoleArn())
          .infraType(QLInfraTypesEnum.AWS);
    } else if (settingAttribute.getValue() instanceof CEGcpConfig) {
      qlCEConnectorBuilder.infraType(QLInfraTypesEnum.GCP);
    } else if (settingAttribute.getValue() instanceof CEAzureConfig) {
      CEAzureConfig ceAzureConfig = (CEAzureConfig) settingAttribute.getValue();
      qlCEConnectorBuilder.azureStorageAccountName(ceAzureConfig.getStorageAccountName())
          .azureStorageContainerName(ceAzureConfig.getContainerName())
          .azureStorageDirectoryName(ceAzureConfig.getDirectoryName())
          .azureSubscriptionId(ceAzureConfig.getSubscriptionId())
          .azureTenantId(ceAzureConfig.getTenantId())
          .infraType(QLInfraTypesEnum.AZURE);
    }
    return qlCEConnectorBuilder.build();
  }

  @Override
  protected QLCESetupFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }

  @Override
  protected void populateFilters(List<QLCESetupFilter> filters, Query query) {
    ceSetupQueryHelper.setQuery(filters, query);
  }
}
