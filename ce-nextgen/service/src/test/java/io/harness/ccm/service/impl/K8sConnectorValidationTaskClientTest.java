/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.delegate.beans.connector.k8Connector.KubernetesConnectorTestHelper.inClusterDelegateK8sConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskResponse;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class K8sConnectorValidationTaskClientTest extends CategoryTest {
  private static final String CONNECTOR_IDENTIFIER = "cid";
  private static final String ACCOUNT_IDENTIFIER = "aid";
  private static final String ORG_IDENTIFIER = "oid";
  private static final String PROJECT_IDENTIFIER = "pid";

  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private K8sConnectorHelper connectorHelper;
  @InjectMocks private K8sConnectorValidationTaskClient k8sConnectorValidationTaskClient;

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testValidateConnectorForCePermissionsThrowsDelegateServiceDriverException() throws Exception {
    final String errorMessage = "Something went wrong";

    when(connectorHelper.getConnectorConfig(
             eq(CONNECTOR_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(inClusterDelegateK8sConfig());
    // for InClusterDelegate, the encryptionDetailsList is null
    when(connectorHelper.getEncryptionDetail(
             eq(inClusterDelegateK8sConfig()), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(null);

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenThrow(new DelegateServiceDriverException(errorMessage));

    assertThatThrownBy(()
                           -> k8sConnectorValidationTaskClient.validateConnectorForCePermissions(
                               CONNECTOR_IDENTIFIER, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isExactlyInstanceOf(DelegateServiceDriverException.class);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testValidateConnectorForCePermissionsSuccess() throws Exception {
    when(connectorHelper.getConnectorConfig(
             eq(CONNECTOR_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(inClusterDelegateK8sConfig());
    // for InClusterDelegate, the encryptionDetailsList is null
    when(connectorHelper.getEncryptionDetail(
             eq(inClusterDelegateK8sConfig()), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(null);

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(KubernetesConnectionTaskResponse.builder()
                        .connectorValidationResult(
                            ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                        .build());

    final ConnectorValidationResult result = k8sConnectorValidationTaskClient.validateConnectorForCePermissions(
        CONNECTOR_IDENTIFIER, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(result.getErrors()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testValidateConnectorForCePermissionsFailure() throws Exception {
    ErrorDetail errorDetail = ErrorDetail.builder().code(0).message("message").reason("reason").build();

    when(connectorHelper.getConnectorConfig(
             eq(CONNECTOR_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(inClusterDelegateK8sConfig());
    // for InClusterDelegate, the encryptionDetailsList is null
    when(connectorHelper.getEncryptionDetail(
             eq(inClusterDelegateK8sConfig()), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(null);

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(KubernetesConnectionTaskResponse.builder()
                        .connectorValidationResult(ConnectorValidationResult.builder()
                                                       .status(ConnectivityStatus.FAILURE)
                                                       .errors(Collections.singletonList(errorDetail))
                                                       .build())
                        .build());

    final ConnectorValidationResult result = k8sConnectorValidationTaskClient.validateConnectorForCePermissions(
        CONNECTOR_IDENTIFIER, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getErrors()).isNotEmpty();
    assertThat(result.getErrors().get(0)).isEqualTo(errorDetail);
  }
}
