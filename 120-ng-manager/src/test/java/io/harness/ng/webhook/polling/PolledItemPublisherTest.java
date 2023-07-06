/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook.polling;

import static io.harness.eventsframework.EventsFrameworkConstants.POLLING_EVENTS_STREAM;
import static io.harness.rule.OwnerRule.MEET;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.webhook.resources.NgWebhookResource;
import io.harness.polling.contracts.PollingResponse;
import io.harness.rule.Owner;

import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PolledItemPublisherTest extends CategoryTest {
  @InjectMocks PolledItemPublisher polledItemPublisher;
  @Mock @Named(POLLING_EVENTS_STREAM) private Producer eventProducer;
  @Mock NgWebhookResource ngWebhookResource;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testPublishPolledItems() {
    when(eventProducer.send(any())).thenReturn("messageId");
    PollingResponse pollingResponse = PollingResponse.newBuilder().setPollingDocId("pollingId").build();
    polledItemPublisher.publishPolledItems(pollingResponse);
    verify(eventProducer, times(1)).send(any());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testSendWebhookRequest() {
    MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
    List<GitPollingWebhookData> redeliveries =
        Collections.singletonList(GitPollingWebhookData.builder().payload("payload").headers(headers).build());
    when(ngWebhookResource.processWebhook("account", "payload", headers)).thenReturn(ResponseDTO.newResponse(""));
    polledItemPublisher.sendWebhookRequest("account", redeliveries);
    verify(ngWebhookResource, times(1)).processWebhook("account", "payload", headers);
  }
}
