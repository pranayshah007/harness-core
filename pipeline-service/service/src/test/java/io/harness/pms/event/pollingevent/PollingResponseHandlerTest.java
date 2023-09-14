/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.pollingevent;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.pms.triggers.build.eventmapper.BuildTriggerEventMapper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.Metadata;
import io.harness.polling.contracts.PollingResponse;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PollingResponseHandlerTest extends CategoryTest {
  @Mock private BuildTriggerEventMapper mapper;
  @Mock private TriggerEventExecutionHelper triggerEventExecutionHelper;
  @Mock private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Inject @InjectMocks PollingResponseHandler handler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testHandleEvent() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value2");

    PollingResponse pollingResponse =
        PollingResponse.newBuilder()
            .setBuildInfo(
                BuildInfo.newBuilder()
                    .addAllVersions(Collections.singletonList("release.1234"))
                    .addAllMetadata(Collections.singletonList(Metadata.newBuilder().putAllMetadata(metadata).build()))
                    .build())
            .build();

    WebhookEventMappingResponse mappingResponse = WebhookEventMappingResponse.builder()
                                                      .webhookEventResponse(TriggerEventResponse.builder().build())
                                                      .failedToFindTrigger(false)
                                                      .build();

    doReturn(mappingResponse).when(mapper).consumeBuildTriggerEvent(pollingResponse);

    handler.handleEvent(pollingResponse, new HashMap<>(), System.currentTimeMillis(), System.currentTimeMillis());
    verify(triggerEventExecutionHelper).processTriggersForActivation(any(), any());
  }
}
