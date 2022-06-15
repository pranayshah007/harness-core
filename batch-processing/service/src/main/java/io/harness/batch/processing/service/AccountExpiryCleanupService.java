/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service;

import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.batch.processing.service.intfc.AccountExpiryService;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord.CEMetadataRecordBuilder;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.license.CeLicenseInfo;

import software.wings.beans.Account;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEGcpConfig;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Singleton
@Service
public class AccountExpiryCleanupService {
  @Autowired private AccountExpiryService accountExpiryService;
  @Autowired private BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private BigQueryHelperService bigQueryHelperService;

  public void execute() {
    List<Account> accounts = cloudToHarnessMappingService.getCeAccountsWithLicense();
    log.info("Accounts batch size is AccountExpiryCleanupTasklet {} ", accounts.size());
    accounts.forEach(this::cleanupPipeline);
  }

  private void cleanupPipeline(Account account) {
    try {
      Boolean isAwsConnectorPresent = false;
      Boolean isGCPConnectorPresent = false;

      String accountId = account.getUuid();
      List<SettingAttribute> ceConnectors = cloudToHarnessMappingService.getCEConnectors(accountId);
      Set<String> settingIdsSet =
          ceConnectors.stream()
              .filter(connector -> connector.getValue().getType().equals(SettingVariableTypes.CE_AWS.toString()))
              .map(SettingAttribute::getUuid)
              .collect(Collectors.toSet());

      if (!settingIdsSet.isEmpty()) {
        isAwsConnectorPresent = true;
      }

      for (SettingAttribute ceConnector : ceConnectors) {
        SettingValue value = ceConnector.getValue();
        if (value.getType().equals(SettingVariableTypes.CE_GCP.toString())) {
          isGCPConnectorPresent = true;
          settingIdsSet.add(((CEGcpConfig) value).getOrganizationSettingId());
        }
      }

      CEMetadataRecordBuilder ceMetadataRecordBuilder = CEMetadataRecord.builder().accountId(accountId);

      if (isAwsConnectorPresent || isGCPConnectorPresent) {
        bigQueryHelperService.updateCloudProviderMetaData(accountId, ceMetadataRecordBuilder);
      }

      cloudToHarnessMappingService.upsertCEMetaDataRecord(
          ceMetadataRecordBuilder.awsConnectorConfigured(isAwsConnectorPresent)
              .gcpConnectorConfigured(isGCPConnectorPresent)
              .build());

      List<BillingDataPipelineRecord> billingDataPipelineRecordList =
          billingDataPipelineRecordDao.getAllRecordsByAccountId(accountId);

      for (BillingDataPipelineRecord record : billingDataPipelineRecordList) {
        if (!settingIdsSet.contains(record.getSettingId())) {
          accountExpiryService.deletePipelinePerRecord(accountId, record);
        }
      }

      CeLicenseInfo ceLicenseInfo = account.getCeLicenseInfo();
      long expiryTime = ceLicenseInfo.getExpiryTimeWithGracePeriod();
      if (expiryTime != 0L && Instant.now().toEpochMilli() > expiryTime) {
        log.info("Triggering Data Pipeline Clean up for account: {} ", account);
        accountExpiryService.dataPipelineCleanup(account);
      }
    } catch (Exception ex) {
      log.error("Exception while clean up pipeline", ex);
    }
  }
}
