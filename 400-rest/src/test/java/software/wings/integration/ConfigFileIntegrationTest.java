/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.UNKNOWN;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.ConfigFile.DEFAULT_TEMPLATE_ID;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.integration.IntegrationTestUtils.randomInt;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.KMS_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.mockChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;
import io.harness.stream.BoundedInputStream;

import software.wings.beans.Application;
import software.wings.beans.ConfigFile;
import software.wings.beans.ConfigFile.ConfigOverrideType;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SyncTaskContext;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.rules.SetupScheduler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;

import com.google.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@SetupScheduler
public class ConfigFileIntegrationTest extends IntegrationTestBase {
  private static final String INPUT_TEXT = "Input Text";
  @Inject private ConfigService configService;
  @Inject @InjectMocks private AppService appService;
  @Mock private LimitCheckerFactory limitCheckerFactory;

  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Inject @InjectMocks private ServiceResourceService serviceResourceService;
  @Inject private FileService fileService;
  @Inject private ServiceTemplateService serviceTemplateService;

  @Rule public TemporaryFolder testFolder = new TemporaryFolder();
  private Application app;
  private Service service;
  private Environment env;
  private String fileName;
  private String kmsId;
  private ConfigFile configFile, newConfigFile;
  private ServiceTemplate serviceTemplate;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    FieldUtils.writeField(configService, "secretManager", secretManager, true);
    loginAdminUser();
    kmsId = KMS_ID;
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(delegateService);
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    app = appService.save(anApplication().accountId(ACCOUNT_ID).name(APP_NAME + System.currentTimeMillis()).build());
    service = serviceResourceService.save(
        Service.builder().appId(app.getUuid()).name(SERVICE_NAME + System.currentTimeMillis()).build());
    env = anEnvironment().appId(app.getAppId()).uuid(generateUuid()).name(ENV_NAME).build();
    serviceTemplate = serviceTemplateService.save(aServiceTemplate()
                                                      .withEnvId(env.getUuid())
                                                      .withAppId(app.getAppId())
                                                      .withUuid(generateUuid())
                                                      .withServiceId(service.getUuid())
                                                      .withName(SERVICE_NAME)
                                                      .build());
    configFile = getConfigFile();
    newConfigFile = getConfigFile();
    fileName = UUID.randomUUID().toString();
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldSaveServiceConfigFile() throws IOException {
    ConfigFile appConfigFile = getConfigFile();
    appConfigFile.setTemplateId(DEFAULT_TEMPLATE_ID);
    appConfigFile.setAppId(service.getAppId());
    appConfigFile.setName(fileName);
    appConfigFile.setFileName(fileName);

    FileInputStream fileInputStream = new FileInputStream(createRandomFile(fileName));
    String configId = configService.save(appConfigFile, new BoundedInputStream(fileInputStream));
    fileInputStream.close();
    ConfigFile configFile = configService.get(service.getAppId(), configId);
    assertThat(configFile).isNotNull().hasFieldOrPropertyWithValue("fileName", fileName);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldUpdateServiceConfigFile() throws IOException {
    ConfigFile appConfigFile = getConfigFile();
    appConfigFile.setTemplateId(DEFAULT_TEMPLATE_ID);
    appConfigFile.setAppId(service.getAppId());
    appConfigFile.setName(fileName);
    appConfigFile.setFileName(fileName);

    FileInputStream fileInputStream = new FileInputStream(createRandomFile(fileName));
    String configId = configService.save(appConfigFile, new BoundedInputStream(fileInputStream));
    fileInputStream.close();
    ConfigFile originalConfigFile = configService.get(service.getAppId(), configId);

    // Update config
    ConfigFile configFile = getConfigFile();
    configFile.setTemplateId(DEFAULT_TEMPLATE_ID);
    configFile.setAppId(service.getAppId());
    configFile.setName(fileName);
    configFile.setFileName(fileName);
    configFile.setUuid(configId);

    fileInputStream = new FileInputStream(createRandomFile(fileName + "_1"));
    configService.update(configFile, new BoundedInputStream(fileInputStream));
    ConfigFile updatedConfigFile = configService.get(service.getAppId(), configId);
    assertThat(updatedConfigFile).isNotNull().hasFieldOrPropertyWithValue("fileName", originalConfigFile.getFileName());
    assertThat(originalConfigFile.getFileUuid()).isNotEmpty().isNotEqualTo(updatedConfigFile.getFileUuid());
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldOverrideSimpleConfigFile() throws IOException {
    ConfigFile configFile = getConfigFile();
    configFile.setAppId(service.getAppId());
    configFile.setName(fileName);
    configFile.setFileName(fileName);

    FileInputStream fileInputStream = new FileInputStream(createRandomFile(fileName));
    String configId = configService.save(configFile, new BoundedInputStream(fileInputStream));
    fileInputStream.close();
    ConfigFile originalConfigFile = configService.get(service.getAppId(), configId);

    // Update config file
    ConfigFile newConfigFile = getConfigFile();
    newConfigFile.setAppId(service.getAppId());
    newConfigFile.setName(fileName);
    newConfigFile.setFileName(fileName + "_1");
    newConfigFile.setUuid(configId);

    fileInputStream = new FileInputStream(createRandomFile(fileName + "_1"));
    configService.update(newConfigFile, new BoundedInputStream(fileInputStream));
    ConfigFile updatedConfigFile = configService.get(service.getAppId(), configId);

    // Original config checks
    assertThat(originalConfigFile.getDefaultVersion()).isEqualTo(1);
    assertThat(originalConfigFile.getFileUuid()).isNotEmpty().isNotEqualTo(updatedConfigFile.getFileUuid());
    assertThat(originalConfigFile.isEncrypted()).isFalse();

    // Updated config checks
    assertThat(updatedConfigFile.getDefaultVersion()).isEqualTo(2);
    assertThat(updatedConfigFile).isNotNull().hasFieldOrPropertyWithValue("fileName", updatedConfigFile.getFileName());
    assertThat(originalConfigFile.isEncrypted()).isFalse();
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldEnvironmentOverrideServiceConfigFile() throws IOException {
    // Entity is SERVICE
    configFile.setAppId(service.getAppId());
    configFile.setName(fileName);
    configFile.setFileName(fileName);
    configFile.setEnvId(env.getUuid());
    configFile.setEntityType(EntityType.SERVICE);

    // Create config file for service
    service.setConfigFiles(Arrays.asList(configFile));

    configFile.setEntityId(service.getUuid());
    FileInputStream fileInputStream = new FileInputStream(createRandomFile(fileName));
    String configId = configService.save(configFile, new BoundedInputStream(fileInputStream));
    fileInputStream.close();
    ConfigFile originalConfigFile = configService.get(service.getAppId(), configId);

    // New config file
    newConfigFile.setAppId(serviceTemplate.getAppId());
    newConfigFile.setName(fileName);
    newConfigFile.setFileName(fileName + "_1");
    newConfigFile.setTemplateId(serviceTemplate.getUuid());
    newConfigFile.setEntityId(serviceTemplate.getUuid());
    newConfigFile.setEntityType(EntityType.SERVICE_TEMPLATE);
    newConfigFile.setConfigOverrideType(ConfigOverrideType.ALL);
    newConfigFile.setParentConfigFileId(originalConfigFile.getUuid());

    // Environment override should save as new config file
    fileInputStream = new FileInputStream(createRandomFile(fileName + "_1"));
    configId = configService.save(newConfigFile, new BoundedInputStream(fileInputStream));
    ConfigFile updatedConfigFile = configService.get(service.getAppId(), configId);

    // Original config - Entity is Service
    assertThat(originalConfigFile.getDefaultVersion()).isEqualTo(1);
    assertThat(originalConfigFile.isEncrypted()).isFalse();
    assertThat(originalConfigFile.getEntityId()).isNotNull().isEqualTo(service.getUuid());
    assertThat(originalConfigFile.getEntityType()).isNotNull().isEqualTo(EntityType.SERVICE);

    // Updated config - Entity is Service Template
    assertThat(updatedConfigFile.getDefaultVersion()).isEqualTo(1);
    assertThat(updatedConfigFile.isEncrypted()).isFalse();
    assertThat(updatedConfigFile.getTemplateId()).isNotNull().isEqualTo(serviceTemplate.getUuid());
    assertThat(updatedConfigFile.getEntityId()).isNotNull().isEqualTo(serviceTemplate.getUuid());
    assertThat(updatedConfigFile.getEntityType()).isNotNull().isEqualTo(EntityType.SERVICE_TEMPLATE);
    assertThat(updatedConfigFile.getParentConfigFileId()).isEqualTo(originalConfigFile.getUuid());
  }

  private File createRandomFile(String fileName) throws IOException {
    File file = testFolder.newFile(fileName == null ? "randomfile " + randomInt() : fileName);
    try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
      out.write("RandomText " + randomInt());
    }
    return file;
  }

  private ConfigFile getConfigFile() {
    ConfigFile configFile = ConfigFile.builder()
                                .entityId(service.getUuid())
                                .entityType(EntityType.SERVICE)
                                .envId(GLOBAL_ENV_ID)
                                .relativeFilePath("tmp")
                                .build();

    configFile.setAccountId(app.getAccountId());
    return configFile;
  }

  @After
  public void tearDown() {
    if (configFile != null) {
      configService.deleteByEntityId(app.getAppId(), configFile.getEntityId());
    }
    if (newConfigFile != null) {
      configService.deleteByEntityId(app.getAppId(), newConfigFile.getEntityId());
    }
    if (serviceTemplate != null) {
      serviceTemplateService.delete(app.getAppId(), serviceTemplate.getUuid());
    }
    if (service != null) {
      serviceResourceService.delete(app.getAppId(), service.getUuid());
    }
    if (app != null) {
      appService.delete(app.getAppId());
    }
  }
}
