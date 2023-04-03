/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.helpers;

import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.io.Resources;
import com.google.inject.name.Named;
import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskResponse;
import io.harness.execution.PlanExecution;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.pms.triggers.TriggerExecutionHelper;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.PollingResponse;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.kryo.DelegateTasksBeansKryoRegister;
import io.harness.serializer.kryo.NGCommonsKryoRegistrar;
import io.harness.serializer.kryo.YamlKryoRegistrar;
import io.harness.tasks.BinaryResponseData;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TriggerEventExecutionHelperTest extends CategoryTest {
  @Inject @InjectMocks TriggerEventExecutionHelper triggerEventExecutionHelper;
  @Mock TriggerExecutionHelper triggerExecutionHelper;
  private final String accountId = "acc";
  private final String orgId = "org";
  private final String projectId = "proj";
  private final String pipelineId = "target";
  private TriggerDetails triggerDetails;
  private PollingResponse pollingResponse;
  private NGTriggerEntity ngTriggerEntity;
  private TriggerWebhookEvent triggerWebhookEvent;
  @Mock KryoSerializer kryoSerializer;
  @Mock SecretManagerClientService ngSecretService;
  @Mock TaskExecutionUtils taskExecutionUtils;
  @Inject @InjectMocks NGTriggerElementMapper ngTriggerElementMapper;
  ClassLoader classLoader = getClass().getClassLoader();

  @Before
  public void setUp() {
    triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .sourceRepoType("CUSTOM")
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build(),
                HeaderConfig.builder().key("X-Hub-Signature-256").values(Arrays.asList("hashedPayload")).build()))
            .payload("{branch: main}")
            .build();
    MockitoAnnotations.initMocks(this);

    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId("acc")
                          .orgIdentifier("org")
                          .projectIdentifier("proj")
                          .targetIdentifier("target")
                          .identifier("trigger")
                          .type(NGTriggerType.ARTIFACT)
                          .build();

    triggerDetails = TriggerDetails.builder()
                         .ngTriggerEntity(ngTriggerEntity)
                         .ngTriggerConfigV2(NGTriggerConfigV2.builder().inputYaml("inputSetYaml").build())
                         .build();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testTriggerEventPipelineExecution() {
    PlanExecution planExecution = PlanExecution.builder().planId("planId").build();
    pollingResponse =
        PollingResponse.newBuilder().setBuildInfo(BuildInfo.newBuilder().addVersions("v1").build()).build();
    doReturn(planExecution)
        .when(triggerExecutionHelper)
        .resolveRuntimeInputAndSubmitExecutionReques(any(), any(), any());
    TriggerEventResponse triggerEventResponse =
        triggerEventExecutionHelper.triggerEventPipelineExecution(triggerDetails, pollingResponse);
    assertThat(triggerEventResponse.getAccountId()).isEqualTo(accountId);
    assertThat(triggerEventResponse.getNgTriggerType()).isEqualTo(NGTriggerType.ARTIFACT);
    assertThat(triggerEventResponse.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(triggerEventResponse.getProjectIdentifier()).isEqualTo(projectId);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testAuthenticateTriggers() throws IOException {
    String triggerYaml = Resources.toString(Objects.requireNonNull(classLoader.getResource("trigger-github-with-authentication.yaml")), StandardCharsets.UTF_8);
    NGTriggerEntity triggerEntity  = NGTriggerEntity.builder()
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .targetIdentifier("target")
            .identifier("trigger")
            .type(NGTriggerType.WEBHOOK)
            .yaml(triggerYaml)
            .encryptedWebhookSecretIdentifier("secret")
            .build();
    NGTriggerConfigV2 triggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(triggerYaml);
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerConfigV2(triggerConfigV2).ngTriggerEntity(triggerEntity).build();
    WebhookEventMappingResponse webhookEventMappingResponse = WebhookEventMappingResponse.builder().triggers(Collections.singletonList(triggerDetails)).build();
    doReturn(Collections.emptyList()).when(ngSecretService).getEncryptionDetails(any(), any());
    doReturn(SecretResponseWrapper.builder().secret(SecretDTOV2.builder().identifier("secret")
            .spec(SecretTextSpecDTO.builder().secretManagerIdentifier("secretManagerId").build()).build()).build()).when(ngSecretService).getSecret(any(), any(), any(), any());
    Set<String> delegateSelectors = new HashSet<>(Collections.singletonList("mydelegate"));
    doReturn(VaultConfigDTO.builder().delegateSelectors(delegateSelectors).build()).when(ngSecretService).getSecretManager(any(), any(), any(), any(), anyBoolean());
    KryoSerializer tmpKryoSerializer = new KryoSerializer(new HashSet<>(Arrays.asList(NGCommonsKryoRegistrar.class, DelegateTasksBeansKryoRegister.class)));
    doReturn(BinaryResponseData.builder()
            .data(
                    tmpKryoSerializer
                            .asDeflatedBytes(TriggerAuthenticationTaskResponse.builder().build()))).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(TriggerAuthenticationTaskResponse.builder()
            .triggersAuthenticationStatus(Collections.singletonList(true)).build()).when(kryoSerializer).asInflatedObject(any());
    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    triggerEventExecutionHelper.authenticateTriggersWithDelegateSelectors(triggerWebhookEvent, webhookEventMappingResponse);
    assertThat(triggerDetails.getAuthenticated()).isEqualTo(true);
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());
    verify(triggerEventExecutionHelper, times(1)).getAuthenticationTaskSelectors(any(), any());
  }
}
