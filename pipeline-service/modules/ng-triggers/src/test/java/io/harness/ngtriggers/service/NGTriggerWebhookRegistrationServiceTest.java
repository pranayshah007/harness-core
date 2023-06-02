/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatusData;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.impl.NGTriggerWebhookRegistrationServiceImpl;
import io.harness.rule.Owner;
import io.harness.utils.ConnectorUtils;
import io.harness.webhook.remote.WebhookEventClient;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class NGTriggerWebhookRegistrationServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock WebhookEventClient webhookEventClient;
  @InjectMocks NGTriggerWebhookRegistrationServiceImpl ngTriggerWebhookRegistrationService;
  @Mock ConnectorUtils connectorUtils;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;

  @Before
  public void setUp() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).build();
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    when(connectorUtils.retrieveURL(connectorDetails)).thenReturn("https://github.com/wings-software/");
    when(connectorUtils.getConnectionType(connectorDetails)).thenReturn(GitConnectionType.ACCOUNT);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldRegisterWebhook() throws IOException {
    Call<ResponseDTO<UpsertWebhookResponseDTO>> requestCall = mock(Call.class);
    when(requestCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(UpsertWebhookResponseDTO.builder().status(200).build())));
    when(webhookEventClient.upsertWebhook(any())).thenReturn(requestCall);
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .projectIdentifier("proj")
            .orgIdentifier("org")
            .accountId("acct")
            .enabled(true)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .git(GitMetadata.builder().connectorIdentifier("conn").repoName("repo").build())
                                       .build())
                          .build())
            .build();
    WebhookRegistrationStatusData webhookRegistrationStatus =
        ngTriggerWebhookRegistrationService.registerWebhook(ngTriggerEntity);
    assertThat(webhookRegistrationStatus.getWebhookAutoRegistrationStatus().getRegistrationResult())
        .isEqualTo(WebhookRegistrationStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void shouldRegisterWebhookWithFailure() throws IOException {
    Call<ResponseDTO<UpsertWebhookResponseDTO>> requestCall = mock(Call.class);
    when(requestCall.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(UpsertWebhookResponseDTO.builder().status(401).error("UNAUTHORIZED").build())));
    when(webhookEventClient.upsertWebhook(any())).thenReturn(requestCall);
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .projectIdentifier("proj")
            .orgIdentifier("org")
            .accountId("acct")
            .enabled(true)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .git(GitMetadata.builder().connectorIdentifier("conn").repoName("repo").build())
                                       .build())
                          .build())
            .build();
    WebhookRegistrationStatusData webhookRegistrationStatus =
        ngTriggerWebhookRegistrationService.registerWebhook(ngTriggerEntity);
    assertThat(webhookRegistrationStatus.getWebhookAutoRegistrationStatus().getRegistrationResult())
        .isEqualTo(WebhookRegistrationStatus.FAILED);
    assertThat(webhookRegistrationStatus.getWebhookAutoRegistrationStatus().getDetailedMessage())
        .isEqualTo("UNAUTHORIZED");
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void shouldRegisterWebhookWithException() throws IOException {
    Call<ResponseDTO<UpsertWebhookResponseDTO>> requestCall = mock(Call.class);
    when(requestCall.execute()).thenThrow(new InvalidRequestException("Exception"));
    when(webhookEventClient.upsertWebhook(any())).thenReturn(requestCall);
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .projectIdentifier("proj")
            .orgIdentifier("org")
            .accountId("acct")
            .enabled(true)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .git(GitMetadata.builder().connectorIdentifier("conn").repoName("repo").build())
                                       .build())
                          .build())
            .build();
    WebhookRegistrationStatusData webhookRegistrationStatus =
        ngTriggerWebhookRegistrationService.registerWebhook(ngTriggerEntity);
    assertThat(webhookRegistrationStatus.getWebhookAutoRegistrationStatus().getRegistrationResult())
        .isEqualTo(WebhookRegistrationStatus.ERROR);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldRegisterWebhookAzure() throws IOException {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.AZURE_REPO).build();
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    when(connectorUtils.retrieveURL(connectorDetails)).thenReturn("https://dev.azure.com/org/project/_git/repo");
    when(connectorUtils.getConnectionType(connectorDetails)).thenReturn(GitConnectionType.PROJECT);

    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_azurerepo_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-azurerepo-pr-v2.yaml")), StandardCharsets.UTF_8);

    when(ngTriggerElementMapper.toTriggerConfigV2(any(String.class))).thenCallRealMethod();
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_azurerepo_pr);

    when(ngTriggerElementMapper.toTriggerConfigV2(any(String.class))).thenReturn(ngTriggerConfigV2);

    Call<ResponseDTO<UpsertWebhookResponseDTO>> requestCall = mock(Call.class);
    when(requestCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(UpsertWebhookResponseDTO.builder().status(200).build())));
    when(webhookEventClient.upsertWebhook(any())).thenReturn(requestCall);
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .projectIdentifier("proj")
            .orgIdentifier("org")
            .accountId("acct")
            .yaml(ngTriggerYaml_azurerepo_pr)
            .enabled(true)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .git(GitMetadata.builder().connectorIdentifier("conn").repoName("repo").build())
                                       .build())
                          .build())
            .build();
    WebhookRegistrationStatusData webhookRegistrationStatus =
        ngTriggerWebhookRegistrationService.registerWebhook(ngTriggerEntity);
    assertThat(webhookRegistrationStatus.getWebhookAutoRegistrationStatus().getRegistrationResult())
        .isEqualTo(WebhookRegistrationStatus.SUCCESS);
  }
}
