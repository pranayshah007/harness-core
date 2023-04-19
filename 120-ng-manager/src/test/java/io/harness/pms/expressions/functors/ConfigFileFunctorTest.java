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
  private static final String SCOPED_FILE_PATH = "scoped-file-path";
  private static final String SCOPED_SECRET_FILE_PATH = "scoped-secret-file-path";
  private static final String BASE64_FILE_CONTENT = "ZmlsZSBjb250ZW50";

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
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithSecretFiles();
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    assertThatThrownBy(() -> configFileFunctor.get(ambiance, "obtainSecret", CONFIG_FILE_IDENTIFIER))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Unsupported configFile functor method: obtainSecret, secretRef: scoped-secret-file-path");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetNotValidFileTypeFunctorMethod() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFiles();
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    assertThatThrownBy(() -> configFileFunctor.get(ambiance, "obtainFile", CONFIG_FILE_IDENTIFIER))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Unsupported configFile functor method: obtainFile, scopedFilePath: scoped-file-path");
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
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithMoreSecretFiles();
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
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFiles();
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdStepHelper.getFileContentAsString(ambiance, SCOPED_FILE_PATH, MAX_CONFIG_FILE_SIZE))
        .thenReturn(FILE_CONTENT);

    String fileContent = (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER);

    assertThat(fileContent).isEqualTo(FILE_CONTENT);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void testGitFileGetAsString() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetGitConfigFileOutcomeWithFiles();
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdExpressionResolver.renderExpression(ambiance, FILE_CONTENT)).thenReturn(FILE_CONTENT);
    String fileContent = (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER);

    assertThat(fileContent).isEqualTo(FILE_CONTENT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testFileGetAsBase64() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithFiles();
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));
    when(cdStepHelper.getFileContentAsBase64(ambiance, SCOPED_FILE_PATH, MAX_CONFIG_FILE_SIZE))
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
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithSecretFiles();
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    String secretExpression =
        (String) configFileFunctor.get(ambiance, FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER);

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"scoped-secret-file-path\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSecretFileGetAsBase64() {
    Ambiance ambiance = getAmbiance();
    ConfigFilesOutcome configFilesOutcome = mockGetConfigFileOutcomeWithSecretFiles();
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    String secretExpression =
        (String) configFileFunctor.get(ambiance, FUNCTOR_BASE64_METHOD_NAME, CONFIG_FILE_IDENTIFIER);

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsBase64(\"scoped-secret-file-path\", 1)}");
  }

  @NotNull
  private ConfigFilesOutcome mockGetConfigFileOutcomeWithFiles() {
    HarnessStore harnessStore =
        HarnessStore.builder().files(ParameterField.createValueField(List.of(SCOPED_FILE_PATH))).build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(harnessStore).build());
    return configFilesOutcome;
  }

  @NotNull
  private ConfigFilesOutcome mockGetGitConfigFileOutcomeWithFiles() {
    GithubStore githubStore =
        GithubStore.builder().paths(ParameterField.createValueField(List.of(SCOPED_FILE_PATH))).build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    ConfigGitFile configGitFile = ConfigGitFile.builder().fileContent(FILE_CONTENT).filePath(SCOPED_FILE_PATH).build();
    List<ConfigGitFile> gitFiles = Arrays.asList(configGitFile);
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(githubStore).gitFiles(gitFiles).build());
    return configFilesOutcome;
  }

  @NotNull
  private ConfigFilesOutcome mockGetConfigFileOutcomeWithSecretFiles() {
    HarnessStore harnessStore =
        HarnessStore.builder().secretFiles(ParameterField.createValueField(List.of(SCOPED_SECRET_FILE_PATH))).build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(harnessStore).build());
    return configFilesOutcome;
  }

  @NotNull
  private ConfigFilesOutcome mockGetConfigFileOutcomeWithFileAndSecretFile() {
    HarnessStore harnessStore = HarnessStore.builder()
                                    .files(ParameterField.createValueField(List.of(SCOPED_FILE_PATH)))
                                    .secretFiles(ParameterField.createValueField(List.of(SCOPED_SECRET_FILE_PATH)))
                                    .build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(harnessStore).build());
    return configFilesOutcome;
  }

  @NotNull
  private ConfigFilesOutcome mockGetConfigFileOutcomeWithMoreFiles() {
    HarnessStore harnessStore = HarnessStore.builder()
                                    .files(ParameterField.createValueField(List.of(SCOPED_FILE_PATH, SCOPED_FILE_PATH)))
                                    .build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    configFilesOutcome.put(CONFIG_FILE_IDENTIFIER,
        ConfigFileOutcome.builder().identifier(CONFIG_FILE_IDENTIFIER).store(harnessStore).build());
    return configFilesOutcome;
  }

  @NotNull
  private ConfigFilesOutcome mockGetConfigFileOutcomeWithMoreSecretFiles() {
    HarnessStore harnessStore =
        HarnessStore.builder()
            .secretFiles(ParameterField.createValueField(List.of(SCOPED_SECRET_FILE_PATH, SCOPED_SECRET_FILE_PATH)))
            .build();
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
