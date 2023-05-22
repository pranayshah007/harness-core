/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.RecommendationState;
import io.harness.ccm.commons.dao.recommendation.AzureRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.ECSRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.RecommendationsIgnoreListDAO;
import io.harness.ccm.commons.entities.recommendations.RecommendationAzureVmId;
import io.harness.ccm.commons.entities.recommendations.RecommendationEC2InstanceId;
import io.harness.ccm.commons.entities.recommendations.RecommendationECSServiceId;
import io.harness.ccm.commons.entities.recommendations.RecommendationNodepoolId;
import io.harness.ccm.commons.entities.recommendations.RecommendationWorkloadId;
import io.harness.ccm.commons.entities.recommendations.RecommendationsIgnoreList;
import io.harness.ccm.graphql.dto.recommendation.RecommendationsIgnoreResourcesDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RecommendationsIgnoreListServiceTest extends CategoryTest {
  @Mock private RecommendationsIgnoreListDAO mockIgnoreListDAO;
  @Mock private K8sRecommendationDAO mockK8sRecommendationDAO;
  @Mock private ECSRecommendationDAO mockEcsRecommendationDAO;
  @Mock private EC2RecommendationDAO mockEc2RecommendationDAO;
  @Mock private AzureRecommendationDAO mockAzureRecommendationDAO;

  private RecommendationsIgnoreList recommendationsExpectedIgnoreList;
  private RecommendationsIgnoreResourcesDTO ignoreResourcesDTO;

  private final String ACCOUNT_ID = "accountId";
  private final String RECOMMENDATION_ID = "recommendationId";
  private final String CLUSTER_NAME = "clusterName";
  private final String NAMESPACE = "namespace";
  private final String WORKLOAD_NAME = "workloadName";
  private final String NODE_POOL_NAME = "nodePoolName";
  private final String SERVICE_NAME = "serviceName";
  private final String AWS_ACCOUNT_ID = "awsAccountId";
  private final String INSTANCE_ID = "instanceId";

  @InjectMocks private RecommendationsIgnoreListService recommendationsIgnoreListServiceUnderTest;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    recommendationsExpectedIgnoreList = getRecommendationsIgnoreList(1, "");
    ignoreResourcesDTO = getIgnoreResourcesDTO(1, "1");
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetIgnoreList() {
    final Optional<RecommendationsIgnoreList> recommendationsIgnoreList =
        Optional.of(recommendationsExpectedIgnoreList);
    when(mockIgnoreListDAO.get(ACCOUNT_ID)).thenReturn(recommendationsIgnoreList);

    final RecommendationsIgnoreList result = recommendationsIgnoreListServiceUnderTest.getIgnoreList(ACCOUNT_ID);

    assertThat(result).isEqualTo(recommendationsExpectedIgnoreList);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetIgnoreList_RecommendationsIgnoreListDAOReturnsAbsent() {
    final RecommendationsIgnoreList expectedResult = getRecommendationsIgnoreList(0, "");
    when(mockIgnoreListDAO.get(ACCOUNT_ID)).thenReturn(Optional.empty());

    final RecommendationsIgnoreList result = recommendationsIgnoreListServiceUnderTest.getIgnoreList(ACCOUNT_ID);

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testAddResources() {
    final Optional<RecommendationsIgnoreList> recommendationsIgnoreList =
        Optional.of(recommendationsExpectedIgnoreList);
    when(mockIgnoreListDAO.get(ACCOUNT_ID)).thenReturn(recommendationsIgnoreList);

    // Run the test
    final RecommendationsIgnoreList result =
        recommendationsIgnoreListServiceUnderTest.addResources(ACCOUNT_ID, ignoreResourcesDTO);

    // Verify the results
    assertThat(result).isEqualTo(recommendationsExpectedIgnoreList);
    verify(mockK8sRecommendationDAO)
        .ignoreWorkloadRecommendations(ACCOUNT_ID, List.of(getRecommendationWorkloadId("1")));
    verify(mockK8sRecommendationDAO)
        .ignoreNodepoolRecommendations(ACCOUNT_ID, List.of(getRecommendationNodepoolId("1")));
    verify(mockEcsRecommendationDAO).ignoreECSRecommendations(ACCOUNT_ID, List.of(getRecommendationECSServiceId("1")));
    verify(mockEc2RecommendationDAO).ignoreEC2Recommendations(ACCOUNT_ID, List.of(getRecommendationEC2InstanceId("1")));
    verify(mockAzureRecommendationDAO)
        .ignoreAzureVmRecommendations(ACCOUNT_ID, List.of(getRecommendationAzureVmId("1")));
    verify(mockIgnoreListDAO).save(recommendationsExpectedIgnoreList);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testAddResources_RecommendationsIgnoreListDAOGetReturnsAbsent() {
    final RecommendationsIgnoreList expectedResult = getRecommendationsIgnoreList(0, "");
    when(mockIgnoreListDAO.get(ACCOUNT_ID)).thenReturn(Optional.empty());

    final RecommendationsIgnoreList result =
        recommendationsIgnoreListServiceUnderTest.addResources(ACCOUNT_ID, ignoreResourcesDTO);

    assertThat(result).isEqualTo(expectedResult);
    verify(mockK8sRecommendationDAO)
        .ignoreWorkloadRecommendations(ACCOUNT_ID, List.of(getRecommendationWorkloadId("1")));
    verify(mockK8sRecommendationDAO)
        .ignoreNodepoolRecommendations(ACCOUNT_ID, List.of(getRecommendationNodepoolId("1")));
    verify(mockEcsRecommendationDAO).ignoreECSRecommendations(ACCOUNT_ID, List.of(getRecommendationECSServiceId("1")));
    verify(mockEc2RecommendationDAO).ignoreEC2Recommendations(ACCOUNT_ID, List.of(getRecommendationEC2InstanceId("1")));
    verify(mockAzureRecommendationDAO)
        .ignoreAzureVmRecommendations(ACCOUNT_ID, List.of(getRecommendationAzureVmId("1")));
    verify(mockIgnoreListDAO).save(getRecommendationsIgnoreList(1, "1"));
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testRemoveResources() {
    final Optional<RecommendationsIgnoreList> recommendationsIgnoreList =
        Optional.of(recommendationsExpectedIgnoreList);
    when(mockIgnoreListDAO.get(ACCOUNT_ID)).thenReturn(recommendationsIgnoreList);

    final RecommendationsIgnoreList result =
        recommendationsIgnoreListServiceUnderTest.removeResources(ACCOUNT_ID, getIgnoreResourcesDTO(1, ""));

    // Verify the results
    assertThat(result).isEqualTo(recommendationsExpectedIgnoreList);
    verify(mockK8sRecommendationDAO)
        .unignoreWorkloadRecommendations(ACCOUNT_ID, List.of(getRecommendationWorkloadId("")));
    verify(mockK8sRecommendationDAO)
        .unignoreNodepoolRecommendations(ACCOUNT_ID, List.of(getRecommendationNodepoolId("")));
    verify(mockEcsRecommendationDAO).unignoreECSRecommendations(ACCOUNT_ID, List.of(getRecommendationECSServiceId("")));
    verify(mockEc2RecommendationDAO)
        .unignoreEC2Recommendations(ACCOUNT_ID, List.of(getRecommendationEC2InstanceId("")));
    verify(mockAzureRecommendationDAO)
        .unIgnoreAzureVmRecommendations(ACCOUNT_ID, List.of(getRecommendationAzureVmId("")));
    verify(mockIgnoreListDAO).save(recommendationsExpectedIgnoreList);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testRemoveResources_RecommendationsIgnoreListDAOGetReturnsAbsent() {
    when(mockIgnoreListDAO.get(ACCOUNT_ID)).thenReturn(Optional.empty());
    final RecommendationsIgnoreList expectedResult = getRecommendationsIgnoreList(0, "");

    final RecommendationsIgnoreList result =
        recommendationsIgnoreListServiceUnderTest.removeResources(ACCOUNT_ID, getIgnoreResourcesDTO(0, "1"));

    // Verify the results
    assertThat(result).isEqualTo(expectedResult);
    verify(mockK8sRecommendationDAO).unignoreWorkloadRecommendations(ACCOUNT_ID, Collections.EMPTY_LIST);
    verify(mockK8sRecommendationDAO).unignoreNodepoolRecommendations(ACCOUNT_ID, Collections.EMPTY_LIST);
    verify(mockEcsRecommendationDAO).unignoreECSRecommendations(ACCOUNT_ID, Collections.EMPTY_LIST);
    verify(mockEc2RecommendationDAO).unignoreEC2Recommendations(ACCOUNT_ID, Collections.EMPTY_LIST);
    verify(mockAzureRecommendationDAO).unIgnoreAzureVmRecommendations(ACCOUNT_ID, Collections.EMPTY_LIST);
    verify(mockIgnoreListDAO).save(expectedResult);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testUpdateWorkloadRecommendationState() {
    when(mockK8sRecommendationDAO.getRecommendationState(RECOMMENDATION_ID)).thenReturn(RecommendationState.OPEN);
    final Optional<RecommendationsIgnoreList> recommendationsIgnoreList =
        Optional.of(recommendationsExpectedIgnoreList);
    when(mockIgnoreListDAO.get(ACCOUNT_ID)).thenReturn(recommendationsIgnoreList);

    recommendationsIgnoreListServiceUnderTest.updateWorkloadRecommendationState(
        RECOMMENDATION_ID, ACCOUNT_ID, CLUSTER_NAME, NAMESPACE, WORKLOAD_NAME);

    verify(mockK8sRecommendationDAO).updateRecommendationState(RECOMMENDATION_ID, RecommendationState.IGNORED);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testUpdateNodeRecommendationState() {
    when(mockK8sRecommendationDAO.getRecommendationState(RECOMMENDATION_ID)).thenReturn(RecommendationState.OPEN);
    final Optional<RecommendationsIgnoreList> recommendationsIgnoreList =
        Optional.of(recommendationsExpectedIgnoreList);
    when(mockIgnoreListDAO.get(ACCOUNT_ID)).thenReturn(recommendationsIgnoreList);

    recommendationsIgnoreListServiceUnderTest.updateNodeRecommendationState(
        RECOMMENDATION_ID, ACCOUNT_ID, CLUSTER_NAME, NODE_POOL_NAME);

    verify(mockK8sRecommendationDAO).updateRecommendationState(RECOMMENDATION_ID, RecommendationState.IGNORED);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testUpdateECSRecommendationState() {
    when(mockK8sRecommendationDAO.getRecommendationState(RECOMMENDATION_ID)).thenReturn(RecommendationState.OPEN);
    final Optional<RecommendationsIgnoreList> recommendationsIgnoreList =
        Optional.of(recommendationsExpectedIgnoreList);
    when(mockIgnoreListDAO.get(ACCOUNT_ID)).thenReturn(recommendationsIgnoreList);

    recommendationsIgnoreListServiceUnderTest.updateECSRecommendationState(
        RECOMMENDATION_ID, ACCOUNT_ID, CLUSTER_NAME, SERVICE_NAME);

    verify(mockK8sRecommendationDAO).updateRecommendationState(RECOMMENDATION_ID, RecommendationState.IGNORED);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testUpdateEC2RecommendationState() {
    when(mockK8sRecommendationDAO.getRecommendationState(RECOMMENDATION_ID)).thenReturn(RecommendationState.OPEN);
    final Optional<RecommendationsIgnoreList> recommendationsIgnoreList =
        Optional.of(recommendationsExpectedIgnoreList);
    when(mockIgnoreListDAO.get(ACCOUNT_ID)).thenReturn(recommendationsIgnoreList);

    recommendationsIgnoreListServiceUnderTest.updateEC2RecommendationState(
        RECOMMENDATION_ID, ACCOUNT_ID, AWS_ACCOUNT_ID, INSTANCE_ID);

    verify(mockK8sRecommendationDAO).updateRecommendationState(RECOMMENDATION_ID, RecommendationState.IGNORED);
  }

  private RecommendationsIgnoreList getRecommendationsIgnoreList(int length, String instance) {
    if (length == 0) {
      return RecommendationsIgnoreList.builder()
          .accountId(ACCOUNT_ID)
          .workloadIgnoreList(Collections.emptySet())
          .nodepoolIgnoreList(Collections.emptySet())
          .ecsServiceIgnoreList(Collections.emptySet())
          .ec2InstanceIgnoreList(Collections.emptySet())
          .azureVmIgnoreList(Collections.emptySet())
          .build();
    }
    return RecommendationsIgnoreList.builder()
        .accountId(ACCOUNT_ID)
        .workloadIgnoreList(new HashSet<>() {
          { add(getRecommendationWorkloadId(instance)); }
        })
        .nodepoolIgnoreList(new HashSet<>() {
          { add(getRecommendationNodepoolId(instance)); }
        })
        .ecsServiceIgnoreList(new HashSet<>() {
          { add(getRecommendationECSServiceId(instance)); }
        })
        .ec2InstanceIgnoreList(new HashSet<>() {
          { add(getRecommendationEC2InstanceId(instance)); }
        })
        .azureVmIgnoreList(new HashSet<>() {
          { add(getRecommendationAzureVmId(instance)); }
        })
        .build();
  }

  private RecommendationsIgnoreResourcesDTO getIgnoreResourcesDTO(int length, String instance) {
    if (length == 0) {
      return RecommendationsIgnoreResourcesDTO.builder()
          .workloads(Collections.emptySet())
          .nodepools(Collections.emptySet())
          .ec2Instances(Collections.emptySet())
          .ecsServices(Collections.emptySet())
          .azureVmIds(Collections.emptySet())
          .build();
    }
    return RecommendationsIgnoreResourcesDTO.builder()
        .workloads(Set.of(getRecommendationWorkloadId(instance)))
        .nodepools(Set.of(getRecommendationNodepoolId(instance)))
        .ecsServices(Set.of(getRecommendationECSServiceId(instance)))
        .ec2Instances(Set.of(getRecommendationEC2InstanceId(instance)))
        .azureVmIds(Set.of(getRecommendationAzureVmId(instance)))
        .build();
  }

  private RecommendationWorkloadId getRecommendationWorkloadId(String instance) {
    return new RecommendationWorkloadId(CLUSTER_NAME + instance, NAMESPACE + instance, WORKLOAD_NAME + instance);
  }

  private RecommendationNodepoolId getRecommendationNodepoolId(String instance) {
    return new RecommendationNodepoolId(CLUSTER_NAME + instance, NODE_POOL_NAME + instance);
  }

  private RecommendationECSServiceId getRecommendationECSServiceId(String instance) {
    return new RecommendationECSServiceId(CLUSTER_NAME + instance, SERVICE_NAME + instance);
  }

  private RecommendationEC2InstanceId getRecommendationEC2InstanceId(String instance) {
    return new RecommendationEC2InstanceId(AWS_ACCOUNT_ID + instance, INSTANCE_ID + instance);
  }

  private RecommendationAzureVmId getRecommendationAzureVmId(String instance) {
    return new RecommendationAzureVmId("subscriptionId" + instance, "resourceGroupId" + instance, "vmName" + instance);
  }
}
