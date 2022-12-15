/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.service.intfc.BillingDataPipelineService;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;

import software.wings.beans.Account;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingVariableTypes;

import com.google.cloud.bigquery.datatransfer.v1.TransferState;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class AzureBillingDataPipelineTasklet implements Tasklet {
  @Autowired private BatchMainConfig mainConfig;
  @Autowired private BillingDataPipelineService billingDataPipelineService;
  @Autowired private BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Autowired protected CloudToHarnessMappingService cloudToHarnessMappingService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();
    Account account = cloudToHarnessMappingService.getAccountInfoFromId(accountId);
    String accountName = account.getAccountName();

    List<SettingAttribute> ceConnectorsList = cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(
        accountId, SettingCategory.CE_CONNECTOR, SettingVariableTypes.CE_AZURE);
    log.info("Found {} Azure connectors", ceConnectorsList.size());
    ceConnectorsList.forEach(settingAttribute -> {
      String settingId = settingAttribute.getUuid();
      BillingDataPipelineRecord billingDataPipelineRecord =
          billingDataPipelineRecordDao.getBySettingId(accountId, settingId);
      if (null == billingDataPipelineRecord) {
        String dataSetId = billingDataPipelineService.createDataSet(account);
        BillingDataPipelineRecord dataPipelineRecord =
            BillingDataPipelineRecord.builder()
                .accountId(accountId)
                .accountName(accountName)
                .cloudProvider(CloudProvider.AZURE.name())
                .settingId(settingId)
                .dataSetId(dataSetId)
                .dataTransferJobStatus(TransferState.PENDING.toString())
                .preAggregatedScheduledQueryStatus(TransferState.PENDING.toString())
                .build();
        billingDataPipelineRecordDao.create(dataPipelineRecord);
      }
    });
    return null;
  }
}
