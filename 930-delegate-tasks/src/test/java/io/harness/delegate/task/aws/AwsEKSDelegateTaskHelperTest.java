/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

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

import com.amazonaws.services.eks.AmazonEKSClient;
import com.amazonaws.services.eks.model.ListClustersResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetEKSClustersList() throws AwsEKSException {
    MockitoAnnotations.openMocks(this);
    doNothing().when(awsUtils).decryptRequestDTOs(any(), any());
    AwsTaskParams awsTaskParams = AwsTaskParams.builder().awsConnector(AwsConnectorDTO.builder().build()).build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsUtils).getAwsInternalConfig(any(), any());
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
}
