/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.EncryptedData.PARENT_ID_KEY;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS;
import static io.harness.rule.TestUserProvider.testUserProvider;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.settings.SettingVariableTypes.CONFIG_FILE;
import static software.wings.settings.SettingVariableTypes.KMS;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.EnvironmentType;
import io.harness.beans.MigrateSecretTask;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SecretChangeLog;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.beans.SecretUsageLog;
import io.harness.category.element.UnitTests;
import io.harness.datacollection.utils.EmptyPredicate;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.clients.LocalEncryptor;
import io.harness.eraro.ErrorCode;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.queue.QueueConsumer;
import io.harness.queue.TimerScheduledExecutorService;
import io.harness.rule.Owner;
import io.harness.rule.Repeat;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secrets.SecretMigrationEventListener;
import io.harness.secrets.SecretService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoSerializer;
import io.harness.stream.BoundedInputStream;

import software.wings.EncryptTestUtils;
import software.wings.SecretManagementTestHelper;
import software.wings.WingsBaseTest;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.Activity;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.ConfigFile;
import software.wings.beans.ConfigFile.ConfigOverrideType;
import software.wings.beans.EntityType;
import software.wings.beans.Event;
import software.wings.beans.KmsConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.OverrideType;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.security.HarnessUserGroup;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.features.api.PremiumFeature;
import software.wings.resources.ServiceVariableResource;
import software.wings.resources.secretsmanagement.KmsResource;
import software.wings.resources.secretsmanagement.SecretManagementResource;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.GenericEntityFilter.FilterType;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UsageRestrictions;
import software.wings.security.UsageRestrictions.AppEnvRestriction.AppEnvRestrictionBuilder;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserRestrictionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.SettingValidationService;
import software.wings.service.impl.UsageRestrictionsServiceImplTestBase;
import software.wings.service.impl.security.GlobalEncryptDecryptClient;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.newrelic.NewRelicService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.KmsService;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import dev.morphia.mapping.Mapper;
import dev.morphia.query.Query;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by rsingh on 9/29/17.
 */
@Slf4j
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._360_CG_MANAGER)
public class KmsTest extends WingsBaseTest {
  @Inject private KmsResource kmsResource;
  @Mock private AccountService accountService;
  @Mock private AppService appService;
  @Inject private HarnessUserGroupService harnessUserGroupService;
  @Inject private ManagerDecryptionService managerDecryptionService;
  @Inject private SettingsService settingsService;
  @Inject private SettingValidationService settingValidationService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private ServiceVariableResource serviceVariableResource;
  @Inject private SecretManagementDelegateService delegateService;
  @Inject private QueueConsumer<MigrateSecretTask> kmsTransitionConsumer;
  @Inject private LocalEncryptor localEncryptor;
  @Inject private LocalSecretManagerService localSecretManagerService;
  @Inject private SecretManagementTestHelper secretManagementTestHelper;
  @Inject private ConfigService configService;
  @Inject private SecretManagementResource secretManagementResource;
  @Inject private QueueConsumer<MigrateSecretTask> transitionKmsQueue;
  @Inject private SecretManager secretManager;
  @Inject private EncryptionService encryptionService;
  @Inject private WingsPersistence wingsPersistence;
  @Mock private ContainerService containerService;
  @Mock private NewRelicService newRelicService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private SecretManagementDelegateService secretManagementDelegateService;
  @Mock private KmsEncryptor kmsEncryptor;
  @Mock private PremiumFeature secretsManagementFeature;
  @Mock protected AuditServiceHelper auditServiceHelper;
  @Mock private GlobalEncryptDecryptClient globalEncryptDecryptClient;
  @Mock private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Inject @InjectMocks private KmsService kmsService;
  @Inject @InjectMocks private SecretService secretService;
  @Inject @InjectMocks private SecretManagerConfigService secretManagerConfigService;
  @Inject @InjectMocks private EntityVersionService entityVersionService;
  private SecretMigrationEventListener transitionEventListener;

  private final int numOfEncryptedValsForKms = 3;
  private final String userEmail = "rsingh@harness.io";
  private final String userName = "UTKARSH";
  private final User user = User.Builder.anUser().email(userEmail).name(userName).build();
  private String userId;
  private String accountId;
  private String kmsId;
  private String appId;
  private String workflowExecutionId;
  private String workflowName;
  private String envId;
  @Mock private FeatureFlagService featureFlagService;

  @Inject KryoSerializer kryoSerializer;

  @Before
  public void setup() throws IOException, NoSuchFieldException, IllegalAccessException {
    initMocks(this);

    Account account = getAccount(AccountType.PAID);
    accountId = account.getUuid();
    when(accountService.get(accountId)).thenReturn(account);
    when(appService.getAccountIdByAppId(appId)).thenReturn(accountId);
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);

    appId =
        wingsPersistence.save(Application.Builder.anApplication().name(generateUuid()).accountId(accountId).build());
    workflowName = generateUuid();
    envId = wingsPersistence.save(
        anEnvironment().environmentType(EnvironmentType.PROD).appId(appId).accountId(accountId).build());
    workflowExecutionId =
        wingsPersistence.save(WorkflowExecution.builder().name(workflowName).appId(appId).envId(envId).build());

    when(kmsEncryptor.encryptSecret(anyString(), any(), any())).then(invocation -> {
      Object[] args = invocation.getArguments();
      if (args[2] instanceof KmsConfig) {
        return EncryptTestUtils.encrypt((String) args[0], ((String) args[1]).toCharArray(), (KmsConfig) args[2]);
      }
      return localEncryptor.encryptSecret(
          (String) args[0], (String) args[1], localSecretManagerService.getEncryptionConfig((String) args[0]));
    });

    when(kmsEncryptor.fetchSecretValue(anyString(), any(), any())).then(invocation -> {
      Object[] args = invocation.getArguments();
      if (args[2] instanceof KmsConfig) {
        return EncryptTestUtils.decrypt((EncryptedRecord) args[1], (KmsConfig) args[2]);
      }
      return localEncryptor.fetchSecretValue(
          (String) args[0], (EncryptedRecord) args[1], localSecretManagerService.getEncryptionConfig((String) args[0]));
    });

    when(kmsEncryptorsRegistry.getKmsEncryptor(any())).thenReturn(kmsEncryptor);
    when(delegateProxyFactory.getV2(eq(SecretManagementDelegateService.class), any(SyncTaskContext.class)))
        .thenReturn(secretManagementDelegateService);
    when(delegateProxyFactory.getV2(eq(EncryptionService.class), any(SyncTaskContext.class)))
        .thenReturn(encryptionService);
    when(delegateProxyFactory.getV2(eq(ContainerService.class), any(SyncTaskContext.class)))
        .thenReturn(containerService);
    when(containerService.validate(any(ContainerServiceParams.class), anyBoolean())).thenReturn(true);
    doNothing().when(newRelicService).validateConfig(any(), any(), any());
    FieldUtils.writeField(secretService, "kmsRegistry", kmsEncryptorsRegistry, true);
    FieldUtils.writeField(encryptionService, "kmsEncryptorsRegistry", kmsEncryptorsRegistry, true);
    FieldUtils.writeField(managerDecryptionService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(wingsPersistence, "secretManager", secretManager, true);
    FieldUtils.writeField(configService, "secretManager", secretManager, true);
    FieldUtils.writeField(settingValidationService, "newRelicService", newRelicService, true);
    FieldUtils.writeField(settingsService, "settingValidationService", settingValidationService, true);
    FieldUtils.writeField(infrastructureMappingService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(kmsResource, "kmsService", kmsService, true);
    FieldUtils.writeField(secretManagementResource, "secretManager", secretManager, true);
    FieldUtils.writeField(secretManager, "secretService", secretService, true);
    userId = wingsPersistence.save(user);
    UserThreadLocal.set(user);
    testUserProvider.setActiveUser(EmbeddedUser.builder().uuid(user.getUuid()).name(userName).email(userEmail).build());

    // Add current user to harness user group so that save-global-kms operation can succeed
    HarnessUserGroup harnessUserGroup =
        HarnessUserGroup.builder().memberIds(Sets.newHashSet(userId)).accountIds(Sets.newHashSet(accountId)).build();
    harnessUserGroupService.save(harnessUserGroup);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void getKmsConfigGlobal() {
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setAccountId(GLOBAL_ACCOUNT_ID);

    LocalEncryptionConfig localEncryptionConfig =
        (LocalEncryptionConfig) secretManagerConfigService.getDefaultSecretManager(UUID.randomUUID().toString());
    assertThat(localEncryptionConfig).isNotNull();

    kmsResource.saveGlobalKmsConfig(accountId, kryoSerializer.clone(kmsConfig));

    KmsConfig savedConfig =
        (KmsConfig) secretManagerConfigService.getDefaultSecretManager(UUID.randomUUID().toString());
    kmsConfig.setUuid(savedConfig.getUuid());
    assertThat(savedConfig).isEqualTo(kmsConfig);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)

  public void getGetGlobalKmsConfig() {
    KmsConfig globalKmsConfig = secretManagementTestHelper.getKmsConfig();
    globalKmsConfig.setName("Global config");
    globalKmsConfig.setDefault(true);
    kmsResource.saveGlobalKmsConfig(accountId, kryoSerializer.clone(globalKmsConfig));

    KmsConfig savedGlobalKmsConfig = kmsService.getGlobalKmsConfig();
    assertThat(savedGlobalKmsConfig).isNotNull();

    // Verified that retrieved global KMS config secret fields are decrypted properly.
    assertThat(savedGlobalKmsConfig.getName()).isEqualTo(globalKmsConfig.getName());
    assertThat(savedGlobalKmsConfig.getAccessKey()).isEqualTo(globalKmsConfig.getAccessKey());
    assertThat(savedGlobalKmsConfig.getSecretKey()).isEqualTo(globalKmsConfig.getSecretKey());
    assertThat(savedGlobalKmsConfig.getKmsArn()).isEqualTo(globalKmsConfig.getKmsArn());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)

  public void updateFileWithGlobalKms() throws IOException {
    KmsConfig globalKmsConfig = secretManagementTestHelper.getKmsConfig();
    globalKmsConfig.setName("Global config");
    globalKmsConfig.setDefault(true);
    kmsId = kmsResource.saveGlobalKmsConfig(accountId, globalKmsConfig).getResource();

    String randomAccountId = UUID.randomUUID().toString();

    String secretName = UUID.randomUUID().toString();
    File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
    SecretFile secretFile = SecretFile.builder()
                                .name(secretName)
                                .kmsId(kmsId)
                                .inheritScopesFromSM(true)
                                .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToSave)))
                                .build();
    String secretFileId = secretManager.saveSecretFile(randomAccountId, secretFile);
    assertThat(secretFileId).isNotNull();

    String newSecretName = UUID.randomUUID().toString();
    File fileToUpdate = new File("400-rest/src/test/resources/encryption/file_to_update.txt");
    secretFile = SecretFile.builder()
                     .name(newSecretName)
                     .kmsId(kmsId)
                     .inheritScopesFromSM(true)
                     .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToUpdate)))
                     .build();
    boolean result = secretManager.updateSecretFile(randomAccountId, secretFileId, secretFile);
    assertThat(result).isTrue();
    assertThat(secretManager.deleteSecret(randomAccountId, secretFileId, new HashMap<>(), true)).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void getKmsConfigForAccount() {
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setAccountId(accountId);

    kmsResource.saveKmsConfig(kmsConfig.getAccountId(), kryoSerializer.clone(kmsConfig));

    KmsConfig savedConfig = (KmsConfig) secretManagerConfigService.getDefaultSecretManager(kmsConfig.getAccountId());
    kmsConfig.setUuid(savedConfig.getUuid());
    assertThat(savedConfig).isEqualTo(kmsConfig);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Repeat(times = 3, successes = 1)
  @Category(UnitTests.class)
  public void saveAndEditConfig() {
    String name = UUID.randomUUID().toString();
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setName(name);
    kmsConfig.setAccountId(accountId);

    kmsResource.saveKmsConfig(kmsConfig.getAccountId(), kryoSerializer.clone(kmsConfig));

    KmsConfig savedConfig = (KmsConfig) secretManagerConfigService.getDefaultSecretManager(kmsConfig.getAccountId());
    kmsConfig.setUuid(savedConfig.getUuid());
    assertThat(savedConfig).isEqualTo(kmsConfig);
    assertThat(savedConfig.getName()).isEqualTo(name);
    List<EncryptedData> encryptedDataList =
        wingsPersistence.createQuery(EncryptedData.class, excludeAuthority).asList();
    assertThat(encryptedDataList).hasSize(numOfEncryptedValsForKms);
    for (EncryptedData encryptedData : encryptedDataList) {
      assertThat(encryptedData.getName().equals(name + "_accessKey")
          || encryptedData.getName().equals(name + "_secretKey") || encryptedData.getName().equals(name + "_arn"))
          .isTrue();
      assertThat(encryptedData.getParents()).hasSize(1);
      assertThat(encryptedData.containsParent(savedConfig.getUuid(), KMS)).isTrue();
    }

    name = UUID.randomUUID().toString();
    kmsConfig = secretManagementTestHelper.getKmsConfig();
    savedConfig.setAccessKey(kmsConfig.getAccessKey());
    savedConfig.setSecretKey(kmsConfig.getSecretKey());
    savedConfig.setKmsArn(kmsConfig.getKmsArn());
    savedConfig.setName(name);
    kmsResource.saveKmsConfig(accountId, savedConfig);
    encryptedDataList =
        wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId).asList();
    assertThat(encryptedDataList).hasSize(numOfEncryptedValsForKms);
    for (EncryptedData encryptedData : encryptedDataList) {
      assertThat(encryptedData.getName().equals(name + "_accessKey")
          || encryptedData.getName().equals(name + "_secretKey") || encryptedData.getName().equals(name + "_arn"))
          .isTrue();
      assertThat(encryptedData.getParents()).hasSize(1);
      assertThat(encryptedData.containsParent(savedConfig.getUuid(), KMS)).isTrue();
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveAndEditConfig_withMaskedSecrets_changeNameDefaultOnly() {
    String name = UUID.randomUUID().toString();
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setName(name);
    kmsConfig.setAccountId(accountId);

    kmsService.saveKmsConfig(accountId, kryoSerializer.clone(kmsConfig));

    KmsConfig savedConfig = (KmsConfig) secretManagerConfigService.getDefaultSecretManager(accountId);
    assertThat(savedConfig.getAccessKey()).isEqualTo(kmsConfig.getAccessKey());
    assertThat(savedConfig.getSecretKey()).isEqualTo(kmsConfig.getSecretKey());
    assertThat(savedConfig.getKmsArn()).isEqualTo(kmsConfig.getKmsArn());
    assertThat(savedConfig.getName()).isEqualTo(kmsConfig.getName());
    assertThat(savedConfig.isDefault()).isEqualTo(true);

    String newName = UUID.randomUUID().toString();
    kmsConfig.setUuid(savedConfig.getUuid());
    kmsConfig.setName(newName);
    kmsConfig.setDefault(false);
    kmsConfig.maskSecrets();

    // Masked Secrets, only name and default flag should be updated.
    kmsService.saveKmsConfig(accountId, kryoSerializer.clone(kmsConfig));

    KmsConfig modifiedSavedConfig = kmsService.getKmsConfig(accountId, savedConfig.getUuid());
    assertThat(modifiedSavedConfig.getAccessKey()).isEqualTo(savedConfig.getAccessKey());
    assertThat(modifiedSavedConfig.getSecretKey()).isEqualTo(savedConfig.getSecretKey());
    assertThat(modifiedSavedConfig.getKmsArn()).isEqualTo(savedConfig.getKmsArn());
    assertThat(modifiedSavedConfig.getName()).isEqualTo(kmsConfig.getName());
    assertThat(modifiedSavedConfig.isDefault()).isEqualTo(false);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void localEncryptionWhileSaving() {
    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    appDynamicsConfig.setPassword(password.toCharArray());
    appDynamicsConfig.setEncryptedPassword(null);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(((AppDynamicsConfig) savedAttribute.getValue()).getEncryptedPassword()).isNotNull();
    assertThat(((AppDynamicsConfig) savedAttribute.getValue()).getPassword()).isNull();
    encryptionService.decrypt((EncryptableSetting) savedAttribute.getValue(),
        secretManager.getEncryptionDetails((EncryptableSetting) savedAttribute.getValue(), workflowExecutionId, appId),
        false);
    assertThat(savedAttribute.getValue()).isEqualTo(appDynamicsConfig);
    assertThat(((AppDynamicsConfig) savedAttribute.getValue()).getEncryptedPassword()).isNull();
    assertThat(new String(((AppDynamicsConfig) savedAttribute.getValue()).getPassword())).isEqualTo(password);
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class);
    assertThat(query.count()).isEqualTo(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void localEncryptionWhileSavingNullEncryptedData() {
    final ArtifactoryConfig artifactoryConfig = ArtifactoryConfig.builder()
                                                    .accountId(UUID.randomUUID().toString())
                                                    .artifactoryUrl(UUID.randomUUID().toString())
                                                    .username(UUID.randomUUID().toString())
                                                    .password(null)
                                                    .build();

    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(artifactoryConfig.getAccountId())
                                            .withValue(artifactoryConfig)
                                            .withAppId(UUID.randomUUID().toString())
                                            .withCategory(SettingCategory.CONNECTOR)
                                            .withEnvId(UUID.randomUUID().toString())
                                            .withName(UUID.randomUUID().toString())
                                            .build();

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(((ArtifactoryConfig) savedAttribute.getValue()).getEncryptedPassword()).isNull();
    assertThat(((ArtifactoryConfig) savedAttribute.getValue()).getPassword()).isNull();
    encryptionService.decrypt((EncryptableSetting) savedAttribute.getValue(),
        secretManager.getEncryptionDetails((EncryptableSetting) savedAttribute.getValue(), workflowExecutionId, appId),
        false);
    artifactoryConfig.setEncryptedPassword(null);
    assertThat(savedAttribute.getValue()).isEqualTo(artifactoryConfig);
    assertThat(((ArtifactoryConfig) savedAttribute.getValue()).getEncryptedPassword()).isNull();
    assertThat(((ArtifactoryConfig) savedAttribute.getValue()).getPassword()).isNull();
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class);
    assertThat(query.count()).isEqualTo(0);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void kmsEncryptionWhileSavingFeatureDisabled() {
    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    appDynamicsConfig.setPassword(password.toCharArray());
    appDynamicsConfig.setEncryptedPassword(null);
    AppDynamicsConfig savedConfig = (AppDynamicsConfig) savedAttribute.getValue();
    assertThat(savedConfig.getEncryptedPassword()).isNotNull();
    assertThat(savedConfig.getPassword()).isNull();
    encryptionService.decrypt(
        savedConfig, secretManager.getEncryptionDetails(savedConfig, workflowExecutionId, appId), false);
    assertThat(savedConfig).isEqualTo(appDynamicsConfig);
    assertThat(((AppDynamicsConfig) savedAttribute.getValue()).getEncryptedPassword()).isNull();
    assertThat(new String(savedConfig.getPassword())).isEqualTo(password);
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class);
    assertThat(query.count()).isEqualTo(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void enableKmsAfterSaving() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(((AppDynamicsConfig) savedAttribute.getValue()).getPassword()).isNull();
    assertThat(savedAttribute.getValue()).isEqualTo(appDynamicsConfig);
    assertThat(isBlank(((AppDynamicsConfig) savedAttribute.getValue()).getEncryptedPassword())).isFalse();
    encryptionService.decrypt((EncryptableSetting) savedAttribute.getValue(),
        secretManager.getEncryptionDetails((EncryptableSetting) savedAttribute.getValue(), workflowExecutionId, appId),
        false);
    assertThat(new String(((AppDynamicsConfig) savedAttribute.getValue()).getPassword())).isEqualTo(password);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void kmsEncryptionWhileSaving() throws IllegalAccessException {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(savedAttribute.getValue()).isEqualTo(appDynamicsConfig);
    assertThat(isBlank(((AppDynamicsConfig) savedAttribute.getValue()).getEncryptedPassword())).isFalse();

    Query<EncryptedData> query =
        wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(settingAttribute.getUuid());
    assertThat(query.count()).isEqualTo(1);
    EncryptedData encryptedData = query.get();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsConfig.getUuid());
    assertThat(encryptedData.getCreatedBy().getUuid()).isEqualTo(user.getUuid());
    assertThat(encryptedData.getCreatedBy().getEmail()).isEqualTo(userEmail);
    assertThat(encryptedData.getCreatedBy().getName()).isEqualTo(userName);

    List<SecretChangeLog> changeLogs =
        secretManager.getChangeLogs(accountId, savedAttributeId, SettingVariableTypes.APP_DYNAMICS);
    assertThat(changeLogs).hasSize(1);
    SecretChangeLog secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user.getUuid());
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user.getEmail());
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(user.getName());

    query = wingsPersistence.createQuery(EncryptedData.class);
    assertThat(query.count()).isEqualTo(numOfEncryptedValsForKms + 1);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testNewKmsConfigIfUnavailable() {
    Account account = getAccount(AccountType.PAID);
    String accountId = account.getUuid();

    when(accountService.get(accountId)).thenReturn(account);
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(false);

    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setAccountId(accountId);

    try {
      kmsService.saveKmsConfig(accountId, kmsConfig);
      fail("");
    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void secretUsageLog() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);

    Query<EncryptedData> encryptedDataQuery =
        wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(settingAttribute.getUuid());
    assertThat(encryptedDataQuery.count()).isEqualTo(1);
    EncryptedData encryptedData = encryptedDataQuery.get();

    secretManager.getEncryptionDetails((EncryptableSetting) savedAttribute.getValue(), appId, workflowExecutionId);
    Query<SecretUsageLog> query = wingsPersistence.createQuery(SecretUsageLog.class);
    assertThat(query.count()).isEqualTo(1);
    SecretUsageLog usageLog = query.get();
    assertThat(usageLog.getAccountId()).isEqualTo(accountId);
    assertThat(usageLog.getWorkflowExecutionId()).isEqualTo(workflowExecutionId);
    assertThat(usageLog.getAppId()).isEqualTo(appId);
    assertThat(usageLog.getEncryptedDataId()).isEqualTo(encryptedData.getUuid());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void kmsEncryptionSaveMultiple() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    int numOfSettingAttributes = 5;
    List<SettingAttribute> settingAttributes =
        SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
    wingsPersistence.save(settingAttributes);

    Collection<SecretManagerConfig> kmsConfigs =
        secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(1);
    assertThat(kmsConfigs.iterator().next().getNumOfEncryptedValue()).isEqualTo(numOfSettingAttributes);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testNumOfEncryptedValue() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveGlobalKmsConfig(accountId, kmsConfig);

    int numOfSettingAttributes1 = 5;
    List<SettingAttribute> settingAttributes =
        SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes1);
    wingsPersistence.save(settingAttributes);

    final String accountId2 = UUID.randomUUID().toString();
    settingAttributes.clear();

    int numOfSettingAttributes2 = 7;
    settingAttributes = SecretManagementTestHelper.getSettingAttributes(accountId2, numOfSettingAttributes2);
    wingsPersistence.save(settingAttributes);

    List<SecretManagerConfig> encryptionConfigs =
        secretManagementResource.listEncryptionConfig(accountId).getResource();
    assertThat(encryptionConfigs).hasSize(1);
    assertThat(encryptionConfigs.get(0).getNumOfEncryptedValue()).isEqualTo(numOfSettingAttributes1);

    encryptionConfigs = secretManagementResource.listEncryptionConfig(accountId2).getResource();
    assertThat(encryptionConfigs).hasSize(1);
    assertThat(encryptionConfigs.get(0).getNumOfEncryptedValue()).isEqualTo(numOfSettingAttributes2);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void noKmsEncryptionUpdateObject() throws IllegalAccessException {
    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(savedAttribute).isEqualTo(settingAttribute);
    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(1);

    ((AppDynamicsConfig) savedAttribute.getValue()).setUsername(UUID.randomUUID().toString());
    ((AppDynamicsConfig) savedAttribute.getValue()).setPassword(UUID.randomUUID().toString().toCharArray());
    User user1 = User.Builder.anUser().email(UUID.randomUUID().toString()).name("user1").build();
    wingsPersistence.save(user1);
    UserThreadLocal.set(user1);
    wingsPersistence.save(savedAttribute);

    SettingAttribute updatedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(updatedAttribute).isEqualTo(savedAttribute);
    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(1);

    Query<EncryptedData> query =
        wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);
    EncryptedData encryptedData = query.get();
    assertThat(encryptedData.getKmsId()).isNotNull();

    verifyChangeLogs(savedAttributeId, savedAttribute, user1);

    final String newPassWord = UUID.randomUUID().toString();
    final AppDynamicsConfig newAppDynamicsConfig =
        SecretManagementTestHelper.getAppDynamicsConfig(accountId, newPassWord);

    String updatedAppId = UUID.randomUUID().toString();
    String updatedName = UUID.randomUUID().toString();
    final Map<String, Object> keyValuePairs = new HashMap<>();
    keyValuePairs.put("name", updatedName);
    keyValuePairs.put("appId", updatedAppId);
    keyValuePairs.put("value", newAppDynamicsConfig);

    wingsPersistence.updateFields(SettingAttribute.class, savedAttributeId, keyValuePairs);
    query = wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);

    encryptedData = query.get();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.LOCAL);
    assertThat(encryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
    assertThat(encryptedData.getKmsId()).isNotNull();
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();

    savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(savedAttribute.getName()).isEqualTo(updatedName);
    assertThat(savedAttribute.getAppId()).isEqualTo(updatedAppId);

    AppDynamicsConfig updatedAppdynamicsConfig = (AppDynamicsConfig) savedAttribute.getValue();
    encryptionService.decrypt(
        updatedAppdynamicsConfig, secretManager.getEncryptionDetails(updatedAppdynamicsConfig, null, null), false);
    assertThat(String.valueOf(updatedAppdynamicsConfig.getPassword())).isEqualTo(newPassWord);
  }

  private void verifyChangeLogs(String savedAttributeId, SettingAttribute savedAttribute, User user1)
      throws IllegalAccessException {
    Query<EncryptedData> query;
    List<SecretChangeLog> changeLogs =
        secretManager.getChangeLogs(accountId, savedAttributeId, SettingVariableTypes.APP_DYNAMICS);
    assertThat(changeLogs).hasSize(2);
    SecretChangeLog secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user1.getUuid());
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user1.getEmail());
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(user1.getName());
    assertThat(secretChangeLog.getDescription()).isEqualTo(" Changed secret");

    secretChangeLog = changeLogs.get(1);
    assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user.getUuid());
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user.getEmail());
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(user.getName());
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");
  }

  @Test
  @Owner(developers = UTKARSH)
  @Repeat(times = 5, successes = 1)
  @Category(UnitTests.class)
  public void noKmsEncryptionUpdateServiceVariable() {
    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    SecretText secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .templateId(UUID.randomUUID().toString())
                                          .envId(UUID.randomUUID().toString())
                                          .entityType(EntityType.APPLICATION)
                                          .entityId(UUID.randomUUID().toString())
                                          .parentServiceVariableId(UUID.randomUUID().toString())
                                          .overrideType(OverrideType.ALL)
                                          .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                          .expression(UUID.randomUUID().toString())
                                          .accountId(accountId)
                                          .name(UUID.randomUUID().toString())
                                          .value(secretId.toCharArray())
                                          .type(ServiceVariableType.ENCRYPTED_TEXT)
                                          .build();

    String savedServiceVariableId = wingsPersistence.save(serviceVariable);
    ServiceVariable savedVariable = wingsPersistence.get(ServiceVariable.class, savedServiceVariableId);
    assertThat(wingsPersistence.createQuery(ServiceVariable.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(1);
    assertThat(savedVariable.getValue()).isNull();
    assertThat(savedVariable.getEncryptedValue()).isNotNull();

    Query<EncryptedData> query =
        wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedServiceVariableId);
    assertThat(query.count()).isEqualTo(1);
    EncryptedData encryptedData = query.get();
    assertThat(encryptedData.getKmsId()).isNotNull();

    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(serviceVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue);

    secretName = UUID.randomUUID().toString();
    secretValue = UUID.randomUUID().toString();
    secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    secretId = secretManager.saveSecretText(accountId, secretText, false);

    String updatedAppId = UUID.randomUUID().toString();
    String updatedName = UUID.randomUUID().toString();
    final Map<String, Object> keyValuePairs = new HashMap<>();
    keyValuePairs.put("name", updatedName);
    keyValuePairs.put("appId", updatedAppId);
    keyValuePairs.put("type", ServiceVariableType.ENCRYPTED_TEXT);
    keyValuePairs.put("value", secretId.toCharArray());

    wingsPersistence.updateFields(ServiceVariable.class, savedServiceVariableId, keyValuePairs);
    query = wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedServiceVariableId);
    assertThat(query.count()).isEqualTo(1);

    encryptedData = query.get();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.LOCAL);
    assertThat(encryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
    assertThat(encryptedData.getKmsId()).isNotNull();
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();

    savedVariable = wingsPersistence.get(ServiceVariable.class, savedServiceVariableId);
    assertThat(savedVariable.getName()).isEqualTo(updatedName);
    assertThat(savedVariable.getAppId()).isEqualTo(updatedAppId);
    assertThat(savedVariable.getEncryptedValue()).isEqualTo(secretId);

    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void kmsEncryptionUpdateObject() throws IllegalAccessException {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(savedAttribute).isEqualTo(settingAttribute);
    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);

    ((AppDynamicsConfig) savedAttribute.getValue()).setUsername(UUID.randomUUID().toString());
    ((AppDynamicsConfig) savedAttribute.getValue()).setPassword(UUID.randomUUID().toString().toCharArray());
    User user1 = User.Builder.anUser().email(UUID.randomUUID().toString()).name("user1").build();
    wingsPersistence.save(user1);
    UserThreadLocal.set(user1);
    wingsPersistence.save(savedAttribute);

    SettingAttribute updatedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(updatedAttribute).isEqualTo(savedAttribute);
    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);

    Query<EncryptedData> query =
        wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);
    EncryptedData encryptedData = query.get();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsConfig.getUuid());

    verifyChangeLogs(savedAttributeId, savedAttribute, user1);

    final String newPassWord = UUID.randomUUID().toString();
    final AppDynamicsConfig newAppDynamicsConfig =
        SecretManagementTestHelper.getAppDynamicsConfig(accountId, newPassWord);

    String updatedAppId = UUID.randomUUID().toString();
    String updatedName = UUID.randomUUID().toString();
    final Map<String, Object> keyValuePairs = new HashMap<>();
    keyValuePairs.put("name", updatedName);
    keyValuePairs.put("appId", updatedAppId);
    keyValuePairs.put("value", newAppDynamicsConfig);

    wingsPersistence.updateFields(SettingAttribute.class, savedAttributeId, keyValuePairs);
    query = wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);

    encryptedData = query.get();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.KMS);
    assertThat(encryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsConfig.getUuid());
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();

    savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(savedAttribute.getName()).isEqualTo(updatedName);
    assertThat(savedAttribute.getAppId()).isEqualTo(updatedAppId);

    AppDynamicsConfig updatedAppdynamicsConfig = (AppDynamicsConfig) savedAttribute.getValue();
    encryptionService.decrypt(
        updatedAppdynamicsConfig, secretManager.getEncryptionDetails(updatedAppdynamicsConfig, null, null), false);
    assertThat(String.valueOf(updatedAppdynamicsConfig.getPassword())).isEqualTo(newPassWord);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void kmsEncryptionUpdateFieldSettingAttribute() throws IllegalAccessException {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(savedAttribute).isEqualTo(settingAttribute);
    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);

    String updatedAppId = UUID.randomUUID().toString();
    wingsPersistence.updateField(SettingAttribute.class, savedAttributeId, "appId", updatedAppId);

    SettingAttribute updatedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(updatedAttribute.getAppId()).isEqualTo(updatedAppId);
    savedAttribute.setAppId(updatedAppId);
    assertThat(updatedAttribute).isEqualTo(savedAttribute);
    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);

    Query<EncryptedData> query =
        wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);

    List<SecretChangeLog> changeLogs =
        secretManager.getChangeLogs(accountId, savedAttributeId, SettingVariableTypes.APP_DYNAMICS);
    assertThat(changeLogs).hasSize(1);
    SecretChangeLog secretChangeLog = changeLogs.get(0);

    assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user.getUuid());
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user.getEmail());
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(user.getName());
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");

    final String newPassWord = UUID.randomUUID().toString();
    final AppDynamicsConfig newAppDynamicsConfig =
        SecretManagementTestHelper.getAppDynamicsConfig(accountId, newPassWord);

    updatedAppId = UUID.randomUUID().toString();
    String updatedName = UUID.randomUUID().toString();
    final Map<String, Object> keyValuePairs = new HashMap<>();
    keyValuePairs.put("name", updatedName);
    keyValuePairs.put("appId", updatedAppId);
    keyValuePairs.put("value", newAppDynamicsConfig);

    User user1 = User.Builder.anUser().email(UUID.randomUUID().toString()).name(UUID.randomUUID().toString()).build();
    wingsPersistence.save(user1);
    UserThreadLocal.set(user1);
    wingsPersistence.updateFields(SettingAttribute.class, savedAttributeId, keyValuePairs);

    // test decryption
    savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    AppDynamicsConfig savedConfig = (AppDynamicsConfig) savedAttribute.getValue();
    assertThat(savedConfig.getPassword()).isNull();
    encryptionService.decrypt(
        savedConfig, secretManager.getEncryptionDetails(savedConfig, workflowExecutionId, appId), false);
    assertThat(String.valueOf(savedConfig.getPassword())).isEqualTo(newPassWord);

    query = wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);
    EncryptedData encryptedData = query.get();

    changeLogs = secretManager.getChangeLogs(accountId, savedAttributeId, SettingVariableTypes.APP_DYNAMICS);
    assertThat(changeLogs).hasSize(2);
    secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user1.getUuid());
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user1.getEmail());
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(user1.getName());
    assertThat(secretChangeLog.getDescription()).isEqualTo(" Changed secret");

    secretChangeLog = changeLogs.get(1);
    assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user.getUuid());
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user.getEmail());
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(user.getName());
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");

    assertThat(encryptedData.getCreatedBy().getUuid()).isEqualTo(user.getUuid());
    assertThat(encryptedData.getCreatedBy().getEmail()).isEqualTo(userEmail);
    assertThat(encryptedData.getCreatedBy().getName()).isEqualTo(userName);

    updatedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(updatedAttribute.getAppId()).isEqualTo(updatedAppId);
    assertThat(updatedAttribute.getName()).isEqualTo(updatedName);

    assertThat(updatedAttribute.getValue()).isEqualTo(newAppDynamicsConfig);
    newAppDynamicsConfig.setPassword(UUID.randomUUID().toString().toCharArray());

    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);

    User user2 = User.Builder.anUser().email(UUID.randomUUID().toString()).name(UUID.randomUUID().toString()).build();
    wingsPersistence.save(user2);
    UserThreadLocal.set(user2);
    wingsPersistence.updateFields(SettingAttribute.class, savedAttributeId, keyValuePairs);

    query = wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);

    changeLogs = secretManager.getChangeLogs(accountId, savedAttributeId, SettingVariableTypes.APP_DYNAMICS);
    assertThat(changeLogs).hasSize(3);
    secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user2.getUuid());
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user2.getEmail());
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(user2.getName());
    assertThat(secretChangeLog.getDescription()).isEqualTo(" Changed secret");

    secretChangeLog = changeLogs.get(1);
    assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user1.getUuid());
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user1.getEmail());
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(user1.getName());
    assertThat(secretChangeLog.getDescription()).isEqualTo(" Changed secret");

    secretChangeLog = changeLogs.get(2);
    assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user.getUuid());
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user.getEmail());
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(user.getName());
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveServiceVariableNoKMS() {
    String value = UUID.randomUUID().toString();
    final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(UUID.randomUUID().toString())
                                                .envId(UUID.randomUUID().toString())
                                                .entityType(EntityType.APPLICATION)
                                                .entityId(UUID.randomUUID().toString())
                                                .parentServiceVariableId(UUID.randomUUID().toString())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                                .expression(UUID.randomUUID().toString())
                                                .accountId(accountId)
                                                .name(UUID.randomUUID().toString())
                                                .value(value.toCharArray())
                                                .type(ServiceVariableType.TEXT)
                                                .build();

    String savedAttributeId = wingsPersistence.save(serviceVariable);
    ServiceVariable savedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(savedAttribute).isEqualTo(serviceVariable);
    assertThat(String.valueOf(savedAttribute.getValue())).isEqualTo(value);
    assertThat(wingsPersistence.createQuery(ServiceVariable.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(0);

    // update to encrypt the variable
    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    SecretText secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    Map<String, Object> keyValuePairs = new HashMap<>();
    keyValuePairs.put("type", ServiceVariableType.ENCRYPTED_TEXT);
    keyValuePairs.put("value", secretId.toCharArray());
    wingsPersistence.updateFields(ServiceVariable.class, serviceVariable.getUuid(), keyValuePairs);
    savedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);

    assertThat(savedAttribute.getType()).isEqualTo(ServiceVariableType.ENCRYPTED_TEXT);
    assertThat(savedAttribute.getValue()).isNull();
    encryptionService.decrypt(
        savedAttribute, secretManager.getEncryptionDetails(savedAttribute, workflowExecutionId, appId), false);
    assertThat(String.valueOf(savedAttribute.getValue())).isEqualTo(secretValue);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(1);

    keyValuePairs = new HashMap<>();
    keyValuePairs.put("type", ServiceVariableType.TEXT);
    keyValuePairs.put("value", "unencrypted".toCharArray());
    wingsPersistence.updateFields(ServiceVariable.class, serviceVariable.getUuid(), keyValuePairs);
    savedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);

    assertThat(savedAttribute.getType()).isEqualTo(ServiceVariableType.TEXT);
    assertThat(String.valueOf(savedAttribute.getValue())).isEqualTo("unencrypted");
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveServiceVariableNoEncryption() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String value = UUID.randomUUID().toString();
    final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(UUID.randomUUID().toString())
                                                .envId(UUID.randomUUID().toString())
                                                .entityType(EntityType.APPLICATION)
                                                .entityId(UUID.randomUUID().toString())
                                                .parentServiceVariableId(UUID.randomUUID().toString())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                                .expression(UUID.randomUUID().toString())
                                                .accountId(accountId)
                                                .name(UUID.randomUUID().toString())
                                                .value(value.toCharArray())
                                                .type(ServiceVariableType.TEXT)
                                                .build();

    String savedAttributeId = wingsPersistence.save(serviceVariable);
    ServiceVariable savedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(savedAttribute).isEqualTo(serviceVariable);
    assertThat(String.valueOf(savedAttribute.getValue())).isEqualTo(value);
    assertThat(wingsPersistence.createQuery(ServiceVariable.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms);

    // update to encrypt the variable
    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    SecretText secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    Map<String, Object> keyValuePairs = new HashMap<>();
    keyValuePairs.put("type", ServiceVariableType.ENCRYPTED_TEXT);
    keyValuePairs.put("value", secretId.toCharArray());
    wingsPersistence.updateFields(ServiceVariable.class, serviceVariable.getUuid(), keyValuePairs);
    savedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);

    assertThat(savedAttribute.getType()).isEqualTo(ServiceVariableType.ENCRYPTED_TEXT);
    assertThat(savedAttribute.getValue()).isNull();
    encryptionService.decrypt(
        savedAttribute, secretManager.getEncryptionDetails(savedAttribute, workflowExecutionId, appId), false);
    assertThat(String.valueOf(savedAttribute.getValue())).isEqualTo(secretValue);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);

    keyValuePairs = new HashMap<>();
    keyValuePairs.put("type", ServiceVariableType.TEXT);
    keyValuePairs.put("value", "unencrypted".toCharArray());
    wingsPersistence.updateFields(ServiceVariable.class, serviceVariable.getUuid(), keyValuePairs);
    savedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);

    assertThat(savedAttribute.getType()).isEqualTo(ServiceVariableType.TEXT);
    assertThat(new String(savedAttribute.getValue())).isEqualTo("unencrypted");
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);
    assertThat(isEmpty(wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                           .filter("type", SettingVariableTypes.SECRET_TEXT)
                           .asList()
                           .get(0)
                           .getParents()))
        .isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void getSecretMappedToAccount() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    // update to encrypt the variable
    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    SecretText secretText =
        SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).scopedToAccount(true).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    UserPermissionInfo userPermissionInfo = UsageRestrictionsServiceImplTestBase.getUserPermissionInfo(
        ImmutableList.of(appId), ImmutableList.of(envId), ImmutableSet.of(Action.UPDATE));

    User user = User.Builder.anUser().name(USER_NAME).uuid(USER_ID).build();
    user.setUserRequestContext(
        UserRequestContext.builder().appIds(ImmutableSet.of(appId)).userPermissionInfo(userPermissionInfo).build());
    UserThreadLocal.set(user);

    EncryptedData secretByName = secretManager.getSecretMappedToAccountByName(accountId, secretName);
    assertThat(secretByName).isNotNull();
    assertThat(secretByName.getName()).isEqualTo(secretName);

    secretByName = secretManager.getSecretMappedToAppByName(accountId, appId, envId, secretName);
    assertThat(secretByName).isNull();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testGetSecretMappedToAccountByName() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    // update to encrypt the variable
    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    SecretText secretText =
        SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).scopedToAccount(true).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    EncryptedData secretByName = secretManager.getSecretMappedToAccountByName(accountId, secretName);
    assertThat(secretByName).isNotNull();
    assertThat(secretByName.getName()).isEqualTo(secretName);
    assertThat(secretByName.getUuid()).isEqualTo(secretId);
    assertThat(secretByName.isScopedToAccount()).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void getSecretMappedToApp() {
    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();

    AppEnvRestrictionBuilder appEnvRestrictionBuilder =
        UsageRestrictions.AppEnvRestriction.builder()
            .appFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build())
            .envFilter(EnvFilter.builder().filterTypes(ImmutableSet.of(EnvFilter.FilterType.PROD)).build());

    SecretText secretText =
        SecretText.builder()
            .name(secretName)
            .kmsId(kmsId)
            .value(secretValue)
            .usageRestrictions(UsageRestrictions.builder()
                                   .appEnvRestrictions(ImmutableSet.of(appEnvRestrictionBuilder.build()))
                                   .build())
            .build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    UserPermissionInfo userPermissionInfo = UsageRestrictionsServiceImplTestBase.getUserPermissionInfo(
        ImmutableList.of(appId), ImmutableList.of(envId), ImmutableSet.of(Action.UPDATE, Action.CREATE, Action.READ));

    User user = User.Builder.anUser().name(USER_NAME).uuid(USER_ID).build();
    UsageRestrictions restrictionsFromUserPermissionsForRead =
        UsageRestrictions.builder().appEnvRestrictions(ImmutableSet.of(appEnvRestrictionBuilder.build())).build();

    Map<String, Set<String>> appEnvMapForRead = ImmutableMap.of(appId, ImmutableSet.of(envId));

    user.setUserRequestContext(
        UserRequestContext.builder()
            .appIds(ImmutableSet.of(appId))
            .userPermissionInfo(userPermissionInfo)
            .userRestrictionInfo(UserRestrictionInfo.builder()
                                     .appEnvMapForReadAction(appEnvMapForRead)
                                     .usageRestrictionsForReadAction(restrictionsFromUserPermissionsForRead)
                                     .build())
            .build());
    UserThreadLocal.set(user);

    EncryptedData secretByName = secretManager.getSecretMappedToAccountByName(accountId, secretName);
    assertThat(secretByName).isNull();

    secretByName = secretManager.getSecretMappedToAppByName(accountId, appId, envId, secretName);
    assertThat(secretByName).isNotNull();
    assertThat(secretByName.getName()).isEqualTo(secretName);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)

  public void kmsEncryptionSaveServiceVariable() throws IllegalAccessException {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    SecretText secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    // try with invalid secret id
    final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(generateUuid())
                                                .envId(generateUuid())
                                                .entityType(EntityType.SERVICE)
                                                .entityId(generateUuid())
                                                .parentServiceVariableId(generateUuid())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(generateUuid()))
                                                .expression(generateUuid())
                                                .accountId(accountId)
                                                .name("name")
                                                .value(generateUuid().toCharArray())
                                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                .build();

    try {
      serviceVariableResource.save(appId, serviceVariable);
      fail("saved invalid service variable");
    } catch (WingsException e) {
      // expected
    }

    serviceVariable.setValue(secretId.toCharArray());
    String savedAttributeId = serviceVariableResource.save(appId, serviceVariable).getResource().getUuid();
    ServiceVariable savedAttribute = serviceVariableResource.get(appId, savedAttributeId, false).getResource();
    assertThat(savedAttribute.getSecretTextName()).isNotNull();
    serviceVariable.setSecretTextName(savedAttribute.getSecretTextName());
    assertThat(savedAttribute).isEqualTo(serviceVariable);
    assertThat(wingsPersistence.createQuery(ServiceVariable.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);

    secretName = UUID.randomUUID().toString();
    secretValue = UUID.randomUUID().toString();
    secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    secretId = secretManager.saveSecretText(accountId, secretText, true);

    Map<String, Object> keyValuePairs = new HashMap<>();
    keyValuePairs.put("name", "newName");
    keyValuePairs.put("type", ServiceVariableType.ENCRYPTED_TEXT);
    keyValuePairs.put("value", secretId.toCharArray());
    wingsPersistence.updateFields(ServiceVariable.class, savedAttributeId, keyValuePairs);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 2);

    Query<EncryptedData> query =
        wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);

    // decrypt and verify
    ServiceVariable savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, workflowExecutionId, appId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue);

    List<SecretChangeLog> changeLogs =
        secretManager.getChangeLogs(accountId, savedAttributeId, SettingVariableTypes.SERVICE_VARIABLE);
    assertThat(changeLogs).hasSize(1);
    SecretChangeLog secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user.getUuid());
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user.getEmail());
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(user.getName());
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");

    // decrypt at manager side and test
    savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(savedVariable.getValue()).isNull();
    managerDecryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, workflowExecutionId, appId));
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue);
    assertThat(savedVariable.getEncryptedValue()).isNull();

    // update serviceVariable with invalid reference and fail
    serviceVariable.setValue(generateUuid().toCharArray());
    try {
      serviceVariableResource.update(appId, savedAttributeId, serviceVariable);
      fail("updated invalid service variable");
    } catch (WingsException e) {
      // expected
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void kmsEncryptionSaveServiceVariableTemplate() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    SecretText secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, false);

    String serviceId = wingsPersistence.save(Service.builder().name(UUID.randomUUID().toString()).build());
    String serviceTemplateId =
        wingsPersistence.save(ServiceTemplate.Builder.aServiceTemplate().withServiceId(serviceId).build());

    final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(UUID.randomUUID().toString())
                                                .envId(UUID.randomUUID().toString())
                                                .entityType(EntityType.SERVICE_TEMPLATE)
                                                .entityId(serviceTemplateId)
                                                .parentServiceVariableId(UUID.randomUUID().toString())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                                .expression(UUID.randomUUID().toString())
                                                .accountId(accountId)
                                                .name(UUID.randomUUID().toString())
                                                .value(secretId.toCharArray())
                                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                .build();

    String savedAttributeId = wingsPersistence.save(serviceVariable);
    ServiceVariable savedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(savedAttribute).isEqualTo(serviceVariable);
    assertThat(savedAttribute.getValue()).isNull();
    encryptionService.decrypt(
        savedAttribute, secretManager.getEncryptionDetails(savedAttribute, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedAttribute.getValue())).isEqualTo(secretValue);

    assertThat(wingsPersistence.createQuery(ServiceVariable.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)

  public void kmsEncryptionUpdateServiceVariable() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    SecretText secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(UUID.randomUUID().toString())
                                                .envId(UUID.randomUUID().toString())
                                                .entityType(EntityType.APPLICATION)
                                                .entityId(UUID.randomUUID().toString())
                                                .parentServiceVariableId(UUID.randomUUID().toString())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                                .expression(UUID.randomUUID().toString())
                                                .accountId(accountId)
                                                .name(UUID.randomUUID().toString())
                                                .value(secretId.toCharArray())
                                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                .build();

    String savedAttributeId = wingsPersistence.save(serviceVariable);
    ServiceVariable savedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(savedAttribute).isEqualTo(serviceVariable);
    assertThat(wingsPersistence.createQuery(ServiceVariable.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);

    String updatedEnvId = UUID.randomUUID().toString();
    wingsPersistence.updateField(ServiceVariable.class, savedAttributeId, "envId", updatedEnvId);

    ServiceVariable updatedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(updatedAttribute.getEnvId()).isEqualTo(updatedEnvId);
    savedAttribute.setEnvId(updatedEnvId);
    updatedAttribute.setLastUpdatedAt(0);
    savedAttribute.setLastUpdatedAt(0);
    assertThat(updatedAttribute).isEqualTo(savedAttribute);
    assertThat(wingsPersistence.createQuery(ServiceVariable.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);

    updatedEnvId = UUID.randomUUID().toString();
    String updatedName = UUID.randomUUID().toString();
    String updatedSecretName = UUID.randomUUID().toString();
    String updatedSecretValue = UUID.randomUUID().toString();
    SecretText updatedSecretText =
        SecretText.builder().name(updatedSecretName).kmsId(kmsId).value(updatedSecretValue).build();
    String updatedSecretId = secretManager.saveSecretText(accountId, updatedSecretText, true);

    final Map<String, Object> keyValuePairs = new HashMap<>();
    keyValuePairs.put("name", updatedName);
    keyValuePairs.put("envId", updatedEnvId);
    keyValuePairs.put("type", ServiceVariableType.ENCRYPTED_TEXT);
    keyValuePairs.put("value", updatedSecretId.toCharArray());

    wingsPersistence.updateFields(ServiceVariable.class, savedAttributeId, keyValuePairs);
    updatedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(updatedAttribute.getEnvId()).isEqualTo(updatedEnvId);
    assertThat(updatedAttribute.getName()).isEqualTo(updatedName);
    assertThat(updatedAttribute.getValue()).isNull();
    encryptionService.decrypt(
        updatedAttribute, secretManager.getEncryptionDetails(updatedAttribute, workflowExecutionId, appId), false);
    assertThat(String.valueOf(updatedAttribute.getValue())).isEqualTo(updatedSecretValue);
    assertThat(wingsPersistence.createQuery(ServiceVariable.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 2);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void kmsEncryptionDeleteSettingAttribute() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    int numOfSettingAttributes = 5;
    List<SettingAttribute> settingAttributes =
        SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
    wingsPersistence.save(settingAttributes);

    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(numOfSettingAttributes);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count())
        .isEqualTo(numOfEncryptedValsForKms + numOfSettingAttributes);
    for (int i = 0; i < numOfSettingAttributes; i++) {
      wingsPersistence.delete(settingAttributes.get(i));
      assertThat(wingsPersistence.createQuery(SettingAttribute.class).count())
          .isEqualTo(numOfSettingAttributes - (i + 1));
      assertThat(wingsPersistence.createQuery(EncryptedData.class).count())
          .isEqualTo(numOfEncryptedValsForKms + numOfSettingAttributes);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void kmsEncryptionDeleteSettingAttributeQueryUuid() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    int numOfSettingAttributes = 5;
    List<SettingAttribute> settingAttributes =
        SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
    for (SettingAttribute settingAttribute : settingAttributes) {
      wingsPersistence.save(settingAttribute);
    }
    secretManagementTestHelper.validateSettingAttributes(
        settingAttributes, numOfEncryptedValsForKms + numOfSettingAttributes);

    settingAttributes = SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
    wingsPersistence.save(settingAttributes);
    secretManagementTestHelper.validateSettingAttributes(
        settingAttributes, numOfEncryptedValsForKms + 2 * numOfSettingAttributes);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void kmsEncryptionDeleteSettingAttributeQuery() {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    int numOfSettingAttributes = 5;
    List<SettingAttribute> settingAttributes =
        SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
    wingsPersistence.save(settingAttributes);

    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(numOfSettingAttributes);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count())
        .isEqualTo(numOfEncryptedValsForKms + numOfSettingAttributes);

    Set<String> idsToDelete = new HashSet<>();
    idsToDelete.add(settingAttributes.get(0).getUuid());
    idsToDelete.add(settingAttributes.get(1).getUuid());
    Query<SettingAttribute> query = wingsPersistence.createQuery(SettingAttribute.class)
                                        .field(Mapper.ID_KEY)
                                        .hasAnyOf(idsToDelete)
                                        .filter(SettingAttributeKeys.accountId, accountId);
    for (int i = 0; i < numOfSettingAttributes; i++) {
      wingsPersistence.delete(query);
      assertThat(wingsPersistence.createQuery(SettingAttribute.class).count())
          .isEqualTo(numOfSettingAttributes - idsToDelete.size());
      assertThat(wingsPersistence.createQuery(EncryptedData.class).count())
          .isEqualTo(numOfEncryptedValsForKms + numOfSettingAttributes);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void kmsEncryptionSaveGlobalConfig() {
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveGlobalKmsConfig(GLOBAL_ACCOUNT_ID, kryoSerializer.clone(kmsConfig));
    assertThat(wingsPersistence.createQuery(KmsConfig.class).count()).isEqualTo(1);

    KmsConfig savedKmsConfig = (KmsConfig) secretManagerConfigService.getDefaultSecretManager(accountId);
    assertThat(savedKmsConfig).isNotNull();

    assertThat(savedKmsConfig.getAccountId()).isEqualTo(GLOBAL_ACCOUNT_ID);
    assertThat(savedKmsConfig.getAccessKey()).isEqualTo(kmsConfig.getAccessKey());
    assertThat(savedKmsConfig.getSecretKey()).isEqualTo(kmsConfig.getSecretKey());
    assertThat(savedKmsConfig.getKmsArn()).isEqualTo(kmsConfig.getKmsArn());

    KmsConfig encryptedKms = wingsPersistence.getDatastore(KmsConfig.class).createQuery(KmsConfig.class).get();

    assertThat(encryptedKms.getAccessKey()).isNotEqualTo(savedKmsConfig.getAccessKey());
    assertThat(encryptedKms.getSecretKey()).isNotEqualTo(savedKmsConfig.getSecretKey());
    assertThat(encryptedKms.getKmsArn()).isNotEqualTo(savedKmsConfig.getKmsArn());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void listEncryptedValues() {
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setAccountId(accountId);
    kmsResource.saveKmsConfig(accountId, kryoSerializer.clone(kmsConfig));

    int numOfSettingAttributes = 5;
    List<SettingAttribute> settingAttributes =
        SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
    wingsPersistence.save(settingAttributes);

    Collection<SettingAttribute> encryptedValues =
        secretManagementResource.listEncryptedSettingAttributes(accountId, null).getResource();
    validateContainEncryptedValues(settingAttributes, encryptedValues);
    for (SettingAttribute encryptedValue : encryptedValues) {
      assertThat(encryptedValue.getEncryptionType()).isEqualTo(EncryptionType.KMS);
    }

    // Retrieving the setting attributes of category CONNECTOR.
    encryptedValues =
        secretManagementResource.listEncryptedSettingAttributes(accountId, SettingCategory.CONNECTOR.name())
            .getResource();
    validateContainEncryptedValues(settingAttributes, encryptedValues);
    for (SettingAttribute encryptedValue : encryptedValues) {
      assertThat(encryptedValue.getEncryptionType()).isEqualTo(EncryptionType.KMS);
    }
  }

  private void validateContainEncryptedValues(
      List<SettingAttribute> settingAttributes, Collection<SettingAttribute> encryptedValues) {
    assertThat(encryptedValues).hasSize(settingAttributes.size());
    Map<String, SettingAttribute> settingAttributeMap = new HashMap<>();
    for (SettingAttribute settingAttribute : settingAttributes) {
      settingAttributeMap.put(settingAttribute.getName(), settingAttribute);
    }

    for (SettingAttribute settingAttribute : encryptedValues) {
      assertThat(settingAttributeMap.containsKey(settingAttribute.getName())).isTrue();
      AppDynamicsConfig appDynamicsConfig = (AppDynamicsConfig) settingAttribute.getValue();
      assertThat(appDynamicsConfig.getPassword()).isEqualTo(SecretManager.ENCRYPTED_FIELD_MASK.toCharArray());
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void listKmsConfigMultiple() {
    KmsConfig kmsConfig1 = secretManagementTestHelper.getKmsConfig();
    kmsConfig1.setDefault(true);
    kmsConfig1.setName(UUID.randomUUID().toString());
    kmsResource.saveKmsConfig(accountId, kryoSerializer.clone(kmsConfig1));

    KmsConfig kmsConfig2 = secretManagementTestHelper.getKmsConfig();
    kmsConfig2.setDefault(false);
    kmsConfig2.setName(UUID.randomUUID().toString());
    String kms2Id = kmsResource.saveKmsConfig(accountId, kryoSerializer.clone(kmsConfig2)).getResource();

    Collection<SecretManagerConfig> kmsConfigs =
        secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(2);

    int defaultConfig = 0;
    int nonDefaultConfig = 0;

    for (SecretManagerConfig config : kmsConfigs) {
      KmsConfig actualConfig = (KmsConfig) config;
      if (actualConfig.isDefault()) {
        defaultConfig++;
        assertThat(actualConfig.getName()).isEqualTo(kmsConfig1.getName());
        assertThat(actualConfig.getAccessKey()).isEqualTo(kmsConfig1.getAccessKey());
        assertThat(actualConfig.getKmsArn()).isEqualTo(SECRET_MASK);
        assertThat(actualConfig.getSecretKey()).isEqualTo(SECRET_MASK);
        assertThat(isEmpty(actualConfig.getUuid())).isFalse();
        assertThat(actualConfig.getAccountId()).isEqualTo(accountId);
      } else {
        nonDefaultConfig++;
        assertThat(actualConfig.getName()).isEqualTo(kmsConfig2.getName());
        assertThat(actualConfig.getAccessKey()).isEqualTo(kmsConfig2.getAccessKey());
        assertThat(actualConfig.getKmsArn()).isEqualTo(SECRET_MASK);
        assertThat(actualConfig.getSecretKey()).isEqualTo(SECRET_MASK);
        assertThat(isEmpty(actualConfig.getUuid())).isFalse();
        assertThat(actualConfig.getAccountId()).isEqualTo(accountId);
      }
    }

    assertThat(defaultConfig).isEqualTo(1);
    assertThat(nonDefaultConfig).isEqualTo(1);

    // Update to set the non-default to default secret manage
    kmsConfig2.setUuid(kms2Id);
    kmsConfig2.setName(UUID.randomUUID().toString());
    kmsConfig2.setDefault(true);

    kmsResource.saveKmsConfig(accountId, kryoSerializer.clone(kmsConfig2));

    kmsConfigs = secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(2);

    defaultConfig = 0;
    nonDefaultConfig = 0;
    for (SecretManagerConfig config : kmsConfigs) {
      KmsConfig actualConfig = (KmsConfig) config;
      if (actualConfig.isDefault()) {
        defaultConfig++;
        assertThat(actualConfig.getName()).isEqualTo(kmsConfig2.getName());
        assertThat(actualConfig.getAccessKey()).isEqualTo(kmsConfig2.getAccessKey());
        assertThat(actualConfig.getKmsArn()).isEqualTo(SECRET_MASK);
        assertThat(actualConfig.getSecretKey()).isEqualTo(SECRET_MASK);
        assertThat(isEmpty(actualConfig.getUuid())).isFalse();
        assertThat(actualConfig.getAccountId()).isEqualTo(accountId);
      } else {
        nonDefaultConfig++;
        assertThat(actualConfig.getName()).isEqualTo(kmsConfig1.getName());
        assertThat(actualConfig.getAccessKey()).isEqualTo(kmsConfig1.getAccessKey());
        assertThat(actualConfig.getKmsArn()).isEqualTo(SECRET_MASK);
        assertThat(actualConfig.getSecretKey()).isEqualTo(SECRET_MASK);
        assertThat(isEmpty(actualConfig.getUuid())).isFalse();
        assertThat(actualConfig.getAccountId()).isEqualTo(accountId);
      }
    }

    assertThat(defaultConfig).isEqualTo(1);
    assertThat(nonDefaultConfig).isEqualTo(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void resetDefaultShouldNotAcrossAccount() {
    int numOfKmsConfigs = 3;
    Set<String> kmsIds = new HashSet<>();
    for (int i = 0; i < numOfKmsConfigs; i++) {
      kmsIds.add(saveKmsConfigWithAccount(getAccount(AccountType.PAID)));
    }

    for (String kmsId : kmsIds) {
      KmsConfig kmsConfig = wingsPersistence.get(KmsConfig.class, kmsId);
      // Each KMS config should be default of its own account and not affecting each other.
      assertThat(kmsConfig.isDefault()).isTrue();
    }
  }

  private String saveKmsConfigWithAccount(Account account) {
    String accountId = wingsPersistence.save(account);
    when(accountService.get(accountId)).thenReturn(account);
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);

    User user = User.Builder.anUser()
                    .accounts(Collections.singletonList(account))
                    .email(UUID.randomUUID().toString())
                    .name(UUID.randomUUID().toString())
                    .build();
    wingsPersistence.save(user);

    UserThreadLocal.set(user);
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    return kmsService.saveKmsConfig(accountId, kmsConfig);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void deleteGlobalKmsNotAllowed() {
    KmsConfig globalKmsConfig = secretManagementTestHelper.getKmsConfig();
    globalKmsConfig.setName("Global config");
    globalKmsConfig.setDefault(true);
    String kmsId = kmsService.saveGlobalKmsConfig(accountId, globalKmsConfig);

    try {
      kmsService.deleteKmsConfig(accountId, kmsId);
      fail("Exception expected when deleting global KMS secret manager");
    } catch (SecretManagementException e) {
      // Global kMS operation exception expected.
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Repeat(times = 5, successes = 1)
  @Category(UnitTests.class)
  public void listKmsGlobalDefault() {
    KmsConfig globalKmsConfig = secretManagementTestHelper.getKmsConfig();
    globalKmsConfig.setName("Global config");

    globalKmsConfig.setDefault(false);
    kmsResource.saveGlobalKmsConfig(accountId, globalKmsConfig);

    Collection<SecretManagerConfig> kmsConfigs =
        secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(1);
    assertThat(kmsConfigs.iterator().next().isDefault()).isTrue();

    int numOfKms = 10;
    for (int i = 1; i <= numOfKms; i++) {
      KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
      kmsConfig.setDefault(true);
      kmsConfig.setName("kms" + i);
      kmsResource.saveKmsConfig(accountId, kmsConfig);
    }

    kmsConfigs = secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(numOfKms + 1);

    int kmsNum = numOfKms;
    for (SecretManagerConfig kmsConfig : kmsConfigs) {
      if (kmsConfig.getAccountId().equals(GLOBAL_ACCOUNT_ID)) {
        assertThat(kmsConfig.isDefault()).isFalse();
        assertThat(kmsConfig.getName()).isEqualTo("Global config");
      } else {
        assertThat(kmsConfig.getName()).isEqualTo("kms" + kmsNum);
      }
      if (kmsNum == numOfKms) {
        assertThat(kmsConfig.isDefault()).isTrue();
      } else {
        assertThat(kmsConfig.isDefault()).isFalse();
      }
      kmsNum--;
    }

    // delete the default and global should become default
    kmsResource.deleteKmsConfig(accountId, kmsConfigs.iterator().next().getUuid());
    kmsConfigs = secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(numOfKms);

    int defaultSet = 0;
    kmsNum = numOfKms - 1;
    for (SecretManagerConfig kmsConfig : kmsConfigs) {
      if (kmsConfig.getAccountId().equals(GLOBAL_ACCOUNT_ID)) {
        assertThat(kmsConfig.isDefault()).isTrue();
        assertThat(kmsConfig.getName()).isEqualTo("Global config");
        defaultSet++;
      } else {
        assertThat(kmsConfig.isDefault()).isFalse();
        assertThat(kmsConfig.getName()).isEqualTo("kms" + kmsNum);
      }
      kmsNum--;
    }

    assertThat(defaultSet).isEqualTo(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void listKmsConfigOrder() {
    int numOfKms = 10;
    for (int i = 1; i <= numOfKms; i++) {
      KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
      kmsConfig.setDefault(true);
      kmsConfig.setName("kms" + i);
      kmsResource.saveKmsConfig(accountId, kmsConfig);
    }

    Collection<SecretManagerConfig> kmsConfigs =
        secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(numOfKms);

    int kmsNum = numOfKms;
    for (SecretManagerConfig kmsConfig : kmsConfigs) {
      if (kmsNum == numOfKms) {
        assertThat(kmsConfig.isDefault()).isTrue();
      } else {
        assertThat(kmsConfig.isDefault()).isFalse();
      }
      assertThat(kmsConfig.getName()).isEqualTo("kms" + kmsNum);
      kmsNum--;
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void listKmsConfigHasDefault() {
    KmsConfig globalKmsConfig = secretManagementTestHelper.getKmsConfig();
    globalKmsConfig.setDefault(false);
    globalKmsConfig.setName("global-kms-config");
    kmsResource.saveGlobalKmsConfig(accountId, kryoSerializer.clone(globalKmsConfig));

    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kryoSerializer.clone(kmsConfig));

    Collection<SecretManagerConfig> kmsConfigs =
        secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(2);

    int defaultConfig = 0;
    int accountConfig = 0;

    for (SecretManagerConfig secretManagerConfig : kmsConfigs) {
      KmsConfig actualConfig = (KmsConfig) secretManagerConfig;
      if (actualConfig.isDefault()) {
        accountConfig++;
        assertThat(actualConfig.getName()).isEqualTo(kmsConfig.getName());
        assertThat(actualConfig.getAccessKey()).isEqualTo(kmsConfig.getAccessKey());
        assertThat(actualConfig.getKmsArn()).isEqualTo(SECRET_MASK);
        assertThat(actualConfig.getSecretKey()).isEqualTo(SECRET_MASK);
        assertThat(isEmpty(actualConfig.getUuid())).isFalse();
      } else {
        defaultConfig++;
        assertThat(actualConfig.getName()).isEqualTo(globalKmsConfig.getName());
        assertThat(actualConfig.getAccessKey()).isEqualTo(globalKmsConfig.getAccessKey());
        assertThat(actualConfig.getKmsArn()).isEqualTo(SECRET_MASK);
        assertThat(actualConfig.getSecretKey()).isEqualTo(SECRET_MASK);
        assertThat(isEmpty(actualConfig.getUuid())).isFalse();
        assertThat(actualConfig.getAccountId()).isEqualTo(GLOBAL_ACCOUNT_ID);
      }
    }

    assertThat(defaultConfig).isEqualTo(1);
    assertThat(accountConfig).isEqualTo(1);

    // test with unmasked
    kmsConfigs = secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, false);
    assertThat(kmsConfigs).hasSize(2);

    defaultConfig = 0;
    accountConfig = 0;

    for (SecretManagerConfig secretManagerConfig : kmsConfigs) {
      KmsConfig actualConfig = (KmsConfig) secretManagerConfig;
      if (actualConfig.isDefault()) {
        accountConfig++;
        assertThat(actualConfig.getName()).isEqualTo(kmsConfig.getName());
        assertThat(actualConfig.getAccessKey()).isEqualTo(kmsConfig.getAccessKey());
        assertThat(actualConfig.getKmsArn()).isEqualTo(kmsConfig.getKmsArn());
        assertThat(actualConfig.getSecretKey()).isEqualTo(kmsConfig.getSecretKey());
        assertThat(isEmpty(actualConfig.getUuid())).isFalse();
      } else {
        defaultConfig++;
        assertThat(actualConfig.getName()).isEqualTo(globalKmsConfig.getName());
        assertThat(actualConfig.getAccessKey()).isEqualTo(globalKmsConfig.getAccessKey());
        assertThat(actualConfig.getKmsArn()).isEqualTo(globalKmsConfig.getKmsArn());
        assertThat(actualConfig.getSecretKey()).isEqualTo(globalKmsConfig.getSecretKey());
        assertThat(isEmpty(actualConfig.getUuid())).isFalse();
        assertThat(actualConfig.getAccountId()).isEqualTo(GLOBAL_ACCOUNT_ID);
      }
    }

    assertThat(defaultConfig).isEqualTo(1);
    assertThat(accountConfig).isEqualTo(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void listKmsConfig() {
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kryoSerializer.clone(kmsConfig));

    Collection<SecretManagerConfig> kmsConfigs =
        secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(1);
    KmsConfig actualConfig = (KmsConfig) kmsConfigs.iterator().next();
    assertThat(actualConfig.getName()).isEqualTo(kmsConfig.getName());
    assertThat(actualConfig.getAccessKey()).isEqualTo(kmsConfig.getAccessKey());
    assertThat(actualConfig.getKmsArn()).isEqualTo(SECRET_MASK);
    assertThat(actualConfig.getSecretKey()).isEqualTo(SECRET_MASK);
    assertThat(isEmpty(actualConfig.getUuid())).isFalse();
    assertThat(actualConfig.isDefault()).isTrue();

    // add another kms
    String name = UUID.randomUUID().toString();
    kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setDefault(true);
    kmsConfig.setName(name);
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    kmsConfigs = secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(2);

    int defaultPresent = 0;
    for (SecretManagerConfig config : kmsConfigs) {
      if (config.getName().equals(name)) {
        defaultPresent++;
        assertThat(config.isDefault()).isTrue();
      } else {
        assertThat(config.isDefault()).isFalse();
      }
    }

    assertThat(defaultPresent).isEqualTo(1);

    name = UUID.randomUUID().toString();
    kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setDefault(true);
    kmsConfig.setName(name);
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    kmsConfigs = secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.KMS, true);
    assertThat(kmsConfigs).hasSize(3);

    defaultPresent = 0;
    for (SecretManagerConfig config : kmsConfigs) {
      if (config.getName().equals(name)) {
        defaultPresent++;
        assertThat(config.isDefault()).isTrue();
      } else {
        assertThat(config.isDefault()).isFalse();
      }
    }
    assertThat(defaultPresent).isEqualTo(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void transitionKms() throws InterruptedException, IllegalAccessException {
    Thread listenerThread = startTransitionListener();
    try {
      KmsConfig fromConfig = secretManagementTestHelper.getKmsConfig();
      kmsResource.saveKmsConfig(accountId, fromConfig);

      int numOfSettingAttributes = 5;
      Map<String, SettingAttribute> encryptedEntities = new HashMap<>();
      for (int i = 0; i < numOfSettingAttributes; i++) {
        String password = UUID.randomUUID().toString();
        final AppDynamicsConfig appDynamicsConfig =
            SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
        SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

        wingsPersistence.save(settingAttribute);
        appDynamicsConfig.setPassword(null);
        encryptedEntities.put(settingAttribute.getUuid(), settingAttribute);
      }

      Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority);
      List<EncryptedData> encryptedData = new ArrayList<>();
      assertThat(query.count()).isEqualTo(numOfEncryptedValsForKms + numOfSettingAttributes);
      for (EncryptedData data : query.asList()) {
        if (data.getKmsId().equals(accountId)) {
          continue;
        }
        encryptedData.add(data);
        assertThat(data.getKmsId()).isEqualTo(fromConfig.getUuid());
        assertThat(data.getAccountId()).isEqualTo(accountId);
      }

      assertThat(encryptedData).hasSize(numOfSettingAttributes);

      KmsConfig toKmsConfig = secretManagementTestHelper.getKmsConfig();
      toKmsConfig.setKmsArn("arn:aws:kms:us-east-1:830767422336:key/e1aebd89-277b-4ec7-a4e9-9a238f8b2594");
      kmsResource.saveKmsConfig(accountId, toKmsConfig);

      secretManagementResource.transitionSecrets(
          accountId, EncryptionType.KMS, fromConfig.getUuid(), EncryptionType.KMS, toKmsConfig.getUuid());
      Thread.sleep(TimeUnit.SECONDS.toMillis(10));
      query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority);
      // 2 kms configs have been saved so far
      assertThat(query.count()).isEqualTo(2 * numOfEncryptedValsForKms + numOfSettingAttributes);
      encryptedData = new ArrayList<>();
      for (EncryptedData data : query.asList()) {
        if (data.getKmsId().equals(accountId)) {
          continue;
        }
        encryptedData.add(data);
        assertThat(data.getKmsId()).isEqualTo(toKmsConfig.getUuid());
        assertThat(data.getAccountId()).isEqualTo(accountId);
      }
      assertThat(encryptedData).hasSize(numOfSettingAttributes);

      // read the values and compare
      PageResponse<SettingAttribute> attributeQuery = wingsPersistence.query(
          SettingAttribute.class, aPageRequest().addFilter("accountId", SearchFilter.Operator.EQ, accountId).build());
      assertThat(attributeQuery).hasSize(numOfSettingAttributes);
      for (SettingAttribute settingAttribute : attributeQuery) {
        assertThat(settingAttribute).isEqualTo(encryptedEntities.get(settingAttribute.getUuid()));
      }
    } finally {
      stopTransitionListener(listenerThread);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void transitionAndDeleteKms() throws InterruptedException, IllegalAccessException {
    Thread listenerThread = startTransitionListener();
    try {
      KmsConfig fromConfig = secretManagementTestHelper.getKmsConfig();
      kmsResource.saveKmsConfig(accountId, fromConfig);

      int numOfSettingAttributes = 5;
      Map<String, SettingAttribute> encryptedEntities = new HashMap<>();
      for (int i = 0; i < numOfSettingAttributes; i++) {
        String password = UUID.randomUUID().toString();
        final AppDynamicsConfig appDynamicsConfig =
            SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
        SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

        wingsPersistence.save(settingAttribute);
        appDynamicsConfig.setPassword(password.toCharArray());
        encryptedEntities.put(settingAttribute.getUuid(), settingAttribute);
      }

      Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class);
      assertThat(query.count()).isEqualTo(numOfEncryptedValsForKms + numOfSettingAttributes);

      KmsConfig toKmsConfig = secretManagementTestHelper.getKmsConfig();
      toKmsConfig.setKmsArn("arn:aws:kms:us-east-1:830767422336:key/e1aebd89-277b-4ec7-a4e9-9a238f8b2594");
      kmsResource.saveKmsConfig(accountId, toKmsConfig);
      assertThat(wingsPersistence.createQuery(KmsConfig.class).count()).isEqualTo(2);

      try {
        kmsResource.deleteKmsConfig(accountId, fromConfig.getUuid());
        fail("Was able to delete kms which has reference in encrypted secrets");
      } catch (WingsException e) {
        // expected
      }

      secretManagementResource.transitionSecrets(
          accountId, EncryptionType.KMS, fromConfig.getUuid(), EncryptionType.KMS, toKmsConfig.getUuid());
      Thread.sleep(TimeUnit.SECONDS.toMillis(10));
      kmsResource.deleteKmsConfig(accountId, fromConfig.getUuid());
      assertThat(wingsPersistence.createQuery(KmsConfig.class).count()).isEqualTo(1);

      query = wingsPersistence.createQuery(EncryptedData.class);
      assertThat(query.count()).isEqualTo(numOfEncryptedValsForKms + numOfSettingAttributes);
    } finally {
      stopTransitionListener(listenerThread);
    }
  }

  @Test
  @Owner(developers = ANKIT, intermittent = true)
  @Category(UnitTests.class)

  public void transitionKmsForConfigFile() throws IOException, InterruptedException, IllegalAccessException {
    Thread listenerThread = startTransitionListener();
    try {
      final long seed = System.currentTimeMillis();
      log.info("seed: " + seed);
      Random r = new Random(seed);
      Account randomAccount = getAccount(AccountType.PAID);
      String randomAccountId = randomAccount.getUuid();
      when(accountService.get(randomAccountId)).thenReturn(randomAccount);
      final String randomAppId = UUID.randomUUID().toString();
      KmsConfig fromConfig = secretManagementTestHelper.getKmsConfig();

      when(secretsManagementFeature.isAvailableForAccount(randomAccountId)).thenReturn(true);

      kmsResource.saveKmsConfig(randomAccountId, fromConfig);

      Service service = Service.builder().name(UUID.randomUUID().toString()).appId(randomAppId).build();
      wingsPersistence.save(service);

      String secretName = UUID.randomUUID().toString();
      File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
      SecretFile secretFile = SecretFile.builder()
                                  .name(secretName)
                                  .kmsId(kmsId)
                                  .inheritScopesFromSM(true)
                                  .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToSave)))
                                  .build();
      String secretFileId = secretManager.saveSecretFile(randomAccountId, secretFile);
      String encryptedUuid = wingsPersistence.createQuery(EncryptedData.class)
                                 .filter(EncryptedDataKeys.accountId, randomAccountId)
                                 .filter(EncryptedDataKeys.type, CONFIG_FILE)
                                 .get()
                                 .getUuid();

      ConfigFile configFile = ConfigFile.builder()
                                  .templateId(UUID.randomUUID().toString())
                                  .envId(UUID.randomUUID().toString())
                                  .entityType(EntityType.SERVICE)
                                  .entityId(service.getUuid())
                                  .description(UUID.randomUUID().toString())
                                  .parentConfigFileId(UUID.randomUUID().toString())
                                  .relativeFilePath(UUID.randomUUID().toString())
                                  .targetToAllEnv(r.nextBoolean())
                                  .defaultVersion(r.nextInt())
                                  .envIdVersionMapString(UUID.randomUUID().toString())
                                  .setAsDefault(r.nextBoolean())
                                  .notes(UUID.randomUUID().toString())
                                  .overridePath(UUID.randomUUID().toString())
                                  .configOverrideType(ConfigOverrideType.CUSTOM)
                                  .configOverrideExpression(UUID.randomUUID().toString())
                                  .encryptedFileId(secretFileId)
                                  .encrypted(true)
                                  .build();

      configFile.setAccountId(randomAccountId);
      configFile.setName(UUID.randomUUID().toString());
      configFile.setFileName(UUID.randomUUID().toString());
      configFile.setAppId(randomAppId);

      String configFileId = configService.save(configFile, null);
      File download = configService.download(randomAppId, configFileId);
      assertThat(FileUtils.readFileToString(fileToSave, Charset.defaultCharset()))
          .isEqualTo(FileUtils.readFileToString(download, Charset.defaultCharset()));

      EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, encryptedUuid);
      assertThat(encryptedData).isNotNull();
      assertThat(encryptedData.getKmsId()).isEqualTo(fromConfig.getUuid());

      KmsConfig toKmsConfig = secretManagementTestHelper.getKmsConfig();
      toKmsConfig.setKmsArn("arn:aws:kms:us-east-1:830767422336:key/e1aebd89-277b-4ec7-a4e9-9a238f8b2594");
      kmsResource.saveKmsConfig(randomAccountId, toKmsConfig);

      secretManagementResource.transitionSecrets(
          randomAccountId, EncryptionType.KMS, fromConfig.getUuid(), EncryptionType.KMS, toKmsConfig.getUuid());
      Thread.sleep(TimeUnit.SECONDS.toMillis(10));

      download = configService.download(randomAppId, configFileId);
      assertThat(FileUtils.readFileToString(fileToSave, Charset.defaultCharset()))
          .isEqualTo(FileUtils.readFileToString(download, Charset.defaultCharset()));
      encryptedData = wingsPersistence.get(EncryptedData.class, encryptedUuid);
      assertThat(encryptedData).isNotNull();
      assertThat(encryptedData.getKmsId()).isEqualTo(toKmsConfig.getUuid());
    } finally {
      stopTransitionListener(listenerThread);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveAwsConfig() {
    KmsConfig fromConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, fromConfig);

    int numOfSettingAttributes = 5;
    Map<String, SettingAttribute> encryptedEntities = new HashMap<>();
    for (int i = 0; i < numOfSettingAttributes; i++) {
      final AwsConfig awsConfig = AwsConfig.builder()
                                      .accountId(accountId)
                                      .accessKey(UUID.randomUUID().toString().toCharArray())
                                      .secretKey(UUID.randomUUID().toString().toCharArray())
                                      .build();

      SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                              .withAccountId(accountId)
                                              .withValue(awsConfig)
                                              .withAppId(UUID.randomUUID().toString())
                                              .withCategory(SettingCategory.CLOUD_PROVIDER)
                                              .withEnvId(UUID.randomUUID().toString())
                                              .withName(UUID.randomUUID().toString())
                                              .build();

      wingsPersistence.save(settingAttribute);
      encryptedEntities.put(settingAttribute.getUuid(), settingAttribute);
    }

    Collection<SettingAttribute> uuidAwares =
        secretManagementResource.listEncryptedSettingAttributes(accountId, null).getResource();
    assertThat(uuidAwares).hasSize(encryptedEntities.size());
    for (SettingAttribute encryptedValue : uuidAwares) {
      assertThat(encryptedValue.getEncryptionType()).isEqualTo(EncryptionType.KMS);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)

  public void saveUpdateConfigFileNoKms() throws IOException, IllegalAccessException {
    final long seed = System.currentTimeMillis();
    log.info("seed: " + seed);
    Random r = new Random(seed);
    final String renameAccountId = UUID.randomUUID().toString();
    final String renameAppId = UUID.randomUUID().toString();

    Service service = Service.builder().name(UUID.randomUUID().toString()).appId(renameAppId).build();
    wingsPersistence.save(service);

    ConfigFile configFile = ConfigFile.builder()
                                .templateId(UUID.randomUUID().toString())
                                .envId(UUID.randomUUID().toString())
                                .entityType(EntityType.SERVICE)
                                .entityId(service.getUuid())
                                .description(UUID.randomUUID().toString())
                                .parentConfigFileId(UUID.randomUUID().toString())
                                .relativeFilePath(UUID.randomUUID().toString())
                                .targetToAllEnv(r.nextBoolean())
                                .defaultVersion(r.nextInt())
                                .envIdVersionMapString(UUID.randomUUID().toString())
                                .setAsDefault(r.nextBoolean())
                                .notes(UUID.randomUUID().toString())
                                .overridePath(UUID.randomUUID().toString())
                                .configOverrideType(ConfigOverrideType.CUSTOM)
                                .configOverrideExpression(UUID.randomUUID().toString())
                                .encrypted(false)
                                .build();

    configFile.setAccountId(renameAccountId);
    configFile.setName(UUID.randomUUID().toString());
    configFile.setFileName(UUID.randomUUID().toString());
    configFile.setAppId(renameAppId);

    File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");

    String configFileId = configService.save(configFile, new BoundedInputStream(new FileInputStream(fileToSave)));
    File download = configService.download(renameAppId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToSave, Charset.defaultCharset()));
    assertThat(wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId).count())
        .isEqualTo(0);
    ConfigFile savedConfigFile = configService.get(renameAppId, configFileId);
    assertThat(savedConfigFile.isEncrypted()).isFalse();
    assertThat(isEmpty(savedConfigFile.getEncryptedFileId())).isTrue();
    assertThat(wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId).count())
        .isEqualTo(0);

    // now make the same file encrypted
    String secretName = UUID.randomUUID().toString();
    File fileToUpdate = new File("400-rest/src/test/resources/encryption/file_to_update.txt");
    SecretFile secretFile = SecretFile.builder()
                                .name(secretName)
                                .kmsId(kmsId)
                                .inheritScopesFromSM(true)
                                .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToUpdate)))
                                .build();
    String secretFileId = secretManager.saveSecretFile(renameAccountId, secretFile);
    configFile.setEncrypted(true);
    configFile.setEncryptedFileId(secretFileId);
    configService.update(configFile, null);
    download = configService.download(renameAppId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToUpdate, Charset.defaultCharset()));
    savedConfigFile = configService.get(renameAppId, configFileId);
    assertThat(savedConfigFile.isEncrypted()).isTrue();
    assertThat(isEmpty(savedConfigFile.getEncryptedFileId())).isFalse();

    assertThat(
        wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, renameAccountId).count())
        .isEqualTo(1);
    EncryptedData encryptedData =
        wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, renameAccountId).get();
    assertThat(encryptedData.getAccountId()).isEqualTo(renameAccountId);
    assertThat(encryptedData.getParents()).hasSize(1);
    assertThat(encryptedData.containsParent(configFileId, CONFIG_FILE)).isTrue();
    assertThat(encryptedData.getEncryptionKey()).isEqualTo(renameAccountId);
    assertThat(encryptedData.getType()).isEqualTo(CONFIG_FILE);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(EmptyPredicate.isNotEmpty(encryptedData.getKmsId())).isTrue();
    assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.LOCAL);

    // now make the same file not encrypted
    fileToUpdate = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
    configFile.setEncrypted(false);
    configService.update(configFile, new BoundedInputStream(new FileInputStream(fileToUpdate)));
    download = configService.download(renameAppId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToUpdate, Charset.defaultCharset()));
    savedConfigFile = configService.get(renameAppId, configFileId);
    assertThat(savedConfigFile.isEncrypted()).isFalse();
    assertThat(isEmpty(savedConfigFile.getEncryptedFileId())).isTrue();

    assertThat(
        wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, renameAccountId).count())
        .isEqualTo(1);
    List<SecretChangeLog> changeLogs = secretManager.getChangeLogs(renameAccountId, secretFileId, CONFIG_FILE);
    assertThat(changeLogs).hasSize(1);
    SecretChangeLog changeLog = changeLogs.get(0);
    assertThat(changeLog.getAccountId()).isEqualTo(renameAccountId);
    assertThat(changeLog.getEncryptedDataId()).isEqualTo(secretFileId);
    assertThat(changeLog.getUser().getName()).isEqualTo(userName);
    assertThat(changeLog.getUser().getEmail()).isEqualTo(userEmail);
    assertThat(changeLog.getUser().getUuid()).isEqualTo(user.getUuid());
    assertThat(changeLog.getDescription()).isEqualTo("Created");
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)

  public void saveConfigFileNoEncryption() throws IOException {
    final long seed = System.currentTimeMillis();
    log.info("seed: " + seed);
    Random r = new Random(seed);
    Account renameAccount = getAccount(AccountType.PAID);
    String renameAccountId = renameAccount.getUuid();
    when(accountService.get(renameAccountId)).thenReturn(renameAccount);
    final String renameAppId = UUID.randomUUID().toString();
    KmsConfig fromConfig = secretManagementTestHelper.getKmsConfig();

    when(secretsManagementFeature.isAvailableForAccount(renameAccountId)).thenReturn(true);

    kmsResource.saveKmsConfig(renameAccountId, fromConfig);

    Service service = Service.builder().name(UUID.randomUUID().toString()).appId(renameAppId).build();
    wingsPersistence.save(service);

    ConfigFile configFile = ConfigFile.builder()
                                .templateId(UUID.randomUUID().toString())
                                .envId(UUID.randomUUID().toString())
                                .entityType(EntityType.SERVICE)
                                .entityId(service.getUuid())
                                .description(UUID.randomUUID().toString())
                                .parentConfigFileId(UUID.randomUUID().toString())
                                .relativeFilePath(UUID.randomUUID().toString())
                                .targetToAllEnv(r.nextBoolean())
                                .defaultVersion(r.nextInt())
                                .envIdVersionMapString(UUID.randomUUID().toString())
                                .setAsDefault(r.nextBoolean())
                                .notes(UUID.randomUUID().toString())
                                .overridePath(UUID.randomUUID().toString())
                                .configOverrideType(ConfigOverrideType.CUSTOM)
                                .configOverrideExpression(UUID.randomUUID().toString())
                                .encrypted(false)
                                .build();

    configFile.setAccountId(renameAccountId);
    configFile.setName(UUID.randomUUID().toString());
    configFile.setFileName(UUID.randomUUID().toString());
    configFile.setAppId(renameAppId);

    File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");

    configService.save(configFile, new BoundedInputStream(new FileInputStream(fileToSave)));
    File download = configService.download(renameAppId, configFile.getUuid());
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToSave, Charset.defaultCharset()));
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)

  public void saveConfigFileWithEncryption() throws IOException, IllegalAccessException {
    final long seed = System.currentTimeMillis();
    log.info("seed: " + seed);
    Random r = new Random(seed);
    Account randomAccount = getAccount(AccountType.PAID);
    String randomAccountId = randomAccount.getUuid();
    when(accountService.get(randomAccountId)).thenReturn(randomAccount);
    final String randomAppId = UUID.randomUUID().toString();
    KmsConfig fromConfig = secretManagementTestHelper.getKmsConfig();

    when(secretsManagementFeature.isAvailableForAccount(randomAccountId)).thenReturn(true);

    kmsResource.saveKmsConfig(randomAccountId, fromConfig);

    Service service = Service.builder().name(UUID.randomUUID().toString()).appId(randomAppId).build();
    wingsPersistence.save(service);

    Activity activity = Activity.builder().workflowExecutionId(workflowExecutionId).environmentId(envId).build();
    activity.setAppId(randomAppId);
    wingsPersistence.save(activity);

    String secretName = UUID.randomUUID().toString();
    File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
    SecretFile secretFile = SecretFile.builder()
                                .name(secretName)
                                .kmsId(kmsId)
                                .inheritScopesFromSM(true)
                                .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToSave)))
                                .build();
    String secretFileId = secretManager.saveSecretFile(randomAccountId, secretFile);
    String encryptedUuid = wingsPersistence.createQuery(EncryptedData.class)
                               .filter(EncryptedDataKeys.type, CONFIG_FILE)
                               .filter(EncryptedDataKeys.accountId, randomAccountId)
                               .get()
                               .getUuid();

    ConfigFile configFile = ConfigFile.builder()
                                .templateId(UUID.randomUUID().toString())
                                .envId(UUID.randomUUID().toString())
                                .entityType(EntityType.SERVICE)
                                .entityId(service.getUuid())
                                .description(UUID.randomUUID().toString())
                                .parentConfigFileId(UUID.randomUUID().toString())
                                .relativeFilePath(UUID.randomUUID().toString())
                                .targetToAllEnv(r.nextBoolean())
                                .defaultVersion(r.nextInt())
                                .envIdVersionMapString(UUID.randomUUID().toString())
                                .setAsDefault(r.nextBoolean())
                                .notes(UUID.randomUUID().toString())
                                .overridePath(UUID.randomUUID().toString())
                                .configOverrideType(ConfigOverrideType.CUSTOM)
                                .configOverrideExpression(UUID.randomUUID().toString())
                                .encryptedFileId(secretFileId)
                                .encrypted(true)
                                .build();

    configFile.setAccountId(randomAccountId);
    configFile.setName(UUID.randomUUID().toString());
    configFile.setFileName(UUID.randomUUID().toString());
    configFile.setAppId(randomAppId);

    String configFileId = configService.save(configFile, null);
    File download = configService.download(randomAppId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToSave, Charset.defaultCharset()));
    assertThat(
        wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, randomAccountId).count())
        .isEqualTo(numOfEncryptedValsForKms + 1);

    List<EncryptedData> encryptedFileData = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                                                .filter("type", CONFIG_FILE)
                                                .filter("accountId", randomAccountId)
                                                .asList();
    assertThat(encryptedFileData).hasSize(1);
    assertThat(encryptedFileData.get(0).getParents()).hasSize(1);
    assertThat(encryptedFileData.get(0).containsParent(configFileId, CONFIG_FILE)).isTrue();

    // test update
    String newSecretName = UUID.randomUUID().toString();
    File fileToUpdate = new File("400-rest/src/test/resources/encryption/file_to_update.txt");
    SecretFile updateSecretFile = SecretFile.builder()
                                      .name(newSecretName)
                                      .kmsId(kmsId)
                                      .inheritScopesFromSM(true)
                                      .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToUpdate)))
                                      .build();
    secretManager.updateSecretFile(randomAccountId, encryptedUuid, updateSecretFile);

    download = configService.download(randomAppId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToUpdate, Charset.defaultCharset()));
    assertThat(
        wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, randomAccountId).count())
        .isEqualTo(numOfEncryptedValsForKms + 1);

    encryptedFileData = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                            .filter("type", CONFIG_FILE)
                            .filter("accountId", randomAccountId)
                            .asList();
    assertThat(encryptedFileData).hasSize(1);
    assertThat(isEmpty(encryptedFileData.get(0).getParents())).isFalse();

    int numOfAccess = 7;
    for (int i = 0; i < numOfAccess; i++) {
      configService.downloadForActivity(randomAppId, configFileId, activity.getUuid());
    }
    List<SecretUsageLog> usageLogs =
        (List<SecretUsageLog>) secretManagementResource
            .getUsageLogs(aPageRequest().build(), randomAccountId, encryptedUuid, CONFIG_FILE)
            .getResource();
    assertThat(usageLogs).hasSize(numOfAccess);

    for (SecretUsageLog usageLog : usageLogs) {
      assertThat(usageLog.getWorkflowExecutionName()).isEqualTo(workflowName);
      assertThat(usageLog.getAccountId()).isEqualTo(randomAccountId);
      assertThat(usageLog.getEnvId()).isEqualTo(envId);
      assertThat(usageLog.getAppId()).isEqualTo(randomAppId);
    }

    List<SecretChangeLog> changeLogs = secretManager.getChangeLogs(randomAccountId, secretFileId, CONFIG_FILE);
    assertThat(changeLogs).hasSize(2);
    SecretChangeLog changeLog = changeLogs.get(0);
    assertThat(changeLog.getAccountId()).isEqualTo(randomAccountId);
    assertThat(changeLog.getEncryptedDataId()).isEqualTo(secretFileId);
    assertThat(changeLog.getUser().getName()).isEqualTo(userName);
    assertThat(changeLog.getUser().getEmail()).isEqualTo(userEmail);
    assertThat(changeLog.getUser().getUuid()).isEqualTo(user.getUuid());
    assertThat(changeLog.getDescription()).isEqualTo("Changed name & secret");

    changeLog = changeLogs.get(1);
    assertThat(changeLog.getAccountId()).isEqualTo(randomAccountId);
    assertThat(changeLog.getEncryptedDataId()).isEqualTo(secretFileId);
    assertThat(changeLog.getUser().getName()).isEqualTo(userName);
    assertThat(changeLog.getUser().getEmail()).isEqualTo(userEmail);
    assertThat(changeLog.getUser().getUuid()).isEqualTo(user.getUuid());
    assertThat(changeLog.getDescription()).isEqualTo("Created");
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)

  public void saveConfigFileTemplateWithEncryption() throws IOException {
    final long seed = System.currentTimeMillis();
    log.info("seed: " + seed);
    Random r = new Random(seed);
    Account renameAccount = getAccount(AccountType.PAID);
    String renameAccountId = renameAccount.getUuid();
    when(accountService.get(renameAccountId)).thenReturn(renameAccount);
    final String renameAppId = UUID.randomUUID().toString();

    KmsConfig fromConfig = secretManagementTestHelper.getKmsConfig();

    when(secretsManagementFeature.isAvailableForAccount(renameAccountId)).thenReturn(true);

    kmsResource.saveKmsConfig(renameAccountId, fromConfig);

    Service service = Service.builder().name(UUID.randomUUID().toString()).build();
    service.setAppId(renameAppId);
    String serviceId = wingsPersistence.save(service);
    ServiceTemplate serviceTemplate = ServiceTemplate.Builder.aServiceTemplate().withServiceId(serviceId).build();
    serviceTemplate.setAppId(renameAppId);
    String serviceTemplateId = wingsPersistence.save(serviceTemplate);

    Activity activity = Activity.builder().workflowId(workflowExecutionId).build();
    activity.setAppId(renameAppId);
    wingsPersistence.save(activity);

    String secretName = UUID.randomUUID().toString();
    File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
    SecretFile secretFile = SecretFile.builder()
                                .name(secretName)
                                .kmsId(kmsId)
                                .inheritScopesFromSM(true)
                                .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToSave)))
                                .build();
    String secretFileId = secretManager.saveSecretFile(renameAccountId, secretFile);
    String encryptedUuid = wingsPersistence.createQuery(EncryptedData.class)
                               .filter(EncryptedDataKeys.accountId, renameAccountId)
                               .filter(EncryptedDataKeys.type, CONFIG_FILE)
                               .get()
                               .getUuid();

    ConfigFile configFile = ConfigFile.builder()
                                .templateId(UUID.randomUUID().toString())
                                .envId(UUID.randomUUID().toString())
                                .entityType(EntityType.SERVICE_TEMPLATE)
                                .entityId(serviceTemplateId)
                                .description(UUID.randomUUID().toString())
                                .parentConfigFileId(UUID.randomUUID().toString())
                                .relativeFilePath(UUID.randomUUID().toString())
                                .targetToAllEnv(r.nextBoolean())
                                .defaultVersion(r.nextInt())
                                .envIdVersionMapString(UUID.randomUUID().toString())
                                .setAsDefault(r.nextBoolean())
                                .notes(UUID.randomUUID().toString())
                                .overridePath(UUID.randomUUID().toString())
                                .configOverrideType(ConfigOverrideType.CUSTOM)
                                .configOverrideExpression(UUID.randomUUID().toString())
                                .encryptedFileId(secretFileId)
                                .templateId(serviceTemplateId)
                                .encrypted(true)
                                .build();

    configFile.setAccountId(renameAccountId);
    configFile.setName(UUID.randomUUID().toString());
    configFile.setFileName(UUID.randomUUID().toString());
    configFile.setAppId(renameAppId);

    String configFileId = configService.save(configFile, null);
    File download = configService.download(renameAppId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToSave, Charset.defaultCharset()));
    assertThat(
        wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, renameAccountId).count())
        .isEqualTo(numOfEncryptedValsForKms + 1);

    List<EncryptedData> encryptedFileData = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                                                .filter("type", CONFIG_FILE)
                                                .filter("accountId", renameAccountId)
                                                .asList();
    assertThat(encryptedFileData).hasSize(1);
    assertThat(isEmpty(encryptedFileData.get(0).getParents())).isFalse();
    // test update
    String newSecretName = UUID.randomUUID().toString();
    File fileToUpdate = new File("400-rest/src/test/resources/encryption/file_to_update.txt");
    SecretFile secretFileUpdate = SecretFile.builder()
                                      .name(secretName)
                                      .kmsId(kmsId)
                                      .inheritScopesFromSM(true)
                                      .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToUpdate)))
                                      .build();
    secretManager.updateSecretFile(renameAccountId, encryptedUuid, secretFileUpdate);

    download = configService.download(renameAppId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToUpdate, Charset.defaultCharset()));
    assertThat(
        wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, renameAccountId).count())
        .isEqualTo(numOfEncryptedValsForKms + 1);

    encryptedFileData = wingsPersistence.createQuery(EncryptedData.class)
                            .filter(EncryptedDataKeys.accountId, renameAccountId)
                            .filter(EncryptedDataKeys.type, CONFIG_FILE)
                            .asList();
    assertThat(encryptedFileData).hasSize(1);
    assertThat(isEmpty(encryptedFileData.get(0).getParents())).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void reuseYamlPasswordNoEncryption() throws IllegalAccessException {
    int numOfSettingAttributes = 5;
    String password = "password";
    Set<String> attributeIds = new HashSet<>();
    AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    attributeIds.add(wingsPersistence.save(settingAttribute));

    String yamlRef =
        secretManager.getEncryptedYamlRef(appDynamicsConfig.getAccountId(), appDynamicsConfig.getEncryptedPassword());

    for (int i = 1; i < numOfSettingAttributes; i++) {
      appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, null, yamlRef);
      settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

      attributeIds.add(wingsPersistence.save(settingAttribute));
    }

    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(numOfSettingAttributes);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(1);

    List<EncryptedData> encryptedDatas = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                                             .field("encryptionType")
                                             .notEqual(EncryptionType.KMS)
                                             .asList();
    assertThat(encryptedDatas).hasSize(1);
    EncryptedData encryptedData = encryptedDatas.get(0);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.LOCAL);
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isNotNull();
    assertThat(encryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
    assertThat(encryptedData.getParents()).hasSize(numOfSettingAttributes);

    for (String attributeId : attributeIds) {
      assertThat(encryptedData.containsParent(attributeId, appDynamicsConfig.getSettingType())).isTrue();
      SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, attributeId);
      AppDynamicsConfig savedConfig = (AppDynamicsConfig) savedAttribute.getValue();
      assertThat(savedConfig.getAccountId()).isEqualTo(accountId);
      assertThat(savedConfig.getPassword()).isNull();
      assertThat(isBlank(savedConfig.getEncryptedPassword())).isFalse();

      encryptionService.decrypt(
          savedConfig, secretManager.getEncryptionDetails(savedConfig, workflowExecutionId, appId), false);
      assertThat(String.valueOf(savedConfig.getPassword())).isEqualTo(password);
    }

    // delete configs and check
    int i = 0;
    for (String attributeId : attributeIds) {
      wingsPersistence.delete(accountId, SettingAttribute.class, attributeId);
      encryptedDatas =
          wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId).asList();
      assertThat(encryptedDatas).hasSize(1);
      encryptedData = encryptedDatas.get(0);
      assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.LOCAL);
      assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
      assertThat(encryptedData.isEnabled()).isTrue();
      assertThat(encryptedData.getKmsId()).isNotNull();
      assertThat(encryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
      assertThat(encryptedData.getParents()).hasSize(numOfSettingAttributes - (i + 1));
      assertThat(encryptedData.containsParent(attributeId, appDynamicsConfig.getSettingType())).isFalse();
      i++;
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void reuseYamlPasswordKmsEncryption() throws IllegalAccessException {
    KmsConfig fromConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, fromConfig);

    int numOfSettingAttributes = 5;
    String password = "password";
    Set<String> attributeIds = new HashSet<>();
    AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    attributeIds.add(wingsPersistence.save(settingAttribute));

    String yamlRef =
        secretManager.getEncryptedYamlRef(appDynamicsConfig.getAccountId(), appDynamicsConfig.getEncryptedPassword());
    for (int i = 1; i < numOfSettingAttributes; i++) {
      appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, null, yamlRef);
      settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

      attributeIds.add(wingsPersistence.save(settingAttribute));
    }

    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(numOfSettingAttributes);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncryptedValsForKms + 1);

    List<EncryptedData> encryptedDatas = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                                             .filter("encryptionType", EncryptionType.KMS)
                                             .asList();
    assertThat(encryptedDatas).hasSize(1);
    EncryptedData encryptedData = encryptedDatas.get(0);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.KMS);
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(fromConfig.getUuid());
    assertThat(encryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
    assertThat(encryptedData.getParents()).hasSize(numOfSettingAttributes);

    for (String attributeId : attributeIds) {
      assertThat(encryptedData.containsParent(attributeId, appDynamicsConfig.getSettingType())).isTrue();
      SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, attributeId);
      AppDynamicsConfig savedConfig = (AppDynamicsConfig) savedAttribute.getValue();
      assertThat(savedConfig.getAccountId()).isEqualTo(accountId);
      assertThat(savedConfig.getPassword()).isNull();
      assertThat(isBlank(savedConfig.getEncryptedPassword())).isFalse();

      encryptionService.decrypt(
          savedConfig, secretManager.getEncryptionDetails(savedConfig, workflowExecutionId, appId), false);
      assertThat(String.valueOf(savedConfig.getPassword())).isEqualTo(password);
    }

    // delete configs and check
    int i = 0;
    for (String attributeId : attributeIds) {
      wingsPersistence.delete(accountId, SettingAttribute.class, attributeId);
      encryptedDatas = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                           .filter("encryptionType", EncryptionType.KMS)
                           .asList();
      assertThat(encryptedDatas).hasSize(1);
      encryptedData = encryptedDatas.get(0);
      assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.KMS);
      assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
      assertThat(encryptedData.isEnabled()).isTrue();
      assertThat(encryptedData.getKmsId()).isEqualTo(fromConfig.getUuid());
      assertThat(encryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
      assertThat(encryptedData.getParents()).hasSize(numOfSettingAttributes - (i + 1));
      assertThat(encryptedData.containsParent(attributeId, appDynamicsConfig.getSettingType())).isFalse();
      i++;
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void getUsageLogs() throws IllegalAccessException {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    SecretText secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(UUID.randomUUID().toString())
                                                .envId(UUID.randomUUID().toString())
                                                .entityType(EntityType.APPLICATION)
                                                .entityId(UUID.randomUUID().toString())
                                                .parentServiceVariableId(UUID.randomUUID().toString())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                                .expression(UUID.randomUUID().toString())
                                                .accountId(accountId)
                                                .name(UUID.randomUUID().toString())
                                                .value(secretId.toCharArray())
                                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                .build();

    String savedAttributeId = wingsPersistence.save(serviceVariable);
    List<SecretUsageLog> usageLogs =
        (List<SecretUsageLog>) secretManagementResource
            .getUsageLogs(aPageRequest().build(), accountId, savedAttributeId, SettingVariableTypes.SERVICE_VARIABLE)
            .getResource();
    assertThat(usageLogs).isEmpty();

    ServiceVariable savedAttribute = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    secretManager.getEncryptionDetails(savedAttribute, appId, workflowExecutionId);
    usageLogs =
        (List<SecretUsageLog>) secretManagementResource
            .getUsageLogs(aPageRequest().build(), accountId, savedAttributeId, SettingVariableTypes.SERVICE_VARIABLE)
            .getResource();
    assertThat(usageLogs).hasSize(1);
    assertThat(usageLogs.get(0).getWorkflowExecutionName()).isEqualTo(workflowName);
    assertThat(usageLogs.get(0).getAccountId()).isEqualTo(accountId);
    assertThat(usageLogs.get(0).getEnvId()).isEqualTo(envId);
    assertThat(usageLogs.get(0).getAppId()).isEqualTo(appId);

    secretManager.getEncryptionDetails(savedAttribute, appId, workflowExecutionId);
    secretManager.getEncryptionDetails(savedAttribute, appId, workflowExecutionId);
    usageLogs =
        (List<SecretUsageLog>) secretManagementResource
            .getUsageLogs(aPageRequest().build(), accountId, savedAttributeId, SettingVariableTypes.SERVICE_VARIABLE)
            .getResource();
    assertThat(usageLogs).hasSize(3);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute appDAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String appDAttributeId = wingsPersistence.save(appDAttribute);
    usageLogs = (List<SecretUsageLog>) secretManagementResource
                    .getUsageLogs(aPageRequest().build(), accountId, appDAttributeId, SettingVariableTypes.APP_DYNAMICS)
                    .getResource();
    assertThat(usageLogs).isEmpty();
    int numOfAccess = 13;
    for (int i = 0; i < numOfAccess; i++) {
      secretManager.getEncryptionDetails((EncryptableSetting) appDAttribute.getValue(), appId, workflowExecutionId);
    }
    usageLogs = (List<SecretUsageLog>) secretManagementResource
                    .getUsageLogs(aPageRequest().build(), accountId, appDAttributeId, SettingVariableTypes.APP_DYNAMICS)
                    .getResource();
    assertThat(usageLogs).hasSize(numOfAccess);
    for (SecretUsageLog usageLog : usageLogs) {
      assertThat(usageLog.getWorkflowExecutionName()).isEqualTo(workflowName);
      assertThat(usageLog.getAccountId()).isEqualTo(accountId);
      assertThat(usageLog.getEnvId()).isEqualTo(envId);
      assertThat(usageLog.getAppId()).isEqualTo(appId);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void getChangeLogs() throws IllegalAccessException {
    final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsResource.saveKmsConfig(accountId, kmsConfig);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute appDAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String appDAttributeId = wingsPersistence.save(appDAttribute);
    int numOfUpdates = 13;
    for (int i = 0; i < numOfUpdates; i++) {
      appDynamicsConfig.setPassword(UUID.randomUUID().toString().toCharArray());
      wingsPersistence.save(appDAttribute);
    }

    List<SecretChangeLog> changeLogs =
        secretManager.getChangeLogs(accountId, appDAttributeId, SettingVariableTypes.APP_DYNAMICS);
    assertThat(changeLogs).hasSize(numOfUpdates + 1);
    for (int i = 0; i <= numOfUpdates; i++) {
      SecretChangeLog secretChangeLog = changeLogs.get(i);
      assertThat(secretChangeLog.getUser().getUuid()).isEqualTo(user.getUuid());
      assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(user.getEmail());
      assertThat(secretChangeLog.getUser().getName()).isEqualTo(user.getName());

      if (i == numOfUpdates) {
        assertThat(secretChangeLog.getDescription()).isEqualTo("Created");
      } else {
        assertThat(secretChangeLog.getDescription()).isEqualTo(" Changed secret");
      }
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void kms_Crud_shouldGenerate_Audit() {
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setAccountId(accountId);

    String secretManagerId = kmsService.saveKmsConfig(accountId, kryoSerializer.clone(kmsConfig));
    verify(auditServiceHelper)
        .reportForAuditingUsingAccountId(eq(accountId), eq(null), any(KmsConfig.class), eq(Event.Type.CREATE));

    kmsConfig.setUuid(secretManagerId);
    kmsConfig.setDefault(false);
    kmsConfig.setName(kmsConfig.getName() + "_Updated");
    kmsService.saveKmsConfig(accountId, kryoSerializer.clone(kmsConfig));
    verify(auditServiceHelper)
        .reportForAuditingUsingAccountId(
            eq(accountId), any(KmsConfig.class), any(KmsConfig.class), eq(Event.Type.UPDATE));

    kmsService.deleteKmsConfig(accountId, secretManagerId);
    verify(auditServiceHelper).reportDeleteForAuditingUsingAccountId(eq(accountId), any(KmsConfig.class));
  }

  private Thread startTransitionListener() throws IllegalAccessException {
    transitionEventListener = new SecretMigrationEventListener(kmsTransitionConsumer);
    FieldUtils.writeField(transitionEventListener, "timer", new TimerScheduledExecutorService(), true);
    FieldUtils.writeField(transitionEventListener, "queueController", new ConfigurationController(), true);
    FieldUtils.writeField(transitionEventListener, "queueConsumer", transitionKmsQueue, true);
    FieldUtils.writeField(transitionEventListener, "secretService", secretService, true);

    Thread eventListenerThread = new Thread(() -> transitionEventListener.run());
    eventListenerThread.start();
    return eventListenerThread;
  }

  private void stopTransitionListener(Thread thread) throws InterruptedException {
    transitionEventListener.shutDown();
    thread.join();
  }

  private KmsConfig getNonDefaultKmsConfig() {
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setDefault(false);

    return kmsConfig;
  }

  private KmsConfig getDefaultKmsConfig() {
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setDefault(true);

    return kmsConfig;
  }
}
