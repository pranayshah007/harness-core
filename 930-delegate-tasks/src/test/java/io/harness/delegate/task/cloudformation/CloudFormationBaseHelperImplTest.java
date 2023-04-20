/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.TMACARI;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.AwsCloudformationPrintHelper;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.cf.DeployStackRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.helpers.ext.cloudformation.response.ExistingStackInfo;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;

import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class CloudFormationBaseHelperImplTest extends CategoryTest {
  @Mock private AWSCloudformationClient awsCloudformationClient;
  @Mock private AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
  @Mock private SecretDecryptionService encryptionService;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private LogCallback logCallback;
  @Mock private AwsCloudformationPrintHelper awsCloudformationPrintHelper;
  @InjectMocks private CloudformationBaseHelperImpl cloudFormationBaseHelper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testPrintStackEvents() {
    String stackName = "HarnessStack-test";
    long stackEventTs = 1000;
    Date timeStamp = new Date();
    Stack testStack = new Stack().withStackStatus("CREATE_COMPLETE").withStackName(stackName);
    LogCallback logCallback = mock(ExecutionLogCallback.class);
    StackEvent stackEvent = new StackEvent()
                                .withStackName(stackName)
                                .withEventId("id")
                                .withResourceStatusReason("statusReason")
                                .withTimestamp(timeStamp);
    when(awsCloudformationClient.getAllStackEvents(any(), any(), any(), anyLong()))
        .thenReturn(singletonList(stackEvent));
    cloudFormationBaseHelper.printStackEvents(
        AwsInternalConfig.builder().build(), "us-east-1", stackEventTs, testStack, logCallback);
    verify(awsCloudformationClient).getAllStackEvents(any(), any(), any(), anyLong());
    verify(awsCloudformationPrintHelper).printStackEvents(any(), anyLong(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetCloudformationTags() throws IOException {
    String tagsJson = "[{\"key\":\"testKey\",\"value\":\"testValue\"}]";
    List<Tag> tags = cloudFormationBaseHelper.getCloudformationTags(tagsJson);
    assertThat(tags.size()).isEqualTo(1);
    List<Tag> emptyTags = cloudFormationBaseHelper.getCloudformationTags("");
    assertThat(emptyTags).isNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetCapabilities() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(Arrays.asList("RETURNED_CAPABILITY"))
        .when(awsCFHelperServiceDelegate)
        .getCapabilities(any(), any(), any(), any());
    Set<String> capabilities = cloudFormationBaseHelper.getCapabilities(
        awsInternalConfig, "region", "templateUrl", Arrays.asList("USER_CAPABILITY"), "s3");
    verify(awsCFHelperServiceDelegate).getCapabilities(eq(awsInternalConfig), anyString(), anyString(), anyString());
    assertThat(capabilities.size()).isEqualTo(2);
    assertThat(capabilities).contains("RETURNED_CAPABILITY", "USER_CAPABILITY");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testPrintStackResources() {
    String stackName = "HarnessStack-test";
    Date timeStamp = new Date();
    Stack stack = new Stack().withStackStatus("CREATE_COMPLETE").withStackName(stackName);
    LogCallback logCallback = mock(ExecutionLogCallback.class);
    StackResource stackResource =
        new StackResource().withStackName(stackName).withResourceStatusReason("statusReason").withTimestamp(timeStamp);
    when(awsCloudformationClient.getAllStackResources(any(), any(), any())).thenReturn(singletonList(stackResource));

    cloudFormationBaseHelper.printStackResources(AwsInternalConfig.builder().build(), "us-east-1", stack, logCallback);
    verify(awsCloudformationPrintHelper).printStackResources(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetExistingStackInfo() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    Parameter parameter = new Parameter().withParameterKey("testKey").withParameterValue("testValue");
    Stack stack = new Stack()
                      .withStackStatus("CREATE_COMPLETE")
                      .withStackName("stackName")
                      .withStackId("stackId")
                      .withParameters(parameter);
    doReturn("stackBody").when(awsCFHelperServiceDelegate).getStackBody(any(), any(), any());
    ExistingStackInfo existingStackInfo =
        cloudFormationBaseHelper.getExistingStackInfo(awsInternalConfig, "region", stack);
    assertThat(existingStackInfo.getOldStackParameters().get("testKey")).endsWith("testValue");
    assertThat(existingStackInfo.getOldStackBody()).isEqualTo("stackBody");
    assertThat(existingStackInfo.isStackExisted()).isTrue();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testAwsInternalConfigWithoutCredentials() {
    AwsConnectorDTO emptyDTO = AwsConnectorDTO.builder().build();
    AwsInternalConfig emptyConfig = AwsInternalConfig.builder().build();
    doReturn(emptyConfig).when(awsNgConfigMapper).createAwsInternalConfig(emptyDTO);
    cloudFormationBaseHelper.getAwsInternalConfig(emptyDTO, "us-east-1", any());
    verify(encryptionService, times(0)).decrypt(any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testAwsInternalConfigWithCredentials() {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    AwsConnectorDTO emptyDTO =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
            .build();
    AwsInternalConfig emptyConfig = AwsInternalConfig.builder().build();
    doReturn(emptyConfig).when(awsNgConfigMapper).createAwsInternalConfig(emptyDTO);
    doReturn(emptyDTO).when(encryptionService).decrypt(any(), any());
    cloudFormationBaseHelper.getAwsInternalConfig(emptyDTO, "us-east-1", encryptedDataDetails);
    verify(encryptionService, times(1)).decrypt(any(), any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testDeleteStack() {
    AwsInternalConfig emptyConfig = AwsInternalConfig.builder().build();
    DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
    deleteStackRequest.withStackName("stackName");
    deleteStackRequest.withRoleARN("roleARN");
    cloudFormationBaseHelper.deleteStack("us-east-1", emptyConfig, "stackName", "roleARN", 10);
    verify(awsCloudformationClient).deleteStack("us-east-1", deleteStackRequest, emptyConfig);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void waitForStackToBeDeleted() {
    AwsInternalConfig emptyConfig = AwsInternalConfig.builder().build();
    DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
    describeStacksRequest.withStackName("stackName");
    cloudFormationBaseHelper.waitForStackToBeDeleted("us-east-1", emptyConfig, "stackName", logCallback, 1000L);
    verify(awsCloudformationClient)
        .waitForStackDeletionCompleted(describeStacksRequest, emptyConfig, "us-east-1", logCallback, 1000L);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testMapToDeployStackRequest() {
    Parameter parameter =
        new Parameter().withParameterKey("V1").withParameterValue("V2").withResolvedValue("V3").withUsePreviousValue(
            true);
    Tag tag = new Tag().withKey("K1").withValue("VAL1");
    UpdateStackRequest updateStackRequest = new UpdateStackRequest()
                                                .withStackName("S1")
                                                .withParameters(parameter)
                                                .withCapabilities("CAPABILITY_IAM")
                                                .withTags(tag)
                                                .withRoleARN("ROLE")
                                                .withTemplateBody("TemplateBody")
                                                .withTemplateURL("TemplateUrl");

    DeployStackRequest deployStackRequest = cloudFormationBaseHelper.transformToDeployStackRequest(updateStackRequest);
    assertThat(deployStackRequest.getStackName()).isEqualTo("S1");
    assertThat(deployStackRequest.getRoleARN()).isEqualTo("ROLE");
    assertThat(deployStackRequest.getTemplateBody()).isEqualTo("TemplateBody");
    assertThat(deployStackRequest.getTemplateURL()).isEqualTo("TemplateUrl");
    assertThat(deployStackRequest.getTags()).containsExactly(tag);
    assertThat(deployStackRequest.getParameters()).containsExactly(parameter);
  }
}
