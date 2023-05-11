/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsListVpcTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.encryption.SecretRefData;
import io.harness.exception.AwsVPCException;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsUtils;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AwsListVpcDelegateTaskHelperTest extends CategoryTest {
  @Mock private AwsCallTracker tracker;
  @Mock private AwsUtils awsUtils;
  @Mock private AmazonEC2Client ec2Client;

  @InjectMocks private AwsListVpcDelegateTaskHelper service;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    doNothing().when(awsUtils).decryptRequestDTOs(any(), any());
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsUtils).getAwsInternalConfig(any(), any());
    doReturn(ec2Client).when(awsUtils).getAmazonEc2Client(any(), any());
    doNothing().when(tracker).trackEC2Call(anyString());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getVpcList() throws IOException {
    Vpc vpc = new Vpc().withVpcId("vpc-id").withTags(new Tag("name", "vpc-name"));
    DescribeVpcsResult describeVpcsResult = new DescribeVpcsResult().withVpcs(vpc);

    doReturn(describeVpcsResult).when(ec2Client).describeVpcs(any());

    AwsTaskParams request = generateRequest();
    AwsListVpcTaskResponse delegateResponseData = (AwsListVpcTaskResponse) service.getVpcList(request);

    assertThat(delegateResponseData.getVpcs().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getVpcListFailure() throws IOException {
    doThrow(RuntimeException.class).when(ec2Client).describeVpcs(any());

    AwsTaskParams request = generateRequest();
    assertThatThrownBy(() -> service.getVpcList(request)).isInstanceOf(AwsVPCException.class);
  }

  private AwsTaskParams generateRequest() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("test-access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();

    return AwsTaskParams.builder()
        .awsConnector(awsConnectorDTO)
        .encryptionDetails(Collections.emptyList())
        .awsTaskType(AwsTaskType.LIST_VPC)
        .region("us-east-1")
        .build();
  }
}
