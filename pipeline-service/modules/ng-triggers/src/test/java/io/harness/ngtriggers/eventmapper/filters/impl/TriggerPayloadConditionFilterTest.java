/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.IN;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PRWebhookEvent;
import io.harness.beans.WebhookBaseAttributes;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.NgTriggersTestHelper;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.AwsCodeCommitSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event.AwsCodeCommitPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event.AwsCodeCommitTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class TriggerPayloadConditionFilterTest extends CategoryTest {
  @Mock private NGTriggerService ngTriggerService;
  @Mock private NGTriggerElementMapper ngTriggerElementMapper;
  @Inject @InjectMocks private PayloadConditionsTriggerFilter filter;
  private static List<NGTriggerEntity> triggerEntities;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void applyFilterTest() {
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder()
            .webhookEvent(PRWebhookEvent.builder()
                              .baseAttributes(WebhookBaseAttributes.builder().source("test").target("main").build())
                              .build())
            .originalEvent(TriggerWebhookEvent.builder().accountId("acc").createdAt(0l).build())
            .parseWebhookResponse(
                ParseWebhookResponse.newBuilder()
                    .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(1).build()).build())
                    .build())
            .build();

    NGTriggerEntity triggerEntityGithub1 =
        NGTriggerEntity.builder()
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("GITHUB").build()).build())
            .yaml("yaml")
            .enabled(true)
            .build();

    NGTriggerConfigV2 ngTriggerConfig1 =
        NGTriggerConfigV2.builder()
            .source(
                NGTriggerSourceV2.builder()
                    .type(WEBHOOK)
                    .spec(WebhookTriggerConfigV2.builder()
                              .type(WebhookTriggerType.GITHUB)
                              .spec(GithubSpec.builder()
                                        .type(GithubTriggerEvent.PUSH)
                                        .spec(GithubPushSpec.builder()
                                                  .payloadConditions(Arrays.asList(TriggerEventDataCondition.builder()
                                                                                       .key("sourceBranch")
                                                                                       .operator(EQUALS)
                                                                                       .value("test")
                                                                                       .build(),
                                                      TriggerEventDataCondition.builder()
                                                          .key("targetBranch")
                                                          .operator(EQUALS)
                                                          .value("master")
                                                          .build()))
                                                  .build())
                                        .build())
                              .build())
                    .build())
            .build();

    NGTriggerConfigV2 ngTriggerConfig2 =
        NGTriggerConfigV2.builder()
            .source(
                NGTriggerSourceV2.builder()
                    .type(WEBHOOK)
                    .spec(WebhookTriggerConfigV2.builder()
                              .type(WebhookTriggerType.GITHUB)
                              .spec(GithubSpec.builder()
                                        .type(GithubTriggerEvent.PUSH)
                                        .spec(GithubPushSpec.builder()
                                                  .payloadConditions(Arrays.asList(TriggerEventDataCondition.builder()
                                                                                       .key("sourceBranch")
                                                                                       .operator(IN)
                                                                                       .value("test,uat")
                                                                                       .build(),
                                                      TriggerEventDataCondition.builder()
                                                          .key("targetBranch")
                                                          .operator(EQUALS)
                                                          .value("main")
                                                          .build()))
                                                  .build())
                                        .build())
                              .build())
                    .build())
            .build();

    // Case 1
    WebhookEventMappingResponse webhookEventMappingResponse =
        filter.applyFilter(FilterRequestData.builder()
                               .details(Arrays.asList(TriggerDetails.builder()
                                                          .ngTriggerConfigV2(ngTriggerConfig1)
                                                          .ngTriggerEntity(triggerEntityGithub1)
                                                          .build()))
                               .webhookPayloadData(webhookPayloadData)
                               .accountId("p")
                               .build());

    assertThat(webhookEventMappingResponse.getWebhookEventResponse()).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS);

    // Trigger found
    webhookEventMappingResponse =
        filter.applyFilter(FilterRequestData.builder()
                               .details(Arrays.asList(TriggerDetails.builder()
                                                          .ngTriggerConfigV2(ngTriggerConfig1)
                                                          .ngTriggerEntity(triggerEntityGithub1)
                                                          .build(),
                                   TriggerDetails.builder()
                                       .ngTriggerConfigV2(ngTriggerConfig2)
                                       .ngTriggerEntity(triggerEntityGithub1)
                                       .build()))
                               .webhookPayloadData(webhookPayloadData)
                               .accountId("p")
                               .build());

    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    assertThat(webhookEventMappingResponse.getTriggers().size()).isEqualTo(1);
    assertThat(webhookEventMappingResponse.getTriggers().get(0).getNgTriggerConfigV2()).isEqualTo(ngTriggerConfig2);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldFilterAwsCodeCommitPayload() {
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder()
            .webhookEvent(NgTriggersTestHelper.getAwsCodecommitPushWebhookEvent())
            .originalEvent(NgTriggersTestHelper.getTriggerWehookEventForAwsCodeCommitPush())
            .parseWebhookResponse(ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().build()).build())
            .build();

    NGTriggerEntity triggerEntityAwsCodeCommit =
        NGTriggerEntity.builder()
            .metadata(
                NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("AWS_CODECOMMIT").build()).build())
            .yaml("yaml")
            .enabled(true)
            .build();

    NGTriggerConfigV2 ngTriggerConfig =
        NGTriggerConfigV2.builder()
            .source(
                NGTriggerSourceV2.builder()
                    .type(WEBHOOK)
                    .spec(WebhookTriggerConfigV2.builder()
                              .type(WebhookTriggerType.AWS_CODECOMMIT)
                              .spec(AwsCodeCommitSpec.builder()
                                        .type(AwsCodeCommitTriggerEvent.PUSH)
                                        .spec(AwsCodeCommitPushSpec.builder()
                                                  .payloadConditions(Arrays.asList(TriggerEventDataCondition.builder()
                                                                                       .key("targetBranch")
                                                                                       .operator(EQUALS)
                                                                                       .value("main")
                                                                                       .build(),
                                                      TriggerEventDataCondition.builder()
                                                          .key("<+trigger.payload.Type>")
                                                          .operator(EQUALS)
                                                          .value("Notification")
                                                          .build()))
                                                  .build())
                                        .build())
                              .build())
                    .build())
            .build();

    // No payload match
    doReturn(ngTriggerConfig).doReturn(ngTriggerConfig).when(ngTriggerElementMapper).toTriggerConfigV2("yaml");

    // Case 1
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(
        FilterRequestData.builder()
            .details(
                Collections.singletonList(TriggerDetails.builder().ngTriggerEntity(triggerEntityAwsCodeCommit).build()))
            .webhookPayloadData(webhookPayloadData)
            .accountId("p")
            .build());

    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    assertThat(webhookEventMappingResponse.getTriggers().size()).isEqualTo(1);
    assertThat(webhookEventMappingResponse.getTriggers().get(0).getNgTriggerConfigV2()).isEqualTo(ngTriggerConfig);
  }
}
