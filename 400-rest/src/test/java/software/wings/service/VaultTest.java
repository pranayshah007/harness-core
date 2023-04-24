/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.EncryptedData.PARENT_ID_KEY;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.PHOENIKX;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.UNKNOWN;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.TestUserProvider.testUserProvider;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.settings.SettingVariableTypes.CONFIG_FILE;

import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.EmbeddedUser;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.MigrateSecretTask;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SecretChangeLog;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.beans.SecretUsageLog;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.task.winrm.AuthenticationScheme;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.encryptors.clients.LocalEncryptor;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.helpers.ext.vault.SecretEngineSummary;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.queue.QueueConsumer;
import io.harness.queue.TimerScheduledExecutorService;
import io.harness.rule.Owner;
import io.harness.rule.Repeat;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secrets.SecretMigrationEventListener;
import io.harness.secrets.SecretService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.threading.Morpheus;

import software.wings.EncryptTestUtils;
import software.wings.SecretManagementTestHelper;
import software.wings.WingsBaseTest;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.Activity;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.ConfigFile;
import software.wings.beans.ConfigFile.ConfigOverrideType;
import software.wings.beans.EntityType;
import software.wings.beans.Event;
import software.wings.beans.KmsConfig;
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
import software.wings.beans.VaultConfig;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.WorkflowExecution;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.features.api.PremiumFeature;
import software.wings.helpers.ext.vault.VaultTokenLookupResult;
import software.wings.resources.secretsmanagement.SecretManagementResource;
import software.wings.security.UsageRestrictions;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.security.GlobalEncryptDecryptClient;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.KmsService;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.security.VaultService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by rsingh on 11/3/17.
 */
@RunWith(Parameterized.class)
@Slf4j
public class VaultTest extends WingsBaseTest {
  private static String VAULT_TOKEN = UUID.randomUUID().toString();

  private final int numOfEncryptedValsForKms = 3;
  private final int numOfEncryptedValsForVault = 1;
  private int numOfEncRecords;
  @Parameter public boolean isKmsEnabled;
  @Mock private AccountService accountService;
  @Mock private AppService appService;
  @Inject private ConfigService configService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject protected EncryptionService encryptionService;
  @Inject @InjectMocks private VaultService vaultService;
  @Inject @InjectMocks private KmsService kmsService;
  @Inject @InjectMocks private SecretManagerConfigService secretManagerConfigService;
  @Inject @InjectMocks private EntityVersionService entityVersionService;
  @Inject @InjectMocks private SecretService secretService;
  @Inject protected SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private QueueConsumer<MigrateSecretTask> kmsTransitionConsumer;
  @Inject protected LocalSecretManagerService localSecretManagerService;
  @Inject private SecretManagementTestHelper secretManagementTestHelper;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private SecretManagementDelegateService secretManagementDelegateService;
  @Mock private DelegateTaskService delegateTaskService;
  @Mock private PremiumFeature secretsManagementFeature;
  @Mock protected AuditServiceHelper auditServiceHelper;
  @Mock private GlobalEncryptDecryptClient globalEncryptDecryptClient;
  @Mock private KmsEncryptor kmsEncryptor;
  @Mock private VaultEncryptor vaultEncryptor;
  @Inject private LocalEncryptor localEncryptor;
  @Inject private SecretManagementResource secretManagementResource;
  @Inject private QueueConsumer<MigrateSecretTask> transitionKmsQueue;
  @Mock private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Mock private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  private final String userEmail = "rsingh@harness.io";
  private final String userName = "UTKARSH";
  private final User user = User.Builder.anUser().email(userEmail).name(userName).build();
  private String accountId;
  private String appId;
  private String workflowExecutionId;
  private String workflowName;
  private SecretMigrationEventListener transitionEventListener;
  private String kmsId;
  private String envId;

  @Inject KryoSerializer kryoSerializer;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Parameters
  public static Collection<Object[]> data() {
    return asList(new Object[][] {{true}, {false}});
  }

  @Before
  public void setup() throws IllegalAccessException {
    //    assumeTrue(getClass().getClassLoader().getResource("vault_token.txt") != null);
    initMocks(this);
    appId = UUID.randomUUID().toString();
    workflowName = UUID.randomUUID().toString();
    envId = UUID.randomUUID().toString();
    workflowExecutionId = wingsPersistence.save(WorkflowExecution.builder().name(workflowName).envId(envId).build());

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

    when(vaultEncryptor.createSecret(anyString(), any(), any())).then(invocation -> {
      Object[] args = invocation.getArguments();
      if (args[2] instanceof VaultConfig) {
        return EncryptTestUtils.encrypt((String) args[0], ((SecretText) args[1]).getName(),
            ((SecretText) args[1]).getValue(), (VaultConfig) args[2], null);
      }
      return null;
    });

    when(vaultEncryptor.createSecret(anyString(), anyString(), anyString(), any())).then(invocation -> {
      Object[] args = invocation.getArguments();
      if (args[3] instanceof VaultConfig) {
        return EncryptTestUtils.encrypt(
            (String) args[0], (String) args[1], (String) args[2], (VaultConfig) args[3], null);
      }
      return null;
    });

    when(vaultEncryptor.updateSecret(anyString(), any(), any(), any())).then(invocation -> {
      Object[] args = invocation.getArguments();
      if (args[3] instanceof VaultConfig) {
        return EncryptTestUtils.encrypt((String) args[0], ((SecretText) args[1]).getName(),
            ((SecretText) args[1]).getValue(), (VaultConfig) args[3], null);
      }
      return null;
    });

    when(vaultEncryptor.fetchSecretValue(anyString(), any(), any())).then(invocation -> {
      Object[] args = invocation.getArguments();
      if (args[2] instanceof VaultConfig) {
        return EncryptTestUtils.decrypt((EncryptedRecord) args[1], (VaultConfig) args[2]);
      }
      return null;
    });

    when(vaultEncryptor.deleteSecret(anyString(), any(), any())).thenReturn(true);

    when(vaultEncryptor.validateReference(anyString(), any(SecretText.class), any())).thenReturn(true);

    when(kmsEncryptorsRegistry.getKmsEncryptor(any())).thenReturn(kmsEncryptor);
    when(vaultEncryptorsRegistry.getVaultEncryptor(any())).thenReturn(vaultEncryptor);
    when(delegateProxyFactory.getV2(eq(SecretManagementDelegateService.class), any(SyncTaskContext.class)))
        .thenReturn(secretManagementDelegateService);
    FieldUtils.writeField(vaultService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(secretManager, "vaultService", vaultService, true);
    FieldUtils.writeField(wingsPersistence, "secretManager", secretManager, true);
    FieldUtils.writeField(configService, "secretManager", secretManager, true);
    FieldUtils.writeField(secretService, "kmsRegistry", kmsEncryptorsRegistry, true);
    FieldUtils.writeField(secretService, "vaultRegistry", vaultEncryptorsRegistry, true);
    FieldUtils.writeField(encryptionService, "kmsEncryptorsRegistry", kmsEncryptorsRegistry, true);
    FieldUtils.writeField(encryptionService, "vaultEncryptorsRegistry", vaultEncryptorsRegistry, true);

    wingsPersistence.save(user);
    UserThreadLocal.set(user);
    testUserProvider.setActiveUser(EmbeddedUser.builder().uuid(user.getUuid()).name(userName).email(userEmail).build());

    Account account = getAccount(AccountType.PAID);
    accountId = account.getUuid();
    when(accountService.get(accountId)).thenReturn(account);
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);
    when(appService.getAccountIdByAppId(appId)).thenReturn(accountId);
    numOfEncRecords = numOfEncryptedValsForVault;
    if (isKmsEnabled) {
      final KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
      kmsId = kmsService.saveKmsConfig(accountId, kmsConfig);
      numOfEncRecords = numOfEncryptedValsForKms + numOfEncryptedValsForVault;
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void invalidConfig() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setAuthToken("invalidKey");
    vaultConfig.setAccountId(accountId);

    try {
      vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
      fail("Saved invalid vault config");
    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.UNKNOWN_ERROR);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void createEncryptedText_WithReadOnlyVault() {
    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setReadOnly(true);
    vaultConfig.setDefault(false);
    String configId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
    assertThat(configId).isNotNull();
    SecretText secretText = SecretText.builder().name(secretName).kmsId(configId).path("/value/utkarsh#key").build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);
    assertThat(secretId).isNotNull();
    try {
      secretText = SecretText.builder().name(secretName).kmsId(configId).value(secretValue).build();
      secretManager.saveSecretText(accountId, secretText, true);
      fail("Should not have been able to create encrypted text with read only vault");
    } catch (SecretManagementException e) {
      log.info("Error", e);
      assertThat(e.getCode()).isEqualTo(ErrorCode.SECRET_MANAGEMENT_ERROR);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void createEncryptedFile_WithReadOnlyVault() throws FileNotFoundException, IOException {
    String secretFileName = UUID.randomUUID().toString();
    File file = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");

    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setReadOnly(true);
    vaultConfig.setDefault(false);
    String configId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
    assertThat(configId).isNotNull();
    SecretFile secretFile = SecretFile.builder()
                                .inheritScopesFromSM(true)
                                .name(secretFileName)
                                .kmsId(configId)
                                .fileContent(ByteStreams.toByteArray(new FileInputStream(file)))
                                .build();

    try {
      secretManager.saveSecretFile(accountId, secretFile);
      fail("Should not have been able to create encrypted file with read only vault");
    } catch (SecretManagementException e) {
      log.info("Error", e);
      assertThat(e.getCode()).isEqualTo(ErrorCode.SECRET_MANAGEMENT_ERROR);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void saveConfig() {
    if (isKmsEnabled) {
      kmsService.deleteKmsConfig(accountId, kmsId);
    }

    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setDefault(false);
    vaultConfig.setReadOnly(false);
    String vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    List<SecretManagerConfig> encryptionConfigs = secretManager.listSecretManagers(accountId);
    VaultConfig next = (VaultConfig) encryptionConfigs.get(0);
    assertThat(next.isDefault()).isFalse();
    assertThat(next.getAccountId()).isEqualTo(accountId);
    assertThat(String.valueOf(next.getAuthToken())).isEqualTo(SECRET_MASK);
    assertThat(next.getName()).isEqualTo(vaultConfig.getName());
    assertThat(next.getVaultUrl()).isEqualTo(vaultConfig.getVaultUrl());
    assertThat(next.isDefault()).isFalse();

    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setDefault(true);
    kmsId = kmsService.saveKmsConfig(accountId, kmsConfig);

    encryptionConfigs = secretManager.listSecretManagers(accountId);
    int numOfVaultDefaults = 0;
    int numOfKmsDefaults = 0;

    for (EncryptionConfig encryptionConfig : encryptionConfigs) {
      if (encryptionConfig.getEncryptionType() == EncryptionType.KMS) {
        assertThat(encryptionConfig.isDefault()).isTrue();
        assertThat(encryptionConfig.getUuid()).isEqualTo(kmsId);
        numOfKmsDefaults++;
      }

      if (encryptionConfig.getEncryptionType() == EncryptionType.VAULT) {
        assertThat(encryptionConfig.isDefault()).isFalse();
        assertThat(encryptionConfig.getUuid()).isEqualTo(vaultConfigId);
        numOfVaultDefaults++;
      }
    }

    assertThat(numOfKmsDefaults).isEqualTo(1);
    assertThat(numOfVaultDefaults).isEqualTo(1);

    vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setDefault(true);
    vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    encryptionConfigs = secretManager.listSecretManagers(accountId);
    numOfVaultDefaults = 0;
    numOfKmsDefaults = 0;

    for (EncryptionConfig encryptionConfig : encryptionConfigs) {
      if (encryptionConfig.getEncryptionType() == EncryptionType.KMS) {
        assertThat(encryptionConfig.isDefault()).isFalse();
        assertThat(encryptionConfig.getUuid()).isEqualTo(kmsId);
        numOfKmsDefaults++;
      }

      if (encryptionConfig.getEncryptionType() == EncryptionType.VAULT) {
        if (encryptionConfig.getUuid().equals(vaultConfigId)) {
          assertThat(encryptionConfig.isDefault()).isTrue();
          numOfVaultDefaults++;
        } else {
          assertThat(encryptionConfig.isDefault()).isFalse();
        }
      }
    }

    assertThat(numOfKmsDefaults).isEqualTo(1);
    assertThat(numOfVaultDefaults).isEqualTo(1);

    kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setDefault(true);
    kmsId = kmsService.saveKmsConfig(accountId, kmsConfig);

    encryptionConfigs = secretManager.listSecretManagers(accountId);

    numOfVaultDefaults = 0;
    numOfKmsDefaults = 0;

    for (EncryptionConfig encryptionConfig : encryptionConfigs) {
      if (encryptionConfig.getEncryptionType() == EncryptionType.KMS) {
        if (encryptionConfig.getUuid().equals(kmsId)) {
          assertThat(encryptionConfig.isDefault()).isTrue();
          numOfKmsDefaults++;
        } else {
          assertThat(encryptionConfig.isDefault()).isFalse();
        }
      }

      if (encryptionConfig.getEncryptionType() == EncryptionType.VAULT) {
        assertThat(encryptionConfig.isDefault()).isFalse();
        numOfVaultDefaults++;
      }
    }

    assertThat(numOfKmsDefaults).isEqualTo(1);
    assertThat(numOfVaultDefaults).isEqualTo(2);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void saveAndEditConfig() {
    Account renameAccount = getAccount(AccountType.PAID);
    String renameAccountId = renameAccount.getUuid();
    when(accountService.get(renameAccountId)).thenReturn(renameAccount);
    when(secretsManagementFeature.isAvailableForAccount(renameAccountId)).thenReturn(true);

    String name = UUID.randomUUID().toString();
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setName(name);
    vaultConfig.setAccountId(renameAccountId);

    vaultService.saveOrUpdateVaultConfig(renameAccountId, vaultConfig, true);
    vaultConfig.setAuthToken(VAULT_TOKEN);

    VaultConfig savedConfig = (VaultConfig) secretManagerConfigService.getDefaultSecretManager(renameAccountId);
    assertThat(savedConfig).isEqualTo(vaultConfig);

    // Testing getVaultConfigByName API is working properly
    savedConfig = vaultService.getVaultConfigByName(renameAccountId, vaultConfig.getName());
    assertThat(savedConfig).isEqualTo(vaultConfig);

    List<EncryptedData> encryptedDataList = wingsPersistence.createQuery(EncryptedData.class)
                                                .filter(EncryptedDataKeys.type, SettingVariableTypes.VAULT)
                                                .filter(EncryptedDataKeys.accountId, renameAccountId)
                                                .asList();
    assertThat(encryptedDataList).hasSize(1);
    assertThat(encryptedDataList.get(0).getParents()).hasSize(1);
    assertThat(encryptedDataList.get(0).containsParent(savedConfig.getUuid(), SettingVariableTypes.VAULT)).isTrue();
    assertThat(encryptedDataList.get(0).getName()).isEqualTo(savedConfig.getUuid() + "_token");

    name = UUID.randomUUID().toString();
    vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken();
    savedConfig.setAuthToken(vaultConfig.getAuthToken());
    savedConfig.setName(name);
    vaultService.saveOrUpdateVaultConfig(renameAccountId, savedConfig, true);
    encryptedDataList = wingsPersistence.createQuery(EncryptedData.class)
                            .filter(EncryptedDataKeys.accountId, renameAccountId)
                            .filter(EncryptedDataKeys.type, SettingVariableTypes.VAULT)
                            .asList();
    assertThat(encryptedDataList).hasSize(1);
    assertThat(encryptedDataList.get(0).getParents()).hasSize(1);
    assertThat(encryptedDataList.get(0).containsParent(savedConfig.getUuid(), SettingVariableTypes.VAULT)).isTrue();
    assertThat(encryptedDataList.get(0).getName()).isEqualTo(savedConfig.getUuid() + "_token");
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveAndEditConfig_withMaskedSecrets_changeNameDefaultOnly() {
    String name = UUID.randomUUID().toString();
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setName(name);
    vaultConfig.setAccountId(accountId);

    vaultService.saveOrUpdateVaultConfig(accountId, kryoSerializer.clone(vaultConfig), true);

    VaultConfig savedConfig = (VaultConfig) secretManagerConfigService.getDefaultSecretManager(accountId);
    assertThat(savedConfig.getAuthToken()).isEqualTo(vaultConfig.getAuthToken());
    assertThat(savedConfig.getSecretId()).isEqualTo(vaultConfig.getSecretId());
    assertThat(savedConfig.getName()).isEqualTo(vaultConfig.getName());
    assertThat(savedConfig.isDefault()).isEqualTo(true);

    String newName = UUID.randomUUID().toString();
    vaultConfig.setUuid(savedConfig.getUuid());
    vaultConfig.setName(newName);
    vaultConfig.setDefault(false);
    vaultConfig.maskSecrets();

    // Masked Secrets, only name and default flag should be updated.
    vaultService.saveOrUpdateVaultConfig(accountId, kryoSerializer.clone(vaultConfig), true);

    VaultConfig modifiedSavedConfig = vaultService.getVaultConfig(accountId, savedConfig.getUuid());
    assertThat(modifiedSavedConfig.getAuthToken()).isEqualTo(savedConfig.getAuthToken());
    assertThat(modifiedSavedConfig.getSecretId()).isEqualTo(savedConfig.getSecretId());
    assertThat(modifiedSavedConfig.getName()).isEqualTo(vaultConfig.getName());
    assertThat(modifiedSavedConfig.isDefault()).isEqualTo(false);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void updateVaultConfig_fromTokenBased_ToK8sAuth_withoutAuthEndpoint() {
    Account account = getAccount(AccountType.PAID);
    String accountId = account.getUuid();

    when(accountService.get(accountId)).thenReturn(account);
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);

    String name = UUID.randomUUID().toString();
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setName(name);
    vaultConfig.setAccountId(accountId);

    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
    vaultConfig.setAuthToken(VAULT_TOKEN);

    String k8sAuthRole = "k8sRole";
    String k8sServiceAccountTokenPath = "k8sServiceAccountTokenPath";

    VaultConfig vaultConfigNew =
        secretManagementTestHelper.getVaultConfigWithK8sAuth(null, k8sAuthRole, k8sServiceAccountTokenPath);
    vaultConfigNew.setUuid(vaultConfig.getUuid());
    ArgumentCaptor<VaultConfig> argumentCaptor = ArgumentCaptor.forClass(VaultConfig.class);
    String vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfigNew, false);
    verify(auditServiceHelper, times(2))
        .reportForAuditingUsingAccountId(eq(accountId), any(), argumentCaptor.capture(), any());
    assertThat(vaultConfigId).isEqualTo(vaultConfig.getUuid());
    VaultConfig updatedConfig = argumentCaptor.getValue();
    assertThat(updatedConfig.isUseK8sAuth()).isEqualTo(true);
    assertThat(updatedConfig.getVaultK8sAuthRole()).isEqualTo(k8sAuthRole);
    assertThat(updatedConfig.getServiceAccountTokenPath()).isEqualTo(k8sServiceAccountTokenPath);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void updateVaultConfig_fromAppRole_ToK8sAuth_withoutAuthEndpoint() {
    Account account = getAccount(AccountType.PAID);
    String accountId = account.getUuid();

    when(accountService.get(accountId)).thenReturn(account);
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);

    String name = UUID.randomUUID().toString();
    String approleId = UUID.randomUUID().toString();
    String secretId = UUID.randomUUID().toString();
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole(approleId, secretId);
    vaultConfig.setName(name);
    vaultConfig.setAccountId(accountId);
    VaultAppRoleLoginResult vaultAppRoleLoginResult = mock(VaultAppRoleLoginResult.class);
    when(secretManagementDelegateService.appRoleLogin(any())).thenReturn(vaultAppRoleLoginResult);
    when(vaultAppRoleLoginResult.getClientToken()).thenReturn(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, false);

    String k8sAuthRole = "k8sRole";
    String k8sServiceAccountTokenPath = "k8sServiceAccountTokenPath";

    VaultConfig vaultConfigNew =
        secretManagementTestHelper.getVaultConfigWithK8sAuth(null, k8sAuthRole, k8sServiceAccountTokenPath);
    vaultConfigNew.setUuid(vaultConfig.getUuid());
    ArgumentCaptor<VaultConfig> argumentCaptor = ArgumentCaptor.forClass(VaultConfig.class);
    String vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfigNew, false);
    verify(auditServiceHelper, times(2))
        .reportForAuditingUsingAccountId(eq(accountId), any(), argumentCaptor.capture(), any());
    assertThat(vaultConfigId).isEqualTo(vaultConfig.getUuid());
    VaultConfig updatedConfig = argumentCaptor.getValue();
    assertThat(updatedConfig.isUseK8sAuth()).isEqualTo(true);
    assertThat(updatedConfig.getVaultK8sAuthRole()).isEqualTo(k8sAuthRole);
    assertThat(updatedConfig.getServiceAccountTokenPath()).isEqualTo(k8sServiceAccountTokenPath);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void updateVaultConfig_fromK8sWithoutAuthEndpoint_ToK8sAuthWithAuthEndpoint() {
    Account account = getAccount(AccountType.PAID);
    String accountId = account.getUuid();

    when(accountService.get(accountId)).thenReturn(account);
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);

    String name = UUID.randomUUID().toString();
    String k8sRole = UUID.randomUUID().toString();
    String k8sSAPath = UUID.randomUUID().toString();
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithK8sAuth(null, k8sRole, k8sSAPath);
    vaultConfig.setName(name);
    vaultConfig.setAccountId(accountId);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, false);

    String k8sAuthEndpoint = "k8sAuthEndpoint";
    String k8sAuthRole = "k8sRole";
    String k8sServiceAccountTokenPath = "k8sServiceAccountTokenPath";

    VaultConfig vaultConfigNew =
        secretManagementTestHelper.getVaultConfigWithK8sAuth(k8sAuthEndpoint, k8sAuthRole, k8sServiceAccountTokenPath);
    vaultConfigNew.setUuid(vaultConfig.getUuid());
    ArgumentCaptor<VaultConfig> argumentCaptor = ArgumentCaptor.forClass(VaultConfig.class);
    String vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfigNew, false);
    verify(auditServiceHelper, times(2))
        .reportForAuditingUsingAccountId(eq(accountId), any(), argumentCaptor.capture(), any());
    assertThat(vaultConfigId).isEqualTo(vaultConfig.getUuid());
    VaultConfig updatedConfig = argumentCaptor.getValue();
    assertThat(updatedConfig.isUseK8sAuth()).isEqualTo(true);
    assertThat(updatedConfig.getK8sAuthEndpoint()).isEqualTo(k8sAuthEndpoint);
    assertThat(updatedConfig.getVaultK8sAuthRole()).isEqualTo(k8sAuthRole);
    assertThat(updatedConfig.getServiceAccountTokenPath()).isEqualTo(k8sServiceAccountTokenPath);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void saveConfigDefaultWithDefaultKms() {
    if (isKmsEnabled) {
      wingsPersistence.delete(KmsConfig.class, kmsId);
    }
    // set kms default config
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setAccountId(GLOBAL_ACCOUNT_ID);
    kmsService.saveGlobalKmsConfig(accountId, kmsConfig);

    List<SecretManagerConfig> encryptionConfigs = secretManager.listSecretManagers(accountId);
    assertThat(encryptionConfigs).hasSize(1);
    KmsConfig savedKmsConfig = (KmsConfig) encryptionConfigs.get(0);
    assertThat(savedKmsConfig.isDefault()).isTrue();
    assertThat(savedKmsConfig.getAccountId()).isEqualTo(GLOBAL_ACCOUNT_ID);

    SecretManagerConfig defaultConfig = secretManagerConfigService.getDefaultSecretManager(accountId);
    assertThat(defaultConfig instanceof KmsConfig).isTrue();
    assertThat(defaultConfig.getAccountId()).isEqualTo(GLOBAL_ACCOUNT_ID);

    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    defaultConfig = secretManagerConfigService.getDefaultSecretManager(accountId);
    assertThat(defaultConfig instanceof VaultConfig).isTrue();
    assertThat(defaultConfig.getAccountId()).isEqualTo(accountId);

    encryptionConfigs = secretManager.listSecretManagers(accountId);
    assertThat(encryptionConfigs).hasSize(2);

    VaultConfig savedVaultConfig = (VaultConfig) encryptionConfigs.get(0);
    assertThat(savedVaultConfig.isDefault()).isTrue();
    assertThat(savedVaultConfig.getAccountId()).isEqualTo(accountId);

    savedKmsConfig = (KmsConfig) encryptionConfigs.get(1);
    // PL-3472: There should be only one default secret manager from the list secret manager call.
    assertThat(savedKmsConfig.isDefault()).isFalse();
    assertThat(savedKmsConfig.getAccountId()).isEqualTo(GLOBAL_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void saveConfigDefault() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    Collection<SecretManagerConfig> vaultConfigs =
        secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.VAULT, true);
    assertThat(vaultConfigs).hasSize(1);
    VaultConfig next = (VaultConfig) vaultConfigs.iterator().next();
    assertThat(next.isDefault()).isTrue();

    vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setName("config1");
    vaultConfig.setDefault(true);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    vaultConfigs = secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.VAULT, true);
    assertThat(vaultConfigs).hasSize(2);

    int numOfDefault = 0;
    int numOfNonDefault = 0;

    for (SecretManagerConfig config : vaultConfigs) {
      if (config.getName().equals(secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN).getName())) {
        assertThat(config.isDefault()).isFalse();
        numOfNonDefault++;
      }

      if (config.getName().equals("config1")) {
        assertThat(config.isDefault()).isTrue();
        numOfDefault++;
      }
    }

    assertThat(numOfDefault).isEqualTo(1);
    assertThat(numOfNonDefault).isEqualTo(1);

    vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setName("config2");
    vaultConfig.setDefault(true);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    vaultConfigs = secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.VAULT, true);
    assertThat(vaultConfigs).hasSize(3);

    for (SecretManagerConfig config : vaultConfigs) {
      if (config.getName().equals(secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN).getName())
          || config.getName().equals("config1")) {
        assertThat(config.isDefault()).isFalse();
        numOfNonDefault++;
      }

      if (config.getName().equals("config2")) {
        assertThat(config.isDefault()).isTrue();
        numOfDefault++;
      }
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void getConfigDefault() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    Collection<SecretManagerConfig> vaultConfigs =
        secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.VAULT, true);
    assertThat(vaultConfigs).hasSize(1);
    VaultConfig next = (VaultConfig) vaultConfigs.iterator().next();
    assertThat(next.isDefault()).isTrue();

    vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setName("config1");
    vaultConfig.setDefault(true);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setName("config2");
    vaultConfig.setDefault(false);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    vaultConfigs = secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.VAULT, true);
    assertThat(vaultConfigs).hasSize(3);

    VaultConfig defaultConfig = (VaultConfig) secretManagerConfigService.getDefaultSecretManager(accountId);
    assertThat(defaultConfig).isNotNull();

    assertThat(defaultConfig.getAccountId()).isEqualTo(accountId);
    assertThat(String.valueOf(defaultConfig.getAuthToken())).isEqualTo(VAULT_TOKEN);
    assertThat(defaultConfig.getName()).isEqualTo("config1");
    assertThat(defaultConfig.getVaultUrl()).isEqualTo(vaultConfig.getVaultUrl());
    assertThat(defaultConfig.isDefault()).isTrue();
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testNewVaultConfigIfUnavailable() {
    Account account = getAccount(AccountType.PAID);
    String accountId = account.getUuid();

    when(accountService.get(accountId)).thenReturn(account);
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(false);

    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setAccountId(accountId);

    try {
      vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
      fail("");
    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void vaultEncryptionWhileSaving() throws IllegalAccessException {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(savedAttribute.getValue()).isEqualTo(appDynamicsConfig);
    assertThat(((AppDynamicsConfig) savedAttribute.getValue()).getPassword()).isNull();
    assertThat(isBlank(((AppDynamicsConfig) savedAttribute.getValue()).getEncryptedPassword())).isFalse();

    Query<EncryptedData> query =
        wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(settingAttribute.getUuid());
    assertThat(query.count()).isEqualTo(1);
    EncryptedData encryptedData = query.get();
    assertThat(encryptedData.getKmsId()).isEqualTo(vaultConfig.getUuid());
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
    assertThat(query.count()).isEqualTo(numOfEncRecords + 1);

    encryptionService.decrypt((EncryptableSetting) savedAttribute.getValue(),
        secretManager.getEncryptionDetails((EncryptableSetting) savedAttribute.getValue(), workflowExecutionId, appId),
        false);

    AppDynamicsConfig value = (AppDynamicsConfig) savedAttribute.getValue();
    assertThat(String.valueOf(value.getPassword())).isEqualTo(password);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void vaultEncryptionSaveMultiple() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    int numOfSettingAttributes = 5;
    List<SettingAttribute> settingAttributes =
        SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
    wingsPersistence.save(settingAttributes);

    Collection<SecretManagerConfig> vaultConfigs =
        secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.VAULT, true);
    assertThat(vaultConfigs).hasSize(1);
    assertThat(vaultConfigs.iterator().next().getNumOfEncryptedValue()).isEqualTo(numOfSettingAttributes);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void vaultEncryptionUpdateObject() throws IllegalAccessException {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(savedAttribute).isEqualTo(settingAttribute);
    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncRecords + 1);

    ((AppDynamicsConfig) savedAttribute.getValue()).setUsername(UUID.randomUUID().toString());
    ((AppDynamicsConfig) savedAttribute.getValue()).setPassword(UUID.randomUUID().toString().toCharArray());
    User user1 = User.Builder.anUser().email(UUID.randomUUID().toString()).name("user1").build();
    wingsPersistence.save(user1);
    UserThreadLocal.set(user1);
    wingsPersistence.save(savedAttribute);

    SettingAttribute updatedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(updatedAttribute).isEqualTo(savedAttribute);
    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncRecords + 1);

    Query<EncryptedData> query =
        wingsPersistence.createQuery(EncryptedData.class).field(PARENT_ID_KEY).hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);
    EncryptedData encryptedData = query.get();
    assertThat(encryptedData.getKmsId()).isEqualTo(vaultConfig.getUuid());

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

    User user2 = User.Builder.anUser().email(UUID.randomUUID().toString()).name("user2").build();
    wingsPersistence.save(user2);
    UserThreadLocal.set(user2);

    ((AppDynamicsConfig) savedAttribute.getValue()).setUsername(UUID.randomUUID().toString());
    ((AppDynamicsConfig) savedAttribute.getValue()).setPassword(UUID.randomUUID().toString().toCharArray());
    wingsPersistence.save(savedAttribute);

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
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNoOnDemandMigrationOnSecretUpdate() {
    if (isKmsEnabled) {
      return;
    }

    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    String vaultId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(savedAttribute).isEqualTo(settingAttribute);

    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                                     .field(PARENT_ID_KEY)
                                     .hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);

    EncryptedData encryptedData = query.get();
    assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
    assertThat(encryptedData.getKmsId()).isEqualTo(vaultId);

    // Change the default to KMS
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsService.saveKmsConfig(accountId, kmsConfig);

    assertThat(secretManager.getEncryptionType(accountId)).isEqualTo(EncryptionType.KMS);

    String updatedAppId = UUID.randomUUID().toString();
    wingsPersistence.updateField(SettingAttribute.class, savedAttributeId, SettingAttributeKeys.appId, updatedAppId);

    SettingAttribute updatedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(updatedAttribute.getAppId()).isEqualTo(updatedAppId);

    query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                .field(PARENT_ID_KEY)
                .hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);

    encryptedData = query.get();
    assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
    assertThat(encryptedData.getKmsId()).isEqualTo(vaultId);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void vaultEncryptionUpdateFieldSettingAttribute() throws IllegalAccessException {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    String password = UUID.randomUUID().toString();
    final AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    String savedAttributeId = wingsPersistence.save(settingAttribute);
    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(savedAttribute).isEqualTo(settingAttribute);
    assertThat(wingsPersistence.createQuery(SettingAttribute.class, excludeAuthority).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class, excludeAuthority).count())
        .isEqualTo(numOfEncRecords + 1);

    String updatedAppId = UUID.randomUUID().toString();
    wingsPersistence.updateField(SettingAttribute.class, savedAttributeId, "appId", updatedAppId);

    SettingAttribute updatedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    assertThat(updatedAttribute.getAppId()).isEqualTo(updatedAppId);
    savedAttribute.setAppId(updatedAppId);
    assertThat(updatedAttribute).isEqualTo(savedAttribute);
    assertThat(wingsPersistence.createQuery(SettingAttribute.class, excludeAuthority).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class, excludeAuthority).count())
        .isEqualTo(numOfEncRecords + 1);

    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                                     .field(PARENT_ID_KEY)
                                     .hasThisOne(savedAttributeId);
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

    query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                .field(PARENT_ID_KEY)
                .hasThisOne(savedAttributeId);
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

    newAppDynamicsConfig.setPassword(null);
    assertThat(updatedAttribute.getValue()).isEqualTo(newAppDynamicsConfig);
    newAppDynamicsConfig.setPassword(newPassWord.toCharArray());

    assertThat(wingsPersistence.createQuery(SettingAttribute.class, excludeAuthority).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncRecords + 1);

    User user2 = User.Builder.anUser().email(UUID.randomUUID().toString()).name(UUID.randomUUID().toString()).build();
    wingsPersistence.save(user2);
    UserThreadLocal.set(user2);
    wingsPersistence.updateFields(SettingAttribute.class, savedAttributeId, keyValuePairs);

    query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                .field(PARENT_ID_KEY)
                .hasThisOne(savedAttributeId);
    assertThat(query.count()).isEqualTo(1);
    encryptedData = query.get();

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

    // test decryption
    savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    AppDynamicsConfig savedConfig = (AppDynamicsConfig) savedAttribute.getValue();
    assertThat(savedConfig.getPassword()).isNull();
    encryptionService.decrypt(
        savedConfig, secretManager.getEncryptionDetails(savedConfig, workflowExecutionId, appId), false);
    assertThat(String.valueOf(savedConfig.getPassword())).isEqualTo(newPassWord);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void vaultEncryptionSaveServiceVariable() throws IllegalAccessException {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

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
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncRecords + 1);

    secretName = UUID.randomUUID().toString();
    secretValue = UUID.randomUUID().toString();
    secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    secretId = secretManager.saveSecretText(accountId, secretText, true);

    Map<String, Object> keyValuePairs = new HashMap<>();
    keyValuePairs.put("name", "newName");
    keyValuePairs.put("type", ServiceVariableType.ENCRYPTED_TEXT);
    keyValuePairs.put("value", secretId.toCharArray());
    wingsPersistence.updateFields(ServiceVariable.class, savedAttributeId, keyValuePairs);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncRecords + 2);

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
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void vaultEncryptionSaveServiceVariableTemplate() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    String secretName = UUID.randomUUID().toString();
    String secretValue = UUID.randomUUID().toString();
    SecretText secretText = SecretText.builder().name(secretName).kmsId(kmsId).value(secretValue).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

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
    assertThat(wingsPersistence.createQuery(ServiceVariable.class).count()).isEqualTo(1);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncRecords + 1);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void vaultEncryptionDeleteSettingAttribute() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    int numOfSettingAttributes = 5;
    List<SettingAttribute> settingAttributes =
        SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
    wingsPersistence.save(settingAttributes);
    secretManagementTestHelper.validateSettingAttributes(settingAttributes, numOfEncRecords + numOfSettingAttributes);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void vaultEncryptionDeleteSettingAttributeQueryUuid() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    int numOfSettingAttributes = 5;
    List<SettingAttribute> settingAttributes =
        SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
    for (SettingAttribute settingAttribute : settingAttributes) {
      wingsPersistence.save(settingAttribute);
    }
    secretManagementTestHelper.validateSettingAttributes(settingAttributes, numOfEncRecords + numOfSettingAttributes);

    settingAttributes = SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
    wingsPersistence.save(settingAttributes);
    secretManagementTestHelper.validateSettingAttributes(
        settingAttributes, numOfEncRecords + 2 * numOfSettingAttributes);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Repeat(times = 3, successes = 1)
  @Category(UnitTests.class)
  public void transitionVault() throws InterruptedException, IllegalAccessException {
    Thread listenerThread = startTransitionListener();
    try {
      VaultConfig fromConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
      vaultService.saveOrUpdateVaultConfig(accountId, fromConfig, true);

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

      Query<EncryptedData> query =
          wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId);
      List<EncryptedData> encryptedData = new ArrayList<>();
      assertThat(query.count()).isEqualTo(numOfEncRecords + numOfSettingAttributes);
      for (EncryptedData data : query.asList()) {
        if (accountId.equals(data.getKmsId()) || data.getType() == SettingVariableTypes.VAULT) {
          continue;
        }
        encryptedData.add(data);
        assertThat(data.getKmsId()).isEqualTo(fromConfig.getUuid());
        assertThat(data.getAccountId()).isEqualTo(accountId);
      }

      assertThat(encryptedData).hasSize(numOfSettingAttributes);

      VaultConfig toConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
      vaultService.saveOrUpdateVaultConfig(accountId, toConfig, true);

      secretManager.transitionSecrets(accountId, EncryptionType.VAULT, fromConfig.getUuid(), EncryptionType.VAULT,
          toConfig.getUuid(), new HashMap<>(), new HashMap<>());
      waitTillEventsProcessed(30);
      query = wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId);

      assertThat(query.count()).isEqualTo(numOfEncRecords + 1 + numOfSettingAttributes);
      encryptedData = new ArrayList<>();
      for (EncryptedData data : query.asList()) {
        if (accountId.equals(data.getKmsId()) || data.getType() == SettingVariableTypes.VAULT) {
          continue;
        }
        encryptedData.add(data);
        assertThat(data.getKmsId()).isEqualTo(toConfig.getUuid());
        assertThat(data.getAccountId()).isEqualTo(accountId);
      }
      assertThat(encryptedData).hasSize(numOfSettingAttributes);

      // read the values and compare
      PageResponse<SettingAttribute> attributeQuery = wingsPersistence.query(
          SettingAttribute.class, aPageRequest().addFilter("accountId", Operator.EQ, accountId).build());
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
  public void transitionVault_shouldFailReadOnly() {
    VaultConfig fromConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    fromConfig.setDefault(false);
    fromConfig.setReadOnly(true);
    String fromConfigId = vaultService.saveOrUpdateVaultConfig(accountId, fromConfig, true);
    assertThat(fromConfigId).isNotNull();
    fromConfig.setUuid(fromConfigId);

    VaultConfig toConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    toConfig.setDefault(false);
    toConfig.setReadOnly(false);
    String toConfigId = vaultService.saveOrUpdateVaultConfig(accountId, toConfig, true);
    assertThat(toConfigId).isNotNull();
    toConfig.setUuid(toConfigId);

    try {
      secretManager.transitionSecrets(accountId, EncryptionType.VAULT, fromConfig.getUuid(), EncryptionType.VAULT,
          toConfig.getUuid(), new HashMap<>(), new HashMap<>());
      fail("Should not have been able to transition secrets from read only vault");
    } catch (SecretManagementException e) {
      log.info("Expected error", e);
      assertThat(e.getCode()).isEqualTo(UNSUPPORTED_OPERATION_EXCEPTION);
    }

    fromConfig.setReadOnly(false);
    vaultService.saveOrUpdateVaultConfig(accountId, fromConfig, true);
    toConfig.setReadOnly(true);
    vaultService.saveOrUpdateVaultConfig(accountId, toConfig, true);

    try {
      secretManager.transitionSecrets(accountId, EncryptionType.VAULT, fromConfig.getUuid(), EncryptionType.VAULT,
          toConfig.getUuid(), new HashMap<>(), new HashMap<>());
      fail("Should not have been able to transition secrets to read only vault");
    } catch (SecretManagementException e) {
      log.info("Expected error", e);
      assertThat(e.getCode()).isEqualTo(UNSUPPORTED_OPERATION_EXCEPTION);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void transitionAndDeleteVault() throws InterruptedException, IllegalAccessException {
    Thread listenerThread = startTransitionListener();
    try {
      VaultConfig fromConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
      vaultService.saveOrUpdateVaultConfig(accountId, fromConfig, true);

      int numOfSettingAttributes = 5;
      List<SettingAttribute> settingAttributes =
          SecretManagementTestHelper.getSettingAttributes(accountId, numOfSettingAttributes);
      wingsPersistence.save(settingAttributes);

      Query<EncryptedData> query =
          wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId);
      List<EncryptedData> encryptedDataList = new ArrayList<>();
      assertThat(query.count()).isEqualTo(numOfEncRecords + numOfSettingAttributes);
      for (EncryptedData data : query.asList()) {
        if (accountId.equals(data.getKmsId()) || data.getType() == SettingVariableTypes.VAULT) {
          continue;
        }
        encryptedDataList.add(data);
        assertThat(data.getKmsId()).isEqualTo(fromConfig.getUuid());
        assertThat(data.getAccountId()).isEqualTo(accountId);
      }

      assertThat(encryptedDataList).hasSize(numOfSettingAttributes);

      VaultConfig toConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
      vaultService.saveOrUpdateVaultConfig(accountId, toConfig, true);

      assertThat(secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.VAULT, true).size())
          .isEqualTo(2);
      try {
        vaultService.deleteVaultConfig(accountId, fromConfig.getUuid());
        fail("Was able to delete vault which has reference in encrypted secrets");
      } catch (WingsException e) {
        // expected
      }

      secretManager.transitionSecrets(accountId, EncryptionType.VAULT, fromConfig.getUuid(), EncryptionType.VAULT,
          toConfig.getUuid(), new HashMap<>(), new HashMap<>());
      waitTillEventsProcessed(30);
      vaultService.deleteVaultConfig(accountId, fromConfig.getUuid());
      assertThat(secretManagerConfigService.listSecretManagersByType(accountId, EncryptionType.VAULT, true).size())
          .isEqualTo(1);

      query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority);
      assertThat(query.count()).isEqualTo(numOfEncRecords + numOfSettingAttributes);

      encryptedDataList.clear();
      for (EncryptedData data : query.asList()) {
        if (accountId.equals(data.getKmsId()) || data.getType() == SettingVariableTypes.VAULT) {
          continue;
        }
        encryptedDataList.add(data);
        assertThat(data.getKmsId()).isEqualTo(toConfig.getUuid());
        assertThat(data.getAccountId()).isEqualTo(accountId);
      }
      assertThat(encryptedDataList.size()).isEqualTo(numOfSettingAttributes);
    } finally {
      stopTransitionListener(listenerThread);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void transitionFromKmsToVault() throws InterruptedException, IllegalAccessException {
    if (isKmsEnabled) {
      return;
    }

    KmsConfig fromConfig = secretManagementTestHelper.getKmsConfig();
    kmsService.saveKmsConfig(accountId, fromConfig);

    Thread listenerThread = startTransitionListener();
    try {
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

      Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                                       .filter(EncryptedDataKeys.type, SettingVariableTypes.SECRET_TEXT);
      assertThat(query.count()).isEqualTo(numOfSettingAttributes);
      for (EncryptedData data : query.asList()) {
        assertThat(data.getKmsId()).isEqualTo(fromConfig.getUuid());
        assertThat(data.getAccountId()).isEqualTo(accountId);
        assertThat(data.getEncryptionType()).isEqualTo(EncryptionType.KMS);
      }

      VaultConfig toConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
      vaultService.saveOrUpdateVaultConfig(accountId, toConfig, true);

      secretManager.transitionSecrets(accountId, EncryptionType.KMS, fromConfig.getUuid(), EncryptionType.VAULT,
          toConfig.getUuid(), new HashMap<>(), new HashMap<>());
      waitTillEventsProcessed(30);
      query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                  .filter(EncryptedDataKeys.type, SettingVariableTypes.SECRET_TEXT);

      assertThat(query.count()).isEqualTo(numOfSettingAttributes);
      for (EncryptedData data : query.asList()) {
        assertThat(data.getKmsId()).isEqualTo(toConfig.getUuid());
        assertThat(data.getAccountId()).isEqualTo(accountId);
        assertThat(data.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
      }

      secretManager.transitionSecrets(accountId, EncryptionType.VAULT, toConfig.getUuid(), EncryptionType.KMS,
          fromConfig.getUuid(), new HashMap<>(), new HashMap<>());
      waitTillEventsProcessed(30);
      query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                  .filter(EncryptedDataKeys.type, SettingVariableTypes.SECRET_TEXT);

      assertThat(query.count()).isEqualTo(numOfSettingAttributes);
      for (EncryptedData data : query.asList()) {
        assertThat(data.getKmsId()).isEqualTo(fromConfig.getUuid());
        assertThat(data.getAccountId()).isEqualTo(accountId);
        assertThat(data.getEncryptionType()).isEqualTo(EncryptionType.KMS);
      }

      // read the values and compare
      PageResponse<SettingAttribute> attributeQuery =
          wingsPersistence.query(SettingAttribute.class, aPageRequest().build());
      assertThat(attributeQuery).hasSize(numOfSettingAttributes);
      for (SettingAttribute settingAttribute : attributeQuery) {
        assertThat(settingAttribute).isEqualTo(encryptedEntities.get(settingAttribute.getUuid()));
      }
    } finally {
      stopTransitionListener(listenerThread);
    }
  }

  private void waitTillEventsProcessed(long seconds) {
    final long startTime = System.currentTimeMillis();
    long remainingCount = wingsPersistence.createQuery(MigrateSecretTask.class, excludeAuthority).count();
    while (remainingCount > 0 && System.currentTimeMillis() < startTime + TimeUnit.SECONDS.toMillis(seconds)) {
      log.info("remaining secrets: " + remainingCount);
      remainingCount = wingsPersistence.createQuery(MigrateSecretTask.class, excludeAuthority).count();
      Morpheus.sleep(ofSeconds(1));
    }

    if (remainingCount != 0) {
      throw new RuntimeException(
          "could not process all the events in " + seconds + " seconds. Remaining secrets: " + remainingCount);
    }

    Morpheus.sleep(ofSeconds(2));
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveConfigFileWithEncryption() throws IOException, IllegalAccessException {
    final long seed = System.currentTimeMillis();
    log.info("seed: " + seed);
    Random r = new Random(seed);
    VaultConfig fromConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, fromConfig, true);

    String secretName = UUID.randomUUID().toString();
    File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
    SecretFile secretFile = SecretFile.builder()
                                .inheritScopesFromSM(true)
                                .name(secretName)
                                .kmsId(kmsId)
                                .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToSave)))
                                .build();
    String secretFileId = secretManager.saveSecretFile(accountId, secretFile);

    String encryptedUuid = wingsPersistence.createQuery(EncryptedData.class)
                               .filter(EncryptedDataKeys.type, CONFIG_FILE)
                               .filter(EncryptedDataKeys.accountId, accountId)
                               .get()
                               .getUuid();

    Service service = Service.builder().name(UUID.randomUUID().toString()).appId(appId).build();
    wingsPersistence.save(service);

    Activity activity = Activity.builder().workflowExecutionId(workflowExecutionId).environmentId(envId).build();
    activity.setAppId(appId);
    wingsPersistence.save(activity);

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

    configFile.setAccountId(accountId);
    configFile.setName(UUID.randomUUID().toString());
    configFile.setFileName(UUID.randomUUID().toString());
    configFile.setAppId(appId);

    String configFileId = configService.save(configFile, null);
    File download = configService.download(appId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToSave, Charset.defaultCharset()));
    assertThat(wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId).count())
        .isEqualTo(numOfEncRecords + 1);

    List<EncryptedData> encryptedFileData = wingsPersistence.createQuery(EncryptedData.class)
                                                .filter(EncryptedDataKeys.type, CONFIG_FILE)
                                                .filter(EncryptedDataKeys.accountId, accountId)
                                                .asList();
    assertThat(encryptedFileData).hasSize(1);
    assertThat(isEmpty(encryptedFileData.get(0).getParents())).isFalse();
    // test update
    String newSecretName = UUID.randomUUID().toString();
    File fileToUpdate = new File("400-rest/src/test/resources/encryption/file_to_update.txt");
    SecretFile secretFileUpdate = SecretFile.builder()
                                      .inheritScopesFromSM(true)
                                      .name(newSecretName)
                                      .kmsId(kmsId)
                                      .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToUpdate)))
                                      .build();
    secretManager.updateSecretFile(accountId, encryptedUuid, secretFileUpdate);

    download = configService.download(appId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToUpdate, Charset.defaultCharset()));
    assertThat(wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId).count())
        .isEqualTo(numOfEncRecords + 1);

    encryptedFileData = wingsPersistence.createQuery(EncryptedData.class)
                            .filter(EncryptedDataKeys.accountId, accountId)
                            .filter(EncryptedDataKeys.type, CONFIG_FILE)
                            .asList();
    assertThat(encryptedFileData).hasSize(1);
    assertThat(isEmpty(encryptedFileData.get(0).getParents())).isFalse();

    int numOfAccess = 7;
    for (int i = 0; i < numOfAccess; i++) {
      configService.downloadForActivity(appId, configFileId, activity.getUuid());
    }
    List<SecretUsageLog> usageLogs =
        secretManager.getUsageLogs(aPageRequest().build(), accountId, encryptedUuid, CONFIG_FILE);
    assertThat(usageLogs).hasSize(numOfAccess);

    for (SecretUsageLog usageLog : usageLogs) {
      assertThat(usageLog.getWorkflowExecutionName()).isEqualTo(workflowName);
      assertThat(usageLog.getAccountId()).isEqualTo(accountId);
      assertThat(usageLog.getEnvId()).isEqualTo(envId);
      assertThat(usageLog.getAppId()).isEqualTo(appId);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void reuseYamlPasswordVaultEncryption() throws IllegalAccessException {
    VaultConfig fromConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, fromConfig, true);

    int numOfSettingAttributes = 5;
    String password = "password";
    Set<String> attributeIds = new HashSet<>();
    AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, password);
    SettingAttribute settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

    attributeIds.add(wingsPersistence.save(settingAttribute));

    // yamlRef will be an URL format like: "hashicorpvault://vaultManagerName/harness/APP_DYNAMICS/...#value" for vault
    // based secrets
    String yamlRef =
        secretManager.getEncryptedYamlRef(appDynamicsConfig.getAccountId(), appDynamicsConfig.getEncryptedPassword());
    assertThat(yamlRef).isNotNull();
    assertThat(yamlRef.startsWith(EncryptionType.VAULT.getYamlName() + "://" + fromConfig.getName() + "/harness/"))
        .isTrue();
    assertThat(yamlRef.contains("#value")).isTrue();

    for (int i = 1; i < numOfSettingAttributes; i++) {
      appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, null, yamlRef);
      settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);

      attributeIds.add(wingsPersistence.save(settingAttribute));
    }

    assertThat(wingsPersistence.createQuery(SettingAttribute.class).count()).isEqualTo(numOfSettingAttributes);
    assertThat(wingsPersistence.createQuery(EncryptedData.class).count()).isEqualTo(numOfEncRecords + 1);

    List<EncryptedData> encryptedDatas = wingsPersistence.createQuery(EncryptedData.class)
                                             .filter(EncryptedDataKeys.encryptionType, EncryptionType.VAULT)
                                             .filter(EncryptedDataKeys.accountId, accountId)
                                             .asList();
    EncryptedData encryptedData = encryptedDatas.get(0);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
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
      encryptedDatas = wingsPersistence.createQuery(EncryptedData.class)
                           .filter(EncryptedDataKeys.accountId, accountId)
                           .filter(EncryptedDataKeys.encryptionType, EncryptionType.VAULT)
                           .asList();
      assertThat(encryptedDatas).hasSize(1);
      encryptedData = encryptedDatas.get(0);
      assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
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
  public void vaultSecretManager_Crud_shouldGenerate_Audit() {
    if (isKmsEnabled) {
      // Doesn't make any difference to run with KMS enabled...
      return;
    }

    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken();
    vaultConfig.setAccountId(accountId);

    String secretManagerId = vaultService.saveOrUpdateVaultConfig(accountId, kryoSerializer.clone(vaultConfig), true);
    verify(auditServiceHelper)
        .reportForAuditingUsingAccountId(eq(accountId), eq(null), any(VaultConfig.class), eq(Event.Type.CREATE));

    vaultConfig.setUuid(secretManagerId);
    vaultConfig.setDefault(false);
    vaultConfig.setName(vaultConfig.getName() + "_Updated");
    vaultService.saveOrUpdateVaultConfig(accountId, kryoSerializer.clone(vaultConfig), true);
    verify(auditServiceHelper)
        .reportForAuditingUsingAccountId(
            eq(accountId), any(VaultConfig.class), any(VaultConfig.class), eq(Event.Type.UPDATE));

    vaultService.deleteVaultConfig(accountId, secretManagerId);
    verify(auditServiceHelper).reportDeleteForAuditingUsingAccountId(eq(accountId), any(VaultConfig.class));
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void secretText_createdBeforeLocalEncryption_shouldBeReturned() throws Exception {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken();
    vaultConfig.setAccountId(accountId);
    vaultService.saveOrUpdateVaultConfig(accountId, kryoSerializer.clone(vaultConfig), true);

    String secretName = UUID.randomUUID().toString();
    SecretText secretText = SecretText.builder()
                                .name(secretName)
                                .kmsId(kmsId)
                                .value("MySecret")
                                .usageRestrictions(new UsageRestrictions())
                                .build();
    secretManager.saveSecretText(accountId, secretText, false);

    PageRequest<EncryptedData> pageRequest =
        aPageRequest()
            .withLimit(UNLIMITED)
            .addFilter("accountId", Operator.EQ, accountId)
            .addFilter("type", Operator.IN, new Object[] {SettingVariableTypes.SECRET_TEXT.name()})
            .build();

    // The above created secret text should be returned by the list secrets call.
    PageResponse<EncryptedData> response = secretManager.listSecrets(accountId, pageRequest, null, null, true, false);
    assertThat(response.getResponse()).isNotEmpty();
    assertThat(response.getResponse().size()).isEqualTo(1);
    EncryptedData encryptedData = response.getResponse().get(0);
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptedBy()).isNotEmpty();

    // Enable local encryption on this account.
    Account account = accountService.get(accountId);
    account.setLocalEncryptionEnabled(true);
    wingsPersistence.save(account);

    // The old secret should still be returned by the list secrets call.
    response = secretManager.listSecrets(accountId, pageRequest, null, null, true, false);
    assertThat(response.getResponse()).isNotEmpty();
    assertThat(response.getResponse().size()).isEqualTo(1);

    // But the secret manager field will be null instead.
    encryptedData = response.getResponse().get(0);
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptedBy()).isNotEmpty();
  }

  private static WinRmConnectionAttributes getWinRmConnectionAttribute(String accountId, String password) {
    return WinRmConnectionAttributes.builder()
        .accountId(accountId)
        .password(password.toCharArray())
        .authenticationScheme(AuthenticationScheme.NTLM)
        .port(5164)
        .skipCertChecks(true)
        .useSSL(true)
        .username("mark.lu")
        .build();
  }

  private static SettingAttribute getSettingAttribute(WinRmConnectionAttributes winRmConnectionAttributes) {
    return SettingAttribute.Builder.aSettingAttribute()
        .withAccountId(winRmConnectionAttributes.getAccountId())
        .withValue(winRmConnectionAttributes)
        .withAppId(UUID.randomUUID().toString())
        .withCategory(SettingCategory.SETTING)
        .withEnvId(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .build();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void WinRmConnections_shouldBeReturned_in_listSettingAttributes() {
    VaultConfig fromConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultService.saveOrUpdateVaultConfig(accountId, fromConfig, true);

    final String password = UUID.randomUUID().toString();
    WinRmConnectionAttributes winRmConnectionAttributes = getWinRmConnectionAttribute(accountId, password);
    SettingAttribute settingAttribute = getSettingAttribute(winRmConnectionAttributes);
    SettingAttribute savedAttribute = settingsService.save(settingAttribute);

    SettingAttribute savedSettingAttribute = wingsPersistence.get(SettingAttribute.class, savedAttribute.getUuid());
    assertThat(savedSettingAttribute).isNotNull();
    assertThat(savedSettingAttribute.getCategory()).isEqualTo(SettingCategory.SETTING);

    Collection<SettingAttribute> encryptedValues =
        secretManagementResource.listEncryptedSettingAttributes(accountId, SettingCategory.SETTING.name())
            .getResource();
    assertThat(encryptedValues).isNotEmpty();
    assertThat(encryptedValues.size()).isEqualTo(1);
    assertThat(encryptedValues.iterator().next().getCategory()).isEqualTo(SettingCategory.SETTING);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void updateVaultConfig_fromToken_ToAppRole_shouldSucceed() {
    if (isKmsEnabled) {
      return;
    }

    String authToken = "authToken";
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(authToken);
    String vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
    vaultConfig = vaultService.getVaultConfig(accountId, vaultConfigId);
    assertThat(vaultConfig.getAuthToken()).isEqualTo(authToken);

    String updatedToken = "updatedToken";
    String appRoleId = "appRoleId";
    String secretId = "secretId";

    vaultConfig.setAppRoleId(appRoleId);
    vaultConfig.setSecretId(secretId);
    vaultConfig.setAuthToken(SECRET_MASK);

    VaultAppRoleLoginResult vaultAppRoleLoginResult = mock(VaultAppRoleLoginResult.class);
    when(secretManagementDelegateService.appRoleLogin(any())).thenReturn(vaultAppRoleLoginResult);
    when(vaultAppRoleLoginResult.getClientToken()).thenReturn(updatedToken);

    vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
    vaultConfig = vaultService.getVaultConfig(accountId, vaultConfigId);
    assertThat(vaultConfig.getAuthToken()).isEqualTo(updatedToken);
    assertThat(vaultConfig.getAppRoleId()).isEqualTo(appRoleId);
    assertThat(vaultConfig.getSecretId()).isEqualTo(secretId);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void updateVaultConfig_withAppRole_shouldSucceed() {
    if (isKmsEnabled) {
      return;
    }

    VaultAppRoleLoginResult vaultAppRoleLoginResult = mock(VaultAppRoleLoginResult.class);
    when(secretManagementDelegateService.appRoleLogin(any())).thenReturn(vaultAppRoleLoginResult);
    String appRoleId = "appRoleId";
    String secretId = "secretId";
    String authToken = "authToken";

    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole(appRoleId, secretId);
    when(vaultAppRoleLoginResult.getClientToken()).thenReturn(authToken);
    String vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
    vaultConfig = vaultService.getVaultConfig(accountId, vaultConfigId);
    assertThat(vaultConfig.getAuthToken()).isEqualTo(authToken);
    assertThat(vaultConfig.getAppRoleId()).isEqualTo(appRoleId);
    assertThat(vaultConfig.getSecretId()).isEqualTo(secretId);

    String updatedSecretId = "updatedSecretId";
    String updatedToken = "updatedToken";

    vaultConfig.setSecretId(updatedSecretId);
    vaultConfig.setAuthToken(SECRET_MASK);
    when(vaultAppRoleLoginResult.getClientToken()).thenReturn(updatedToken);
    vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
    vaultConfig = vaultService.getVaultConfig(accountId, vaultConfigId);
    assertThat(vaultConfig.getAuthToken()).isEqualTo(updatedToken);
    assertThat(vaultConfig.getAppRoleId()).isEqualTo(appRoleId);
    assertThat(vaultConfig.getSecretId()).isEqualTo(updatedSecretId);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void appRoleLoginRenewal_shouldBeSuccessful() {
    if (isKmsEnabled) {
      return;
    }

    long currentTime = System.currentTimeMillis();
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole("appRoleId", "secretId");
    vaultConfig.setAccountId(accountId);
    String initialToken = "initialToken";
    VaultAppRoleLoginResult vaultAppRoleLoginResult = mock(VaultAppRoleLoginResult.class);
    when(secretManagementDelegateService.appRoleLogin(any())).thenReturn(vaultAppRoleLoginResult);
    when(vaultAppRoleLoginResult.getClientToken()).thenReturn(initialToken);
    String vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
    vaultConfig = vaultService.getVaultConfig(accountId, vaultConfigId);
    assertThat(vaultConfig.getAuthToken()).isEqualTo(initialToken);

    String renewedToken = "renewedToken";
    when(vaultAppRoleLoginResult.getClientToken()).thenReturn(renewedToken);
    VaultConfig encryptedVaultConfig = (VaultConfig) wingsPersistence.get(SecretManagerConfig.class, vaultConfigId);
    vaultService.renewAppRoleClientToken(encryptedVaultConfig);
    vaultConfig = vaultService.getVaultConfig(accountId, vaultConfigId);
    assertThat(vaultConfig.getAuthToken()).isEqualTo(renewedToken);
    assertThat(vaultConfig.getRenewedAt()).isGreaterThan(currentTime);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenewToken_shouldSucceed() {
    if (isKmsEnabled) {
      return;
    }

    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken();
    vaultConfig.setAccountId(accountId);
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    VaultConfig savedVaultConfig = wingsPersistence.get(VaultConfig.class, vaultConfig.getUuid());
    assertThat(savedVaultConfig.getRenewedAt()).isEqualTo(0);

    long currentTime = System.currentTimeMillis();
    vaultService.renewToken(savedVaultConfig);
    savedVaultConfig = wingsPersistence.get(VaultConfig.class, vaultConfig.getUuid());
    assertThat(savedVaultConfig.getRenewedAt()).isGreaterThanOrEqualTo(currentTime);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void listSecretEngines_shouldSucceed() {
    if (isKmsEnabled) {
      return;
    }

    List<SecretEngineSummary> expectedSecretEngines = new ArrayList<>();
    SecretEngineSummary secretEngineSummary1 =
        SecretEngineSummary.builder().name("secret").version(2).type("kv").build();
    SecretEngineSummary secretEngineSummary2 =
        SecretEngineSummary.builder().name("harness-test").version(1).type("kv").build();
    expectedSecretEngines.add(secretEngineSummary1);
    expectedSecretEngines.add(secretEngineSummary2);
    when(secretManagementDelegateService.listSecretEngines(any())).thenReturn(expectedSecretEngines);

    String authToken = "authToken";
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(authToken);
    String vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
    vaultConfig = vaultService.getVaultConfig(accountId, vaultConfigId);
    vaultConfig.setAuthToken(SECRET_MASK);
    vaultConfig.setSecretId(SECRET_MASK);
    List<SecretEngineSummary> secretEngines = vaultService.listSecretEngines(vaultConfig);
    assertThat(secretEngines).isNotNull();
    assertThat(secretEngines.size()).isEqualTo(2);
    assertThat(secretEngines.get(0).getName()).isEqualTo(secretEngineSummary1.getName());
    assertThat(secretEngines.get(0).getVersion()).isEqualTo(secretEngineSummary1.getVersion());
    assertThat(secretEngines.get(1).getName()).isEqualTo(secretEngineSummary2.getName());
    assertThat(secretEngines.get(1).getVersion()).isEqualTo(secretEngineSummary2.getVersion());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void createTemplatizedVault_shouldSucceed() {
    if (isKmsEnabled) {
      return;
    }

    // should save a valid configuration with appRoleId and secretId as templatized
    String temporaryAppRoleId = "appRoleId";
    String temporarySecretId = "secretId";
    String temporaryToken = "token";
    VaultConfig vaultConfig =
        secretManagementTestHelper.getVaultConfigWithAppRole(temporaryAppRoleId, temporarySecretId);
    vaultConfig.setDefault(false);
    vaultConfig.setTemplatizedFields(Lists.newArrayList("appRoleId", "secretId"));

    VaultAppRoleLoginResult vaultAppRoleLoginResult = mock(VaultAppRoleLoginResult.class);
    when(secretManagementDelegateService.appRoleLogin(any())).thenReturn(vaultAppRoleLoginResult);
    when(vaultAppRoleLoginResult.getClientToken()).thenReturn(temporaryToken);

    String vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
    vaultConfig = vaultService.getVaultConfig(accountId, vaultConfigId);

    assertThat(vaultConfig.getAuthToken()).isEqualTo(temporaryToken);
    assertThat(vaultConfig.getTemplatizedFields()).hasSize(2);
    assertThat(vaultConfig.getAppRoleId()).isEqualTo(temporaryAppRoleId);
    assertThat(vaultConfig.getSecretId()).isEqualTo(temporarySecretId);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void createTemplatizedVault_shouldFail() {
    if (isKmsEnabled) {
      return;
    }

    // should fail when trying to save a templatized SM as default
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole(null, "secretId");
    vaultConfig.setDefault(true);
    vaultConfig.setTemplatizedFields(Lists.newArrayList("appRoleId", "secretId"));
    try {
      vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
      fail("Should not save a templatized secret manager as default");
    } catch (InvalidRequestException ire) {
      assertThat(ire.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // should fail when trying to save a templatized SM with required fields not provided
    vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole(null, "secretId");
    vaultConfig.setDefault(false);
    vaultConfig.setTemplatizedFields(Lists.newArrayList("appRoleId", "secretId"));
    try {
      vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
      fail("Saving a vault configuration with invalid values should fail.");
    } catch (InvalidRequestException ire) {
      assertThat(ire.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // should fail when trying to save a templatized SM with required fields not provided
    vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole("", null);
    vaultConfig.setDefault(false);
    vaultConfig.setTemplatizedFields(Lists.newArrayList("appRoleId", "secretId"));
    try {
      vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
      fail("Saving a vault configuration with invalid values should fail.");
    } catch (InvalidRequestException ire) {
      assertThat(ire.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // should fail when trying to save a templatized SM with required fields not provided
    vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(null);
    vaultConfig.setDefault(false);
    vaultConfig.setTemplatizedFields(Lists.newArrayList("authToken"));
    try {
      vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
      fail("Saving a vault configuration with invalid values should fail.");
    } catch (InvalidRequestException ire) {
      assertThat(ire.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void updateTemplatizedVault_shouldSucceed() {
    if (isKmsEnabled) {
      return;
    }

    // should update vault with a valid configuration with appRoleId and secretId as templatized
    String temporaryAppRoleId = "tempAppRoleId";
    String temporarySecretId = "tempSecretId";
    String temporaryToken = "tempToken";
    VaultConfig templatizedVaultConfig =
        secretManagementTestHelper.getVaultConfigWithAppRole(temporaryAppRoleId, temporarySecretId);
    templatizedVaultConfig.setTemplatizedFields(Lists.newArrayList("appRoleId", "secretId"));
    templatizedVaultConfig.setDefault(false);

    VaultAppRoleLoginResult vaultAppRoleLoginResult = mock(VaultAppRoleLoginResult.class);
    when(secretManagementDelegateService.appRoleLogin(any())).thenReturn(vaultAppRoleLoginResult);
    when(vaultAppRoleLoginResult.getClientToken()).thenReturn(temporaryToken);

    String vaultConfigId = vaultService.saveOrUpdateVaultConfig(accountId, templatizedVaultConfig, true);
    templatizedVaultConfig = vaultService.getVaultConfig(accountId, vaultConfigId);

    assertThat(templatizedVaultConfig.getAppRoleId()).isEqualTo(temporaryAppRoleId);
    assertThat(templatizedVaultConfig.getSecretId()).isEqualTo(temporarySecretId);
    assertThat(templatizedVaultConfig.getAuthToken()).isEqualTo(temporaryToken);
    assertThat(templatizedVaultConfig.getTemplatizedFields()).hasSize(2);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void updateTemplatizedVault_shouldFail() {
    if (isKmsEnabled) {
      return;
    }

    String appRoleId = "appRoleId";
    String secretId = "secretId";

    // should fail when trying to update templatized SM which is default
    VaultConfig templatizedVaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole(appRoleId, secretId);
    templatizedVaultConfig.setTemplatizedFields(Lists.newArrayList("appRoleId", "secretId"));
    templatizedVaultConfig.setDefault(true);
    templatizedVaultConfig.setAccountId(accountId);
    try {
      vaultService.saveOrUpdateVaultConfig(accountId, templatizedVaultConfig, true);
      fail("Saving a vault configuration with invalid values should fail.");
    } catch (InvalidRequestException ire) {
      assertThat(ire.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // should fail when required fields not present
    templatizedVaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole(null, "secretId");
    templatizedVaultConfig.setDefault(false);
    templatizedVaultConfig.setTemplatizedFields(Lists.newArrayList("appRoleId", "secretId"));
    try {
      vaultService.saveOrUpdateVaultConfig(accountId, templatizedVaultConfig, true);
      fail("Saving a vault configuration with invalid values should fail.");
    } catch (InvalidRequestException ire) {
      assertThat(ire.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // should fail when required fields not present
    templatizedVaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole("", null);
    templatizedVaultConfig.setDefault(false);
    templatizedVaultConfig.setTemplatizedFields(Lists.newArrayList("appRoleId", "secretId"));
    try {
      vaultService.saveOrUpdateVaultConfig(accountId, templatizedVaultConfig, true);
      fail("Saving a vault configuration with invalid values should fail.");
    } catch (InvalidRequestException ire) {
      assertThat(ire.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // should fail when required fields not present
    templatizedVaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(null);
    templatizedVaultConfig.setDefault(false);
    templatizedVaultConfig.setTemplatizedFields(Lists.newArrayList("authToken"));
    try {
      vaultService.saveOrUpdateVaultConfig(accountId, templatizedVaultConfig, true);
      fail("Saving a vault configuration with invalid values should fail.");
    } catch (InvalidRequestException ire) {
      assertThat(ire.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void save_tokenBasedAuth_withRootToken_withNonZeroRenewInterval_shouldFail() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setAccountId(accountId);
    vaultConfig.setRenewalInterval(10L);
    when(secretManagementDelegateService.tokenLookup(any()))
        .thenReturn(VaultTokenLookupResult.builder().expiryTime(null).renewable(false).name("name").build());
    when(delegateTaskService.isTaskTypeSupportedByAllDelegates(accountId, "VAULT_TOKEN_LOOKUP")).thenReturn(true);
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(
        "The token used is a root token. Please set renewal interval as zero if you are using root token.");

    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, false);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void save_tokenBasedAuth_withNonRootToken_withNonRenewableToken_shouldFail() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken(VAULT_TOKEN);
    vaultConfig.setAccountId(accountId);
    vaultConfig.setRenewalInterval(10L);
    when(secretManagementDelegateService.tokenLookup(any()))
        .thenReturn(VaultTokenLookupResult.builder()
                        .expiryTime(UUIDGenerator.generateUuid())
                        .renewable(false)
                        .name("name")
                        .build());
    when(delegateTaskService.isTaskTypeSupportedByAllDelegates(accountId, "VAULT_TOKEN_LOOKUP")).thenReturn(true);
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(
        "The token used is a non-renewable token. Please set renewal interval as zero or use a renewable token.");
    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, false);
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
}
