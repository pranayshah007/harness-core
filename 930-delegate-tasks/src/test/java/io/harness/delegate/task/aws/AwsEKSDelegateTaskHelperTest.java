/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsListClustersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.exception.AwsEKSException;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsUtils;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.eks.AmazonEKSClient;
import com.amazonaws.services.eks.model.AccessDeniedException;
import com.amazonaws.services.eks.model.ListClustersResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsEKSDelegateTaskHelperTest extends CategoryTest {
  @Mock private AmazonEKSClient amazonEKSClient;
  @Mock private AwsUtils awsUtils;
  @Spy @InjectMocks private AwsEKSDelegateTaskHelper awsEKSDelegateTaskHelper;

  private AutoCloseable autoCloseable;
  private AwsTaskParams awsTaskParams;

  @Before
  public void setup() {
    autoCloseable = MockitoAnnotations.openMocks(this);
    doNothing().when(awsUtils).decryptRequestDTOs(any(), any());
    awsTaskParams = AwsTaskParams.builder().awsConnector(AwsConnectorDTO.builder().build()).build();
    doReturn(AwsInternalConfig.builder().build()).when(awsUtils).getAwsInternalConfig(any(), any());
  }

  @After
  public void teardown() throws Exception {
    autoCloseable.close();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetEKSClustersList() throws AwsEKSException {
    List<String> regions = new ArrayList<>();
    regions.add("us-east-1");
    doReturn(regions).when(awsUtils).listAwsRegionsForGivenAccount(any());

    doReturn(amazonEKSClient).when(awsUtils).getAmazonEKSClient(any(), any());

    ListClustersResult listClustersResult = new ListClustersResult().withNextToken(null).withClusters("c1");
    doReturn(listClustersResult).when(amazonEKSClient).listClusters(any());

    DelegateResponseData delegateResponseData = awsEKSDelegateTaskHelper.getEKSClustersList(awsTaskParams);
    AwsListClustersTaskResponse awsListClustersTaskResponse = AwsListClustersTaskResponse.builder()
                                                                  .clusters(Collections.singletonList("us-east-1/c1"))
                                                                  .commandExecutionStatus(SUCCESS)
                                                                  .build();
    assertThat(delegateResponseData).isEqualTo(awsListClustersTaskResponse);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testEksListClustersFailingForAllRegions() {
    List<String> regions = List.of("us-east-1", "ap-south-1");
    doReturn(regions).when(awsUtils).listAwsRegionsForGivenAccount(any());
    doReturn(amazonEKSClient).when(awsUtils).getAmazonEKSClient(any(), any());
    String errorMessage = "you do not have access";
    doThrow(new AccessDeniedException(errorMessage)).when(amazonEKSClient).listClusters(any());

    assertThatThrownBy(() -> awsEKSDelegateTaskHelper.getEKSClustersList(awsTaskParams))
        .isInstanceOf(AwsEKSException.class)
        .hasMessageContaining(regions.get(0))
        .hasMessageContaining(regions.get(1))
        .hasMessageContaining(errorMessage);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testEksListClustersFailingForSomeRegions() {
    List<String> regions = List.of("us-east-1", "ap-south-1");
    doReturn(regions).when(awsUtils).listAwsRegionsForGivenAccount(any());

    AmazonEKSClient useast1Client = mock(AmazonEKSClient.class);
    AmazonEKSClient apsouth1Client = mock(AmazonEKSClient.class);

    doReturn(useast1Client).when(awsUtils).getAmazonEKSClient(eq(Regions.fromName("us-east-1")), any());
    doReturn(apsouth1Client).when(awsUtils).getAmazonEKSClient(eq(Regions.fromName("ap-south-1")), any());

    doThrow(new AccessDeniedException("errorMessage")).when(useast1Client).listClusters(any());
    doReturn(new ListClustersResult().withNextToken(null).withClusters("c1", "c2"))
        .when(apsouth1Client)
        .listClusters(any());

    AwsListClustersTaskResponse response =
        (AwsListClustersTaskResponse) awsEKSDelegateTaskHelper.getEKSClustersList(awsTaskParams);
    assertThat(response.getClusters().size()).isEqualTo(2);
    assertThat(response.getClusters()).contains("ap-south-1/c1", "ap-south-1/c2");
  }
}
