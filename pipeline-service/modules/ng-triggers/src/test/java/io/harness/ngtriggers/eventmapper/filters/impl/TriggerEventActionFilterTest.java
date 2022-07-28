/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_EVENT_ACTION;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.rule.OwnerRule.ADWAIT;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PRWebhookEvent;
import io.harness.category.element.UnitTests;
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
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class TriggerEventActionFilterTest extends CategoryTest {
  @Mock private NGTriggerService ngTriggerService;
  @Mock private NGTriggerElementMapper ngTriggerElementMapper;
  @Inject @InjectMocks private EventActionTriggerFilter filter;
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
            .webhookEvent(PRWebhookEvent.builder().build())
            .originalEvent(TriggerWebhookEvent.builder().accountId("acc").createdAt(0l).build())
            .parseWebhookResponse(
                ParseWebhookResponse.newBuilder()
                    .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(1).build()).build())
                    .build())
            .build();

    NGTriggerEntity triggerEntityGithub =
        NGTriggerEntity.builder()
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("GITHUB").build()).build())
            .yaml("yaml")
            .enabled(true)
            .build();

    GithubSpec githubPushSpec =
        GithubSpec.builder().type(GithubTriggerEvent.PUSH).spec(GithubPushSpec.builder().build()).build();
    WebhookTriggerConfigV2 webhookTriggerConfigV1 =
        WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).spec(githubPushSpec).build();
    NGTriggerSourceV2 ngTriggerSourceV2 =
        NGTriggerSourceV2.builder().type(WEBHOOK).spec(webhookTriggerConfigV1).build();
    NGTriggerConfigV2 ngTriggerConfigV2 = NGTriggerConfigV2.builder().source(ngTriggerSourceV2).build();

    // No payload match
    doReturn(ngTriggerConfigV2).when(ngTriggerElementMapper).toTriggerConfigV2(triggerEntityGithub);
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(
        FilterRequestData.builder()
            .details(Arrays.asList(TriggerDetails.builder().ngTriggerEntity(triggerEntityGithub).build()))
            .webhookPayloadData(webhookPayloadData)
            .accountId("p")
            .build());

    assertThat(webhookEventMappingResponse.getWebhookEventResponse()).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(NO_MATCHING_TRIGGER_FOR_EVENT_ACTION);

    // Trigger found
    webhookTriggerConfigV1.setSpec(GithubSpec.builder()
                                       .type(GithubTriggerEvent.PULL_REQUEST)
                                       .spec(GithubPRSpec.builder().actions(emptyList()).build())
                                       .build());
    webhookEventMappingResponse = filter.applyFilter(
        FilterRequestData.builder()
            .details(Arrays.asList(TriggerDetails.builder().ngTriggerEntity(triggerEntityGithub).build()))
            .accountId("p")
            .webhookPayloadData(webhookPayloadData)
            .build());

    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    assertThat(webhookEventMappingResponse.getTriggers().size()).isEqualTo(1);
    assertThat(webhookEventMappingResponse.getTriggers().get(0).getNgTriggerEntity()).isEqualTo(triggerEntityGithub);
  }
}
