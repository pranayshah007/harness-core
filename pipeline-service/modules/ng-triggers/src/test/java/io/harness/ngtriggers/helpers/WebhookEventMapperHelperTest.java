/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.CUSTOM;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.eventmapper.impl.CustomWebhookEventToTriggerMapper;
import io.harness.ngtriggers.eventmapper.impl.GitWebhookEventToTriggerMapper;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WebhookEventMapperHelperTest extends CategoryTest {
  @Mock private GitWebhookEventToTriggerMapper gitWebhookEventToTriggerMapper;
  @Mock private CustomWebhookEventToTriggerMapper customWebhookEventToTriggerMapper;
  @InjectMocks private WebhookEventMapperHelper webhookEventMapperHelper;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGitWebhook() {
    TriggerMappingRequestData triggerMappingRequestData =
        TriggerMappingRequestData.builder()
            .triggerWebhookEvent(TriggerWebhookEvent.builder().sourceRepoType(null).build())
            .build();
    doReturn(null).when(gitWebhookEventToTriggerMapper).mapWebhookEventToTriggers(any());
    webhookEventMapperHelper.mapWebhookEventToTriggers(triggerMappingRequestData);
    verify(gitWebhookEventToTriggerMapper, times(1)).mapWebhookEventToTriggers(any());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCustomWebhook() {
    TriggerMappingRequestData triggerMappingRequestData =
        TriggerMappingRequestData.builder()
            .triggerWebhookEvent(TriggerWebhookEvent.builder().sourceRepoType(CUSTOM.name()).build())
            .build();
    doReturn(null).when(customWebhookEventToTriggerMapper).mapWebhookEventToTriggers(any());
    webhookEventMapperHelper.mapWebhookEventToTriggers(triggerMappingRequestData);
    verify(customWebhookEventToTriggerMapper, times(1)).mapWebhookEventToTriggers(any());
  }
}
