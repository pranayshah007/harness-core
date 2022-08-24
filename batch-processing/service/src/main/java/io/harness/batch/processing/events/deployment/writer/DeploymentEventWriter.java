/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.events.deployment.writer;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.CostEventSource;
import io.harness.batch.processing.ccm.CostEventType;
import io.harness.batch.processing.events.timeseries.data.CostEventData;
import io.harness.batch.processing.events.timeseries.service.intfc.CostEventService;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.persistence.HPersistence;

import software.wings.api.DeploymentSummary;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class DeploymentEventWriter implements ItemWriter<List<String>> {
  @Autowired private HPersistence hPersistence;
  @Autowired private CostEventService costEventService;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;

  private JobParameters parameters;

  private static final String DEPLOYMENT_EVENT_DESCRIPTION = "Service %s got deployed to %s.";

  @BeforeStep
  public void beforeStep(final StepExecution stepExecution) {
    parameters = stepExecution.getJobExecution().getJobParameters();
  }

  @Override
  public void write(List<? extends List<String>> list) {
    String accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
    int offset = 0;
    Instant startTime = CCMJobConstants.getFieldValueFromJobParams(parameters, CCMJobConstants.JOB_START_DATE);
    Instant endTime = CCMJobConstants.getFieldValueFromJobParams(parameters, CCMJobConstants.JOB_END_DATE);

    List<DeploymentSummary> deploymentSummaries =
        cloudToHarnessMappingService.getDeploymentSummary(accountId, String.valueOf(offset), startTime, endTime);

    do {
      log.info("deploymentSummaries data size {}", deploymentSummaries.size());
      offset = offset + deploymentSummaries.size();
      createCostEvent(accountId, deploymentSummaries);
      deploymentSummaries =
          cloudToHarnessMappingService.getDeploymentSummary(accountId, String.valueOf(offset), startTime, endTime);
    } while (!deploymentSummaries.isEmpty());
  }

  private void createCostEvent(String accountId, List<DeploymentSummary> deploymentSummaries) {
    if (!deploymentSummaries.isEmpty()) {
      List<HarnessServiceInfo> harnessServiceInfoList =
          cloudToHarnessMappingService.getHarnessServiceInfoList(deploymentSummaries);
      Map<String, HarnessServiceInfo> infraMappingHarnessServiceInfo =
          harnessServiceInfoList.stream().collect(Collectors.toMap(
              HarnessServiceInfo::getInfraMappingId, Function.identity(), (existing, replacement) -> existing));

      List<String> serviceIds =
          harnessServiceInfoList.stream().map(HarnessServiceInfo::getServiceId).collect(Collectors.toList());
      List<String> envIds =
          harnessServiceInfoList.stream().map(HarnessServiceInfo::getEnvId).collect(Collectors.toList());

      Map<String, String> serviceNameMap = cloudToHarnessMappingService.getServiceName(accountId, serviceIds);
      Map<String, String> envNameMap = cloudToHarnessMappingService.getEnvName(accountId, envIds);

      List<CostEventData> cloudEventDataList = new ArrayList<>();
      deploymentSummaries.forEach(deploymentSummary -> {
        HarnessServiceInfo harnessServiceInfo =
            infraMappingHarnessServiceInfo.get(deploymentSummary.getInfraMappingId());
        if (null != harnessServiceInfo) {
          String serviceId = harnessServiceInfo.getServiceId();
          String envId = harnessServiceInfo.getEnvId();
          String serviceName = serviceNameMap.containsKey(serviceId) ? serviceNameMap.get(serviceId) : serviceId;
          String envName = envNameMap.containsKey(envId) ? envNameMap.get(envId) : envId;
          String deploymentEventDescription = String.format(DEPLOYMENT_EVENT_DESCRIPTION, serviceName, envName);
          CostEventData cloudEventData = CostEventData.builder()
                                             .accountId(accountId)
                                             .appId(harnessServiceInfo.getAppId())
                                             .serviceId(serviceId)
                                             .envId(envId)
                                             .cloudProviderId(harnessServiceInfo.getCloudProviderId())
                                             .deploymentId(deploymentSummary.getUuid())
                                             .eventDescription(deploymentEventDescription)
                                             .costEventType(CostEventType.DEPLOYMENT.name())
                                             .costEventSource(CostEventSource.HARNESS_CD.name())
                                             .startTimestamp(deploymentSummary.getDeployedAt())
                                             .build();
          log.debug("cloud event data {}", cloudEventData.toString());
          cloudEventDataList.add(cloudEventData);
        } else {
          log.warn("Harness service info not found {} infra {}", deploymentSummary.getUuid(),
              deploymentSummary.getInfraMappingId());
        }
      });

      if (!cloudEventDataList.isEmpty()) {
        costEventService.create(cloudEventDataList);
      }
    }
  }
}
