/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.rule.OwnerRule.UTSAV;

import static software.wings.beans.TaskType.CE_VALIDATE_KUBERNETES_CONFIG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.CEKubernetesConnectionTaskParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskResponse;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesDelegateDetailsDTO;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CEKubernetesConnectionValidatorTest extends CategoryTest {
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private EncryptionHelper encryptionHelper;
  @Mock private ConnectorService connectorService;
  @InjectMocks private CEKubernetesConnectionValidator ceKubernetesConnectionValidator;

  private static final String CONNECTOR_REF = "account.connectorRef";
  private static final String ACCOUNT_ID = "accountId";
  private static final String CONNECTOR_IDENTIFIER = "connectorRef";
  private static final String DELEGATE_NAME = "delegateName";
  private CEKubernetesClusterConfigDTO ceKubernetesClusterConfigDTO;
  ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
      ArgumentCaptor.forClass(DelegateTaskRequest.class);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    ceKubernetesClusterConfigDTO = CEKubernetesClusterConfigDTO.builder()
                                       .connectorRef(CONNECTOR_REF)
                                       .featuresEnabled(Collections.singletonList(CEFeatures.VISIBILITY))
                                       .build();

    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(KubernetesConnectionTaskResponse.builder()
                        .connectorValidationResult(ConnectorValidationResult.builder()
                                                       .status(ConnectivityStatus.SUCCESS)
                                                       .testedAt(Instant.now().toEpochMilli())
                                                       .build())
                        .build());
    when(connectorService.get(any(), any(), any(), eq(CONNECTOR_IDENTIFIER)))
        .thenReturn(Optional.of(createConnectorResponseDTO()));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testSuccess() {
    final ConnectorValidationResult connectorValidationResult =
        ceKubernetesConnectionValidator.validate(ceKubernetesClusterConfigDTO, ACCOUNT_ID, null, null, null);

    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    final DelegateTaskRequest delegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    assertDelegateTaskRequest(delegateTaskRequest);

    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(connectorValidationResult.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testConnectorRefNotFound() {
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    ceKubernetesConnectionValidator.validate(ceKubernetesClusterConfigDTO, ACCOUNT_ID, null, null, null);

    verifyNoInteractions(delegateGrpcClientWrapper);
    verify(connectorService, times(1)).get(any(), any(), any(), eq(CONNECTOR_IDENTIFIER));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGenericValidationFailure() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(KubernetesConnectionTaskResponse.builder()
                        .connectorValidationResult(ConnectorValidationResult.builder()
                                                       .status(ConnectivityStatus.FAILURE)
                                                       .testedAt(Instant.now().toEpochMilli())
                                                       .errorSummary("errorSummary")
                                                       .build())
                        .build());

    final ConnectorValidationResult connectorValidationResult =
        ceKubernetesConnectionValidator.validate(ceKubernetesClusterConfigDTO, ACCOUNT_ID, null, null, null);

    verify(connectorService, times(1)).get(any(), any(), any(), eq(CONNECTOR_IDENTIFIER));
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    final DelegateTaskRequest delegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    assertDelegateTaskRequest(delegateTaskRequest);

    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(connectorValidationResult.getErrorSummary()).isEqualTo("errorSummary");
  }

  @Test(expected = InvalidIdentifierRefException.class)
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testWronglyScopedConnectorConfig() {
    final String randomScopeName = "sdbvladk";
    ceKubernetesClusterConfigDTO.setConnectorRef(randomScopeName + "." + CONNECTOR_IDENTIFIER);
    final ConnectorValidationResult connectorValidationResult =
        ceKubernetesConnectionValidator.validate(ceKubernetesClusterConfigDTO, null, null, null, null);

    verifyNoInteractions(delegateGrpcClientWrapper);
    verifyNoInteractions(connectorService);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testCorrectTaskType() {
    assertThat(ceKubernetesConnectionValidator.getTaskType()).isEqualTo(CE_VALIDATE_KUBERNETES_CONFIG.name());
  }

  private void assertDelegateTaskRequest(DelegateTaskRequest delegateTaskRequest) {
    assertThat(delegateTaskRequest).isNotNull();
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(TaskType.CE_VALIDATE_KUBERNETES_CONFIG.name());
    assertThat(delegateTaskRequest.getTaskParameters()).isExactlyInstanceOf(CEKubernetesConnectionTaskParams.class);

    final CEKubernetesConnectionTaskParams ceKubernetesConnectionTaskParams =
        (CEKubernetesConnectionTaskParams) delegateTaskRequest.getTaskParameters();

    assertThat(ceKubernetesConnectionTaskParams.getKubernetesClusterConfig()).isNotNull();
    assertThat(ceKubernetesConnectionTaskParams.getKubernetesClusterConfig().getCredential()).isNotNull();
    assertThat(ceKubernetesConnectionTaskParams.getFeaturesEnabled()).isNotEmpty().contains(CEFeatures.VISIBILITY);
    assertThat(
        ceKubernetesConnectionTaskParams.getKubernetesClusterConfig().getCredential().getKubernetesCredentialType())
        .isEqualTo(INHERIT_FROM_DELEGATE);
    assertThat(ceKubernetesConnectionTaskParams.getKubernetesClusterConfig().getCredential().getConfig())
        .isExactlyInstanceOf(KubernetesDelegateDetailsDTO.class);

    assertThat(ceKubernetesConnectionTaskParams.getKubernetesClusterConfig().getDelegateSelectors())
        .contains(DELEGATE_NAME);
  }

  private static ConnectorResponseDTO createConnectorResponseDTO() {
    KubernetesClusterConfigDTO connectorDTOWithDelegateCreds =
        KubernetesClusterConfigDTO.builder()
            .delegateSelectors(Collections.singleton(DELEGATE_NAME))
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(INHERIT_FROM_DELEGATE)
                            .config(KubernetesDelegateDetailsDTO.builder().build())
                            .build())
            .build();
    return ConnectorResponseDTO.builder()
        .connector(ConnectorInfoDTO.builder().connectorConfig(connectorDTOWithDelegateCreds).build())
        .build();
  }
}
