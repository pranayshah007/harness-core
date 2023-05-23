/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.recommendation;

import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.graphql.dto.recommendation.AzureVmRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.EC2RecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.ECSRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.NodeRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationDetailsDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationItemDTO;
import io.harness.ccm.graphql.dto.recommendation.WorkloadRecommendationDTO;
import io.harness.ccm.graphql.query.recommendation.RecommendationsDetailsQuery;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import io.leangen.graphql.execution.ResolutionEnvironment;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RESTWrapperRecommendationDetailsTest extends CategoryTest {
  @Mock private RecommendationsDetailsQuery detailsQuery;
  @InjectMocks private RESTWrapperRecommendationDetails restWrapperRecommendationDetails;

  private ArgumentCaptor<ResolutionEnvironment> envCaptor;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String RECOMMENDATION_ID = "RECOMMENDATION_ID";
  private static final GraphQLUtils graphQLUtils = new GraphQLUtils();

  @Before
  public void setUp() throws Exception {
    envCaptor = ArgumentCaptor.forClass(ResolutionEnvironment.class);
  }

  @After
  public void tearDown() throws Exception {
    assertThat(graphQLUtils.getAccountIdentifier(envCaptor.getValue())).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testNodeRecommendationDetail() throws Exception {
    RecommendationDetailsDTO recommendationDetailsDTO = NodeRecommendationDTO.builder().id(RECOMMENDATION_ID).build();
    when(detailsQuery.recommendationDetails(
             any(String.class), eq(ResourceType.NODE_POOL), eq(null), eq(null), eq(0L), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    NodeRecommendationDTO nodeRecommendationDTO =
        restWrapperRecommendationDetails.nodeRecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID).getData();

    assertThat(nodeRecommendationDTO).isInstanceOf(RecommendationDetailsDTO.class);
    assertThat(nodeRecommendationDTO).isEqualTo(recommendationDetailsDTO);

    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testWorkloadRecommendationDetailDefaultDateTimeInput() throws Exception {
    final ArgumentCaptor<OffsetDateTime> startTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    final ArgumentCaptor<OffsetDateTime> endTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

    RecommendationDetailsDTO recommendationDetailsDTO =
        WorkloadRecommendationDTO.builder().id(RECOMMENDATION_ID).build();
    when(detailsQuery.recommendationDetails(any(String.class), eq(ResourceType.WORKLOAD), startTimeCaptor.capture(),
             endTimeCaptor.capture(), any(Long.class), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    WorkloadRecommendationDTO workloadRecommendationDTO =
        restWrapperRecommendationDetails.workloadRecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID, null, null)
            .getData();

    assertThat(workloadRecommendationDTO).isInstanceOf(RecommendationDetailsDTO.class);
    assertThat(workloadRecommendationDTO).isEqualTo(recommendationDetailsDTO);

    assertThat(startTimeCaptor.getValue()).isEqualTo(OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(7));
    assertThat(endTimeCaptor.getValue()).isEqualTo(OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS));

    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testWorkloadRecommendationDetailDateTimeInput() throws Exception {
    final ArgumentCaptor<OffsetDateTime> startTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    final ArgumentCaptor<OffsetDateTime> endTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

    RecommendationDetailsDTO recommendationDetailsDTO =
        WorkloadRecommendationDTO.builder().id(RECOMMENDATION_ID).build();
    when(detailsQuery.recommendationDetails(any(String.class), eq(ResourceType.WORKLOAD), startTimeCaptor.capture(),
             endTimeCaptor.capture(), any(Long.class), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    WorkloadRecommendationDTO workloadRecommendationDTO =
        restWrapperRecommendationDetails
            .workloadRecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID, "2022-01-03", "2022-01-10")
            .getData();

    assertThat(workloadRecommendationDTO).isInstanceOf(RecommendationDetailsDTO.class);
    assertThat(workloadRecommendationDTO).isEqualTo(recommendationDetailsDTO);

    assertThat(startTimeCaptor.getValue().toString()).isEqualTo("2022-01-03T00:00Z");
    assertThat(endTimeCaptor.getValue().toString()).isEqualTo("2022-01-10T00:00Z");

    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testECSRecommendationDetailDefaultDateTimeInput() throws Exception {
    final ArgumentCaptor<OffsetDateTime> startTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    final ArgumentCaptor<OffsetDateTime> endTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

    RecommendationDetailsDTO recommendationDetailsDTO = ECSRecommendationDTO.builder().id(RECOMMENDATION_ID).build();
    when(detailsQuery.recommendationDetails(any(String.class), eq(ResourceType.ECS_SERVICE), startTimeCaptor.capture(),
             endTimeCaptor.capture(), any(Long.class), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    ECSRecommendationDTO ecsRecommendationDTO =
        restWrapperRecommendationDetails.ecsRecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID, null, null, 0L)
            .getData();

    assertThat(ecsRecommendationDTO).isInstanceOf(RecommendationDetailsDTO.class);
    assertThat(ecsRecommendationDTO).isEqualTo(recommendationDetailsDTO);

    assertThat(startTimeCaptor.getValue()).isEqualTo(OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(7));
    assertThat(endTimeCaptor.getValue()).isEqualTo(OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS));

    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testECSRecommendationDetailDateTimeInput() throws Exception {
    final ArgumentCaptor<OffsetDateTime> startTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    final ArgumentCaptor<OffsetDateTime> endTimeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

    RecommendationDetailsDTO recommendationDetailsDTO = ECSRecommendationDTO.builder().id(RECOMMENDATION_ID).build();
    when(detailsQuery.recommendationDetails(any(String.class), eq(ResourceType.ECS_SERVICE), startTimeCaptor.capture(),
             endTimeCaptor.capture(), any(Long.class), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    ECSRecommendationDTO ecsRecommendationDTO =
        restWrapperRecommendationDetails
            .ecsRecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID, "2022-01-03", "2022-01-10", 0L)
            .getData();

    assertThat(ecsRecommendationDTO).isInstanceOf(RecommendationDetailsDTO.class);
    assertThat(ecsRecommendationDTO).isEqualTo(recommendationDetailsDTO);

    assertThat(startTimeCaptor.getValue().toString()).isEqualTo("2022-01-03T00:00Z");
    assertThat(endTimeCaptor.getValue().toString()).isEqualTo("2022-01-10T00:00Z");

    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testEC2RecommendationDetail() {
    RecommendationDetailsDTO recommendationDetailsDTO = EC2RecommendationDTO.builder().id(RECOMMENDATION_ID).build();
    when(detailsQuery.recommendationDetails(
             eq(RECOMMENDATION_ID), eq(ResourceType.EC2_INSTANCE), eq(null), eq(null), eq(null), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    EC2RecommendationDTO ec2RecommendationDTO =
        restWrapperRecommendationDetails.ec2RecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID).getData();

    assertThat(ec2RecommendationDTO).isInstanceOf(EC2RecommendationDTO.class);
    assertThat(ec2RecommendationDTO).isEqualTo(recommendationDetailsDTO);
    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testEC2RecommendationDetail_RecommendationDetailsReturnNull() {
    when(detailsQuery.recommendationDetails(
             eq(RECOMMENDATION_ID), eq(ResourceType.EC2_INSTANCE), eq(null), eq(null), eq(null), envCaptor.capture()))
        .thenReturn(null);

    EC2RecommendationDTO ec2RecommendationDTO =
        restWrapperRecommendationDetails.ec2RecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID).getData();

    assertThat(ec2RecommendationDTO).isNull();
    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testEC2RecommendationDetail_RecommendationDetailsReturnNoItems() {
    RecommendationDetailsDTO recommendationDetailsDTO = EC2RecommendationDTO.builder().build();
    when(detailsQuery.recommendationDetails(
             eq(RECOMMENDATION_ID), eq(ResourceType.EC2_INSTANCE), eq(null), eq(null), eq(null), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    EC2RecommendationDTO ec2RecommendationDTO =
        restWrapperRecommendationDetails.ec2RecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID).getData();

    assertThat(ec2RecommendationDTO).isInstanceOf(EC2RecommendationDTO.class);
    assertThat(ec2RecommendationDTO).isEqualTo(recommendationDetailsDTO);
    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testAzureVmRecommendationDetail() {
    RecommendationDetailsDTO recommendationDetailsDTO =
        AzureVmRecommendationDTO.builder().id(RECOMMENDATION_ID).build();
    when(detailsQuery.recommendationDetails(
             eq(RECOMMENDATION_ID), eq(ResourceType.AZURE_INSTANCE), eq(null), eq(null), eq(null), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    AzureVmRecommendationDTO azureVmRecommendationDTO =
        restWrapperRecommendationDetails.azureVmRecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID).getData();

    assertThat(azureVmRecommendationDTO).isInstanceOf(AzureVmRecommendationDTO.class);
    assertThat(azureVmRecommendationDTO).isEqualTo(recommendationDetailsDTO);
    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testAzureVmRecommendationDetail_RecommendationDetailsReturnNull() {
    when(detailsQuery.recommendationDetails(
             eq(RECOMMENDATION_ID), eq(ResourceType.AZURE_INSTANCE), eq(null), eq(null), eq(null), envCaptor.capture()))
        .thenReturn(null);

    AzureVmRecommendationDTO azureVmRecommendationDTO =
        restWrapperRecommendationDetails.azureVmRecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID).getData();

    assertThat(azureVmRecommendationDTO).isNull();
    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testAzureVmRecommendationDetail_RecommendationDetailsReturnNoItems() {
    RecommendationDetailsDTO recommendationDetailsDTO = AzureVmRecommendationDTO.builder().build();
    when(detailsQuery.recommendationDetails(
             eq(RECOMMENDATION_ID), eq(ResourceType.AZURE_INSTANCE), eq(null), eq(null), eq(null), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    AzureVmRecommendationDTO azureVmRecommendationDTO =
        restWrapperRecommendationDetails.azureVmRecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID).getData();

    assertThat(azureVmRecommendationDTO).isInstanceOf(AzureVmRecommendationDTO.class);
    assertThat(azureVmRecommendationDTO).isEqualTo(recommendationDetailsDTO);
    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any(), any());
  }
}
