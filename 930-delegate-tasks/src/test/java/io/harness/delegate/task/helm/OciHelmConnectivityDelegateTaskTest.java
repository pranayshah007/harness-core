/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectivityTaskParams;
import io.harness.delegate.beans.connector.helm.OciHelmConnectivityTaskResponse;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.delegate.beans.connector.helm.OciHelmValidationParams;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class OciHelmConnectivityDelegateTaskTest extends CategoryTest {
  @Mock private OciHelmValidationHandler ociHelmValidationHandler;
  @InjectMocks private HelmTaskHelperBase helmTaskHelperBase;
  @InjectMocks
  private OciHelmConnectivityDelegateTask delegateTask =
      new OciHelmConnectivityDelegateTask(DelegateTaskPackage.builder()
                                              .delegateId("delegateid")
                                              .data(TaskData.builder().timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                                              .build(),
          null, notifyResponseData -> {}, () -> true);

  private static ConnectorValidationResult SUCCESS =
      ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();

  private static final List<String> urlList =
      Arrays.asList("localhost", "localhost:443", "oci://localhost", "oci://localhost:443");

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testShouldCallValidationHandlerWithUsernamePassword() {
    OciHelmConnectorDTO helmConnectorDTO =
        OciHelmConnectorDTO.builder()
            .helmRepoUrl("localhost")
            .auth(
                OciHelmAuthenticationDTO.builder()
                    .authType(OciHelmAuthType.USER_PASSWORD)
                    .credentials(
                        OciHelmUsernamePasswordDTO.builder()
                            .username("test")
                            .passwordRef(SecretRefData.builder().identifier("passwordRef").scope(Scope.ACCOUNT).build())
                            .build())
                    .build())
            .build();
    OciHelmConnectivityTaskParams taskParams = OciHelmConnectivityTaskParams.builder()
                                                   .helmConnector(helmConnectorDTO)
                                                   .encryptionDetails(Collections.emptyList())
                                                   .build();

    doReturn(SUCCESS).when(ociHelmValidationHandler).validate(any(), any());
    DelegateResponseData responseData = delegateTask.run(taskParams);
    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(OciHelmConnectivityTaskResponse.class);
    OciHelmConnectivityTaskResponse response = (OciHelmConnectivityTaskResponse) responseData;
    assertThat(response.getConnectorValidationResult()).isEqualTo(SUCCESS);

    ArgumentCaptor<ConnectorValidationParams> paramsArgumentCaptor =
        ArgumentCaptor.forClass(ConnectorValidationParams.class);
    verify(ociHelmValidationHandler, times(1)).validate(paramsArgumentCaptor.capture(), any());
    ConnectorValidationParams connectorValidationParams = paramsArgumentCaptor.getValue();
    assertThat(connectorValidationParams).isNotNull();
    assertThat(connectorValidationParams).isInstanceOf(OciHelmValidationParams.class);
    OciHelmValidationParams helmValidationParams = (OciHelmValidationParams) connectorValidationParams;
    assertThat(helmValidationParams.getEncryptionDataDetails()).isEmpty();
    assertThat(helmValidationParams.getOciHelmConnectorDTO()).isEqualTo(helmConnectorDTO);
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testShouldCallValidationHandlerWithAnonymous() {
    OciHelmConnectorDTO helmConnectorDTO =
        OciHelmConnectorDTO.builder()
            .helmRepoUrl("localhost")
            .auth(OciHelmAuthenticationDTO.builder().authType(OciHelmAuthType.ANONYMOUS).build())
            .build();
    OciHelmConnectivityTaskParams taskParams = OciHelmConnectivityTaskParams.builder()
                                                   .helmConnector(helmConnectorDTO)
                                                   .encryptionDetails(Collections.emptyList())
                                                   .build();

    doReturn(SUCCESS).when(ociHelmValidationHandler).validate(any(), any());
    DelegateResponseData responseData = delegateTask.run(taskParams);
    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(OciHelmConnectivityTaskResponse.class);
    OciHelmConnectivityTaskResponse response = (OciHelmConnectivityTaskResponse) responseData;
    assertThat(response.getConnectorValidationResult()).isEqualTo(SUCCESS);

    ArgumentCaptor<ConnectorValidationParams> paramsArgumentCaptor =
        ArgumentCaptor.forClass(ConnectorValidationParams.class);
    verify(ociHelmValidationHandler, times(1)).validate(paramsArgumentCaptor.capture(), any());
    ConnectorValidationParams connectorValidationParams = paramsArgumentCaptor.getValue();
    assertThat(connectorValidationParams).isNotNull();
    assertThat(connectorValidationParams).isInstanceOf(OciHelmValidationParams.class);
    OciHelmValidationParams helmValidationParams = (OciHelmValidationParams) connectorValidationParams;
    assertThat(helmValidationParams.getEncryptionDataDetails()).isEmpty();
    assertThat(helmValidationParams.getOciHelmConnectorDTO()).isEqualTo(helmConnectorDTO);
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testGetParsedURI() throws URISyntaxException {
    for (String url : urlList) {
      testGetParsedURIHelper(url);
    }
  }

  private void testGetParsedURIHelper(String ociUrl) throws URISyntaxException {
    URI uri = helmTaskHelperBase.getParsedURI(ociUrl);
    assertThat(uri.getScheme()).isEqualTo("oci");
    assertThat(uri.getHost()).isEqualTo("localhost");
    assertThat(uri.getPort()).isEqualTo(443);
    assertThat(uri.toString()).isEqualTo("oci://localhost:443");
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testGetParsedUrlForUsernamePwd() {
    int i = 0;
    for (String url : urlList) {
      testGetParsedUrlUsernamePwdHelper(url, i++ % 2);
    }
  }

  private void testGetParsedUrlUsernamePwdHelper(String ociUrl, int rem) {
    String url = helmTaskHelperBase.getParsedUrlForUserNamePwd(ociUrl);
    if (rem == 0) {
      assertThat(url).isEqualTo("localhost");
    } else {
      assertThat(url).isEqualTo("localhost:443");
    }
  }
}
