/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.aws.AwsClient;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.heartbeat.AwsValidationParamsProvider;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.impl.DefaultConnectorServiceImpl;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.aws.AwsNgConfigMapper;
import io.harness.connector.task.aws.AwsValidationHandler;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsInheritFromDelegateSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsValidateTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
public class AwsConnectorValidatorTest extends CategoryTest {
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private EncryptionHelper encryptionHelper;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private AwsClient awsClient;
  @Mock private DefaultConnectorServiceImpl connectorService;
  @InjectMocks private AwsConnectorValidator awsConnectorValidator;
  @InjectMocks private AwsNgConfigMapper ngConfigMapper;
  @Mock private ExceptionManager exceptionManager;
  @Mock private Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Mock private Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    on(ngConfigMapper).set("decryptionHelper", decryptionHelper);
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV)
  @Category(UnitTests.class)
  public void validateTestWithEc2Iam() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder()
                            .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                            .crossAccountAccess(CrossAccountAccessDTO.builder().build())
                            .config(AwsInheritFromDelegateSpecDTO.builder()
                                        .delegateSelectors(Collections.singleton("delegate"))
                                        .build())
                            .build())
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(AwsValidateTaskResponse.builder()
                        .connectorValidationResult(
                            ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                        .build());
    awsConnectorValidator.validate(
        awsConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV)
  @Category(UnitTests.class)
  public void validateTestWithAccessKey() {
    SecretRefData passwordSecretRef =
        SecretRefData.builder().identifier("passwordRefIdentifier").scope(Scope.ACCOUNT).build();
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .crossAccountAccess(CrossAccountAccessDTO.builder().build())
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(
                        AwsManualConfigSpecDTO.builder().secretKeyRef(passwordSecretRef).accessKey("accessKey").build())
                    .build())
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(AwsValidateTaskResponse.builder()
                        .connectorValidationResult(
                            ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                        .build());
    awsConnectorValidator.validate(
        awsConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = OwnerRule.RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void validateTestWithAccessKeyOnManager() {
    SecretRefData passwordSecretRef =
        SecretRefData.builder().identifier("passwordRefIdentifier").scope(Scope.ACCOUNT).build();
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .crossAccountAccess(CrossAccountAccessDTO.builder().build())
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(
                        AwsManualConfigSpecDTO.builder().secretKeyRef(passwordSecretRef).accessKey("accessKey").build())
                    .build())
            .executeOnDelegate(false)
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    when(decryptionHelper.decrypt(any(), any()))
        .thenReturn(AwsManualConfigSpecDTO.builder()
                        .secretKeyRef(SecretRefData.builder().build())
                        .accessKey("accessKey")
                        .build());
    doNothing().when(awsClient).validateAwsAccountCredential(any(), any());
    AwsValidationHandler awsValidationHandler = mock(AwsValidationHandler.class);
    on(awsValidationHandler).set("ngConfigMapper", ngConfigMapper);
    on(awsValidationHandler).set("awsClient", awsClient);
    when(awsValidationHandler.validate((ConnectorValidationParams) any(), any())).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(ArgumentMatchers.eq("Aws"))).thenReturn(awsValidationHandler);

    AwsValidationParamsProvider awsValidationParamsProvider = new AwsValidationParamsProvider();
    on(awsValidationParamsProvider).set("encryptionHelper", encryptionHelper);
    when(connectorValidationParamsProviderMap.get(ArgumentMatchers.eq("Aws"))).thenReturn(awsValidationParamsProvider);
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.AWS)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .build())
                                    .build()));
    ConnectorValidationResult connectorValidationResult = awsConnectorValidator.validate(
        awsConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = OwnerRule.RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void validateTestWithoutAccessKeyOnManager() {
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder()
                                          .credential(AwsCredentialDTO.builder()
                                                          .crossAccountAccess(CrossAccountAccessDTO.builder().build())
                                                          .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                                                          .build())
                                          .executeOnDelegate(false)
                                          .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    AwsValidationHandler awsValidationHandler = mock(AwsValidationHandler.class);
    on(awsValidationHandler).set("ngConfigMapper", ngConfigMapper);
    on(awsValidationHandler).set("exceptionManager", exceptionManager);
    when(exceptionManager.processException(any(), any(), any()))
        .thenReturn(
            WingsException.builder()
                .message(
                    "Connector with credential type INHERIT_FROM_DELEGATE does not support validation through harness")
                .build());
    when(awsValidationHandler.validate((ConnectorValidationParams) any(), any())).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(ArgumentMatchers.eq("Aws"))).thenReturn(awsValidationHandler);

    AwsValidationParamsProvider awsValidationParamsProvider = new AwsValidationParamsProvider();
    on(awsValidationParamsProvider).set("encryptionHelper", encryptionHelper);
    when(connectorValidationParamsProviderMap.get(ArgumentMatchers.eq("Aws"))).thenReturn(awsValidationParamsProvider);
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.AWS)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .build())
                                    .build()));
    assertThatThrownBy(()
                           -> awsConnectorValidator.validate(awsConnectorDTO, "accountIdentifier", "orgIdentifier",
                               "projectIdentifier", "identifier"))
        .isInstanceOf(WingsException.class)
        .hasMessage("Connector with credential type INHERIT_FROM_DELEGATE does not support validation through harness");
  }
}
