/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import static io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData;
import static io.harness.ccm.commons.beans.InstanceType.K8S_PV;
import static io.harness.ccm.commons.beans.InstanceType.K8S_PVC;

import static java.util.Optional.ofNullable;

import io.harness.batch.processing.billing.timeseries.data.InstanceUtilizationData;
import io.harness.batch.processing.billing.timeseries.service.impl.K8sUtilizationGranularDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;

import com.google.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class K8sPVUtilizationAggregationTasklet implements Tasklet {
  @Autowired protected InstanceDataDao instanceDataDao;
  @Autowired private UtilizationDataServiceImpl utilizationDataService;
  @Autowired private K8sUtilizationGranularDataServiceImpl k8sUtilizationGranularDataService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);

    Map<String, InstanceUtilizationData> instanceUtilizationDataMap =
        k8sUtilizationGranularDataService.getAggregatedUtilizationDataOfType(
            jobConstants.getAccountId(), K8S_PVC, jobConstants.getJobStartTime(), jobConstants.getJobEndTime());
    List<InstanceData> instanceDataList = instanceDataDao.fetchActivePVList(jobConstants.getAccountId(),
        Instant.ofEpochMilli(jobConstants.getJobStartTime()), Instant.ofEpochMilli(jobConstants.getJobEndTime()));

    List<InstanceUtilizationData> instanceUtilizationDataList =
        instanceDataList.stream()
            .map(instanceData -> {
              String instanceId = String.format("%s/%s",
                  getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLAIM_NAMESPACE, instanceData),
                  getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLAIM_NAME, instanceData));

              String settingId = instanceData.getSettingId();
              String clusterId = instanceData.getClusterId();

              double storageCapacity = instanceData.getStorageResource().getCapacity(); // It is in "MiB"

              final InstanceUtilizationData instanceUtilizationData = instanceUtilizationDataMap.get(instanceId);
              double storageUsageAvgValue =
                  ofNullable(instanceUtilizationData).map(InstanceUtilizationData::getStorageUsageAvgValue).orElse(0D);
              double storageRequestAvgValue = ofNullable(instanceUtilizationData)
                                                  .map(InstanceUtilizationData::getStorageRequestAvgValue)
                                                  .orElse(0D);
              double storageUsageMaxValue =
                  ofNullable(instanceUtilizationData).map(InstanceUtilizationData::getStorageUsageMaxValue).orElse(0D);

              double storageRequestMaxValue = ofNullable(instanceUtilizationData)
                                                  .map(InstanceUtilizationData::getStorageRequestMaxValue)
                                                  .orElse(0D);

              return InstanceUtilizationData.builder()
                  .accountId(jobConstants.getAccountId())
                  .clusterId(clusterId)
                  .settingId(settingId)
                  .instanceType(K8S_PV.name())
                  .instanceId(instanceId)
                  .storageCapacityAvgValue(storageCapacity)
                  .storageRequestAvgValue(storageRequestAvgValue)
                  .storageUsageAvgValue(storageUsageAvgValue)
                  .storageRequestMaxValue(storageRequestMaxValue)
                  .storageUsageMaxValue(storageUsageMaxValue)
                  .startTimestamp(jobConstants.getJobStartTime())
                  .endTimestamp(jobConstants.getJobEndTime())
                  .build();
            })
            .collect(Collectors.toList());

    utilizationDataService.create(instanceUtilizationDataList);

    return null;
  }
}
