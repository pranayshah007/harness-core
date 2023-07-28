/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.rule.OwnerRule.MEET;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.eventmapper.filters.impl.buildtrigger.BuildTriggerSignatureFilter;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.polling.contracts.PollingResponse;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BuildTriggerSignatureFilterTest {
  @Mock private BuildTriggerHelper buildTriggerHelper;
  @Mock private NGTriggerService ngTriggerService;
  @Mock private NGTriggerElementMapper ngTriggerElementMapper;

  @InjectMocks private BuildTriggerSignatureFilter filter;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testApplyFilter_NoMatchingTrigger() {
    // Prepare test data
    FilterRequestData requestData =
        FilterRequestData.builder()
            .pollingResponse(PollingResponse.newBuilder().addSignatures("sign").build())
            .webhookPayloadData(
                WebhookPayloadData.builder().originalEvent(TriggerWebhookEvent.builder().createdAt(1L).build()).build())
            .build();
    WebhookEventMappingResponse expectedResponse =
        WebhookEventMappingResponse.builder()
            .webhookEventResponse(
                TriggerEventResponse.builder()
                    .exceptionOccurred(true)
                    .createdAt(1L)
                    .message("No trigger signature matched with AccountId nad Signature from Event: null")
                    .finalStatus(TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_FOR_EVENT_SIGNATURES)
                    .build())
            .build();

    // Mock the behavior of ngTriggerService.findBuildTriggersByAccountIdAndSignature
    when(ngTriggerService.findBuildTriggersByAccountIdAndSignature(any(), anyList())).thenReturn(new ArrayList<>());

    // Perform the test
    WebhookEventMappingResponse response = filter.applyFilter(requestData);

    // Verify the result
    assertEquals(expectedResponse, response);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testApplyFilter_MatchingTrigger() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder().build();
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();

    // Prepare test data
    FilterRequestData requestData = FilterRequestData.builder()
                                        .accountId("accountId")
                                        .pollingResponse(PollingResponse.newBuilder().addSignatures("sign").build())
                                        .build();
    WebhookEventMappingResponse expectedResponse =
        WebhookEventMappingResponse.builder().failedToFindTrigger(false).trigger(triggerDetails).build();

    List<NGTriggerEntity> matchingTriggers = new ArrayList<>();
    matchingTriggers.add(ngTriggerEntity);
    List<TriggerDetails> expectedTriggerDetails = new ArrayList<>();
    expectedTriggerDetails.add(triggerDetails);

    // Mock the behavior of ngTriggerService.findBuildTriggersByAccountIdAndSignature
    when(ngTriggerService.findBuildTriggersByAccountIdAndSignature(any(), anyList())).thenReturn(matchingTriggers);

    // Mock the behavior of ngTriggerElementMapper.toTriggerDetails
    when(ngTriggerElementMapper.toTriggerDetails(any())).thenReturn(triggerDetails);

    // Perform the test
    WebhookEventMappingResponse response = filter.applyFilter(requestData);

    // Verify the result
    assertEquals(expectedResponse, response);
  }

  // Add more test methods as needed
}
