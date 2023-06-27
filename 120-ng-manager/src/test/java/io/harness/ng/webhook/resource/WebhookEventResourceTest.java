/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.resource;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.resources.WebhookEventResource;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WebhookEventResourceTest extends CategoryTest {
  @InjectMocks WebhookEventResource webhookEventResource;
  @Mock WebhookEventService webhookEventService;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpsertWebhook() {
    UpsertWebhookResponseDTO upsertWebhookResponseDTO = UpsertWebhookResponseDTO.builder().status(200).build();
    when(webhookEventService.upsertWebhook(any())).thenReturn(upsertWebhookResponseDTO);
    assertThat(webhookEventResource.upsertWebhook(UpsertWebhookRequestDTO.builder().build()).getData().getStatus())
        .isEqualTo(200);
  }
}
