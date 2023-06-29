/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.resource;
import static io.harness.constants.Constants.UNRECOGNIZED_WEBHOOK;
import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.resources.NgWebhookResource;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.rule.Owner;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NgWebhookResourceTest extends CategoryTest {
  @InjectMocks NgWebhookResource ngWebhookResource;
  @Mock ContainerRequest containerRequest;
  @Mock WebhookHelper webhookHelper;
  @Mock WebhookService webhookService;
  String accountId = "accountId";
  String eventPayload = "eventPayload";
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testProcessWebhookEvent() {
    MultivaluedMap<String, String> httpHeaders = new MultivaluedHashMap<>();
    httpHeaders.add("key", "value");
    when(containerRequest.getRequestHeaders()).thenReturn(httpHeaders);
    when(webhookHelper.toNGTriggerWebhookEvent(accountId, eventPayload, httpHeaders)).thenReturn(null);
    assertThat(ngWebhookResource.processWebhookEvent(accountId, eventPayload, containerRequest).getData())
        .isEqualTo(UNRECOGNIZED_WEBHOOK);

    WebhookEvent eventEntity = WebhookEvent.builder().build();
    WebhookEvent newEvent = WebhookEvent.builder().uuid("uuid").build();
    when(webhookHelper.toNGTriggerWebhookEvent(accountId, eventPayload, httpHeaders)).thenReturn(eventEntity);
    when(webhookService.addEventToQueue(eventEntity)).thenReturn(newEvent);
    assertThat(ngWebhookResource.processWebhookEvent(accountId, eventPayload, containerRequest).getData())
        .isEqualTo("uuid");
  }
}
