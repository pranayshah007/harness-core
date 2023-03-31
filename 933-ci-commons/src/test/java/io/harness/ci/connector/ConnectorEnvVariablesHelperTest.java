/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.connector;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorEnvVariablesHelper;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

public class ConnectorEnvVariablesHelperTest extends CategoryTest {
  private static final String USERNAME_ENV = "USERNAME";
  private static final String SECRET_ENV = "SECRET";
  private static final String ENDPOINT_ENV = "ENDPOINT";
  private static final String ACCESS_KEY_ENV = "ACCESS_KEY";
  private static final String SECRET_KEY_ENV = "SECRET_KEY";
  private static final String REGISTRY_ENV = "REGISTRY";
  private static final String GCP_KEY_ENV = "GCP_KEY";

  private static final String CONNECTOR_ID = "id";
  private static final String SERVER_URL = "https://harness.io";
  private static final String USERNAME_VALUE = "username";
  private static final String SECRET_VALUE = "s3cret";
  private static final String GCP_KEY_CONTENTS = "{\"key\":\"s3cret\"}";
  private static final String AWS_SECRET = "s3cret?k3y";
  private static final String AWS_ACCESS = "acc3ss?k3y";

  @Mock SecretDecryptor secretDecryptor;
  @InjectMocks ConnectorEnvVariablesHelper connectorEnvVariablesHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(secretDecryptor.decrypt(any(DecryptableEntity.class), any()))
        .thenAnswer((Answer<DecryptableEntity>) invocation -> {
          Object[] args = invocation.getArguments();
          return (DecryptableEntity) args[0];
        });
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldGetArtifactorySecretVariablesWithUserName() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_USERNAME, USERNAME_ENV)
            .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_PASSWORD, SECRET_ENV)
            .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_ENDPOINT, ENDPOINT_ENV)
            .identifier(CONNECTOR_ID)
            .connectorType(ConnectorType.ARTIFACTORY)
            .connectorConfig(
                ArtifactoryConnectorDTO.builder()
                    .artifactoryServerUrl(SERVER_URL)
                    .auth(ArtifactoryAuthenticationDTO.builder()
                              .authType(ArtifactoryAuthType.USER_PASSWORD)
                              .credentials(
                                  ArtifactoryUsernamePasswordAuthDTO.builder()
                                      .usernameRef(
                                          SecretRefData.builder().decryptedValue(USERNAME_VALUE.toCharArray()).build())
                                      .passwordRef(
                                          SecretRefData.builder().decryptedValue(SECRET_VALUE.toCharArray()).build())
                                      .build())
                              .build())
                    .build())
            .build();
    Map<String, SecretParams> actualSecretVariables =
        connectorEnvVariablesHelper.getArtifactorySecretVariables(connectorDetails);
    Map<String, SecretParams> expectedSecretVariables = new HashMap<>();
    expectedSecretVariables.put(ENDPOINT_ENV,
        SecretParams.builder()
            .value(encodeBase64(SERVER_URL))
            .secretKey(ENDPOINT_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    expectedSecretVariables.put(USERNAME_ENV,
        SecretParams.builder()
            .value(encodeBase64(USERNAME_VALUE))
            .secretKey(USERNAME_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    expectedSecretVariables.put(SECRET_ENV,
        SecretParams.builder()
            .value(encodeBase64(SECRET_VALUE))
            .secretKey(SECRET_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    assertThat(actualSecretVariables).isEqualTo(expectedSecretVariables);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetArtifactorySecretVariables() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_USERNAME, USERNAME_ENV)
            .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_PASSWORD, SECRET_ENV)
            .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_ENDPOINT, ENDPOINT_ENV)
            .identifier(CONNECTOR_ID)
            .connectorType(ConnectorType.ARTIFACTORY)
            .connectorConfig(
                ArtifactoryConnectorDTO.builder()
                    .artifactoryServerUrl(SERVER_URL)
                    .auth(ArtifactoryAuthenticationDTO.builder()
                              .authType(ArtifactoryAuthType.USER_PASSWORD)
                              .credentials(
                                  ArtifactoryUsernamePasswordAuthDTO.builder()
                                      .username(USERNAME_VALUE)
                                      .passwordRef(
                                          SecretRefData.builder().decryptedValue(SECRET_VALUE.toCharArray()).build())
                                      .build())
                              .build())
                    .build())
            .build();
    Map<String, SecretParams> actualSecretVariables =
        connectorEnvVariablesHelper.getArtifactorySecretVariables(connectorDetails);
    Map<String, SecretParams> expectedSecretVariables = new HashMap<>();
    expectedSecretVariables.put(ENDPOINT_ENV,
        SecretParams.builder()
            .value(encodeBase64(SERVER_URL))
            .secretKey(ENDPOINT_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    expectedSecretVariables.put(USERNAME_ENV,
        SecretParams.builder()
            .value(encodeBase64(USERNAME_VALUE))
            .secretKey(USERNAME_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    expectedSecretVariables.put(SECRET_ENV,
        SecretParams.builder()
            .value(encodeBase64(SECRET_VALUE))
            .secretKey(SECRET_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    assertThat(actualSecretVariables).isEqualTo(expectedSecretVariables);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetGcpSecretVariables() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .envToSecretEntry(EnvVariableEnum.GCP_KEY, GCP_KEY_ENV)
            .identifier(CONNECTOR_ID)
            .connectorType(ConnectorType.GCP)
            .connectorConfig(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(GCP_KEY_CONTENTS.toCharArray()).build())
                                    .build())
                            .build())
                    .build())
            .build();
    Map<String, SecretParams> actualSecretVariables =
        connectorEnvVariablesHelper.getGcpSecretVariables(connectorDetails);
    Map<String, SecretParams> expectedSecretVariables = new HashMap<>();
    expectedSecretVariables.put(GCP_KEY_ENV,
        SecretParams.builder()
            .value(encodeBase64(GCP_KEY_CONTENTS))
            .secretKey(GCP_KEY_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    assertThat(actualSecretVariables).isEqualTo(expectedSecretVariables);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetAwsSecretVariables() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .envToSecretEntry(EnvVariableEnum.AWS_ACCESS_KEY, ACCESS_KEY_ENV)
            .envToSecretEntry(EnvVariableEnum.AWS_SECRET_KEY, SECRET_KEY_ENV)
            .identifier(CONNECTOR_ID)
            .connectorType(ConnectorType.AWS)
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(
                        AwsCredentialDTO.builder()
                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                            .config(AwsManualConfigSpecDTO.builder()
                                        .accessKey(AWS_ACCESS)
                                        .secretKeyRef(
                                            SecretRefData.builder().decryptedValue(AWS_SECRET.toCharArray()).build())
                                        .build())
                            .build())
                    .build())
            .build();
    Map<String, SecretParams> actualSecretVariables =
        connectorEnvVariablesHelper.getAwsSecretVariables(connectorDetails);
    Map<String, SecretParams> expectedSecretVariables = new HashMap<>();
    expectedSecretVariables.put(ACCESS_KEY_ENV,
        SecretParams.builder()
            .value(encodeBase64(AWS_ACCESS))
            .secretKey(ACCESS_KEY_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    expectedSecretVariables.put(SECRET_KEY_ENV,
        SecretParams.builder()
            .value(encodeBase64(AWS_SECRET))
            .secretKey(SECRET_KEY_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    assertThat(actualSecretVariables).isEqualTo(expectedSecretVariables);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetDockerSecretVariablesWithSecretUserName() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .identifier(CONNECTOR_ID)
            .envToSecretEntry(EnvVariableEnum.DOCKER_USERNAME, USERNAME_ENV)
            .envToSecretEntry(EnvVariableEnum.DOCKER_PASSWORD, SECRET_ENV)
            .envToSecretEntry(EnvVariableEnum.DOCKER_REGISTRY, REGISTRY_ENV)
            .connectorType(ConnectorType.DOCKER)
            .connectorConfig(
                DockerConnectorDTO.builder()
                    .dockerRegistryUrl(SERVER_URL)
                    .auth(DockerAuthenticationDTO.builder()
                              .authType(DockerAuthType.USER_PASSWORD)
                              .credentials(
                                  DockerUserNamePasswordDTO.builder()
                                      .usernameRef(
                                          SecretRefData.builder().decryptedValue(USERNAME_VALUE.toCharArray()).build())
                                      .passwordRef(
                                          SecretRefData.builder().decryptedValue(SECRET_VALUE.toCharArray()).build())
                                      .build())
                              .build())
                    .build())
            .build();
    Map<String, SecretParams> actualSecretVariables =
        connectorEnvVariablesHelper.getDockerSecretVariables(connectorDetails);
    Map<String, SecretParams> expectedSecretVariables = new HashMap<>();
    expectedSecretVariables.put(USERNAME_ENV,
        SecretParams.builder()
            .value(encodeBase64(USERNAME_VALUE))
            .secretKey(USERNAME_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    expectedSecretVariables.put(SECRET_ENV,
        SecretParams.builder()
            .value(encodeBase64(SECRET_VALUE))
            .secretKey(SECRET_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    expectedSecretVariables.put(REGISTRY_ENV,
        SecretParams.builder()
            .value(encodeBase64(SERVER_URL))
            .secretKey(REGISTRY_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    assertThat(actualSecretVariables).isEqualTo(expectedSecretVariables);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetDockerSecretVariables() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .identifier(CONNECTOR_ID)
            .envToSecretEntry(EnvVariableEnum.DOCKER_USERNAME, USERNAME_ENV)
            .envToSecretEntry(EnvVariableEnum.DOCKER_PASSWORD, SECRET_ENV)
            .envToSecretEntry(EnvVariableEnum.DOCKER_REGISTRY, REGISTRY_ENV)
            .connectorType(ConnectorType.DOCKER)
            .connectorConfig(
                DockerConnectorDTO.builder()
                    .dockerRegistryUrl(SERVER_URL)
                    .auth(DockerAuthenticationDTO.builder()
                              .authType(DockerAuthType.USER_PASSWORD)
                              .credentials(
                                  DockerUserNamePasswordDTO.builder()
                                      .username(USERNAME_VALUE)
                                      .passwordRef(
                                          SecretRefData.builder().decryptedValue(SECRET_VALUE.toCharArray()).build())
                                      .build())
                              .build())
                    .build())
            .build();
    Map<String, SecretParams> actualSecretVariables =
        connectorEnvVariablesHelper.getDockerSecretVariables(connectorDetails);
    Map<String, SecretParams> expectedSecretVariables = new HashMap<>();
    expectedSecretVariables.put(USERNAME_ENV,
        SecretParams.builder()
            .value(encodeBase64(USERNAME_VALUE))
            .secretKey(USERNAME_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    expectedSecretVariables.put(SECRET_ENV,
        SecretParams.builder()
            .value(encodeBase64(SECRET_VALUE))
            .secretKey(SECRET_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    expectedSecretVariables.put(REGISTRY_ENV,
        SecretParams.builder()
            .value(encodeBase64(SERVER_URL))
            .secretKey(REGISTRY_ENV + CONNECTOR_ID)
            .type(TEXT)
            .build());
    assertThat(actualSecretVariables).isEqualTo(expectedSecretVariables);
  }
}
