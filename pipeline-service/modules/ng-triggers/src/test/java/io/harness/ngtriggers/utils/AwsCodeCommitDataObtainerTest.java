/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.VINICIUS;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CommitDetails;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.PushWebhookEvent;
import io.harness.beans.Repository;
import io.harness.beans.WebhookGitUser;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitApiTaskResponse;
import io.harness.delegate.beans.aws.codecommit.AwsCodeCommitDataObtainmentTaskResult;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ngtriggers.NgTriggersTestHelper;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Signature;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.utils.ConnectorUtils;

import com.google.protobuf.Timestamp;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(PIPELINE)
public class AwsCodeCommitDataObtainerTest extends CategoryTest {
  @Mock private WebhookEventPayloadParser webhookEventPayloadParser;
  @Mock TaskExecutionUtils taskExecutionUtils;
  @Mock KryoSerializer kryoSerializer;
  @Mock KryoSerializer referenceFalseKryoSerializer;
  @Mock ConnectorUtils connectorUtils;
  @Spy @InjectMocks AwsCodeCommitDataObtainer awsCodeCommitDataObtainer;
  private static final List<TriggerDetails> triggerDetailsList;
  private static final Repository repository =
      Repository.builder().id("arn:aws:codecommit:eu-central-1:44864EXAMPLE:test").build();

  private static final WebhookGitUser webhookGitUser =
      WebhookGitUser.builder()
          .gitId("arn:aws:iam::44864EXAMPLE:user/vault-okta-username.lastname@gitmail.io-a1616318518-9029")
          .build();

  private static final PushWebhookEvent pushWebhookEvent = NgTriggersTestHelper.getAwsCodecommitPushWebhookEvent();

  private static final TriggerWebhookEvent triggerWebhookEvent =
      NgTriggersTestHelper.getTriggerWehookEventForAwsCodeCommitPush();

  private static final Commit commit =
      Commit.newBuilder()
          .setSha("f70e8226cac251f6116315984b6e9ed7098ce586")
          .setAuthor(
              Signature.newBuilder()
                  .setDate(Timestamp.newBuilder().setSeconds(1616318557).build())
                  .setLogin("arn:aws:iam::44864EXAMPLE:user/vault-okta-username.lastname@gitmail.io-a1616318518-9029")
                  .build())
          .setCommitter(
              Signature.newBuilder()
                  .setDate(Timestamp.newBuilder().setSeconds(1616318557).build())
                  .setLogin("arn:aws:iam::44864EXAMPLE:user/vault-okta-username.lastname@gitmail.io-a1616318518-9029")
                  .build())
          .build();

  private static final ParseWebhookResponse parseWebhookResponse =
      ParseWebhookResponse.newBuilder()
          .setPush(PushHook.newBuilder()
                       .setRef("refs/heads/main")
                       .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                                    .setId("arn:aws:codecommit:eu-central-1:44864EXAMPLE:test")
                                    .setName("test")
                                    .setBranch("main")
                                    .setPrivate(true)
                                    .setCreated(Timestamp.newBuilder().setSeconds(62135596800L).build())
                                    .setUpdated(Timestamp.newBuilder().setSeconds(62135596800L).build())
                                    .build())
                       .addCommits(commit)
                       .setCommit(commit)
                       .build())
          .build();

  private static final WebhookPayloadData webhookPayloadData = WebhookPayloadData.builder()
                                                                   .webhookGitUser(webhookGitUser)
                                                                   .parseWebhookResponse(parseWebhookResponse)
                                                                   .webhookEvent(pushWebhookEvent)
                                                                   .repository(repository)
                                                                   .originalEvent(triggerWebhookEvent)
                                                                   .build();

  static {
    TriggerDetails details1 = NgTriggersTestHelper.getAwsRepoTriggerDetails();
    TriggerDetails details2 = NgTriggersTestHelper.getAwsRegionTriggerDetails();
    triggerDetailsList = asList(details1, details2);
  }

  private static final AwsCodeCommitApiTaskResponse awsCodeCommitApiTaskResponse =
      AwsCodeCommitApiTaskResponse.builder()
          .awsCodecommitApiResult(
              AwsCodeCommitDataObtainmentTaskResult.builder()
                  .commitDetailsList(singletonList(CommitDetails.builder()
                                                       .commitId("f70e8226cac251f6116315984b6e9ed7098ce586")
                                                       .message("Initial commit")
                                                       .ownerName("Firstname Lastname")
                                                       .ownerEmail("firstname.lastname@gitmail.io")
                                                       .timeStamp(1616317729)
                                                       .build()))
                  .repository(Repository.builder()
                                  .id("37489034-ca56-411e-85c9-58fa8dffaab0")
                                  .name("test")
                                  .branch("main")
                                  .httpURL("https://git-codecommit.eu-central-1.amazonaws.com/v1/repos/test")
                                  .sshURL("ssh://git-codecommit.eu-central-1.amazonaws.com/v1/repos/test")
                                  .build())
                  .webhookGitUser(
                      WebhookGitUser.builder()
                          .name("user/vault-okta-username.lastname@gitmail.io-a1616318518-9029")
                          .gitId(
                              "arn:aws:iam::44864EXAMPLE:user/vault-okta-username.lastname@gitmail.io-a1616318518-9029")
                          .build())
                  .build())
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

  public static final ParseWebhookResponse obtainedParseWebhookResponse =
      ParseWebhookResponse.newBuilder()
          .setPush(
              PushHook.newBuilder()
                  .setRef("refs/heads/main")
                  .setRepo(io.harness.product.ci.scm.proto.Repository.newBuilder()
                               .setId("37489034-ca56-411e-85c9-58fa8dffaab0")
                               .setName("test")
                               .setBranch("main")
                               .setPrivate(true)
                               .setClone("https://git-codecommit.eu-central-1.amazonaws.com/v1/repos/test")
                               .setCloneSsh("ssh://git-codecommit.eu-central-1.amazonaws.com/v1/repos/test")
                               .setCreated(Timestamp.newBuilder().setSeconds(62135596800L).build())
                               .setUpdated(Timestamp.newBuilder().setSeconds(62135596800L).build())
                               .build())
                  .setSender(
                      User.newBuilder()
                          .setLogin(
                              "arn:aws:iam::44864EXAMPLE:user/vault-okta-username.lastname@gitmail.io-a1616318518-9029")
                          .setName("user/vault-okta-username.lastname@gitmail.io-a1616318518-9029")
                          .build())
                  .addCommits(Commit.newBuilder()
                                  .setSha("f70e8226cac251f6116315984b6e9ed7098ce586")
                                  .setMessage("Initial commit")
                                  .setAuthor(Signature.newBuilder()
                                                 .setName("Firstname Lastname")
                                                 .setEmail("firstname.lastname@gitmail.io")
                                                 .build())
                                  .setCommitter(Signature.newBuilder()
                                                    .setDate(Timestamp.newBuilder().setSeconds(1616317).build())
                                                    .build())
                                  .build())
                  .setCommit(commit)
                  .build())
          .build();
  private static final WebhookPayloadData expectedWebhookPayloadData =
      WebhookPayloadData.builder().parseWebhookResponse(obtainedParseWebhookResponse).build();

  @Before
  public void setUp() throws IOException {
    initMocks(this);
    on(awsCodeCommitDataObtainer).set("kryoSerializer", kryoSerializer);
    on(awsCodeCommitDataObtainer).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldAcquireAwsCodeCommitData() {
    FilterRequestData filterRequestData = FilterRequestData.builder()
                                              .details(triggerDetailsList)
                                              .accountId("acc")
                                              .webhookPayloadData(webhookPayloadData)
                                              .build();

    doReturn(awsCodeCommitApiTaskResponse)
        .when(awsCodeCommitDataObtainer)
        .buildAndExecuteAwsCodeCommitDelegateTask(eq(webhookPayloadData), any(), any());

    doReturn(expectedWebhookPayloadData)
        .when(webhookEventPayloadParser)
        .convertWebhookResponse(eq(obtainedParseWebhookResponse), any());

    awsCodeCommitDataObtainer.acquireProviderData(filterRequestData, triggerDetailsList);

    assertThat(filterRequestData).isNotNull();
    assertThat(filterRequestData.getWebhookPayloadData()).isNotNull();
    assertThat(filterRequestData.getWebhookPayloadData().getParseWebhookResponse())
        .isEqualTo(obtainedParseWebhookResponse);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testBuildAndExecuteAwsCodeCommitDelegateTask() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(AwsCodeCommitConnectorDTO.builder().build())
                                            .executeOnDelegate(true)
                                            .build();
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().build());
    when(kryoSerializer.asInflatedObject(any())).thenReturn(awsCodeCommitApiTaskResponse);
    AwsCodeCommitApiTaskResponse response = awsCodeCommitDataObtainer.buildAndExecuteAwsCodeCommitDelegateTask(
        webhookPayloadData, triggerDetailsList.get(0), "connector");
    assertThat(
        ((AwsCodeCommitDataObtainmentTaskResult) response.getAwsCodecommitApiResult()).getCommitDetailsList().size())
        .isEqualTo(1);
    assertThat(((AwsCodeCommitDataObtainmentTaskResult) response.getAwsCodecommitApiResult())
                   .getCommitDetailsList()
                   .get(0)
                   .getCommitId())
        .isEqualTo("f70e8226cac251f6116315984b6e9ed7098ce586");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testParsePrHook() {
    ParseWebhookResponse parsedWebhookResponse = awsCodeCommitDataObtainer.parsePrHook(obtainedParseWebhookResponse,
        (AwsCodeCommitDataObtainmentTaskResult) awsCodeCommitApiTaskResponse.getAwsCodecommitApiResult());
    assertThat(parsedWebhookResponse).isEqualToComparingFieldByField(obtainedParseWebhookResponse);
  }
}
