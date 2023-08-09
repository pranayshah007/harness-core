/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.connector;

import static io.harness.connector.SecretSpecBuilder.RANDOM_LENGTH;
import static io.harness.connector.SecretSpecBuilder.SECRET_KEY;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cistatus.service.GithubService;
import io.harness.connector.ConnectorEnvVariablesHelper;
import io.harness.connector.ImageSecretBuilder;
import io.harness.connector.SecretSpecBuilder;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsCredentialsDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitSecretKeyAccessKeyDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class SecretSpecBuilderTest extends CategoryTest {
  @Mock private SecretDecryptor secretDecryptor;
  @Mock private ConnectorEnvVariablesHelper connectorEnvVariablesHelper;
  @Mock private ImageSecretBuilder imageSecretBuilder;
  @Mock private GithubService githubService;
  @InjectMocks private SecretSpecBuilder secretSpecBuilder;

  private static final String secretName = "foo";
  private static final String imageName = "IMAGE";
  private static final String tag = "TAG";
  private static final String namespace = "default";
  private static final String registryUrl = "https://index.docker.io/v1/";
  private static final String userName = "usr";
  private static final String password = "pwd";
  private static final String gitRepoUrl = "https://github.com/harness/harness-core.git";
  private static final String gitSecretName = "hs-wings-software-portal-hs";
  private static final String encryptedKey = "encryptedKey";
  private static final String passwordRefId = "git_password";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldConvertCustomSecretTextVariables() {
    SecretVariableDetails secretVariableDetails =
        SecretVariableDetails.builder()
            .secretVariableDTO(SecretVariableDTO.builder()
                                   .name("abc")
                                   .type(SecretVariableDTO.Type.TEXT)
                                   .secret(SecretRefData.builder()
                                               .decryptedValue("pass".toCharArray())
                                               .identifier("secret_id")
                                               .scope(Scope.ACCOUNT)
                                               .build())
                                   .build())
            .encryptedDataDetailList(singletonList(
                EncryptedDataDetail.builder()
                    .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.KMS).build())
                    .build()))
            .build();
    when(secretDecryptor.decrypt(
             secretVariableDetails.getSecretVariableDTO(), secretVariableDetails.getEncryptedDataDetailList()))
        .thenReturn(secretVariableDetails.getSecretVariableDTO());
    Map<String, SecretParams> decryptedSecrets =
        secretSpecBuilder.decryptCustomSecretVariables(singletonList(secretVariableDetails), new HashMap<>());
    assertThat(decryptedSecrets.get("abc").getValue()).isEqualTo(encodeBase64("pass"));
    assertThat(decryptedSecrets.get("abc").getSecretKey()).isEqualTo(SECRET_KEY + "abc");
    assertThat(decryptedSecrets.get("abc").getType()).isEqualTo(TEXT);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldConvertCustomSecretVariablesOnlyOnce() {
    SecretVariableDetails secretVariableDetails =
        SecretVariableDetails.builder()
            .secretVariableDTO(SecretVariableDTO.builder()
                                   .name("abc")
                                   .type(SecretVariableDTO.Type.TEXT)
                                   .secret(SecretRefData.builder()
                                               .decryptedValue("pass".toCharArray())
                                               .identifier("secret_id")
                                               .scope(Scope.ACCOUNT)
                                               .build())
                                   .build())
            .encryptedDataDetailList(singletonList(
                EncryptedDataDetail.builder()
                    .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.KMS).build())
                    .build()))
            .build();
    when(secretDecryptor.decrypt(
             secretVariableDetails.getSecretVariableDTO(), secretVariableDetails.getEncryptedDataDetailList()))
        .thenReturn(secretVariableDetails.getSecretVariableDTO());
    Map<String, SecretVariableDTO> cache = new HashMap<>();
    Map<String, SecretParams> decryptedSecrets =
        secretSpecBuilder.decryptCustomSecretVariables(singletonList(secretVariableDetails), cache);
    assertThat(decryptedSecrets.get("abc").getValue()).isEqualTo(encodeBase64("pass"));
    assertThat(decryptedSecrets.get("abc").getSecretKey()).isEqualTo(SECRET_KEY + "abc");
    assertThat(decryptedSecrets.get("abc").getType()).isEqualTo(TEXT);

    decryptedSecrets = secretSpecBuilder.decryptCustomSecretVariables(singletonList(secretVariableDetails), cache);
    assertThat(decryptedSecrets.get("abc").getValue()).isEqualTo(encodeBase64("pass"));
    assertThat(decryptedSecrets.get("abc").getSecretKey()).isEqualTo(SECRET_KEY + "abc");
    assertThat(decryptedSecrets.get("abc").getType()).isEqualTo(TEXT);
    Mockito.verify(secretDecryptor, Mockito.times(1))
        .decrypt(secretVariableDetails.getSecretVariableDTO(), secretVariableDetails.getEncryptedDataDetailList());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldConvertCustomSecretVariablesWithInvalidChar() {
    SecretVariableDetails secretVariableDetails =
        SecretVariableDetails.builder()
            .secretVariableDTO(SecretVariableDTO.builder()
                                   .name("abc//#-")
                                   .type(SecretVariableDTO.Type.TEXT)
                                   .secret(SecretRefData.builder()
                                               .decryptedValue("pass".toCharArray())
                                               .identifier("secret_id")
                                               .scope(Scope.ACCOUNT)
                                               .build())
                                   .build())
            .encryptedDataDetailList(singletonList(
                EncryptedDataDetail.builder()
                    .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.KMS).build())
                    .build()))
            .build();
    when(secretDecryptor.decrypt(
             secretVariableDetails.getSecretVariableDTO(), secretVariableDetails.getEncryptedDataDetailList()))
        .thenReturn(secretVariableDetails.getSecretVariableDTO());
    Map<String, SecretVariableDTO> cache = new HashMap<>();
    Map<String, SecretParams> decryptedSecrets =
        secretSpecBuilder.decryptCustomSecretVariables(singletonList(secretVariableDetails), cache);
    assertThat(decryptedSecrets.get("abc____").getValue()).isEqualTo(encodeBase64("pass"));
    assertThat(decryptedSecrets.get("abc____").getSecretKey()).isEqualTo(SECRET_KEY + "abc____");
    assertThat(decryptedSecrets.get("abc____").getType()).isEqualTo(TEXT);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldConvertCustomSecretFile() {
    SecretVariableDetails secretVariableDetails =
        SecretVariableDetails.builder()
            .secretVariableDTO(SecretVariableDTO.builder()
                                   .name("abc")
                                   .type(SecretVariableDTO.Type.FILE)
                                   .secret(SecretRefData.builder()
                                               .decryptedValue("pass".toCharArray())
                                               .identifier("secret_id")
                                               .scope(Scope.ACCOUNT)
                                               .build())
                                   .build())
            .encryptedDataDetailList(singletonList(
                EncryptedDataDetail.builder()
                    .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.KMS).build())
                    .build()))
            .build();
    when(secretDecryptor.decrypt(
             secretVariableDetails.getSecretVariableDTO(), secretVariableDetails.getEncryptedDataDetailList()))
        .thenReturn(secretVariableDetails.getSecretVariableDTO());
    Map<String, SecretParams> decryptedSecrets =
        secretSpecBuilder.decryptCustomSecretVariables(singletonList(secretVariableDetails), new HashMap<>());
    assertThat(decryptedSecrets.get("abc").getValue()).isEqualTo(encodeBase64("pass"));
    assertThat(decryptedSecrets.get("abc").getSecretKey()).isEqualTo(SECRET_KEY + "abc");
    assertThat(decryptedSecrets.get("abc").getType()).isEqualTo(SecretParams.Type.FILE);
  }

  @Test()
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldDecryptDockerConfig() {
    Map<String, ConnectorDetails> map = new HashMap<>();
    ConnectorDetails setting =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.DOCKER)
            .envToSecretEntry(EnvVariableEnum.DOCKER_USERNAME, "USERNAME_docker")
            .envToSecretEntry(EnvVariableEnum.DOCKER_PASSWORD, "PASSWORD_docker")
            .envToSecretEntry(EnvVariableEnum.DOCKER_REGISTRY, "ENDPOINT_docker")
            .identifier("docker")
            .connectorConfig(
                DockerConnectorDTO.builder()
                    .dockerRegistryUrl("https://index.docker.io/v1/")
                    .auth(DockerAuthenticationDTO.builder()
                              .authType(DockerAuthType.USER_PASSWORD)
                              .credentials(DockerUserNamePasswordDTO.builder().username("username").build())
                              .build())
                    .build())
            .build();
    map.put("docker", setting);

    Map<String, SecretParams> expectedSecretParams = new HashMap<>();
    expectedSecretParams.put("USERNAME_docker",
        SecretParams.builder().secretKey("USERNAME_dockerdocker").value(encodeBase64("username")).build());
    expectedSecretParams.put("PASSWORD_docker",
        SecretParams.builder().secretKey("PASSWORD_dockerdocker").value(encodeBase64("password")).build());
    expectedSecretParams.put("ENDPOINT_docker",
        SecretParams.builder()
            .secretKey("ENDPOINT_dockerdocker")
            .value(encodeBase64("https://index.docker.io/v1/"))
            .build());

    when(connectorEnvVariablesHelper.getDockerSecretVariables(any())).thenReturn(expectedSecretParams);
    Map<String, String> data = secretSpecBuilder.decryptConnectorSecretVariables(map).values().stream().collect(
        Collectors.toMap(SecretParams::getSecretKey, SecretParams::getValue));
    assertThat(data)
        .containsKeys("USERNAME_dockerdocker", "PASSWORD_dockerdocker", "ENDPOINT_dockerdocker")
        .containsEntry("USERNAME_dockerdocker", encodeBase64("username"))
        .containsEntry("PASSWORD_dockerdocker", encodeBase64("password"))
        .containsEntry("ENDPOINT_dockerdocker", encodeBase64("https://index.docker.io/v1/"));
  }

  @Test()
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldDecryptGitSecretVariablesForAwsCodeCommitConnector() {
    AwsCodeCommitSecretKeyAccessKeyDTO awsCodeCommitSecretKeyAccessKeyDTO =
        AwsCodeCommitSecretKeyAccessKeyDTO.builder()
            .accessKey("AKIAIOSFODNN7EXAMPLE")
            .secretKeyRef(SecretRefData.builder()
                              .identifier("secretKeyRefIdentifier")
                              .scope(Scope.ACCOUNT)
                              .decryptedValue("S3CR3TKEYEXAMPLE".toCharArray())
                              .build())
            .build();
    AwsCodeCommitConnectorDTO awsCodeCommitConnectorDTO =
        AwsCodeCommitConnectorDTO.builder()
            .url("https://git-codecommit.eu-central-1.amazonaws.com/v1/repos/test")
            .urlType(AwsCodeCommitUrlType.REPO)
            .authentication(AwsCodeCommitAuthenticationDTO.builder()
                                .authType(AwsCodeCommitAuthType.HTTPS)
                                .credentials(AwsCodeCommitHttpsCredentialsDTO.builder()
                                                 .type(AwsCodeCommitHttpsAuthType.ACCESS_KEY_AND_SECRET_KEY)
                                                 .httpCredentialsSpec(awsCodeCommitSecretKeyAccessKeyDTO)
                                                 .build())

                                .build())
            .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.CODECOMMIT)
                                            .connectorConfig(awsCodeCommitConnectorDTO)
                                            .build();
    when(secretDecryptor.decrypt(eq(awsCodeCommitConnectorDTO), any())).thenReturn(awsCodeCommitConnectorDTO);
    Map<String, SecretParams> gitSecretVariables = secretSpecBuilder.decryptGitSecretVariables(connectorDetails);
    assertThat(gitSecretVariables).containsOnlyKeys("DRONE_AWS_ACCESS_KEY", "DRONE_AWS_SECRET_KEY");
    assertThat(gitSecretVariables.get("DRONE_AWS_ACCESS_KEY"))
        .isEqualTo(SecretParams.builder()
                       .secretKey("DRONE_AWS_ACCESS_KEY")
                       .value(encodeBase64("AKIAIOSFODNN7EXAMPLE"))
                       .type(TEXT)
                       .build());
    assertThat(gitSecretVariables.get("DRONE_AWS_SECRET_KEY"))
        .isEqualTo(SecretParams.builder()
                       .secretKey("DRONE_AWS_SECRET_KEY")
                       .value(encodeBase64("S3CR3TKEYEXAMPLE"))
                       .type(TEXT)
                       .build());
  }

  @Test()
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldDecryptGitSecretVariablesForAzureRepoConnector() {
    AzureRepoUsernameTokenDTO azureRepoUsernameTokenDTO =
        AzureRepoUsernameTokenDTO.builder()
            .username("username")
            .tokenRef(SecretRefData.builder()
                          .identifier("secretKeyRefIdentifier")
                          .scope(Scope.ACCOUNT)
                          .decryptedValue("S3CR3TKEYEXAMPLE".toCharArray())
                          .build())
            .build();
    AzureRepoConnectorDTO azureRepoConnectorDTO =
        AzureRepoConnectorDTO.builder()
            .url("https://dev.azure.com/org/project/repo")
            .connectionType(AzureRepoConnectionTypeDTO.REPO)
            .authentication(AzureRepoAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(AzureRepoHttpCredentialsDTO.builder()
                                                 .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                 .httpCredentialsSpec(azureRepoUsernameTokenDTO)
                                                 .build())

                                .build())
            .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AZURE_REPO)
                                            .connectorConfig(azureRepoConnectorDTO)
                                            .build();
    when(secretDecryptor.decrypt(any(), any())).thenReturn(azureRepoUsernameTokenDTO);
    Map<String, SecretParams> gitSecretVariables = secretSpecBuilder.decryptGitSecretVariables(connectorDetails);
    assertThat(gitSecretVariables).containsOnlyKeys("DRONE_NETRC_USERNAME", "DRONE_NETRC_PASSWORD");

    final SecretParams droneNetrcUsername = gitSecretVariables.get("DRONE_NETRC_USERNAME");
    final String usernameSecretKey = droneNetrcUsername.getSecretKey();
    final String usernamePrefix = "DRONE_NETRC_USERNAME_";
    assertThat(usernameSecretKey.startsWith(usernamePrefix));
    assertThat(usernameSecretKey.length()).isEqualTo(usernamePrefix.length() + RANDOM_LENGTH);

    final SecretParams droneNetrcPassword = gitSecretVariables.get("DRONE_NETRC_PASSWORD");
    final String passwordSecretKey = droneNetrcPassword.getSecretKey();
    final String passwordPrefix = "DRONE_NETRC_PASSWORD_";
    assertThat(passwordSecretKey.startsWith(passwordPrefix));
    assertThat(passwordSecretKey.length()).isEqualTo(passwordPrefix.length() + RANDOM_LENGTH);

    assertThat(droneNetrcUsername)
        .isEqualTo(
            SecretParams.builder().secretKey(usernameSecretKey).value(encodeBase64("username")).type(TEXT).build());
    assertThat(droneNetrcPassword)
        .isEqualTo(SecretParams.builder()
                       .secretKey(passwordSecretKey)
                       .value(encodeBase64("S3CR3TKEYEXAMPLE"))
                       .type(TEXT)
                       .build());
  }

  @Test()
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldDecryptGitSecretVariablesForGithubAPP() {
    GithubAppDTO githubAppDTO = GithubAppDTO.builder()
                                    .installationId("id")
                                    .applicationId("app")
                                    .privateKeyRef(SecretRefData.builder().decryptedValue("key".toCharArray()).build())
                                    .build();
    GithubHttpCredentialsDTO credentials = GithubHttpCredentialsDTO.builder()
                                               .type(GithubHttpAuthenticationType.GITHUB_APP)
                                               .httpCredentialsSpec(githubAppDTO)
                                               .build();
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .url("https://github.com/account/repo")
            .connectionType(GitConnectionType.REPO)
            .authentication(
                GithubAuthenticationDTO.builder().credentials(credentials).authType(GitAuthType.HTTP).build())
            .build();
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).connectorConfig(githubConnectorDTO).build();
    when(secretDecryptor.decrypt(any(), any())).thenReturn(githubAppDTO);
    when(githubService.getToken(any())).thenReturn("token");
    Map<String, SecretParams> gitSecretVariables = secretSpecBuilder.decryptGitSecretVariables(connectorDetails);
    assertThat(gitSecretVariables).containsOnlyKeys("DRONE_NETRC_USERNAME", "DRONE_NETRC_PASSWORD");

    final SecretParams droneNetrcUsername = gitSecretVariables.get("DRONE_NETRC_USERNAME");
    final String usernameSecretKey = droneNetrcUsername.getSecretKey();
    final String usernamePrefix = "DRONE_NETRC_USERNAME_";
    assertThat(usernameSecretKey.startsWith(usernamePrefix));
    assertThat(usernameSecretKey.length()).isEqualTo(usernamePrefix.length() + RANDOM_LENGTH);

    final SecretParams droneNetrcPassword = gitSecretVariables.get("DRONE_NETRC_PASSWORD");
    final String passwordSecretKey = droneNetrcPassword.getSecretKey();
    final String passwordPrefix = "DRONE_NETRC_PASSWORD_";
    assertThat(passwordSecretKey.startsWith(passwordPrefix));
    assertThat(passwordSecretKey.length()).isEqualTo(passwordPrefix.length() + RANDOM_LENGTH);

    assertThat(droneNetrcUsername)
        .isEqualTo(SecretParams.builder()
                       .secretKey(usernameSecretKey)
                       .value(encodeBase64(GithubAppDTO.username))
                       .type(TEXT)
                       .build());
    assertThat(droneNetrcPassword)
        .isEqualTo(SecretParams.builder().secretKey(passwordSecretKey).value(encodeBase64("token")).type(TEXT).build());
  }
}
