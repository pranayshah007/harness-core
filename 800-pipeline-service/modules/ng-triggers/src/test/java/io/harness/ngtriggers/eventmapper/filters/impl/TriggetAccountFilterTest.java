/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO;
import static io.harness.rule.OwnerRule.ADWAIT;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.service.NGTriggerService;
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
import org.powermock.api.mockito.PowerMockito;

@OwnedBy(PIPELINE)
public class TriggetAccountFilterTest extends CategoryTest {
  @Mock private NGTriggerService ngTriggerService;
  @Inject @InjectMocks AccountTriggerFilter accountTriggerFilter;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void applyAccountFilerTestTest() {
    NGTriggerEntity t1 = NGTriggerEntity.builder().identifier("T1").build();
    NGTriggerEntity t2 = NGTriggerEntity.builder().identifier("T2").build();

    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId("acc")
                                                  .orgIdentifier(null)
                                                  .projectIdentifier("null")
                                                  .sourceRepoType("GITHUB")
                                                  .createdAt(0l)
                                                  .nextIteration(0l)
                                                  .build();

    PowerMockito.doReturn(null)
        .doReturn(Arrays.asList(t1, t2))
        .when(ngTriggerService)
        .findTriggersForWehbookBySourceRepoType(triggerWebhookEvent, false, true);

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("acc")
            .webhookPayloadData(WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build())
            .build();

    WebhookEventMappingResponse webhookEventMappingResponse = accountTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO);
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getMessage())
        .isEqualTo("No enabled trigger found for Account:acc, SourceRepoType: GITHUB");

    webhookEventMappingResponse = accountTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    List<TriggerDetails> triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(2);
    List<NGTriggerEntity> entities =
        triggerDetails.stream().map(triggerDetails1 -> triggerDetails1.getNgTriggerEntity()).collect(toList());
    assertThat(entities).containsExactlyInAnyOrder(t1, t2);
  }
}
