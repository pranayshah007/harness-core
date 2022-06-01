/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng;

import static io.harness.beans.FeatureName.OPTIMIZED_GIT_FETCH_FILES;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VIKAS_S;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome.K8sDirectInfrastructureOutcomeBuilder;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.helm.HelmFetchFileConfig;
import io.harness.encryption.SecretRefData;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CDStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private ConnectorService connectorService;
  @Mock private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private ConnectorInfoDTO connectorInfoDTO;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Spy @InjectMocks private K8sEntityHelper k8sEntityHelper;
  @Spy @InjectMocks private CDStepHelper CDStepHelper;
  private final Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();
  private String id = "identifier";
  private ParameterField folderPath = ParameterField.createValueField("folderPath/");
  private List<String> paths = asList("test/path1.yaml", "test/path2.yaml");

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldGetGitStoreDelegateConfigFFEnabledGithubUsernameTokenAuth() {
    List<String> paths = asList("path/to");
    GitStoreConfig gitStoreConfig = GithubStore.builder()
                                        .repoName(ParameterField.createValueField("parent-repo/module"))
                                        .paths(ParameterField.createValueField(paths))
                                        .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    List<EncryptedDataDetail> apiEncryptedDataDetails = new ArrayList<>();
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                 .httpCredentialsSpec(GithubUsernameTokenDTO.builder()
                                                                          .username("usermane")
                                                                          .tokenRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();
    connectorInfoDTO.setConnectorConfig(githubConnectorDTO);

    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), eq(OPTIMIZED_GIT_FETCH_FILES));
    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());
    doReturn(apiEncryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());

    GitStoreDelegateConfig gitStoreDelegateConfig = CDStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorInfoDTO, K8sManifestOutcome.builder().build(), paths, ambiance);

    assertThat(gitStoreDelegateConfig).isNotNull();
    assertThat(gitStoreDelegateConfig.isOptimizedFilesFetch()).isTrue();
    assertThat(gitStoreDelegateConfig.getGitConfigDTO()).isInstanceOf(GithubConnectorDTO.class);
    assertThat(gitStoreDelegateConfig.getApiAuthEncryptedDataDetails()).isEqualTo(apiEncryptedDataDetails);
    GithubConnectorDTO convertedGithubConnectorDTO = (GithubConnectorDTO) gitStoreDelegateConfig.getGitConfigDTO();
    assertThat(convertedGithubConnectorDTO.getUrl()).isEqualTo("http://localhost/parent-repo/module");
    assertThat(convertedGithubConnectorDTO.getConnectionType()).isEqualTo(GitConnectionType.REPO);
    assertThat(convertedGithubConnectorDTO.getApiAccess()).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldGetGitStoreDelegateConfigFFEnabledGithubApiAccess() {
    List<String> paths = asList("path/to");
    GitStoreConfig gitStoreConfig = GithubStore.builder()
                                        .repoName(ParameterField.createValueField("parent-repo/module"))
                                        .paths(ParameterField.createValueField(paths))
                                        .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    List<EncryptedDataDetail> apiEncryptedDataDetails = new ArrayList<>();
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();
    connectorInfoDTO.setConnectorConfig(githubConnectorDTO);

    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), eq(OPTIMIZED_GIT_FETCH_FILES));
    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());
    doReturn(apiEncryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());

    GitStoreDelegateConfig gitStoreDelegateConfig = CDStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorInfoDTO, K8sManifestOutcome.builder().build(), paths, ambiance);

    assertThat(gitStoreDelegateConfig).isNotNull();
    assertThat(gitStoreDelegateConfig.isOptimizedFilesFetch()).isTrue();
    assertThat(gitStoreDelegateConfig.getGitConfigDTO()).isInstanceOf(GithubConnectorDTO.class);
    assertThat(gitStoreDelegateConfig.getApiAuthEncryptedDataDetails()).isEqualTo(apiEncryptedDataDetails);
    GithubConnectorDTO convertedGithubConnectorDTO = (GithubConnectorDTO) gitStoreDelegateConfig.getGitConfigDTO();
    assertThat(convertedGithubConnectorDTO.getUrl()).isEqualTo("http://localhost/parent-repo/module");
    assertThat(convertedGithubConnectorDTO.getConnectionType()).isEqualTo(GitConnectionType.REPO);
    assertThat(convertedGithubConnectorDTO.getApiAccess()).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldGetGitStoreDelegateConfigFFEnabledNotGithubOrGitlab() {
    List<String> paths = asList("path/to");
    GitStoreConfig gitStoreConfig = GithubStore.builder()
                                        .repoName(ParameterField.createValueField("parent-repo/module"))
                                        .paths(ParameterField.createValueField(paths))
                                        .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    GitConfigDTO gitConfigDTO =
        GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).url("http://localhost").build();
    connectorInfoDTO.setConnectorConfig(gitConfigDTO);

    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), eq(OPTIMIZED_GIT_FETCH_FILES));
    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());

    GitStoreDelegateConfig gitStoreDelegateConfig = CDStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorInfoDTO, K8sManifestOutcome.builder().build(), paths, ambiance);

    assertThat(gitStoreDelegateConfig).isNotNull();
    assertThat(gitStoreDelegateConfig.getGitConfigDTO()).isInstanceOf(GitConfigDTO.class);
    assertThat(gitStoreDelegateConfig.isOptimizedFilesFetch()).isFalse();
    GitConfigDTO convertedConfig = (GitConfigDTO) gitStoreDelegateConfig.getGitConfigDTO();
    assertThat(convertedConfig.getUrl()).isEqualTo("http://localhost/parent-repo/module");
    assertThat(convertedConfig.getGitConnectionType()).isEqualTo(GitConnectionType.REPO);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldGetGitStoreDelegateConfigFFEnabledGithubUsernamePasswordAuth() {
    List<String> paths = asList("path/to");
    GitStoreConfig gitStoreConfig = GithubStore.builder()
                                        .repoName(ParameterField.createValueField("parent-repo/module"))
                                        .paths(ParameterField.createValueField(paths))
                                        .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();
    connectorInfoDTO.setConnectorConfig(githubConnectorDTO);

    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), eq(OPTIMIZED_GIT_FETCH_FILES));
    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());

    GitStoreDelegateConfig gitStoreDelegateConfig = CDStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorInfoDTO, K8sManifestOutcome.builder().build(), paths, ambiance);

    assertThat(gitStoreDelegateConfig).isNotNull();
    assertThat(gitStoreDelegateConfig.getGitConfigDTO()).isInstanceOf(GitConfigDTO.class);
    assertThat(gitStoreDelegateConfig.isOptimizedFilesFetch()).isFalse();
    GitConfigDTO convertedConfig = (GitConfigDTO) gitStoreDelegateConfig.getGitConfigDTO();
    assertThat(convertedConfig.getUrl()).isEqualTo("http://localhost/parent-repo/module");
    assertThat(convertedConfig.getGitConnectionType()).isEqualTo(GitConnectionType.REPO);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldGetGitStoreDelegateConfigOptimizedFilesFetchWithKustomize() {
    List<String> paths = asList("path/to");
    GitStoreConfig gitStoreConfig = GithubStore.builder()
                                        .repoName(ParameterField.createValueField("parent-repo/module"))
                                        .paths(ParameterField.createValueField(paths))
                                        .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    List<EncryptedDataDetail> apiEncryptedDataDetails = new ArrayList<>();
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .build();
    connectorInfoDTO.setConnectorConfig(githubConnectorDTO);

    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), eq(OPTIMIZED_GIT_FETCH_FILES));
    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());
    doReturn(apiEncryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());

    GitStoreDelegateConfig gitStoreDelegateConfig = CDStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorInfoDTO, KustomizeManifestOutcome.builder().build(), paths, ambiance);

    assertThat(gitStoreDelegateConfig).isNotNull();
    assertThat(gitStoreDelegateConfig.isOptimizedFilesFetch()).isFalse();
    GitConfigDTO gitConfigDTO = (GitConfigDTO) gitStoreDelegateConfig.getGitConfigDTO();
    assertThat(gitConfigDTO.getUrl()).isEqualTo("http://localhost/parent-repo/module");
    assertThat(gitConfigDTO.getGitConnectionType()).isEqualTo(GitConnectionType.REPO);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldConvertGitAccountRepoWithRepoName() {
    List<String> paths = asList("path/to");
    GitStoreConfig gitStoreConfig = GithubStore.builder()
                                        .repoName(ParameterField.createValueField("parent-repo/module"))
                                        .paths(ParameterField.createValueField(paths))
                                        .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    GitConfigDTO gitConfigDTO =
        GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).url("http://localhost").build();
    connectorInfoDTO.setConnectorConfig(gitConfigDTO);

    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());

    GitStoreDelegateConfig gitStoreDelegateConfig = CDStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorInfoDTO, K8sManifestOutcome.builder().build(), paths, ambiance);

    assertThat(gitStoreDelegateConfig).isNotNull();
    assertThat(gitStoreDelegateConfig.getGitConfigDTO()).isInstanceOf(GitConfigDTO.class);
    GitConfigDTO convertedConfig = (GitConfigDTO) gitStoreDelegateConfig.getGitConfigDTO();
    assertThat(convertedConfig.getUrl()).isEqualTo("http://localhost/parent-repo/module");
    assertThat(convertedConfig.getGitConnectionType()).isEqualTo(GitConnectionType.REPO);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldNotConvertGitRepoWithRepoName() {
    List<String> paths = asList("path/to");
    GitStoreConfig gitStoreConfig = GithubStore.builder()
                                        .repoName(ParameterField.createValueField("parent-repo/module"))
                                        .paths(ParameterField.createValueField(paths))
                                        .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    GitConfigDTO gitConfigDTO =
        GitConfigDTO.builder().gitConnectionType(GitConnectionType.REPO).url("http://localhost/repository").build();
    connectorInfoDTO.setConnectorConfig(gitConfigDTO);

    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());

    GitStoreDelegateConfig gitStoreDelegateConfig = CDStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorInfoDTO, K8sManifestOutcome.builder().build(), paths, ambiance);

    assertThat(gitStoreDelegateConfig).isNotNull();
    assertThat(gitStoreDelegateConfig.getGitConfigDTO()).isInstanceOf(GitConfigDTO.class);
    GitConfigDTO convertedConfig = (GitConfigDTO) gitStoreDelegateConfig.getGitConfigDTO();
    assertThat(convertedConfig.getUrl()).isEqualTo("http://localhost/repository");
    assertThat(convertedConfig.getGitConnectionType()).isEqualTo(GitConnectionType.REPO);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldFailGitRepoConversionIfRepoNameIsMissing() {
    List<String> paths = asList("path/to");
    GitStoreConfig gitStoreConfig = GithubStore.builder().paths(ParameterField.createValueField(paths)).build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    GitConfigDTO gitConfigDTO =
        GitConfigDTO.builder().gitConnectionType(GitConnectionType.ACCOUNT).url("http://localhost").build();
    connectorInfoDTO.setConnectorConfig(gitConfigDTO);

    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());
    try {
      CDStepHelper.getGitStoreDelegateConfig(
          gitStoreConfig, connectorInfoDTO, K8sManifestOutcome.builder().build(), paths, ambiance);
    } catch (Exception thrown) {
      assertThat(thrown).isNotNull();
      assertThat(thrown).isInstanceOf(InvalidRequestException.class);
      assertThat(thrown.getMessage()).isEqualTo("Repo name cannot be empty for Account level git connector");
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldTrimFieldsForGetGitStoreDelegateConfig() {
    List<String> paths = asList("test/path1", "test/path2 ", " test/path3", " test/path4 ", "te st/path5 ");
    GitStoreConfig gitStoreConfig = GithubStore.builder()
                                        .paths(ParameterField.createValueField(paths))
                                        .commitId(ParameterField.createValueField(" commitId "))
                                        .branch(ParameterField.createValueField(" branch "))
                                        .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    GitConfigDTO gitConfigDTO =
        GitConfigDTO.builder().gitConnectionType(GitConnectionType.REPO).url("http://localhost").build();
    connectorInfoDTO.setConnectorConfig(gitConfigDTO);

    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());

    GitStoreDelegateConfig gitStoreDelegateConfig = CDStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorInfoDTO, K8sManifestOutcome.builder().build(), paths, ambiance);

    assertThat(gitStoreDelegateConfig.getBranch()).isEqualTo("branch");
    assertThat(gitStoreDelegateConfig.getCommitId()).isEqualTo("commitId");
    assertThat(gitStoreDelegateConfig.getPaths())
        .containsExactlyInAnyOrder("test/path1", "test/path2", "test/path3", "test/path4", "te st/path5");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetTimeoutValue() {
    StepElementParameters definedValue =
        StepElementParameters.builder().timeout(ParameterField.createValueField("15m")).build();
    StepElementParameters nullValue = StepElementParameters.builder().timeout(ParameterField.ofNull()).build();
    assertThat(CDStepHelper.getTimeoutValue(definedValue)).isEqualTo("15m");
    assertThat(CDStepHelper.getTimeoutValue(nullValue)).isEqualTo("10m");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetTimeoutInMin() {
    StepElementParameters value =
        StepElementParameters.builder().timeout(ParameterField.createValueField("15m")).build();
    assertThat(CDStepHelper.getTimeoutInMin(value)).isEqualTo(15);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetTimeoutInMillis() {
    StepElementParameters value =
        StepElementParameters.builder().timeout(ParameterField.createValueField("15m")).build();
    assertThat(CDStepHelper.getTimeoutInMillis(value)).isEqualTo(900000);
  }

  public Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();

    setupAbstractions.put(SetupAbstractionKeys.accountId, "account1");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org1");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project1");

    return Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetProjectConnector() {
    Ambiance ambiance = getAmbiance();
    ConnectorInfoDTO connectorDTO = ConnectorInfoDTO.builder().build();
    Optional<ConnectorResponseDTO> connectorDTOOptional =
        Optional.of(ConnectorResponseDTO.builder().connector(connectorDTO).build());
    doReturn(connectorDTOOptional).when(connectorService).get("account1", "org1", "project1", "abcConnector");

    ConnectorInfoDTO actualConnector = CDStepHelper.getConnector("abcConnector", ambiance);
    assertThat(actualConnector).isEqualTo(connectorDTO);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetOrgConnector() {
    Ambiance ambiance = getAmbiance();
    ConnectorInfoDTO connectorDTO = ConnectorInfoDTO.builder().build();
    Optional<ConnectorResponseDTO> connectorDTOOptional =
        Optional.of(ConnectorResponseDTO.builder().connector(connectorDTO).build());
    doReturn(connectorDTOOptional).when(connectorService).get("account1", "org1", null, "abcConnector");

    ConnectorInfoDTO actualConnector = CDStepHelper.getConnector("org.abcConnector", ambiance);
    assertThat(actualConnector).isEqualTo(connectorDTO);
  }

  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testGetReleaseName() {
    // Invalid formats
    assertThatThrownBy(()
                           -> CDStepHelper.getReleaseName(ambiance,
                               K8sDirectInfrastructureOutcome.builder().releaseName("").build()))
        .isInstanceOf(InvalidArgumentsException.class); // empty releaseName

    assertThatThrownBy(()
                           -> CDStepHelper.getReleaseName(ambiance,
                               K8sDirectInfrastructureOutcome.builder().releaseName("NameWithUpperCase").build()))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        ()
            -> CDStepHelper.getReleaseName(
                ambiance, K8sGcpInfrastructureOutcome.builder().releaseName("-starting.with.non.alphanumeric").build()))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        ()
            -> CDStepHelper.getReleaseName(ambiance,
                K8sDirectInfrastructureOutcome.builder().releaseName(".starting.with.non.alphanumeric").build()))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        ()
            -> CDStepHelper.getReleaseName(
                ambiance, K8sGcpInfrastructureOutcome.builder().releaseName("containing)invalid.characters+").build()))
        .isInstanceOf(InvalidRequestException.class);

    // Valid Formats
    CDStepHelper.getReleaseName(
        ambiance, K8sDirectInfrastructureOutcome.builder().releaseName("alphanumeriname124").build());
    CDStepHelper.getReleaseName(
        ambiance, K8sGcpInfrastructureOutcome.builder().releaseName("1starting.with.number").build());
    CDStepHelper.getReleaseName(
        ambiance, K8sDirectInfrastructureOutcome.builder().releaseName("starting.with.alphabet").build());
    CDStepHelper.getReleaseName(ambiance, K8sGcpInfrastructureOutcome.builder().releaseName("containing.dot").build());
    CDStepHelper.getReleaseName(
        ambiance, K8sDirectInfrastructureOutcome.builder().releaseName("containing-hyphen").build());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetAccountConnector() {
    Ambiance ambiance = getAmbiance();
    ConnectorInfoDTO connectorDTO = ConnectorInfoDTO.builder().build();
    Optional<ConnectorResponseDTO> connectorDTOOptional =
        Optional.of(ConnectorResponseDTO.builder().connector(connectorDTO).build());

    doReturn(connectorDTOOptional).when(connectorService).get("account1", null, null, "abcConnector");
    doReturn(Optional.empty()).when(connectorService).get("account1", "org1", null, "abcConnector");
    doReturn(Optional.empty()).when(connectorService).get("account1", "org1", "project1", "abcConnector");

    ConnectorInfoDTO actualConnector = CDStepHelper.getConnector("account.abcConnector", ambiance);
    assertThat(actualConnector).isEqualTo(connectorDTO);

    assertThatThrownBy(() -> CDStepHelper.getConnector("org.abcConnector", ambiance))
        .hasMessageContaining("Connector not found for identifier : [org.abcConnector]");

    assertThatThrownBy(() -> CDStepHelper.getConnector("abcConnector", ambiance))
        .hasMessageContaining("Connector not found for identifier : [abcConnector]");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleStepExceptionFailure() {
    List<UnitProgress> progressList = Collections.singletonList(UnitProgress.newBuilder().build());
    StepExceptionPassThroughData data =
        StepExceptionPassThroughData.builder()
            .unitProgressData(UnitProgressData.builder().unitProgresses(progressList).build())
            .errorMessage("Something went wrong")
            .build();

    StepResponse result = CDStepHelper.handleStepExceptionFailure(data);

    assertThat(result.getUnitProgressList()).isEqualTo(progressList);
    assertThat(result.getStatus()).isEqualTo(Status.FAILED);
    assertThat(result.getFailureInfo().getFailureDataList()).hasSize(1);
    FailureData failureData = result.getFailureInfo().getFailureData(0);
    assertThat(failureData.getFailureTypesList()).contains(FailureType.APPLICATION_FAILURE);
    assertThat(failureData.getCode()).isEqualTo(GENERAL_ERROR.name());
    assertThat(failureData.getMessage()).isEqualTo("Something went wrong");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testValidateManifest() {
    when(connectorInfoDTO.getConnectorConfig()).thenReturn(null);
    String[] manifestStoreTypes = {ManifestStoreType.GIT, ManifestStoreType.GITHUB, ManifestStoreType.BITBUCKET,
        ManifestStoreType.GITLAB, ManifestStoreType.HTTP, ManifestStoreType.S3, ManifestStoreType.GCS};
    for (String storeType : manifestStoreTypes) {
      assertThatThrownBy(() -> CDStepHelper.validateManifest(storeType, ConnectorInfoDTO.builder().build(), ""))
          .isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testGetK8sInfraDelegateConfig() {
    Ambiance ambiance = getAmbiance();
    ConnectorInfoDTO connectorDTO = ConnectorInfoDTO.builder().connectorType(GITHUB).build();
    Optional<ConnectorResponseDTO> connectorDTOOptional =
        Optional.of(ConnectorResponseDTO.builder().connector(connectorDTO).build());
    doReturn(connectorDTOOptional).when(connectorService).get("account1", "org1", "project1", "abcConnector");

    K8sDirectInfrastructureOutcomeBuilder outcomeBuilder =
        K8sDirectInfrastructureOutcome.builder().connectorRef("abcConnector").namespace("valid");

    assertThatThrownBy(() -> CDStepHelper.getK8sInfraDelegateConfig(outcomeBuilder.build(), ambiance))
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(()
                           -> CDStepHelper.getK8sInfraDelegateConfig(K8sGcpInfrastructureOutcome.builder()
                                                                         .connectorRef("abcConnector")
                                                                         .namespace("valid")
                                                                         .cluster("cluster")
                                                                         .build(),
                               ambiance))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testNamespaceValidation() {
    Ambiance ambiance = getAmbiance();
    ConnectorInfoDTO connectorDTO = ConnectorInfoDTO.builder().build();
    Optional<ConnectorResponseDTO> connectorDTOOptional =
        Optional.of(ConnectorResponseDTO.builder().connector(connectorDTO).build());
    doReturn(connectorDTOOptional).when(connectorService).get("account1", "org1", "project1", "abcConnector");

    K8sDirectInfrastructureOutcomeBuilder outcomeBuilder =
        K8sDirectInfrastructureOutcome.builder().connectorRef("abcConnector");

    try {
      outcomeBuilder.namespace("");
      CDStepHelper.getK8sInfraDelegateConfig(outcomeBuilder.build(), ambiance);
      fail("Should not reach here.");
    } catch (InvalidArgumentsException ex) {
      assertThat(ex.getParams().get("args")).isEqualTo("Namespace: Namespace cannot be empty");
    }

    try {
      outcomeBuilder.namespace(" namespace test ");
      CDStepHelper.getK8sInfraDelegateConfig(outcomeBuilder.build(), ambiance);
      fail("Should not reach here.");
    } catch (InvalidArgumentsException ex) {
      assertThat(ex.getParams().get("args"))
          .isEqualTo("Namespace: [ namespace test ] contains leading or trailing whitespaces");
    }
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateGitStoreConfigWithNoArguments() {
    CDStepHelper.validateGitStoreConfig(null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateGitStoreConfigWithEmptyBranch() {
    GithubStore gitStore = GithubStore.builder().gitFetchType(FetchType.BRANCH).build();
    CDStepHelper.validateGitStoreConfig(gitStore);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateGitStoreConfigWithEmptyCommit() {
    GithubStore gitStore = GithubStore.builder().gitFetchType(FetchType.COMMIT).build();
    CDStepHelper.validateGitStoreConfig(gitStore);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateGitStoreConfigWithBrachAndCommit() {
    ParameterField<String> branch = new ParameterField<>();
    ParameterField<String> commit = new ParameterField<>();

    branch.setValue("branch");
    commit.setValue("commit");
    GithubStore gitStore = GithubStore.builder().gitFetchType(FetchType.BRANCH).branch(branch).build();
    CDStepHelper.validateGitStoreConfig(gitStore);
    GithubStore gitStore2 = GithubStore.builder().gitFetchType(FetchType.COMMIT).commitId(commit).build();
    CDStepHelper.validateGitStoreConfig(gitStore2);
    assertThat(gitStore.getBranch()).isEqualTo(branch);
    assertThat(gitStore2.getCommitId()).isEqualTo(commit);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCompleteUnitProgressDataEmpty() {
    Ambiance ambiance = getAmbiance();
    UnitProgressData response = CDStepHelper.completeUnitProgressData(null, ambiance, "foobar");
    assertThat(response.getUnitProgresses().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCompleteUnitProgressData() {
    List<UnitProgress> unitProgresses = new ArrayList<>();
    unitProgresses.add(UnitProgress.newBuilder().setStatus(UnitStatus.SUCCESS).build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    Ambiance ambiance = getAmbiance();
    UnitProgressData response = CDStepHelper.completeUnitProgressData(unitProgressData, ambiance, "foobar");
    assertThat(response.getUnitProgresses().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testCompleteUnitProgressDataRunning() {
    List<UnitProgress> unitProgresses = new ArrayList<>();
    unitProgresses.add(UnitProgress.newBuilder().setStatus(UnitStatus.RUNNING).setUnitName("foobar").build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    Ambiance ambiance = getAmbiance();
    NGLogCallback mockCallback = mock(NGLogCallback.class);
    doReturn(mockCallback).when(CDStepHelper).getLogCallback("foobar", ambiance, true);
    doNothing().when(mockCallback).saveExecutionLog(any(), any(), any());
    ILogStreamingStepClient mockLogSgtreamingStepClient = mock(ILogStreamingStepClient.class);
    doReturn(mockLogSgtreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
    UnitProgressData response = CDStepHelper.completeUnitProgressData(unitProgressData, ambiance, "foobar");
    assertThat(response.getUnitProgresses().get(0).getStatus()).isEqualTo(UnitStatus.FAILURE);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetGitFetchFilesConfig() {
    Ambiance ambiance = getAmbiance();
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder().build();
    ConnectorInfoDTO connectorDTO =
        ConnectorInfoDTO.builder().connectorConfig(githubConnectorDTO).connectorType(GITHUB).build();
    GitStoreConfig githubStore = GithubStore.builder()
                                     .paths(ParameterField.createValueField(paths))
                                     .connectorRef(ParameterField.createValueField("connectorId"))
                                     .build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder().paths(paths).build();
    doReturn(connectorDTO).when(k8sEntityHelper).getConnectorInfoDTO(any(), any());
    doReturn(gitStoreDelegateConfig).when(CDStepHelper).getGitStoreDelegateConfig(any(), any(), any(), any(), any());
    ManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder().identifier(id).store(githubStore).build();
    GitFetchFilesConfig valuesGitFetchFilesConfig =
        CDStepHelper.getGitFetchFilesConfig(ambiance, githubStore, "passed", valuesManifestOutcome);
    assertThat(valuesGitFetchFilesConfig.getIdentifier().equals(id));
    assertThat(valuesGitFetchFilesConfig.getManifestType().equals(ManifestType.VALUES));
    assertThat(valuesGitFetchFilesConfig.getGitStoreDelegateConfig().getPaths().equals(paths));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetPathsFromInheritFromManifestStoreConfig() {
    Ambiance ambiance = getAmbiance();
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder().build();
    ConnectorInfoDTO connectorDTO =
        ConnectorInfoDTO.builder().connectorConfig(githubConnectorDTO).connectorType(GITHUB).build();
    GitStoreConfig githubStore = GithubStore.builder()
                                     .folderPath(folderPath)
                                     .connectorRef(ParameterField.createValueField("connectorId"))
                                     .build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder().paths(paths).build();
    doReturn(connectorDTO).when(k8sEntityHelper).getConnectorInfoDTO(any(), any());
    doReturn(gitStoreDelegateConfig).when(CDStepHelper).getGitStoreDelegateConfig(any(), any(), any(), any(), any());
    InheritFromManifestStoreConfig inheritFromManifestStoreConfig =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(paths)).build();
    ManifestOutcome kustomizeManifestOutcome = KustomizeManifestOutcome.builder().store(githubStore).build();
    ManifestOutcome patchesManifestOutcome =
        KustomizePatchesManifestOutcome.builder().identifier(id).store(inheritFromManifestStoreConfig).build();
    GitFetchFilesConfig patchesGitFetchFilesConfig = CDStepHelper.getPathsFromInheritFromManifestStoreConfig(
        ambiance, "passed", patchesManifestOutcome, (GitStoreConfig) kustomizeManifestOutcome.getStore());
    List<String> finalPaths = new ArrayList<>();
    for (String path : paths) {
      finalPaths.add(folderPath.getValue() + path);
    }
    assertThat(patchesGitFetchFilesConfig.getIdentifier().equals(id));
    assertThat(patchesGitFetchFilesConfig.getManifestType().equals(ManifestType.KustomizePatches));
    assertThat(patchesGitFetchFilesConfig.getGitStoreDelegateConfig().getPaths().equals(finalPaths));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testMapValuesManifestsToInheritFromManifestFetchFileConfig() {
    InheritFromManifestStoreConfig inheritFromManifestStoreConfig =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(paths)).build();
    List<ValuesManifestOutcome> valuesManifestOutcome = new ArrayList<>(
        asList(ValuesManifestOutcome.builder().identifier(id).store(inheritFromManifestStoreConfig).build()));
    List<HelmFetchFileConfig> helmFetchFileConfigList =
        CDStepHelper.mapValuesManifestsToHelmFetchFileConfig(valuesManifestOutcome);
    assertThat(helmFetchFileConfigList.get(0).getIdentifier().equals(id));
    assertThat(helmFetchFileConfigList.get(0).getManifestType().equals(ManifestType.VALUES));
    assertThat(helmFetchFileConfigList.get(0).getFilePaths().equals(paths));
  }
}
