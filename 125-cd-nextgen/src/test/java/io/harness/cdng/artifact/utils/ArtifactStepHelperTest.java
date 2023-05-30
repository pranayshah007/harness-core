/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.utils;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.connector.entities.embedded.githubconnector.GithubConnector;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactStepHelperTest extends CDNGTestBase {
  @InjectMocks private ArtifactStepHelper artifactStepHelper;
  @Mock private SecretManagerClientService secretManagerClientService;
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  private static final GithubTokenSpecDTO GITHUB_TOKEN_SPEC_DTO = GithubTokenSpecDTO.builder().build();
  private static final GithubUsernamePasswordDTO GITHUB_USERNAME_PASSWORD_DTO =
      GithubUsernamePasswordDTO.builder().build();
  private static final GithubUsernameTokenDTO GITHUB_USERNAME_TOKEN_DTO = GithubUsernameTokenDTO.builder().build();
  private static final EncryptedDataDetail ENCRYPTED_DATA_DETAIL_TOKEN_API_ACCESS =
      EncryptedDataDetail.builder().fieldName("tokenRef").build();
  private static final EncryptedDataDetail ENCRYPTED_DATA_DETAIL_TOKEN_AUTH =
      EncryptedDataDetail.builder().fieldName("tokenRef").build();
  private static final EncryptedDataDetail ENCRYPTED_DATA_DETAIL_USERNAME =
      EncryptedDataDetail.builder().fieldName("usernameRef").build();
  private static final GithubConnector GITHUB_CONNECTOR = GithubConnector.builder().build();

  String serviceEntityYaml = "service:\n"
      + "  name: nginx\n"
      + "  identifier: id\n"
      + "  tags: {}\n"
      + "  serviceDefinition:\n"
      + "    spec:\n"
      + "      artifacts:\n"
      + "        primary:\n"
      + "          primaryArtifactRef: <+input>\n"
      + "          sources:\n"
      + "            - spec:\n"
      + "                connectorRef: docker_hub\n"
      + "                imagePath: library/nginx\n"
      + "                tag: <+input>\n"
      + "              identifier: nginx\n"
      + "              type: DockerRegistry\n"
      + "    type: Kubernetes";

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlWithOneArtifactAndPrimaryArtifactRefSet() throws IOException {
    YamlField yamlField =
        YamlUtils.readTree(artifactStepHelper.processArtifactsInYaml(ambiance, serviceEntityYaml).getServiceYaml());
    YamlField serviceDefField =
        yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY).getNode().getField(YamlTypes.SERVICE_DEFINITION);
    YamlField serviceSpecField = serviceDefField.getNode().getField(YamlTypes.SERVICE_SPEC);
    YamlField artifactsField = serviceSpecField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    YamlField primaryArtifactField = artifactsField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT);
    assertThat(yamlField).isNotNull();
    assertThat(primaryArtifactField).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlWithOneArtifactAndPrimaryArtifactRefNull() throws IOException {
    YamlField yamlField = YamlUtils.readTree(
        artifactStepHelper.processArtifactsInYaml(ambiance, getServiceEntityYamlWithNullPrimaryArtifact())
            .getServiceYaml());
    YamlField serviceDefField =
        yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY).getNode().getField(YamlTypes.SERVICE_DEFINITION);
    YamlField serviceSpecField = serviceDefField.getNode().getField(YamlTypes.SERVICE_SPEC);
    YamlField artifactsField = serviceSpecField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    YamlField primaryArtifactField = artifactsField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT);
    assertThat(yamlField).isNotNull();
    assertThat(primaryArtifactField).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlWithMultipleArtifactAndPrimaryArtifactRefSet() throws IOException {
    YamlField yamlField = YamlUtils.readTree(
        artifactStepHelper
            .processArtifactsInYaml(ambiance, getServiceEntityYamlWithMultipleSourcesAndPrimaryArtifactSet())
            .getServiceYaml());
    YamlField serviceDefField =
        yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY).getNode().getField(YamlTypes.SERVICE_DEFINITION);
    YamlField serviceSpecField = serviceDefField.getNode().getField(YamlTypes.SERVICE_SPEC);
    YamlField artifactsField = serviceSpecField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    assertThat(yamlField).isNotNull();
    assertThat(artifactsField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT)).isNotNull();
    assertThat(artifactsField.getNode()
                   .getField(YamlTypes.PRIMARY_ARTIFACT)
                   .getNode()
                   .getCurrJsonNode()
                   .get("spec")
                   .get("imagePath")
                   .asText())
        .isEqualTo("library/nginx");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlWithMultipleArtifactAndPrimaryArtifactRefIsNull() {
    assertThatThrownBy(()
                           -> artifactStepHelper.processArtifactsInYaml(
                               ambiance, getServiceEntityYamlWithMultipleArtifactSourcesAndPrimaryArtifactRefIsNull()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetGithubEncryptedDetails_UsernameAndToken() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .apiAccess(GithubApiAccessDTO.builder().type(GithubApiAccessType.TOKEN).spec(GITHUB_TOKEN_SPEC_DTO).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                 .httpCredentialsSpec(GITHUB_USERNAME_TOKEN_DTO)
                                                 .build())
                                .build())
            .build();
    List<EncryptedDataDetail> encryptedDataDetailListAPIAccess = new ArrayList<>();
    encryptedDataDetailListAPIAccess.add(ENCRYPTED_DATA_DETAIL_TOKEN_API_ACCESS);
    Mockito.when(secretManagerClientService.getEncryptionDetails(GITHUB_CONNECTOR, GITHUB_TOKEN_SPEC_DTO))
        .thenReturn(encryptedDataDetailListAPIAccess);
    Mockito.when(secretManagerClientService.getEncryptionDetails(GITHUB_CONNECTOR, GITHUB_USERNAME_TOKEN_DTO))
        .thenReturn(Arrays.asList(ENCRYPTED_DATA_DETAIL_TOKEN_AUTH, ENCRYPTED_DATA_DETAIL_USERNAME));
    List<EncryptedDataDetail> encryptedDataDetails =
        artifactStepHelper.getGithubEncryptedDetails(githubConnectorDTO, GITHUB_CONNECTOR);
    assertThat(encryptedDataDetails.size()).isEqualTo(2);
    assertThat(encryptedDataDetails.get(0)).isSameAs(ENCRYPTED_DATA_DETAIL_TOKEN_API_ACCESS);
    assertThat(encryptedDataDetails.get(1)).isSameAs(ENCRYPTED_DATA_DETAIL_USERNAME);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetGithubEncryptedDetails_UsernameAndPassword() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .apiAccess(GithubApiAccessDTO.builder().type(GithubApiAccessType.TOKEN).spec(GITHUB_TOKEN_SPEC_DTO).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GITHUB_USERNAME_PASSWORD_DTO)
                                                 .build())
                                .build())
            .build();
    List<EncryptedDataDetail> encryptedDataDetailListAPIAccess = new ArrayList<>();
    encryptedDataDetailListAPIAccess.add(ENCRYPTED_DATA_DETAIL_TOKEN_API_ACCESS);
    Mockito.when(secretManagerClientService.getEncryptionDetails(GITHUB_CONNECTOR, GITHUB_TOKEN_SPEC_DTO))
        .thenReturn(encryptedDataDetailListAPIAccess);
    Mockito.when(secretManagerClientService.getEncryptionDetails(GITHUB_CONNECTOR, GITHUB_USERNAME_PASSWORD_DTO))
        .thenReturn(Arrays.asList(ENCRYPTED_DATA_DETAIL_TOKEN_AUTH, ENCRYPTED_DATA_DETAIL_USERNAME));
    List<EncryptedDataDetail> encryptedDataDetails =
        artifactStepHelper.getGithubEncryptedDetails(githubConnectorDTO, GITHUB_CONNECTOR);
    assertThat(encryptedDataDetails.size()).isEqualTo(2);
    assertThat(encryptedDataDetails.get(0)).isSameAs(ENCRYPTED_DATA_DETAIL_TOKEN_API_ACCESS);
    assertThat(encryptedDataDetails.get(1)).isSameAs(ENCRYPTED_DATA_DETAIL_USERNAME);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetGithubEncryptedDetails_NonHttp() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .apiAccess(GithubApiAccessDTO.builder().type(GithubApiAccessType.TOKEN).spec(GITHUB_TOKEN_SPEC_DTO).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.SSH)
                                .credentials(GithubSshCredentialsDTO.builder().build())
                                .build())
            .build();
    List<EncryptedDataDetail> encryptedDataDetailListAPIAccess = new ArrayList<>();
    encryptedDataDetailListAPIAccess.add(ENCRYPTED_DATA_DETAIL_TOKEN_API_ACCESS);
    Mockito.when(secretManagerClientService.getEncryptionDetails(GITHUB_CONNECTOR, GITHUB_TOKEN_SPEC_DTO))
        .thenReturn(encryptedDataDetailListAPIAccess);
    List<EncryptedDataDetail> encryptedDataDetails =
        artifactStepHelper.getGithubEncryptedDetails(githubConnectorDTO, GITHUB_CONNECTOR);
    assertThat(encryptedDataDetails.size()).isEqualTo(1);
    assertThat(encryptedDataDetails.get(0)).isSameAs(ENCRYPTED_DATA_DETAIL_TOKEN_API_ACCESS);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetGithubEncryptedDetails_NullAuth() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .apiAccess(GithubApiAccessDTO.builder().type(GithubApiAccessType.TOKEN).spec(GITHUB_TOKEN_SPEC_DTO).build())
            .build();
    List<EncryptedDataDetail> encryptedDataDetailListAPIAccess = new ArrayList<>();
    encryptedDataDetailListAPIAccess.add(ENCRYPTED_DATA_DETAIL_TOKEN_API_ACCESS);
    Mockito.when(secretManagerClientService.getEncryptionDetails(GITHUB_CONNECTOR, GITHUB_TOKEN_SPEC_DTO))
        .thenReturn(encryptedDataDetailListAPIAccess);
    List<EncryptedDataDetail> encryptedDataDetails =
        artifactStepHelper.getGithubEncryptedDetails(githubConnectorDTO, GITHUB_CONNECTOR);
    assertThat(encryptedDataDetails.size()).isEqualTo(1);
    assertThat(encryptedDataDetails.get(0)).isSameAs(ENCRYPTED_DATA_DETAIL_TOKEN_API_ACCESS);
  }

  @NotNull
  private static String getServiceEntityYamlWithMultipleArtifactSourcesAndPrimaryArtifactRefIsNull() {
    return "service:\n"
        + "  name: nginx\n"
        + "  identifier: id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          sources:\n"
        + "            - spec:\n"
        + "                connectorRef: docker_hub\n"
        + "                imagePath: library/nginx\n"
        + "                tag: <+input>\n"
        + "              identifier: nginx\n"
        + "              type: DockerRegistry\n"
        + "            - spec:\n"
        + "                connectorRef: docker_hub\n"
        + "                imagePath: library/busybox\n"
        + "                tag: <+input>\n"
        + "              identifier: busybox\n"
        + "              type: DockerRegistry\n"
        + "    type: Kubernetes";
  }

  @NotNull
  private static String getServiceEntityYamlWithNullPrimaryArtifact() {
    String serviceEntityYamlWithNullPrimaryArtifact = "service:\n"
        + "  name: nginx\n"
        + "  identifier: id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          sources:\n"
        + "            - spec:\n"
        + "                connectorRef: docker_hub\n"
        + "                imagePath: library/nginx\n"
        + "                tag: <+input>\n"
        + "              identifier: nginx\n"
        + "              type: DockerRegistry\n"
        + "    type: Kubernetes";
    return serviceEntityYamlWithNullPrimaryArtifact;
  }

  private static String getServiceEntityYamlWithMultipleSourcesAndPrimaryArtifactSet() {
    return "service:\n"
        + "  name: nginx\n"
        + "  identifier: id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: nginx\n"
        + "          sources:\n"
        + "            - spec:\n"
        + "                connectorRef: docker_hub\n"
        + "                imagePath: library/nginx\n"
        + "                tag: <+input>\n"
        + "              identifier: nginx\n"
        + "              type: DockerRegistry\n"
        + "            - spec:\n"
        + "                connectorRef: docker_hub\n"
        + "                imagePath: library/busybox\n"
        + "                tag: <+input>\n"
        + "              identifier: busybox\n"
        + "              type: DockerRegistry\n"
        + "    type: Kubernetes";
  }
}
