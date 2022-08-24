/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventBuilder;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.validations.TriggerWebhookValidator;
import io.harness.rule.Owner;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class NGTriggerWebhookResourceImplTest extends CategoryTest {
  NGTriggerWebhookConfigResourceImpl ngTriggerWebhookConfigResource;
  @Mock NGTriggerService ngTriggerService;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  TriggerWebhookValidator triggerWebhookValidator;

  private final String accountIdentifier = "account";
  private final String orgIdentifier = "org";
  private final String projectIdentifier = "project";
  private final String pipelineIdentifier = "pipeline";
  private final String triggerIdentifier = "trigger";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    triggerWebhookValidator = new TriggerWebhookValidator(ngTriggerService);
    ngTriggerWebhookConfigResource =
        new NGTriggerWebhookConfigResourceImpl(ngTriggerService, ngTriggerElementMapper, triggerWebhookValidator);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testProcessCustomWebhookWithNoTriggers() {
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>());
    TriggerWebhookEventBuilder triggerWebhookEventBuilder = TriggerWebhookEvent.builder()
                                                                .accountId(accountIdentifier)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .pipelineIdentifier(pipelineIdentifier)
                                                                .triggerIdentifier(triggerIdentifier);
    when(ngTriggerElementMapper.toNGTriggerWebhookEventForCustom(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(triggerWebhookEventBuilder);
    assertThatThrownBy(()
                           -> ngTriggerWebhookConfigResource.processWebhookEvent(accountIdentifier, orgIdentifier,
                               projectIdentifier, pipelineIdentifier, triggerIdentifier, "payload", headers))
        .isInstanceOf(InvalidRequestException.class);
  }
}
