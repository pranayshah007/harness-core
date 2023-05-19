/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.ngperpetualtask.service;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.K8sEventCollectionBundle;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CE)
public class K8sWatchTaskServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w";
  private static final String TASK_ID = "1xz66buyQ5KYoeeHthDRJw";

  @Mock private KryoSerializer kryoSerializer;
  @Mock private PerpetualTaskService perpetualTaskService;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock(answer = RETURNS_DEEP_STUBS) private ConnectorResourceClient connectorResourceClient;
  @Mock private Call call;

  @InjectMocks private K8sWatchTaskServiceImpl k8sWatchTaskService;

  K8sEventCollectionBundle k8sEventCollectionBundle = K8sEventCollectionBundle.builder()
                                                          .cloudProviderId("cloudProviderId")
                                                          .clusterId("clusterId")
                                                          .clusterName("clusterName")
                                                          .connectorIdentifier("connectorIdentifier")
                                                          .orgIdentifier("orgIdentifier")
                                                          .projectIdentifier("projectIdentifier")
                                                          .build();

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void create() throws Exception {
    when(perpetualTaskService.createTask(
             eq(PerpetualTaskType.K8S_WATCH), eq(ACCOUNT_ID), any(), any(), anyBoolean(), any()))
        .thenReturn(TASK_ID);

    mockCreateExecutionBundle();

    assertThat(k8sWatchTaskService.create(ACCOUNT_ID, k8sEventCollectionBundle)).isEqualTo(TASK_ID);

    verify(call, times(2)).execute();
    verify(perpetualTaskService, times(1)).createTask(any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void resetTask() throws Exception {
    when(perpetualTaskService.resetTask(eq(ACCOUNT_ID), eq(TASK_ID), any())).thenReturn(true);

    mockCreateExecutionBundle();

    assertThat(k8sWatchTaskService.resetTask(ACCOUNT_ID, TASK_ID, k8sEventCollectionBundle)).isEqualTo(true);

    verify(call, times(2)).execute();
    verify(perpetualTaskService, times(1)).resetTask(any(), any(), any());
  }

  private void mockCreateExecutionBundle() throws IOException {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {'a'});
    when(kryoSerializer.asDeflatedBytes(any())).thenReturn(new byte[] {'a'});

    CEKubernetesClusterConfigDTO ceKubernetesClusterConfigDTO =
        CEKubernetesClusterConfigDTO.builder().connectorRef("K8s_ce").build();
    when(call.execute())
        .thenReturn(createResponse(ceKubernetesClusterConfigDTO))
        .thenReturn(createManualK8sConfigResponse());

    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(Collections.emptyList());

    when(connectorResourceClient.get(any(), any(), any(), any())).thenReturn(call);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void delete() throws Exception {
    when(perpetualTaskService.deleteTask(eq(ACCOUNT_ID), eq(TASK_ID))).thenReturn(true);

    assertThat(k8sWatchTaskService.delete(ACCOUNT_ID, TASK_ID)).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void getStatus() throws Exception {
    when(perpetualTaskService.getTaskRecord(eq(TASK_ID)))
        .thenReturn(PerpetualTaskRecord.builder()
                        .accountId(ACCOUNT_ID)
                        .uuid(TASK_ID)
                        .perpetualTaskType(PerpetualTaskType.K8S_WATCH)
                        .state(PerpetualTaskState.TASK_UNASSIGNED)
                        .build());

    PerpetualTaskRecord pTaskRecord = k8sWatchTaskService.getStatus(TASK_ID);

    assertThat(pTaskRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(pTaskRecord.getUuid()).isEqualTo(TASK_ID);
    assertThat(pTaskRecord.getPerpetualTaskType()).isEqualTo(PerpetualTaskType.K8S_WATCH);
    assertThat(pTaskRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
  }

  private Response<ResponseDTO<?>> createManualK8sConfigResponse() {
    KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(KubernetesCredentialType.MANUAL_CREDENTIALS)
                            .config(KubernetesClusterDetailsDTO.builder()
                                        .masterUrl("http://localhost:8080")
                                        .auth(KubernetesAuthDTO.builder()
                                                  .authType(KubernetesAuthType.SERVICE_ACCOUNT)
                                                  .credentials(KubernetesServiceAccountDTO.builder()
                                                                   .serviceAccountTokenRef(
                                                                       SecretRefData.builder()
                                                                           .identifier("id0")
                                                                           .decryptedValue(new char[] {'j', 'k'})
                                                                           .scope(Scope.ACCOUNT)
                                                                           .build())
                                                                   .build())
                                                  .build())
                                        .build())
                            .build())
            .build();

    return createResponse(kubernetesClusterConfigDTO);
  }

  private Response<ResponseDTO<?>> createResponse(ConnectorConfigDTO connectorConfigDTO) {
    return Response.success(ResponseDTO.newResponse(
        Optional.of(ConnectorDTO.builder()
                        .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).build())
                        .build())));
  }
}
