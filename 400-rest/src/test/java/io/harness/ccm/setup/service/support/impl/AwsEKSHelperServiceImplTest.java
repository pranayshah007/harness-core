/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.setup.service.support.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.amazonaws.services.eks.AmazonEKSClient;
import com.amazonaws.services.eks.model.AmazonEKSException;
import com.amazonaws.services.eks.model.ListClustersRequest;
import com.amazonaws.services.eks.model.ListClustersResult;
import com.amazonaws.services.securitytoken.model.AWSSecurityTokenServiceException;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsEKSHelperServiceImplTest extends CategoryTest {
  @Spy private AwsEKSHelperServiceImpl awsEKSHelperService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListEKSClusters() {
    String nextToken = "nextClusterToken";
    AmazonEKSClient mockClient = mock(AmazonEKSClient.class);
    doReturn(mockClient).when(awsEKSHelperService).getAmazonEKSClient(any(), any());
    doReturn(new ListClustersResult().withClusters(ImmutableList.of("cluster1", "cluster2")).withNextToken(nextToken))
        .when(mockClient)
        .listClusters(new ListClustersRequest().withNextToken(null));

    doReturn(new ListClustersResult().withClusters(ImmutableList.of("cluster3", "cluster4")).withNextToken(null))
        .when(mockClient)
        .listClusters(new ListClustersRequest().withNextToken(nextToken));

    List<String> eksClusters = awsEKSHelperService.listEKSClusters(any(), any());
    assertThat(eksClusters).hasSize(4);
    assertThat(eksClusters.get(0)).isEqualTo("cluster1");
    assertThat(eksClusters.get(1)).isEqualTo("cluster2");
    assertThat(eksClusters.get(2)).isEqualTo("cluster3");
    assertThat(eksClusters.get(3)).isEqualTo("cluster4");
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListEmptyEKSClustersWhenExceptionOccur() {
    AmazonEKSClient mockClient = mock(AmazonEKSClient.class);
    doReturn(mockClient).when(awsEKSHelperService).getAmazonEKSClient(any(), any());

    doThrow(new AmazonEKSException("EKS Exception"))
        .when(mockClient)
        .listClusters(new ListClustersRequest().withNextToken(null));
    List<String> eksClusters = awsEKSHelperService.listEKSClusters(any(), any());
    assertThat(eksClusters).hasSize(0);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testVerifyAccessWhenAccessIsNotGiven() {
    AmazonEKSClient mockClient = mock(AmazonEKSClient.class);
    doReturn(mockClient).when(awsEKSHelperService).getAmazonEKSClient(any(), any());

    doThrow(new AWSSecurityTokenServiceException("Security Exception"))
        .when(mockClient)
        .listClusters(new ListClustersRequest().withNextToken(null));
    boolean verifyAccess = awsEKSHelperService.verifyAccess(any(), any());
    assertThat(verifyAccess).isFalse();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testVerifyAccessWhenAccessIsGiven() {
    AmazonEKSClient mockClient = mock(AmazonEKSClient.class);
    doReturn(mockClient).when(awsEKSHelperService).getAmazonEKSClient(any(), any());

    doReturn(new ListClustersResult().withClusters(ImmutableList.of("cluster3", "cluster4")).withNextToken(null))
        .when(mockClient)
        .listClusters(new ListClustersRequest().withNextToken(null));
    boolean verifyAccess = awsEKSHelperService.verifyAccess(any(), any());
    assertThat(verifyAccess).isTrue();
  }
}
