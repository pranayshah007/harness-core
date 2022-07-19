/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.setup.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.setup.graphql.QLCEOverviewStatsData.QLCEOverviewStatsDataBuilder;
import io.harness.ccm.views.dto.DefaultViewIdDto;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.query.QLNoOpQueryParameters;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class OverviewPageStatsDataFetcher
    extends AbstractObjectDataFetcher<QLCEOverviewStatsData, QLNoOpQueryParameters> {
  @Inject HPersistence persistence;
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject protected DataFetcherUtils utils;
  @Inject protected FeatureFlagService featureFlagService;
  @Inject private CEViewService ceViewService;
  @Inject private CEMetadataRecordDao metadataRecordDao;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLCEOverviewStatsData fetch(QLNoOpQueryParameters parameters, String accountId) {
    CEMetadataRecord ceMetadataRecord = metadataRecordDao.getByAccountId(accountId);

    boolean isAWSConnectorPresent = false;
    boolean isGCPConnectorPresent = false;
    boolean isAzureConnectorPresent = false;
    boolean isApplicationDataPresent = false;
    boolean isClusterDataPresent = false;
    if (ceMetadataRecord != null) {
      if (ceMetadataRecord.getAwsConnectorConfigured() != null) {
        isAWSConnectorPresent = ceMetadataRecord.getAwsConnectorConfigured();
      }
      if (ceMetadataRecord.getGcpConnectorConfigured() != null) {
        isGCPConnectorPresent = ceMetadataRecord.getGcpConnectorConfigured();
      }
      if (ceMetadataRecord.getAzureConnectorConfigured() != null) {
        isAzureConnectorPresent = ceMetadataRecord.getAzureConnectorConfigured();
      }
      if (ceMetadataRecord.getClusterDataConfigured() != null) {
        isClusterDataPresent = ceMetadataRecord.getClusterDataConfigured();
      }
      if (ceMetadataRecord.getApplicationDataPresent() != null) {
        isApplicationDataPresent = ceMetadataRecord.getApplicationDataPresent();
      }
    }
    boolean inventoryDashboard = true;

    QLCEOverviewStatsDataBuilder overviewStatsDataBuilder =
        QLCEOverviewStatsData.builder().inventoryDataPresent(inventoryDashboard);

    overviewStatsDataBuilder.cloudConnectorsPresent(
        isAWSConnectorPresent || isGCPConnectorPresent || isAzureConnectorPresent);

    boolean isCeEnabledCloudProviderPresent = getCEEnabledCloudProvider(accountId);

    if (!isClusterDataPresent
        && featureFlagService.isEnabledReloadCache(FeatureName.CE_SAMPLE_DATA_GENERATION, accountId)) {
      log.info("Sample data generation enabled for accountId:{}", accountId);
      if (utils.isSampleClusterDataPresent()) {
        isClusterDataPresent = true;
        overviewStatsDataBuilder.isSampleClusterPresent(true);
        log.info("sample data is present");
      }
    }
    overviewStatsDataBuilder.clusterDataPresent(isClusterDataPresent)
        .applicationDataPresent(isApplicationDataPresent)
        .ceEnabledClusterPresent(isCeEnabledCloudProviderPresent);

    // AWS, GCP, AZURE Data Present

    if (ceMetadataRecord != null) {
      if (ceMetadataRecord.getAwsDataPresent() != null) {
        isAWSConnectorPresent = ceMetadataRecord.getAwsDataPresent();
      } else {
        isAWSConnectorPresent = false;
      }
      if (ceMetadataRecord.getGcpDataPresent() != null) {
        isGCPConnectorPresent = ceMetadataRecord.getGcpDataPresent();
      } else {
        isGCPConnectorPresent = false;
      }
      if (ceMetadataRecord.getAzureDataPresent() != null) {
        isAzureConnectorPresent = ceMetadataRecord.getAzureDataPresent();
      } else {
        isAzureConnectorPresent = false;
      }
    }

    overviewStatsDataBuilder.awsConnectorsPresent(isAWSConnectorPresent)
        .gcpConnectorsPresent(isGCPConnectorPresent)
        .azureConnectorsPresent(isAzureConnectorPresent);

    DefaultViewIdDto defaultViewIds = ceViewService.getDefaultViewIds(accountId);

    overviewStatsDataBuilder.defaultAzurePerspectiveId(defaultViewIds.getAzureViewId());
    overviewStatsDataBuilder.defaultAwsPerspectiveId(defaultViewIds.getAwsViewId());
    overviewStatsDataBuilder.defaultGcpPerspectiveId(defaultViewIds.getGcpViewId());
    overviewStatsDataBuilder.defaultClusterPerspectiveId(defaultViewIds.getClusterViewId());

    log.info("Returning /overviewPageStats ");
    return overviewStatsDataBuilder.build();
  }

  protected boolean getCEEnabledCloudProvider(String accountId) {
    return null
        != persistence.createQuery(SettingAttribute.class, excludeValidate)
               .field(SettingAttributeKeys.accountId)
               .equal(accountId)
               .field(SettingAttributeKeys.category)
               .equal(SettingCategory.CLOUD_PROVIDER.toString())
               .field(SettingAttributeKeys.isCEEnabled)
               .equal(true)
               .get();
  }
}
