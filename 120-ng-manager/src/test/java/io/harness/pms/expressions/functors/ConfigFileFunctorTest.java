/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.NGCommonEntityConstants.FUNCTOR_BASE64_METHOD_NAME;
import static io.harness.NGCommonEntityConstants.FUNCTOR_STRING_METHOD_NAME;
import static io.harness.pms.expressions.functors.ConfigFileFunctor.MAX_CONFIG_FILE_SIZE;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.configfile.ConfigGitFile;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ConfigFileFunctorTest extends CategoryTest {
  private static final Long EXPRESSION_FUNCTOR_TOKEN = 1L;
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String FILE_CONTENT = "file content";
  private static final String CONFIG_FILE_IDENTIFIER = "configFileIdentifier";
  private static final String BASE64_FILE_CONTENT = "ZmlsZSBjb250ZW50";
  private static final String ACCOUNT_SCOPED_FILE_PATH = "account:/folder1/folder2/configFile";
  public static final String ORG_SCOPED_FILE_PATH = "org:/folder1/folder2/configFile";
  public static final String PROJECT_SCOPED_FILE_PATH = "/folder1/folder2/configFile";
  public static final String GIT_FILE_PATH = "ssh-winrm/configSshFile.yml";
  private static final String ACCOUNT_SECRET_REF_PATH = "account.AzureFileSecret";
  public static final String ORG_SECRET_REF_PATH = "org.AzureFileSecret";
  public static final String PROJECT_SECRET_REF_PATH = "AzureFileSecret";
  private static final String GIT_FILE_CONTENT = "git file content";
  private static final String FILE_CONTENT_ON_ACCOUNT = "file-content-on-account";
  private static final String FILE_CONTENT_ON_ORG = "file-content-on-org";
  private static final String FILE_CONTENT_ON_PROJECT = "file-content-on-project";

  @Mock private CDStepHelper cdStepHelper;
  @Mock private CDExpressionResolver cdExpressionResolver;

  @InjectMocks private ConfigFileFunctor configFileFunctor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetNotValidArgs() {
    assertThatThrownBy(() -> configFileFunctor.get(Ambiance.newBuilder().build(), FUNCTOR_STRING_METHOD_NAME))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Invalid configFile functor arguments: [getAsString]");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetNotValidSecretTypeFunctorMethod() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithSecretFiles(List.of(ACCOUNT_SECRET_REF_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    assertThatThrownBy(() -> configFileFunctor.get(ambiance, "obtainSecret", CONFIG_FILE_IDENTIFIER))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Unsupported configFile functor method: obtainSecret, secretRef: account.AzureFileSecret");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetNotValidFileTypeFunctorMethod() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFiles(List.of(ACCOUNT_SCOPED_FILE_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    assertThatThrownBy(() -> configFileFunctor.get(ambiance, "obtainFile", CONFIG_FILE_IDENTIFIER))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Unsupported configFile functor method: obtainFile, scopedFilePath: account:/folder1/folder2/configFile");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWithFileAndSecretFile() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFileAndSecretFile();
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    assertThatThrownBy(() -> configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Found file and encrypted file both attached to config file, configFileIdentifier: configFileIdentifier");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWithWithoutFileAndSecretFile() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFileAndSecretFile();
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    assertThatThrownBy(() -> configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Found file and encrypted file both attached to config file, configFileIdentifier: configFileIdentifier");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWithMoreFiles() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithMoreFiles();
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    assertThatThrownBy(() -> configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Found more files attached to config file, configFileIdentifier: configFileIdentifier");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWithMoreSecretFiles() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithMoreSecretFiles(
        List.of(ACCOUNT_SECRET_REF_PATH, ORG_SECRET_REF_PATH, PROJECT_SECRET_REF_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    assertThatThrownBy(() -> configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Found more encrypted files attached to config file, configFileIdentifier: configFileIdentifier");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testFileGetAsString() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFiles(List.of(ACCOUNT_SCOPED_FILE_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdStepHelper.getFileContentAsString(ambiance, ACCOUNT_SCOPED_FILE_PATH, MAX_CONFIG_FILE_SIZE))
        .thenReturn(FILE_CONTENT);

    String fileContent = (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER);

    assertThat(fileContent).isEqualTo(FILE_CONTENT);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void testGitFileGetAsString() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetGitConfigFileOutcomeWithFiles(List.of(GIT_FILE_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdExpressionResolver.renderExpression(ambiance, GIT_FILE_CONTENT)).thenReturn(GIT_FILE_CONTENT);
    String fileContent = (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER);

    assertThat(fileContent).isEqualTo(GIT_FILE_CONTENT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testFileGetAsBase64() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFiles(List.of(ACCOUNT_SCOPED_FILE_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdStepHelper.getFileContentAsBase64(ambiance, ACCOUNT_SCOPED_FILE_PATH, MAX_CONFIG_FILE_SIZE))
        .thenReturn(BASE64_FILE_CONTENT);

    String base64FileContent =
        (String) configFileFunctor.get(getAmbiance(), FUNCTOR_BASE64_METHOD_NAME, CONFIG_FILE_IDENTIFIER);

    assertThat(base64FileContent).isEqualTo(BASE64_FILE_CONTENT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSecretFileGetAsString() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithSecretFiles(List.of(ACCOUNT_SECRET_REF_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    String secretExpression =
        (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER);

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"account.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSecretFileGetAsBase64() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithSecretFiles(List.of(ACCOUNT_SECRET_REF_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    String secretExpression =
        (String) configFileFunctor.get(ambiance, FUNCTOR_BASE64_METHOD_NAME, CONFIG_FILE_IDENTIFIER);

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsBase64(\"account.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetConfigFileIdentifierAndReference() {
    assertThatThrownBy(() -> configFileFunctor.getConfigFileIdentifierAndReference(""))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Config file identifier cannot be null or empty");

    assertThatThrownBy(() -> configFileFunctor.getConfigFileIdentifierAndReference("configFileId:"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Found invalid config file identifier, configFileId:");

    assertThatThrownBy(() -> configFileFunctor.getConfigFileIdentifierAndReference(":configFileId"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Found invalid config file identifier, :configFileId");

    Pair<String, String> configFileIdentifier = configFileFunctor.getConfigFileIdentifierAndReference("configFileId");
    assertThat(configFileIdentifier.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifier.getRight()).isEqualTo(null);

    Pair<String, String> configFileIdentifierAndReferenceFSAccount =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:account:/folder1/folder2/configFile");
    assertThat(configFileIdentifierAndReferenceFSAccount.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceFSAccount.getRight()).isEqualTo("account:/folder1/folder2/configFile");

    Pair<String, String> configFileIdentifierAndReferenceFSOrg =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:org:/folder1/folder2/configFile");
    assertThat(configFileIdentifierAndReferenceFSOrg.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceFSOrg.getRight()).isEqualTo("org:/folder1/folder2/configFile");

    Pair<String, String> configFileIdentifierAndReferenceFSProject =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:/folder1/folder2/configFile");
    assertThat(configFileIdentifierAndReferenceFSProject.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceFSProject.getRight()).isEqualTo("/folder1/folder2/configFile");

    Pair<String, String> configFileIdentifierAndReferenceGit1 =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:ssh-winrm/configSshFile.yml");
    assertThat(configFileIdentifierAndReferenceGit1.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceGit1.getRight()).isEqualTo("ssh-winrm/configSshFile.yml");

    Pair<String, String> configFileIdentifierAndReferenceGit2 =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:ssh-winrm/configSshFile2.yml");
    assertThat(configFileIdentifierAndReferenceGit2.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceGit2.getRight()).isEqualTo("ssh-winrm/configSshFile2.yml");

    Pair<String, String> configFileIdentifierAndReferenceSecretAccount =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:account.AzureFileSecret");
    assertThat(configFileIdentifierAndReferenceSecretAccount.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceSecretAccount.getRight()).isEqualTo("account.AzureFileSecret");

    Pair<String, String> configFileIdentifierAndReferenceSecretOrg =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:org.AzureFileSecret");
    assertThat(configFileIdentifierAndReferenceSecretOrg.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceSecretOrg.getRight()).isEqualTo("org.AzureFileSecret");

    Pair<String, String> configFileIdentifierAndReferenceSecretProject =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:AzureFileSecret");
    assertThat(configFileIdentifierAndReferenceSecretProject.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceSecretProject.getRight()).isEqualTo("AzureFileSecret");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileOnAccountLevel() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFiles(
        List.of(ACCOUNT_SCOPED_FILE_PATH, ORG_SCOPED_FILE_PATH, PROJECT_SCOPED_FILE_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdStepHelper.getFileContentAsString(ambiance, ACCOUNT_SCOPED_FILE_PATH, MAX_CONFIG_FILE_SIZE))
        .thenReturn(FILE_CONTENT_ON_ACCOUNT);
    String resolvedFileContent = (String) configFileFunctor.get(
        ambiance, FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + ACCOUNT_SCOPED_FILE_PATH);

    assertThat(resolvedFileContent).isEqualTo(FILE_CONTENT_ON_ACCOUNT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileWithoutReference() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFiles(List.of(ACCOUNT_SCOPED_FILE_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdStepHelper.getFileContentAsString(ambiance, ACCOUNT_SCOPED_FILE_PATH, MAX_CONFIG_FILE_SIZE))
        .thenReturn(FILE_CONTENT_ON_ACCOUNT);
    String resolvedFileContent =
        (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier");

    assertThat(resolvedFileContent).isEqualTo(FILE_CONTENT_ON_ACCOUNT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileOnOrgLevel() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFiles(
        List.of(ACCOUNT_SCOPED_FILE_PATH, ORG_SCOPED_FILE_PATH, PROJECT_SCOPED_FILE_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdStepHelper.getFileContentAsString(ambiance, ORG_SCOPED_FILE_PATH, MAX_CONFIG_FILE_SIZE))
        .thenReturn(FILE_CONTENT_ON_ORG);
    String resolvedFileContent = (String) configFileFunctor.get(
        ambiance, FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + ORG_SCOPED_FILE_PATH);

    assertThat(resolvedFileContent).isEqualTo(FILE_CONTENT_ON_ORG);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileOnProjectLevel() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFiles(
        List.of(ACCOUNT_SCOPED_FILE_PATH, ORG_SCOPED_FILE_PATH, PROJECT_SCOPED_FILE_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdStepHelper.getFileContentAsString(ambiance, PROJECT_SCOPED_FILE_PATH, MAX_CONFIG_FILE_SIZE))
        .thenReturn(FILE_CONTENT_ON_PROJECT);
    String resolvedFileContent = (String) configFileFunctor.get(
        ambiance, FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + PROJECT_SCOPED_FILE_PATH);

    assertThat(resolvedFileContent).isEqualTo(FILE_CONTENT_ON_PROJECT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSecretOnAccountLevel() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithMoreSecretFiles(
        List.of(ACCOUNT_SECRET_REF_PATH, ORG_SECRET_REF_PATH, PROJECT_SECRET_REF_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    String secretExpression = (String) configFileFunctor.get(
        ambiance, FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + ACCOUNT_SECRET_REF_PATH);

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"account.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSecretWithoutReference() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome =
        mockGetConfigFileOutcomeWithMoreSecretFiles(List.of(ACCOUNT_SECRET_REF_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    String secretExpression =
        (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier");

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"account.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSecretOnOrgLevel() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithMoreSecretFiles(
        List.of(ACCOUNT_SECRET_REF_PATH, ORG_SECRET_REF_PATH, PROJECT_SECRET_REF_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    String secretExpression = (String) configFileFunctor.get(
        ambiance, FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + ORG_SECRET_REF_PATH);

    assertThat(secretExpression).isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"org.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSecretOnAProjectLevel() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithMoreSecretFiles(
        List.of(ACCOUNT_SECRET_REF_PATH, ORG_SECRET_REF_PATH, PROJECT_SECRET_REF_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    String secretExpression = (String) configFileFunctor.get(
        ambiance, FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + PROJECT_SECRET_REF_PATH);

    assertThat(secretExpression).isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetGitFile() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetGitConfigFileOutcomeWithFiles(List.of(GIT_FILE_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdExpressionResolver.renderExpression(ambiance, GIT_FILE_CONTENT)).thenReturn(GIT_FILE_CONTENT);

    String gitFileContent =
        (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + GIT_FILE_PATH);

    assertThat(gitFileContent).isEqualTo(GIT_FILE_CONTENT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetGitFileWithoutReference() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetGitConfigFileOutcomeWithFiles(List.of(GIT_FILE_PATH));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdExpressionResolver.renderExpression(ambiance, GIT_FILE_CONTENT)).thenReturn(GIT_FILE_CONTENT);

    String gitFileContent =
        (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier");

    assertThat(gitFileContent).isEqualTo(GIT_FILE_CONTENT);
  }

  @NotNull
  private ConfigFilesOutcome mockGetConfigFileOutcomeWithFiles(List<String> filePaths) {
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(filePaths)).build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(harnessStore).build());
    return configFilesOutcome;
  }

  @NotNull
  private ConfigFilesOutcome mockGetGitConfigFileOutcomeWithFiles(List<String> gitConfigFiles) {
    GithubStore githubStore = GithubStore.builder().paths(ParameterField.createValueField(gitConfigFiles)).build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    //    ConfigGitFile configGitFile =
    //        ConfigGitFile.builder().fileContent(FILE_CONTENT).filePath(ACCOUNT_SCOPED_FILE_PATH).build();
    ConfigGitFile configGitFile2 =
        ConfigGitFile.builder().fileContent(GIT_FILE_CONTENT).filePath(GIT_FILE_PATH).build();
    List<ConfigGitFile> gitFiles = Arrays.asList(configGitFile2);
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(githubStore).gitFiles(gitFiles).build());
    return configFilesOutcome;
  }

  @NotNull
  private ConfigFilesOutcome mockGetConfigFileOutcomeWithSecretFiles(List<String> filePaths) {
    HarnessStore harnessStore = HarnessStore.builder().secretFiles(ParameterField.createValueField(filePaths)).build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(harnessStore).build());
    return configFilesOutcome;
  }

  @NotNull
  private ConfigFilesOutcome mockGetConfigFileOutcomeWithFileAndSecretFile() {
    HarnessStore harnessStore = HarnessStore.builder()
                                    .files(ParameterField.createValueField(List.of(ACCOUNT_SCOPED_FILE_PATH)))
                                    .secretFiles(ParameterField.createValueField(List.of(ACCOUNT_SECRET_REF_PATH)))
                                    .build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(harnessStore).build());
    return configFilesOutcome;
  }

  @NotNull
  private ConfigFilesOutcome mockGetConfigFileOutcomeWithMoreFiles() {
    HarnessStore harnessStore =
        HarnessStore.builder()
            .files(ParameterField.createValueField(List.of(ACCOUNT_SCOPED_FILE_PATH, ACCOUNT_SCOPED_FILE_PATH)))
            .build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(harnessStore).build());
    return configFilesOutcome;
  }

  @NotNull
  private ConfigFilesOutcome mockGetConfigFileOutcomeWithMoreSecretFiles(List<String> secretRefPaths) {
    HarnessStore harnessStore =
        HarnessStore.builder().secretFiles(ParameterField.createValueField(secretRefPaths)).build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(harnessStore).build());
    return configFilesOutcome;
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
        .setExpressionFunctorToken(EXPRESSION_FUNCTOR_TOKEN)
        .build();
  }
}
