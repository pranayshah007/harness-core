/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.BaseUrls;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.rule.Owner;
import io.harness.security.ServiceTokenGenerator;
import io.harness.service.ScmClient;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HarnessSCMWebhookServiceImplTest extends CategoryTest {
  @Mock private BaseUrls baseUrls;
  @Mock private ScmClient scmClient;
  @Mock private ServiceTokenGenerator tokenGenerator;
  @InjectMocks private HarnessSCMWebhookServiceImpl webhookService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void upsertWebhook_ShouldReturnResponseDTO() {
    UpsertWebhookRequestDTO requestDTO = UpsertWebhookRequestDTO.builder().build();

    when(scmClient.upsertWebhook(any(), any())).thenReturn(CreateWebhookResponse.newBuilder().build());
    when(baseUrls.getNgManagerScmBaseUrl()).thenReturn("http://example.com/");
    when(tokenGenerator.getServiceTokenWithDuration(any(), any(), any())).thenReturn("random");

    webhookService.upsertWebhook(requestDTO);

    verify(scmClient, times(1)).upsertWebhook(any(), any());
    verify(baseUrls, times(1)).getNgManagerScmBaseUrl();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void upsertWebhook_ShouldThrowException_WhenErrorOccurs() {
    UpsertWebhookRequestDTO requestDTO = UpsertWebhookRequestDTO.builder().build();

    when(scmClient.upsertWebhook(any(), any())).thenThrow(new RuntimeException());
    when(tokenGenerator.getServiceTokenWithDuration(any(), any(), any())).thenReturn("random");
    assertThatThrownBy(() -> webhookService.upsertWebhook(requestDTO));
  }
}
