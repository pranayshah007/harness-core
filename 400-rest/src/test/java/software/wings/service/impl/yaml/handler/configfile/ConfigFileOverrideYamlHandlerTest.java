/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.configfile;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rule.OwnerRule.INDER;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.Service.GLOBAL_SERVICE_NAME_FOR_YAML;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.FILE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.stream.BoundedInputStream;

import software.wings.beans.Application;
import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ConfigFileOverrideYamlHandlerTest extends YamlHandlerTestBase {
  @InjectMocks @Inject private ConfigFileOverrideYamlHandler configFileOverrideYamlHandler;
  @InjectMocks @Inject private YamlHelper yamlHelper;

  @Mock private ConfigService configService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private SecretManager secretManager;

  private static final String yamlFilePathForAService =
      "Setup/Applications/APP_NAME/Environments/ENV_NAME/Config Files/SERVICE_NAME/configFile.txt.yaml";
  private static final String yamlFilePathForAllServices =
      "Setup/Applications/APP_NAME/Environments/ENV_NAME/Config Files/__all_service__/configFile.txt.yaml";
  private static final String configFilePathForAService =
      "Setup/Applications/APP_NAME/Environments/ENV_NAME/Config Files/SERVICE_NAME/configFile.txt";
  private static final String configFilePathForAllServices =
      "Setup/Applications/APP_NAME/Environments/ENV_NAME/Config Files/__all_service__/configFile.txt";
  private static final String resourcePath = "400-rest/src/test/resources/configfiles/Environment";

  private static final String UNENCRYPTED_OVERRIDE_YAML = "unEncryptedFileOverride.yaml";
  private static final String UNENCRYPTED_OVERRIDE_YAML_2 = "unEncryptedFileOverride2.yaml";
  private static final String ENCRYPTED_OVERRIDE_YAML = "encryptedFileOverride.yaml";
  private static final String CONFIG_FILE = "configFile.txt";
  private static final String INCORRECT_OVERRIDE_YAML = "incorrectOverrideYaml.yaml";

  private ArgumentCaptor<ConfigFile> captor = ArgumentCaptor.forClass(ConfigFile.class);
  private ArgumentCaptor<BoundedInputStream> captorBoundedInputStream =
      ArgumentCaptor.forClass(BoundedInputStream.class);

  private void getApplication() {
    Application application =
        Application.Builder.anApplication().uuid(APP_ID).accountId(ACCOUNT_ID).appId(APP_ID).build();
    when(appService.getAppByName(any(), any())).thenReturn(application);
  }

  private void getEnvironment() {
    Environment environment = Environment.Builder.anEnvironment().uuid(ENV_ID).accountId(ACCOUNT_ID).build();
    when(environmentService.getEnvironmentByName(any(), any())).thenReturn(environment);
  }

  private void getServiceAndServiceTemplateId() {
    Service service = Service.builder().uuid(SERVICE_ID).build();
    when(serviceResourceService.getServiceByName(any(), any())).thenReturn(service);
    ServiceTemplate serviceTemplate = ServiceTemplate.Builder.aServiceTemplate().withUuid(SERVICE_TEMPLATE_ID).build();
    when(serviceTemplateService.get(any(), any(), any())).thenReturn(serviceTemplate);
  }

  private GitFileChange getGitFileChange(String yamlFilePath, String yamlString) {
    return GitFileChange.Builder.aGitFileChange()
        .withAccountId(ACCOUNT_ID)
        .withFilePath(yamlFilePath)
        .withFileContent(yamlString)
        .build();
  }

  private ChangeContext<ConfigFile.OverrideYaml> getOverrideYamlChangeContext(String yamlString, String yamlFilePath)
      throws IOException {
    GitFileChange gitFileChange = getGitFileChange(yamlFilePath, yamlString);
    ChangeContext<ConfigFile.OverrideYaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.CONFIG_FILE_OVERRIDE);
    ConfigFile.OverrideYaml yaml = (ConfigFile.OverrideYaml) getYaml(yamlString, ConfigFile.OverrideYaml.class);
    changeContext.setYaml(yaml);
    return changeContext;
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDeleteIfApplicationNotExist() throws IOException {
    String yamlString = getYamlFile(UNENCRYPTED_OVERRIDE_YAML);
    ChangeContext<ConfigFile.OverrideYaml> changeContext =
        getOverrideYamlChangeContext(yamlString, yamlFilePathForAService);

    configFileOverrideYamlHandler.delete(changeContext);
    verify(appService, times(1)).getAppByName(ACCOUNT_ID, APP_NAME);
    verify(environmentService, never()).getEnvironmentByName(anyString(), anyString());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDeleteIfEnvironmentNotExist() throws IOException {
    String yamlString = getYamlFile(UNENCRYPTED_OVERRIDE_YAML);
    ChangeContext<ConfigFile.OverrideYaml> changeContext =
        getOverrideYamlChangeContext(yamlString, yamlFilePathForAService);
    getApplication();

    configFileOverrideYamlHandler.delete(changeContext);
    verify(environmentService, times(1)).getEnvironmentByName(APP_ID, ENV_NAME);
    verify(configService, never()).delete(anyString(), anyString(), any(), anyString(), anyBoolean());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDeleteWithFileOverrideForAService() throws IOException {
    String yamlString = getYamlFile(UNENCRYPTED_OVERRIDE_YAML);
    ChangeContext<ConfigFile.OverrideYaml> changeContext =
        getOverrideYamlChangeContext(yamlString, yamlFilePathForAService);
    changeContext.getChange().setSyncFromGit(true);
    getApplication();
    getEnvironment();
    getServiceAndServiceTemplateId();

    configFileOverrideYamlHandler.delete(changeContext);
    verify(configService, times(1))
        .delete(eq(APP_ID), eq(SERVICE_TEMPLATE_ID), eq(EntityType.SERVICE_TEMPLATE), anyString(), eq(true));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDeleteWithFileOverrideForAllServices() throws IOException {
    String yamlString = getYamlFile(UNENCRYPTED_OVERRIDE_YAML);
    ChangeContext<ConfigFile.OverrideYaml> changeContext =
        getOverrideYamlChangeContext(yamlString, yamlFilePathForAllServices);
    getApplication();
    getEnvironment();

    configFileOverrideYamlHandler.delete(changeContext);
    verify(configService, times(1))
        .delete(anyString(), anyString(), eq(EntityType.ENVIRONMENT), anyString(), anyBoolean());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpsertFromYamlForUnencryptedOverridesForAllServices() throws IOException {
    testUpsertFromYaml(
        UNENCRYPTED_OVERRIDE_YAML, CONFIG_FILE, yamlFilePathForAllServices, configFilePathForAllServices);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpsertFromYamlForUnencryptedOverridesForAService() throws IOException {
    testUpsertFromYaml(UNENCRYPTED_OVERRIDE_YAML, CONFIG_FILE, yamlFilePathForAService, configFilePathForAService);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpsertFromYamlForEncryptedOverridesForAService() throws IOException {
    testUpsertFromYaml(ENCRYPTED_OVERRIDE_YAML, null, yamlFilePathForAService, configFilePathForAService);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpsertFromYamlForEncryptedOverridesForAllService() throws IOException {
    testUpsertFromYaml(ENCRYPTED_OVERRIDE_YAML, null, yamlFilePathForAllServices, configFilePathForAllServices);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGitSyncFlagOnUpsertFromYaml() throws IOException {
    String yamlFilePath = yamlFilePathForAService;
    String yamlString = getYamlFile(UNENCRYPTED_OVERRIDE_YAML);
    ChangeContext<ConfigFile.OverrideYaml> changeContext = getOverrideYamlChangeContext(yamlString, yamlFilePath);
    changeContext.getChange().setSyncFromGit(true);
    ConfigFile.OverrideYaml yaml = changeContext.getYaml();
    boolean isEncrypted = yaml.isEncrypted();

    List<ChangeContext> changeSetContext =
        getChangeSetContexts(CONFIG_FILE, configFilePathForAService, changeContext, isEncrypted);

    String serviceName = mockServicesSetup(yamlFilePathForAService, yaml);
    ConfigFile savedConfigFile = testSaveFromYaml(changeContext, isEncrypted, changeSetContext, serviceName);
    assertThat(savedConfigFile.isSyncFromGit()).isTrue();

    savedConfigFile.setFileUuid(FILE_ID);
    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(savedConfigFile);
    ConfigFile updatedConfigFile =
        testUpdateFromYaml(changeContext, isEncrypted, changeSetContext, serviceName, savedConfigFile);
    assertThat(updatedConfigFile.isSyncFromGit()).isTrue();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowErrorIfConfigFileAbsentOnSave() throws IOException {
    testUpsertFromYaml(UNENCRYPTED_OVERRIDE_YAML, null, yamlFilePathForAService, configFilePathForAService);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotThrowErrorIfConfigFileAbsentOnUpdate() throws IOException {
    String yamlString = getYamlFile(UNENCRYPTED_OVERRIDE_YAML);
    ChangeContext<ConfigFile.OverrideYaml> changeContext =
        getOverrideYamlChangeContext(yamlString, yamlFilePathForAService);
    ConfigFile.OverrideYaml yaml = changeContext.getYaml();
    boolean isEncrypted = yaml.isEncrypted();

    List<ChangeContext> changeSetContext =
        getChangeSetContexts(CONFIG_FILE, configFilePathForAService, changeContext, isEncrypted);
    String serviceName = mockServicesSetup(yamlFilePathForAService, yaml);
    ConfigFile savedConfigFile = testSaveFromYaml(changeContext, isEncrypted, changeSetContext, serviceName);
    savedConfigFile.setFileUuid(FILE_ID);
    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(savedConfigFile);
    changeSetContext = getChangeSetContexts(null, configFilePathForAService, changeContext, isEncrypted);

    testUpdateFromYaml(changeContext, isEncrypted, changeSetContext, serviceName, savedConfigFile);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldIgnoreTargetToEnvFieldIfPresent() throws IOException {
    String yamlString = getYamlFile(INCORRECT_OVERRIDE_YAML);
    ChangeContext<ConfigFile.OverrideYaml> changeContext =
        getOverrideYamlChangeContext(yamlString, yamlFilePathForAService);
    ConfigFile.OverrideYaml yaml = changeContext.getYaml();
    boolean isEncrypted = yaml.isEncrypted();

    List<ChangeContext> changeSetContext =
        getChangeSetContexts(CONFIG_FILE, configFilePathForAService, changeContext, isEncrypted);
    String serviceName = mockServicesSetup(yamlFilePathForAService, yaml);
    ConfigFile savedConfigFile = testSaveFromYaml(changeContext, isEncrypted, changeSetContext, serviceName);
    savedConfigFile.setFileUuid(FILE_ID);
    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(savedConfigFile);
    ConfigFile updatedConfigFile =
        testUpdateFromYaml(changeContext, isEncrypted, changeSetContext, serviceName, savedConfigFile);

    yaml = configFileOverrideYamlHandler.toYaml(updatedConfigFile, APP_ID);
    assertThat(yaml).isNotNull();
    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    String correctYamlString = getYamlFile(UNENCRYPTED_OVERRIDE_YAML);
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(correctYamlString);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpdateFromYamlWhenRelativePathHasBackSlashesForAllServices() throws IOException {
    String yamlString = getYamlFile(UNENCRYPTED_OVERRIDE_YAML_2);
    ChangeContext<ConfigFile.OverrideYaml> changeContext =
        getOverrideYamlChangeContext(yamlString, yamlFilePathForAllServices);
    ConfigFile.OverrideYaml yaml = changeContext.getYaml();
    boolean isEncrypted = yaml.isEncrypted();

    List<ChangeContext> changeSetContext =
        getChangeSetContexts(CONFIG_FILE, configFilePathForAllServices, changeContext, isEncrypted);

    String serviceName = mockServicesSetup(yamlFilePathForAllServices, yaml);
    ConfigFile savedConfigFile = testSaveFromYaml(changeContext, isEncrypted, changeSetContext, serviceName);

    savedConfigFile.setFileUuid(FILE_ID);
    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(savedConfigFile);
    testUpdateFromYaml(changeContext, isEncrypted, changeSetContext, serviceName, savedConfigFile);
    verify(configService, times(2))
        .get(eq(APP_ID), eq(ENV_ID), eq(EntityType.ENVIRONMENT), eq(yaml.getTargetFilePath()));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpdateFromYamlWhenRelativePathHasBackSlashesForAService() throws IOException {
    String yamlString = getYamlFile(UNENCRYPTED_OVERRIDE_YAML_2);
    ChangeContext<ConfigFile.OverrideYaml> changeContext =
        getOverrideYamlChangeContext(yamlString, yamlFilePathForAService);
    ConfigFile.OverrideYaml yaml = changeContext.getYaml();
    boolean isEncrypted = yaml.isEncrypted();

    List<ChangeContext> changeSetContext =
        getChangeSetContexts(CONFIG_FILE, configFilePathForAService, changeContext, isEncrypted);

    String serviceName = mockServicesSetup(yamlFilePathForAService, yaml);
    ConfigFile savedConfigFile = testSaveFromYaml(changeContext, isEncrypted, changeSetContext, serviceName);

    savedConfigFile.setFileUuid(FILE_ID);
    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(savedConfigFile);
    testUpdateFromYaml(changeContext, isEncrypted, changeSetContext, serviceName, savedConfigFile);
    verify(configService, times(2))
        .get(eq(APP_ID), eq(SERVICE_TEMPLATE_ID), eq(EntityType.SERVICE_TEMPLATE), eq(yaml.getTargetFilePath()));
  }

  private void testUpsertFromYaml(
      String yamlFileName, String configFileName, String yamlFilePath, String configFilePath) throws IOException {
    String yamlString = getYamlFile(yamlFileName);
    ChangeContext<ConfigFile.OverrideYaml> changeContext = getOverrideYamlChangeContext(yamlString, yamlFilePath);
    ConfigFile.OverrideYaml yaml = changeContext.getYaml();
    boolean isEncrypted = yaml.isEncrypted();

    List<ChangeContext> changeSetContext =
        getChangeSetContexts(configFileName, configFilePath, changeContext, isEncrypted);

    String serviceName = mockServicesSetup(yamlFilePath, yaml);
    ConfigFile savedConfigFile = testSaveFromYaml(changeContext, isEncrypted, changeSetContext, serviceName);

    savedConfigFile.setFileUuid(FILE_ID);
    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(savedConfigFile);
    ConfigFile updatedConfigFile =
        testUpdateFromYaml(changeContext, isEncrypted, changeSetContext, serviceName, savedConfigFile);

    yaml = configFileOverrideYamlHandler.toYaml(updatedConfigFile, APP_ID);

    assertThat(yaml).isNotNull();
    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlString);
  }

  private List<ChangeContext> getChangeSetContexts(String configFileName, String configFilePath,
      ChangeContext<ConfigFile.OverrideYaml> changeContext, boolean isEncrypted) throws IOException {
    List<ChangeContext> changeSetContext;
    if (!isEncrypted && isNotEmpty(configFileName)) {
      String configFileString = getYamlFile(configFileName);
      ChangeContext configFileChangeContext = getChangeContextForConfigFile(configFilePath, configFileString);

      changeSetContext = Arrays.asList(changeContext, configFileChangeContext);
    } else {
      changeSetContext = Collections.singletonList(changeContext);
    }
    return changeSetContext;
  }

  private ConfigFile testUpdateFromYaml(ChangeContext<ConfigFile.OverrideYaml> changeContext, boolean isEncrypted,
      List<ChangeContext> changeSetContext, String serviceName, ConfigFile savedConfigFile) {
    configFileOverrideYamlHandler.upsertFromYaml(changeContext, changeSetContext);
    verify(configService).update(captor.capture(), any());
    ConfigFile updatedConfigFile = captor.getValue();
    assertThat(updatedConfigFile).isNotNull();
    assertThat(updatedConfigFile.getEntityType()).isNotNull().isEqualTo(savedConfigFile.getEntityType());
    assertConfigFileEntityType(serviceName, updatedConfigFile);
    verifyConfigFilePresence(isEncrypted);
    verifyEncryptionFields(savedConfigFile, isEncrypted);
    return updatedConfigFile;
  }

  private ConfigFile testSaveFromYaml(ChangeContext<ConfigFile.OverrideYaml> changeContext, boolean isEncrypted,
      List<ChangeContext> changeSetContext, String serviceName) {
    configFileOverrideYamlHandler.upsertFromYaml(changeContext, changeSetContext);
    verify(configService).save(captor.capture(), captorBoundedInputStream.capture());
    ConfigFile savedConfigFile = captor.getValue();
    assertThat(savedConfigFile).isNotNull();
    assertThat(savedConfigFile.getEntityType()).isNotNull();
    assertConfigFileEntityType(serviceName, savedConfigFile);
    verifyConfigFilePresence(isEncrypted);
    verifyEncryptionFields(savedConfigFile, isEncrypted);
    return savedConfigFile;
  }

  private String mockServicesSetup(String yamlFilePath, ConfigFile.OverrideYaml yaml) {
    getApplication();
    getEnvironment();
    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(null);
    when(secretManager.getEncryptedDataFromYamlRef(yaml.getFileName(), ACCOUNT_ID))
        .thenReturn(EncryptedData.builder().uuid(FILE_ID).build());
    when(secretManager.getEncryptedYamlRef(ACCOUNT_ID, FILE_ID)).thenReturn("safeharness:FILE_ID");

    String serviceName = yamlHelper.getServiceNameForFileOverride(yamlFilePath);
    if (!serviceName.equals(GLOBAL_SERVICE_NAME_FOR_YAML)) {
      getServiceAndServiceTemplateId();
    }
    return serviceName;
  }

  private void verifyConfigFilePresence(boolean isEncrypted) {
    BoundedInputStream boundedInputStream = captorBoundedInputStream.getValue();
    if (!isEncrypted) {
      assertThat(boundedInputStream).isNotNull();
    } else {
      assertThat(boundedInputStream).isNull();
    }
  }

  private void verifyEncryptionFields(ConfigFile configFile, boolean isEncrypted) {
    if (!isEncrypted) {
      verify(secretManager, never()).getEncryptedDataFromYamlRef(any(), any());
      assertThat(configFile.getEncryptedFileId()).isEqualTo("");
    } else {
      assertThat(configFile.getEncryptedFileId()).isEqualTo(FILE_ID);
    }
  }

  private void assertConfigFileEntityType(String serviceName, ConfigFile savedConfigFile) {
    if (serviceName.equals(GLOBAL_SERVICE_NAME_FOR_YAML)) {
      assertThat(savedConfigFile.getEntityType()).isEqualTo(EntityType.ENVIRONMENT);
      assertThat(savedConfigFile.getEnvId()).isNotNull().isEqualTo(GLOBAL_ENV_ID);
      assertThat(savedConfigFile.getTemplateId()).isNotNull().isEqualTo(ConfigFile.DEFAULT_TEMPLATE_ID);
      assertThat(savedConfigFile.getEntityId()).isNotNull().isEqualTo(ENV_ID);
    } else {
      assertThat(savedConfigFile.getEntityType()).isEqualTo(EntityType.SERVICE_TEMPLATE);
      assertThat(savedConfigFile.getEnvId()).isNotNull().isEqualTo(ENV_ID);
      assertThat(savedConfigFile.getTemplateId()).isNotNull().isEqualTo(SERVICE_TEMPLATE_ID);
      assertThat(savedConfigFile.getEntityId()).isNotNull().isEqualTo(SERVICE_TEMPLATE_ID);
    }
  }

  private String getYamlFile(String yamlFileName) throws IOException {
    File yamlFile = null;
    yamlFile = new File(resourcePath + PATH_DELIMITER + yamlFileName);
    assertThat(yamlFile).isNotNull();
    return FileUtils.readFileToString(yamlFile, "UTF-8");
  }

  private ChangeContext getChangeContextForConfigFile(String yamlFilePath, String yamlString) {
    GitFileChange gitFileChange = getGitFileChange(yamlFilePath, yamlString);
    ChangeContext changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    return changeContext;
  }
}
