/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.k8s.cluster.resources.rancher;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.rancher.RancherConnectorBearerTokenAuthenticationDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.delegate.beans.connector.rancher.RancherListClustersTaskResponse;
import io.harness.delegate.beans.connector.rancher.RancherTaskParams;
import io.harness.encryption.SecretRefData;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;

@OwnedBy(HarnessTeam.CDP)
public class RancherClusterHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks private RancherClusterHelper rancherClusterHelper = new RancherClusterHelper();

  @Mock private ConnectorService connectorService;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private ExceptionManager exceptionManager;

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testDelegateTaskResponseExceptions() {
    DelegateResponseData errorResponse = ErrorNotifyResponseData.builder().build();
    assertThatThrownBy(() -> RancherClusterHelper.throwExceptionIfTaskFailed(errorResponse))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to list rancher clusters");

    DelegateResponseData failedTaskResponse =
        RancherListClustersTaskResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
    assertThatThrownBy(() -> RancherClusterHelper.throwExceptionIfTaskFailed(failedTaskResponse))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to list rancher clusters");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetEncryptionDetailsWithNothingToDecrypt() {
    RancherConnectorDTO rancherConnectorDTO = mock(RancherConnectorDTO.class);
    doReturn(emptyList()).when(rancherConnectorDTO).getDecryptableEntities();
    List<EncryptedDataDetail> encryptionDetails = rancherClusterHelper.getEncryptionDetails(rancherConnectorDTO, null);

    assertThat(encryptionDetails).isEmpty();
    verify(secretManagerClientService, times(0))
        .getEncryptionDetails(any(NGAccess.class), any(DecryptableEntity.class));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetEncryptionDetails() {
    RancherConnectorDTO rancherConnectorDTO = mock(RancherConnectorDTO.class);
    BaseNGAccess ngAccess = mock(BaseNGAccess.class);
    List<EncryptedDataDetail> expectedEncryptedDetails = mock(List.class);
    doReturn(List.of(RancherConnectorBearerTokenAuthenticationDTO.builder()
                         .passwordRef(SecretRefData.builder().build())
                         .build()))
        .when(rancherConnectorDTO)
        .getDecryptableEntities();
    doReturn(expectedEncryptedDetails)
        .when(secretManagerClientService)
        .getEncryptionDetails(any(NGAccess.class), any(DecryptableEntity.class));
    List<EncryptedDataDetail> actualEncryptedDetails =
        rancherClusterHelper.getEncryptionDetails(rancherConnectorDTO, ngAccess);

    assertThat(actualEncryptedDetails).isEqualTo(expectedEncryptedDetails);
    verify(secretManagerClientService, times(1))
        .getEncryptionDetails(any(NGAccess.class), any(DecryptableEntity.class));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetRancherConnectorFailures() {
    doReturn(Optional.empty()).when(connectorService).get(any(), any(), any(), any());
    assertThatThrownBy(() -> rancherClusterHelper.getRancherConnector(IdentifierRef.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Connector not found");

    ConnectorResponseDTO rancherConnectorResponse = mock(ConnectorResponseDTO.class);
    ConnectorInfoDTO connectorInfoDTO = mock(ConnectorInfoDTO.class);
    doReturn(connectorInfoDTO).when(rancherConnectorResponse).getConnector();
    doReturn(ConnectorType.ARTIFACTORY).when(connectorInfoDTO).getConnectorType();
    doReturn(Optional.of(rancherConnectorResponse)).when(connectorService).get(any(), any(), any(), any());
    assertThatThrownBy(() -> rancherClusterHelper.getRancherConnector(IdentifierRef.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Connector not found");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetRancherConnector() {
    ConnectorResponseDTO rancherConnectorResponse = mock(ConnectorResponseDTO.class);
    ConnectorInfoDTO connectorInfoDTO = mock(ConnectorInfoDTO.class);
    RancherConnectorDTO connectorDTO = mock(RancherConnectorDTO.class);
    doReturn(connectorInfoDTO).when(rancherConnectorResponse).getConnector();
    doReturn(ConnectorType.RANCHER).when(connectorInfoDTO).getConnectorType();
    doReturn(Optional.of(rancherConnectorResponse)).when(connectorService).get(any(), any(), any(), any());
    doReturn(connectorDTO).when(connectorInfoDTO).getConnectorConfig();

    assertThat(rancherClusterHelper.getRancherConnector(IdentifierRef.builder().build())).isEqualTo(connectorDTO);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testDelegateTaskExceptionProcessing() {
    doThrow(WingsException.class).when(delegateGrpcClientWrapper).executeSyncTaskV2(any(DelegateTaskRequest.class));
    rancherClusterHelper.executeListClustersDelegateTask(
        RancherTaskParams.builder()
            .rancherConnectorDTO(RancherConnectorDTO.builder().delegateSelectors(emptySet()).build())
            .build(),
        BaseNGAccess.builder().build());
    verify(exceptionManager, times(1))
        .processException(
            any(DelegateServiceDriverException.class), any(WingsException.ExecutionContext.class), any(Logger.class));
  }
}
