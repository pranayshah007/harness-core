/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.taskhandlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectivityStatus.SUCCESS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.task.gcp.GcpValidationTaskHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.gcp.GcpValidationParams;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.request.GcpValidationRequest;
import io.harness.encryption.SecretRefData;
import io.harness.gcp.client.GcpClient;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class GcpValidationTaskHandlerTest extends CategoryTest {
  @Mock private GcpClient gcpClient;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private GcpRequestMapper gcpRequestMapper;
  @InjectMocks private GcpValidationTaskHandler taskHandler;
  private String accountIdentifier = "accountIdentifier";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeRequestSuccess() {
    final ConnectorValidationResult connectorValidationResult = taskHandler.validate(buildGcpValidationRequest());
    assertThat(connectorValidationResult.getStatus()).isEqualTo(SUCCESS);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeRequestFailure() {
    doThrow(new RuntimeException("No Default Credentials found")).when(gcpClient).validateDefaultCredentials();
    taskHandler.validate(buildGcpValidationRequest());
  }

  private GcpValidationRequest buildGcpValidationRequest() {
    return GcpValidationRequest.builder().delegateSelectors(Collections.singleton("foo")).build();
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV)
  @Category(UnitTests.class)
  public void executeRequestSuccessForSecretKey() {
    when(decryptionHelper.decrypt(any(), any()))
        .thenReturn(GcpManualDetailsDTO.builder().secretKeyRef(SecretRefData.builder().build()).build());
    final ConnectorValidationResult connectorValidationResult =
        taskHandler.validate(buildGcpValidationRequestWithSecretKey());
    assertThat(connectorValidationResult.getStatus()).isEqualTo(SUCCESS);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = OwnerRule.ABHINAV)
  @Category(UnitTests.class)
  public void executeRequestFailureForSecretKey() {
    when(decryptionHelper.decrypt(any(), any()))
        .thenReturn(GcpManualDetailsDTO.builder().secretKeyRef(SecretRefData.builder().build()).build());
    doThrow(new RuntimeException("No Credentials found")).when(gcpClient).getGkeContainerService(any());
    taskHandler.validate(buildGcpValidationRequestWithSecretKey());
  }

  private GcpValidationRequest buildGcpValidationRequestWithSecretKey() {
    return GcpValidationRequest.builder()
        .gcpManualDetailsDTO(GcpManualDetailsDTO.builder().secretKeyRef(SecretRefData.builder().build()).build())
        .encryptionDetails(ImmutableList.of(EncryptedDataDetail.builder().fieldName("accessKey").build()))
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void validateSuccess() {
    when(decryptionHelper.decrypt(any(), any()))
        .thenReturn(GcpManualDetailsDTO.builder().secretKeyRef(SecretRefData.builder().build()).build());
    doCallRealMethod().when(gcpRequestMapper).toGcpRequest(any());
    GcpManualDetailsDTO config = GcpManualDetailsDTO.builder().secretKeyRef(SecretRefData.builder().build()).build();
    GcpConnectorCredentialDTO gcpConnectorCredentialDTO = GcpConnectorCredentialDTO.builder()
                                                              .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                              .config(config)
                                                              .build();
    GcpConnectorDTO gcpConnectorDTO = GcpConnectorDTO.builder().credential(gcpConnectorCredentialDTO).build();
    ConnectorValidationParams connectorValidationParams =
        GcpValidationParams.builder()
            .gcpConnectorDTO(gcpConnectorDTO)
            .connectorName("GcpConnectorName")
            .delegateSelectors(Collections.singleton("foo"))
            .encryptionDetails(ImmutableList.of(EncryptedDataDetail.builder().fieldName("accessKey").build()))
            .build();

    ConnectorValidationResult result = taskHandler.validate(connectorValidationParams, accountIdentifier);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void validateFailure() {
    when(decryptionHelper.decrypt(any(), any()))
        .thenReturn(GcpManualDetailsDTO.builder().secretKeyRef(SecretRefData.builder().build()).build());
    doThrow(new RuntimeException("No Credentials found")).when(gcpClient).getGkeContainerService(any());
    doCallRealMethod().when(gcpRequestMapper).toGcpRequest(any());
    GcpManualDetailsDTO config = GcpManualDetailsDTO.builder().secretKeyRef(SecretRefData.builder().build()).build();
    GcpConnectorCredentialDTO gcpConnectorCredentialDTO = GcpConnectorCredentialDTO.builder()
                                                              .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                              .config(config)
                                                              .build();
    GcpConnectorDTO gcpConnectorDTO = GcpConnectorDTO.builder().credential(gcpConnectorCredentialDTO).build();
    ConnectorValidationParams connectorValidationParams = GcpValidationParams.builder()
                                                              .gcpConnectorDTO(gcpConnectorDTO)
                                                              .connectorName("GcpConnectorName")
                                                              .encryptionDetails(null)
                                                              .build();

    taskHandler.validate(connectorValidationParams, accountIdentifier);
  }
}
