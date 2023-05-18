/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiConfirmSubParams;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiTaskParams;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiTaskResponse;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitDataObtainmentParams;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitDataObtainmentTaskResult;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitRequestType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import com.amazonaws.services.codecommit.model.Commit;
import com.amazonaws.services.codecommit.model.RepositoryMetadata;
import com.amazonaws.services.codecommit.model.UserInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class AwsCodeCommitApiDelegateTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private AwsClient awsClient;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;

  @InjectMocks
  private AwsCodeCommitApiDelegateTask task = new AwsCodeCommitApiDelegateTask(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRunWithObjectParams() {
    assertThatThrownBy(() -> task.run(new Object[10]))
        .hasMessage("not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldObtainAwsCodeCommitDataSuccess() {
    AwsCodeCommitConnectorDTO awsCodeCommitConnectorDTO =
        AwsCodeCommitConnectorDTO.builder().authentication(AwsCodeCommitAuthenticationDTO.builder().build()).build();
    AwsCodeCommitDataObtainmentParams awsCodeCommitDataObtainmentParams =
        AwsCodeCommitDataObtainmentParams.builder()
            .triggerUserArn("arn:aws:iam::123456789012:user/Development/product_1234/*")
            .repoArn("arn:aws:iam::123456789012:user/Development/product_1234/*")
            .commitIds(Arrays.asList("1", "2"))
            .build();
    AwsCodeCommitApiTaskParams taskParams = AwsCodeCommitApiTaskParams.builder()
                                                .apiParams(awsCodeCommitDataObtainmentParams)
                                                .awsCodeCommitConnectorDTO(awsCodeCommitConnectorDTO)
                                                .requestType(AwsCodeCommitRequestType.OBTAIN_AWS_CODECOMMIT_DATA)
                                                .encryptedDataDetails(Collections.emptyList())
                                                .build();
    AwsConfig awsConfig = AwsConfig.builder().build();
    doReturn(awsConfig).when(awsNgConfigMapper).mapAwsCodeCommit(any(), any());

    RepositoryMetadata repositoryMetadata = new RepositoryMetadata();
    repositoryMetadata.setRepositoryId("id");
    repositoryMetadata.setRepositoryName("name");
    repositoryMetadata.setCloneUrlHttp("http");
    repositoryMetadata.setCloneUrlSsh("ssh");
    repositoryMetadata.setDefaultBranch("master");
    doReturn(repositoryMetadata).when(awsClient).fetchRepositoryInformation(eq(awsConfig), any(), any());

    UserInfo user1 = new UserInfo();
    user1.setEmail("email");
    user1.setName("name");
    user1.setDate("1484167798 -0800");
    Commit commit1 = new Commit();
    commit1.setMessage("First commit");
    commit1.setCommitId("1");
    commit1.setAuthor(user1);
    Commit commit2 = new Commit();
    commit2.setCommitId("2");
    commit2.setMessage("Second commit");
    commit2.setAuthor(user1);

    List<Commit> commits = Arrays.asList(commit1, commit2);
    doReturn(commits).when(awsClient).fetchCommitInformation(eq(awsConfig), any(), any(), any());

    DelegateResponseData result = task.run(taskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsCodeCommitApiTaskResponse.class);
    AwsCodeCommitApiTaskResponse response = (AwsCodeCommitApiTaskResponse) result;
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getAwsCodecommitApiResult()).isInstanceOf(AwsCodeCommitDataObtainmentTaskResult.class);
    AwsCodeCommitDataObtainmentTaskResult ccResult =
        (AwsCodeCommitDataObtainmentTaskResult) response.getAwsCodecommitApiResult();
    assertThat(ccResult.getCommitDetailsList()).isNotEmpty();
    assertThat(ccResult.getCommitDetailsList()).hasSameSizeAs(commits);
    assertThat(ccResult.getCommitDetailsList().get(0).getCommitId()).isEqualTo(commit1.getCommitId());
    assertThat(ccResult.getCommitDetailsList().get(0).getMessage()).isEqualTo(commit1.getMessage());
    assertThat(ccResult.getCommitDetailsList().get(0).getOwnerEmail()).isEqualTo(commit1.getAuthor().getEmail());
    assertThat(ccResult.getCommitDetailsList().get(0).getOwnerName()).isEqualTo(commit1.getAuthor().getName());
    assertThat(ccResult.getCommitDetailsList().get(0).getTimeStamp()).isEqualTo(1484167798);

    assertThat(ccResult.getCommitDetailsList().get(1).getCommitId()).isEqualTo(commit2.getCommitId());
    assertThat(ccResult.getCommitDetailsList().get(1).getMessage()).isEqualTo(commit2.getMessage());
    assertThat(ccResult.getCommitDetailsList().get(1).getOwnerEmail()).isEqualTo(commit2.getAuthor().getEmail());
    assertThat(ccResult.getCommitDetailsList().get(1).getOwnerName()).isEqualTo(commit2.getAuthor().getName());
    assertThat(ccResult.getCommitDetailsList().get(1).getTimeStamp()).isEqualTo(1484167798);

    assertThat(ccResult.getRepository()).isNotNull();
    assertThat(ccResult.getRepository().getId()).isEqualTo(repositoryMetadata.getRepositoryId());
    assertThat(ccResult.getRepository().getHttpURL()).isEqualTo(repositoryMetadata.getCloneUrlHttp());
    assertThat(ccResult.getRepository().getSshURL()).isEqualTo(repositoryMetadata.getCloneUrlSsh());
    assertThat(ccResult.getRepository().getBranch()).isEqualTo(repositoryMetadata.getDefaultBranch());
    assertThat(ccResult.getWebhookGitUser()).isNotNull();
    assertThat(ccResult.getWebhookGitUser().getGitId())
        .isEqualTo(awsCodeCommitDataObtainmentParams.getTriggerUserArn());
    assertThat(ccResult.getWebhookGitUser().getName()).isEqualTo("user/Development/product_1234/*");

    verify(awsNgConfigMapper, times(1)).mapAwsCodeCommit(any(), any());
    verify(awsClient, times(1)).fetchRepositoryInformation(eq(awsConfig), any(), any());
    verify(awsClient, times(1)).fetchCommitInformation(eq(awsConfig), any(), any(), any());
  }
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldObtainAwsCodeCommitDataFailure() {
    AwsCodeCommitConnectorDTO awsCodeCommitConnectorDTO =
        AwsCodeCommitConnectorDTO.builder().authentication(AwsCodeCommitAuthenticationDTO.builder().build()).build();
    AwsCodeCommitDataObtainmentParams awsCodeCommitDataObtainmentParams =
        AwsCodeCommitDataObtainmentParams.builder()
            .triggerUserArn("arn:aws:iam::123456789012:user/Development/product_1234/*")
            .repoArn("arn:aws:iam::123456789012:user/Development/product_1234/*")
            .commitIds(Arrays.asList("1", "2"))
            .build();
    AwsCodeCommitApiTaskParams taskParams = AwsCodeCommitApiTaskParams.builder()
                                                .apiParams(awsCodeCommitDataObtainmentParams)
                                                .awsCodeCommitConnectorDTO(awsCodeCommitConnectorDTO)
                                                .requestType(AwsCodeCommitRequestType.OBTAIN_AWS_CODECOMMIT_DATA)
                                                .encryptedDataDetails(Collections.emptyList())
                                                .build();

    doThrow(new InvalidArgumentsException("Bad authentication provided"))
        .when(awsNgConfigMapper)
        .mapAwsCodeCommit(any(), any());

    DelegateResponseData result = task.run(taskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsCodeCommitApiTaskResponse.class);
    AwsCodeCommitApiTaskResponse response = (AwsCodeCommitApiTaskResponse) result;
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldConfirmTriggerSubscriptionSuccess() {
    AwsCodeCommitApiConfirmSubParams awsCodeCommitApiConfirmSubParams =
        AwsCodeCommitApiConfirmSubParams.builder().topicArn("test").subscriptionConfirmationMessage("message").build();
    AwsCodeCommitApiTaskParams taskParams = AwsCodeCommitApiTaskParams.builder()
                                                .apiParams(awsCodeCommitApiConfirmSubParams)
                                                .requestType(AwsCodeCommitRequestType.CONFIRM_TRIGGER_SUBSCRIPTION)
                                                .build();

    DelegateResponseData result = task.run(taskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsCodeCommitApiTaskResponse.class);
    AwsCodeCommitApiTaskResponse response = (AwsCodeCommitApiTaskResponse) result;
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> topicArnCaptor = ArgumentCaptor.forClass(String.class);
    verify(awsClient, times(1)).confirmSnsSubscription(messageCaptor.capture(), topicArnCaptor.capture());
    assertThat(messageCaptor.getValue()).isEqualTo("message");
    assertThat(topicArnCaptor.getValue()).isEqualTo("test");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldConfirmTriggerSubscriptionFailure() {
    AwsCodeCommitApiConfirmSubParams awsCodeCommitApiConfirmSubParams =
        AwsCodeCommitApiConfirmSubParams.builder().topicArn("test").subscriptionConfirmationMessage("message").build();
    AwsCodeCommitApiTaskParams taskParams = AwsCodeCommitApiTaskParams.builder()
                                                .apiParams(awsCodeCommitApiConfirmSubParams)
                                                .requestType(AwsCodeCommitRequestType.CONFIRM_TRIGGER_SUBSCRIPTION)
                                                .build();

    doThrow(new InvalidRequestException("Bad sns subscription")).when(awsClient).confirmSnsSubscription(any(), any());
    DelegateResponseData result = task.run(taskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsCodeCommitApiTaskResponse.class);
    AwsCodeCommitApiTaskResponse response = (AwsCodeCommitApiTaskResponse) result;
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }
}
