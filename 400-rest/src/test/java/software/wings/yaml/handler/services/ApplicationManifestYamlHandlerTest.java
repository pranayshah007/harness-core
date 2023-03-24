/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.manifest.CustomSourceConfig;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.Builder;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.helpers.ext.kustomize.KustomizeConfig;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.impl.yaml.handler.service.ApplicationManifestYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.WingsTestConstants;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class ApplicationManifestYamlHandlerTest extends YamlHandlerTestBase {
  @Mock private AppService appService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private SettingsService settingsService;
  @Mock private EnvironmentService environmentService;
  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks @Inject private GitFileConfigHelperService gitFileConfigHelperService;
  @InjectMocks @Inject private YamlHelper yamlHelper;
  @InjectMocks @Inject private ApplicationManifestService applicationManifestService;
  @InjectMocks @Inject private ApplicationManifestYamlHandler yamlHandler;

  private ApplicationManifest localApplicationManifest;
  private ApplicationManifest remoteApplicationManifest;
  private ApplicationManifest kustomizeApplicationManifest;
  private ApplicationManifest customApplicationManifest;
  private ApplicationManifest customOpenshiftTemplateApplicationManifest;

  private static final String CONNECTOR_ID = "CONNECTOR_ID";
  private static final String CONNECTOR_NAME = "CONNECTOR_NAME";
  private String localValidYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_MANIFEST\n"
      + "storeType: Local";

  private String remoteYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_MANIFEST\n"
      + "gitFileConfig:\n"
      + "  branch: BRANCH\n"
      + "  connectorName: CONNECTOR_NAME\n"
      + "  filePath: ABC/\n"
      + "  useBranch: true\n"
      + "  useInlineServiceDefinition: false\n"
      + "storeType: Remote";

  private String customManifestYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_MANIFEST\n"
      + "customSourceConfig:\n"
      + "  path: ./local\n"
      + "  script: echo test\n"
      + "storeType: CUSTOM";

  private String customOpenshiftTemplateYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_MANIFEST\n"
      + "customSourceConfig:\n"
      + "  path: ./openshift/local\n"
      + "  script: echo test\n"
      + "storeType: CUSTOM_OPENSHIFT_TEMPLATE";

  private String validYamlFilePath = "Setup/Applications/APP_NAME/Services/SERVICE_NAME/Manifests/Index.yaml";
  private String invalidYamlFilePath = "Setup/Applications/APP_NAME/ServicesInvalid/SERVICE_NAME/Manifests/Index.yaml";

  private String envOverrideLocalValidYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_MANIFEST_VALUES_ENV_OVERRIDE\n"
      + "storeType: Local";
  private String envOverrideRemoteValidYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_MANIFEST_VALUES_ENV_OVERRIDE\n"
      + "gitFileConfig:\n"
      + "  branch: BRANCH\n"
      + "  connectorName: CONNECTOR_NAME\n"
      + "  filePath: ABC/\n"
      + "  useBranch: true\n"
      + "  useInlineServiceDefinition: false\n"
      + "storeType: Remote";
  private String envOverrideValidYamlFilePath = "Setup/Applications/APP_NAME/Environments/ENV_NAME/Values/Index.yaml";

  private String envServiceOverrideLocalValidYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_MANIFEST_VALUES_ENV_SERVICE_OVERRIDE\n"
      + "storeType: Local";
  private String envServiceOverrideRemoteValidYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_MANIFEST_VALUES_ENV_SERVICE_OVERRIDE\n"
      + "gitFileConfig:\n"
      + "  branch: BRANCH\n"
      + "  connectorName: CONNECTOR_NAME\n"
      + "  filePath: ABC/\n"
      + "  useBranch: true\n"
      + "  useInlineServiceDefinition: false\n"
      + "storeType: Remote";
  private String envServiceOverrideValidYamlFilePath =
      "Setup/Applications/APP_NAME/Environments/ENV_NAME/Values/Services/SERVICE_NAME/Index.yaml";

  private String remoteYamlContentWithSkipVersioing = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_MANIFEST\n"
      + "gitFileConfig:\n"
      + "  branch: BRANCH\n"
      + "  connectorName: CONNECTOR_NAME\n"
      + "  filePath: ABC/\n"
      + "  useBranch: true\n"
      + "  useInlineServiceDefinition: false\n"
      + "storeType: Remote\n"
      + "skipVersioningForAllK8sObjects: true";

  private static final String resourcePath = "400-rest/src/test/resources/yaml/ApplicationManifest";
  private static final String kustomizeYamlFile = "kustomize_manifest.yaml";
  ArgumentCaptor<ApplicationManifest> captor = ArgumentCaptor.forClass(ApplicationManifest.class);

  @Before
  public void setUp() {
    localApplicationManifest = ApplicationManifest.builder()
                                   .serviceId(WingsTestConstants.SERVICE_ID)
                                   .storeType(StoreType.Local)
                                   .kind(AppManifestKind.VALUES)
                                   .build();
    remoteApplicationManifest = ApplicationManifest.builder()
                                    .serviceId(WingsTestConstants.SERVICE_ID)
                                    .storeType(StoreType.Remote)
                                    .gitFileConfig(GitFileConfig.builder()
                                                       .filePath("ABC/")
                                                       .branch("BRANCH")
                                                       .useBranch(true)
                                                       .connectorId(CONNECTOR_ID)
                                                       .build())
                                    .kind(AppManifestKind.VALUES)
                                    .build();
    kustomizeApplicationManifest =
        ApplicationManifest.builder()
            .serviceId(SERVICE_ID)
            .storeType(StoreType.KustomizeSourceRepo)
            .gitFileConfig(GitFileConfig.builder().branch("BRANCH").useBranch(true).connectorId(CONNECTOR_ID).build())
            .kind(AppManifestKind.VALUES)
            .build();

    customApplicationManifest =
        ApplicationManifest.builder()
            .serviceId(SERVICE_ID)
            .storeType(StoreType.CUSTOM)
            .customSourceConfig(CustomSourceConfig.builder().path("./local").script("echo test").build())
            .build();

    customOpenshiftTemplateApplicationManifest =
        ApplicationManifest.builder()
            .serviceId(SERVICE_ID)
            .storeType(StoreType.CUSTOM_OPENSHIFT_TEMPLATE)
            .customSourceConfig(CustomSourceConfig.builder().path("./openshift/local").script("echo test").build())
            .build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(appService.getAppByName(ACCOUNT_ID, APP_NAME))
        .thenReturn(Application.Builder.anApplication().uuid(APP_ID).name(APP_NAME).build());
    when(serviceResourceService.getServiceByName(APP_ID, SERVICE_NAME))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build());
    when(serviceResourceService.getWithDetails(any(), any()))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build());
    when(serviceResourceService.exist(any(), any())).thenReturn(true);
    when(environmentService.getEnvironmentByName(APP_ID, ENV_NAME))
        .thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).name(ENV_NAME).build());

    SettingAttribute settingAttribute = Builder.aSettingAttribute()
                                            .withName(CONNECTOR_NAME)
                                            .withUuid(CONNECTOR_ID)
                                            .withValue(GitConfig.builder().build())
                                            .build();
    when(settingsService.get(CONNECTOR_ID)).thenReturn(settingAttribute);
    when(settingsService.getConnectorByName(ACCOUNT_ID, APP_ID, CONNECTOR_NAME)).thenReturn(settingAttribute);

    when(featureFlagService.isEnabled(FeatureName.CUSTOM_MANIFEST, ACCOUNT_ID)).thenReturn(true);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCRUDAndGetForLocal() throws IOException {
    ChangeContext<ApplicationManifest.Yaml> changeContext =
        createChangeContext(localValidYamlContent, validYamlFilePath);

    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(localValidYamlContent, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(Service.builder().build());
    ApplicationManifest savedApplicationManifest = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareAppManifest(localApplicationManifest, savedApplicationManifest);

    validateYamlContent(localValidYamlContent, localApplicationManifest);

    ApplicationManifest applicationManifestFromGet = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    compareAppManifest(localApplicationManifest, applicationManifestFromGet);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCRUDAndGetForRemote() throws IOException {
    ChangeContext<ApplicationManifest.Yaml> changeContext = createChangeContext(remoteYamlContent, validYamlFilePath);

    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(remoteYamlContent, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(Service.builder().build());
    ApplicationManifest savedApplicationManifest = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareAppManifest(remoteApplicationManifest, savedApplicationManifest);

    validateYamlContent(remoteYamlContent, remoteApplicationManifest);

    ApplicationManifest applicationManifestFromGet = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    compareAppManifest(remoteApplicationManifest, applicationManifestFromGet);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCRUDAndGetForCustomManifest() throws IOException {
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(Service.builder().build());
    testCRUDAndGetForCustomManifest(customManifestYamlContent, customApplicationManifest);
    testCRUDAndGetForCustomManifest(customOpenshiftTemplateYamlContent, customOpenshiftTemplateApplicationManifest);
  }

  private void testCRUDAndGetForCustomManifest(String yamlContent, ApplicationManifest appManifest) throws IOException {
    ChangeContext<ApplicationManifest.Yaml> changeContext = createChangeContext(yamlContent, validYamlFilePath);

    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(yamlContent, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    ApplicationManifest savedApplicationManifest = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareAppManifest(appManifest, savedApplicationManifest);

    validateYamlContent(yamlContent, appManifest);

    ApplicationManifest applicationManifestFromGet = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    compareAppManifest(appManifest, applicationManifestFromGet);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInvalidCustomManifestConfig() throws IOException {
    String localManifestWithCustomManifestConfig = "harnessApiVersion: '1.0'\n"
        + "type: APPLICATION_MANIFEST\n"
        + "customSourceConfig:\n"
        + "  path: ./local\n"
        + "  script: echo test\n"
        + "storeType: Local";

    ChangeContext<ApplicationManifest.Yaml> changeContext =
        createChangeContext(localManifestWithCustomManifestConfig, validYamlFilePath);
    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(localManifestWithCustomManifestConfig, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    assertThatThrownBy(() -> yamlHandler.upsertFromYaml(changeContext, asList(changeContext)))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("CustomSourceConfig should only be used with Custom store type");

    // Feature Flag is disabled
    when(featureFlagService.isEnabled(FeatureName.CUSTOM_MANIFEST, ACCOUNT_ID)).thenReturn(false);
    changeContext.setYaml(
        (ApplicationManifest.Yaml) getYaml(customManifestYamlContent, ApplicationManifest.Yaml.class));
    assertThatThrownBy(() -> yamlHandler.upsertFromYaml(changeContext, asList(changeContext)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Custom Manifest feature is not enabled. Please contact Harness support");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testFailures() throws IOException {
    ChangeContext<ApplicationManifest.Yaml> changeContext =
        createChangeContext(localValidYamlContent, invalidYamlFilePath);

    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(localValidYamlContent, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
  }

  private void validateYamlContent(String yamlFileContent, ApplicationManifest applicationManifest) {
    ApplicationManifest.Yaml yaml = yamlHandler.toYaml(applicationManifest, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isNotNull();

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlFileContent);
  }

  private void compareAppManifest(ApplicationManifest lhs, ApplicationManifest rhs) {
    assertThat(rhs.getStoreType()).isEqualTo(lhs.getStoreType());
    assertThat(rhs.getGitFileConfig()).isEqualTo(lhs.getGitFileConfig());
    if (lhs.getGitFileConfig() != null) {
      assertThat(rhs.getGitFileConfig().getConnectorId()).isEqualTo(lhs.getGitFileConfig().getConnectorId());
      assertThat(rhs.getGitFileConfig().getBranch()).isEqualTo(lhs.getGitFileConfig().getBranch());
      assertThat(rhs.getGitFileConfig().getFilePath()).isEqualTo(lhs.getGitFileConfig().getFilePath());
      assertThat(rhs.getGitFileConfig().isUseBranch()).isEqualTo(lhs.getGitFileConfig().isUseBranch());
      assertThat(rhs.getGitFileConfig().getConnectorName()).isEqualTo(lhs.getGitFileConfig().getConnectorName());
    }
  }

  private ChangeContext<ApplicationManifest.Yaml> createChangeContext(String fileContent, String filePath) {
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(fileContent);
    gitFileChange.setFilePath(filePath);
    gitFileChange.setAccountId(ACCOUNT_ID);

    ChangeContext<ApplicationManifest.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.APPLICATION_MANIFEST);
    changeContext.setYamlSyncHandler(yamlHandler);

    return changeContext;
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCRUDAndGetForEnvOverrideWithLocalStoreType() throws IOException {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .envId(WingsTestConstants.ENV_ID)
                                                  .storeType(StoreType.Local)
                                                  .kind(AppManifestKind.VALUES)
                                                  .build();

    ChangeContext<ApplicationManifest.Yaml> changeContext =
        createChangeContext(envOverrideLocalValidYamlContent, envOverrideValidYamlFilePath);

    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(envOverrideLocalValidYamlContent, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    ApplicationManifest savedApplicationManifest = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareAppManifest(applicationManifest, savedApplicationManifest);

    validateYamlContent(envOverrideLocalValidYamlContent, applicationManifest);

    ApplicationManifest applicationManifestFromGet = yamlHandler.get(ACCOUNT_ID, envOverrideValidYamlFilePath);
    compareAppManifest(applicationManifest, applicationManifestFromGet);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCRUDAndGetForEnvOverrideWithRemoteStoreType() throws IOException {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .envId(WingsTestConstants.ENV_ID)
                                                  .storeType(StoreType.Remote)
                                                  .gitFileConfig(GitFileConfig.builder()
                                                                     .filePath("ABC/")
                                                                     .branch("BRANCH")
                                                                     .useBranch(true)
                                                                     .connectorId(CONNECTOR_ID)
                                                                     .build())
                                                  .kind(AppManifestKind.VALUES)
                                                  .build();

    ChangeContext<ApplicationManifest.Yaml> changeContext =
        createChangeContext(envOverrideRemoteValidYamlContent, envOverrideValidYamlFilePath);

    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(envOverrideRemoteValidYamlContent, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    ApplicationManifest savedApplicationManifest = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareAppManifest(applicationManifest, savedApplicationManifest);

    validateYamlContent(envOverrideRemoteValidYamlContent, applicationManifest);

    ApplicationManifest applicationManifestFromGet = yamlHandler.get(ACCOUNT_ID, envOverrideValidYamlFilePath);
    compareAppManifest(applicationManifest, applicationManifestFromGet);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCRUDAndGetForEnvServiceOverrideWithLocalStoreType() throws IOException {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .envId(ENV_ID)
                                                  .serviceId(SERVICE_ID)
                                                  .storeType(StoreType.Local)
                                                  .kind(AppManifestKind.VALUES)
                                                  .build();

    ChangeContext<ApplicationManifest.Yaml> changeContext =
        createChangeContext(envServiceOverrideLocalValidYamlContent, envServiceOverrideValidYamlFilePath);

    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(envServiceOverrideLocalValidYamlContent, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    ApplicationManifest savedApplicationManifest = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareAppManifest(applicationManifest, savedApplicationManifest);

    validateYamlContent(envServiceOverrideLocalValidYamlContent, applicationManifest);

    ApplicationManifest applicationManifestFromGet = yamlHandler.get(ACCOUNT_ID, envServiceOverrideValidYamlFilePath);
    compareAppManifest(applicationManifest, applicationManifestFromGet);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCRUDAndGetForEnvServiceOverrideWithRemoteStoreType() throws IOException {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .envId(WingsTestConstants.ENV_ID)
                                                  .serviceId(SERVICE_ID)
                                                  .storeType(StoreType.Remote)
                                                  .gitFileConfig(GitFileConfig.builder()
                                                                     .filePath("ABC/")
                                                                     .branch("BRANCH")
                                                                     .useBranch(true)
                                                                     .connectorId(CONNECTOR_ID)
                                                                     .build())
                                                  .kind(AppManifestKind.VALUES)
                                                  .build();

    ChangeContext<ApplicationManifest.Yaml> changeContext =
        createChangeContext(envServiceOverrideRemoteValidYamlContent, envServiceOverrideValidYamlFilePath);

    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(envServiceOverrideRemoteValidYamlContent, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    ApplicationManifest savedApplicationManifest = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareAppManifest(applicationManifest, savedApplicationManifest);

    validateYamlContent(envServiceOverrideRemoteValidYamlContent, applicationManifest);

    ApplicationManifest applicationManifestFromGet = yamlHandler.get(ACCOUNT_ID, envServiceOverrideValidYamlFilePath);
    compareAppManifest(applicationManifest, applicationManifestFromGet);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCRUDFileAndGetForKustomizeManifest() throws IOException {
    String kustomizeYamlContent = readResourceFile(kustomizeYamlFile);
    ApplicationManifest kustomizeManifest = kustomizeApplicationManifest.cloneInternal();
    kustomizeManifest.setKustomizeConfig(KustomizeConfig.builder().kustomizeDirPath("a").pluginRootDir("b").build());
    ChangeContext<ApplicationManifest.Yaml> changeContext =
        createChangeContext(kustomizeYamlContent, validYamlFilePath);

    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(kustomizeYamlContent, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(Service.builder().build());
    ApplicationManifest savedApplicationManifest = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareAppManifest(kustomizeManifest, savedApplicationManifest);

    validateYamlContent(kustomizeYamlContent, kustomizeManifest);

    ApplicationManifest applicationManifestFromGet = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    compareAppManifest(kustomizeManifest, applicationManifestFromGet);
  }

  /*
  If this test breaks, make sure you have added the new attribute in the yaml class as well. After that
  we also need to update the toYaml and fromYaml method of the corresponding Yaml Handler.
   */
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testFieldsInYaml() {
    int attributeDiff = attributeDiff(ApplicationManifest.class, ApplicationManifest.Yaml.class);
    assertThat(attributeDiff).isEqualTo(13);
  }

  private String readResourceFile(String fileName) throws IOException {
    File yamlFile = null;
    yamlFile = new File(resourcePath + PATH_DELIMITER + fileName);

    return FileUtils.readFileToString(yamlFile, "UTF-8");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSkipVersioningForAllK8sObjectException() throws IOException {
    doReturn(false).when(serviceResourceService).isK8sV2Service(any(), any());
    ChangeContext<ApplicationManifest.Yaml> changeContext =
        createChangeContext(remoteYamlContentWithSkipVersioing, validYamlFilePath);

    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(remoteYamlContentWithSkipVersioing, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    assertThatThrownBy(() -> yamlHandler.upsertFromYaml(changeContext, asList(changeContext)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("SkipVersioning is only allowed for k8s services at the service level");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSkipVersioningForAllK8sObject() throws IOException {
    doReturn(true).when(serviceResourceService).isK8sV2Service(any(), any());
    ChangeContext<ApplicationManifest.Yaml> changeContext =
        createChangeContext(remoteYamlContentWithSkipVersioing, validYamlFilePath);

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(Service.builder().build());
    ApplicationManifest.Yaml yamlObject =
        (ApplicationManifest.Yaml) getYaml(remoteYamlContentWithSkipVersioing, ApplicationManifest.Yaml.class);
    changeContext.setYaml(yamlObject);

    ApplicationManifest applicationManifest = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(applicationManifest.getSkipVersioningForAllK8sObjects()).isTrue();
  }
}
