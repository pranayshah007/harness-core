/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.pollingevent;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.Metadata;
import io.harness.polling.contracts.PollingResponse;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
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
public class PollingEventStreamListenerTest extends CategoryTest {
  @Mock private PollingResponseHandler pollingResponseHandler;
  @InjectMocks PollingEventStreamListener pollingEventStreamListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleMessage() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ENTITY_TYPE, PIPELINE_ENTITY);
    metadata.put(ACTION, DELETE_ACTION);

    PollingResponse pollingResponse =
        PollingResponse.newBuilder()
            .setBuildInfo(BuildInfo.newBuilder()
                              .addAllVersions(Collections.singletonList("release.1234"))
                              .addAllMetadata(Collections.singletonList(
                                  Metadata.newBuilder().putAllMetadata(ImmutableMap.of("k", "v")).build()))
                              .build())
            .build();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(metadata)
                                          .setData(pollingResponse.toByteString())
                                          .build())
                          .build();

    pollingEventStreamListener.handleMessage(message, System.currentTimeMillis());

    verify(pollingResponseHandler).handleEvent(eq(pollingResponse), eq(metadata), anyLong(), anyLong());
  }
}
