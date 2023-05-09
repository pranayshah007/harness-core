/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.VINICIUS;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.NGProcessWebhookResponseDTO;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventBuilder;
import io.harness.ngtriggers.helpers.UrlHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.validations.TriggerWebhookValidator;
import io.harness.rule.Owner;
import io.harness.webhook.WebhookConfigProvider;

import java.net.URI;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class NGTriggerWebhookResourceImplTest extends CategoryTest {
  NGTriggerWebhookConfigResourceImpl ngTriggerWebhookConfigResource;
  @Mock NGTriggerService ngTriggerService;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock WebhookConfigProvider webhookConfigProvider;
  TriggerWebhookValidator triggerWebhookValidator;
  @InjectMocks private UrlHelper urlHelper = spy(UrlHelper.class);
  private final String accountIdentifier = "account";
  private final String orgIdentifier = "org";
  private final String projectIdentifier = "project";
  private final String pipelineIdentifier = "pipeline";
  private final String triggerIdentifier = "trigger";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    triggerWebhookValidator = spy(new TriggerWebhookValidator(ngTriggerService));
    ngTriggerWebhookConfigResource = new NGTriggerWebhookConfigResourceImpl(
        ngTriggerService, ngTriggerElementMapper, triggerWebhookValidator, urlHelper);
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

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testProcessCustomWebhookV2WithNoTriggers() {
    HttpHeaders headers = mock(HttpHeaders.class);
    UriInfo uriInfo = mock(UriInfo.class);
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
                           -> ngTriggerWebhookConfigResource.processWebhookEventV2(accountIdentifier, orgIdentifier,
                               projectIdentifier, pipelineIdentifier, triggerIdentifier, "payload", headers))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testProcessWebhookEvent() {
    HttpHeaders headers = mock(HttpHeaders.class);
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("base_url/"));
    when(headers.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>());
    doReturn("base_ui_url/").when(urlHelper).getBaseUrl(any());
    String executionUuid = "executionUuid";
    TriggerWebhookEventBuilder triggerWebhookEventBuilder = TriggerWebhookEvent.builder()
                                                                .accountId(accountIdentifier)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .pipelineIdentifier(pipelineIdentifier)
                                                                .triggerIdentifier(triggerIdentifier)
                                                                .uuid(executionUuid);
    TriggerWebhookEvent eventEntity = triggerWebhookEventBuilder.build();
    when(ngTriggerElementMapper.toNGTriggerWebhookEventForCustom(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(triggerWebhookEventBuilder);
    doNothing().when(triggerWebhookValidator).applyValidationsForCustomWebhook(any(), any());
    when(ngTriggerService.addEventToQueue(any())).thenReturn(eventEntity);
    ResponseDTO<String> response = ngTriggerWebhookConfigResource.processWebhookEvent(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, triggerIdentifier, "payload", headers);
    assertThat(response.getData()).isEqualTo(executionUuid);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testProcessWebhookEventV2() {
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>());
    doReturn("base_ui_url/").when(urlHelper).getBaseUrl(any());
    String executionUuid = "executionUuid";
    TriggerWebhookEventBuilder triggerWebhookEventBuilder = TriggerWebhookEvent.builder()
                                                                .accountId(accountIdentifier)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .pipelineIdentifier(pipelineIdentifier)
                                                                .triggerIdentifier(triggerIdentifier)
                                                                .uuid(executionUuid);
    TriggerWebhookEvent eventEntity = triggerWebhookEventBuilder.build();
    when(ngTriggerElementMapper.toNGTriggerWebhookEventForCustom(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(triggerWebhookEventBuilder);
    doNothing().when(triggerWebhookValidator).applyValidationsForCustomWebhook(any(), any());
    when(ngTriggerService.addEventToQueue(any())).thenReturn(eventEntity);
    ResponseDTO<NGProcessWebhookResponseDTO> response = ngTriggerWebhookConfigResource.processWebhookEventV2(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, triggerIdentifier, "payload", headers);
    assertThat(response.getData().getEventCorrelationId()).isEqualTo(executionUuid);
    String expectedApiUrl = format("%sgateway/pipeline/api/webhook/triggerExecutionDetails/%s?accountIdentifier=%s",
        "base_ui_url/", executionUuid, accountIdentifier);
    assertThat(response.getData().getApiUrl()).isEqualTo(expectedApiUrl);
    String expectedUiUrl = format("%sng/#/account/%s/cd/orgs/%s/projects/%s/deployments?pipelineIdentifier=%s&page=0",
        "base_ui_url/", accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
    assertThat(response.getData().getUiUrl()).isEqualTo(expectedUiUrl);
    String expectedUiSetupUrl = format("%sng/#/account/%s/cd/orgs/%s/projects/%s/pipelines/%s/pipeline-studio/",
        "base_ui_url/", accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
    assertThat(response.getData().getUiSetupUrl()).isEqualTo(expectedUiSetupUrl);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testProcessWebhookEventV3() {
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>());
    doReturn("base_ui_url/").when(urlHelper).getBaseUrl(any());
    String executionUuid = "executionUuid";
    String webhookToken = "webhookToken";
    TriggerWebhookEventBuilder triggerWebhookEventBuilder = TriggerWebhookEvent.builder()
                                                                .accountId(accountIdentifier)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .pipelineIdentifier(pipelineIdentifier)
                                                                .triggerIdentifier(triggerIdentifier)
                                                                .uuid(executionUuid);
    TriggerWebhookEvent eventEntity = triggerWebhookEventBuilder.build();
    when(ngTriggerElementMapper.toNGTriggerWebhookEventForCustom(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(triggerWebhookEventBuilder);
    doNothing().when(triggerWebhookValidator).applyValidationsForCustomWebhook(any(), any());
    when(ngTriggerService.addEventToQueue(any())).thenReturn(eventEntity);
    ResponseDTO<NGProcessWebhookResponseDTO> response =
        ngTriggerWebhookConfigResource.processWebhookEventV3(webhookToken, accountIdentifier, orgIdentifier,
            projectIdentifier, pipelineIdentifier, triggerIdentifier, "payload", headers);
    assertThat(response.getData().getEventCorrelationId()).isEqualTo(executionUuid);
    String expectedApiUrl = format("%sgateway/pipeline/api/webhook/triggerExecutionDetails/%s?accountIdentifier=%s",
        "base_ui_url/", executionUuid, accountIdentifier);
    assertThat(response.getData().getApiUrl()).isEqualTo(expectedApiUrl);
    String expectedUiUrl = format("%sng/#/account/%s/cd/orgs/%s/projects/%s/deployments?pipelineIdentifier=%s&page=0",
        "base_ui_url/", accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
    assertThat(response.getData().getUiUrl()).isEqualTo(expectedUiUrl);
    String expectedUiSetupUrl = format("%sng/#/account/%s/cd/orgs/%s/projects/%s/pipelines/%s/pipeline-studio/",
        "base_ui_url/", accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
    assertThat(response.getData().getUiSetupUrl()).isEqualTo(expectedUiSetupUrl);
  }
}
