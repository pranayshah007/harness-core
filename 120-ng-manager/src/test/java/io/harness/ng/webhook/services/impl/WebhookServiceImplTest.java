/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.constants.Constants.X_GIT_HUB_EVENT;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_PUSH_EVENT;
import static io.harness.rule.OwnerRule.HARI;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.scm.GitWebhookTaskType;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.webhookpayloads.webhookdata.GitDetails;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.runtime.SCMRuntimeException;
import io.harness.gitsync.common.service.ScmClientFacilitatorService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.WebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;
import io.harness.rule.Owner;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class WebhookServiceImplTest extends CategoryTest {
  @InjectMocks @Spy DefaultWebhookServiceImpl webhookService;
  @Mock AccountOrgProjectHelper accountOrgProjectHelper;
  @Mock WebhookEventRepository webhookEventRepository;
  @Mock ConnectorService connectorService;
  @Mock ScmClientFacilitatorService scmClientFacilitatorService;
  @Mock ScmOrchestratorService scmOrchestratorService;
  @Mock NextGenConfiguration nextGenConfiguration;
  @Mock WebhookHelper webhookHelper;
  @Mock HsqsClientService hsqsClientService;

  @InjectMocks WebhookServiceImpl webhookServiceImpl;
  private String accountId = "accountId";
  private String orgId = "orgId";
  private String projectId = "projectId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARI)
  @Category(UnitTests.class)
  public void getTargetUrlTest() throws MalformedURLException, IllegalAccessException {
    doReturn("https://app.harness.io/gateway/ng/api/").when(webhookService).getWebhookBaseUrl();
    doReturn(null).when(accountOrgProjectHelper).getVanityUrl("abcde");
    final String targetUrl = webhookService.getTargetUrl("abcde");
    assertThat(targetUrl).isEqualTo("https://app.harness.io/gateway/ng/api/webhook?accountIdentifier=abcde");
    doReturn("https://app.harness.io/gateway/ng/api").when(webhookService).getWebhookBaseUrl();
    doReturn("https://vanity.harness.io/").when(accountOrgProjectHelper).getVanityUrl("abcde");
    final String targetUrl2 = webhookService.getTargetUrl("abcde");
    assertThat(targetUrl2).isEqualTo("https://vanity.harness.io/ng/api/webhook?accountIdentifier=abcde");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testAddEventToQueue() {
    WebhookEvent webhookEvent = WebhookEvent.builder().accountId("acc").build();
    when(webhookEventRepository.save(webhookEvent)).thenReturn(webhookEvent);
    assertThat(webhookServiceImpl.addEventToQueue(webhookEvent)).isEqualTo(webhookEvent);

    when(webhookEventRepository.save(webhookEvent)).thenThrow(new InvalidRequestException("message"));
    assertThatThrownBy(() -> webhookServiceImpl.addEventToQueue(webhookEvent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Webhook event could not be saved for processing");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpsertWebhook() {
    doReturn("https://app.harness.io/gateway/ng/api/").when(webhookService).getWebhookBaseUrl();
    doReturn(null).when(accountOrgProjectHelper).getVanityUrl("abcde");

    UpsertWebhookRequestDTO upsertWebhookRequestDTO = UpsertWebhookRequestDTO.builder()
                                                          .accountIdentifier(accountId)
                                                          .projectIdentifier(projectId)
                                                          .orgIdentifier(orgId)
                                                          .connectorIdentifierRef("identifier")
                                                          .build();
    CreateWebhookResponse createWebhookResponse =
        CreateWebhookResponse.newBuilder().setWebhook(WebhookResponse.newBuilder().build()).setStatus(200).build();
    ConnectorResponseDTO connectorResponseDTO =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder().connectorConfig(GithubConnectorDTO.builder().build()).build())
            .build();
    when(connectorService.getByRef(accountId, orgId, projectId, "identifier"))
        .thenReturn(Optional.of(connectorResponseDTO));
    when(
        scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(createWebhookResponse);
    when(scmClientFacilitatorService.upsertWebhook(upsertWebhookRequestDTO,
             "https://app.harness.io/gateway/ng/api/webhook?accountIdentifier=abcde", GitWebhookTaskType.UPSERT))
        .thenReturn(createWebhookResponse);
    assertThat(webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .isEqualTo(UpsertWebhookResponseDTO.builder()
                       .status(200)
                       .error("")
                       .webhookResponse(WebhookResponse.newBuilder().build())
                       .build());

    doThrow(new ExplanationException("message", new ScmException(ErrorCode.SCM_UNAUTHORIZED)))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("The credentials provided in the Github connector identifier are invalid or have expired. message")
        .isInstanceOf(ScmUnauthorizedException.class);

    doThrow(new ExplanationException("message", new InvalidRequestException("message")))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("message")
        .isInstanceOf(ExplanationException.class);

    doThrow(new HintException("message", new HintException("message", new ScmException(ErrorCode.SCM_UNAUTHORIZED))))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("The credentials provided in the Github connector identifier are invalid or have expired. message")
        .isInstanceOf(ScmUnauthorizedException.class);

    doThrow(new HintException("message", new InvalidRequestException("message")))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("message")
        .isInstanceOf(HintException.class);

    doThrow(new ScmException(ErrorCode.SCM_UNAUTHORIZED))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("The credentials provided in the Github connector identifier are invalid or have expired. ")
        .isInstanceOf(ScmUnauthorizedException.class);

    doThrow(new SCMRuntimeException("message"))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage(
            "Unable to connect to Git Provider. Please check if credentials provided are correct and the repo url is correct.")
        .isInstanceOf(ScmBadRequestException.class);

    doThrow(new InvalidRequestException("message"))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("message")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetScmConnectorException() {
    when(connectorService.getByRef(accountId, orgId, projectId, "identifier")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> webhookService.getScmConnector(accountId, orgId, projectId, "identifier"))
        .isInstanceOf(ConnectorNotFoundException.class)
        .hasMessage("No connector found for accountIdentifier: [" + accountId + "], orgIdentifier : [" + orgId
            + "], projectIdentifier : [" + projectId + "], connectorRef : [identifier]");

    ConnectorResponseDTO connectorResponseDTO =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder().connectorConfig(DockerConnectorDTO.builder().build()).build())
            .build();
    when(connectorService.getByRef(accountId, orgId, projectId, "identifier"))
        .thenReturn(Optional.of(connectorResponseDTO));
    assertThatThrownBy(() -> webhookService.getScmConnector(accountId, orgId, projectId, "identifier"))
        .isInstanceOf(UnexpectedException.class)
        .hasMessage(
            "The connector with the  identifier [null], accountIdentifier [accountId], orgIdentifier [orgId], projectIdentifier [projectId] is not an scm connector");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGenerateWebhookDTOAndEnqueue() {
    doReturn(QueueServiceClientConfig.builder().topic("topic1").build())
        .when(nextGenConfiguration)
        .getQueueServiceClientConfig();
    WebhookEvent event = WebhookEvent.builder()
                             .accountId("accountId")
                             .uuid(generateUuid())
                             .createdAt(0L)
                             .headers(List.of(HeaderConfig.builder().key(X_GIT_HUB_EVENT).build()))
                             .build();
    doReturn(SourceRepoType.GITHUB).when(webhookHelper).getSourceRepoType(event);
    doReturn(null).when(webhookHelper).invokeScmService(event);
    WebhookDTO webhookDTO =
        WebhookDTO.newBuilder()
            .setAccountId("accountId")
            .setGitDetails(GitDetails.newBuilder()
                               .setSourceRepoType(SourceRepoType.GITHUB)
                               .setEvent(WebhookEventType.PUSH)
                               .build())
            .setParsedResponse(ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().build()).build())
            .build();
    doReturn(webhookDTO).when(webhookHelper).generateWebhookDTO(event, null, SourceRepoType.GITHUB);
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic("topic1" + WEBHOOK_EVENT)
                                        .subTopic("accountId")
                                        .producerName("topic1" + WEBHOOK_EVENT)
                                        .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                        .build();
    EnqueueRequest pushEnqueueRequest = EnqueueRequest.builder()
                                            .topic("topic1" + WEBHOOK_PUSH_EVENT)
                                            .subTopic("accountId")
                                            .producerName("topic1" + WEBHOOK_PUSH_EVENT)
                                            .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                            .build();
    doReturn(EnqueueResponse.builder().itemId("itemId").build()).when(hsqsClientService).enqueue(enqueueRequest);
    doReturn(EnqueueResponse.builder().itemId("itemId2").build()).when(hsqsClientService).enqueue(pushEnqueueRequest);
    assertThatCode(() -> webhookServiceImpl.generateWebhookDTOAndEnqueue(event)).doesNotThrowAnyException();
    verify(hsqsClientService, times(1)).enqueue(enqueueRequest);
    verify(hsqsClientService, times(1)).enqueue(pushEnqueueRequest);
  }
}
