/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import static software.wings.beans.SettingAttribute.SettingCategory.CE_CONNECTOR;
import static software.wings.settings.SettingVariableTypes.CE_AZURE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.BatchProcessingException;
import io.harness.batch.processing.ccm.AzureStorageSyncRecord;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.batch.processing.service.impl.AzureStorageSyncServiceImpl;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceazure.BillingExportSpecDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAzureConfig;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CE)
public class AzureStorageSyncEventWriter extends EventWriter implements ItemWriter<SettingAttribute> {
  @Autowired private AzureStorageSyncServiceImpl azureStorageSyncService;
  @Autowired private ConnectorResourceClient connectorResourceClient;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private NGConnectorHelper ngConnectorHelper;
  private JobParameters parameters;

  @BeforeStep
  public void beforeStep(final StepExecution stepExecution) {
    parameters = stepExecution.getJobExecution().getJobParameters();
  }

  @Override
  public void write(List<? extends SettingAttribute> dummySettingAttributeList) {
    String accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
    boolean areAllSyncSuccessful = true;
    boolean isSuccessfulSync = syncCurrentGenAzureContainers(accountId);
    log.info("areAllSyncSuccessful (CG): {}", isSuccessfulSync);
    areAllSyncSuccessful = areAllSyncSuccessful && isSuccessfulSync;
    isSuccessfulSync = syncNextGenContainers(accountId);
    log.info("areAllSyncSuccessful (NG): {}", isSuccessfulSync);
    areAllSyncSuccessful = areAllSyncSuccessful && isSuccessfulSync;

    if (!areAllSyncSuccessful) {
      throw new BatchProcessingException("Azure sync failed", null);
    }
  }

  public boolean syncCurrentGenAzureContainers(String accountId) {
    List<ConnectorResponseDTO> currentGenConnectorResponses = new ArrayList<>();
    List<SettingAttribute> ceConnectorsList =
        cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(accountId, CE_CONNECTOR, CE_AZURE);
    log.info("Processing batch size of {} in AzureStorageSyncEventWriter", ceConnectorsList.size());
    ceConnectorsList.forEach(settingAttribute -> {
      if (settingAttribute.getValue() instanceof CEAzureConfig) {
        CEAzureConfig ceAzureConfig = (CEAzureConfig) settingAttribute.getValue();
        BillingExportSpecDTO billingExportDetails = BillingExportSpecDTO.builder()
                                                        .containerName(ceAzureConfig.getContainerName())
                                                        .directoryName(ceAzureConfig.getDirectoryName())
                                                        .storageAccountName(ceAzureConfig.getStorageAccountName())
                                                        .build();

        ConnectorConfigDTO connectorConfig = CEAzureConnectorDTO.builder()
                                                 .billingExportSpec(billingExportDetails)
                                                 .subscriptionId(ceAzureConfig.getSubscriptionId())
                                                 .tenantId(ceAzureConfig.getTenantId())
                                                 .build();
        ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                                .connectorConfig(connectorConfig)
                                                .connectorType(ConnectorType.CE_AZURE)
                                                .identifier(settingAttribute.getUuid())
                                                .name(settingAttribute.getName())
                                                .build();
        ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
        currentGenConnectorResponses.add(connectorResponse);
      }
    });
    return syncAzureContainers(currentGenConnectorResponses, accountId);
  }

  public boolean syncNextGenContainers(String accountId) {
    List<ConnectorResponseDTO> nextGenConnectors = ngConnectorHelper.getNextGenConnectors(
        accountId, Arrays.asList(ConnectorType.CE_AZURE), Arrays.asList(CEFeatures.BILLING), Collections.emptyList());
    log.info("Processing batch size of {} in AzureStorageSyncEventWriter (From NG)", nextGenConnectors.size());
    return syncAzureContainers(nextGenConnectors, accountId);
  }

  public boolean syncAzureContainers(List<ConnectorResponseDTO> connectorResponses, String accountId) {
    boolean areAllSyncSuccessful = true;
    for (ConnectorResponseDTO connector : connectorResponses) {
      try {
        CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connector.getConnector().getConnectorConfig();
        if (ceAzureConnectorDTO != null && ceAzureConnectorDTO.getBillingExportSpec() != null) {
          AzureStorageSyncRecord azureStorageSyncRecord =
              AzureStorageSyncRecord.builder()
                  .accountId(accountId)
                  .settingId(connector.getConnector().getIdentifier())
                  .containerName(ceAzureConnectorDTO.getBillingExportSpec().getContainerName())
                  .directoryName(ceAzureConnectorDTO.getBillingExportSpec().getDirectoryName())
                  .subscriptionId(ceAzureConnectorDTO.getSubscriptionId())
                  .storageAccountName(ceAzureConnectorDTO.getBillingExportSpec().getStorageAccountName())
                  .tenantId(ceAzureConnectorDTO.getTenantId())
                  .reportName(ceAzureConnectorDTO.getBillingExportSpec().getReportName())
                  .build();
          log.info("azureStorageSyncRecord {}", azureStorageSyncRecord);
          boolean isSuccessfulSync = azureStorageSyncService.syncContainer(azureStorageSyncRecord);
          areAllSyncSuccessful = areAllSyncSuccessful && isSuccessfulSync;
        }
      } catch (Exception ex) {
        log.error("Exception syncing azure report", ex);
      }
    }
    return areAllSyncSuccessful;
  }
}
