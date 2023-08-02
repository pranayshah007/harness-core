/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import io.harness.ccm.commons.dao.recommendation.AzureRecommendationDAO;
import io.harness.ccm.commons.entities.azure.AzureRecommendation;
import io.harness.ccm.graphql.dto.recommendation.AzureVmDTO;
import io.harness.ccm.graphql.dto.recommendation.AzureVmRecommendationDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * This service is a helper class between the API classes and DAOs.
 */
@Singleton
@Slf4j
public class AzureVmRecommendationService {
  @Inject private AzureRecommendationDAO azureRecommendationDAO;

  /**
   * This method fetches the ec2 instances recommendations from mongo table EC2Recommendations.
   * @param accountIdentifier
   * @param id
   * @return
   */
  @Nullable
  public AzureVmRecommendationDTO getAzureVmRecommendationById(@NonNull final String accountIdentifier, String id) {
    AzureRecommendation azureRecommendation =
        azureRecommendationDAO.fetchAzureRecommendationById(accountIdentifier, id);
    if (azureRecommendation == null) {
      return AzureVmRecommendationDTO.builder().build();
    }
    return convertDTOFromEntity(azureRecommendation);
  }

  private AzureVmRecommendationDTO convertDTOFromEntity(AzureRecommendation azureRecommendation) {
    return AzureVmRecommendationDTO.builder()
        .id(azureRecommendation.getRecommendationId())
        .tenantId(azureRecommendation.getTenantId())
        .subscriptionId(azureRecommendation.getSubscriptionId())
        .vmName(azureRecommendation.getImpactedValue())
        .vmId(azureRecommendation.getVmId())
        .connectorId(azureRecommendation.getConnectorId())
        .connectorName(azureRecommendation.getConnectorName())
        .duration(Integer.parseInt(azureRecommendation.getDuration()))
        .resourceGroupId(azureRecommendation.getRecommendationId().split("/")[4])
        .current(AzureVmDTO.builder()
                     .cores(azureRecommendation.getCurrentVmDetails().getNumberOfCores())
                     .memory(azureRecommendation.getCurrentVmDetails().getMemoryInMB())
                     .monthlyCost(azureRecommendation.getCurrentVmDetails().getCostInDefaultCurrencyPref())
                     .region(azureRecommendation.getRegionName())
                     .vmSize(azureRecommendation.getCurrentVmDetails().getName())
                     .avgCpuUtilization(azureRecommendation.getCurrentVmDetails().getAvgCpuUtilisation())
                     .maxCpuUtilization(azureRecommendation.getCurrentVmDetails().getMaxCpuUtilisation())
                     .avgMemoryUtilization(azureRecommendation.getCurrentVmDetails().getAvgMemoryUtilisation())
                     .maxMemoryUtilization(azureRecommendation.getCurrentVmDetails().getMaxMemoryUtilisation())
                     .build())
        .target(AzureVmDTO.builder()
                    .cores(azureRecommendation.getTargetVmDetails().getNumberOfCores())
                    .memory(azureRecommendation.getTargetVmDetails().getMemoryInMB())
                    .monthlyCost(azureRecommendation.getTargetVmDetails().getCostInDefaultCurrencyPref())
                    .region(azureRecommendation.getRegionName())
                    .vmSize(azureRecommendation.getTargetVmDetails().getName())
                    .avgCpuUtilization(azureRecommendation.getTargetVmDetails().getAvgCpuUtilisation())
                    .maxCpuUtilization(azureRecommendation.getTargetVmDetails().getMaxCpuUtilisation())
                    .avgMemoryUtilization(azureRecommendation.getTargetVmDetails().getAvgMemoryUtilisation())
                    .maxMemoryUtilization(azureRecommendation.getTargetVmDetails().getMaxMemoryUtilisation())
                    .build())
        .showTerminated(azureRecommendation.getTargetVmDetails().getName().equals("Shutdown"))
        .jiraDetails(azureRecommendation.getJiraDetails())
        .serviceNowDetails(azureRecommendation.getServiceNowDetails())
        .build();
  }
}
