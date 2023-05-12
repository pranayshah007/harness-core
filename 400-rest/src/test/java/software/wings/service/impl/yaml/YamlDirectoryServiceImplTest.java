/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.appmanifest.AppManifestKind.K8S_MANIFEST;
import static software.wings.beans.appmanifest.AppManifestKind.OC_PARAMS;
import static software.wings.beans.appmanifest.AppManifestKind.VALUES;
import static software.wings.beans.appmanifest.StoreType.HelmChartRepo;
import static software.wings.beans.template.TemplateGallery.GalleryKey;
import static software.wings.beans.yaml.YamlConstants.APPLICATION_TEMPLATE_LIBRARY_FOLDER;
import static software.wings.beans.yaml.YamlConstants.GLOBAL_TEMPLATE_LIBRARY_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.common.TemplateConstants.SHELL_SCRIPT;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.CollectionUtils;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.PcfConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.AppManifestSource;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateFolder;
import software.wings.common.TemplateConstants;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.yaml.YamlVersion;
import software.wings.yaml.directory.AccountLevelYamlNode;
import software.wings.yaml.directory.AppLevelYamlNode;
import software.wings.yaml.directory.DirectoryNode;
import software.wings.yaml.directory.DirectoryPath;
import software.wings.yaml.directory.FolderNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class YamlDirectoryServiceImplTest extends WingsBaseTest {
  @Mock private YamlGitService yamlGitSyncService;
  @Mock private TemplateService templateService;
  @Mock private AppService appService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private TemplateGalleryService templateGalleryService;
  @Mock private EnvironmentService environmentService;
  @InjectMocks private YamlDirectoryServiceImpl yamlDirectoryService = new YamlDirectoryServiceImpl();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_doTemplateLibraryForApp() {
    final String accountid = "accountid";
    final String appid = "appid";
    final Application application = anApplication().accountId(accountid).appId(appid).build();
    DirectoryPath directoryPath = new DirectoryPath("/Setup/Applications/app");
    TemplateFolder templateFolder = TemplateFolder.builder().uuid("root_folder").name("harness").build();
    doReturn(templateFolder)
        .when(templateService)
        .getTemplateTree(any(), any(), any(), eq(TemplateConstants.TEMPLATE_TYPES_WITH_YAML_SUPPORT));
    final Template template1 = Template.builder().name("template1").folderId("root_folder").type(SHELL_SCRIPT).build();
    final Template template2 = Template.builder().folderId("root_folder").name("template2").type(SHELL_SCRIPT).build();
    PageResponse<Template> pageResponse = aPageResponse().withResponse(asList(template1, template2)).build();
    doReturn(pageResponse).when(templateService).list(any(PageRequest.class), anyList(), anyString(), anyBoolean());
    doReturn(GalleryKey.ACCOUNT_TEMPLATE_GALLERY).when(templateGalleryService).getAccountGalleryKey();

    final FolderNode folderNode =
        yamlDirectoryService.doTemplateLibraryForApp(application, directoryPath, false, Collections.EMPTY_SET);
    assertThat(folderNode.getName()).isEqualTo(APPLICATION_TEMPLATE_LIBRARY_FOLDER);
    assertThat(folderNode.getAccountId()).isEqualTo(accountid);
    final List<DirectoryNode> children = folderNode.getChildren();
    assertThat(children.size()).isEqualTo(1);
    assertThat(children.get(0).getName()).isEqualTo("harness");
    final List<DirectoryNode> leaves = ((FolderNode) children.get(0)).getChildren();
    assertThat(leaves.size()).isEqualTo(2);
    final AppLevelYamlNode templateNode = (AppLevelYamlNode) leaves.get(0);
    assertThat(templateNode.getName()).isEqualTo("template1.yaml");
    assertThat(templateNode.getAccountId()).isEqualTo(accountid);
    assertThat(templateNode.getAppId()).isEqualTo(appid);
    final AppLevelYamlNode templateNode2 = (AppLevelYamlNode) leaves.get(1);

    assertThat(templateNode2.getName()).isEqualTo("template2.yaml");
    assertThat(templateNode2.getAccountId()).isEqualTo(accountid);
    assertThat(templateNode2.getAppId()).isEqualTo(appid);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_doTemplateLibraryForApp_() {
    final String accountid = "accountid";
    final String appid = "appid";
    final Application application = anApplication().accountId(accountid).appId(appid).build();
    DirectoryPath directoryPath = new DirectoryPath("/Setup/Applications/app");
    TemplateFolder templateFolder = TemplateFolder.builder().uuid("root_folder").name("harness").build();
    doReturn(templateFolder)
        .when(templateService)
        .getTemplateTree(any(), any(), any(), eq(TemplateConstants.TEMPLATE_TYPES_WITH_YAML_SUPPORT));
    PageResponse<Template> pageResponse = aPageResponse().build();
    doReturn(pageResponse).when(templateService).list(any(PageRequest.class), anyList(), anyString(), anyBoolean());
    doReturn(GalleryKey.ACCOUNT_TEMPLATE_GALLERY).when(templateGalleryService).getAccountGalleryKey();

    final FolderNode folderNode =
        yamlDirectoryService.doTemplateLibraryForApp(application, directoryPath, false, Collections.EMPTY_SET);
    assertThat(folderNode.getName()).isEqualTo(APPLICATION_TEMPLATE_LIBRARY_FOLDER);
    assertThat(folderNode.getAccountId()).isEqualTo(accountid);
    final List<DirectoryNode> children = folderNode.getChildren();
    assertThat(children.size()).isEqualTo(1);
    assertThat(children.get(0).getName()).isEqualTo("harness");
    final List<DirectoryNode> leaves = ((FolderNode) children.get(0)).getChildren();
    assertThat(leaves.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_doTemplateLibraryForApp_GLOBAL() {
    final String accountid = "accountid";
    DirectoryPath directoryPath = new DirectoryPath("/Setup");

    final Template template1 = Template.builder().name("template1").folderId("root_folder").type(SHELL_SCRIPT).build();
    final Template template2 =
        Template.builder().folderId("child_folder").name("child_folder_template").type(SHELL_SCRIPT).build();
    PageResponse<Template> pageResponse = aPageResponse().withResponse(asList(template1, template2)).build();
    doReturn(pageResponse).when(templateService).list(any(PageRequest.class), anyList(), anyString(), anyBoolean());
    doReturn(GalleryKey.ACCOUNT_TEMPLATE_GALLERY).when(templateGalleryService).getAccountGalleryKey();

    TemplateFolder childFolder = TemplateFolder.builder().uuid("child_folder").name("child_folder_1").build();
    TemplateFolder templateFolder = TemplateFolder.builder()
                                        .uuid("root_folder")
                                        .name("harness")
                                        .children(Collections.singletonList(childFolder))
                                        .build();
    doReturn(templateFolder)
        .when(templateService)
        .getTemplateTree(any(), any(), any(), eq(TemplateConstants.TEMPLATE_TYPES_WITH_YAML_SUPPORT));

    final FolderNode folderNode =
        yamlDirectoryService.doTemplateLibrary(accountid, directoryPath.clone(), GLOBAL_APP_ID,
            GLOBAL_TEMPLATE_LIBRARY_FOLDER, YamlVersion.Type.GLOBAL_TEMPLATE_LIBRARY, false, Collections.EMPTY_SET);
    assertThat(folderNode.getName()).isEqualTo(GLOBAL_TEMPLATE_LIBRARY_FOLDER);
    assertThat(folderNode.getAccountId()).isEqualTo(accountid);
    assertThat(folderNode.getDirectoryPath().getPath()).isEqualTo("/Setup/" + GLOBAL_TEMPLATE_LIBRARY_FOLDER);
    final List<DirectoryNode> children = folderNode.getChildren();
    assertThat(children.size()).isEqualTo(1);
    assertThat(children.get(0).getName()).isEqualTo("harness");
    final List<DirectoryNode> harnessChildren = ((FolderNode) children.get(0)).getChildren();
    assertThat(harnessChildren.size()).isEqualTo(2);
    final AccountLevelYamlNode templateNode = (AccountLevelYamlNode) harnessChildren.get(1);
    assertThat(templateNode.getAccountId()).isEqualTo(accountid);
    assertThat(templateNode.getName()).isEqualTo("template1.yaml");
    final FolderNode childFolderNode = (FolderNode) harnessChildren.get(0);
    assertThat(childFolderNode.getAccountId()).isEqualTo(accountid);
    assertThat(childFolderNode.getName()).isEqualTo("child_folder_1");

    assertThat(childFolderNode.getChildren().size()).isEqualTo(1);

    final AccountLevelYamlNode template2Node = (AccountLevelYamlNode) childFolderNode.getChildren().get(0);
    assertThat(template2Node.getAccountId()).isEqualTo(accountid);
    assertThat(template2Node.getName()).isEqualTo("child_folder_template.yaml");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetRootByTemplate() {
    Template template = Template.builder().appId(GLOBAL_APP_ID).build();
    String folderName = "Harness/name";
    when(templateService.getTemplateFolderPathString(template)).thenReturn(folderName);
    assertThat(yamlDirectoryService.getRootPathByTemplate(template))
        .isEqualTo(yamlDirectoryService.getRootPath() + PATH_DELIMITER + GLOBAL_TEMPLATE_LIBRARY_FOLDER + PATH_DELIMITER
            + folderName);
    template.setAppId("random");
    Application mockApp = anApplication().name("random").build();
    when(appService.get("random")).thenReturn(mockApp);
    assertThat(yamlDirectoryService.getRootPathByTemplate(template))
        .isEqualTo(yamlDirectoryService.getRootPathByApp(mockApp) + PATH_DELIMITER + APPLICATION_TEMPLATE_LIBRARY_FOLDER
            + PATH_DELIMITER + folderName);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateEnvHelmOverridesFolder() {
    Environment env = anEnvironment().uuid(ENV_ID).appId(APP_ID).uuid(ENV_ID).build();
    DirectoryPath dirPath = new DirectoryPath("Setup/Applications/Test/Environments/Dev");
    testHelmOverrideFolderIfNonePresent(env, dirPath);
    testHelmOverrideFolderIfOnlyServiceOverridePresent(env, dirPath);
    testHelmOverrideFolderIfOnlyGlobalOverridePresent(env, dirPath);
    testHelmOverrideFolderIfAllTypeOfOverridesPresent(env, dirPath);
  }

  private void testHelmOverrideFolderIfNonePresent(Environment env, DirectoryPath dirPath) {
    doReturn(null)
        .doReturn(Collections.emptyList())
        .when(applicationManifestService)
        .getAllByEnvIdAndKind(anyString(), anyString(), eq(AppManifestKind.HELM_CHART_OVERRIDE));
    doReturn(null)
        .when(applicationManifestService)
        .getByEnvId(anyString(), anyString(), eq(AppManifestKind.HELM_CHART_OVERRIDE));

    assertThat(yamlDirectoryService.generateEnvHelmOverridesFolder(ACCOUNT_ID, env, dirPath)).isNull();
    assertThat(yamlDirectoryService.generateEnvHelmOverridesFolder(ACCOUNT_ID, env, dirPath)).isNull();
  }

  private void testHelmOverrideFolderIfOnlyServiceOverridePresent(Environment env, DirectoryPath dirPath) {
    ApplicationManifest serviceManifest1 =
        ApplicationManifest.builder().envId(ENV_ID).storeType(HelmChartRepo).serviceId("sid-1").build();
    ApplicationManifest serviceManifest2 =
        ApplicationManifest.builder().envId(ENV_ID).storeType(HelmChartRepo).serviceId("sid-2").build();

    doReturn(asList(serviceManifest1, serviceManifest2))
        .when(applicationManifestService)
        .getAllByEnvIdAndKind(anyString(), anyString(), eq(AppManifestKind.HELM_CHART_OVERRIDE));
    doReturn(null)
        .when(applicationManifestService)
        .getByEnvId(anyString(), anyString(), eq(AppManifestKind.HELM_CHART_OVERRIDE));
    doReturn(Service.builder().name("s-1").uuid("sid-1").appId(APP_ID).build())
        .when(serviceResourceService)
        .get(APP_ID, "sid-1", false);
    doReturn(Service.builder().name("s-2").uuid("sid-2").appId(APP_ID).build())
        .when(serviceResourceService)
        .get(APP_ID, "sid-2", false);

    final FolderNode folder = yamlDirectoryService.generateEnvHelmOverridesFolder(ACCOUNT_ID, env, dirPath);

    assertThat(getFolderStructure(folder))
        .containsExactly("Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services",
            "Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services/s-1",
            "Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services/s-1/Index.yaml",
            "Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services/s-2",
            "Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services/s-2/Index.yaml");
  }

  private void testHelmOverrideFolderIfOnlyGlobalOverridePresent(Environment env, DirectoryPath dirPath) {
    ApplicationManifest globalManifest = ApplicationManifest.builder().envId(ENV_ID).storeType(HelmChartRepo).build();

    doReturn(asList(globalManifest))
        .when(applicationManifestService)
        .getAllByEnvIdAndKind(anyString(), anyString(), eq(AppManifestKind.HELM_CHART_OVERRIDE));
    doReturn(globalManifest)
        .when(applicationManifestService)
        .getByEnvId(anyString(), anyString(), eq(AppManifestKind.HELM_CHART_OVERRIDE));

    final FolderNode folder = yamlDirectoryService.generateEnvHelmOverridesFolder(ACCOUNT_ID, env, dirPath);

    assertThat(getFolderStructure(folder))
        .containsExactly("Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Index.yaml",
            "Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services");
  }

  private void testHelmOverrideFolderIfAllTypeOfOverridesPresent(Environment env, DirectoryPath dirPath) {
    ApplicationManifest serviceManifest1 =
        ApplicationManifest.builder().envId(ENV_ID).storeType(HelmChartRepo).serviceId("sid-1").build();
    ApplicationManifest serviceManifest2 =
        ApplicationManifest.builder().envId(ENV_ID).storeType(HelmChartRepo).serviceId("sid-2").build();
    ApplicationManifest globalManifest = ApplicationManifest.builder().envId(ENV_ID).storeType(HelmChartRepo).build();

    doReturn(asList(serviceManifest1, serviceManifest2, globalManifest))
        .when(applicationManifestService)
        .getAllByEnvIdAndKind(anyString(), anyString(), eq(AppManifestKind.HELM_CHART_OVERRIDE));
    doReturn(globalManifest)
        .when(applicationManifestService)
        .getByEnvId(anyString(), anyString(), eq(AppManifestKind.HELM_CHART_OVERRIDE));
    doReturn(Service.builder().name("s-1").uuid("sid-1").appId(APP_ID).build())
        .when(serviceResourceService)
        .get(APP_ID, "sid-1", false);
    doReturn(Service.builder().name("s-2").uuid("sid-2").appId(APP_ID).build())
        .when(serviceResourceService)
        .get(APP_ID, "sid-2", false);

    final FolderNode folder = yamlDirectoryService.generateEnvHelmOverridesFolder(ACCOUNT_ID, env, dirPath);

    assertThat(getFolderStructure(folder))
        .containsExactly("Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Index.yaml",
            "Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services",
            "Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services/s-1",
            "Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services/s-1/Index.yaml",
            "Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services/s-2",
            "Setup/Applications/Test/Environments/Dev/Helm Chart Overrides/Services/s-2/Index.yaml");
  }

  private List<String> getFolderStructure(FolderNode fn) {
    List<String> structure = new ArrayList<>();
    CollectionUtils.emptyIfNull(fn.getChildren()).forEach(child -> {
      if (child instanceof FolderNode) {
        structure.add(child.getDirectoryPath().getPath());
        structure.addAll(getFolderStructure((FolderNode) child));
      } else {
        structure.add(child.getDirectoryPath().getPath());
      }
    });
    return structure;
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGenerateEnvAzureAppSettingsOverridesFolderManifestNonPresent() {
    Environment env = anEnvironment().uuid(ENV_ID).appId(APP_ID).uuid(ENV_ID).build();
    DirectoryPath dirPath = new DirectoryPath("Setup/Applications/Test/Environments/Dev");
    doReturn(null)
        .when(applicationManifestService)
        .getAllByEnvIdAndKind(anyString(), anyString(), eq(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE));
    FolderNode envAppSettingsOverridesFolder =
        yamlDirectoryService.generateEnvAzureAppSettingsOverridesFolder(ACCOUNT_ID, env, dirPath);

    assertThat(envAppSettingsOverridesFolder).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGenerateEnvAzureAppSettingsOverridesFolderWithEnvOverrides() {
    Environment env = anEnvironment().uuid(ENV_ID).appId(APP_ID).uuid(ENV_ID).build();
    DirectoryPath dirPath = new DirectoryPath("Setup/Applications/Test/Environments/Dev");
    mockAzureAppServiceOverride(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE);

    doReturn(null).when(serviceResourceService).get(APP_ID, SERVICE_ID, false);

    FolderNode envAppSettingsOverridesFolder =
        yamlDirectoryService.generateEnvAzureAppSettingsOverridesFolder(ACCOUNT_ID, env, dirPath);

    assertThat(envAppSettingsOverridesFolder).isNotNull();
    assertThat(envAppSettingsOverridesFolder.getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/Test/Environments/Dev/App Settings Overrides");
    List<String> folderStructure = getFolderStructure(envAppSettingsOverridesFolder);
    assertThat(folderStructure.size()).isEqualTo(4);
    assertThat(folderStructure)
        .containsExactly("Setup/Applications/Test/Environments/Dev/App Settings Overrides/Index.yaml",
            "Setup/Applications/Test/Environments/Dev/App Settings Overrides/appsettings",
            "Setup/Applications/Test/Environments/Dev/App Settings Overrides/connstrings",
            "Setup/Applications/Test/Environments/Dev/App Settings Overrides/Services");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGenerateEnvAzureAppSettingsOverridesFolderWithServiceOverrides() {
    Environment env = anEnvironment().uuid(ENV_ID).appId(APP_ID).uuid(ENV_ID).build();
    DirectoryPath dirPath = new DirectoryPath("Setup/Applications/Test/Environments/Dev");
    mockAzureAppServiceOverride(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE);

    doReturn(Service.builder().name(SERVICE_NAME).uuid(SERVICE_ID).appId(APP_ID).build())
        .when(serviceResourceService)
        .get(APP_ID, SERVICE_ID, false);

    FolderNode envAppSettingsOverridesFolder =
        yamlDirectoryService.generateEnvAzureAppSettingsOverridesFolder(ACCOUNT_ID, env, dirPath);

    assertThat(envAppSettingsOverridesFolder).isNotNull();
    assertThat(envAppSettingsOverridesFolder.getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/Test/Environments/Dev/App Settings Overrides");
    List<String> folderStructure = getFolderStructure(envAppSettingsOverridesFolder);
    assertThat(folderStructure.size()).isEqualTo(8);
    assertThat(folderStructure)
        .containsExactly("Setup/Applications/Test/Environments/Dev/App Settings Overrides/Index.yaml",
            "Setup/Applications/Test/Environments/Dev/App Settings Overrides/appsettings",
            "Setup/Applications/Test/Environments/Dev/App Settings Overrides/connstrings",
            "Setup/Applications/Test/Environments/Dev/App Settings Overrides/Services",
            "Setup/Applications/Test/Environments/Dev/App Settings Overrides/Services/SERVICE_NAME",
            "Setup/Applications/Test/Environments/Dev/App Settings Overrides/Services/SERVICE_NAME/Index.yaml",
            "Setup/Applications/Test/Environments/Dev/App Settings Overrides/Services/SERVICE_NAME/appsettings",
            "Setup/Applications/Test/Environments/Dev/App Settings Overrides/Services/SERVICE_NAME/connstrings");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGenerateEnvAzureConnStringsOverridesFolderManifestNonPresent() {
    Environment env = anEnvironment().uuid(ENV_ID).appId(APP_ID).uuid(ENV_ID).build();
    DirectoryPath dirPath = new DirectoryPath("Setup/Applications/Test/Environments/Dev");
    doReturn(null)
        .when(applicationManifestService)
        .getAllByEnvIdAndKind(anyString(), anyString(), eq(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE));
    FolderNode envConnStringsOverridesFolder =
        yamlDirectoryService.generateEnvAzureConnStringsOverridesFolder(ACCOUNT_ID, env, dirPath);

    assertThat(envConnStringsOverridesFolder).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGenerateEnvAzureConnStringsOverridesFolderWithEnvOverrides() {
    Environment env = anEnvironment().uuid(ENV_ID).appId(APP_ID).uuid(ENV_ID).build();
    DirectoryPath dirPath = new DirectoryPath("Setup/Applications/Test/Environments/Dev");
    mockAzureAppServiceOverride(AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE);

    doReturn(null).when(serviceResourceService).get(APP_ID, SERVICE_ID, false);

    FolderNode envAppSettingsOverridesFolder =
        yamlDirectoryService.generateEnvAzureConnStringsOverridesFolder(ACCOUNT_ID, env, dirPath);

    assertThat(envAppSettingsOverridesFolder).isNotNull();
    assertThat(envAppSettingsOverridesFolder.getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/Test/Environments/Dev/Conn Strings Overrides");
    List<String> folderStructure = getFolderStructure(envAppSettingsOverridesFolder);
    assertThat(folderStructure.size()).isEqualTo(4);
    assertThat(folderStructure)
        .containsExactly("Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/Index.yaml",
            "Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/appsettings",
            "Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/connstrings",
            "Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/Services");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGenerateEnvAzureConnStringsOverridesFolderrWithServiceOverrides() {
    Environment env = anEnvironment().uuid(ENV_ID).appId(APP_ID).uuid(ENV_ID).build();
    DirectoryPath dirPath = new DirectoryPath("Setup/Applications/Test/Environments/Dev");
    mockAzureAppServiceOverride(AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE);

    doReturn(Service.builder().name(SERVICE_NAME).uuid(SERVICE_ID).appId(APP_ID).build())
        .when(serviceResourceService)
        .get(APP_ID, SERVICE_ID, false);

    FolderNode envAppSettingsOverridesFolder =
        yamlDirectoryService.generateEnvAzureConnStringsOverridesFolder(ACCOUNT_ID, env, dirPath);

    assertThat(envAppSettingsOverridesFolder).isNotNull();
    assertThat(envAppSettingsOverridesFolder.getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/Test/Environments/Dev/Conn Strings Overrides");
    List<String> folderStructure = getFolderStructure(envAppSettingsOverridesFolder);
    assertThat(folderStructure.size()).isEqualTo(8);
    assertThat(folderStructure)
        .containsExactly("Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/Index.yaml",
            "Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/appsettings",
            "Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/connstrings",
            "Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/Services",
            "Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/Services/SERVICE_NAME",
            "Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/Services/SERVICE_NAME/Index.yaml",
            "Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/Services/SERVICE_NAME/appsettings",
            "Setup/Applications/Test/Environments/Dev/Conn Strings Overrides/Services/SERVICE_NAME/connstrings");
  }

  private void mockAzureAppServiceOverride(AppManifestKind kind) {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .envId(ENV_ID)
                                                  .storeType(StoreType.Local)
                                                  .serviceId(SERVICE_ID)
                                                  .kind(AppManifestKind.AZURE_APP_SERVICE_MANIFEST)
                                                  .build();

    doReturn(Collections.singletonList(applicationManifest))
        .when(applicationManifestService)
        .getAllByEnvIdAndKind(anyString(), anyString(), eq(kind));

    doReturn(applicationManifest).when(applicationManifestService).getByEnvId(anyString(), anyString(), eq(kind));
    List<ManifestFile> manifestFiles = new ArrayList<>();
    manifestFiles.add(ManifestFile.builder().fileName("appsettings").build());
    manifestFiles.add(ManifestFile.builder().fileName("connstrings").build());
    doReturn(manifestFiles).when(applicationManifestService).getManifestFilesByAppManifestId(any(), any());
  }

  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testPathForSpotInstAndPcfConfig() {
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withValue(PcfConfig.builder().build()).build();
    String path = yamlDirectoryService.getRootPathBySettingAttribute(settingAttribute);
    assertThat(path).isEqualTo("Setup/Cloud Providers");

    settingAttribute.setValue(SpotInstConfig.builder().build());
    yamlDirectoryService.getRootPathBySettingAttribute(settingAttribute);
    assertThat(path).isEqualTo("Setup/Cloud Providers");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getRootPathByManifestFileForValues() {
    rootPathForValuesService();
    rootPathForValuesEnvService();
    rootPathForValuesEnv();
  }

  private void rootPathForValuesService() {
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .serviceId(SERVICE_ID)
                                          .kind(VALUES)
                                          .storeType(StoreType.Remote)
                                          .accountId(ACCOUNT_ID)
                                          .build();
    appManifest.setAppId(APP_ID);

    doReturn(AppManifestSource.SERVICE).when(applicationManifestService).getAppManifestType(appManifest);
    doReturn(anApplication().name("My App").accountId(ACCOUNT_ID).uuid(APP_ID).build()).when(appService).get(APP_ID);
    doReturn(Service.builder().uuid(SERVICE_ID).name("backend").build())
        .when(serviceResourceService)
        .getWithDetails(APP_ID, SERVICE_ID);

    final String path = yamlDirectoryService.getRootPathByManifestFile(ManifestFile.builder().build(), appManifest);

    assertThat(path).isEqualTo("Setup/Applications/My App/Services/backend/Values");
  }

  private void rootPathForValuesEnvService() {
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .serviceId(SERVICE_ID)
                                          .envId(ENV_ID)
                                          .kind(VALUES)
                                          .storeType(StoreType.Remote)
                                          .accountId(ACCOUNT_ID)
                                          .build();
    appManifest.setAppId(APP_ID);

    doReturn(AppManifestSource.ENV_SERVICE).when(applicationManifestService).getAppManifestType(appManifest);
    doReturn(anApplication().name("My App").accountId(ACCOUNT_ID).uuid(APP_ID).build()).when(appService).get(APP_ID);
    doReturn(Service.builder().uuid(SERVICE_ID).name("backend").build())
        .when(serviceResourceService)
        .getWithDetails(APP_ID, SERVICE_ID);
    doReturn(anEnvironment().uuid(ENV_ID).appId(APP_ID).accountId(ACCOUNT_ID).name("Production").build())
        .when(environmentService)
        .get(APP_ID, ENV_ID, true);

    final String path = yamlDirectoryService.getRootPathByManifestFile(ManifestFile.builder().build(), appManifest);

    assertThat(path).isEqualTo("Setup/Applications/My App/Environments/Production/Values/Services/backend");
  }

  private void rootPathForValuesEnv() {
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .envId(ENV_ID)
                                          .kind(VALUES)
                                          .storeType(StoreType.Remote)
                                          .accountId(ACCOUNT_ID)
                                          .build();
    appManifest.setAppId(APP_ID);

    doReturn(AppManifestSource.ENV).when(applicationManifestService).getAppManifestType(appManifest);
    doReturn(anApplication().name("My App").accountId(ACCOUNT_ID).uuid(APP_ID).build()).when(appService).get(APP_ID);
    doReturn(anEnvironment().uuid(ENV_ID).appId(APP_ID).accountId(ACCOUNT_ID).name("Production").build())
        .when(environmentService)
        .get(APP_ID, ENV_ID, true);

    final String path = yamlDirectoryService.getRootPathByManifestFile(ManifestFile.builder().build(), appManifest);

    assertThat(path).isEqualTo("Setup/Applications/My App/Environments/Production/Values");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getRootPathByManifestFileForK8sManifest() {
    rootPathForManifestService();
    rootPathForManifestEnvService();
    rootPathForManifestEnv();
  }

  private void rootPathForManifestService() {
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .serviceId(SERVICE_ID)
                                          .kind(K8S_MANIFEST)
                                          .storeType(StoreType.Remote)
                                          .accountId(ACCOUNT_ID)
                                          .build();
    appManifest.setAppId(APP_ID);

    doReturn(AppManifestSource.SERVICE).when(applicationManifestService).getAppManifestType(appManifest);
    doReturn(anApplication().name("My App").accountId(ACCOUNT_ID).uuid(APP_ID).build()).when(appService).get(APP_ID);
    doReturn(Service.builder().uuid(SERVICE_ID).name("backend").build())
        .when(serviceResourceService)
        .getWithDetails(APP_ID, SERVICE_ID);

    final String path = yamlDirectoryService.getRootPathByManifestFile(ManifestFile.builder().build(), appManifest);

    assertThat(path).isEqualTo("Setup/Applications/My App/Services/backend/Manifests/Files");
  }

  private void rootPathForManifestEnvService() {
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .serviceId(SERVICE_ID)
                                          .envId(ENV_ID)
                                          .kind(K8S_MANIFEST)
                                          .storeType(StoreType.Remote)
                                          .accountId(ACCOUNT_ID)
                                          .build();
    appManifest.setAppId(APP_ID);

    doReturn(AppManifestSource.ENV_SERVICE).when(applicationManifestService).getAppManifestType(appManifest);
    doReturn(anApplication().name("My App").accountId(ACCOUNT_ID).uuid(APP_ID).build()).when(appService).get(APP_ID);
    doReturn(Service.builder().uuid(SERVICE_ID).name("backend").build())
        .when(serviceResourceService)
        .getWithDetails(APP_ID, SERVICE_ID);
    doReturn(anEnvironment().uuid(ENV_ID).appId(APP_ID).accountId(ACCOUNT_ID).name("Production").build())
        .when(environmentService)
        .get(APP_ID, ENV_ID, true);

    final String path = yamlDirectoryService.getRootPathByManifestFile(ManifestFile.builder().build(), appManifest);

    assertThat(path).isEqualTo("Setup/Applications/My App/Environments/Production/Values/Services/backend");
  }

  private void rootPathForManifestEnv() {
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .envId(ENV_ID)
                                          .kind(VALUES)
                                          .storeType(StoreType.Remote)
                                          .accountId(ACCOUNT_ID)
                                          .build();
    appManifest.setAppId(APP_ID);

    doReturn(AppManifestSource.ENV).when(applicationManifestService).getAppManifestType(appManifest);
    doReturn(anApplication().name("My App").accountId(ACCOUNT_ID).uuid(APP_ID).build()).when(appService).get(APP_ID);
    doReturn(anEnvironment().uuid(ENV_ID).appId(APP_ID).accountId(ACCOUNT_ID).name("Production").build())
        .when(environmentService)
        .get(APP_ID, ENV_ID, true);

    final String path = yamlDirectoryService.getRootPathByManifestFile(ManifestFile.builder().build(), appManifest);

    assertThat(path).isEqualTo("Setup/Applications/My App/Environments/Production/Values");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getRootPathByManifestFileForOcParams() {
    rootPathForOcParamsService();
    rootPathForOcParamsEnvService();
    rootPathForOcParamsEnv();
  }

  private void rootPathForOcParamsService() {
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .serviceId(SERVICE_ID)
                                          .kind(OC_PARAMS)
                                          .storeType(StoreType.Remote)
                                          .accountId(ACCOUNT_ID)
                                          .build();
    appManifest.setAppId(APP_ID);

    doReturn(AppManifestSource.SERVICE).when(applicationManifestService).getAppManifestType(appManifest);
    doReturn(anApplication().name("My App").accountId(ACCOUNT_ID).uuid(APP_ID).build()).when(appService).get(APP_ID);
    doReturn(Service.builder().uuid(SERVICE_ID).name("backend").build())
        .when(serviceResourceService)
        .getWithDetails(APP_ID, SERVICE_ID);

    final String path = yamlDirectoryService.getRootPathByManifestFile(ManifestFile.builder().build(), appManifest);

    assertThat(path).isEqualTo("Setup/Applications/My App/Services/backend/OC Params");
  }

  private void rootPathForOcParamsEnvService() {
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .serviceId(SERVICE_ID)
                                          .envId(ENV_ID)
                                          .kind(OC_PARAMS)
                                          .storeType(StoreType.Remote)
                                          .accountId(ACCOUNT_ID)
                                          .build();
    appManifest.setAppId(APP_ID);

    doReturn(AppManifestSource.ENV_SERVICE).when(applicationManifestService).getAppManifestType(appManifest);
    doReturn(anApplication().name("My App").accountId(ACCOUNT_ID).uuid(APP_ID).build()).when(appService).get(APP_ID);
    doReturn(Service.builder().uuid(SERVICE_ID).name("backend").build())
        .when(serviceResourceService)
        .getWithDetails(APP_ID, SERVICE_ID);
    doReturn(anEnvironment().uuid(ENV_ID).appId(APP_ID).accountId(ACCOUNT_ID).name("Production").build())
        .when(environmentService)
        .get(APP_ID, ENV_ID, true);

    final String path = yamlDirectoryService.getRootPathByManifestFile(ManifestFile.builder().build(), appManifest);

    assertThat(path).isEqualTo("Setup/Applications/My App/Environments/Production/OC Params/Services/backend");
  }

  private void rootPathForOcParamsEnv() {
    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .envId(ENV_ID)
                                          .kind(OC_PARAMS)
                                          .storeType(StoreType.Remote)
                                          .accountId(ACCOUNT_ID)
                                          .build();
    appManifest.setAppId(APP_ID);

    doReturn(AppManifestSource.ENV).when(applicationManifestService).getAppManifestType(appManifest);
    doReturn(anApplication().name("My App").accountId(ACCOUNT_ID).uuid(APP_ID).build()).when(appService).get(APP_ID);
    doReturn(anEnvironment().uuid(ENV_ID).appId(APP_ID).accountId(ACCOUNT_ID).name("Production").build())
        .when(environmentService)
        .get(APP_ID, ENV_ID, true);

    final String path = yamlDirectoryService.getRootPathByManifestFile(ManifestFile.builder().build(), appManifest);

    assertThat(path).isEqualTo("Setup/Applications/My App/Environments/Production/OC Params");
  }
}
