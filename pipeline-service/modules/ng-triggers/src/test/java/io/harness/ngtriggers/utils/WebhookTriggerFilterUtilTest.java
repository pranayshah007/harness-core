/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.X_HARNESS_TRIGGER_ID;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.CONTAINS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.DOES_NOT_CONTAIN;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.ENDS_WITH;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.IN;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.NOT_EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.NOT_IN;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.REGEX;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.STARTS_WITH;
import static io.harness.ngtriggers.utils.WebhookTriggerFilterUtils.checkIfActionMatches;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VINICIUS;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.beans.PRWebhookEvent;
import io.harness.beans.PRWebhookEvent.PRWebhookEventBuilder;
import io.harness.beans.WebhookBaseAttributes;
import io.harness.beans.WebhookBaseAttributes.WebhookBaseAttributesBuilder;
import io.harness.beans.WebhookEvent.Type;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData.WebhookPayloadDataBuilder;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubIssueCommentSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubReleaseSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.GitlabSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabTriggerEvent;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class WebhookTriggerFilterUtilTest extends CategoryTest {
  private String payload = "    {\n"
      + "\t\t\"object_kind\": \"merge_request\",\n"
      + "\t\t\"event_type\": \"merge_request\",\n"
      + "\t\t\"user\": {\n"
      + "\t\t  \"name\": \"charles grant\",\n"
      + "\t\t  \"username\": \"charles.grant\",\n"
      + "\t\t  \"avatar_url\": \"https://secure.gravatar.com/avatar/8e\",\n"
      + "\t\t  \"email\": \"cgrant@gmail.com\"\n"
      + "\t\t}\n"
      + "    } ";

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void parseEventTest() {
    TriggerExpressionEvaluator triggerExpressionEvaluator =
        WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(null, emptyList(), payload);
    assertThat(WebhookTriggerFilterUtils.readFromPayload("<+trigger.payload.event_type>", triggerExpressionEvaluator))
        .isEqualTo("merge_request");
    assertThat(WebhookTriggerFilterUtils.readFromPayload("<+trigger.payload.object_kind>", triggerExpressionEvaluator))
        .isEqualTo("merge_request");
    assertThat(WebhookTriggerFilterUtils.readFromPayload("<+trigger.payload.user.name>", triggerExpressionEvaluator))
        .isEqualTo("charles grant");
    assertThat(
        WebhookTriggerFilterUtils.readFromPayload("<+trigger.payload.user.username>", triggerExpressionEvaluator))
        .isEqualTo("charles.grant");
    assertThat(
        WebhookTriggerFilterUtils.readFromPayload("<+trigger.payload.user.avatar_url>", triggerExpressionEvaluator))
        .isEqualTo("https://secure.gravatar.com/avatar/8e");
    assertThat(WebhookTriggerFilterUtils.readFromPayload("<+trigger.payload.user.email>", triggerExpressionEvaluator))
        .isEqualTo("cgrant@gmail.com");
    assertThat(triggerExpressionEvaluator.renderExpression("<+trigger.eventPayload>")).isEqualTo(payload);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void ConvertToLowerCaseTest() {
    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl("<+trigger.header['X-GitHub-Event']>"))
        .isEqualTo("<+trigger.header['x-github-event']>");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl("<+trigger.header[\"X-GitHub-Event\"]>"))
        .isEqualTo("<+trigger.header['x-github-event']>");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl("<+trigger.header['X-github-Event']>"))
        .isEqualTo("<+trigger.header['x-github-event']>");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl("<+trigger.header['x-gituhb-event']>"))
        .isEqualTo("<+trigger.header['x-gituhb-event']>");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl("<+trigger.header['XGitHubEvent']>"))
        .isEqualTo("<+trigger.header['xgithubevent']>");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl("<+trigger.header[\"XGitHubEvent\"]>"))
        .isEqualTo("<+trigger.header['xgithubevent']>");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl("<+trigger.header['zzzz']>"))
        .isEqualTo("<+trigger.header['zzzz']>");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl("<+trigger.header[\"zzzz\"]>"))
        .isEqualTo("<+trigger.header['zzzz']>");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl("<+trigger.header.zzzz>"))
        .isEqualTo("<+trigger.header.zzzz>");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl(null)).isEqualTo(null);

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl(
                   "<+trigger.payload.pull_request.user.login> == \"adwait\" && "
                   + "<+trigger.header['X-GitHub-Event']> == \"PR\" || <+trigger.header['X-Event']> == \"PR\""))
        .isEqualTo(
            "<+trigger.payload.pull_request.user.login> == \"adwait\" && <+trigger.header['x-github-event']> == \"PR\" || <+trigger.header['x-event']> == \"PR\"");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl(
                   "<+trigger.payload.pull_request.user.login> == \"adwait\" && "
                   + "<+trigger.header['x-github-event']> == \"PR\" || <+trigger.header['X-Event']> == \"PR\""))
        .isEqualTo(
            "<+trigger.payload.pull_request.user.login> == \"adwait\" && <+trigger.header['x-github-event']> == \"PR\" || <+trigger.header['x-event']> == \"PR\"");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl(
                   "<+trigger.payload.pull_request.user.login> == \"adwait\" && "
                   + "<+trigger.header['xGithubEvent']> == \"PR\" || <+trigger.header['X-Event']> == \"PR\""))
        .isEqualTo(
            "<+trigger.payload.pull_request.user.login> == \"adwait\" && <+trigger.header['xgithubevent']> == \"PR\" || <+trigger.header['x-event']> == \"PR\"");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl(
                   "<+trigger.payload.pull_request.user.login> == \"adwait\" && "
                   + "<+trigger.header['xgithubevent']> == \"PR\" || <+trigger.header[\"X-Event\"]> == \"PR\""))
        .isEqualTo(
            "<+trigger.payload.pull_request.user.login> == \"adwait\" && <+trigger.header['xgithubevent']> == \"PR\" || <+trigger.header['x-event']> == \"PR\"");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl(
                   "<+trigger.payload.pull_request.user.login> == \"adwait\" && "
                   + "<+trigger.header[\"xgithubevent\"]> == \"PR\" || <+trigger.header[\"X-Event\"]> == \"PR\""))
        .isEqualTo(
            "<+trigger.payload.pull_request.user.login> == \"adwait\" && <+trigger.header['xgithubevent']> == \"PR\" || <+trigger.header['x-event']> == \"PR\"");

    assertThat(WebhookTriggerFilterUtils.sanitiseHeaderConditionsForJexl(
                   "<+trigger.payload.pull_request.user.login> == \"adwait\" && "
                   + "(<+trigger.header['x-github-event']> == \"PR\" || <+trigger.header.XEvent> == \"PR\")"))
        .isEqualTo(
            "<+trigger.payload.pull_request.user.login> == \"adwait\" && (<+trigger.header['x-github-event']> == \"PR\" || <+trigger.header.XEvent> == \"PR\")");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void checkIfEventTypeMatchesTest() {
    WebhookTriggerSpecV2 webhookTriggerSpecPR =
        GithubSpec.builder().type(GithubTriggerEvent.PULL_REQUEST).spec(GithubPRSpec.builder().build()).build();
    WebhookTriggerSpecV2 webhookTriggerSpecPush =
        GithubSpec.builder().type(GithubTriggerEvent.PUSH).spec(GithubPushSpec.builder().build()).build();
    WebhookTriggerSpecV2 webhookTriggerSpecIssueComment = GithubSpec.builder()
                                                              .type(GithubTriggerEvent.ISSUE_COMMENT)
                                                              .spec(GithubIssueCommentSpec.builder().build())
                                                              .build();
    WebhookTriggerSpecV2 webhookTriggerSpecRelease =
        GithubSpec.builder().type(GithubTriggerEvent.RELEASE).spec(GithubReleaseSpec.builder().build()).build();
    assertThat(
        WebhookTriggerFilterUtils.checkIfEventTypeMatches(io.harness.beans.WebhookEvent.Type.PR, webhookTriggerSpecPR))
        .isTrue();
    assertThat(WebhookTriggerFilterUtils.checkIfEventTypeMatches(
                   io.harness.beans.WebhookEvent.Type.PR, webhookTriggerSpecPush))
        .isFalse();
    assertThat(WebhookTriggerFilterUtils.checkIfEventTypeMatches(
                   io.harness.beans.WebhookEvent.Type.PUSH, webhookTriggerSpecPush))
        .isTrue();
    assertThat(WebhookTriggerFilterUtils.checkIfEventTypeMatches(
                   io.harness.beans.WebhookEvent.Type.PUSH, webhookTriggerSpecIssueComment))
        .isFalse();
    assertThat(WebhookTriggerFilterUtils.checkIfEventTypeMatches(Type.ISSUE_COMMENT, webhookTriggerSpecIssueComment))
        .isTrue();
    assertThat(WebhookTriggerFilterUtils.checkIfEventTypeMatches(Type.ISSUE_COMMENT, webhookTriggerSpecRelease))
        .isFalse();
    assertThat(WebhookTriggerFilterUtils.checkIfEventTypeMatches(
                   io.harness.beans.WebhookEvent.Type.RELEASE, webhookTriggerSpecRelease))
        .isTrue();
    assertThat(WebhookTriggerFilterUtils.checkIfEventTypeMatches(
                   io.harness.beans.WebhookEvent.Type.RELEASE, webhookTriggerSpecPR))
        .isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void checkIfActionMatchesTest() {
    WebhookBaseAttributesBuilder baseAttributesBuilder = WebhookBaseAttributes.builder().action("open");
    PRWebhookEventBuilder prWebhookEventBuilder =
        PRWebhookEvent.builder().baseAttributes(baseAttributesBuilder.build());
    WebhookPayloadDataBuilder webhookPayloadDataBuilder =
        WebhookPayloadData.builder()
            .originalEvent(TriggerWebhookEvent.builder().payload(payload).build())
            .webhookEvent(prWebhookEventBuilder.build());

    List<GithubPRAction> githubPRActions = new ArrayList<>();
    githubPRActions.add(GithubPRAction.OPEN);
    WebhookTriggerSpecV2 webhookTriggerSpec = GithubSpec.builder()
                                                  .type(GithubTriggerEvent.PULL_REQUEST)
                                                  .spec(GithubPRSpec.builder().actions(githubPRActions).build())
                                                  .build();
    assertThat(checkIfActionMatches(webhookPayloadDataBuilder.build(), webhookTriggerSpec)).isTrue();

    githubPRActions.clear();
    assertThat(checkIfActionMatches(webhookPayloadDataBuilder.build(), webhookTriggerSpec)).isTrue();

    githubPRActions.add(GithubPRAction.CLOSE);
    assertThat(checkIfActionMatches(webhookPayloadDataBuilder.build(), webhookTriggerSpec)).isFalse();

    baseAttributesBuilder.action("close");
    webhookPayloadDataBuilder.webhookEvent(prWebhookEventBuilder.baseAttributes(baseAttributesBuilder.build()).build())
        .build();
    assertThat(checkIfActionMatches(webhookPayloadDataBuilder.build(), webhookTriggerSpec)).isTrue();

    githubPRActions.clear();
    assertThat(checkIfActionMatches(webhookPayloadDataBuilder.build(), webhookTriggerSpec)).isTrue();

    githubPRActions.add(GithubPRAction.EDIT);
    assertThat(checkIfActionMatches(webhookPayloadDataBuilder.build(), webhookTriggerSpec)).isFalse();

    baseAttributesBuilder.action("update");
    webhookPayloadDataBuilder.webhookEvent(prWebhookEventBuilder.baseAttributes(baseAttributesBuilder.build()).build())
        .build();
    assertThat(checkIfActionMatches(webhookPayloadDataBuilder.build(), webhookTriggerSpec)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void evaluateFilterConditionsTest() {
    WebhookTriggerSpecV2 webhookTriggerSpec = getGitLabTriggerSpec();

    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder()
            .originalEvent(TriggerWebhookEvent.builder().payload(payload).build())
            .webhookEvent(PRWebhookEvent.builder()
                              .baseAttributes(WebhookBaseAttributes.builder().source("stage").target("master").build())
                              .build())
            .build();

    assertThat(WebhookTriggerFilterUtils.checkIfEventTypeMatches(Type.PR, webhookTriggerSpec)).isTrue();
    assertThat(checkIfActionMatches(webhookPayloadData, webhookTriggerSpec)).isTrue();
    assertThat(WebhookTriggerFilterUtils.checkIfPayloadConditionsMatch(webhookPayloadData, webhookTriggerSpec))
        .isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void checkIfJexlConditionsMatchTest() {
    WebhookTriggerSpecV2 webhookTriggerSpec = getGitLabTriggerSpec();

    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder()
            .originalEvent(TriggerWebhookEvent.builder().payload(payload).build())
            .webhookEvent(PRWebhookEvent.builder()
                              .baseAttributes(WebhookBaseAttributes.builder().source("stage").target("master").build())
                              .build())
            .build();

    assertThat(WebhookTriggerFilterUtils.checkIfEventTypeMatches(Type.PR, webhookTriggerSpec)).isTrue();
    assertThat(checkIfActionMatches(webhookPayloadData, webhookTriggerSpec)).isTrue();
    assertThat(WebhookTriggerFilterUtils.checkIfJexlConditionsMatch(webhookPayloadData.getParseWebhookResponse(),
                   Collections.emptyList(), payload, webhookTriggerSpec.fetchPayloadAware().fetchJexlCondition()))
        .isTrue();
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void evaluateFilterConditionsDoesNotContainTest() {
    WebhookTriggerSpecV2 webhookTriggerSpec = getGitLabTriggerSpec();

    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder()
            .originalEvent(TriggerWebhookEvent.builder().payload(payload).build())
            .webhookEvent(PRWebhookEvent.builder()
                              .baseAttributes(WebhookBaseAttributes.builder().source("stage").target("master").build())
                              .build())
            .build();

    assertThat(WebhookTriggerFilterUtils.checkIfEventTypeMatches(Type.PR, webhookTriggerSpec)).isTrue();
    assertThat(checkIfActionMatches(webhookPayloadData, webhookTriggerSpec)).isTrue();
    assertThat(WebhookTriggerFilterUtils.checkIfPayloadConditionsMatch(webhookPayloadData, webhookTriggerSpec))
        .isTrue();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void evaluateFilterConditionsForCustomPayloadWebhookTest() {
    List<HeaderConfig> headerConfigList =
        Arrays.asList(HeaderConfig.builder().key("X-GITHUB-EVENT").values(Arrays.asList("push")).build(),
            HeaderConfig.builder().key(X_HARNESS_TRIGGER_ID).values(Arrays.asList("customertriggerspec")).build());

    assertThat(WebhookTriggerFilterUtils.checkIfCustomHeaderConditionsMatch(headerConfigList,
                   Arrays.asList(TriggerEventDataCondition.builder()
                                     .key("X-HARNESS-TRIGGER-IDENTIFIER")
                                     .operator(EQUALS)
                                     .value("customertriggerspec")
                                     .build(),
                       TriggerEventDataCondition.builder()
                           .key("X-GITHUB-EVENT")
                           .operator(IN)
                           .value("push, pull_request")
                           .build())))
        .isFalse();
  }

  private WebhookTriggerSpecV2 getGitLabTriggerSpec() {
    return GitlabSpec.builder()
        .type(GitlabTriggerEvent.MERGE_REQUEST)
        .spec(
            GitlabPRSpec.builder()
                .actions(emptyList())
                .payloadConditions(Arrays.asList(
                    TriggerEventDataCondition.builder().key("sourceBranch").operator(EQUALS).value("stage").build(),
                    TriggerEventDataCondition.builder().key("sourceBranch").operator(NOT_EQUALS).value("qa").build(),
                    TriggerEventDataCondition.builder().key("targetBranch").operator(REGEX).value("^master$").build(),
                    TriggerEventDataCondition.builder()
                        .key("<+trigger.payload.event_type>")
                        .operator(IN)
                        .value("pull_request, merge_request")
                        .build(),
                    TriggerEventDataCondition.builder()
                        .key("<+trigger.payload.object_kind>")
                        .operator(NOT_IN)
                        .value("push, package")
                        .build(),
                    TriggerEventDataCondition.builder()
                        .key("<+trigger.payload.user.name>")
                        .operator(STARTS_WITH)
                        .value("charles")
                        .build(),
                    TriggerEventDataCondition.builder()
                        .key("<+trigger.payload.user.username>")
                        .operator(ENDS_WITH)
                        .value("grant")
                        .build(),
                    TriggerEventDataCondition.builder()
                        .key("<+trigger.payload.user.avatar_url>")
                        .operator(CONTAINS)
                        .value("secure.gravatar.com")
                        .build(),
                    TriggerEventDataCondition.builder()
                        .key("<+trigger.payload.user.email>")
                        .operator(DOES_NOT_CONTAIN)
                        .value("secure.gravatar.com")
                        .build()))
                .jexlCondition(
                    "<+trigger.payload.user.name> == \"charles grant\" && <+trigger.payload.user.email> != \"wrong_email\"")
                .build())
        .build();
  }
}
