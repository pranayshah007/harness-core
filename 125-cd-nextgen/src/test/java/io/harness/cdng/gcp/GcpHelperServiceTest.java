/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gcp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.GcpTaskType;
import io.harness.delegate.task.gcp.request.GcpListBucketsRequest;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.delegate.task.gcp.request.GcpTaskParameters;
import io.harness.delegate.task.gcp.response.GcpListBucketsResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class GcpHelperServiceTest extends CategoryTest {
  @Mock private ConnectorService connectorService;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @InjectMocks private GcpHelperService gcpHelperService;

  private final BaseNGAccess baseNGAccess = BaseNGAccess.builder().accountIdentifier("account").build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteSyncTask() {
    GcpRequest gcpRequest = GcpListBucketsRequest.builder().delegateSelectors(Collections.emptySet()).build();
    GcpListBucketsResponse response =
        GcpListBucketsResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    doReturn(response).when(delegateGrpcClientWrapper).executeSyncTaskV2(any(DelegateTaskRequest.class));

    GcpListBucketsResponse actualResponse =
        gcpHelperService.executeSyncTask(baseNGAccess, gcpRequest, GcpTaskType.LIST_BUCKETS, "list GCS buckets");
    assertThat(actualResponse).isEqualTo(response);
    ArgumentCaptor<DelegateTaskRequest> taskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(taskRequestCaptor.capture());
    DelegateTaskRequest taskRequest = taskRequestCaptor.getValue();
    GcpTaskParameters taskParameters = (GcpTaskParameters) taskRequest.getTaskParameters();
    assertThat(taskParameters.getGcpTaskType()).isEqualTo(GcpTaskType.LIST_BUCKETS);
    assertThat(taskParameters.getAccountId()).isEqualTo("account");
    assertThat(taskParameters.getGcpRequest()).isEqualTo(gcpRequest);
    assertThat(taskRequest.getTaskType()).isEqualTo(NGTaskType.GCP_TASK.name());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteSyncTaskFailed() {
    GcpRequest gcpRequest = GcpListBucketsRequest.builder().delegateSelectors(Collections.emptySet()).build();
    GcpListBucketsResponse response =
        GcpListBucketsResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage("Something went wrong")
            .errorDetail(ErrorDetail.builder().code(500).message("Something went wrong").build())
            .build();

    doReturn(response).when(delegateGrpcClientWrapper).executeSyncTaskV2(any(DelegateTaskRequest.class));

    assertThatThrownBy(
        () -> gcpHelperService.executeSyncTask(baseNGAccess, gcpRequest, GcpTaskType.LIST_BUCKETS, "list GCS buckets"))
        .hasMessageContaining("Failed to list GCS buckets - Something went wrong with error detail:");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteSyncTaskDelegateFailed() {
    GcpRequest gcpRequest = GcpListBucketsRequest.builder().delegateSelectors(Collections.emptySet()).build();
    ErrorNotifyResponseData responseData =
        ErrorNotifyResponseData.builder().errorMessage("Something went wrong").build();

    doReturn(responseData).when(delegateGrpcClientWrapper).executeSyncTaskV2(any(DelegateTaskRequest.class));

    assertThatThrownBy(
        () -> gcpHelperService.executeSyncTask(baseNGAccess, gcpRequest, GcpTaskType.LIST_BUCKETS, "list GCS buckets"))
        .hasMessageContaining("Failed to list GCS buckets - Something went wrong");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetConnector() {
    IdentifierRef ref = IdentifierRef.builder().identifier("test").build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    setupConnector(ref, connectorDTO, ConnectorType.GCP);

    GcpConnectorDTO result = gcpHelperService.getConnector(ref);

    assertThat(result).isEqualTo(connectorDTO);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetConnectorInvalidType() {
    IdentifierRef ref = IdentifierRef.builder().identifier("test").build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    setupConnector(ref, connectorDTO, ConnectorType.AWS);

    assertThatThrownBy(() -> gcpHelperService.getConnector(ref))
        .hasMessageContaining("Connector not found for identifier: [test]");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetConnectorMissing() {
    IdentifierRef ref = IdentifierRef.builder().identifier("test").build();
    setupConnector(ref, null, ConnectorType.GCP);

    assertThatThrownBy(() -> gcpHelperService.getConnector(ref))
        .hasMessageContaining("Connector not found for identifier: [test]");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetGetEncryptionDetailsManual() {
    GcpManualDetailsDTO manualDetailsDTO = GcpManualDetailsDTO.builder().build();
    GcpConnectorDTO gcpConnectorDTO = GcpConnectorDTO.builder()
                                          .credential(GcpConnectorCredentialDTO.builder()
                                                          .config(manualDetailsDTO)
                                                          .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                          .build())
                                          .build();
    List<EncryptedDataDetail> encryptedDataDetailList = singletonList(EncryptedDataDetail.builder().build());
    doReturn(encryptedDataDetailList)
        .when(secretManagerClientService)
        .getEncryptionDetails(baseNGAccess, manualDetailsDTO);

    assertThat(gcpHelperService.getEncryptionDetails(gcpConnectorDTO, baseNGAccess)).isEqualTo(encryptedDataDetailList);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetGetEncryptionDetailsDelegate() {
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder().gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).build())
            .build();

    assertThat(gcpHelperService.getEncryptionDetails(gcpConnectorDTO, baseNGAccess)).isEmpty();

    verify(secretManagerClientService, never()).getEncryptionDetails(eq(baseNGAccess), any(DecryptableEntity.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManualDetailsDTO() {
    GcpManualDetailsDTO manualDetailsDTO = GcpManualDetailsDTO.builder().build();
    GcpConnectorDTO gcpConnectorDTO = GcpConnectorDTO.builder()
                                          .credential(GcpConnectorCredentialDTO.builder()
                                                          .config(manualDetailsDTO)
                                                          .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                          .build())
                                          .build();

    assertThat(gcpHelperService.getManualDetailsDTO(gcpConnectorDTO)).isEqualTo(manualDetailsDTO);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManualDetailsDTODelegate() {
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder().gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).build())
            .build();

    assertThat(gcpHelperService.getManualDetailsDTO(gcpConnectorDTO)).isNull();
  }

  private void setupConnector(IdentifierRef ref, GcpConnectorDTO connector, ConnectorType type) {
    if (connector == null) {
      doReturn(Optional.empty())
          .when(connectorService)
          .get(ref.getAccountIdentifier(), ref.getOrgIdentifier(), ref.getProjectIdentifier(), ref.getIdentifier());
    } else {
      doReturn(
          Optional.of(ConnectorResponseDTO.builder()
                          .connector(ConnectorInfoDTO.builder().connectorType(type).connectorConfig(connector).build())
                          .build()))
          .when(connectorService)
          .get(ref.getAccountIdentifier(), ref.getOrgIdentifier(), ref.getProjectIdentifier(), ref.getIdentifier());
    }
  }
}
