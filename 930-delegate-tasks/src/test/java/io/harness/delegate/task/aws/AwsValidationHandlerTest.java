/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.aws.AwsClient;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.aws.AwsNgConfigMapper;
import io.harness.connector.task.aws.AwsValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsValidationParams;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.HintException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AwsValidationHandlerTest extends CategoryTest {
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private NGErrorHelper ngErrorHelper;
  @Mock private AwsClient awsClient;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @InjectMocks AwsValidationHandler awsValidationHandler;
  @InjectMocks ExceptionManager exceptionManager;
  private final String accountIdentifier = "accountIdentifier";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    on(awsValidationHandler).set("exceptionManager", exceptionManager);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testValidateSuccess() {
    String secretKeyRefIdentifier = "secretKeyRefIdentifier";
    String secretKey = "secretKey";
    SecretRefData passwordSecretRef = SecretRefData.builder()
                                          .identifier(secretKeyRefIdentifier)
                                          .scope(Scope.ACCOUNT)
                                          .decryptedValue(secretKey.toCharArray())
                                          .build();
    AwsManualConfigSpecDTO awsManualConfigSpecDTO =
        AwsManualConfigSpecDTO.builder().accessKey("testAccessKey").secretKeyRef(passwordSecretRef).build();

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                            .config(awsManualConfigSpecDTO)
                                            .build();

    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();

    ConnectorValidationParams connectorValidationParams = AwsValidationParams.builder()
                                                              .awsConnectorDTO(awsConnectorDTO)
                                                              .connectorName("TestAWSName")
                                                              .encryptedDataDetails(null)
                                                              .build();

    ConnectorValidationResult result = awsValidationHandler.validate(connectorValidationParams, accountIdentifier);
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testValidateFailure() {
    doThrow(new RuntimeException("No Credentials found")).when(awsClient).validateAwsAccountCredential(any(), any());
    String secretKeyRefIdentifier = "secretKeyRefIdentifier";
    String secretKey = "secretKey";
    SecretRefData passwordSecretRef = SecretRefData.builder()
                                          .identifier(secretKeyRefIdentifier)
                                          .scope(Scope.ACCOUNT)
                                          .decryptedValue(secretKey.toCharArray())
                                          .build();
    AwsManualConfigSpecDTO awsManualConfigSpecDTO =
        AwsManualConfigSpecDTO.builder().accessKey("testAccessKey").secretKeyRef(passwordSecretRef).build();

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                            .config(awsManualConfigSpecDTO)
                                            .build();

    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();

    ConnectorValidationParams connectorValidationParams = AwsValidationParams.builder()
                                                              .awsConnectorDTO(awsConnectorDTO)
                                                              .connectorName("TestAWSName")
                                                              .encryptedDataDetails(null)
                                                              .build();

    assertThatThrownBy(() -> awsValidationHandler.validate(connectorValidationParams, accountIdentifier))
        .isInstanceOf(HintException.class);
  }
}
