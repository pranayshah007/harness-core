/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cloudformation.cloudformationtaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_BODY;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_GIT;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_URL;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static com.amazonaws.services.cloudformation.model.StackStatus.UPDATE_ROLLBACK_COMPLETE;
import static com.amazonaws.services.cloudformation.model.StackStatus.UPDATE_ROLLBACK_FAILED;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.AwsCloudformationPrintHelper;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest.CloudFormationCommandType;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationDeleteStackRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationListStacksRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationListStacksResponse;
import software.wings.helpers.ext.cloudformation.response.ExistingStackInfo;
import software.wings.helpers.ext.cloudformation.response.StackSummaryInfo;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.UpdateStackResult;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class CloudFormationCommandTaskHandlerTest extends WingsBaseTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AWSCloudformationClient awsCloudformationClient;
  @Mock private AwsCFHelperServiceDelegate mockAwsCFHelperServiceDelegate;
  @Mock private AwsCloudformationPrintHelper awsCloudformationPrintHelper;
  @InjectMocks @Inject private CloudFormationCreateStackHandler createStackHandler;
  @InjectMocks @Inject private CloudFormationDeleteStackHandler deleteStackHandler;
  @InjectMocks @Inject private CloudFormationListStacksHandler listStacksHandler;
  private CloudformationBaseHelperImpl cloudformationBaseHelper;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    cloudformationBaseHelper = new CloudformationBaseHelperImpl();
    on(createStackHandler).set("awsCFHelperServiceDelegate", mockAwsCFHelperServiceDelegate);
    on(listStacksHandler).set("awsCFHelperServiceDelegate", mockAwsCFHelperServiceDelegate);
    on(deleteStackHandler).set("awsCFHelperServiceDelegate", mockAwsCFHelperServiceDelegate);
    on(deleteStackHandler).set("cloudformationBaseHelper", cloudformationBaseHelper);
    on(listStacksHandler).set("cloudformationBaseHelper", cloudformationBaseHelper);
    on(createStackHandler).set("cloudformationBaseHelper", cloudformationBaseHelper);
    on(cloudformationBaseHelper).set("awsCloudformationClient", awsCloudformationClient);
    on(cloudformationBaseHelper).set("awsCFHelperServiceDelegate", mockAwsCFHelperServiceDelegate);
    on(cloudformationBaseHelper).set("awsCloudformationPrintHelper", awsCloudformationPrintHelper);
    when(mockEncryptionService.decrypt(any(), any(), eq(false))).thenReturn(null);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetIfStackExists() {
    String customStackName = "CUSTOM_STACK_NAME";
    String stackId = "STACK_ID";
    doReturn(singletonList(new Stack().withStackId(stackId).withStackName(customStackName)))
        .when(awsCloudformationClient)
        .getAllStacks(anyString(), any(), any());
    Optional<Stack> stack =
        createStackHandler.getIfStackExists(customStackName, "foo", AwsInternalConfig.builder().build(), "us-east-1");
    assertThat(stack.isPresent()).isTrue();
    assertThat(stackId).isEqualTo(stack.get().getStackId());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateStack() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    String roleArn = "roleArn";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .cloudFormationRoleArn(roleArn)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType(CLOUDFORMATION_STACK_CREATE_BODY)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    String stackId = "Stack Id 00";
    CreateStackResult createStackResult = new CreateStackResult().withStackId(stackId);
    doReturn(createStackResult).when(awsCloudformationClient).createStack(any(), any(), any());
    List<Stack> createProgressList = singletonList(new Stack().withStackStatus("CREATE_IN_PROGRESS"));
    List<Stack> createCompleteList =
        singletonList(new Stack()
                          .withStackStatus("CREATE_COMPLETE")
                          .withOutputs(new Output().withOutputKey("vpcs").withOutputValue("vpcs"),
                              new Output().withOutputKey("subnets").withOutputValue("subnets"),
                              new Output().withOutputKey("securityGroups").withOutputValue("sgs")));
    doReturn(Collections.emptyList()).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    doReturn(Optional.of(createProgressList.get(0)))
        .doReturn(Optional.of(createCompleteList.get(0)))
        .when(awsCloudformationClient)
        .getStack(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNotNull();
    assertThat(formationCommandResponse instanceof CloudFormationCreateStackResponse).isTrue();
    CloudFormationCreateStackResponse createStackResponse =
        (CloudFormationCreateStackResponse) formationCommandResponse;
    Map<String, Object> outputMap = createStackResponse.getCloudFormationOutputMap();
    assertThat(3).isEqualTo(outputMap.size());
    validateMapContents(outputMap, "vpcs", "vpcs");
    validateMapContents(outputMap, "subnets", "subnets");
    validateMapContents(outputMap, "securityGroups", "sgs");
    ExistingStackInfo existingStackInfo = createStackResponse.getExistingStackInfo();
    assertThat(existingStackInfo).isNotNull();
    assertThat(existingStackInfo.isStackExisted()).isFalse();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateStackWithNoStacksFound() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    String roleArn = "roleArn";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .cloudFormationRoleArn(roleArn)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType(CLOUDFORMATION_STACK_CREATE_BODY)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    String stackId = "Stack Id 00";
    CreateStackResult createStackResult = new CreateStackResult().withStackId(stackId);
    doReturn(createStackResult).when(awsCloudformationClient).createStack(any(), any(), any());
    doReturn(Collections.emptyList()).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    doReturn(Optional.empty()).when(awsCloudformationClient).getStack(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNull();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateStackGit() {
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String data = "data";
    String stackNameSuffix = "Stack Name 00";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .data(data)
            .createType(CLOUDFORMATION_STACK_CREATE_BODY)
            .stackNameSuffix(stackNameSuffix)
            .build();
    String stackId = "Stack Id 00";
    CreateStackResult createStackResult = new CreateStackResult().withStackId(stackId);
    doReturn(createStackResult).when(awsCloudformationClient).createStack(any(), any(), any());
    List<Stack> createProgressList = singletonList(new Stack().withStackStatus("CREATE_IN_PROGRESS"));
    List<Stack> createCompleteList =
        singletonList(new Stack()
                          .withStackStatus("CREATE_COMPLETE")
                          .withOutputs(new Output().withOutputKey("vpcs").withOutputValue("vpcs"),
                              new Output().withOutputKey("subnets").withOutputValue("subnets"),
                              new Output().withOutputKey("securityGroups").withOutputValue("sgs")));
    doReturn(Collections.emptyList()).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    doReturn(Optional.of(createProgressList.get(0)))
        .doReturn(Optional.of(createCompleteList.get(0)))
        .when(awsCloudformationClient)
        .getStack(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);

    assertThat(response).isNotNull();
    assertThat(data).isEqualTo(request.getData());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNotNull();
    assertThat(formationCommandResponse instanceof CloudFormationCreateStackResponse).isTrue();
    CloudFormationCreateStackResponse createStackResponse =
        (CloudFormationCreateStackResponse) formationCommandResponse;
    Map<String, Object> outputMap = createStackResponse.getCloudFormationOutputMap();
    assertThat(3).isEqualTo(outputMap.size());
    validateMapContents(outputMap, "vpcs", "vpcs");
    validateMapContents(outputMap, "subnets", "subnets");
    validateMapContents(outputMap, "securityGroups", "sgs");
    ExistingStackInfo existingStackInfo = createStackResponse.getExistingStackInfo();
    assertThat(existingStackInfo).isNotNull();
    assertThat(existingStackInfo.isStackExisted()).isFalse();
  }

  private void validateMapContents(Map<String, Object> map, String key, String value) {
    assertThat(map.containsKey(key)).isTrue();
    assertThat(value).isEqualTo((String) map.get(key));
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testUpdateStack() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType(CLOUDFORMATION_STACK_CREATE_BODY)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    List<Stack> exitingList =
        singletonList(new Stack().withStackStatus("CREATE_COMPLETE").withStackName("HarnessStack-" + stackNameSuffix));
    List<Stack> updateProgressList = singletonList(new Stack().withStackStatus("UPDATE_IN_PROGRESS"));
    List<Stack> updateCompleteList = singletonList(new Stack().withStackStatus("UPDATE_COMPLETE"));
    doReturn(exitingList).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    doReturn(Optional.of(updateProgressList.get(0)))
        .doReturn(Optional.of(updateCompleteList.get(0)))
        .when(awsCloudformationClient)
        .getStack(any(), any(), any());
    UpdateStackResult updateStackResult = new UpdateStackResult();
    updateStackResult.setStackId("StackId1");
    doReturn(updateStackResult).when(awsCloudformationClient).updateStack(any(), any(), any());
    doReturn("Body").when(mockAwsCFHelperServiceDelegate).getStackBody(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNotNull();
    assertThat(formationCommandResponse instanceof CloudFormationCreateStackResponse).isTrue();
    CloudFormationCreateStackResponse createStackResponse =
        (CloudFormationCreateStackResponse) formationCommandResponse;
    ExistingStackInfo existingStackInfo = createStackResponse.getExistingStackInfo();
    assertThat(existingStackInfo).isNotNull();
    assertThat(existingStackInfo.isStackExisted()).isTrue();
    assertThat("Body").isEqualTo(existingStackInfo.getOldStackBody());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testUpdateStackWithExistingStackInUpdateRollbackCompleteState() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType(CLOUDFORMATION_STACK_CREATE_BODY)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    List<Stack> exitingList =
        singletonList(new Stack().withStackStatus("CREATE_COMPLETE").withStackName("HarnessStack-" + stackNameSuffix));
    List<Stack> updateCompleteList =
        singletonList(new Stack().withStackStatus("UPDATE_ROLLBACK_COMPLETE").withStackId("stackId1"));
    doReturn(exitingList).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    doReturn(Optional.of(updateCompleteList.get(0))).when(awsCloudformationClient).getStack(any(), any(), any());
    UpdateStackResult updateStackResult = new UpdateStackResult();
    doReturn(updateStackResult).when(awsCloudformationClient).updateStack(any(), any(), any());
    doReturn("Body").when(mockAwsCFHelperServiceDelegate).getStackBody(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNotNull();
    assertThat(formationCommandResponse instanceof CloudFormationCreateStackResponse).isTrue();
    CloudFormationCreateStackResponse createStackResponse =
        (CloudFormationCreateStackResponse) formationCommandResponse;
    assertThat(createStackResponse.getStackId()).isEqualTo("stackId1");
    ExistingStackInfo existingStackInfo = createStackResponse.getExistingStackInfo();
    assertThat(existingStackInfo).isNotNull();
    assertThat(existingStackInfo.isStackExisted()).isTrue();
    assertThat("Body").isEqualTo(existingStackInfo.getOldStackBody());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testUpdateStackWithExistingStackInUpdateRollbackFailedState() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType(CLOUDFORMATION_STACK_CREATE_BODY)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    List<Stack> exitingList =
        singletonList(new Stack().withStackStatus("CREATE_COMPLETE").withStackName("HarnessStack-" + stackNameSuffix));
    List<Stack> updateCompleteList =
        singletonList(new Stack().withStackStatus("UPDATE_ROLLBACK_FAILED").withStackId("stackId1"));
    doReturn(exitingList).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    doReturn(Optional.of(updateCompleteList.get(0))).when(awsCloudformationClient).getStack(any(), any(), any());
    UpdateStackResult updateStackResult = new UpdateStackResult();
    doReturn(updateStackResult).when(awsCloudformationClient).updateStack(any(), any(), any());
    doReturn("Body").when(mockAwsCFHelperServiceDelegate).getStackBody(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getErrorMessage())
        .isEqualTo(
            "# Existing stack with name null is already in status: UPDATE_ROLLBACK_FAILED, therefore exiting with failure");
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNotNull();
    assertThat(formationCommandResponse instanceof CloudFormationCreateStackResponse).isTrue();
    CloudFormationCreateStackResponse createStackResponse =
        (CloudFormationCreateStackResponse) formationCommandResponse;
    assertThat(createStackResponse.getStackId()).isEqualTo("stackId1");
    assertThat(createStackResponse.getStackStatus()).isEqualTo(UPDATE_ROLLBACK_FAILED.name());
    ExistingStackInfo existingStackInfo = createStackResponse.getExistingStackInfo();
    assertThat(existingStackInfo).isNotNull();
    assertThat(existingStackInfo.isStackExisted()).isTrue();
    assertThat("Body").isEqualTo(existingStackInfo.getOldStackBody());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testUpdateStackWithStackStatusToMarkAsSuccess() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .stackStatusesToMarkAsSuccess(singletonList(UPDATE_ROLLBACK_COMPLETE))
            .createType(CLOUDFORMATION_STACK_CREATE_BODY)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    List<Stack> exitingList =
        singletonList(new Stack().withStackStatus("CREATE_COMPLETE").withStackName("HarnessStack-" + stackNameSuffix));
    List<Stack> updateProgressList = singletonList(new Stack().withStackStatus("UPDATE_IN_PROGRESS"));
    List<Stack> updateCompleteList = singletonList(new Stack().withStackStatus("UPDATE_ROLLBACK_COMPLETE"));
    doReturn(exitingList).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    doReturn(Optional.of(updateProgressList.get(0)))
        .doReturn(Optional.of(updateCompleteList.get(0)))
        .when(awsCloudformationClient)
        .getStack(any(), any(), any());
    UpdateStackResult updateStackResult = new UpdateStackResult();
    updateStackResult.setStackId("StackId1");
    doReturn(updateStackResult).when(awsCloudformationClient).updateStack(any(), any(), any());
    doReturn("Body").when(mockAwsCFHelperServiceDelegate).getStackBody(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNotNull();
    assertThat(formationCommandResponse instanceof CloudFormationCreateStackResponse).isTrue();
    CloudFormationCreateStackResponse createStackResponse =
        (CloudFormationCreateStackResponse) formationCommandResponse;
    ExistingStackInfo existingStackInfo = createStackResponse.getExistingStackInfo();
    assertThat(existingStackInfo).isNotNull();
    assertThat(existingStackInfo.isStackExisted()).isTrue();
    assertThat("Body").isEqualTo(existingStackInfo.getOldStackBody());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testUpdateStackGit() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .gitConfig(GitConfig.builder().repoUrl("").branch("").build())
            .gitFileConfig(GitFileConfig.builder().filePath("").commitId("").build())
            .createType(CLOUDFORMATION_STACK_CREATE_GIT)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    List<Stack> exitingList =
        singletonList(new Stack().withStackStatus("CREATE_COMPLETE").withStackName("HarnessStack-" + stackNameSuffix));
    List<Stack> updateProgressList = singletonList(new Stack().withStackStatus("UPDATE_IN_PROGRESS"));
    List<Stack> updateCompleteList = singletonList(new Stack().withStackStatus("UPDATE_COMPLETE"));
    doReturn(exitingList).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    doReturn(Optional.of(updateProgressList.get(0)))
        .doReturn(Optional.of(updateCompleteList.get(0)))
        .when(awsCloudformationClient)
        .getStack(any(), any(), any());
    UpdateStackResult updateStackResult = new UpdateStackResult();
    updateStackResult.setStackId("StackId1");
    doReturn(updateStackResult).when(awsCloudformationClient).updateStack(any(), any(), any());
    doReturn("Body").when(mockAwsCFHelperServiceDelegate).getStackBody(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNotNull();
    assertThat(formationCommandResponse instanceof CloudFormationCreateStackResponse).isTrue();
    CloudFormationCreateStackResponse createStackResponse =
        (CloudFormationCreateStackResponse) formationCommandResponse;
    ExistingStackInfo existingStackInfo = createStackResponse.getExistingStackInfo();
    assertThat(existingStackInfo).isNotNull();
    assertThat(existingStackInfo.isStackExisted()).isTrue();
    assertThat("Body").isEqualTo(existingStackInfo.getOldStackBody());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testDeleteStack() {
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 01";
    String roleArn = "RoleArn";
    CloudFormationDeleteStackRequest request =
        CloudFormationDeleteStackRequest.builder()
            .commandType(CloudFormationCommandType.DELETE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Delete Stack")
            .cloudFormationRoleArn(roleArn)
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .stackNameSuffix(stackNameSuffix)
            .skipWaitForResources(true)
            .build();
    String stackId = "Stack Id 01";

    List<Stack> existingStackList =
        singletonList(new Stack().withStackName("HarnessStack-" + stackNameSuffix).withStackId(stackId));
    List<Stack> deleteInProgressList =
        singletonList(new Stack().withStackId(stackId).withStackStatus("DELETE_IN_PROGRESS"));
    List<Stack> deleteCompleteList = singletonList(new Stack().withStackId(stackId).withStackStatus("DELETE_COMPLETE"));
    doReturn(existingStackList)
        .doReturn(deleteInProgressList)
        .doReturn(deleteCompleteList)
        .when(awsCloudformationClient)
        .getAllStacks(any(), any(), any());
    CloudFormationCommandExecutionResponse response = deleteStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(awsCloudformationClient).deleteStack(any(), any(), any());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testDeleteStackNoExistingStack() {
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 01";
    String roleArn = "RoleArn";
    CloudFormationDeleteStackRequest request =
        CloudFormationDeleteStackRequest.builder()
            .commandType(CloudFormationCommandType.DELETE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Delete Stack")
            .cloudFormationRoleArn(roleArn)
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .skipWaitForResources(true)
            .stackNameSuffix(stackNameSuffix + "nomatch")
            .build();
    doReturn(Optional.empty()).when(awsCloudformationClient).getStack(any(), any(), any());
    CloudFormationCommandExecutionResponse response = deleteStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testDeleteStackWithException() {
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 01";
    String roleArn = "RoleArn";
    CloudFormationDeleteStackRequest request =
        CloudFormationDeleteStackRequest.builder()
            .commandType(CloudFormationCommandType.DELETE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Delete Stack")
            .cloudFormationRoleArn(roleArn)
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .stackNameSuffix(stackNameSuffix)
            .skipWaitForResources(true)
            .build();
    Exception ex = new RuntimeException("This is an exception");
    String stackId = "Stack Id 01";
    List<Stack> existingStackList =
        singletonList(new Stack().withStackName("HarnessStack-" + stackNameSuffix).withStackId(stackId));
    doReturn(existingStackList).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    doThrow(ex).when(awsCloudformationClient).deleteStack(any(), any(), any());
    CloudFormationCommandExecutionResponse response = deleteStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testDeleteStackWithTimeout() {
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 01";
    String roleArn = "RoleArn";
    CloudFormationDeleteStackRequest request =
        CloudFormationDeleteStackRequest.builder()
            .commandType(CloudFormationCommandType.DELETE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Delete Stack")
            .cloudFormationRoleArn(roleArn)
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(1)
            .stackNameSuffix(stackNameSuffix)
            .skipWaitForResources(true)
            .build();
    String stackId = "Stack Id 01";

    List<Stack> existingStackList =
        singletonList(new Stack().withStackName("HarnessStack-" + stackNameSuffix).withStackId(stackId));
    List<Stack> stackList = singletonList(new Stack().withStackId(stackId).withStackStatus("DELETE_IN_PROGRESS"));
    doReturn(existingStackList).doReturn(stackList).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    CloudFormationCommandExecutionResponse response = deleteStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(awsCloudformationClient).deleteStack(any(), any(), any());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testDeleteStackStatusDeleteFailed() {
    testFailureForDeleteStackStatus("DELETE_FAILED");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testDeleteStackStatusUnknown() {
    testFailureForDeleteStackStatus("Unknown");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListStacks() {
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    CloudFormationListStacksRequest request =
        CloudFormationListStacksRequest.builder()
            .commandType(CloudFormationCommandType.GET_STACKS)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("List Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .build();
    List<Stack> stacks = Arrays.asList(new Stack()
                                           .withStackId("sId1")
                                           .withStackName("sName1")
                                           .withStackStatus("sStatus1")
                                           .withStackStatusReason("sReason1"),
        new Stack()
            .withStackId("sId2")
            .withStackName("sName2")
            .withStackStatus("sStatus2")
            .withStackStatusReason("sReason2"));
    doReturn(stacks).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    CloudFormationCommandExecutionResponse response = listStacksHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNotNull();
    assertThat(formationCommandResponse instanceof CloudFormationListStacksResponse).isTrue();
    CloudFormationListStacksResponse listStacksResponse = (CloudFormationListStacksResponse) formationCommandResponse;
    List<StackSummaryInfo> summaryInfos = listStacksResponse.getStackSummaryInfos();
    assertThat(summaryInfos).isNotNull();
    assertThat(summaryInfos).hasSize(2);
    validateStackSummaryInfo(summaryInfos.get(0), "sId1", "sName1", "sStatus1", "sReason1");
    validateStackSummaryInfo(summaryInfos.get(1), "sId2", "sName2", "sStatus2", "sReason2");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListStacksWithException() {
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    CloudFormationListStacksRequest request =
        CloudFormationListStacksRequest.builder()
            .commandType(CloudFormationCommandType.GET_STACKS)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("List Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .stackId("stackId")
            .build();
    Exception ex = new RuntimeException("This is an exception");
    doThrow(ex).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    CloudFormationCommandExecutionResponse response = listStacksHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNull();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCreateStackCreateTypeUrl() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    String roleArn = "roleArn";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .cloudFormationRoleArn(roleArn)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType(CLOUDFORMATION_STACK_CREATE_URL)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    String stackId = "Stack Id 00";
    CreateStackResult createStackResult = new CreateStackResult().withStackId(stackId);
    doReturn(createStackResult).when(awsCloudformationClient).createStack(any(), any(), any());
    List<Stack> createProgressList = singletonList(new Stack().withStackStatus("CREATE_IN_PROGRESS"));
    List<Stack> createCompleteList =
        singletonList(new Stack()
                          .withStackStatus("CREATE_COMPLETE")
                          .withOutputs(new Output().withOutputKey("vpcs").withOutputValue("vpcs"),
                              new Output().withOutputKey("subnets").withOutputValue("subnets"),
                              new Output().withOutputKey("securityGroups").withOutputValue("sgs")));
    doReturn(Collections.emptyList()).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    doReturn(Optional.of(createProgressList.get(0)))
        .doReturn(Optional.of(createCompleteList.get(0)))
        .when(awsCloudformationClient)
        .getStack(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNotNull();
    assertThat(formationCommandResponse instanceof CloudFormationCreateStackResponse).isTrue();
    CloudFormationCreateStackResponse createStackResponse =
        (CloudFormationCreateStackResponse) formationCommandResponse;
    Map<String, Object> outputMap = createStackResponse.getCloudFormationOutputMap();
    assertThat(3).isEqualTo(outputMap.size());
    validateMapContents(outputMap, "vpcs", "vpcs");
    validateMapContents(outputMap, "subnets", "subnets");
    validateMapContents(outputMap, "securityGroups", "sgs");
    ExistingStackInfo existingStackInfo = createStackResponse.getExistingStackInfo();
    assertThat(existingStackInfo).isNotNull();
    assertThat(existingStackInfo.isStackExisted()).isFalse();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCreateStackCreateTypeUnknown() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    String roleArn = "roleArn";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .cloudFormationRoleArn(roleArn)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType("Unknown")
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    String stackId = "Stack Id 00";
    CreateStackResult createStackResult = new CreateStackResult().withStackId(stackId);
    doReturn(createStackResult).when(awsCloudformationClient).createStack(any(), any(), any());
    List<Stack> createProgressList = singletonList(new Stack().withStackStatus("CREATE_IN_PROGRESS"));
    List<Stack> createCompleteList =
        singletonList(new Stack()
                          .withStackStatus("CREATE_COMPLETE")
                          .withOutputs(new Output().withOutputKey("vpcs").withOutputValue("vpcs"),
                              new Output().withOutputKey("subnets").withOutputValue("subnets"),
                              new Output().withOutputKey("securityGroups").withOutputValue("sgs")));
    doReturn(Optional.empty())
        .doReturn(Optional.of(createProgressList.get(0)))
        .doReturn(Optional.of(createCompleteList.get(0)))
        .when(awsCloudformationClient)
        .getStack(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNull();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testUpdateStackUpdateTypeUnknown() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType("Unknown")
            .data(templateBody)
            .cloudFormationRoleArn("testRole")
            .stackNameSuffix(stackNameSuffix)
            .build();
    List<Stack> exitingList =
        singletonList(new Stack().withStackStatus("CREATE_COMPLETE").withStackName("HarnessStack-" + stackNameSuffix));
    List<Stack> updateProgressList = singletonList(new Stack().withStackStatus("UPDATE_IN_PROGRESS"));
    List<Stack> updateCompleteList = singletonList(new Stack().withStackStatus("UPDATE_COMPLETE"));
    doReturn(Optional.of(exitingList.get(0)))
        .doReturn(Optional.of(updateProgressList.get(0)))
        .doReturn(Optional.of(updateCompleteList.get(0)))
        .when(awsCloudformationClient)
        .getStack(any(), any(), any());
    doReturn("Body").when(mockAwsCFHelperServiceDelegate).getStackBody(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNull();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testUpdateStackUpdateTypeUrl() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType(CLOUDFORMATION_STACK_CREATE_URL)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    List<Stack> exitingList =
        singletonList(new Stack().withStackStatus("CREATE_COMPLETE").withStackName("HarnessStack-" + stackNameSuffix));
    List<Stack> updateProgressList = singletonList(new Stack().withStackStatus("UPDATE_IN_PROGRESS"));
    List<Stack> updateCompleteList = singletonList(new Stack().withStackStatus("UPDATE_COMPLETE"));
    doReturn(exitingList).when(awsCloudformationClient).getAllStacks(any(), any(), any());

    doReturn(Optional.of(updateProgressList.get(0)))
        .doReturn(Optional.of(updateCompleteList.get(0)))
        .when(awsCloudformationClient)
        .getStack(any(), any(), any());

    UpdateStackResult updateStackResult = new UpdateStackResult();
    updateStackResult.setStackId("StackId1");
    doReturn(updateStackResult).when(awsCloudformationClient).updateStack(any(), any(), any());
    doReturn("Body").when(mockAwsCFHelperServiceDelegate).getStackBody(any(), any(), any());

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    assertThat(formationCommandResponse).isNotNull();
    assertThat(formationCommandResponse instanceof CloudFormationCreateStackResponse).isTrue();
    CloudFormationCreateStackResponse createStackResponse =
        (CloudFormationCreateStackResponse) formationCommandResponse;
    ExistingStackInfo existingStackInfo = createStackResponse.getExistingStackInfo();
    assertThat(existingStackInfo).isNotNull();
    assertThat(existingStackInfo.isStackExisted()).isTrue();
    assertThat("Body").isEqualTo(existingStackInfo.getOldStackBody());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCreateStackWithException() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    String roleArn = "roleArn";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .cloudFormationRoleArn(roleArn)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType(CLOUDFORMATION_STACK_CREATE_URL)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    RuntimeException ex = new RuntimeException("errorMessage");
    doThrow(ex).when(mockEncryptionService).decrypt(any(), any(), eq(false));
    String errorMessage = format("Exception: %s while executing CF task.", ExceptionUtils.getMessage(ex));
    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo(errorMessage);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCreateStackRollbackProgressStatus() {
    testFailureForCreateStackStatus("ROLLBACK_IN_PROGRESS");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCreateStackCreateFailedStatus() {
    testFailureForCreateStackStatus("CREATE_FAILED");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCreateStackRollbackFailedStatus() {
    testFailureForCreateStackStatus("ROLLBACK_FAILED");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCreateStackRollbackCompleteStatus() {
    testFailureForCreateStackStatus("ROLLBACK_COMPLETE");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCreateStackDefaultStatus() {
    testFailureForCreateStackStatus("Unknown");
  }

  private void testFailureForDeleteStackStatus(String status) {
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 01";
    String roleArn = "RoleArn";
    CloudFormationDeleteStackRequest request =
        CloudFormationDeleteStackRequest.builder()
            .commandType(CloudFormationCommandType.DELETE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Delete Stack")
            .cloudFormationRoleArn(roleArn)
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .skipWaitForResources(true)
            .stackNameSuffix(stackNameSuffix)
            .build();
    String stackId = "Stack Id 01";

    List<Stack> existingStackList =
        singletonList(new Stack().withStackName("HarnessStack-" + stackNameSuffix).withStackId(stackId));
    List<Stack> stackList = singletonList(new Stack().withStackId(stackId).withStackStatus(status));
    doReturn(existingStackList).doReturn(stackList).when(awsCloudformationClient).getAllStacks(any(), any(), any());
    CloudFormationCommandExecutionResponse response = deleteStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(awsCloudformationClient).deleteStack(any(), any(), any());
  }

  private void testFailureForCreateStackStatus(String status) {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    String roleArn = "roleArn";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .cloudFormationRoleArn(roleArn)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType(CLOUDFORMATION_STACK_CREATE_BODY)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .build();
    String stackId = "Stack Id 00";
    CreateStackResult createStackResult = new CreateStackResult().withStackId(stackId);
    doReturn(createStackResult).when(awsCloudformationClient).createStack(any(), any(), any());
    List<Stack> rollbackList =
        singletonList(new Stack().withStackStatus(status).withStackName("HarnessStack-" + stackNameSuffix));
    List<Stack> rollbackCompleteList = singletonList(
        new Stack().withStackStatus("ROLLBACK_COMPLETE").withStackName("HarnessStack-" + stackNameSuffix));

    doReturn(Collections.emptyList()).when(awsCloudformationClient).getAllStacks(any(), any(), any());

    if ("ROLLBACK_IN_PROGRESS".equalsIgnoreCase(status)) {
      doReturn(Optional.of(rollbackList.get(0)))
          .doReturn(Optional.of(rollbackCompleteList.get(0)))
          .when(awsCloudformationClient)
          .getStack(any(), any(), any());
    } else {
      doReturn(Optional.of(rollbackList.get(0))).when(awsCloudformationClient).getStack(any(), any(), any());
    }

    CloudFormationCommandExecutionResponse response = createStackHandler.execute(request, null);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    CloudFormationCommandResponse formationCommandResponse = response.getCommandResponse();
    CloudFormationCreateStackResponse expectedCreateStackResponse =
        CloudFormationCreateStackResponse.builder().stackStatus(status).build();
    if ("ROLLBACK_IN_PROGRESS".equalsIgnoreCase(status)) {
      expectedCreateStackResponse =
          CloudFormationCreateStackResponse.builder().stackStatus("ROLLBACK_COMPLETE").build();
    }

    assertThat(formationCommandResponse).isEqualTo(expectedCreateStackResponse);
  }

  private void validateStackSummaryInfo(
      StackSummaryInfo info, String stackId, String stackName, String stackStatus, String stackReason) {
    assertThat(info).isNotNull();
    assertThat(stackId).isEqualTo(info.getStackId());
    assertThat(stackName).isEqualTo(info.getStackName());
    assertThat(stackStatus).isEqualTo(info.getStackStatus());
    assertThat(stackReason).isEqualTo(info.getStackStatusReason());
  }
}
