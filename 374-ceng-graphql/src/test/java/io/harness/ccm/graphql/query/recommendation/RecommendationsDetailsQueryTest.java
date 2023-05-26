/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.recommendation;

import static io.harness.ccm.RecommenderUtils.CPU_HISTOGRAM_FIRST_BUCKET_SIZE;
import static io.harness.ccm.RecommenderUtils.HISTOGRAM_BUCKET_SIZE_GROWTH;
import static io.harness.ccm.RecommenderUtils.MEMORY_HISTOGRAM_FIRST_BUCKET_SIZE;
import static io.harness.rule.OwnerRule.ANMOL;
import static io.harness.rule.OwnerRule.TRUNAPUSHPA;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.NodePoolId;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.beans.recommendation.TotalResourceUsage;
import io.harness.ccm.commons.beans.recommendation.models.NodePool;
import io.harness.ccm.commons.beans.recommendation.models.RecommendClusterRequest;
import io.harness.ccm.commons.beans.recommendation.models.RecommendNodePoolClusterRequest;
import io.harness.ccm.commons.beans.recommendation.models.RecommendationResponse;
import io.harness.ccm.commons.beans.recommendation.models.VirtualMachine;
import io.harness.ccm.graphql.core.recommendation.AzureVmRecommendationService;
import io.harness.ccm.graphql.core.recommendation.EC2RecommendationService;
import io.harness.ccm.graphql.core.recommendation.ECSRecommendationService;
import io.harness.ccm.graphql.core.recommendation.NodeRecommendationService;
import io.harness.ccm.graphql.core.recommendation.RuleRecommendationService;
import io.harness.ccm.graphql.core.recommendation.WorkloadRecommendationService;
import io.harness.ccm.graphql.dto.recommendation.AzureVmRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.ContainerHistogramDTO;
import io.harness.ccm.graphql.dto.recommendation.EC2RecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.ECSRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.NodeRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationDetailsDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationItemDTO;
import io.harness.ccm.graphql.dto.recommendation.RuleRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.WorkloadRecommendationDTO;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;

import com.google.common.collect.ImmutableList;
import java.math.BigDecimal;
import java.util.Collections;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RecommendationsDetailsQueryTest extends CategoryTest {
  private static final int NUM_BUCKET = 6;
  private static final int MIN_BUCKET = 7;
  private static final int MAX_BUCKET = 8;
  private static final double TOTAL_WEIGHT = 9;

  private static final String ACCOUNT_ID = "accountId";
  private static final String NAME = "name";
  private static final String CLUSTER_NAME = "clusterName";
  private static final Double MONTHLY_COST = 100D;
  private static final Double MONTHLY_SAVING = 40D;
  private static final String CONTAINER_NAME = "containerName";
  private static final String ID = "id0";
  private static final String INSTANCE_TYPE = "n1-standard-4";
  private static final String UUID = "5fd2b09ea2a4931e7822e8d8";

  @Mock private GraphQLUtils graphQLUtils;
  @Mock private WorkloadRecommendationService workloadRecommendationService;
  @Mock private NodeRecommendationService nodeRecommendationService;
  @Mock private ECSRecommendationService ecsRecommendationService;
  @Mock private AzureVmRecommendationService azureVmRecommendationService;
  @Mock private EC2RecommendationService ec2RecommendationService;
  @Mock private RuleRecommendationService ruleRecommendationService;
  @InjectMocks private RecommendationsDetailsQuery detailsQuery;

  private static final Cost cost =
      Cost.builder().memory(BigDecimal.valueOf(0.116)).cpu(BigDecimal.valueOf(0.4678)).build();

  @Before
  public void setUp() throws Exception {
    when(graphQLUtils.getAccountIdentifier(any())).thenReturn(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetRecommendationDetailsByIdInRecommendationItemContext() {
    when(workloadRecommendationService.getWorkloadRecommendationById(eq(ACCOUNT_ID), eq(ID), any(), any()))
        .thenReturn(createWorkloadRecommendation());

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(createRecommendationItem(ID), null, null, 0L, null);

    assertWorkloadRecommendationDetails(recommendationDetails);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetRecommendationDetailsByIdAsParentQueryNotFound() {
    when(workloadRecommendationService.getWorkloadRecommendationById(eq(ACCOUNT_ID), eq(ID), any(), any()))
        .thenReturn(WorkloadRecommendationDTO.builder().items(Collections.emptyList()).build());

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(ID, ResourceType.WORKLOAD, null, null, 0L, null);

    assertWorkloadRecommendationByIdNotFound(recommendationDetails);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetRecommendationDetailsByIdAsParentQuery() {
    when(workloadRecommendationService.getWorkloadRecommendationById(eq(ACCOUNT_ID), eq(ID), any(), any()))
        .thenReturn(createWorkloadRecommendation());

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(ID, ResourceType.WORKLOAD, null, null, 0L, null);

    assertWorkloadRecommendationDetails(recommendationDetails);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetRecommendationDetailsByIdInRecommendationItemContextNotFound() {
    when(workloadRecommendationService.getWorkloadRecommendationById(eq(ACCOUNT_ID), eq(ID), any(), any()))
        .thenReturn(WorkloadRecommendationDTO.builder().items(Collections.emptyList()).build());

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(createRecommendationItem(ID), null, null, 0L, null);

    assertWorkloadRecommendationByIdNotFound(recommendationDetails);
  }

  private void assertWorkloadRecommendationByIdNotFound(final RecommendationDetailsDTO recommendationDetails) {
    assertThat(recommendationDetails).isNotNull();
    assertThat(recommendationDetails).isExactlyInstanceOf(WorkloadRecommendationDTO.class);
    assertThat(recommendationDetails)
        .isEqualTo(WorkloadRecommendationDTO.builder().items(Collections.emptyList()).build());
  }

  private static WorkloadRecommendationDTO createWorkloadRecommendation() {
    return WorkloadRecommendationDTO.builder()
        .items(ImmutableList.of(createContainerHistogram()))
        .lastDayCost(cost)
        .build();
  }

  private static ContainerHistogramDTO createContainerHistogram() {
    return ContainerHistogramDTO.builder()
        .containerName(CONTAINER_NAME)
        .memoryHistogram(ContainerHistogramDTO.HistogramExp.builder()
                             .numBuckets(NUM_BUCKET)
                             .minBucket(MIN_BUCKET)
                             .maxBucket(MAX_BUCKET)
                             .firstBucketSize(MEMORY_HISTOGRAM_FIRST_BUCKET_SIZE)
                             .growthRatio(HISTOGRAM_BUCKET_SIZE_GROWTH)
                             .bucketWeights(new double[] {1, 2})
                             .precomputed(new double[] {2, 3})
                             .totalWeight(TOTAL_WEIGHT)
                             .build())
        .cpuHistogram(ContainerHistogramDTO.HistogramExp.builder()
                          .numBuckets(NUM_BUCKET)
                          .minBucket(MIN_BUCKET)
                          .maxBucket(MAX_BUCKET)
                          .firstBucketSize(CPU_HISTOGRAM_FIRST_BUCKET_SIZE)
                          .growthRatio(HISTOGRAM_BUCKET_SIZE_GROWTH)
                          .bucketWeights(new double[] {3, 4})
                          .precomputed(new double[] {4, 5})
                          .totalWeight(TOTAL_WEIGHT)
                          .build())
        .build();
  }
  private void assertWorkloadRecommendationDetails(final RecommendationDetailsDTO recommendationDetails) {
    assertThat(recommendationDetails).isNotNull();
    assertThat(recommendationDetails).isExactlyInstanceOf(WorkloadRecommendationDTO.class);

    final WorkloadRecommendationDTO workloadRecommendationDTO = (WorkloadRecommendationDTO) recommendationDetails;
    assertThat(workloadRecommendationDTO.getLastDayCost()).isEqualTo(cost);
    assertThat(workloadRecommendationDTO.getItems()).hasSize(1);
    assertThat(workloadRecommendationDTO.getItems().get(0).getContainerName()).isEqualTo(CONTAINER_NAME);

    final ContainerHistogramDTO.HistogramExp cpuHistogram =
        workloadRecommendationDTO.getItems().get(0).getCpuHistogram();

    assertHistogram(cpuHistogram, CPU_HISTOGRAM_FIRST_BUCKET_SIZE);
    assertThat(cpuHistogram.getBucketWeights()[0]).isEqualTo(3.0);
    assertThat(cpuHistogram.getBucketWeights()[1]).isEqualTo(4.0);

    final ContainerHistogramDTO.HistogramExp memoryHistogram =
        workloadRecommendationDTO.getItems().get(0).getMemoryHistogram();

    assertHistogram(memoryHistogram, MEMORY_HISTOGRAM_FIRST_BUCKET_SIZE);
    assertThat(memoryHistogram.getBucketWeights()[0]).isEqualTo(1.0);
    assertThat(memoryHistogram.getBucketWeights()[1]).isEqualTo(2.0);
  }

  private void assertHistogram(ContainerHistogramDTO.HistogramExp histogramExp, double firstBucketSize) {
    assertThat(histogramExp.getFirstBucketSize()).isEqualTo(firstBucketSize);
    assertThat(histogramExp.getMinBucket()).isEqualTo(MIN_BUCKET);
    assertThat(histogramExp.getMaxBucket()).isEqualTo(MAX_BUCKET);
    assertThat(histogramExp.getGrowthRatio()).isEqualTo(HISTOGRAM_BUCKET_SIZE_GROWTH);
    assertThat(histogramExp.getTotalWeight()).isEqualTo(TOTAL_WEIGHT);
    assertThat(histogramExp.getNumBuckets()).isEqualTo(NUM_BUCKET);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testGetNodeRecommendationDetailsWithItemContext() throws Exception {
    when(nodeRecommendationService.getRecommendation(eq(ACCOUNT_ID), eq(ID))).thenReturn(createNodeRecommendation());

    RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(createRecommendationItem(ID, ResourceType.NODE_POOL), null, null, 0L, null);

    assertNodeRecommendationDetails(recommendationDetails);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testGetNodeRecommendationDetailsById() throws Exception {
    when(nodeRecommendationService.getRecommendation(eq(ACCOUNT_ID), eq(ID))).thenReturn(createNodeRecommendation());

    RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(ID, ResourceType.NODE_POOL, null, null, 0L, null);

    assertNodeRecommendationDetails(recommendationDetails);
  }

  private void assertNodeRecommendationDetails(RecommendationDetailsDTO recommendationDetails) {
    assertThat(recommendationDetails).isNotNull();
    assertThat(recommendationDetails).isExactlyInstanceOf(NodeRecommendationDTO.class);

    NodeRecommendationDTO nodeRecommendationDTO = (NodeRecommendationDTO) recommendationDetails;

    assertThat(nodeRecommendationDTO.getId()).isEqualTo(ID);

    assertThat(nodeRecommendationDTO.getRecommended().getNodePools()).hasSize(1);
    assertThat(nodeRecommendationDTO.getRecommended().getNodePools().get(0).getVm().getType()).isEqualTo(INSTANCE_TYPE);

    assertThat(nodeRecommendationDTO.getResourceRequirement()).isNotNull();
    assertThat(nodeRecommendationDTO.getResourceRequirement().getMaxNodes()).isEqualTo(10L);
    assertThat(nodeRecommendationDTO.getResourceRequirement().getMinNodes()).isEqualTo(3L);
    assertThat(nodeRecommendationDTO.getResourceRequirement().getSumCpu()).isEqualTo(3.0D);

    assertThat(nodeRecommendationDTO.getNodePoolId()).isNotNull();
    assertThat(nodeRecommendationDTO.getNodePoolId().getClusterid()).isEqualTo(CLUSTER_NAME);
    assertThat(nodeRecommendationDTO.getNodePoolId().getNodepoolname()).isEqualTo(NAME);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testNodeRecommendationRequest() throws Exception {
    NodePoolId nodePoolId = NodePoolId.builder().clusterid("cId").nodepoolname("npName").build();
    when(nodeRecommendationService.constructRecommendationRequest(eq(ACCOUNT_ID), eq(nodePoolId), any(), any()))
        .thenReturn(RecommendNodePoolClusterRequest.builder()
                        .totalResourceUsage(
                            TotalResourceUsage.builder().maxcpu(2D).maxmemory(8D).sumcpu(4D).summemory(16D).build())
                        .recommendClusterRequest(RecommendClusterRequest.builder()
                                                     .sumCpu(20D)
                                                     .sumMem(64D)
                                                     .maxNodes(7L)
                                                     .minNodes(3L)
                                                     .onDemandPct(100L)
                                                     .build())
                        .build());

    RecommendNodePoolClusterRequest recommendNodePoolClusterRequest =
        detailsQuery.nodeRecommendationRequest(nodePoolId, null, null, null);
    final RecommendClusterRequest request = recommendNodePoolClusterRequest.getRecommendClusterRequest();
    final TotalResourceUsage totalResourceUsage = recommendNodePoolClusterRequest.getTotalResourceUsage();

    assertThat(request).isNotNull();
    assertThat(request.getMinNodes()).isEqualTo(3L);
    assertThat(request.getMaxNodes()).isEqualTo(7L);
    assertThat(request.getSumCpu()).isEqualTo(20D);
    assertThat(request.getSumMem()).isEqualTo(64D);

    assertThat(totalResourceUsage).isNotNull();
    assertThat(totalResourceUsage.getMaxcpu()).isEqualTo(2D);
    assertThat(totalResourceUsage.getMaxmemory()).isEqualTo(8D);
    assertThat(totalResourceUsage.getSumcpu()).isEqualTo(4D);
    assertThat(totalResourceUsage.getSummemory()).isEqualTo(16D);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetECSRecommendationDetailsByIdAsParentQueryNotFound() {
    when(ecsRecommendationService.getECSRecommendationById(eq(ACCOUNT_ID), eq(ID), any(), any(), any()))
        .thenReturn(ECSRecommendationDTO.builder().build());

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(ID, ResourceType.ECS_SERVICE, null, null, 0L, null);

    assertECSRecommendationByIdNotFound(recommendationDetails);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetECSRecommendationDetailsByIdAsParentQuery() {
    when(ecsRecommendationService.getECSRecommendationById(eq(ACCOUNT_ID), eq(ID), any(), any(), any()))
        .thenReturn(createECSRecommendation());

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(ID, ResourceType.ECS_SERVICE, null, null, 0L, null);

    assertECSRecommendationDetails(recommendationDetails);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetECSRecommendationDetailsByIdInRecommendationItemContextNotFound() {
    when(ecsRecommendationService.getECSRecommendationById(eq(ACCOUNT_ID), eq(ID), any(), any(), any()))
        .thenReturn(ECSRecommendationDTO.builder().build());

    final RecommendationDetailsDTO recommendationDetails = detailsQuery.recommendationDetails(
        createRecommendationItem(ID, ResourceType.ECS_SERVICE), null, null, 0L, null);

    assertECSRecommendationByIdNotFound(recommendationDetails);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetRecommendationDetailsForAzureInstance() {
    when(azureVmRecommendationService.getAzureVmRecommendationById(eq(ACCOUNT_ID), eq(ID)))
        .thenReturn(AzureVmRecommendationDTO.builder().id(ID).build());

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(ID, ResourceType.AZURE_INSTANCE, null, null, 0L, null);

    assertThat(recommendationDetails).isNotNull();
    assertThat(recommendationDetails).isExactlyInstanceOf(AzureVmRecommendationDTO.class);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetRecommendationDetailsForAzureInstance_GetAzureVmRecommendationByIdReturnsNull() {
    when(azureVmRecommendationService.getAzureVmRecommendationById(eq(ACCOUNT_ID), eq(ID))).thenReturn(null);

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(ID, ResourceType.AZURE_INSTANCE, null, null, 0L, null);

    assertThat(recommendationDetails).isNull();
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetRecommendationDetailsForEC2Instance() {
    when(ec2RecommendationService.getEC2RecommendationById(eq(ACCOUNT_ID), eq(ID)))
        .thenReturn(EC2RecommendationDTO.builder().id(ID).build());

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(ID, ResourceType.EC2_INSTANCE, null, null, 0L, null);

    assertThat(recommendationDetails).isNotNull();
    assertThat(recommendationDetails).isExactlyInstanceOf(EC2RecommendationDTO.class);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetRecommendationDetailsForEC2Instance_GetEC2RecommendationByIdReturnsNull() {
    when(ec2RecommendationService.getEC2RecommendationById(eq(ACCOUNT_ID), eq(ID))).thenReturn(null);

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(ID, ResourceType.EC2_INSTANCE, null, null, 0L, null);

    assertThat(recommendationDetails).isNull();
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetRecommendationDetailsForGovernance() {
    when(ruleRecommendationService.getRuleRecommendation(eq(UUID), eq(ACCOUNT_ID)))
        .thenReturn(RuleRecommendationDTO.builder().uuid(new ObjectId(UUID)).build());

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(UUID, ResourceType.GOVERNANCE, null, null, 0L, null);

    assertThat(recommendationDetails).isNotNull();
    assertThat(recommendationDetails).isExactlyInstanceOf(RuleRecommendationDTO.class);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetRecommendationDetailsForGovernance_GetRuleRecommendationReturnsNull() {
    when(ruleRecommendationService.getRuleRecommendation(eq(ACCOUNT_ID), eq(ID))).thenReturn(null);

    final RecommendationDetailsDTO recommendationDetails =
        detailsQuery.recommendationDetails(ID, ResourceType.GOVERNANCE, null, null, 0L, null);

    assertThat(recommendationDetails).isNull();
  }

  private static ECSRecommendationDTO createECSRecommendation() {
    return ECSRecommendationDTO.builder()
        .lastDayCost(cost)
        .memoryHistogram(ContainerHistogramDTO.HistogramExp.builder()
                             .numBuckets(NUM_BUCKET)
                             .minBucket(MIN_BUCKET)
                             .maxBucket(MAX_BUCKET)
                             .bucketWeights(new double[] {1, 2})
                             .precomputed(new double[] {2, 3})
                             .totalWeight(TOTAL_WEIGHT)
                             .build())
        .cpuHistogram(ContainerHistogramDTO.HistogramExp.builder()
                          .numBuckets(NUM_BUCKET)
                          .minBucket(MIN_BUCKET)
                          .maxBucket(MAX_BUCKET)
                          .bucketWeights(new double[] {3, 4})
                          .precomputed(new double[] {4, 5})
                          .totalWeight(TOTAL_WEIGHT)
                          .build())
        .lastDayCost(cost)
        .build();
  }

  private void assertECSRecommendationByIdNotFound(final RecommendationDetailsDTO recommendationDetails) {
    assertThat(recommendationDetails).isNotNull();
    assertThat(recommendationDetails).isExactlyInstanceOf(ECSRecommendationDTO.class);
    assertThat(recommendationDetails).isEqualTo(ECSRecommendationDTO.builder().build());
  }

  private void assertECSRecommendationDetails(final RecommendationDetailsDTO recommendationDetails) {
    assertThat(recommendationDetails).isNotNull();
    assertThat(recommendationDetails).isExactlyInstanceOf(ECSRecommendationDTO.class);

    final ECSRecommendationDTO ecsRecommendationDTO = (ECSRecommendationDTO) recommendationDetails;
    assertThat(ecsRecommendationDTO.getLastDayCost()).isEqualTo(cost);

    final ContainerHistogramDTO.HistogramExp cpuHistogram = ecsRecommendationDTO.getCpuHistogram();

    assertHistogram(cpuHistogram);
    assertThat(cpuHistogram.getBucketWeights()[0]).isEqualTo(3.0);
    assertThat(cpuHistogram.getBucketWeights()[1]).isEqualTo(4.0);

    final ContainerHistogramDTO.HistogramExp memoryHistogram = ecsRecommendationDTO.getMemoryHistogram();

    assertThat(memoryHistogram.getBucketWeights()[0]).isEqualTo(1.0);
    assertThat(memoryHistogram.getBucketWeights()[1]).isEqualTo(2.0);
  }

  private void assertHistogram(ContainerHistogramDTO.HistogramExp histogramExp) {
    assertThat(histogramExp.getMinBucket()).isEqualTo(MIN_BUCKET);
    assertThat(histogramExp.getMaxBucket()).isEqualTo(MAX_BUCKET);
    assertThat(histogramExp.getTotalWeight()).isEqualTo(TOTAL_WEIGHT);
    assertThat(histogramExp.getNumBuckets()).isEqualTo(NUM_BUCKET);
  }

  public static NodeRecommendationDTO createNodeRecommendation() {
    return NodeRecommendationDTO.builder()
        .id(ID)
        .recommended(RecommendationResponse.builder().nodePools(Collections.singletonList(createPool())).build())
        .resourceRequirement(createRequest())
        .nodePoolId(NodePoolId.builder().clusterid(CLUSTER_NAME).nodepoolname(NAME).build())
        .build();
  }

  private static NodePool createPool() {
    return NodePool.builder()
        .vm(VirtualMachine.builder()
                .allocatableCpusPerVm(1.45D)
                .avgPrice(9.0)
                .onDemandPrice(10.0)
                .cpusPerVm(4D)
                .memPerVm(64D)
                .type(INSTANCE_TYPE)
                .build())
        .build();
  }

  private static RecommendClusterRequest createRequest() {
    return RecommendClusterRequest.builder()
        .maxNodes(10L)
        .minNodes(3L)
        .onDemandPct(100L)
        .sumCpu(3.0D)
        .sumMem(30.0D)
        .build();
  }

  private static RecommendationItemDTO createRecommendationItem(String id) {
    return createRecommendationItem(id, ResourceType.WORKLOAD);
  }

  private static RecommendationItemDTO createRecommendationItem(String id, ResourceType resourceType) {
    return RecommendationItemDTO.builder()
        .id(id)
        .clusterName(CLUSTER_NAME)
        .monthlyCost(MONTHLY_COST)
        .monthlySaving(MONTHLY_SAVING)
        .resourceName(NAME)
        .recommendationDetails(null)
        .resourceType(resourceType)
        .build();
  }
}
