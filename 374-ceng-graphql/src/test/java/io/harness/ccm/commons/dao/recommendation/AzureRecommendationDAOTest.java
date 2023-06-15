/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.recommendation;

import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HQuery.excludeValidate;
import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.entities.azure.AzureRecommendation;
import io.harness.ccm.commons.entities.azure.AzureVmDetails;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AzureRecommendationDAOTest extends CategoryTest {
  @Mock private HPersistence mockHPersistence;
  @Mock private Query<AzureRecommendation> query;
  @Mock private UpdateOperations<AzureRecommendation> updateOperations;
  @Mock private FieldEnd fieldEnd;

  private AzureRecommendation expectedResult;
  private CCMJiraDetails jiraDetails;

  @InjectMocks private AzureRecommendationDAO azureRecommendationDAOUnderTest;

  private static final String ACCOUNT_ID = "accountId";
  private static final String UUID = "5fd2b09ea2a4931e7822e8d8";
  private static final String RECOMMENDATION_ID = "recommendationId";
  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final String RESOURCE_GROUP_ID = "resourceGroupId";
  private static final String VM_NAME = "vmName";

  @Before
  public void setUp() throws Exception {
    when(query.filter(any(), any())).thenReturn(query);
    when(query.field(any())).thenReturn(fieldEnd);
    when(fieldEnd.equal(any())).thenReturn(query);
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    jiraDetails = CCMJiraDetails.builder().build();
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testFetchAzureRecommendationById() {
    when(mockHPersistence.createQuery(AzureRecommendation.class, excludeValidate)).thenReturn(query);
    expectedResult = getExpectedResult();
    when(query.get()).thenReturn(expectedResult);

    final AzureRecommendation result = azureRecommendationDAOUnderTest.fetchAzureRecommendationById(ACCOUNT_ID, UUID);

    verify(mockHPersistence).createQuery(AzureRecommendation.class, excludeValidate);
    verify(query).get();
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testFetchAzureRecommendationById_NoRecommendationFound() {
    when(mockHPersistence.createQuery(AzureRecommendation.class, excludeValidate)).thenReturn(query);
    expectedResult = getExpectedResult();
    when(query.get()).thenReturn(null);

    final AzureRecommendation result = azureRecommendationDAOUnderTest.fetchAzureRecommendationById(ACCOUNT_ID, UUID);

    verify(mockHPersistence).createQuery(AzureRecommendation.class, excludeValidate);
    verify(query).get();
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testSaveRecommendation() {
    when(mockHPersistence.createQuery(AzureRecommendation.class)).thenReturn(query);
    AzureRecommendation azureRecommendation = getExpectedResult();
    AzureRecommendation expectedResult = getExpectedResult();
    when(mockHPersistence.createUpdateOperations(AzureRecommendation.class)).thenReturn(updateOperations);
    when(mockHPersistence.upsert(any(Query.class), any(UpdateOperations.class), any(FindAndModifyOptions.class)))
        .thenReturn(azureRecommendation);

    final AzureRecommendation result = azureRecommendationDAOUnderTest.saveRecommendation(azureRecommendation);

    verify(mockHPersistence).createQuery(AzureRecommendation.class);
    verify(mockHPersistence).createUpdateOperations(AzureRecommendation.class);
    verify(mockHPersistence).upsert(query, updateOperations, upsertReturnNewOptions);
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testUpdateJiraInAzureRecommendation() {
    when(mockHPersistence.createQuery(AzureRecommendation.class)).thenReturn(query);
    when(mockHPersistence.createUpdateOperations(AzureRecommendation.class)).thenReturn(updateOperations);

    azureRecommendationDAOUnderTest.updateJiraInAzureRecommendation(ACCOUNT_ID, UUID, jiraDetails);

    verify(mockHPersistence).upsert(any(Query.class), any(UpdateOperations.class));
  }

  private AzureRecommendation getExpectedResult() {
    return AzureRecommendation.builder()
        .uuid(UUID)
        .accountId(ACCOUNT_ID)
        .recommendationId(RECOMMENDATION_ID)
        .vmId("vmId")
        .impactedField("impactedField")
        .impactedValue("impactedValue")
        .maxCpuP95("maxCpuP95")
        .maxTotalNetworkP95("maxTotalNetworkP95")
        .maxMemoryP95("maxMemoryP95")
        .currencyCode("currencyCode")
        .expectedMonthlySavings(0.0)
        .expectedAnnualSavings(0.0)
        .currentVmDetails(AzureVmDetails.builder().cost(0.0).build())
        .targetVmDetails(AzureVmDetails.builder().cost(0.0).build())
        .recommendationMessage("recommendationMessage")
        .recommendationType("recommendationType")
        .regionName("regionName")
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId("tenantId")
        .duration("duration")
        .connectorId("connectorId")
        .connectorName("connectorName")
        .build();
  }
}
