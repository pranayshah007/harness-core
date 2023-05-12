/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.connector.ConnectivityStatus.SUCCESS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.heartbeat.ArtifactoryValidationParamsProvider;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.artifactory.ArtifactoryRequestMapper;
import io.harness.connector.task.artifactory.ArtifactoryValidationHandler;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthCredentialsDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

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
public class ArtifactoryConnectionValidatorTest extends CategoryTest {
  @Mock private EncryptionHelper encryptionHelper;
  @Mock private ConnectorService connectorService;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private ArtifactoryClientImpl artifactoryService;
  ArtifactoryRequestMapper artifactoryRequestMapper = new ArtifactoryRequestMapper();
  @Mock private Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Mock private Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap;
  @InjectMocks private ArtifactoryConnectionValidator artifactoryConnectionValidator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.DEV_MITTAL)
  @Category(UnitTests.class)
  public void validateTestViaManager() {
    String artifactoryUrl = "url";
    String artifactoryUserName = "ArtifactoryUserName";
    String passwordRefIdentifier = "passwordRefIdentifier";
    SecretRefData passwordSecretRef = SecretRefData.builder()
                                          .identifier(passwordRefIdentifier)
                                          .scope(Scope.ACCOUNT)
                                          .decryptedValue("password".toCharArray())
                                          .build();

    ArtifactoryAuthCredentialsDTO artifactoryAuthCredentialsDTO = ArtifactoryUsernamePasswordAuthDTO.builder()
                                                                      .username(artifactoryUserName)
                                                                      .passwordRef(passwordSecretRef)
                                                                      .build();

    ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO = ArtifactoryAuthenticationDTO.builder()
                                                                    .authType(ArtifactoryAuthType.USER_PASSWORD)
                                                                    .credentials(artifactoryAuthCredentialsDTO)
                                                                    .build();
    ArtifactoryConnectorDTO artifactoryConnectorDTO = ArtifactoryConnectorDTO.builder()
                                                          .artifactoryServerUrl(artifactoryUrl)
                                                          .auth(artifactoryAuthenticationDTO)
                                                          .executeOnDelegate(false)
                                                          .build();
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.ARTIFACTORY)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .build())
                                    .build()));

    ArtifactoryValidationHandler artifactoryValidationHandler = mock(ArtifactoryValidationHandler.class);
    when(artifactoryService.validateArtifactServer(any())).thenReturn(true);
    on(artifactoryValidationHandler).set("decryptionHelper", decryptionHelper);
    on(artifactoryValidationHandler).set("artifactoryService", artifactoryService);
    on(artifactoryValidationHandler).set("artifactoryRequestMapper", artifactoryRequestMapper);
    when(artifactoryValidationHandler.validate(any(), any())).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(ArgumentMatchers.eq("Artifactory")))
        .thenReturn(artifactoryValidationHandler);

    ArtifactoryValidationParamsProvider artifactoryValidationParamsProvider = new ArtifactoryValidationParamsProvider();
    on(artifactoryValidationParamsProvider).set("encryptionHelper", encryptionHelper);
    when(connectorValidationParamsProviderMap.get(ArgumentMatchers.eq("Artifactory")))
        .thenReturn(artifactoryValidationParamsProvider);

    ConnectorValidationResult validationResult = artifactoryConnectionValidator.validate(
        artifactoryConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    assertThat(validationResult.getStatus()).isEqualTo(SUCCESS);
  }
}
