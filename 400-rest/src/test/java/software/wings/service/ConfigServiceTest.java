/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.ConfigFile.DEFAULT_TEMPLATE_ID;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENTITY_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.FILE_ID;
import static software.wings.utils.WingsTestConstants.FILE_NAME;
import static software.wings.utils.WingsTestConstants.PARENT;
import static software.wings.utils.WingsTestConstants.PATH;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EncryptedData;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SecretUsageLog;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.FileBucket;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;
import io.harness.stream.BoundedInputStream;

import software.wings.WingsBaseTest;
import software.wings.beans.Activity;
import software.wings.beans.ConfigFile;
import software.wings.beans.ConfigFile.ConfigFileKeys;
import software.wings.beans.EntityType;
import software.wings.beans.EntityVersion;
import software.wings.beans.Event;
import software.wings.beans.ServiceTemplate;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.security.auth.ConfigFileAuthHandler;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.inject.Inject;
import dev.morphia.query.CriteriaContainer;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Created by anubhaw on 8/9/16.
 */
public class ConfigServiceTest extends WingsBaseTest {
  /**
   * The Query.
   */
  @Mock Query<ConfigFile> query;
  /**
   * The End.
   */
  @Mock FieldEnd end;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private FileService fileService;
  @Mock private HostService hostService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private ConfigFileAuthHandler configFileAuthHandler;
  @Mock private AppService appService;
  @Mock private YamlPushService yamlPushService;
  @Mock private SecretManager secretManager;
  @Mock private ActivityService activityService;

  @Inject @InjectMocks private EntityVersionService entityVersionService;
  @Inject @InjectMocks private ConfigService configService;
  private InputStream inputStream;

  /**
   * Sets up.
   *
   * @throws IOException the io exception
   */
  @Before
  public void setUp() throws IOException {
    inputStream = IOUtils.toInputStream("Some content", "UTF-8");
    when(wingsPersistence.createQuery(ConfigFile.class)).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    when(query.get()).thenReturn(ConfigFile.builder().build());
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
  }

  /**
   * Should list.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldList() {
    ConfigFile configFile = ConfigFile.builder()
                                .entityType(EntityType.SERVICE)
                                .entityId(SERVICE_ID)
                                .templateId(ConfigFile.DEFAULT_TEMPLATE_ID)
                                .build();

    configFile.setAppId(APP_ID);
    configFile.setName(FILE_NAME);
    configFile.setFileName(FILE_NAME);
    PageResponse<ConfigFile> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(configFile));
    pageResponse.setTotal(1l);

    PageRequest pageRequest = aPageRequest()
                                  .withLimit("50")
                                  .withOffset("0")
                                  .addFilter("appId", EQ, APP_ID)
                                  .addFilter("envId", EQ, ENV_ID)
                                  .addFilter("templateId", EQ, TEMPLATE_ID)
                                  .addFilter("entityId", EQ, "ENTITY_ID")
                                  .build();

    when(wingsPersistence.query(ConfigFile.class, pageRequest)).thenReturn(pageResponse);
    PageResponse<ConfigFile> configFiles = configService.list(pageRequest);
    assertThat(configFiles).isNotNull();
    assertThat(configFiles.getResponse().get(0)).isInstanceOf(ConfigFile.class);
  }

  /**
   * Should save.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldSave() {
    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID))
        .thenReturn(aServiceTemplate().withAppId(APP_ID).withEnvId(ENV_ID).withUuid(TEMPLATE_ID).build());
    when(serviceTemplateService.exist(APP_ID, TEMPLATE_ID)).thenReturn(true);

    ConfigFile configFile = ConfigFile.builder()
                                .envId(ENV_ID)
                                .entityType(EntityType.SERVICE_TEMPLATE)
                                .entityId(TEMPLATE_ID)
                                .templateId(TEMPLATE_ID)
                                .relativeFilePath("PATH/" + FILE_NAME)
                                .build();
    configFile.setAccountId(ACCOUNT_ID);
    configFile.setAppId(APP_ID);
    configFile.setName(FILE_NAME);
    configFile.setFileName(FILE_NAME);
    configFile.setUuid(FILE_ID);
    BoundedInputStream inputStream = new BoundedInputStream(this.inputStream);
    when(wingsPersistence.save(configFile)).thenReturn(FILE_ID);
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, FILE_ID)).thenReturn(configFile);
    configService.save(configFile, inputStream);
    verify(fileService).saveFile(configFile, inputStream, FileBucket.CONFIGS);
    assertThat(configFile.getRelativeFilePath()).isEqualTo("PATH/" + FILE_NAME);
    verify(wingsPersistence).save(configFile);
  }

  /**
   * Should throw exception for unsupported entity types.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForUnsupportedEntityTypes() {
    ConfigFile configFile =
        ConfigFile.builder().entityType(EntityType.ENVIRONMENT).entityId(ENV_ID).templateId(TEMPLATE_ID).build();
    configFile.setAccountId(ACCOUNT_ID);
    configFile.setAppId(APP_ID);
    configFile.setName(FILE_NAME);
    configFile.setFileName(FILE_NAME);
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> configService.save(configFile, new BoundedInputStream(inputStream)));
  }

  /**
   * Should get.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldGet() {
    ConfigFile configFile = ConfigFile.builder().build();
    configFile.setAppId(APP_ID);
    configFile.setUuid(FILE_ID);
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, FILE_ID)).thenReturn(configFile);

    configFile = configService.get(APP_ID, FILE_ID);
    verify(wingsPersistence).getWithAppId(ConfigFile.class, APP_ID, FILE_ID);
    assertThat(configFile.getUuid()).isEqualTo(FILE_ID);
  }

  /**
   * Should get config file by template.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetConfigFileByTemplate() {
    ServiceTemplate serviceTemplate =
        aServiceTemplate().withAppId(APP_ID).withEnvId(ENV_ID).withUuid(TEMPLATE_ID).build();
    ConfigFile configFile = ConfigFile.builder()
                                .entityType(EntityType.SERVICE_TEMPLATE)
                                .entityId(TEMPLATE_ID)
                                .templateId(TEMPLATE_ID)
                                .build();
    configFile.setAppId(APP_ID);
    configFile.setName(FILE_NAME);
    configFile.setFileName(FILE_NAME);

    when(query.asList()).thenReturn(asList(configFile));
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, FILE_ID)).thenReturn(configFile);
    List<ConfigFile> configFiles = configService.getConfigFileByTemplate(APP_ID, ENV_ID, serviceTemplate.getUuid());

    verify(query).filter("appId", APP_ID);
    verify(query).filter("envId", ENV_ID);
    verify(query).filter("templateId", TEMPLATE_ID);
    assertThat(configFiles.get(0)).isEqualTo(configFile);
  }

  /**
   * Should download.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldDownload() {
    ConfigFile configFile = ConfigFile.builder()
                                .envId(ENV_ID)
                                .entityType(EntityType.SERVICE_TEMPLATE)
                                .entityId(TEMPLATE_ID)
                                .templateId(TEMPLATE_ID)
                                .relativeFilePath("PATH")
                                .build();
    configFile.setAppId(APP_ID);
    configFile.setName(FILE_NAME);
    configFile.setFileName(FILE_NAME);
    configFile.setUuid(FILE_ID);
    configFile.setFileUuid("GFS_FILE_ID");
    configFile.setChecksum("CHECKSUM");
    configFile.setSize(12);

    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, FILE_ID)).thenReturn(configFile);
    File file = configService.download(APP_ID, FILE_ID);
    verify(wingsPersistence).getWithAppId(ConfigFile.class, APP_ID, FILE_ID);
    verify(fileService).download(eq("GFS_FILE_ID"), any(File.class), eq(FileBucket.CONFIGS));
    assertThat(file.getName()).isEqualTo("PATH");
  }

  /**
   * Should update.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    ConfigFile configFile = ConfigFile.builder()
                                .envId(ENV_ID)
                                .entityType(EntityType.SERVICE_TEMPLATE)
                                .entityId(TEMPLATE_ID)
                                .templateId(TEMPLATE_ID)
                                .relativeFilePath("PATH")
                                .encrypted(true)
                                .build();
    configFile.setAccountId(ACCOUNT_ID);
    configFile.setAppId(APP_ID);
    configFile.setUuid(FILE_ID);
    configFile.setFileUuid("GFS_FILE_ID");
    configFile.setFileName(FILE_NAME);
    configFile.setChecksum("CHECKSUM");
    configFile.setSize(12);
    configFile.setEncryptedFileId("ENC_ID");
    configFile.setName("Name00");

    when(wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId()))
        .thenReturn(EncryptedData.builder().encryptedValue("csd".toCharArray()).build());
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, FILE_ID)).thenReturn(configFile);
    BoundedInputStream boundedInputStream = new BoundedInputStream(this.inputStream);
    when(query.get()).thenReturn(configFile);
    configService.update(configFile, boundedInputStream);
    ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(wingsPersistence).updateFields(eq(ConfigFile.class), eq(FILE_ID), argumentCaptor.capture());

    Map<String, Object> updateMap = argumentCaptor.getValue();
    assertThat(updateMap.size()).isEqualTo(5);
    assertThat(updateMap.get("encrypted")).isEqualTo(true);
    assertThat(updateMap.get("encryptedFileId")).isNotNull();
    assertThat(updateMap.get("size")).isNotNull();
  }

  /**
   * Should delete.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDelete() {
    when(wingsPersistence.delete(any(Query.class))).thenReturn(true);
    when(wingsPersistence.createQuery(ConfigFile.class)).thenReturn(query);
    doNothing().when(configFileAuthHandler).authorize(any());

    configService.delete(APP_ID, FILE_ID);
    verify(wingsPersistence).delete(query);
    verify(fileService).deleteAllFilesForEntity(FILE_ID, FileBucket.CONFIGS);
  }

  /**
   * Should get config files for entity.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetConfigFilesForEntity() {
    ConfigFile configFile = ConfigFile.builder()
                                .entityType(EntityType.SERVICE_TEMPLATE)
                                .entityId(TEMPLATE_ID)
                                .templateId(TEMPLATE_ID)
                                .build();

    configFile.setAppId(APP_ID);
    configFile.setName(FILE_NAME);
    configFile.setFileName(FILE_NAME);
    PageResponse<ConfigFile> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(configFile));
    pageResponse.setTotal(1l);

    PageRequest<ConfigFile> pageRequest = aPageRequest()
                                              .addFilter("appId", Operator.EQ, APP_ID)
                                              .addFilter("templateId", Operator.EQ, TEMPLATE_ID)
                                              .addFilter("entityId", Operator.EQ, "ENTITY_ID")
                                              .build();

    when(wingsPersistence.query(ConfigFile.class, pageRequest)).thenReturn(pageResponse);

    configService.getConfigFilesForEntity(APP_ID, TEMPLATE_ID, "ENTITY_ID");
    verify(wingsPersistence).query(ConfigFile.class, pageRequest);
  }

  /**
   * Should delete by entity id.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldDeleteByEntityId() {
    ConfigFile configFile = ConfigFile.builder()
                                .envId(ENV_ID)
                                .entityType(EntityType.SERVICE_TEMPLATE)
                                .entityId(TEMPLATE_ID)
                                .templateId(TEMPLATE_ID)
                                .relativeFilePath("PATH")
                                .build();

    configFile.setAppId(APP_ID);
    configFile.setUuid(FILE_ID);
    configFile.setName(FILE_NAME);
    configFile.setFileUuid("GFS_FILE_ID");
    configFile.setFileName(FILE_NAME);
    configFile.setChecksum("CHECKSUM");
    configFile.setSize(12);

    PageResponse<ConfigFile> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(configFile));
    pageResponse.setTotal(1l);

    PageRequest<ConfigFile> pageRequest = aPageRequest()
                                              .addFilter("appId", Operator.EQ, APP_ID)
                                              .addFilter("templateId", Operator.EQ, TEMPLATE_ID)
                                              .addFilter("entityId", Operator.EQ, "ENTITY_ID")
                                              .build();

    when(wingsPersistence.createQuery(ConfigFile.class)).thenReturn(query);
    when(wingsPersistence.query(ConfigFile.class, pageRequest)).thenReturn(pageResponse);
    when(wingsPersistence.delete(any(Query.class))).thenReturn(true);

    configService.deleteByEntityId(APP_ID, TEMPLATE_ID, "ENTITY_ID");
    verify(wingsPersistence).delete(query);
    verify(fileService).deleteAllFilesForEntity(FILE_ID, FileBucket.CONFIGS);
  }

  /**
   * Should validate and resolve file path.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldValidateAndResolveFilePath() throws Exception {
    assertThat(configService.validateAndResolveFilePath("config/abc.txt")).isEqualTo("config/abc.txt");
    assertThat(configService.validateAndResolveFilePath("./config/abc.txt")).isEqualTo("config/abc.txt");
    assertThat(configService.validateAndResolveFilePath("./config/./abc.txt")).isEqualTo("config/abc.txt");
    assertThat(configService.validateAndResolveFilePath("./config/./abc.txt")).isEqualTo("config/abc.txt");
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> configService.validateAndResolveFilePath("/config"))
        .withMessage(INVALID_ARGUMENT.name());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldSaveEntityVersionWithAccountId() throws Exception {
    EntityVersion entityVersion = entityVersionService.newEntityVersion(
        APP_ID, EntityType.ENVIRONMENT, ENTITY_ID, PARENT, EntityVersion.ChangeType.CREATED, "Data");
    assertThat(entityVersion.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testShouldDeleteByAccountId() {
    when(wingsPersistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    Query<PersistentEntity> mockedQueryEntity = mock(Query.class);
    when(wingsPersistence.createQuery(any())).thenReturn(mockedQueryEntity);
    when(mockedQueryEntity.filter(anyString(), any())).thenReturn(mockedQueryEntity);
    entityVersionService.deleteByAccountId(ACCOUNT_ID);
    verify(wingsPersistence, times(1)).delete((Query<PersistentEntity>) any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldNotUpdateWithDuplicateName() {
    ConfigFile configFile = createConfigFile("PATH", UUIDGenerator.generateUuid());
    ConfigFile existingConfigFile = createConfigFile("PATH", UUIDGenerator.generateUuid());

    when(query.get()).thenReturn(existingConfigFile);
    when(wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId()))
        .thenReturn(EncryptedData.builder().encryptedValue("csd".toCharArray()).build());
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, configFile.getUuid())).thenReturn(configFile);
    BoundedInputStream boundedInputStream = new BoundedInputStream(this.inputStream);
    configService.update(configFile, boundedInputStream);

    assertThatThrownBy(() -> configService.update(configFile, boundedInputStream))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Duplicate name PATH");
  }

  private ConfigFile createConfigFile(String relativeFilePath, String uuid) {
    ConfigFile configFile = ConfigFile.builder()
                                .envId(ENV_ID)
                                .entityType(EntityType.SERVICE)
                                .entityId(TEMPLATE_ID)
                                .templateId(TEMPLATE_ID)
                                .relativeFilePath(relativeFilePath)
                                .encrypted(true)
                                .build();
    configFile.setAccountId(ACCOUNT_ID);
    configFile.setAppId(APP_ID);
    configFile.setUuid(uuid);
    configFile.setFileUuid("GFS_FILE_ID");
    configFile.setFileName(FILE_NAME);
    configFile.setChecksum("CHECKSUM");
    configFile.setSize(12);
    configFile.setEncryptedFileId("ENC_ID");
    configFile.setName("Name00");

    return configFile;
  }

  private ConfigFile createConfigFileOverrideForAllServices(String relativeFilePath, String uuid) {
    ConfigFile configFile = ConfigFile.builder()
                                .envId(ENV_ID)
                                .entityType(EntityType.ENVIRONMENT)
                                .entityId(ENV_ID)
                                .templateId(TEMPLATE_ID)
                                .relativeFilePath(relativeFilePath)
                                .encrypted(true)
                                .build();
    configFile.setAccountId(ACCOUNT_ID);
    configFile.setAppId(APP_ID);
    configFile.setUuid(uuid);
    configFile.setFileUuid("GFS_FILE_ID");
    configFile.setFileName(FILE_NAME);
    configFile.setChecksum("CHECKSUM");
    configFile.setSize(12);
    configFile.setEncryptedFileId("ENC_ID");
    configFile.setName("Name00");

    return configFile;
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldPruneByEnvironment() {
    configFileDeletionSetup(ENV_ID);

    configService.pruneByEnvironment(APP_ID, ENV_ID);
    verify(wingsPersistence, times(2)).delete(query);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(
            eq(ACCOUNT_ID), any(ConfigFile.class), isNull(), eq(Event.Type.DELETE), eq(false), eq(false));
    verify(wingsPersistence, times(2)).save(any(EncryptedData.class));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldPruneByService() {
    configFileDeletionSetup(SERVICE_ID);

    configService.pruneByService(APP_ID, SERVICE_ID);
    verify(wingsPersistence, times(2)).delete(query);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(
            eq(ACCOUNT_ID), any(ConfigFile.class), isNull(), eq(Event.Type.DELETE), eq(false), eq(false));
    verify(wingsPersistence, times(2)).save(any(EncryptedData.class));
  }

  private void configFileDeletionSetup(String entityId) {
    ConfigFile configFile = createConfigFile(PATH, UUID);
    ConfigFile encryptedConfigFile = createConfigFile("path", "uuid");

    PageResponse<ConfigFile> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(configFile, encryptedConfigFile));
    pageResponse.setTotal(2L);

    PageRequest<ConfigFile> pageRequest = aPageRequest()
                                              .addFilter("appId", Operator.EQ, APP_ID)
                                              .addFilter("templateId", Operator.EQ, DEFAULT_TEMPLATE_ID)
                                              .addFilter("entityId", Operator.EQ, entityId)
                                              .build();

    when(wingsPersistence.createQuery(ConfigFile.class)).thenReturn(query);
    when(query.filter(ConfigFile.APP_ID_KEY2, APP_ID)).thenReturn(query);
    when(query.filter(eq(ID_KEY), anyString())).thenReturn(query);
    when(query.get()).thenReturn(encryptedConfigFile);
    when(wingsPersistence.query(ConfigFile.class, pageRequest)).thenReturn(pageResponse);
    when(wingsPersistence.delete(any(Query.class))).thenReturn(true);
    when(wingsPersistence.get(EncryptedData.class, encryptedConfigFile.getEncryptedFileId()))
        .thenReturn(EncryptedData.builder().encryptedValue("csd".toCharArray()).build());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDeleteFromYaml() {
    ConfigFile configFile = createConfigFile(PATH, UUID);
    configFile.setEncrypted(false);
    when(wingsPersistence.createQuery(ConfigFile.class)).thenReturn(query);
    when(query.filter(ConfigFile.APP_ID_KEY2, APP_ID)).thenReturn(query);
    when(query.filter(ConfigFileKeys.entityType, EntityType.SERVICE)).thenReturn(query);
    when(query.filter(ConfigFileKeys.entityId, ENTITY_ID)).thenReturn(query);
    when(query.filter(ConfigFileKeys.relativeFilePath, PATH)).thenReturn(query);
    when(query.get()).thenReturn(configFile);
    when(wingsPersistence.delete(ConfigFile.class, UUID)).thenReturn(true);

    configService.delete(APP_ID, ENTITY_ID, EntityType.SERVICE, PATH);
    verify(wingsPersistence).delete(ConfigFile.class, UUID);
    verify(yamlPushService)
        .pushYamlChangeSet(
            eq(ACCOUNT_ID), any(ConfigFile.class), isNull(), eq(Event.Type.DELETE), eq(false), eq(false));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldSaveEncryptedConfigFile() {
    ConfigFile configFile = createConfigFile(PATH, UUID);
    BoundedInputStream inputStream = new BoundedInputStream(this.inputStream);
    when(wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId()))
        .thenReturn(EncryptedData.builder().encryptedValue("csd".toCharArray()).fileSize(12).build());
    when(wingsPersistence.save(configFile)).thenReturn(UUID);
    when(serviceResourceService.exist(eq(APP_ID), anyString())).thenReturn(true);
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, UUID)).thenReturn(configFile);

    configService.save(configFile, inputStream);
    verify(wingsPersistence, times(2)).get(EncryptedData.class, configFile.getEncryptedFileId());
    verify(wingsPersistence).save(configFile);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testEncryptedFileDownload() {
    ConfigFile configFile = createConfigFile(PATH, UUID);
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, UUID)).thenReturn(configFile);
    when(wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId()))
        .thenReturn(
            EncryptedData.builder().accountId(ACCOUNT_ID).encryptedValue("csd".toCharArray()).fileSize(12).build());
    when(secretManager.getFile(eq(ACCOUNT_ID), anyString(), any(File.class))).thenAnswer(i -> i.getArguments()[2]);

    File file = configService.download(APP_ID, UUID);
    verify(wingsPersistence).getWithAppId(ConfigFile.class, APP_ID, UUID);
    verify(secretManager).getFile(eq(ACCOUNT_ID), anyString(), any(File.class));
    assertThat(file.getName()).isEqualTo(PATH);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testEncryptedFileDownloadWithVersion() {
    ConfigFile configFile = createConfigFile(PATH, UUID);
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, UUID)).thenReturn(configFile);
    when(wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId()))
        .thenReturn(
            EncryptedData.builder().accountId(ACCOUNT_ID).encryptedValue("csd".toCharArray()).fileSize(12).build());
    when(secretManager.getFile(eq(ACCOUNT_ID), anyString(), any(File.class))).thenAnswer(i -> i.getArguments()[2]);

    File file = configService.download(APP_ID, UUID, 1);
    verify(wingsPersistence).getWithAppId(ConfigFile.class, APP_ID, UUID);
    verify(secretManager).getFile(eq(ACCOUNT_ID), anyString(), any(File.class));
    assertThat(file.getName()).isEqualTo(PATH);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testFileDownloadWithVersion() {
    ConfigFile configFile = createConfigFile(PATH, UUID);
    configFile.setEncrypted(false);
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, UUID)).thenReturn(configFile);

    File file = configService.download(APP_ID, UUID, 1);
    verify(wingsPersistence).getWithAppId(ConfigFile.class, APP_ID, UUID);
    verify(fileService).download(eq("GFS_FILE_ID"), any(File.class), eq(FileBucket.CONFIGS));
    assertThat(file.getName()).isEqualTo(PATH);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testEncryptedFileDownloadForActivity() {
    ConfigFile configFile = createConfigFile(PATH, UUID);
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, UUID)).thenReturn(configFile);
    when(wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId()))
        .thenReturn(
            EncryptedData.builder().accountId(ACCOUNT_ID).encryptedValue("csd".toCharArray()).fileSize(12).build());
    when(secretManager.getFile(eq(ACCOUNT_ID), anyString(), any(File.class))).thenAnswer(i -> i.getArguments()[2]);
    when(activityService.get(ACTIVITY_ID, APP_ID))
        .thenReturn(Activity.builder().environmentId(ENV_ID).workflowExecutionId(WORKFLOW_EXECUTION_ID).build());

    File file = configService.downloadForActivity(APP_ID, UUID, ACTIVITY_ID);
    verify(wingsPersistence).getWithAppId(ConfigFile.class, APP_ID, UUID);
    verify(secretManager).getFile(eq(ACCOUNT_ID), anyString(), any(File.class));
    assertThat(file.getName()).isEqualTo(PATH);
    verify(wingsPersistence).save(any(SecretUsageLog.class));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testFileDownloadForActivity() {
    ConfigFile configFile = createConfigFile(PATH, UUID);
    configFile.setEncrypted(false);
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, UUID)).thenReturn(configFile);

    File file = configService.downloadForActivity(APP_ID, UUID, ACTIVITY_ID);
    verify(wingsPersistence).getWithAppId(ConfigFile.class, APP_ID, UUID);
    verify(fileService).download(eq("GFS_FILE_ID"), any(File.class), eq(FileBucket.CONFIGS));
    assertThat(file.getName()).isEqualTo(PATH);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldUpdateNonEncryptedFiles() {
    ConfigFile configFile = createConfigFile(PATH, UUID);
    configFile.setEncrypted(false);
    configFile.setDescription("This is a test");
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, UUID)).thenReturn(configFile);
    BoundedInputStream boundedInputStream = new BoundedInputStream(this.inputStream);
    when(query.get()).thenReturn(configFile);
    when(fileService.saveFile(configFile, boundedInputStream, FileBucket.CONFIGS)).thenReturn(FILE_ID);

    configService.update(configFile, boundedInputStream);
    ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(wingsPersistence).updateFields(eq(ConfigFile.class), eq(UUID), argumentCaptor.capture());
    Map<String, Object> updateMap = argumentCaptor.getValue();
    assertThat(updateMap.size()).isEqualTo(9);
    assertThat(updateMap.get("encrypted")).isEqualTo(false);
    assertThat(updateMap.get("encryptedFileId")).isEqualTo("");
    assertThat(updateMap.get("size")).isNotNull();
    assertThat(updateMap.get("fileUuid")).isEqualTo("GFS_FILE_ID");
    assertThat(updateMap.get("checksum")).isEqualTo("CHECKSUM");
    assertThat(updateMap.get("description")).isEqualTo("This is a test");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldAllowRelativePathUpdateForService() {
    ConfigFile savedConfigFile = createConfigFile(PATH, UUID);
    ConfigFile configFile = createConfigFile(PATH + "/test", UUID);
    configFile.setEncrypted(false);
    configFile.setDescription("This is a test");
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, UUID)).thenAnswer(new Answer() {
      private int count = 0;

      @Override
      public Object answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count++;
          return savedConfigFile;
        }
        return configFile;
      }
    });

    when(wingsPersistence.createQuery(ConfigFile.class)).thenReturn(query);
    when(query.filter("appId", APP_ID)).thenReturn(query);
    when(query.filter(ConfigFileKeys.relativeFilePath, savedConfigFile.getRelativeFilePath())).thenReturn(query);

    FieldEnd fieldEnd = mock(FieldEnd.class);
    CriteriaContainer container = mock(CriteriaContainer.class);
    when(query.criteria(anyString())).thenReturn(fieldEnd);
    when(fieldEnd.equal(anyString())).thenReturn(container);
    when(fieldEnd.in(any(Iterable.class))).thenReturn(container);
    when(query.or(any(), any())).thenReturn(container);
    UpdateOperations updateOperations = mock(UpdateOperations.class);
    when(wingsPersistence.createUpdateOperations(ConfigFile.class)).thenReturn(updateOperations);
    when(updateOperations.set(eq("relativeFilePath"), anyString())).thenReturn(updateOperations);

    BoundedInputStream boundedInputStream = new BoundedInputStream(this.inputStream);
    when(query.get()).thenReturn(configFile);
    when(fileService.saveFile(configFile, boundedInputStream, FileBucket.CONFIGS)).thenReturn(FILE_ID);

    configService.update(configFile, boundedInputStream);
    verify(wingsPersistence).update(query, updateOperations);
    ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(wingsPersistence).updateFields(eq(ConfigFile.class), eq(UUID), argumentCaptor.capture());
    Map<String, Object> updateMap = argumentCaptor.getValue();
    assertThat(updateMap.size()).isEqualTo(9);
    assertThat(updateMap.get("encrypted")).isEqualTo(false);
    assertThat(updateMap.get("encryptedFileId")).isEqualTo("");
    assertThat(updateMap.get("size")).isNotNull();
    assertThat(updateMap.get("fileUuid")).isEqualTo("GFS_FILE_ID");
    assertThat(updateMap.get("checksum")).isEqualTo("CHECKSUM");
    assertThat(updateMap.get("description")).isEqualTo("This is a test");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldAllowRelativePathUpdateForFileOverrideForAllServices() {
    ConfigFile savedConfigFile = createConfigFileOverrideForAllServices(PATH, UUID);
    ConfigFile configFile = createConfigFileOverrideForAllServices(PATH + "/test", UUID);
    configFile.setEncrypted(false);
    configFile.setDescription("This is a test");
    when(wingsPersistence.getWithAppId(ConfigFile.class, APP_ID, UUID)).thenAnswer(new Answer() {
      private int count = 0;

      @Override
      public Object answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count++;
          return savedConfigFile;
        }
        return configFile;
      }
    });

    when(wingsPersistence.createQuery(ConfigFile.class)).thenReturn(query);
    when(query.filter("appId", APP_ID)).thenReturn(query);
    when(query.filter(ConfigFileKeys.relativeFilePath, savedConfigFile.getRelativeFilePath())).thenReturn(query);

    FieldEnd fieldEnd = mock(FieldEnd.class);
    CriteriaContainer container = mock(CriteriaContainer.class);
    when(query.criteria(anyString())).thenReturn(fieldEnd);
    when(fieldEnd.equal(anyString())).thenReturn(container);
    when(fieldEnd.in(any(Iterable.class))).thenReturn(container);
    when(query.or(any(), any())).thenReturn(container);
    UpdateOperations updateOperations = mock(UpdateOperations.class);
    when(wingsPersistence.createUpdateOperations(ConfigFile.class)).thenReturn(updateOperations);
    when(updateOperations.set(eq("relativeFilePath"), anyString())).thenReturn(updateOperations);

    BoundedInputStream boundedInputStream = new BoundedInputStream(this.inputStream);
    when(query.get()).thenReturn(configFile);
    when(fileService.saveFile(configFile, boundedInputStream, FileBucket.CONFIGS)).thenReturn(FILE_ID);

    configService.update(configFile, boundedInputStream);
    verify(wingsPersistence).update(query, updateOperations);
    ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(wingsPersistence).updateFields(eq(ConfigFile.class), eq(UUID), argumentCaptor.capture());
    Map<String, Object> updateMap = argumentCaptor.getValue();
    assertThat(updateMap.size()).isEqualTo(9);
    assertThat(updateMap.get("encrypted")).isEqualTo(false);
    assertThat(updateMap.get("encryptedFileId")).isEqualTo("");
    assertThat(updateMap.get("size")).isNotNull();
    assertThat(updateMap.get("fileUuid")).isEqualTo("GFS_FILE_ID");
    assertThat(updateMap.get("checksum")).isEqualTo("CHECKSUM");
    assertThat(updateMap.get("description")).isEqualTo("This is a test");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetFileContents() {
    ConfigFile configfile = createConfigFile(PATH, UUID);
    configfile.setEncrypted(false);
    when(fileService.openDownloadStream(configfile.getFileUuid(), FileBucket.CONFIGS)).thenReturn(inputStream);

    byte[] contents = configService.getFileContent(APP_ID, configfile);
    verify(fileService).openDownloadStream(configfile.getFileUuid(), FileBucket.CONFIGS);
    assertThat(contents).isNotEmpty();

    configfile.setEncrypted(true);
    when(secretManager.getFileContents(ACCOUNT_ID, configfile.getEncryptedFileId())).thenReturn(contents);

    byte[] contents2 = configService.getFileContent(APP_ID, configfile);
    verify(secretManager).getFileContents(ACCOUNT_ID, configfile.getEncryptedFileId());
    assertThat(contents2).isEqualTo(contents);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetConfigFileOverridesForEnv() {
    ConfigFile configFile = createConfigFile(PATH, UUID);
    configFile.setEncrypted(false);
    configFile.setEntityId(ENV_ID);
    configFile.setEntityType(EntityType.ENVIRONMENT);
    PageRequest pageRequest1 = aPageRequest()
                                   .addFilter("appId", EQ, APP_ID)
                                   .addFilter("entityType", EQ, EntityType.ENVIRONMENT)
                                   .addFilter("entityId", EQ, ENV_ID)
                                   .addFilter(SearchFilter.builder().build())
                                   .build();
    PageResponse<ConfigFile> pageResponse1 = new PageResponse<>();
    pageResponse1.setResponse(asList(configFile));
    pageResponse1.setTotal(1l);

    ConfigFile configFile2 = createConfigFile(PATH, UUID);
    configFile2.setEntityId(SERVICE_TEMPLATE_ID);
    configFile2.setEntityType(EntityType.SERVICE_TEMPLATE);
    PageRequest pageRequest2 = aPageRequest()
                                   .addFilter("appId", EQ, APP_ID)
                                   .addFilter("entityType", EQ, EntityType.SERVICE_TEMPLATE)
                                   .addFilter("envId", EQ, ENV_ID)
                                   .addFilter(SearchFilter.builder().build())
                                   .build();
    PageResponse<ConfigFile> pageResponse2 = new PageResponse<>();
    pageResponse2.setResponse(asList(configFile2));
    pageResponse2.setTotal(1l);
    when(wingsPersistence.get(EncryptedData.class, configFile2.getEncryptedFileId()))
        .thenReturn(EncryptedData.builder().name("test").build());

    when(wingsPersistence.query(ConfigFile.class, pageRequest1)).thenReturn(pageResponse1);
    when(wingsPersistence.query(ConfigFile.class, pageRequest2)).thenReturn(pageResponse2);

    List<ConfigFile> configFiles = configService.getConfigFileOverridesForEnv(APP_ID, ENV_ID);
    assertThat(configFiles).isNotNull().hasSize(2);
    assertThat(configFiles).isEqualTo(asList(configFile, configFile2));
  }
}
