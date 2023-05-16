/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectivityTaskParams;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectivityTaskResponse;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmValidationParams;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class HttpHelmConnectivityDelegateTaskTest extends CategoryTest {
  @Mock private HttpHelmValidationHandler httpHelmValidationHandler;
  @InjectMocks
  private HttpHelmConnectivityDelegateTask delegateTask =
      new HttpHelmConnectivityDelegateTask(DelegateTaskPackage.builder()
                                               .delegateId("delegateid")
                                               .data(TaskData.builder().timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                                               .build(),
          null, notifyResponseData -> {}, () -> true);

  private static ConnectorValidationResult SUCCESS =
      ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCallValidationHandlerWithAnonymous() {
    HttpHelmConnectorDTO helmConnectorDTO =
        HttpHelmConnectorDTO.builder()
            .helmRepoUrl("localhost")
            .auth(HttpHelmAuthenticationDTO.builder().authType(HttpHelmAuthType.ANONYMOUS).build())
            .build();
    HttpHelmConnectivityTaskParams taskParams = HttpHelmConnectivityTaskParams.builder()
                                                    .helmConnector(helmConnectorDTO)
                                                    .encryptionDetails(Collections.emptyList())
                                                    .build();

    doReturn(SUCCESS).when(httpHelmValidationHandler).validate(any(), any());
    DelegateResponseData responseData = delegateTask.run(taskParams);
    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(HttpHelmConnectivityTaskResponse.class);
    HttpHelmConnectivityTaskResponse response = (HttpHelmConnectivityTaskResponse) responseData;
    assertThat(response.getConnectorValidationResult()).isEqualTo(SUCCESS);

    ArgumentCaptor<ConnectorValidationParams> paramsArgumentCaptor =
        ArgumentCaptor.forClass(ConnectorValidationParams.class);
    verify(httpHelmValidationHandler, times(1)).validate(paramsArgumentCaptor.capture(), any());
    ConnectorValidationParams connectorValidationParams = paramsArgumentCaptor.getValue();
    assertThat(connectorValidationParams).isNotNull();
    assertThat(connectorValidationParams).isInstanceOf(HttpHelmValidationParams.class);
    HttpHelmValidationParams helmValidationParams = (HttpHelmValidationParams) connectorValidationParams;
    assertThat(helmValidationParams.getEncryptionDataDetails()).isEmpty();
    assertThat(helmValidationParams.getHttpHelmConnectorDTO()).isEqualTo(helmConnectorDTO);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCallValidationHandlerWithUsernamePassword() {
    HttpHelmConnectorDTO helmConnectorDTO =
        HttpHelmConnectorDTO.builder()
            .helmRepoUrl("localhost")
            .auth(
                HttpHelmAuthenticationDTO.builder()
                    .authType(HttpHelmAuthType.USER_PASSWORD)
                    .credentials(
                        HttpHelmUsernamePasswordDTO.builder()
                            .username("test")
                            .passwordRef(SecretRefData.builder().identifier("passwordRef").scope(Scope.ACCOUNT).build())
                            .build())
                    .build())
            .build();
    HttpHelmConnectivityTaskParams taskParams = HttpHelmConnectivityTaskParams.builder()
                                                    .helmConnector(helmConnectorDTO)
                                                    .encryptionDetails(Collections.emptyList())
                                                    .build();

    doReturn(SUCCESS).when(httpHelmValidationHandler).validate(any(), any());
    DelegateResponseData responseData = delegateTask.run(taskParams);
    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(HttpHelmConnectivityTaskResponse.class);
    HttpHelmConnectivityTaskResponse response = (HttpHelmConnectivityTaskResponse) responseData;
    assertThat(response.getConnectorValidationResult()).isEqualTo(SUCCESS);

    ArgumentCaptor<ConnectorValidationParams> paramsArgumentCaptor =
        ArgumentCaptor.forClass(ConnectorValidationParams.class);
    verify(httpHelmValidationHandler, times(1)).validate(paramsArgumentCaptor.capture(), any());
    ConnectorValidationParams connectorValidationParams = paramsArgumentCaptor.getValue();
    assertThat(connectorValidationParams).isNotNull();
    assertThat(connectorValidationParams).isInstanceOf(HttpHelmValidationParams.class);
    HttpHelmValidationParams helmValidationParams = (HttpHelmValidationParams) connectorValidationParams;
    assertThat(helmValidationParams.getEncryptionDataDetails()).isEmpty();
    assertThat(helmValidationParams.getHttpHelmConnectorDTO()).isEqualTo(helmConnectorDTO);
  }
}
