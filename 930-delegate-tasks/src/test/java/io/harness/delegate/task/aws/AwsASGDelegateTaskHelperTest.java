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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGInstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGNamesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.encryption.SecretRefData;
import io.harness.exception.AwsInstanceException;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.model.AwsEC2Instance;

import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.Instance;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AwsASGDelegateTaskHelperTest extends CategoryTest {
  @Mock private AwsCallTracker tracker;
  @Mock private AmazonAutoScalingClient amazonAutoScalingClient;
  @Mock private AwsListEC2InstancesDelegateTaskHelper awsListEC2InstancesDelegateTaskHelper;
  @Mock private AwsUtils awsUtils;

  private AwsASGDelegateTaskHelper service;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    service = spy(new AwsASGDelegateTaskHelper());

    on(service).set("tracker", tracker);
    on(service).set("awsUtils", awsUtils);
    on(service).set("awsListEC2InstancesDelegateTaskHelper", awsListEC2InstancesDelegateTaskHelper);

    doNothing().when(awsUtils).decryptRequestDTOs(any(), any());
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsUtils).getAwsInternalConfig(any(), any());
    doReturn(amazonAutoScalingClient).when(awsUtils).getAmazonAutoScalingClient(any(), any());
    doNothing().when(tracker).trackEC2Call(anyString());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getInstanceIds() throws IOException {
    Instance instance = (new Instance()).withInstanceId("id");
    AutoScalingGroup autoScalingGroup = (new AutoScalingGroup()).withInstances(instance);
    DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
        (new DescribeAutoScalingGroupsResult()).withAutoScalingGroups(autoScalingGroup);

    doReturn(describeAutoScalingGroupsResult).when(amazonAutoScalingClient).describeAutoScalingGroups(any());

    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    List<String> ids = service.getInstanceIds(awsInternalConfig, "us-east-1", "GroupName");
    assertThat(ids.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getInstanceIdsFailure() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doThrow(RuntimeException.class).when(amazonAutoScalingClient).describeAutoScalingGroups(any());
    assertThatThrownBy(() -> service.getInstanceIds(awsInternalConfig, "us-east-1", "GroupName"))
        .isInstanceOf(AwsInstanceException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getInstances() throws IOException {
    List<String> ids = Arrays.asList("id");
    List<AwsEC2Instance> instances = Arrays.asList(AwsEC2Instance.builder().instanceId("id").build());

    doReturn(ids).when(service).getInstanceIds(any(), any(), any());
    doReturn(instances).when(awsListEC2InstancesDelegateTaskHelper).getInstances(any(), any(), any());

    AwsListASGInstancesTaskParamsRequest request = generateRequest();
    AwsListEC2InstancesTaskResponse response = (AwsListEC2InstancesTaskResponse) service.getInstances(request);

    assertThat(response.getInstances().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAllASGsTest() throws IOException {
    Instance instance = (new Instance()).withInstanceId("id");
    AutoScalingGroup autoScalingGroup = (new AutoScalingGroup()).withInstances(instance);
    DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
        (new DescribeAutoScalingGroupsResult()).withAutoScalingGroups(autoScalingGroup);

    doReturn(describeAutoScalingGroupsResult).when(amazonAutoScalingClient).describeAutoScalingGroups(any());

    List<AutoScalingGroup> autoScalingGroups = service.getAllASGs(generateRequest());
    assertThat(autoScalingGroups.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAllASGNamesTest() throws IOException {
    Instance instance = (new Instance()).withInstanceId("id");
    AutoScalingGroup autoScalingGroup =
        (new AutoScalingGroup()).withInstances(instance).withAutoScalingGroupName("asg-name");
    DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
        (new DescribeAutoScalingGroupsResult()).withAutoScalingGroups(autoScalingGroup);

    doReturn(describeAutoScalingGroupsResult).when(amazonAutoScalingClient).describeAutoScalingGroups(any());

    AwsListASGNamesTaskResponse response = service.getASGNames(generateRequest());
    assertThat(response.getNames().get(0)).isEqualTo("asg-name");
  }

  private AwsListASGInstancesTaskParamsRequest generateRequest() {
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

    return AwsListASGInstancesTaskParamsRequest.builder()
        .awsConnector(awsConnectorDTO)
        .encryptionDetails(Collections.emptyList())
        .awsTaskType(AwsTaskType.LIST_ASG_INSTANCES)
        .autoScalingGroupName("group")
        .region("us-east-1")
        .build();
  }
}
