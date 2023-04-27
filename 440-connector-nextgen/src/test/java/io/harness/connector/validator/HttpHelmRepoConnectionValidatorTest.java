/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.connector.ConnectivityStatus.SUCCESS;

import static software.wings.beans.TaskType.HTTP_HELM_CONNECTIVITY_TASK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectivityTaskParams;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectivityTaskResponse;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HttpHelmRepoConnectionValidatorTest extends CategoryTest {
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private EncryptionHelper encryptionHelper;
  @InjectMocks private HttpHelmRepoConnectionValidator validator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void testShouldValidateSuccess() {
    SecretRefData passwordRef = SecretRefData.builder().identifier("password").scope(Scope.ACCOUNT).build();
    SecretRefData usernameRef = SecretRefData.builder().identifier("username").scope(Scope.ACCOUNT).build();

    HttpHelmUsernamePasswordDTO credentials =
        HttpHelmUsernamePasswordDTO.builder().usernameRef(usernameRef).passwordRef(passwordRef).build();

    HttpHelmAuthenticationDTO auth =
        HttpHelmAuthenticationDTO.builder().authType(HttpHelmAuthType.USER_PASSWORD).credentials(credentials).build();
    HttpHelmConnectorDTO connectorDTO = HttpHelmConnectorDTO.builder().helmRepoUrl("localhost").auth(auth).build();

    doReturn(Collections.emptyList()).when(encryptionHelper).getEncryptionDetail(any(), any(), any(), any());
    doReturn(Collections.emptyList()).when(ngSecretService).getEncryptionDetails(any(), any());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(HttpHelmConnectivityTaskResponse.builder()
                        .connectorValidationResult(ConnectorValidationResult.builder().status(SUCCESS).build())
                        .build());
    validator.validate(connectorDTO, "acc", "org", "prj", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void testTaskType() {
    assertThat(validator.getTaskType()).isEqualTo(HTTP_HELM_CONNECTIVITY_TASK.name());
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void testTaskParameters() {
    SecretRefData passwordRef = SecretRefData.builder().identifier("password").scope(Scope.ACCOUNT).build();
    SecretRefData usernameRef = SecretRefData.builder().identifier("username").scope(Scope.ACCOUNT).build();

    HttpHelmUsernamePasswordDTO credentials =
        HttpHelmUsernamePasswordDTO.builder().usernameRef(usernameRef).passwordRef(passwordRef).build();

    HttpHelmAuthenticationDTO auth =
        HttpHelmAuthenticationDTO.builder().authType(HttpHelmAuthType.USER_PASSWORD).credentials(credentials).build();
    HttpHelmConnectorDTO connectorDTO = HttpHelmConnectorDTO.builder().helmRepoUrl("localhost").auth(auth).build();
    doReturn(Collections.emptyList()).when(encryptionHelper).getEncryptionDetail(any(), any(), any(), any());

    TaskParameters params = validator.getTaskParameters(connectorDTO, "acc", "org", "prj");
    assertThat(params).isNotNull();
    assertThat(params).isInstanceOf(HttpHelmConnectivityTaskParams.class);
    HttpHelmConnectivityTaskParams helmTaskParams = (HttpHelmConnectivityTaskParams) params;
    assertThat(helmTaskParams.getHelmConnector()).isEqualTo(connectorDTO);
    assertThat(helmTaskParams.getEncryptionDetails()).isEmpty();
  }
}
