/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.k8s.model.HelmVersion.V2;
import static io.harness.pcf.model.PcfConstants.VARS_YML;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.appmanifest.AppManifestKind.K8S_MANIFEST;
import static software.wings.beans.appmanifest.AppManifestKind.PCF_OVERRIDE;
import static software.wings.beans.appmanifest.AppManifestKind.VALUES;
import static software.wings.beans.appmanifest.ManifestFile.VALUES_YAML_KEY;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.beans.appmanifest.StoreType.Remote;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.manifest.CustomSourceConfig;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.ApplicationManifestKeys;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.service.impl.ApplicationManifestServiceImpl;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class ApplicationManifestServiceTest extends WingsBaseTest {
  private static final String GIT_CONNECTOR_ID = "gitConnectorId";
  private static final String BRANCH = "branch";
  private static final String FILE_PATH = "filePath";
  private static final String FILE_NAME = "fileName";
  private static final String FILE_CONTENT = "fileContent";
  private static final String APP_MANIFEST_NAME = "APP_MANIFEST_NAME";

  @Mock private AppService appService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private YamlPushService yamlPushService;
  @Mock private FeatureFlagService featureFlagService;

  @Inject private HPersistence persistence;
  @Inject private EnvironmentService environmentService;

  @Inject @InjectMocks ApplicationManifestService applicationManifestService;
  @Inject ApplicationManifestServiceImpl applicationManifestServiceImpl;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    applicationManifest.setAppId(APP_ID);
    manifestFile.setAppId(APP_ID);
    doReturn(V2).when(serviceResourceService).getHelmVersionWithDefault(anyString(), anyString());
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().isK8sV2(true).artifactFromManifest(false).build());
  }

  private static ApplicationManifest applicationManifest =
      ApplicationManifest.builder().serviceId(SERVICE_ID).storeType(Local).kind(AppManifestKind.K8S_MANIFEST).build();

  private static ManifestFile manifestFile =
      ManifestFile.builder().fileName("deploy.yaml").fileContent("deployment spec").build();

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void createShouldFailIfServiceDoesNotExist() {
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(false);

    try {
      applicationManifestService.create(applicationManifest);
    } catch (InvalidRequestException e) {
      assertThat(e.getParams().get("message")).isEqualTo("Service doesn't exist");
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void createTest() {
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(true);
    ApplicationManifest savedManifest = applicationManifestService.create(applicationManifest);

    assertThat(savedManifest.getUuid()).isNotEmpty();
    assertThat(savedManifest.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(savedManifest.getStoreType()).isEqualTo(Local);

    ApplicationManifest manifest = persistence.createQuery(ApplicationManifest.class)
                                       .filter(ApplicationManifest.APP_ID_KEY2, APP_ID)
                                       .filter(ApplicationManifestKeys.serviceId, SERVICE_ID)
                                       .get();

    assertThat(manifest).isEqualTo(savedManifest);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void updateTest() {
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(true);
    ApplicationManifest savedManifest = applicationManifestService.create(applicationManifest);

    // savedManifest.setManifestFiles(asList(manifestFile));

    applicationManifestService.update(savedManifest);

    ApplicationManifest manifest = persistence.createQuery(ApplicationManifest.class)
                                       .filter(ApplicationManifest.APP_ID_KEY2, APP_ID)
                                       .filter(ApplicationManifestKeys.serviceId, SERVICE_ID)
                                       .get();

    // assertThat(manifest.getManifestFiles()).isEqualTo(asList(manifestFile));
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void getTest() {
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(true);
    ApplicationManifest savedManifest = applicationManifestService.create(applicationManifest);

    ApplicationManifest manifest = applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID);

    assertThat(manifest).isEqualTo(savedManifest);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void deleteTest() {
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(true);
    applicationManifestService.create(applicationManifest);
    manifestFile.setApplicationManifestId(applicationManifest.getUuid());

    ManifestFile savedmManifestFile = applicationManifestService.createManifestFileByServiceId(
        ApplicationManifestServiceTest.manifestFile, SERVICE_ID);

    ManifestFile manifestFileById = applicationManifestService.getManifestFileById(APP_ID, manifestFile.getUuid());
    assertThat(savedmManifestFile).isEqualTo(manifestFileById);

    applicationManifestService.deleteManifestFileById(APP_ID, savedmManifestFile.getUuid());
    manifestFileById = applicationManifestService.getManifestFileById(APP_ID, manifestFile.getUuid());
    assertThat(manifestFileById).isNull();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)

  public void testDuplicateManifestFileNames() {
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(true);

    persistence.ensureIndexForTesting(ManifestFile.class);
    applicationManifestService.create(applicationManifest);

    ManifestFile manifestFileWithSameName =
        ManifestFile.builder().fileName("deploy.yaml").fileContent("duplicate deployment spec").build();
    manifestFileWithSameName.setAppId(APP_ID);

    ManifestFile savedmManifestFile =
        applicationManifestService.createManifestFileByServiceId(manifestFile, SERVICE_ID);
    assertThat(savedmManifestFile).isNotNull();
    applicationManifestService.createManifestFileByServiceId(manifestFileWithSameName, SERVICE_ID);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateAppManifestForService() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);

    ApplicationManifest savedApplicationManifest = applicationManifestService.create(applicationManifest);
    assertThat(savedApplicationManifest.getUuid()).isNotNull();
    assertThat(savedApplicationManifest.getGitFileConfig()).isNull();
    assertThat(savedApplicationManifest.getEnvId()).isNull();
    assertThat(savedApplicationManifest.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(savedApplicationManifest.getStoreType()).isEqualTo(Local);
    assertThat(savedApplicationManifest.getKind()).isEqualTo(K8S_MANIFEST);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdateAppManifestForService() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    persistence.save(applicationManifest);

    SettingAttribute setting =
        aSettingAttribute().withUuid(GIT_CONNECTOR_ID).withValue(GitConfig.builder().build()).build();
    persistence.save(setting);

    GitFileConfig gitFileConfig = GitFileConfig.builder()
                                      .connectorId(GIT_CONNECTOR_ID)
                                      .useBranch(true)
                                      .branch(BRANCH)
                                      .filePath(FILE_PATH)
                                      .build();
    applicationManifest.setStoreType(Remote);
    applicationManifest.setGitFileConfig(gitFileConfig);
    Service service = Service.builder().deploymentType(DeploymentType.KUBERNETES).build();
    doReturn(service).when(serviceResourceService).getWithDetails(any(), any());
    ApplicationManifest savedApplicationManifest = applicationManifestService.update(applicationManifest);
    assertThat(savedApplicationManifest.getUuid()).isNotNull();
    assertThat(savedApplicationManifest.getEnvId()).isNull();
    assertThat(savedApplicationManifest.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(savedApplicationManifest.getStoreType()).isEqualTo(Remote);
    assertThat(savedApplicationManifest.getKind()).isEqualTo(K8S_MANIFEST);
    compareGitFileConfig(gitFileConfig, applicationManifest.getGitFileConfig());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateInvalidRemoteAppManifest() {
    GitFileConfig gitFileConfig = GitFileConfig.builder().useBranch(true).branch(BRANCH).filePath(FILE_PATH).build();

    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .storeType(Remote)
                                                  .kind(K8S_MANIFEST)
                                                  .serviceId(SERVICE_ID)
                                                  .gitFileConfig(gitFileConfig)
                                                  .build();

    applicationManifestService.create(applicationManifest);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateInvalidLocalAppManifest() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .storeType(Local)
                                                  .kind(K8S_MANIFEST)
                                                  .serviceId(SERVICE_ID)
                                                  .gitFileConfig(GitFileConfig.builder().build())
                                                  .build();

    applicationManifestService.create(applicationManifest);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateInvalidAppManifest() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).build();

    applicationManifestService.create(applicationManifest);
  }

  private void compareGitFileConfig(GitFileConfig gitFileConfig, GitFileConfig savedGitFileConfig) {
    assertThat(savedGitFileConfig.getFilePath()).isEqualTo(gitFileConfig.getFilePath());
    assertThat(savedGitFileConfig.getConnectorId()).isEqualTo(gitFileConfig.getConnectorId());
    assertThat(savedGitFileConfig.isUseBranch()).isEqualTo(gitFileConfig.isUseBranch());
    assertThat(savedGitFileConfig.getBranch()).isEqualTo(gitFileConfig.getBranch());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdateAppManifestKind() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    persistence.save(applicationManifest);

    applicationManifest.setKind(AppManifestKind.VALUES);
    ApplicationManifest savedApplicationManifest = applicationManifestService.update(applicationManifest);
    assertThat(savedApplicationManifest.getKind()).isEqualTo(K8S_MANIFEST);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpsertApplicationManifestFileForCreate() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    persistence.save(applicationManifest);

    ManifestFile manifestFile = ManifestFile.builder().fileContent(FILE_CONTENT).fileName(FILE_NAME).build();
    manifestFile.setAppId(APP_ID);

    manifestFile = applicationManifestService.upsertApplicationManifestFile(manifestFile, applicationManifest, true);
    assertThat(manifestFile.getFileName()).isEqualTo(FILE_NAME);
    assertThat(manifestFile.getFileContent()).isEqualTo(FILE_CONTENT);
    assertThat(manifestFile.getApplicationManifestId()).isEqualTo(applicationManifest.getUuid());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpsertApplicationManifestFileForUpdate() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    persistence.save(applicationManifest);

    ManifestFile manifestFile = ManifestFile.builder().fileContent(FILE_CONTENT).fileName(FILE_NAME).build();
    manifestFile.setAppId(APP_ID);
    persistence.save(manifestFile);

    manifestFile.setFileName("updated" + FILE_NAME);
    manifestFile.setFileContent("updated" + FILE_CONTENT);
    manifestFile.setApplicationManifestId("randomId");

    ManifestFile savedManifestFile =
        applicationManifestService.upsertApplicationManifestFile(manifestFile, applicationManifest, true);
    assertThat(savedManifestFile.getFileName()).isEqualTo("updated" + FILE_NAME);
    assertThat(savedManifestFile.getFileContent()).isEqualTo("updated" + FILE_CONTENT);
    assertThat(savedManifestFile.getApplicationManifestId()).isEqualTo(applicationManifest.getUuid());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidManifestFileName() {
    validateManifestFileName("  ");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidManifestFileName1() {
    validateManifestFileName(" / ");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidManifestFileName2() {
    validateManifestFileName("a//c");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidManifestFileName3() {
    validateManifestFileName("a/ /c");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidManifestFileName4() {
    validateManifestFileName("a/b /c");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidManifestFileName5() {
    validateManifestFileName("a/b/ c");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidManifestFileName6() {
    validateManifestFileName("a/b/c ");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidManifestFileName7() {
    validateManifestFileName("a/ b/c");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidManifestFileName8() {
    validateManifestFileName(" a/b/c");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidManifestFileName9() {
    validateManifestFileName("a /b/c");
  }

  private void validateManifestFileName(String fileName) {
    ManifestFile manifestFile = ManifestFile.builder().fileContent(FILE_CONTENT).fileName(fileName).build();
    manifestFile.setAppId(APP_ID);

    applicationManifestService.upsertApplicationManifestFile(manifestFile, applicationManifest, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateFileNamePrefixForDirectory() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    persistence.save(applicationManifest);

    ManifestFile manifestFile1 = getManifestFileWithName("a/b/c");
    ManifestFile manifestFile2 = getManifestFileWithName("a/b");

    applicationManifestService.upsertApplicationManifestFile(manifestFile1, applicationManifest, true);
    applicationManifestService.upsertApplicationManifestFile(manifestFile2, applicationManifest, true);
  }

  private ManifestFile getManifestFileWithName(String fileName) {
    ManifestFile manifestFile = ManifestFile.builder().fileContent(FILE_CONTENT).fileName(fileName).build();
    manifestFile.setAppId(APP_ID);

    return manifestFile;
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeleteAppManifest() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest = applicationManifestService.create(applicationManifest);

    ManifestFile manifestFile1 = getManifestFileWithName("a");
    ManifestFile manifestFile2 = getManifestFileWithName("b");

    applicationManifestService.upsertApplicationManifestFile(manifestFile1, applicationManifest, true);
    applicationManifestService.upsertApplicationManifestFile(manifestFile2, applicationManifest, true);

    applicationManifestService.deleteAppManifest(APP_ID, applicationManifest.getUuid());

    List<ManifestFile> manifestFiles =
        applicationManifestService.getManifestFilesByAppManifestId(APP_ID, applicationManifest.getUuid());
    assertThat(manifestFiles).isEmpty();
    applicationManifest = applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID);
    assertThat(applicationManifest).isNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeleteAppManifestMultipleTimes() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest = applicationManifestService.create(applicationManifest);

    ManifestFile manifestFile1 = getManifestFileWithName("a");
    ManifestFile manifestFile2 = getManifestFileWithName("b");

    applicationManifestService.upsertApplicationManifestFile(manifestFile1, applicationManifest, true);
    applicationManifestService.upsertApplicationManifestFile(manifestFile2, applicationManifest, true);

    applicationManifestService.deleteAppManifest(APP_ID, applicationManifest.getUuid());
    applicationManifestService.deleteAppManifest(APP_ID, applicationManifest.getUuid());

    List<ManifestFile> manifestFiles =
        applicationManifestService.getManifestFilesByAppManifestId(APP_ID, applicationManifest.getUuid());
    assertThat(manifestFiles).isEmpty();
    applicationManifest = applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID);
    assertThat(applicationManifest).isNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeleteManifestFileForService() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest = applicationManifestService.create(applicationManifest);

    ManifestFile manifestFile = getManifestFileWithName("a");

    manifestFile = applicationManifestService.upsertApplicationManifestFile(manifestFile, applicationManifest, true);
    applicationManifestService.deleteManifestFileById(APP_ID, manifestFile.getUuid());

    List<ManifestFile> manifestFiles =
        applicationManifestService.getManifestFilesByAppManifestId(APP_ID, applicationManifest.getUuid());
    assertThat(manifestFiles).isEmpty();
    applicationManifest = applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID);
    assertThat(applicationManifest).isNotNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeleteManifestFileForEnvironment() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(VALUES).envId(ENV_ID).build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest = applicationManifestService.create(applicationManifest);

    ManifestFile manifestFile = getManifestFileWithName("a");
    manifestFile = applicationManifestService.upsertApplicationManifestFile(manifestFile, applicationManifest, true);

    applicationManifestService.deleteManifestFile(APP_ID, manifestFile);

    List<ManifestFile> manifestFiles =
        applicationManifestService.getManifestFilesByAppManifestId(APP_ID, applicationManifest.getUuid());
    assertThat(manifestFiles).isEmpty();
    applicationManifest = applicationManifestService.getByEnvId(APP_ID, ENV_ID, VALUES);
    assertThat(applicationManifest).isNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeleteManifestFileForEnvironmentMultipleTimes() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(VALUES).envId(ENV_ID).build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest = applicationManifestService.create(applicationManifest);

    ManifestFile manifestFile = getManifestFileWithName("a");
    manifestFile = applicationManifestService.upsertApplicationManifestFile(manifestFile, applicationManifest, true);

    applicationManifestService.deleteManifestFile(APP_ID, manifestFile);
    applicationManifestService.deleteManifestFile(APP_ID, manifestFile);

    List<ManifestFile> manifestFiles =
        applicationManifestService.getManifestFilesByAppManifestId(APP_ID, applicationManifest.getUuid());
    assertThat(manifestFiles).isEmpty();
    applicationManifest = applicationManifestService.getByEnvId(APP_ID, ENV_ID, VALUES);
    assertThat(applicationManifest).isNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testPruneByService() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest = applicationManifestService.create(applicationManifest);

    ManifestFile manifestFile1 = getManifestFileWithName("a");
    ManifestFile manifestFile2 = getManifestFileWithName("b");

    applicationManifestService.upsertApplicationManifestFile(manifestFile1, applicationManifest, true);
    applicationManifestService.upsertApplicationManifestFile(manifestFile2, applicationManifest, true);

    applicationManifestService.pruneByService(APP_ID, SERVICE_ID);

    List<ManifestFile> manifestFiles =
        applicationManifestService.getManifestFilesByAppManifestId(APP_ID, applicationManifest.getUuid());
    assertThat(manifestFiles).isEmpty();
    applicationManifest = applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID);
    assertThat(applicationManifest).isNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testPruneByEnvironment() {
    ApplicationManifest envAppManifest =
        ApplicationManifest.builder().storeType(Local).kind(VALUES).envId(ENV_ID).build();
    envAppManifest.setAppId(APP_ID);
    envAppManifest = applicationManifestService.create(envAppManifest);
    ManifestFile manifestFile1 = getManifestFileWithName("a");
    applicationManifestService.upsertApplicationManifestFile(manifestFile1, envAppManifest, true);

    ApplicationManifest envServiceAppManifest =
        ApplicationManifest.builder().storeType(Local).kind(VALUES).envId(ENV_ID).serviceId(SERVICE_ID).build();
    envServiceAppManifest.setAppId(APP_ID);
    envServiceAppManifest = applicationManifestService.create(envServiceAppManifest);
    ManifestFile manifestFile2 = getManifestFileWithName("a");
    applicationManifestService.upsertApplicationManifestFile(manifestFile2, envServiceAppManifest, true);

    applicationManifestService.pruneByEnvironment(APP_ID, ENV_ID);

    List<ManifestFile> manifestFiles =
        applicationManifestService.getManifestFilesByAppManifestId(APP_ID, envAppManifest.getUuid());
    assertThat(manifestFiles).isEmpty();
    manifestFiles = applicationManifestService.getManifestFilesByAppManifestId(APP_ID, envServiceAppManifest.getUuid());
    assertThat(manifestFiles).isEmpty();
    List<ApplicationManifest> applicationManifests =
        applicationManifestService.getAllByEnvIdAndKind(APP_ID, ENV_ID, VALUES);
    assertThat(applicationManifests).isEmpty();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateManifestFileName() {
    upsertManifestFile("abc/def", "abc/pqr");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateManifestFileName1() {
    upsertManifestFile("abc/def", "abc");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateManifestFileName2() {
    upsertManifestFile("abc/def/ghi", "abc");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateManifestFileName3() {
    upsertManifestFile("abc/def/ghi", "abc/def");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateManifestFileName4() {
    upsertManifestFile("abc", "abc/def");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateManifestFileName5() {
    upsertManifestFile("abc/def", "abc/def/ghi");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateManifestFileName6() {
    upsertManifestFile("abc/def", "abc/def/ghi/klm");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)

  public void testDuplicateManifestFileName() {
    persistence.ensureIndexForTesting(ManifestFile.class);
    upsertManifestFile("abc/def", "abc/def");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateManifestFileName8() {
    upsertManifestFile("abc/def", "abc/ghi");
    upsertManifestFile("abc/jkl", "abc/mno");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testEditManifestFileContent() {
    ApplicationManifest appManifest = createAppManifest();

    ManifestFile manifestFile = getManifestFileWithName("abc/values.yaml");
    manifestFile = applicationManifestService.upsertApplicationManifestFile(manifestFile, appManifest, true);

    manifestFile.setFileContent("file-content-abc");
    applicationManifestService.upsertApplicationManifestFile(manifestFile, appManifest, true);

    ManifestFile manifestFileById =
        applicationManifestService.getManifestFileById(manifestFile.getAppId(), manifestFile.getUuid());

    assertThat(manifestFileById.getFileName()).isEqualTo("abc/values.yaml");
    assertThat(manifestFileById.getFileContent()).isEqualTo("file-content-abc");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testEditManifestFileName() {
    ApplicationManifest appManifest = createAppManifest();

    ManifestFile manifestFile = getManifestFileWithName("abc/values.yaml");
    manifestFile = applicationManifestService.upsertApplicationManifestFile(manifestFile, appManifest, true);

    manifestFile.setFileName("xyz");
    applicationManifestService.upsertApplicationManifestFile(manifestFile, appManifest, true);

    ManifestFile manifestFileById =
        applicationManifestService.getManifestFileById(manifestFile.getAppId(), manifestFile.getUuid());

    assertThat(manifestFileById.getFileName()).isEqualTo("xyz");
    assertThat(manifestFileById.getFileContent()).isEqualTo(FILE_CONTENT);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testMoveManifestFileToExistingDirectory() {
    ApplicationManifest appManifest = createAppManifest();

    ManifestFile manifestFile = getManifestFileWithName("abc/file1");
    applicationManifestService.upsertApplicationManifestFile(manifestFile, appManifest, true);

    manifestFile = getManifestFileWithName("xyz/file2");
    manifestFile = applicationManifestService.upsertApplicationManifestFile(manifestFile, appManifest, true);

    manifestFile.setFileName("abc");
    applicationManifestService.upsertApplicationManifestFile(manifestFile, appManifest, true);
  }

  private ApplicationManifest createAppManifest() {
    ApplicationManifest envAppManifest =
        ApplicationManifest.builder().storeType(Local).kind(VALUES).serviceId(SERVICE_ID).build();
    envAppManifest.setAppId(APP_ID);
    return applicationManifestService.create(envAppManifest);
  }

  private void upsertManifestFile(String fileName1, String fileName2) {
    ApplicationManifest appManifest = createAppManifest();

    ManifestFile manifestFile = getManifestFileWithName(fileName1);
    applicationManifestService.upsertApplicationManifestFile(manifestFile, appManifest, true);

    manifestFile = getManifestFileWithName(fileName2);
    ManifestFile savedManifestFile =
        applicationManifestService.upsertApplicationManifestFile(manifestFile, appManifest, true);

    assertThat(manifestFile.getFileContent()).isEqualTo(savedManifestFile.getFileContent());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testRemoveNamespace() {
    applicationManifestServiceImpl.removeNamespace(null);

    ManifestFile manifestFile = ManifestFile.builder().build();
    applicationManifestServiceImpl.removeNamespace(manifestFile);

    String fileContent = null;
    manifestFile.setFileContent(fileContent);
    applicationManifestServiceImpl.removeNamespace(manifestFile);
    assertThat(manifestFile.getFileContent() == null).isTrue();

    fileContent = "";
    manifestFile.setFileContent(fileContent);
    applicationManifestServiceImpl.removeNamespace(manifestFile);
    assertThat(manifestFile.getFileContent().equals("")).isTrue();

    fileContent = "{{- if .Values.env.config}}\n"
        + "apiVersion: v1\n"
        + "kind: ConfigMap\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}\n"
        + "data:\n"
        + "{{.Values.env.config | toYaml | indent 2}}\n"
        + "---\n"
        + "{{- end}}\n"
        + "\n"
        + "{{- if .Values.env.secrets}}\n"
        + "apiVersion: v1\n"
        + "kind: Secret\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}\n"
        + "stringData:\n"
        + "{{.Values.env.secrets | toYaml | indent 2}}\n"
        + "---\n"
        + "{{- end}}\n"
        + "\n"
        + "{{- if .Values.createImagePullSecret}}\n"
        + "apiVersion: v1\n"
        + "kind: Secret\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}-dockercfg\n"
        + "  annotations:\n"
        + "    harness.io/skip-versioning: true\n"
        + "data:\n"
        + "  .dockercfg: {{.Values.dockercfg}}\n"
        + "type: kubernetes.io/dockercfg\n"
        + "---\n"
        + "{{- end}}\n"
        + "\n"
        + "apiVersion: apps/v1\n"
        + "kind: Deployment\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}-deployment\n"
        + "spec:\n"
        + "  replicas: {{int .Values.replicas}}\n"
        + "  selector:\n"
        + "    matchLabels:\n"
        + "      app: {{.Values.name}}\n"
        + "  template:\n"
        + "    metadata:\n"
        + "      labels:\n"
        + "        app: {{.Values.name}}\n"
        + "    spec:\n"
        + "      {{- if .Values.createImagePullSecret}}\n"
        + "      imagePullSecrets:\n"
        + "      - name: {{.Values.name}}-dockercfg\n"
        + "      {{- end}}\n"
        + "      containers:\n"
        + "      - name: {{.Values.name}}\n"
        + "        image: {{.Values.image}}\n"
        + "        {{- if or .Values.env.config .Values.env.secrets}}\n"
        + "        envFrom:\n"
        + "        {{- if .Values.env.config}}\n"
        + "        - configMapRef:\n"
        + "            name: {{.Values.name}}\n"
        + "        {{- end}}\n"
        + "        {{- if .Values.env.secrets}}\n"
        + "        - secretRef:\n"
        + "            name: {{.Values.name}}\n"
        + "        {{- end}}\n"
        + "        {{- end}}";
    manifestFile.setFileContent(fileContent);
    applicationManifestServiceImpl.removeNamespace(manifestFile);
    assertThat(manifestFile.getFileContent().equals(fileContent)).isTrue();

    fileContent = "{{- if .Values.env.config}}\n"
        + "apiVersion: v1\n"
        + "kind: ConfigMap\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}\n"
        + "data:\n"
        + "{{.Values.env.config | toYaml | indent 2}}\n"
        + "---\n"
        + "{{- end}}\n"
        + "\n"
        + "{{- if .Values.env.secrets}}\n"
        + "apiVersion: v1\n"
        + "kind: Secret\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}\n"
        + "stringData:\n"
        + "{{.Values.env.secrets | toYaml | indent 2}}\n"
        + "---\n"
        + "{{- end}}\n"
        + "\n"
        + "{{- if .Values.createImagePullSecret}}\n"
        + "apiVersion: v1\n"
        + "kind: Secret\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}-dockercfg\n"
        + "  annotations:\n"
        + "    harness.io/skip-versioning: true\n"
        + "  namespace: abc\n"
        + "data:\n"
        + "  .dockercfg: {{.Values.dockercfg}}\n"
        + "type: kubernetes.io/dockercfg\n"
        + "---\n"
        + "{{- end}}\n"
        + "\n"
        + "apiVersion: apps/v1\n"
        + "kind: Deployment\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}-deployment\n"
        + "spec:\n"
        + "  replicas: {{int .Values.replicas}}\n"
        + "  selector:\n"
        + "    matchLabels:\n"
        + "      app: {{.Values.name}}\n"
        + "  template:\n"
        + "    metadata:\n"
        + "      labels:\n"
        + "        app: {{.Values.name}}\n"
        + "    spec:\n"
        + "      - name: {{.Values.name}}\n"
        + "        image: {{.Values.image}}\n"
        + "        {{- if or .Values.env.config .Values.env.secrets}}\n"
        + "        envFrom:\n"
        + "        {{- if .Values.env.config}}\n"
        + "        - configMapRef:\n"
        + "            name: {{.Values.name}}\n"
        + "        {{- end}}\n"
        + "        {{- if .Values.env.secrets}}\n"
        + "        - secretRef:\n"
        + "            name: {{.Values.name}}\n"
        + "        {{- end}}\n"
        + "        {{- end}}";

    String expectedFileContent = "{{- if .Values.env.config}}\n"
        + "apiVersion: v1\n"
        + "kind: ConfigMap\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}\n"
        + "data:\n"
        + "{{.Values.env.config | toYaml | indent 2}}\n"
        + "---\n"
        + "{{- end}}\n"
        + "\n"
        + "{{- if .Values.env.secrets}}\n"
        + "apiVersion: v1\n"
        + "kind: Secret\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}\n"
        + "stringData:\n"
        + "{{.Values.env.secrets | toYaml | indent 2}}\n"
        + "---\n"
        + "{{- end}}\n"
        + "\n"
        + "{{- if .Values.createImagePullSecret}}\n"
        + "apiVersion: v1\n"
        + "kind: Secret\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}-dockercfg\n"
        + "  annotations:\n"
        + "    harness.io/skip-versioning: true\n"
        + "data:\n"
        + "  .dockercfg: {{.Values.dockercfg}}\n"
        + "type: kubernetes.io/dockercfg\n"
        + "---\n"
        + "{{- end}}\n"
        + "\n"
        + "apiVersion: apps/v1\n"
        + "kind: Deployment\n"
        + "metadata:\n"
        + "  name: {{.Values.name}}-deployment\n"
        + "spec:\n"
        + "  replicas: {{int .Values.replicas}}\n"
        + "  selector:\n"
        + "    matchLabels:\n"
        + "      app: {{.Values.name}}\n"
        + "  template:\n"
        + "    metadata:\n"
        + "      labels:\n"
        + "        app: {{.Values.name}}\n"
        + "    spec:\n"
        + "      - name: {{.Values.name}}\n"
        + "        image: {{.Values.image}}\n"
        + "        {{- if or .Values.env.config .Values.env.secrets}}\n"
        + "        envFrom:\n"
        + "        {{- if .Values.env.config}}\n"
        + "        - configMapRef:\n"
        + "            name: {{.Values.name}}\n"
        + "        {{- end}}\n"
        + "        {{- if .Values.env.secrets}}\n"
        + "        - secretRef:\n"
        + "            name: {{.Values.name}}\n"
        + "        {{- end}}\n"
        + "        {{- end}}";

    manifestFile.setFileContent(fileContent);
    applicationManifestServiceImpl.removeNamespace(manifestFile);
    assertThat(manifestFile.getFileContent().equals(expectedFileContent)).isTrue();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForLargeFiles() {
    String content = "abcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabc";
    ManifestFile manifestFile = ManifestFile.builder().fileContent(content).build();
    assertThatThrownBy(() -> applicationManifestServiceImpl.doFileSizeValidation(manifestFile, 1))
        .isInstanceOf(InvalidRequestException.class);

    applicationManifestServiceImpl.doFileSizeValidation(manifestFile, 100);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateLocalAppManifest() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder().storeType(StoreType.Local).build();
    applicationManifestServiceImpl.validateLocalAppManifest(applicationManifest);

    applicationManifest.setServiceId("testservice");
    Service service = Service.builder().build();
    when(serviceResourceService.getWithDetails(any(), any())).thenReturn(service);
    applicationManifestServiceImpl.validateLocalAppManifest(applicationManifest);

    service.setDeploymentType(DeploymentType.KUBERNETES);
    applicationManifestServiceImpl.validateLocalAppManifest(applicationManifest);

    applicationManifest.setKind(AppManifestKind.VALUES);
    applicationManifestServiceImpl.validateLocalAppManifest(applicationManifest);

    service.setDeploymentType(DeploymentType.HELM);
    applicationManifest.setKind(AppManifestKind.K8S_MANIFEST);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateLocalAppManifest(applicationManifest));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateLocalAppManifestWithCustomSourceConfig() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .storeType(Local)
                                                  .customSourceConfig(CustomSourceConfig.builder().path("test").build())
                                                  .build();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateLocalAppManifest(applicationManifest));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetOverrideManifestFilesByEnvId() {
    persistence.save(Environment.Builder.anEnvironment().appId(APP_ID).uuid(ENV_ID).build());

    ManifestFile localManifestFile = ManifestFile.builder().fileName("val.yaml").fileContent("values").build();
    environmentService.createValues(APP_ID, ENV_ID, null, localManifestFile, null);

    localManifestFile = ManifestFile.builder().fileName("variable.yaml").fileContent("vars").build();
    environmentService.createValues(APP_ID, ENV_ID, null, localManifestFile, PCF_OVERRIDE);

    List<ManifestFile> manifestFiles = applicationManifestService.getOverrideManifestFilesByEnvId(APP_ID, ENV_ID);
    assertThat(manifestFiles).isNotEmpty();
    assertThat(manifestFiles.get(0).getFileName()).isEqualTo(VALUES_YAML_KEY);
    assertThat(manifestFiles.get(0).getFileContent()).isEqualTo("values");
    assertThat(manifestFiles.get(1).getFileName()).isEqualTo(VARS_YML);
    assertThat(manifestFiles.get(1).getFileContent()).isEqualTo("vars");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetOverrideManifestFilesByEnvIdEmptyCase() {
    List<ManifestFile> manifestFiles = applicationManifestService.getOverrideManifestFilesByEnvId(APP_ID, ENV_ID);
    assertThat(manifestFiles).isEmpty();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateManifestFileNameForDotsInPath() {
    ApplicationManifest appManifest = createAppManifest();

    ManifestFile manifestFile = getManifestFileWithName("templates/../abc");
    validateDotsInPath(manifestFile, appManifest, true);
    validateDotsInPath(manifestFile, appManifest, false);

    manifestFile = getManifestFileWithName("../templates/../abc");
    validateDotsInPath(manifestFile, appManifest, true);
    validateDotsInPath(manifestFile, appManifest, false);

    manifestFile = getManifestFileWithName(".../templates/.../abc");
    assertThat(manifestFile.getFileName()).isEqualTo(".../templates/.../abc");

    manifestFile = getManifestFileWithName("../templates/abc");
    assertThat(manifestFile.getFileName()).isEqualTo("../templates/abc");

    manifestFile = getManifestFileWithName("templates/../abc");
    assertThat(manifestFile.getFileName()).isEqualTo("templates/../abc");
  }

  private void validateDotsInPath(ManifestFile manifestFile, ApplicationManifest appManifest, boolean create) {
    try {
      applicationManifestService.upsertApplicationManifestFile(manifestFile, appManifest, create);
      fail("Should not reach here.");
    } catch (Exception ex) {
      assertThat(ex instanceof InvalidRequestException).isTrue();
      assertThat(ex.getMessage()).isEqualTo("Manifest file path component cannot contain [..]");
    }
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateDuplicateAppManifest() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).kind(VALUES).serviceId(SERVICE_ID).envId(ENV_ID).build();

    when(serviceResourceService.get(null, SERVICE_ID, false))
        .thenReturn(Service.builder().isK8sV2(true).artifactFromManifest(false).build());
    applicationManifestService.create(applicationManifest);
    try {
      applicationManifestService.create(applicationManifest);
      fail("Should not reach here.");
    } catch (Exception ex) {
      assertThat(ex instanceof InvalidRequestException).isTrue();
      assertThat(ex.getMessage())
          .isEqualTo("App Manifest already exists for app null with kind VALUES, serviceId SERVICE_ID, envId ENV_ID");
    }
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void getByNameTest() {
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(true);
    applicationManifest.setName(APP_MANIFEST_NAME);
    ApplicationManifest savedManifest = applicationManifestService.create(applicationManifest);

    ApplicationManifest manifest =
        applicationManifestService.getAppManifestByName(APP_ID, null, SERVICE_ID, APP_MANIFEST_NAME);

    assertThat(manifest).isEqualTo(savedManifest);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldBeAbleToCreateMultipleApplicationManifest() {
    when(featureFlagService.isEnabled(eq(FeatureName.HELM_CHART_AS_ARTIFACT), any())).thenReturn(true);
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(true);
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().isK8sV2(true).artifactFromManifest(true).build());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .name(APP_MANIFEST_NAME + 2)
            .serviceId(SERVICE_ID)
            .storeType(StoreType.HelmChartRepo)
            .kind(AppManifestKind.K8S_MANIFEST)
            .helmChartConfig(HelmChartConfig.builder().connectorId(SETTING_ID).chartName("chartName").build())
            .build();
    applicationManifest.setAppId(APP_ID);
    ApplicationManifest savedManifest = applicationManifestService.create(applicationManifest);
    ApplicationManifest applicationManifest2 =
        ApplicationManifest.builder()
            .name(APP_MANIFEST_NAME)
            .serviceId(SERVICE_ID)
            .storeType(StoreType.HelmChartRepo)
            .kind(AppManifestKind.K8S_MANIFEST)
            .helmChartConfig(HelmChartConfig.builder().connectorId(SETTING_ID).chartName("chartName").build())
            .build();
    applicationManifest2.setAppId(APP_ID);
    ApplicationManifest savedManifest2 = applicationManifestService.create(applicationManifest2);

    List<ApplicationManifest> manifests =
        applicationManifestService.getManifestsByServiceId(APP_ID, SERVICE_ID, K8S_MANIFEST);

    assertThat(manifests).containsExactlyInAnyOrder(savedManifest, savedManifest2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForApplicationManifestWithSameName() {
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(true);
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().isK8sV2(true).artifactFromManifest(true).build());
    when(featureFlagService.isEnabled(eq(FeatureName.HELM_CHART_AS_ARTIFACT), any())).thenReturn(true);

    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .name(APP_MANIFEST_NAME)
            .serviceId(SERVICE_ID)
            .storeType(StoreType.HelmChartRepo)
            .kind(AppManifestKind.K8S_MANIFEST)
            .helmChartConfig(HelmChartConfig.builder().connectorId(SETTING_ID).chartName("chartName").build())
            .build();
    applicationManifest.setAppId(APP_ID);
    applicationManifestService.create(applicationManifest);
    ApplicationManifest applicationManifest2 =
        ApplicationManifest.builder()
            .name(APP_MANIFEST_NAME)
            .serviceId(SERVICE_ID)
            .storeType(StoreType.HelmChartRepo)
            .kind(AppManifestKind.K8S_MANIFEST)
            .helmChartConfig(HelmChartConfig.builder().connectorId(SETTING_ID).chartName("chartName").build())
            .build();
    applicationManifest2.setAppId(APP_ID);
    assertThatThrownBy(() -> applicationManifestService.create(applicationManifest2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Application Manifest with name APP_MANIFEST_NAME already exists in Service null");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionToAddMultipleWrongKind() {
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(true);
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().isK8sV2(true).artifactFromManifest(true).build());

    when(featureFlagService.isEnabled(eq(FeatureName.HELM_CHART_AS_ARTIFACT), any())).thenReturn(true);
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .name(APP_MANIFEST_NAME)
            .serviceId(SERVICE_ID)
            .storeType(StoreType.HelmChartRepo)
            .helmChartConfig(HelmChartConfig.builder().connectorId("connector").chartName("name").build())
            .kind(K8S_MANIFEST)
            .build();

    applicationManifest.setAppId(APP_ID);
    applicationManifestService.create(applicationManifest);
    ApplicationManifest applicationManifest2 = ApplicationManifest.builder()
                                                   .name(APP_MANIFEST_NAME)
                                                   .serviceId(SERVICE_ID)
                                                   .storeType(Local)
                                                   .kind(AppManifestKind.K8S_MANIFEST)
                                                   .build();
    applicationManifest2.setAppId(APP_ID);
    assertThatThrownBy(() -> applicationManifestService.create(applicationManifest2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Application Manifest should be of kind Helm Chart from Helm Repo for Service with artifact from manifest enabled");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetNamesForIds() {
    when(serviceResourceService.exist(anyString(), anyString())).thenReturn(true);
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().isK8sV2(true).artifactFromManifest(true).build());
    when(featureFlagService.isEnabled(eq(FeatureName.HELM_CHART_AS_ARTIFACT), any())).thenReturn(true);
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .name(APP_MANIFEST_NAME + 2)
            .serviceId(SERVICE_ID)
            .storeType(StoreType.HelmChartRepo)
            .kind(AppManifestKind.K8S_MANIFEST)
            .helmChartConfig(HelmChartConfig.builder().chartName("name").connectorId(SETTING_ID).build())
            .build();
    applicationManifest.setAppId(APP_ID);
    ApplicationManifest savedManifest = applicationManifestService.create(applicationManifest);
    ApplicationManifest applicationManifest2 =
        ApplicationManifest.builder()
            .name(APP_MANIFEST_NAME)
            .serviceId(SERVICE_ID)
            .storeType(StoreType.HelmChartRepo)
            .kind(AppManifestKind.K8S_MANIFEST)
            .helmChartConfig(HelmChartConfig.builder().chartName("name").connectorId(SETTING_ID).build())
            .build();
    applicationManifest2.setAppId(APP_ID);
    ApplicationManifest savedManifest2 = applicationManifestService.create(applicationManifest2);
    Map<String, String> appManifestIdNames = applicationManifestService.getNamesForIds(
        APP_ID, ImmutableSet.of(savedManifest.getUuid(), savedManifest2.getUuid()));
    assertThat(appManifestIdNames).hasSize(2);
    assertThat(appManifestIdNames.get(savedManifest.getUuid())).isEqualTo(APP_MANIFEST_NAME + 2);
    assertThat(appManifestIdNames.get(savedManifest2.getUuid())).isEqualTo(APP_MANIFEST_NAME);
  }
}
