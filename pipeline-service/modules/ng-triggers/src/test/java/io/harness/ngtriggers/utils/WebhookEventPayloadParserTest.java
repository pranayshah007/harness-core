/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.HeaderConfig;
import io.harness.beans.PushWebhookEvent;
import io.harness.beans.Repository;
import io.harness.beans.WebhookEvent;
import io.harness.beans.WebhookGitUser;
import io.harness.beans.WebhookPayload;
import io.harness.category.element.UnitTests;
import io.harness.impl.WebhookParserSCMServiceImpl;
import io.harness.ngtriggers.beans.dto.WebhookEventHeaderData;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class WebhookEventPayloadParserTest extends CategoryTest {
  @Spy @InjectMocks WebhookEventPayloadParser webhookEventPayloadParser;
  @Mock WebhookParserSCMServiceImpl webhookParserSCMService;
  private TriggerWebhookEvent triggerWebhookEvent;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
    triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload("{a: b}")
            .build();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void parseEventTest() {
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("previousCommit").setAfter("lastCommit").build())
            .build();
    when(webhookParserSCMService.parseWebhookUsingSCMAPI(any(), any())).thenReturn(parseWebhookResponse);
    when(webhookParserSCMService.parseWebhookPayload(parseWebhookResponse))
        .thenReturn(WebhookPayload.builder()
                        .parseWebhookResponse(parseWebhookResponse)
                        .repository(Repository.builder().branch("a_branch").build())
                        .build());
    webhookEventPayloadParser.parseEvent(triggerWebhookEvent);
    verify(webhookParserSCMService, times(1))
        .parseWebhookUsingSCMAPI(triggerWebhookEvent.getHeaders(), triggerWebhookEvent.getPayload());
    verify(webhookParserSCMService, times(1)).parseWebhookPayload(parseWebhookResponse);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void invokeScmServiceTest() {
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("previousCommit").setAfter("lastCommit").build())
            .build();
    when(webhookParserSCMService.parseWebhookUsingSCMAPI(any(), any())).thenReturn(parseWebhookResponse);
    webhookEventPayloadParser.invokeScmService(triggerWebhookEvent);
    verify(webhookParserSCMService, times(1))
        .parseWebhookUsingSCMAPI(triggerWebhookEvent.getHeaders(), triggerWebhookEvent.getPayload());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void convertWebhookResponseTest() {
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("previousCommit").setAfter("lastCommit").build())
            .build();
    WebhookGitUser webhookGitUser = WebhookGitUser.builder().name("name").build();
    Repository repository = Repository.builder().branch("a_branch").build();
    WebhookEvent webhookEvent = PushWebhookEvent.builder().link("a_link").build();
    when(webhookParserSCMService.parseWebhookPayload(parseWebhookResponse))
        .thenReturn(WebhookPayload.builder()
                        .parseWebhookResponse(parseWebhookResponse)
                        .repository(repository)
                        .webhookGitUser(webhookGitUser)
                        .webhookEvent(webhookEvent)
                        .build());
    WebhookPayloadData convertedWebhookResponse =
        webhookEventPayloadParser.convertWebhookResponse(parseWebhookResponse, triggerWebhookEvent);
    verify(webhookParserSCMService, times(1)).parseWebhookPayload(parseWebhookResponse);
    assertThat(convertedWebhookResponse.getParseWebhookResponse()).isEqualToComparingFieldByField(parseWebhookResponse);
    assertThat(convertedWebhookResponse.getOriginalEvent()).isEqualToComparingFieldByField(triggerWebhookEvent);
    assertThat(convertedWebhookResponse.getWebhookGitUser()).isEqualToComparingFieldByField(webhookGitUser);
    assertThat(convertedWebhookResponse.getWebhookEvent()).isEqualToComparingFieldByField(webhookEvent);
    assertThat(convertedWebhookResponse.getRepository()).isEqualToComparingFieldByField(repository);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void containsHeaderKeyTrueTest() {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("hEaDeR1", Collections.singletonList("value"));
    assertThat(webhookEventPayloadParser.containsHeaderKey(headers, "header1")).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void containsHeaderKeyFalseTest() {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("hEaDeR1", Collections.singletonList("value"));
    assertThat(webhookEventPayloadParser.containsHeaderKey(headers, "header")).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void getHeaderValueTest() {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("hEaDeR1", Collections.singletonList("value"));
    assertThat(webhookEventPayloadParser.getHeaderValue(headers, "header1"))
        .isEqualTo(Collections.singletonList("value"));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void parseObtainWebhookSourceKeyDataTest() {
    HeaderConfig headerConfig1 = HeaderConfig.builder().key("test1").values(Arrays.asList("a", "b")).build();
    HeaderConfig headerConfig2 = HeaderConfig.builder().key("test2").values(Arrays.asList("c", "b")).build();
    HeaderConfig headerConfig3 =
        HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("Pull Request")).build();

    WebhookEventHeaderData webhookEventHeaderData = webhookEventPayloadParser.obtainWebhookSourceKeyData(
        Arrays.asList(headerConfig1, headerConfig2, headerConfig3));
    assertThat(webhookEventHeaderData.isDataFound()).isTrue();
    assertThat(webhookEventHeaderData.getSourceKey()).isEqualTo("X-GitHub-Event");
    assertThat(webhookEventHeaderData.getSourceKeyVal().get(0)).isEqualTo("Pull Request");

    headerConfig3.setKey("X-Gitlab-Event");
    headerConfig3.setValues(Arrays.asList("Merge Request Hook"));
    webhookEventHeaderData = webhookEventPayloadParser.obtainWebhookSourceKeyData(
        Arrays.asList(headerConfig1, headerConfig2, headerConfig3));
    assertThat(webhookEventHeaderData.isDataFound()).isTrue();
    assertThat(webhookEventHeaderData.getSourceKey()).isEqualTo("X-Gitlab-Event");
    assertThat(webhookEventHeaderData.getSourceKeyVal().get(0)).isEqualTo("Merge Request Hook");

    headerConfig3.setKey("X-Event-Key");
    headerConfig3.setValues(Arrays.asList("Merge:open"));
    webhookEventHeaderData = webhookEventPayloadParser.obtainWebhookSourceKeyData(
        Arrays.asList(headerConfig1, headerConfig2, headerConfig3));
    assertThat(webhookEventHeaderData.isDataFound()).isTrue();
    assertThat(webhookEventHeaderData.getSourceKey()).isEqualTo("X-Event-Key");
    assertThat(webhookEventHeaderData.getSourceKeyVal().get(0)).isEqualTo("Merge:open");

    webhookEventHeaderData =
        webhookEventPayloadParser.obtainWebhookSourceKeyData(Arrays.asList(headerConfig1, headerConfig2));
    assertThat(webhookEventHeaderData.isDataFound()).isFalse();
  }
}
