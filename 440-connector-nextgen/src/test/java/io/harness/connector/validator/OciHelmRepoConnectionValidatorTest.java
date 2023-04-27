/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.connector.ConnectivityStatus.SUCCESS;

import static software.wings.beans.TaskType.OCI_HELM_CONNECTIVITY_TASK;

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
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectivityTaskParams;
import io.harness.delegate.beans.connector.helm.OciHelmConnectivityTaskResponse;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
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

public class OciHelmRepoConnectionValidatorTest extends CategoryTest {
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private EncryptionHelper encryptionHelper;
  @InjectMocks private OciHelmRepoConnectionValidator validator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testShouldValidateSuccess() {
    SecretRefData passwordRef = SecretRefData.builder().identifier("password").scope(Scope.ACCOUNT).build();
    SecretRefData usernameRef = SecretRefData.builder().identifier("username").scope(Scope.ACCOUNT).build();

    OciHelmUsernamePasswordDTO credentials =
        OciHelmUsernamePasswordDTO.builder().usernameRef(usernameRef).passwordRef(passwordRef).build();

    OciHelmAuthenticationDTO auth =
        OciHelmAuthenticationDTO.builder().authType(OciHelmAuthType.USER_PASSWORD).credentials(credentials).build();
    OciHelmConnectorDTO connectorDTO = OciHelmConnectorDTO.builder().helmRepoUrl("localhost").auth(auth).build();

    doReturn(Collections.emptyList()).when(encryptionHelper).getEncryptionDetail(any(), any(), any(), any());
    doReturn(Collections.emptyList()).when(ngSecretService).getEncryptionDetails(any(), any());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(OciHelmConnectivityTaskResponse.builder()
                        .connectorValidationResult(ConnectorValidationResult.builder().status(SUCCESS).build())
                        .build());
    validator.validate(connectorDTO, "acc", "org", "prj", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testTaskType() {
    assertThat(validator.getTaskType()).isEqualTo(OCI_HELM_CONNECTIVITY_TASK.name());
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testTaskParameters() {
    SecretRefData passwordRef = SecretRefData.builder().identifier("password").scope(Scope.ACCOUNT).build();
    SecretRefData usernameRef = SecretRefData.builder().identifier("username").scope(Scope.ACCOUNT).build();

    OciHelmUsernamePasswordDTO credentials =
        OciHelmUsernamePasswordDTO.builder().usernameRef(usernameRef).passwordRef(passwordRef).build();

    OciHelmAuthenticationDTO auth =
        OciHelmAuthenticationDTO.builder().authType(OciHelmAuthType.USER_PASSWORD).credentials(credentials).build();
    OciHelmConnectorDTO connectorDTO = OciHelmConnectorDTO.builder().helmRepoUrl("localhost").auth(auth).build();
    doReturn(Collections.emptyList()).when(encryptionHelper).getEncryptionDetail(any(), any(), any(), any());

    TaskParameters params = validator.getTaskParameters(connectorDTO, "acc", "org", "prj");
    assertThat(params).isNotNull();
    assertThat(params).isInstanceOf(OciHelmConnectivityTaskParams.class);
    OciHelmConnectivityTaskParams helmTaskParams = (OciHelmConnectivityTaskParams) params;
    assertThat(helmTaskParams.getHelmConnector()).isEqualTo(connectorDTO);
    assertThat(helmTaskParams.getEncryptionDetails()).isEmpty();
  }
}
