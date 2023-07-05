/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook;

import static io.harness.constants.Constants.X_AMZ_SNS_MESSAGE_TYPE;
import static io.harness.constants.Constants.X_BIT_BUCKET_EVENT;
import static io.harness.constants.Constants.X_GIT_HUB_EVENT;
import static io.harness.constants.Constants.X_GIT_LAB_EVENT;
import static io.harness.constants.Constants.X_HARNESS_TRIGGER;
import static io.harness.constants.Constants.X_VSS_HEADER;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_BRANCH_HOOK_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PR_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PUSH_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.security.PrincipalProtoMapper.toPrincipalProto;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.webhookpayloads.webhookdata.GitDetails;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.BranchHook;
import io.harness.product.ci.scm.proto.IssueCommentHook;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.ReleaseHook;
import io.harness.rule.Owner;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WebhookHelperTest extends CategoryTest {
  @InjectMocks WebhookHelper webhookHelper;
  @Mock @Named(WEBHOOK_EVENTS_STREAM) private Producer webhookEventProducer;
  @Mock @Named(GIT_PUSH_EVENT_STREAM) private Producer gitPushEventProducer;
  @Mock @Named(GIT_PR_EVENT_STREAM) private Producer gitPrEventProducer;
  @Mock @Named(GIT_BRANCH_HOOK_EVENT_STREAM) private Producer gitBranchHookEventProducer;
  private String accountId = "accountId";
  private String payload = "payload";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testToNGTriggerWebhookEvent() {
    MultivaluedMap<String, String> httpHeaders = new MultivaluedHashMap<>();
    httpHeaders.add("key", "value");
    WebhookEvent webhookEvent = webhookHelper.toNGTriggerWebhookEvent(accountId, payload, httpHeaders);
    assertThat(webhookEvent.getHeaders().get(0))
        .isEqualTo(HeaderConfig.builder().key("key").values(Collections.singletonList("value")).build());
    assertThat(webhookEvent.getAccountId()).isEqualTo(accountId);
    assertThat(webhookEvent.getPayload()).isEqualTo(payload);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testContainsHeaderKey() {
    Map<String, List<String>> headers = new HashMap<>();
    assertThat(webhookHelper.containsHeaderKey(headers, "key")).isEqualTo(false);
    headers.put("key", Collections.singletonList("value"));
    assertThat(webhookHelper.containsHeaderKey(headers, "key")).isEqualTo(true);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGenerateWebhookDTO() {
    List<HeaderConfig> headerConfigs =
        Collections.singletonList(HeaderConfig.builder().key("key").values(Collections.singletonList("value")).build());
    WebhookEvent webhookEvent = WebhookEvent.builder()
                                    .payload(payload)
                                    .accountId(accountId)
                                    .headers(headerConfigs)
                                    .uuid("uuid")
                                    .createdAt(1L)
                                    .build();
    assertThat(webhookHelper.generateWebhookDTO(webhookEvent, null, null).getWebhookTriggerType())
        .isEqualTo(WebhookTriggerType.CUSTOM);
    ParseWebhookResponse parseWebhookResponse = ParseWebhookResponse.newBuilder().build();
    assertThat(webhookHelper.generateWebhookDTO(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB)
                   .getWebhookTriggerType())
        .isEqualTo(WebhookTriggerType.GIT);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetProducerListForEvent() {
    List<Producer> producers = new ArrayList<>();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder()
                                .setParsedResponse(ParseWebhookResponse.newBuilder().build())
                                .setGitDetails(GitDetails.newBuilder().setEvent(WebhookEventType.PR).build())
                                .build();
    producers.add(webhookEventProducer);
    producers.add(gitPrEventProducer);
    assertThat(webhookHelper.getProducerListForEvent(webhookDTO)).isEqualTo(producers);

    producers.remove(gitPrEventProducer);
    webhookDTO = WebhookDTO.newBuilder()
                     .setParsedResponse(ParseWebhookResponse.newBuilder().build())
                     .setGitDetails(GitDetails.newBuilder().setEvent(WebhookEventType.PUSH).build())
                     .build();
    producers.add(gitPushEventProducer);
    assertThat(webhookHelper.getProducerListForEvent(webhookDTO)).isEqualTo(producers);

    producers.remove(gitPushEventProducer);
    webhookDTO = WebhookDTO.newBuilder()
                     .setParsedResponse(ParseWebhookResponse.newBuilder().build())
                     .setGitDetails(GitDetails.newBuilder().setEvent(WebhookEventType.PUSH).build())
                     .build();
    producers.add(gitPushEventProducer);
    assertThat(webhookHelper.getProducerListForEvent(webhookDTO)).isEqualTo(producers);

    producers.remove(gitPushEventProducer);
    webhookDTO = WebhookDTO.newBuilder()
                     .setParsedResponse(ParseWebhookResponse.newBuilder().build())
                     .setGitDetails(GitDetails.newBuilder().setEvent(WebhookEventType.CREATE_BRANCH).build())
                     .build();
    producers.add(gitBranchHookEventProducer);
    assertThat(webhookHelper.getProducerListForEvent(webhookDTO)).isEqualTo(producers);

    webhookDTO = WebhookDTO.newBuilder()
                     .setParsedResponse(ParseWebhookResponse.newBuilder().build())
                     .setGitDetails(GitDetails.newBuilder().setEvent(WebhookEventType.DELETE_BRANCH).build())
                     .build();
    assertThat(webhookHelper.getProducerListForEvent(webhookDTO)).isEqualTo(producers);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetSourceRepoType() {
    WebhookEvent webhookEvent =
        WebhookEvent.builder()
            .headers(Collections.singletonList(
                HeaderConfig.builder().key(X_GIT_HUB_EVENT).values(Collections.singletonList("value")).build()))
            .build();
    assertThat(webhookHelper.getSourceRepoType(webhookEvent)).isEqualTo(SourceRepoType.GITHUB);

    webhookEvent.setHeaders(Collections.singletonList(
        HeaderConfig.builder().key(X_GIT_LAB_EVENT).values(Collections.singletonList("value")).build()));
    assertThat(webhookHelper.getSourceRepoType(webhookEvent)).isEqualTo(SourceRepoType.GITLAB);

    webhookEvent.setHeaders(Collections.singletonList(
        HeaderConfig.builder().key(X_BIT_BUCKET_EVENT).values(Collections.singletonList("value")).build()));
    assertThat(webhookHelper.getSourceRepoType(webhookEvent)).isEqualTo(SourceRepoType.BITBUCKET);

    webhookEvent.setHeaders(Collections.singletonList(
        HeaderConfig.builder().key(X_AMZ_SNS_MESSAGE_TYPE).values(Collections.singletonList("value")).build()));
    assertThat(webhookHelper.getSourceRepoType(webhookEvent)).isEqualTo(SourceRepoType.AWS_CODECOMMIT);

    webhookEvent.setHeaders(Collections.singletonList(
        HeaderConfig.builder().key(X_VSS_HEADER).values(Collections.singletonList("value")).build()));
    assertThat(webhookHelper.getSourceRepoType(webhookEvent)).isEqualTo(SourceRepoType.AZURE);

    webhookEvent.setHeaders(Collections.singletonList(
        HeaderConfig.builder().key(X_HARNESS_TRIGGER).values(Collections.singletonList("value")).build()));
    assertThat(webhookHelper.getSourceRepoType(webhookEvent)).isEqualTo(SourceRepoType.HARNESS);

    webhookEvent.setHeaders(Collections.singletonList(
        HeaderConfig.builder().key("key").values(Collections.singletonList("value")).build()));
    assertThat(webhookHelper.getSourceRepoType(webhookEvent)).isEqualTo(SourceRepoType.UNRECOGNIZED);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGenerateGitDetails() {
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build();
    assertThat(webhookHelper.generateGitDetails(parseWebhookResponse, SourceRepoType.GITHUB))
        .isEqualTo(
            GitDetails.newBuilder().setSourceRepoType(SourceRepoType.GITHUB).setEvent(WebhookEventType.PR).build());

    parseWebhookResponse = ParseWebhookResponse.newBuilder().setComment(IssueCommentHook.newBuilder().build()).build();
    assertThat(webhookHelper.generateGitDetails(parseWebhookResponse, SourceRepoType.GITHUB))
        .isEqualTo(GitDetails.newBuilder()
                       .setSourceRepoType(SourceRepoType.GITHUB)
                       .setEvent(WebhookEventType.ISSUE_COMMENT)
                       .build());

    parseWebhookResponse = ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().build()).build();
    assertThat(webhookHelper.generateGitDetails(parseWebhookResponse, SourceRepoType.GITHUB))
        .isEqualTo(
            GitDetails.newBuilder().setSourceRepoType(SourceRepoType.GITHUB).setEvent(WebhookEventType.PUSH).build());

    parseWebhookResponse = ParseWebhookResponse.newBuilder().setRelease(ReleaseHook.newBuilder().build()).build();
    assertThat(webhookHelper.generateGitDetails(parseWebhookResponse, SourceRepoType.GITHUB))
        .isEqualTo(GitDetails.newBuilder()
                       .setSourceRepoType(SourceRepoType.GITHUB)
                       .setEvent(WebhookEventType.RELEASE)
                       .build());

    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setBranch(BranchHook.newBuilder().setAction(Action.CREATE).build()).build();
    assertThat(webhookHelper.generateGitDetails(parseWebhookResponse, SourceRepoType.GITHUB))
        .isEqualTo(GitDetails.newBuilder()
                       .setSourceRepoType(SourceRepoType.GITHUB)
                       .setEvent(WebhookEventType.CREATE_BRANCH)
                       .build());

    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setBranch(BranchHook.newBuilder().setAction(Action.DELETE).build()).build();
    assertThat(webhookHelper.generateGitDetails(parseWebhookResponse, SourceRepoType.GITHUB))
        .isEqualTo(GitDetails.newBuilder()
                       .setSourceRepoType(SourceRepoType.GITHUB)
                       .setEvent(WebhookEventType.DELETE_BRANCH)
                       .build());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGenerateWebhookDTOForCustomWebhook() {
    HeaderConfig header = HeaderConfig.builder().key("header-key").values(List.of("value")).build();
    Principal principal = new UserPrincipal("name", "mail", "username", "account");
    WebhookEvent webhookEvent = WebhookEvent.builder()
                                    .payload("payload")
                                    .headers(List.of(header))
                                    .accountId("account")
                                    .uuid("id")
                                    .createdAt(123L)
                                    .principal(principal)
                                    .build();
    WebhookDTO webhookDTO = webhookHelper.generateWebhookDTO(webhookEvent, null, null);
    assertThat(webhookDTO.getAccountId()).isEqualTo("account");
    assertThat(webhookDTO.getHeaders(0).getKey()).isEqualTo("header-key");
    assertThat(webhookDTO.getHeaders(0).getValues(0)).isEqualTo("value");
    assertThat(webhookDTO.getJsonPayload()).isEqualTo("payload");
    assertThat(webhookDTO.getEventId()).isEqualTo("id");
    assertThat(webhookDTO.getTime()).isEqualTo(123L);
    assertThat(webhookDTO.getPrincipal()).isEqualToComparingFieldByField(toPrincipalProto(principal));
  }
}
