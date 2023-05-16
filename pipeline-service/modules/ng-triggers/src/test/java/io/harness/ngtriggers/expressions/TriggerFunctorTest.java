/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADWAIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionMetadataServiceImpl;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.ngtriggers.expressions.functors.TriggerFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Reference;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class TriggerFunctorTest extends CategoryTest {
  private String bigPayload = "{\n"
      + " \"Type\" : \"Notification\",\n"
      + " \"MessageId\" : \"43eb6d30-4059-59d7-a714-35e414b6c4b9\",\n"
      + " \"TopicArn\" : \"arn:aws:sns:eu-central-1:448640225317:aws_cc_push_trigger\",\n"
      + " \"Subject\" : \"UPDATE: AWS CodeCommit eu-central-1 push: test\",\n"
      + " \"Message\" : \"{\\\"Records\\\":[{\\\"awsRegion\\\":\\\"eu-central-1\\\",\\\"codecommit\\\":{\\\"references\\\":[{\\\"commit\\\":\\\"f3c8a35a6e83e9ab85c05845c6731c7990de8672\\\",\\\"ref\\\":\\\"refs/heads/main\\\"}]},\\\"customData\\\":null,\\\"eventId\\\":\\\"58939308-51e9-4dad-abd5-97c8dabe3815\\\",\\\"eventName\\\":\\\"ReferenceChanges\\\",\\\"eventPartNumber\\\":1,\\\"eventSource\\\":\\\"aws:codecommit\\\",\\\"eventSourceARN\\\":\\\"arn:aws:codecommit:eu-central-1:448640225317:test\\\",\\\"eventTime\\\":\\\"2021-03-23T20:42:23.131+0000\\\",\\\"eventTotalParts\\\":1,\\\"eventTriggerConfigId\\\":\\\"96765393-40e7-4f49-aed7-64c9ba599195\\\",\\\"eventTriggerName\\\":\\\"push\\\",\\\"eventVersion\\\":\\\"1.0\\\",\\\"userIdentityARN\\\":\\\"arn:aws:iam::448640225317:user/vault-okta-aleksandar.radisavljevic@harness.io-a1616531435-217\\\"}]}\",\n"
      + " \"Timestamp\" : \"2021-03-23T20:42:23.163Z\",\n"
      + " \"SignatureVersion\" : \"1\",\n"
      + " \"Signature\" : \"qWIfB0MLIwWfGGEe+JgloCOL47LdizJU7Y9ETbz6jIeH/NNkyBYtzys18Q0TyJOsKzagddG1wqeS0K5n0Ewz2ZZys5nQNt3EAgagW8SrajWnToSuhFhAvJxxBhBsnHAoU7pV+4cZIWLKhF8ro4otm9xuDxnLD2sWTR8+xXzVdJJGDhcbxnykNJZ05O6kV/TEeCSCUBdQkhVZmyKM8XrN39QMnC8zZkFXXE9ssy51zVuH7IpSsq44pmEZQYA5lFXVHyu7p5fjih3Et03/IJg5i77zCjGRbjeRcER1JPlL74d+0U5jfINDRYSfc/gDBKQ3/zArLF12REO1orivX76NIQ==\","
      + " \"SigningCertURL\" : \"https://sns.eu-central-1.amazonaws.com/SimpleNotificationService-010a507c1833636cd94bdb98bd93083a.pem\",\n"
      + " \"UnsubscribeURL\" : \"https://sns.eu-central-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-central-1:448640225317:aws_cc_push_trigger:bf6ca40a-eb7c-43a8-b452-ec5869813da4\"\n"
      + "}";

  ParseWebhookResponse prEvent = ParseWebhookResponse.newBuilder()
                                     .setPr(PullRequestHook.newBuilder()
                                                .setPr(PullRequest.newBuilder()
                                                           .setNumber(1)
                                                           .setTitle("This is Title")
                                                           .setTarget("target")
                                                           .setSource("source")
                                                           .setSha("123")
                                                           .setBase(Reference.newBuilder().setSha("234").build())
                                                           .build())
                                                .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                                                .setSender(User.newBuilder().setLogin("user").build())
                                                .build())
                                     .build();

  ParseWebhookResponse pushEvent =
      ParseWebhookResponse.newBuilder()
          .setPush(PushHook.newBuilder()
                       .setAfter("456")
                       .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                       .setSender(User.newBuilder().setLogin("user").build())
                       .build())
          .build();

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetMetadataWebhook() {
    PlanExecutionMetadataService metadataService = mock(PlanExecutionMetadataServiceImpl.class);
    when(metadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(
            PlanExecutionMetadata.builder()
                .triggerJsonPayload(bigPayload)
                .triggerPayload(TriggerPayload.newBuilder()
                                    .setParsedPayload(ParsedPayload.newBuilder().setPr(prEvent.getPr()).build())
                                    .setType(Type.WEBHOOK)
                                    .setSourceType(SourceType.GITHUB_REPO)
                                    .build())
                .build()));
    SampleEvaluator expressionEvaluator = new SampleEvaluator(
        new TriggerFunctor(Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder()).build(), metadataService));

    assertThat(expressionEvaluator.renderExpression("<+trigger.branch>")).isEqualTo("source");
    assertThat(expressionEvaluator.renderExpression("<+trigger.sourceBranch>")).isEqualTo("source");
    assertThat(expressionEvaluator.renderExpression("<+trigger.targetBranch>")).isEqualTo("target");
    assertThat(expressionEvaluator.renderExpression("<+trigger.event>")).isEqualTo("PR");
    assertThat(expressionEvaluator.renderExpression("<+trigger.type>")).isEqualTo("Webhook");
    assertThat(expressionEvaluator.renderExpression("<+trigger.sourceType>")).isEqualTo("Github");
    assertThat(expressionEvaluator.renderExpression("<+trigger.commitSha>")).isEqualTo("123");
    assertThat(expressionEvaluator.renderExpression("<+trigger.baseCommitSha>")).isEqualTo("234");
    assertThat(expressionEvaluator.renderExpression("<+trigger.prNumber>")).isEqualTo("1");
    assertThat(expressionEvaluator.renderExpression("<+trigger.repoUrl>")).isEqualTo("https://github.com");
    assertThat(expressionEvaluator.renderExpression("<+trigger.gitUser>")).isEqualTo("user");
    assertThat(expressionEvaluator.renderExpression("<+trigger.prTitle>")).isEqualTo("This is Title");

    when(metadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(
            PlanExecutionMetadata.builder()
                .triggerJsonPayload(bigPayload)
                .triggerPayload(TriggerPayload.newBuilder()
                                    .setParsedPayload(ParsedPayload.newBuilder().setPush(pushEvent.getPush()).build())
                                    .setType(Type.WEBHOOK)
                                    .setSourceType(SourceType.GITHUB_REPO)
                                    .build())
                .build()));

    expressionEvaluator = new SampleEvaluator(
        new TriggerFunctor(Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder()).build(), metadataService));

    assertThat(expressionEvaluator.renderExpression("<+trigger.event>")).isEqualTo("PUSH");
    assertThat(expressionEvaluator.renderExpression("<+trigger.type>")).isEqualTo("Webhook");
    assertThat(expressionEvaluator.renderExpression("<+trigger.sourceType>")).isEqualTo("Github");
    assertThat(expressionEvaluator.renderExpression("<+trigger.repoUrl>")).isEqualTo("https://github.com");
    assertThat(expressionEvaluator.renderExpression("<+trigger.gitUser>")).isEqualTo("user");
  }

  public static class SampleEvaluator extends EngineExpressionEvaluator {
    TriggerFunctor triggerFunctor;

    public SampleEvaluator(TriggerFunctor triggerFunctor) {
      super(null);
      this.triggerFunctor = triggerFunctor;
    }

    @Override
    protected void initialize() {
      addToContext("trigger", triggerFunctor);
      super.initialize();
    }
  }
}
