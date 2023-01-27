/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.TerraformCloudValidationParamsProvider;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.impl.DefaultConnectorServiceImpl;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.terraformcloud.TerraformCloudConfigMapper;
import io.harness.connector.task.terraformcloud.TerraformCloudValidationHandler;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudValidateTaskResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.terraformcloud.TerraformCloudConfig;

import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(CDP)
public class TerraformCloudConnectorValidatorTest extends CategoryTest {
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private EncryptionHelper encryptionHelper;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private DefaultConnectorServiceImpl connectorService;
  @InjectMocks private TerraformCloudConfigMapper terraformCloudConfigMapper;
  @InjectMocks private TerraformCloudConnectorValidator terraformCloudConnectorValidator;
  @Mock private Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Mock private Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    on(terraformCloudConfigMapper).set("decryptionHelper", decryptionHelper);
  }

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void validateTerraformCloudConnectionValidationWithApiToken() {
    SecretRefData secretRef = SecretRefData.builder().identifier("secretKey").scope(Scope.ACCOUNT).build();
    String url = "https://some.io";
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        TerraformCloudConnectorDTO.builder()
            .terraformCloudUrl(url)
            .credential(TerraformCloudCredentialDTO.builder()
                            .type(TerraformCloudCredentialType.API_TOKEN)
                            .spec(TerraformCloudTokenCredentialsDTO.builder().apiToken(secretRef).build())
                            .build())
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(TerraformCloudValidateTaskResponse.builder()
                        .connectorValidationResult(
                            ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                        .build());
    terraformCloudConnectorValidator.validate(
        terraformCloudConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void validateTerraformCloudConnectionValidationWithApiTokenOnManager() {
    String token = "dummy_token";
    String url = "https://some.io";
    SecretRefData tokenSecretRef = SecretRefData.builder()
                                       .identifier("tokenRefIdentifier")
                                       .decryptedValue(token.toCharArray())
                                       .scope(Scope.ACCOUNT)
                                       .build();
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        TerraformCloudConnectorDTO.builder()
            .terraformCloudUrl(url)
            .credential(TerraformCloudCredentialDTO.builder()
                            .type(TerraformCloudCredentialType.API_TOKEN)
                            .spec(TerraformCloudTokenCredentialsDTO.builder().apiToken(tokenSecretRef).build())
                            .build())
            .executeOnDelegate(false)
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    TerraformCloudTokenCredentialsDTO terraformCloudTokenCredentialsDTO =
        TerraformCloudTokenCredentialsDTO.builder()
            .apiToken(SecretRefData.builder()
                          .identifier("account.tokenRefIdentifier")
                          .decryptedValue(token.toCharArray())
                          .build())
            .build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(terraformCloudTokenCredentialsDTO);
    TerraformCloudValidationHandler terraformClodValidationHandler = mock(TerraformCloudValidationHandler.class);
    on(terraformClodValidationHandler).set("terraformCloudConfigMapper", terraformCloudConfigMapper);
    when(terraformClodValidationHandler.validate(any(ConnectorValidationParams.class), any())).thenCallRealMethod();
    when(terraformClodValidationHandler.validate(any(TerraformCloudConfig.class))).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(Matchers.eq("TerraformCloud")))
        .thenReturn(terraformClodValidationHandler);

    TerraformCloudValidationParamsProvider terraformCloudValidationParamsProvider =
        new TerraformCloudValidationParamsProvider();
    on(terraformCloudValidationParamsProvider).set("encryptionHelper", encryptionHelper);
    when(connectorValidationParamsProviderMap.get(Matchers.eq("TerraformCloud")))
        .thenReturn(terraformCloudValidationParamsProvider);
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.TERRAFORM_CLOUD)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .connectorConfig(terraformCloudConnectorDTO)
                                                   .build())
                                    .build()));
    ConnectorValidationResult connectorValidationResult = terraformCloudConnectorValidator.validate(
        terraformCloudConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }
}
