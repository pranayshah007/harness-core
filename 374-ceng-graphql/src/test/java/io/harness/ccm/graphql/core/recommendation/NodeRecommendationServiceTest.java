/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.withinPercentage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.beans.recommendation.K8sServiceProvider;
import io.harness.ccm.commons.beans.recommendation.NodePoolId;
import io.harness.ccm.commons.beans.recommendation.TotalResourceUsage;
import io.harness.ccm.commons.beans.recommendation.models.NodePool;
import io.harness.ccm.commons.beans.recommendation.models.RecommendClusterRequest;
import io.harness.ccm.commons.beans.recommendation.models.RecommendNodePoolClusterRequest;
import io.harness.ccm.commons.beans.recommendation.models.RecommendationResponse;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sNodeRecommendation;
import io.harness.ccm.graphql.dto.recommendation.NodeRecommendationDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.assertj.core.data.Percentage;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NodeRecommendationServiceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ID = "id0";

  private static final Percentage doubleOffset = withinPercentage(5);

  @Mock private K8sRecommendationDAO k8sRecommendationDAO;
  @InjectMocks private NodeRecommendationService nodeRecommendationService;

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testGetDefaultRecommendation() throws Exception {
    K8sNodeRecommendation nodeRecommendation =
        K8sNodeRecommendation.builder()
            .currentServiceProvider(K8sServiceProvider.builder()
                                        .nodeCount(5)
                                        .cloudProvider(CloudProvider.GCP)
                                        .costPerVmPerHr(10D)
                                        .spotCostPerVmPerHr(5D)
                                        .cpusPerVm(4D)
                                        .memPerVm(64D)
                                        .instanceCategory(InstanceCategory.SPOT)
                                        .instanceFamily("n1-standard-2")
                                        .region("us-west-1")
                                        .build())
            .recommendClusterRequest(
                RecommendClusterRequest.builder().sumCpu(4D).sumMem(64D).minNodes(3L).maxNodes(10L).build())
            .recommendation(RecommendationResponse.builder()
                                .service(CloudProvider.GCP.getK8sService())
                                .provider(CloudProvider.GCP.getCloudProviderName())
                                .build())
            .build();

    when(k8sRecommendationDAO.fetchNodeRecommendationById(eq(ACCOUNT_ID), eq(ID)))
        .thenReturn(Optional.ofNullable(nodeRecommendation));

    NodeRecommendationDTO nodeRecommendationDTO = nodeRecommendationService.getRecommendation(ACCOUNT_ID, ID);

    assertThat(nodeRecommendationDTO).isNotNull();
    assertThat(nodeRecommendationDTO.getRecommended()).isNotNull();
    assertThat(nodeRecommendationDTO.getRecommended().getService()).isEqualTo("gke");
    assertThat(nodeRecommendationDTO.getRecommended().getProvider()).isEqualTo("google");

    assertThat(nodeRecommendationDTO.getCurrent()).isNotNull();
    assertThat(nodeRecommendationDTO.getCurrent().getRegion()).isEqualTo("us-west-1");
    assertThat(nodeRecommendationDTO.getCurrent().getInstanceCategory()).isEqualTo(InstanceCategory.SPOT);
    assertThat(nodeRecommendationDTO.getCurrent().getNodePools()).hasSize(1);

    final NodePool nodePool = nodeRecommendationDTO.getCurrent().getNodePools().get(0);
    assertThat(nodePool.getSumNodes()).isEqualTo(5L);
    assertThat(nodePool.getVm().getType()).isEqualTo("n1-standard-2");
    assertThat(nodePool.getVm().getOnDemandPrice()).isEqualTo(10D);
    assertThat(nodePool.getVm().getAvgPrice()).isEqualTo(5D);
    assertThat(nodePool.getVm().getCpusPerVm()).isEqualTo(4D);
    assertThat(nodePool.getVm().getMemPerVm()).isEqualTo(64D);

    assertThat(nodeRecommendationDTO.getResourceRequirement().getSumCpu()).isCloseTo(4D, doubleOffset);
    assertThat(nodeRecommendationDTO.getResourceRequirement().getSumMem()).isCloseTo(64D, doubleOffset);
    assertThat(nodeRecommendationDTO.getResourceRequirement().getMinNodes()).isCloseTo(3L, doubleOffset);
    assertThat(nodeRecommendationDTO.getResourceRequirement().getMaxNodes()).isCloseTo(10L, doubleOffset);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testConstructRecommendationRequest() throws Exception {
    when(k8sRecommendationDAO.aggregateTotalResourceRequirement(eq(ACCOUNT_ID), any(), any(), any()))
        .thenReturn(TotalResourceUsage.builder()
                        .sumcpu(10D * 1024D)
                        .summemory(64D * 1024D)
                        .maxcpu(2D * 1024D)
                        .maxmemory(8D * 1024D)
                        .build());

    RecommendNodePoolClusterRequest recommendNodePoolClusterRequest =
        nodeRecommendationService.constructRecommendationRequest(ACCOUNT_ID,
            NodePoolId.builder().clusterid("cId").nodepoolname("npName").build(), java.time.OffsetDateTime.now(),
            java.time.OffsetDateTime.now());

    RecommendClusterRequest request = recommendNodePoolClusterRequest.getRecommendClusterRequest();

    TotalResourceUsage totalResourceUsage = recommendNodePoolClusterRequest.getTotalResourceUsage();
    assertThat(request).isNotNull();

    assertThat(request.getSumCpu()).isCloseTo(10D, doubleOffset);
    assertThat(request.getSumMem()).isCloseTo(64D, doubleOffset);

    assertThat(request.getMaxNodes()).isEqualTo(5L);
    assertThat(request.getMinNodes()).isEqualTo(3L);

    assertThat(totalResourceUsage.getMaxcpu()).isEqualTo(2L);
    assertThat(totalResourceUsage.getMaxmemory()).isEqualTo(8L);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testInvalidConstructRecommendationRequest() throws Exception {
    when(k8sRecommendationDAO.aggregateTotalResourceRequirement(eq(ACCOUNT_ID), any(), any(), any()))
        .thenReturn(TotalResourceUsage.builder()
                        .sumcpu(10D * 1024D)
                        .summemory(8D * 1024D)
                        .maxcpu(2D * 1024D)
                        .maxmemory(8.001D * 1024D)
                        .build());

    assertThatThrownBy(()
                           -> nodeRecommendationService
                                  .constructRecommendationRequest(ACCOUNT_ID,
                                      NodePoolId.builder().clusterid("cId").nodepoolname("npName").build(),
                                      OffsetDateTime.now(), OffsetDateTime.now())
                                  .getRecommendClusterRequest())
        .isExactlyInstanceOf(InvalidRequestException.class);
  }
}
