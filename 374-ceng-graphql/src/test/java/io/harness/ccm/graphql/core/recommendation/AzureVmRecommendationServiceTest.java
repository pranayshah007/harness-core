/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.dao.recommendation.AzureRecommendationDAO;
import io.harness.ccm.commons.entities.azure.AzureRecommendation;
import io.harness.ccm.commons.entities.azure.AzureVmDetails;
import io.harness.ccm.graphql.dto.recommendation.AzureVmDTO;
import io.harness.ccm.graphql.dto.recommendation.AzureVmRecommendationDTO;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AzureVmRecommendationServiceTest extends CategoryTest {
  @Mock private AzureRecommendationDAO mockAzureRecommendationDAO;

  @InjectMocks private AzureVmRecommendationService azureVmRecommendationServiceUnderTest;

  private AzureVmRecommendationDTO expectedResult;
  private AzureRecommendation azureRecommendation;

  private final String RECOMMENDATION_ID =
      "/subscriptions/subs/resourcegroups/resourceGroupId/providers/microsoft.compute/virtualmachines/vm/providers/Microsoft.Advisor/recommendations/abc";
  private final String ACCOUNT_ID = "accountId";
  private final String UUID = "uuid";
  private final String SHUTDOWN = "Shutdown";
  private final String VM_RIGHT_SIZING = "VmRightSizing";
  private final String REGION = "region";
  private final String CURRENT = "current";
  private final String TARGET = "target";
  private final String TENANT_ID = "tenantId";
  private final String SUBSCRIPTION_ID = "subscriptionId";
  private final String RESOURCE_GROUP_ID = "resourceGroupId";
  private final String IMPACTED_VALUE = "impactedValue";
  private final String CONNECTOR_NAME = "connectorName";
  private final String VM_ID = "vmId";
  private final String CONNECTOR_ID = "connectorId";

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetAzureVmRecommendationById_ShutdownRecommendation() {
    expectedResult = getAzureVmRecommendationDTO(SHUTDOWN);
    azureRecommendation = getAzureRecommendation(SHUTDOWN);
    when(mockAzureRecommendationDAO.fetchAzureRecommendationById(ACCOUNT_ID, UUID)).thenReturn(azureRecommendation);

    final AzureVmRecommendationDTO result =
        azureVmRecommendationServiceUnderTest.getAzureVmRecommendationById(ACCOUNT_ID, UUID);

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetAzureVmRecommendationById_VmRightSizingRecommendation() {
    expectedResult = getAzureVmRecommendationDTO(VM_RIGHT_SIZING);
    azureRecommendation = getAzureRecommendation(VM_RIGHT_SIZING);
    when(mockAzureRecommendationDAO.fetchAzureRecommendationById(ACCOUNT_ID, UUID)).thenReturn(azureRecommendation);

    final AzureVmRecommendationDTO result =
        azureVmRecommendationServiceUnderTest.getAzureVmRecommendationById(ACCOUNT_ID, UUID);

    assertThat(result).isEqualTo(expectedResult);
  }

  private AzureVmRecommendationDTO getAzureVmRecommendationDTO(String target) {
    AzureVmDTO currentVm = AzureVmDTO.builder()
                               .cores(4)
                               .memory(2048)
                               .monthlyCost(130.0)
                               .vmSize(CURRENT)
                               .cpuUtilization(5.0)
                               .region(REGION)
                               .build();
    AzureVmDTO targetVm = AzureVmDTO.builder()
                              .cores(2)
                              .memory(1024)
                              .monthlyCost(65.0)
                              .vmSize(TARGET)
                              .cpuUtilization(10.0)
                              .region(REGION)
                              .build();
    if (target.equalsIgnoreCase(SHUTDOWN)) {
      targetVm = AzureVmDTO.builder()
                     .cores(0)
                     .memory(0)
                     .monthlyCost(0.0)
                     .vmSize(SHUTDOWN)
                     .cpuUtilization(0.0)
                     .region(REGION)
                     .build();
    }
    return AzureVmRecommendationDTO.builder()
        .id(RECOMMENDATION_ID)
        .tenantId(TENANT_ID)
        .subscriptionId(SUBSCRIPTION_ID)
        .resourceGroupId(RESOURCE_GROUP_ID)
        .vmName(IMPACTED_VALUE)
        .vmId(VM_ID)
        .connectorName(CONNECTOR_NAME)
        .connectorId(CONNECTOR_ID)
        .duration(7)
        .current(currentVm)
        .showTerminated(target.equalsIgnoreCase(SHUTDOWN))
        .target(targetVm)
        .build();
  }

  private AzureRecommendation getAzureRecommendation(String target) {
    AzureVmDetails currentVm = AzureVmDetails.builder()
                                   .numberOfCores(4)
                                   .memoryInMB(2048)
                                   .cost(130.0)
                                   .name(CURRENT)
                                   .cpuUtilisation(5.0)
                                   .build();
    AzureVmDetails targetVm =
        AzureVmDetails.builder().numberOfCores(2).memoryInMB(1024).cost(65.0).name(TARGET).cpuUtilisation(10.0).build();
    if (target.equalsIgnoreCase(SHUTDOWN)) {
      targetVm =
          AzureVmDetails.builder().numberOfCores(0).memoryInMB(0).cost(0.0).name(SHUTDOWN).cpuUtilisation(0.0).build();
    }
    return AzureRecommendation.builder()
        .recommendationId(RECOMMENDATION_ID)
        .vmId(VM_ID)
        .impactedValue(IMPACTED_VALUE)
        .currentVmDetails(currentVm)
        .targetVmDetails(targetVm)
        .regionName(REGION)
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .duration("7")
        .connectorId(CONNECTOR_ID)
        .connectorName(CONNECTOR_NAME)
        .build();
  }
}
