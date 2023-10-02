/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.azure.vm.service.tasklet;

import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.batch.processing.cloudevents.azure.vm.service.AzureHelperService;
import io.harness.batch.processing.cloudevents.azure.vm.service.helper.AzureConfigHelper;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.dao.recommendation.AzureRecommendationDAO;
import io.harness.ccm.commons.entities.azure.AzureRecommendation;
import io.harness.rule.Owner;
import io.harness.testsupport.BaseTaskletTest;

import software.wings.beans.AzureAccountAttributes;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.repeat.RepeatStatus;

@RunWith(MockitoJUnitRunner.class)
public class AzureRecommendationTaskletTest extends BaseTaskletTest {
  @Mock private AzureConfigHelper mockAzureConfigHelper;
  @Mock private AzureHelperService mockAzureHelperService;
  @Mock private AzureRecommendationDAO mockAzureRecommendationDAO;
  @InjectMocks private AzureRecommendationTasklet azureRecommendationTaskletUnderTest;

  private Map<String, AzureAccountAttributes> stringAzureAccountAttributesMap;
  private List<AzureRecommendation> azureRecommendations;
  private AzureRecommendation azureRecommendation;

  private final String TENANT_ID_SUBSCRIPTION_ID = "tenantId-subscriptionId";
  private final String RECOMMENDATION_ID =
      "/subscriptions/subs/resourcegroups/resourceGroupId/providers/microsoft.compute/virtualmachines/vm/providers/Microsoft.Advisor/recommendations/abc";
  private final String UUID = "uuid";
  private final String RESOURCE_GROUP_ID = "resourceGroupId";

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    stringAzureAccountAttributesMap = Map.ofEntries(Map.entry(TENANT_ID_SUBSCRIPTION_ID, getAzureAccountAttributes()));
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testExecute() throws Exception {
    when(mockAzureConfigHelper.getAzureAccountAttributes(ACCOUNT_ID)).thenReturn(stringAzureAccountAttributesMap);
    azureRecommendations = getMockedAzureRecommendations();
    when(mockAzureHelperService.getRecommendations(ACCOUNT_ID, AzureAccountAttributes.builder().build()))
        .thenReturn(azureRecommendations);

    azureRecommendation = getMockedAzureRecommendationWithUuid();
    when(mockAzureRecommendationDAO.saveRecommendation(
             AzureRecommendation.builder().recommendationId(RECOMMENDATION_ID).build()))
        .thenReturn(azureRecommendation);

    final RepeatStatus result = azureRecommendationTaskletUnderTest.execute(null, chunkContext);
    assertThat(result).isNull();
    verify(mockAzureRecommendationDAO).saveRecommendation(azureRecommendations.get(0));
    verify(mockAzureRecommendationDAO)
        .upsertCeRecommendation(azureRecommendation, Instant.ofEpochMilli(START_TIME_MILLIS), RESOURCE_GROUP_ID);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testExecute_AzureRecommendationDAOThrowsException() throws Exception {
    when(mockAzureConfigHelper.getAzureAccountAttributes(ACCOUNT_ID)).thenReturn(stringAzureAccountAttributesMap);
    azureRecommendations = getMockedAzureRecommendations();
    when(mockAzureHelperService.getRecommendations(ACCOUNT_ID, AzureAccountAttributes.builder().build()))
        .thenReturn(azureRecommendations);

    when(mockAzureRecommendationDAO.saveRecommendation(
             AzureRecommendation.builder().recommendationId(RECOMMENDATION_ID).build()))
        .thenThrow(new NullPointerException());

    final RepeatStatus result = azureRecommendationTaskletUnderTest.execute(null, chunkContext);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testExecute_AzureConfigHelperReturnsNoItems() throws Exception {
    when(mockAzureConfigHelper.getAzureAccountAttributes(ACCOUNT_ID)).thenReturn(new HashMap<>());
    final RepeatStatus result = azureRecommendationTaskletUnderTest.execute(null, chunkContext);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testExecute_AzureRecommendationServiceReturnsNoItems() throws Exception {
    when(mockAzureConfigHelper.getAzureAccountAttributes(ACCOUNT_ID)).thenReturn(stringAzureAccountAttributesMap);
    when(mockAzureHelperService.getRecommendations(ACCOUNT_ID, AzureAccountAttributes.builder().build()))
        .thenReturn(Collections.emptyList());
    final RepeatStatus result = azureRecommendationTaskletUnderTest.execute(null, chunkContext);
    assertThat(result).isNull();
  }

  private AzureAccountAttributes getAzureAccountAttributes() {
    return AzureAccountAttributes.builder().build();
  }

  private List<AzureRecommendation> getMockedAzureRecommendations() {
    return List.of(AzureRecommendation.builder().recommendationId(RECOMMENDATION_ID).build());
  }

  private AzureRecommendation getMockedAzureRecommendationWithUuid() {
    return AzureRecommendation.builder().recommendationId(RECOMMENDATION_ID).uuid(UUID).build();
  }
}
