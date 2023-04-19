/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.configfile;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rule.OwnerRule.INDER;

import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.FILE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

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
import io.harness.rule.Owner;
import io.harness.stream.BoundedInputStream;

import software.wings.beans.Application;
import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.ServiceResourceService;
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

public class ConfigFileYamlHandlerTest extends YamlHandlerTestBase {
  @InjectMocks @Inject private ConfigFileYamlHandler configFileYamlHandler;
  @InjectMocks @Inject private YamlHelper yamlHelper;

  @Mock private AppService appService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ConfigService configService;
  @Mock private SecretManager secretManager;

  private static final String yamlFilePath =
      "Setup/Applications/APP_NAME/Services/SERVICE_NAME/Config Files/configFile.txt.yaml";
  private static final String configFilePath =
      "Setup/Applications/APP_NAME/Services/SERVICE_NAME/Config Files/configFile.txt";
  private static final String resourcePath = "400-rest/src/test/resources/configfiles/Service";
  private static final String UNENCRYPTED_CONFIG_FILE_YAML = "unEncryptedConfigFile.yaml";
  private static final String UNENCRYPTED_CONFIG_FILE_YAML_2 = "unEncryptedConfigFile2.yaml";
  private static final String ENCRYPTED_CONFIG_FILE_YAML = "encryptedConfigFile.yaml";
  private static final String CONFIG_FILE = "configFile.txt";

  private ArgumentCaptor<ConfigFile> captor = ArgumentCaptor.forClass(ConfigFile.class);
  private ArgumentCaptor<BoundedInputStream> captorBoundedInputStream =
      ArgumentCaptor.forClass(BoundedInputStream.class);

  private void getApplication() {
    Application application =
        Application.Builder.anApplication().uuid(APP_ID).accountId(ACCOUNT_ID).appId(APP_ID).build();
    when(appService.getAppByName(anyString(), anyString())).thenReturn(application);
  }

  private void getService() {
    Service service = Service.builder().uuid(SERVICE_ID).build();
    when(serviceResourceService.getServiceByName(anyString(), anyString())).thenReturn(service);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCrudFromYamlForUnencryptedConfigFile() throws IOException {
    testCrudConfigFiles(UNENCRYPTED_CONFIG_FILE_YAML, CONFIG_FILE, yamlFilePath, configFilePath);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCrudFromYamlForEncryptedConfigFile() throws IOException {
    testCrudConfigFiles(ENCRYPTED_CONFIG_FILE_YAML, null, yamlFilePath, configFilePath);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpdateFromYamlWhenRelativePathHasBackSlashes() throws IOException {
    String yamlString = getYamlFile(UNENCRYPTED_CONFIG_FILE_YAML_2);
    ChangeContext<ConfigFile.Yaml> changeContext = getYamlChangeContext(yamlString, yamlFilePath);
    ConfigFile.Yaml yaml = changeContext.getYaml();
    boolean isEncrypted = yaml.isEncrypted();

    List<ChangeContext> changeSetContext = getChangeSetContext(CONFIG_FILE, configFilePath, changeContext, isEncrypted);

    testSetUp(yaml);

    ConfigFile savedConfigFile = testSaveConfigFile(changeContext, isEncrypted, changeSetContext);

    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(savedConfigFile);

    testUpdateYaml(changeContext, isEncrypted, changeSetContext, savedConfigFile);
    verify(configService, times(2))
        .get(eq(APP_ID), eq(SERVICE_ID), eq(EntityType.SERVICE), eq(yaml.getTargetFilePath()));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGitSyncFlagOnCRUDFromYaml() throws IOException {
    String yamlString = getYamlFile(UNENCRYPTED_CONFIG_FILE_YAML);
    ChangeContext<ConfigFile.Yaml> changeContext = getYamlChangeContext(yamlString, yamlFilePath);
    changeContext.getChange().setSyncFromGit(true);
    ConfigFile.Yaml yaml = changeContext.getYaml();
    boolean isEncrypted = yaml.isEncrypted();

    List<ChangeContext> changeSetContext = getChangeSetContext(CONFIG_FILE, configFilePath, changeContext, isEncrypted);

    testSetUp(yaml);

    ConfigFile savedConfigFile = testSaveConfigFile(changeContext, isEncrypted, changeSetContext);
    assertThat(savedConfigFile.isSyncFromGit()).isTrue();

    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(savedConfigFile);

    ConfigFile updatedConfigFile = testUpdateYaml(changeContext, isEncrypted, changeSetContext, savedConfigFile);
    assertThat(updatedConfigFile.isSyncFromGit()).isTrue();

    configFileYamlHandler.delete(changeContext);
    verify(configService).delete(eq(APP_ID), eq(SERVICE_ID), eq(EntityType.SERVICE), anyString(), eq(true));
  }

  private void testCrudConfigFiles(
      String yamlFileName, String configFileName, String yamlFilePath, String configFilePath) throws IOException {
    String yamlString = getYamlFile(yamlFileName);
    ChangeContext<ConfigFile.Yaml> changeContext = getYamlChangeContext(yamlString, yamlFilePath);
    ConfigFile.Yaml yaml = changeContext.getYaml();
    boolean isEncrypted = yaml.isEncrypted();

    List<ChangeContext> changeSetContext =
        getChangeSetContext(configFileName, configFilePath, changeContext, isEncrypted);

    testSetUp(yaml);

    ConfigFile savedConfigFile = testSaveConfigFile(changeContext, isEncrypted, changeSetContext);

    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(savedConfigFile);

    ConfigFile updatedConfigFile = testUpdateYaml(changeContext, isEncrypted, changeSetContext, savedConfigFile);

    yaml = configFileYamlHandler.toYaml(updatedConfigFile, APP_ID);

    assertThat(yaml).isNotNull();
    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlString);

    configFileYamlHandler.delete(changeContext);
    verify(configService).delete(eq(APP_ID), eq(SERVICE_ID), eq(EntityType.SERVICE), anyString(), anyBoolean());
  }

  private List<ChangeContext> getChangeSetContext(String configFileName, String configFilePath,
      ChangeContext<ConfigFile.Yaml> changeContext, boolean isEncrypted) throws IOException {
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

  private ConfigFile testUpdateYaml(ChangeContext<ConfigFile.Yaml> changeContext, boolean isEncrypted,
      List<ChangeContext> changeSetContext, ConfigFile savedConfigFile) {
    configFileYamlHandler.upsertFromYaml(changeContext, changeSetContext);
    verify(configService).update(captor.capture(), any());
    ConfigFile updatedConfigFile = captor.getValue();
    assertThat(updatedConfigFile).isNotNull();
    assertThat(updatedConfigFile.getEntityType()).isNotNull().isEqualTo(savedConfigFile.getEntityType());
    assertConfigFileEntityType(updatedConfigFile);
    verifyConfigFilePresence(isEncrypted);
    verifyEncryptionFields(savedConfigFile, isEncrypted);
    return updatedConfigFile;
  }

  private ConfigFile testSaveConfigFile(
      ChangeContext<ConfigFile.Yaml> changeContext, boolean isEncrypted, List<ChangeContext> changeSetContext) {
    configFileYamlHandler.upsertFromYaml(changeContext, changeSetContext);
    verify(configService).save(captor.capture(), captorBoundedInputStream.capture());
    ConfigFile savedConfigFile = captor.getValue();
    assertThat(savedConfigFile).isNotNull();
    assertThat(savedConfigFile.getEntityType()).isNotNull();
    assertConfigFileEntityType(savedConfigFile);
    verifyConfigFilePresence(isEncrypted);
    verifyEncryptionFields(savedConfigFile, isEncrypted);
    return savedConfigFile;
  }

  private void testSetUp(ConfigFile.Yaml yaml) {
    getApplication();
    getService();
    when(configService.get(eq(APP_ID), anyString(), any(), any())).thenReturn(null);
    when(secretManager.getEncryptedDataFromYamlRef(yaml.getFileName(), ACCOUNT_ID))
        .thenReturn(EncryptedData.builder().uuid(FILE_ID).build());
    when(secretManager.getEncryptedYamlRef(ACCOUNT_ID, FILE_ID)).thenReturn("safeharness:FILE_ID");
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

  private void assertConfigFileEntityType(ConfigFile savedConfigFile) {
    assertThat(savedConfigFile.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedConfigFile.getEntityType()).isEqualTo(EntityType.SERVICE);
    assertThat(savedConfigFile.getTemplateId()).isNotNull().isEqualTo(ConfigFile.DEFAULT_TEMPLATE_ID);
    assertThat(savedConfigFile.getEntityId()).isNotNull().isEqualTo(SERVICE_ID);
  }

  private String getYamlFile(String yamlFileName) throws IOException {
    File yamlFile = null;

    yamlFile = new File(resourcePath + PATH_DELIMITER + yamlFileName);

    assertThat(yamlFile).isNotNull();
    return FileUtils.readFileToString(yamlFile, "UTF-8");
  }

  private GitFileChange getGitFileChange(String yamlFilePath, String yamlString) {
    return GitFileChange.Builder.aGitFileChange()
        .withAccountId(ACCOUNT_ID)
        .withFilePath(yamlFilePath)
        .withFileContent(yamlString)
        .build();
  }

  private ChangeContext getChangeContextForConfigFile(String yamlFilePath, String yamlString) {
    GitFileChange gitFileChange = getGitFileChange(yamlFilePath, yamlString);
    ChangeContext changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    return changeContext;
  }

  private ChangeContext<ConfigFile.Yaml> getYamlChangeContext(String yamlString, String yamlFilePath)
      throws IOException {
    GitFileChange gitFileChange = getGitFileChange(yamlFilePath, yamlString);
    ChangeContext<ConfigFile.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.CONFIG_FILE);
    ConfigFile.Yaml yaml = (ConfigFile.Yaml) getYaml(yamlString, ConfigFile.Yaml.class);
    changeContext.setYaml(yaml);
    return changeContext;
  }
}
