/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cloudformation.cloudformationtaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_BODY;
import static io.harness.rule.OwnerRule.ARVIND;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.cf.DeployStackResult;
import io.harness.aws.cf.Status;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse;
import software.wings.helpers.ext.cloudformation.response.ExistingStackInfo;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.UpdateStackResult;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class CloudFormationCreateStackHandlerTest extends WingsBaseTest {
  @Mock private AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
  @Mock private AWSCloudformationClient mockAwsHelperService;
  @Mock private EncryptionService mockEncryptionService;
  @Mock private CloudformationBaseHelper mockCloudformationBaseHelper;
  @InjectMocks @Inject private CloudFormationCreateStackHandler cloudFormationCreateStackHandler;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    when(mockEncryptionService.decrypt(any(), any(), eq(false))).thenReturn(null);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testUpdateStackUsingDeploy() {
    CloudFormationCreateStackRequest request = initUpdate();

    doReturn(DeployStackResult.builder().status(Status.SUCCESS).noUpdatesToPerform(false).build())
        .when(mockAwsHelperService)
        .deployStack(nullable(String.class), any(), any(), any(), any());
    doReturn(new HashSet<>()).when(mockCloudformationBaseHelper).getCapabilities(any(), any(), any(), any(), any());

    CloudFormationCommandExecutionResponse response = cloudFormationCreateStackHandler.execute(request, null);
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

    verify(mockAwsHelperService).deployStack(nullable(String.class), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testUpdateStackUsingDeployWithFailure() {
    CloudFormationCreateStackRequest request = initUpdate();

    doReturn(DeployStackResult.builder().status(Status.FAILURE).noUpdatesToPerform(false).build())
        .when(mockAwsHelperService)
        .deployStack(nullable(String.class), any(), any(), any(), any());
    CloudFormationCommandExecutionResponse response = cloudFormationCreateStackHandler.execute(request, null);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  private CloudFormationCreateStackRequest initUpdate() {
    String templateBody = "Template Body";
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackNameSuffix = "Stack Name 00";
    CloudFormationCreateStackRequest request =
        CloudFormationCreateStackRequest.builder()
            .commandType(CloudFormationCommandRequest.CloudFormationCommandType.CREATE_STACK)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName("Create Stack")
            .awsConfig(AwsConfig.builder().accessKey(accessKey).accountId(ACCOUNT_ID).secretKey(secretKey).build())
            .timeoutInMs(10 * 60 * 1000)
            .createType(CLOUDFORMATION_STACK_CREATE_BODY)
            .data(templateBody)
            .stackNameSuffix(stackNameSuffix)
            .deploy(true)
            .build();
    List<Stack> exitingList =
        singletonList(new Stack().withStackStatus("CREATE_COMPLETE").withStackName("HarnessStack-" + stackNameSuffix));
    List<Stack> updateProgressList = singletonList(new Stack().withStackStatus("UPDATE_IN_PROGRESS"));
    List<Stack> updateCompleteList = singletonList(new Stack().withStackStatus("UPDATE_COMPLETE"));
    doReturn(exitingList).when(mockAwsHelperService).getAllStacks(nullable(String.class), any(), any());

    doReturn(Optional.of(updateProgressList.get(0)))
        .doReturn(Optional.of(updateCompleteList.get(0)))
        .when(mockAwsHelperService)
        .getStack(nullable(String.class), any(), any());
    UpdateStackResult updateStackResult = new UpdateStackResult();
    updateStackResult.setStackId("StackId1");
    doReturn(updateStackResult).when(mockAwsHelperService).updateStack(nullable(String.class), any(), any());
    doReturn(ExistingStackInfo.builder().stackExisted(true).oldStackBody("Body").build())
        .when(mockCloudformationBaseHelper)
        .getExistingStackInfo(any(), any(), any());
    return request;
  }
}