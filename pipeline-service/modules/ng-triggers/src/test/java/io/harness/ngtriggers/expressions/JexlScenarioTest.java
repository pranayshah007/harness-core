/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.rule.OwnerRule.MATT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.utils.WebhookTriggerFilterUtils;
import io.harness.pms.contracts.triggers.ArtifactData;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class JexlScenarioTest extends CategoryTest {
  private String smallPayload = "{\n"
      + "  \"Type\" : \"Notification\",\n"
      + "  \"MessageId\" : \"43eb6d30-4059-59d7-a714-35e414b6c4b9\",\n"
      + "  \"TopicArn\" : \"arn:aws:sns:eu-central-1:448640225317:aws_cc_push_trigger\",\n"
      + "  \"Subject\" : \"UPDATE: AWS CodeCommit eu-central-1 push: test\",\n"
      + "  \"Timestamp\" : \"2021-03-23T20:42:23.163Z\",\n"
      + "  \"arr\" : [\"abc\", \"def\"],\n"
      + "  \"SignatureVersion\" : \"1\"\n"
      + "}";

  private String json = "{\n"
      + "  \"action\": \"opened\",\n"
      + "  \"number\": 1,\n"
      + "  \"pull_request\": {\n"
      + "    \"id\": 526274089,\n"
      + "    \"assignee\": [\"test\", \"test1\"],\n"
      + "    \"Labels\": [\n"
      + "       {\"name\": \"python\"},\n"
      + "       {\"name\": \"java\"}\n"
      + "    ]\n"
      + "  }\n"
      + "}";

  private String jsonMeta = "{\n"
      + "  \"field1\": \"value1\",\n"
      + "  \"field2\": \"value2\"\n"
      + "}";

  private String jsonGo = "{\n"
      + "  \"action\": \"opened\",\n"
      + "  \"number\": 1,\n"
      + "  \"pull_request\": {\n"
      + "    \"id\": 526274089,\n"
      + "    \"assignee\": [\"test\", \"test1\"],\n"
      + "    \"Labels\": [\n"
      + "       {\"name\": \"go\"},\n"
      + "       {\"name\": \"java\"}\n"
      + "    ]\n"
      + "  }\n"
      + "}";

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
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testSimplePayload() {
    assertThat(WebhookTriggerFilterUtils.checkIfJexlConditionsMatch(
                   null, emptyList(), smallPayload, "<+trigger.payload.arr>.contains(\"abc\")"))
        .isTrue();
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testSimpleNegative() {
    assertThat(WebhookTriggerFilterUtils.checkIfJexlConditionsMatch(null, emptyList(), smallPayload, "false"))
        .isFalse();
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testJexlAnd() {
    assertThat(WebhookTriggerFilterUtils.checkIfJexlConditionsMatch(null, emptyList(), bigPayload,
                   "<+trigger.payload.Type> == \"Notification\" && <+trigger.payload.SignatureVersion> == 1"))
        .isTrue();
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testJexlOr() {
    assertThat(WebhookTriggerFilterUtils.checkIfJexlConditionsMatch(null, emptyList(), bigPayload,
                   "<+trigger.payload.Subject>.contains(\"GIT\") || <+trigger.payload.SignatureVersion> == 1"))
        .isTrue();
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testJexlArithmetic() {
    assertThat(WebhookTriggerFilterUtils.checkIfJexlConditionsMatch(null, emptyList(), bigPayload,
                   "size(<+trigger.payload.Signature>) + size(<+trigger.payload.MessageId>) > 300"))
        .isTrue();
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testJexlParens() {
    assertThat(
        WebhookTriggerFilterUtils.checkIfJexlConditionsMatch(null, emptyList(), bigPayload,
            "(size(<+trigger.payload.Signature>) + size(<+trigger.payload.MessageId>) > 300) && (<+trigger.payload.Subject>.contains(\"GIT\") || <+trigger.payload.SignatureVersion> == 1)"))
        .isTrue();
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testJexlScript() {
    TriggerExpressionEvaluator triggerExpressionEvaluator =
        new TriggerExpressionEvaluator(null, null, emptyList(), json);
    Object o = triggerExpressionEvaluator.evaluateExpression(
        "for (var item : <+trigger.payload.pull_request.Labels>) { if (item.name == 'java') return true; } return false;");
    assertThat((Boolean) o).isTrue();

    o = triggerExpressionEvaluator.evaluateExpression(
        "for (var item : <+trigger.payload.pull_request.Labels>) { if (item.name == 'go') return true; } return false;");
    assertThat((Boolean) o).isFalse();

    triggerExpressionEvaluator = new TriggerExpressionEvaluator(null, null, emptyList(), jsonGo);
    o = triggerExpressionEvaluator.evaluateExpression(
        "for (var item : <+trigger.payload.pull_request.Labels>) { if (item.name == 'go') return true; } return false;");
    assertThat((Boolean) o).isTrue();

    o = triggerExpressionEvaluator.evaluateExpression(
        "for (var item : <+trigger.payload.pull_request.Labels>) { if (item.name == 'go') return false; } return true;");
    assertThat((Boolean) o).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testMapWebhookEventToTriggers() {
    TriggerExpressionEvaluator triggerExpressionEvaluator =
        new TriggerExpressionEvaluator(null, null, emptyList(), json);
    assertThat(triggerExpressionEvaluator.renderExpression("<+trigger.payload.pull_request.Labels[0].name>"))
        .isEqualTo("python");
    Object o =
        triggerExpressionEvaluator.evaluateExpression("<+trigger.payload.pull_request.assignee>.contains('test')");
    assertThat((Boolean) o).isTrue();
    assertThat(triggerExpressionEvaluator.renderExpression("<+trigger.payload.pull_request.Labels[1].name>"))
        .isEqualTo("java");
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testGetHeaderWebhook() {
    TriggerExpressionEvaluator triggerExpressionEvaluator = new TriggerExpressionEvaluator(null, null,
        Arrays.asList(HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build()),
        json);
    assertThat(triggerExpressionEvaluator.renderExpression("<+trigger.header['content-type']>"))
        .isEqualTo("application/json");
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testGetMetadataWebhook() {
    TriggerExpressionEvaluator triggerExpressionEvaluator =
        new TriggerExpressionEvaluator(prEvent, null, emptyList(), json);
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.sourceBranch>")).isEqualTo("source");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.targetBranch>")).isEqualTo("target");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.event>")).isEqualTo("PR");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.type>")).isEqualTo("Webhook");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.commitSha>")).isEqualTo("123");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.prNumber>")).isEqualTo("1");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.repoUrl>")).isEqualTo("https://github.com");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.gitUser>")).isEqualTo("user");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.prTitle>")).isEqualTo("This is Title");

    triggerExpressionEvaluator = new TriggerExpressionEvaluator(pushEvent, null, emptyList(), json);
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.commitSha>")).isEqualTo("456");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.repoUrl>")).isEqualTo("https://github.com");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.gitUser>")).isEqualTo("user");
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetVariablesManualExecution() {
    TriggerExpressionEvaluator triggerExpressionEvaluator =
        new TriggerExpressionEvaluator(null, null, emptyList(), json);
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.sourceBranch>")).isEqualTo("null");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.targetBranch>")).isEqualTo("null");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.event>")).isEqualTo("null");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.commitSha>")).isEqualTo("null");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.prNumber>")).isEqualTo("null");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.repoUrl>")).isEqualTo("null");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.gitUser>")).isEqualTo("null");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.prTitle>")).isEqualTo("null");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetVariablesForArtifactMetadata() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("field1", "value1");
    metadata.put("field2", "value2");
    ArtifactData artifactData = ArtifactData.newBuilder().putAllMetadata(metadata).build();
    TriggerExpressionEvaluator triggerExpressionEvaluator =
        new TriggerExpressionEvaluator(null, artifactData, emptyList(), jsonMeta);
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.artifact.metadata.field1>"))
        .isEqualTo("value1");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.artifact.metadata.field2>"))
        .isEqualTo("value2");
    assertThat(triggerExpressionEvaluator.evaluateExpression("<+trigger.artifact.metadata.field3>")).isEqualTo("null");
  }
}
