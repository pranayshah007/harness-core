/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.delegate.beans.connector.k8Connector.KubernetesConnectorTestHelper.inClusterDelegateK8sConfig;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesConnectorTestHelper.manualK8sConfig;

import static software.wings.beans.TaskType.K8S_SERVICE_ACCOUNT_INFO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.k8Connector.K8sServiceAccountInfoResponse;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskParams;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class K8sServiceAccountDelegateTaskClientTest extends CategoryTest {
  private static final String CONNECTOR_IDENTIFIER = "cid";
  private static final String ACCOUNT_IDENTIFIER = "aid";
  private static final String ORG_IDENTIFIER = "oid";
  private static final String PROJECT_IDENTIFIER = "pid";
  private static final String USERNAME = "system:serviceaccount:harness-delegate:default";

  @Captor ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor;

  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private K8sConnectorHelper connectorHelper;
  @InjectMocks private K8sServiceAccountDelegateTaskClient delegateTaskClient;

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void fetchServiceAccount_DelegateResponseErrorCheck() throws Exception {
    final String errorMessage = "Something went wrong";

    when(connectorHelper.getConnectorConfig(
             eq(CONNECTOR_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(inClusterDelegateK8sConfig());
    // for InClusterDelegate, the encryptionDetailsList is null
    when(connectorHelper.getEncryptionDetail(
             eq(inClusterDelegateK8sConfig()), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(null);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ErrorNotifyResponseData.builder().errorMessage(errorMessage).build());

    assertThatThrownBy(()
                           -> delegateTaskClient.fetchServiceAccount(
                               CONNECTOR_IDENTIFIER, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessage(errorMessage);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void fetchServiceAccount_InheritFromDelegate() throws Exception {
    when(connectorHelper.getConnectorConfig(
             eq(CONNECTOR_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(inClusterDelegateK8sConfig());
    // for In Cluster Delegate, the encryptionDetailsList is null
    when(connectorHelper.getEncryptionDetail(
             eq(inClusterDelegateK8sConfig()), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(null);

    KubernetesConnectionTaskParams kubernetesConnectionTaskParams = assertAndReturnTaskParams();

    assertThat(kubernetesConnectionTaskParams.getEncryptionDetails()).isNull();
    assertThat(kubernetesConnectionTaskParams.getKubernetesClusterConfig()).isEqualTo(inClusterDelegateK8sConfig());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void fetchServiceAccount_ManualConfig() throws Exception {
    final EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().fieldName("fname").build();

    when(connectorHelper.getConnectorConfig(
             eq(CONNECTOR_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(manualK8sConfig());
    when(connectorHelper.getEncryptionDetail(
             eq(manualK8sConfig()), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(ImmutableList.of(encryptedDataDetail));

    KubernetesConnectionTaskParams kubernetesConnectionTaskParams = assertAndReturnTaskParams();

    assertThat(kubernetesConnectionTaskParams.getEncryptionDetails()).containsExactlyInAnyOrder(encryptedDataDetail);
    assertThat(kubernetesConnectionTaskParams.getKubernetesClusterConfig()).isEqualTo(manualK8sConfig());
  }

  private KubernetesConnectionTaskParams assertAndReturnTaskParams() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture()))
        .thenReturn(K8sServiceAccountInfoResponse.builder().username(USERNAME).build());

    final DelegateResponseData response = delegateTaskClient.fetchServiceAccount(
        CONNECTOR_IDENTIFIER, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(response).isNotNull().isExactlyInstanceOf(K8sServiceAccountInfoResponse.class);

    assertThat(((K8sServiceAccountInfoResponse) response).getUsername()).isEqualTo(USERNAME);

    DelegateTaskRequest request = delegateTaskRequestArgumentCaptor.getValue();
    assertThat(request).isNotNull();
    assertThat(request.getAccountId()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(request.getTaskType()).isEqualTo(K8S_SERVICE_ACCOUNT_INFO.name());

    TaskParameters taskParameters = request.getTaskParameters();
    assertThat(taskParameters).isNotNull().isExactlyInstanceOf(KubernetesConnectionTaskParams.class);

    return (KubernetesConnectionTaskParams) taskParameters;
  }
}
