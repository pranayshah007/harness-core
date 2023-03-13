/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS;
import static io.harness.security.encryption.EncryptionType.GCP_KMS;
import static io.harness.security.encryption.EncryptionType.LOCAL;

import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.service.impl.security.SecretManagerImpl.ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_VARIABLE_NAME;

import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretText;
import io.harness.beans.SecretUsageLog;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rule.Owner;
import io.harness.secrets.SecretsRBACService;
import io.harness.secrets.setupusage.SecretSetupUsage;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionType;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.Base;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.UsageRestrictions;
import software.wings.security.UsageRestrictions.AppEnvRestriction;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.security.GcpSecretsManagerService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._360_CG_MANAGER)
public class SecretManagerImplTest extends WingsBaseTest {
  private Account account;
  @Mock private AccountService accountService;
  @Mock private HarnessUserGroupService harnessUserGroupService;
  @Mock private FileService fileService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Inject @InjectMocks private GcpSecretsManagerService gcpSecretsManagerService;
  @Inject @InjectMocks private SecretManagerImpl secretManager;
  @Inject private SecretsRBACService secretsRBACService;
  @Inject private UsageRestrictionsService usageRestrictionsService;
  @Inject private WingsPersistence wingsPersistence;
  private String secretName = "secretName";
  private String secretValue = "secretValue";

  @Before
  public void setup() throws IllegalAccessException {
    account = getAccount(AccountType.PAID);
    account.setLocalEncryptionEnabled(false);
    wingsPersistence.save(account);
    List<Account> accounts = new ArrayList<>();
    accounts.add(account);
    User user = User.Builder.anUser()
                    .uuid("uuid")
                    .name("Hello")
                    .uuid(generateUuid())
                    .email("hello@harness.io")
                    .accounts(accounts)
                    .build();
    UserThreadLocal.set(user);
    FieldUtils.writeField(secretsRBACService, "appService", appService, true);
    FieldUtils.writeField(secretsRBACService, "envService", environmentService, true);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_saveSecretLocal() {
    Account newAccount = getAccount(AccountType.PAID);
    newAccount.setLocalEncryptionEnabled(true);
    String accountId = wingsPersistence.save(newAccount);
    SecretText secretText = SecretText.builder().name(secretName).value(secretValue).build();
    String secretId = secretManager.saveSecretUsingLocalMode(accountId, secretText);
    assertThat(secretId).isNotNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_canUseSecretsInAppAndEnv_OnlyAppIdandEnvId() {
    String appId1 = "appId1";
    String envId1 = "envId1";
    String appId2 = "appId2";
    String envId2 = "envId2";
    String appId3 = "appId3";
    String envId3 = "envId3";

    AppEnvRestriction appEnvRestriction1 =
        usageRestrictionsService.getDefaultUsageRestrictions(account.getUuid(), appId1, envId1)
            .getAppEnvRestrictions()
            .iterator()
            .next();
    AppEnvRestriction appEnvRestriction2 =
        usageRestrictionsService.getDefaultUsageRestrictions(account.getUuid(), appId2, envId2)
            .getAppEnvRestrictions()
            .iterator()
            .next();
    AppEnvRestriction appEnvRestriction3 =
        usageRestrictionsService.getDefaultUsageRestrictions(account.getUuid(), appId3, envId3)
            .getAppEnvRestrictions()
            .iterator()
            .next();

    UsageRestrictions usageRestrictions1 =
        UsageRestrictions.builder().appEnvRestrictions(Sets.newHashSet(appEnvRestriction1, appEnvRestriction2)).build();
    UsageRestrictions usageRestrictions2 =
        UsageRestrictions.builder().appEnvRestrictions(Sets.newHashSet(appEnvRestriction2, appEnvRestriction3)).build();

    EncryptedData encryptedData = EncryptedData.builder()
                                      .accountId(account.getUuid())
                                      .enabled(true)
                                      .kmsId(account.getUuid())
                                      .encryptionType(GCP_KMS)
                                      .encryptionKey("Dummy Key")
                                      .encryptedValue("Dummy Value".toCharArray())
                                      .base64Encoded(false)
                                      .name("Dummy record")
                                      .type(SettingVariableTypes.AWS)
                                      .usageRestrictions(usageRestrictions1)
                                      .build();

    String encryptedDataId1 = wingsPersistence.save(encryptedData);
    encryptedData.setUuid(null);
    encryptedData.setName(UUIDGenerator.generateUuid());
    encryptedData.setUsageRestrictions(usageRestrictions2);
    String encryptedDataId2 = wingsPersistence.save(encryptedData);

    Environment environment1 = mock(Environment.class);
    when(environment1.getUuid()).thenReturn(envId1);
    Environment environment2 = mock(Environment.class);
    when(environment2.getUuid()).thenReturn(envId2);
    Environment environment3 = mock(Environment.class);
    when(environment3.getUuid()).thenReturn(envId3);

    Map<String, List<Base>> appIdEnvMapForAccount = new HashMap<>();
    appIdEnvMapForAccount.put(appId1, Collections.singletonList(environment1));
    appIdEnvMapForAccount.put(appId2, Collections.singletonList(environment1));
    appIdEnvMapForAccount.put(appId3, Collections.singletonList(environment1));

    when(appService.getAppIdsByAccountId(account.getUuid())).thenReturn(Arrays.asList(appId1, appId2, appId3));
    when(environmentService.getAppIdEnvMap(any(), any())).thenReturn(appIdEnvMapForAccount);

    boolean canUseSecrets = secretManager.canUseSecretsInAppAndEnv(
        Sets.newHashSet(encryptedDataId1, encryptedDataId2, UUIDGenerator.generateUuid()), account.getUuid(), appId2,
        envId2);
    assertThat(canUseSecrets).isTrue();

    canUseSecrets = secretManager.canUseSecretsInAppAndEnv(
        Sets.newHashSet(encryptedDataId1, encryptedDataId2), account.getUuid(), appId1, envId1);
    assertThat(canUseSecrets).isFalse();

    canUseSecrets = secretManager.canUseSecretsInAppAndEnv(
        Sets.newHashSet(encryptedDataId1, encryptedDataId2), account.getUuid(), appId3, envId3);
    assertThat(canUseSecrets).isFalse();

    canUseSecrets =
        secretManager.canUseSecretsInAppAndEnv(Sets.newHashSet(encryptedDataId1), account.getUuid(), appId1, envId1);
    assertThat(canUseSecrets).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_canUseSecretsInAppAndEnv_OnlyAppIdandEnvId_shouldReturnTrue() {
    String encryptedDataId1 = UUIDGenerator.generateUuid();
    String encryptedDataId2 = UUIDGenerator.generateUuid();

    boolean canUseSecrets = secretManager.canUseSecretsInAppAndEnv(
        Sets.newHashSet(encryptedDataId1, encryptedDataId2), account.getUuid(), "appId2", "envId2");
    assertThat(canUseSecrets).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getSecretUsageForEncryptedText() {
    Account newAccount = getAccount(AccountType.PAID);
    String accountId = wingsPersistence.save(newAccount);
    newAccount.setUuid(accountId);
    EncryptedData encryptedData = EncryptedData.builder()
                                      .accountId(accountId)
                                      .enabled(true)
                                      .kmsId(accountId)
                                      .encryptionType(LOCAL)
                                      .encryptionKey("Dummy Key")
                                      .encryptedValue("Dummy Value".toCharArray())
                                      .base64Encoded(false)
                                      .name("Dummy record")
                                      .type(SettingVariableTypes.SECRET_TEXT)
                                      .build();
    String secretId = wingsPersistence.save(encryptedData);
    encryptedData.setUuid(secretId);

    EncryptedData encryptedData1 = EncryptedData.builder()
                                       .accountId(accountId)
                                       .enabled(true)
                                       .kmsId(accountId)
                                       .encryptionType(LOCAL)
                                       .encryptionKey("Dummy Key")
                                       .encryptedValue("Dummy Value".toCharArray())
                                       .base64Encoded(false)
                                       .name("Dummy record 1")
                                       .type(SettingVariableTypes.SECRET_TEXT)
                                       .build();
    String secretId1 = wingsPersistence.save(encryptedData1);
    encryptedData1.setUuid(secretId1);

    ServiceTemplate serviceTemplate = aServiceTemplate().build();
    String entityId = wingsPersistence.save(serviceTemplate);

    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .accountId(accountId)
                                          .envId(ENV_ID)
                                          .entityType(EntityType.SERVICE_TEMPLATE)
                                          .entityId(entityId)
                                          .templateId(entityId)
                                          .name(SERVICE_VARIABLE_NAME + "2")
                                          .type(ENCRYPTED_TEXT)
                                          .encryptedValue(secretId)
                                          .build();

    ServiceVariable serviceVariable1 = ServiceVariable.builder()
                                           .accountId(accountId)
                                           .envId(ENV_ID)
                                           .entityType(EntityType.SERVICE_TEMPLATE)
                                           .entityId(entityId)
                                           .entityId(entityId)
                                           .name(SERVICE_VARIABLE_NAME + "3")
                                           .type(ENCRYPTED_TEXT)
                                           .encryptedValue(secretId)
                                           .build();

    String serviceVariableId = wingsPersistence.save(serviceVariable);
    serviceVariable.setUuid(serviceVariableId);
    String serviceVariableId1 = wingsPersistence.save(serviceVariable1);
    serviceVariable1.setUuid(serviceVariableId1);

    encryptedData = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedData.getParents()).hasSize(2);
    encryptedData.addParent(new EncryptedDataParent(
        generateUuid(), SettingVariableTypes.SERVICE_VARIABLE, SettingVariableTypes.SERVICE_VARIABLE.toString()));
    wingsPersistence.save(encryptedData);

    encryptedData = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedData.getParents()).hasSize(3);

    wingsPersistence.updateField(ServiceVariable.class, serviceVariable1.getUuid(), ServiceVariableKeys.encryptedValue,
        encryptedData1.getUuid());

    encryptedData = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedData.getParents()).hasSize(2);

    encryptedData1 = wingsPersistence.get(EncryptedData.class, secretId1);
    assertThat(encryptedData1.getParents()).hasSize(1);

    Set<SecretSetupUsage> usages = secretManager.getSecretUsage(accountId, encryptedData.getUuid());
    assertThat(usages).isNotNull();
    assertThat(usages.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testSaveEncryptedData_whenUsageRestrictionIsNotPresent() {
    Account newAccount = getAccount(AccountType.PAID);
    String accountId = wingsPersistence.save(newAccount);
    newAccount.setUuid(accountId);
    SecretText secretText =
        SecretText.builder().name("Dummy record").kmsId(accountId).value("value").scopedToAccount(true).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);
    EncryptedData encryptedDataInDB = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedDataInDB).isNotNull();
    assertThat(encryptedDataInDB.isScopedToAccount()).isTrue();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testSaveEncryptedData_whenUsageRestrictionIsPresent() {
    Account newAccount = getAccount(AccountType.PAID);
    String accountId = wingsPersistence.save(newAccount);
    newAccount.setUuid(accountId);

    Set<AppEnvRestriction> appEnvRestrictions = new HashSet();
    appEnvRestrictions.add(
        AppEnvRestriction.builder()
            .appFilter(GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build())
            .envFilter(EnvFilter.builder().filterTypes(Sets.newHashSet(EnvFilter.FilterType.NON_PROD)).build())
            .build());
    UsageRestrictions usageRestrictions = UsageRestrictions.builder().appEnvRestrictions(appEnvRestrictions).build();
    SecretText secretText = SecretText.builder()
                                .name("Dummy record")
                                .kmsId(accountId)
                                .value("value")
                                .usageRestrictions(usageRestrictions)
                                .build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    EncryptedData encryptedDataInDB = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedDataInDB).isNotNull();
    assertThat(encryptedDataInDB.isScopedToAccount()).isFalse();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUpdateSecretText_shouldInvalidateCache() {
    Account newAccount = getAccount(AccountType.PAID);
    String accountId = wingsPersistence.save(newAccount);
    newAccount.setUuid(accountId);
    SecretText secretText =
        SecretText.builder().name("Dummy record").kmsId(accountId).value("value").scopedToAccount(true).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    EncryptedData encryptedDataInDB = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedDataInDB).isNotNull();
    assertThat(encryptedDataInDB.isScopedToAccount()).isTrue();

    Set<AppEnvRestriction> appEnvRestrictions = new HashSet();
    UsageRestrictions usageRestrictions = UsageRestrictions.builder().appEnvRestrictions(appEnvRestrictions).build();
    SecretText secretTextUpdate =
        SecretText.builder().name("Dummy record").kmsId(accountId).usageRestrictions(usageRestrictions).build();

    secretManager.updateSecretText(accountId, secretId, secretTextUpdate, true);
    EncryptedData editedEncryptedDataInDB = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(editedEncryptedDataInDB).isNotNull();
    assertThat(editedEncryptedDataInDB.isScopedToAccount()).isFalse();
    assertThat(secretId).isEqualTo(secretId);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testSaveEncryptedData_whenUsageRestrictionIsEdited() {
    Account newAccount = getAccount(AccountType.PAID);
    String accountId = wingsPersistence.save(newAccount);
    newAccount.setUuid(accountId);
    SecretText secretText =
        SecretText.builder().name("Dummy record").kmsId(accountId).value("value").scopedToAccount(true).build();
    String secretId = secretManager.saveSecretText(accountId, secretText, true);

    EncryptedData encryptedDataInDB = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedDataInDB).isNotNull();
    assertThat(encryptedDataInDB.isScopedToAccount()).isTrue();

    Set<AppEnvRestriction> appEnvRestrictions = new HashSet();
    appEnvRestrictions.add(
        AppEnvRestriction.builder()
            .appFilter(GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build())
            .envFilter(EnvFilter.builder().filterTypes(Sets.newHashSet(EnvFilter.FilterType.NON_PROD)).build())
            .build());
    UsageRestrictions usageRestrictions = UsageRestrictions.builder().appEnvRestrictions(appEnvRestrictions).build();
    SecretText secretTextUpdate =
        SecretText.builder().name("Dummy record").kmsId(accountId).usageRestrictions(usageRestrictions).build();
    boolean isUpdated = secretManager.updateSecretText(accountId, secretId, secretTextUpdate, true);

    EncryptedData editedEncryptedDataInDB = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(editedEncryptedDataInDB).isNotNull();
    assertThat(editedEncryptedDataInDB.isScopedToAccount()).isFalse();
    assertThat(secretId).isEqualTo(secretId);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testSetEncryptedValueToFileContentWithBackupEncryptedValue() {
    for (EncryptionType encryptionType : EncryptionType.values()) {
      EncryptedData encryptedData = EncryptedData.builder()
                                        .encryptionType(encryptionType)
                                        .encryptedValue("DummyValue".toCharArray())
                                        .backupEncryptionType(encryptionType)
                                        .backupEncryptedValue("BackupDummyValue".toCharArray())
                                        .build();
      Mockito.doNothing().when(fileService).downloadToStream(anyString(), any(), any());

      secretManager.setEncryptedValueToFileContent(encryptedData);

      if (ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD.contains(encryptionType)) {
        assertThat("").isEqualTo(new String(encryptedData.getEncryptedValue()));
        assertThat("").isEqualTo(new String(encryptedData.getBackupEncryptedValue()));
      } else {
        assertThat("DummyValue").isEqualTo(new String(encryptedData.getEncryptedValue()));
        assertThat("BackupDummyValue").isEqualTo(new String(encryptedData.getBackupEncryptedValue()));
      }
    }
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testSetEncryptedValueToFileContentWithoutBackupEncryptedValue() {
    for (EncryptionType encryptionType : EncryptionType.values()) {
      EncryptedData encryptedData =
          EncryptedData.builder().encryptionType(encryptionType).encryptedValue("DummyValue".toCharArray()).build();
      Mockito.doNothing().when(fileService).downloadToStream(anyString(), any(), any());

      secretManager.setEncryptedValueToFileContent(encryptedData);

      if (ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD.contains(encryptionType)) {
        assertThat("").isEqualTo(new String(encryptedData.getEncryptedValue()));
        assertThat(encryptedData.getBackupEncryptedValue()).isNull();
      } else {
        assertThat("DummyValue").isEqualTo(new String(encryptedData.getEncryptedValue()));
        assertThat(encryptedData.getBackupEncryptedValue()).isNull();
      }
    }
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSaveFile_hideFromListing() {
    byte[] fileContent = "fileContent".getBytes();
    SecretFile secretFile = SecretFile.builder()
                                .name(secretName)
                                .kmsId(account.getUuid())
                                .usageRestrictions(UsageRestrictions.builder().build())
                                .scopedToAccount(true)
                                .hideFromListing(true)
                                .fileContent(fileContent)
                                .build();
    String recordId = secretManager.saveSecretFile(account.getUuid(), secretFile);

    byte[] savedFileContent = secretManager.getFileContents(account.getUuid(), recordId);
    assertThat(savedFileContent).isEqualTo(fileContent);

    EncryptedData savedData = secretManager.getSecretById(account.getUuid(), recordId);
    assertThat(savedData.getName()).isEqualTo(secretName);
    assertThat(savedData.isHideFromListing()).isEqualTo(true);

    secretManager.deleteSecret(account.getUuid(), recordId, new HashMap<>(), false);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testListSecrets() throws IllegalAccessException {
    byte[] hiddenFileContent = "hiddenFileContent".getBytes();
    Set<AppEnvRestriction> appEnvRestrictions = new HashSet();
    appEnvRestrictions.add(
        AppEnvRestriction.builder()
            .appFilter(GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build())
            .envFilter(EnvFilter.builder().filterTypes(Sets.newHashSet(EnvFilter.FilterType.NON_PROD)).build())
            .build());
    UsageRestrictions usageRestrictions = UsageRestrictions.builder().appEnvRestrictions(appEnvRestrictions).build();

    SecretFile hidden = SecretFile.builder()
                            .name(secretName)
                            .kmsId(account.getUuid())
                            .usageRestrictions(null)
                            .scopedToAccount(true)
                            .hideFromListing(true)
                            .fileContent(hiddenFileContent)
                            .build();
    String hiddenFileRecordId = secretManager.saveSecretFile(account.getUuid(), hidden);

    byte[] fileContent = "fileContent".getBytes();
    SecretFile secretFile = SecretFile.builder()
                                .name("fileName")
                                .kmsId(account.getUuid())
                                .usageRestrictions(null)
                                .scopedToAccount(true)
                                .hideFromListing(false)
                                .fileContent(fileContent)
                                .build();
    String recordId = secretManager.saveSecretFile(account.getUuid(), secretFile);

    PageRequest<EncryptedData> pageRequest = new PageRequest<>();
    pageRequest.addFilter("type", SearchFilter.Operator.EQ, SettingVariableTypes.CONFIG_FILE);
    pageRequest.addFilter("accountId", SearchFilter.Operator.EQ, account.getUuid());

    // show all files
    PageResponse<EncryptedData> retrievedSecretsAll =
        secretManager.listSecrets(account.getUuid(), pageRequest, null, null, false, true, false);
    assertThat(retrievedSecretsAll.getResponse().size()).isEqualTo(2);

    // don't show hidden files
    PageResponse<EncryptedData> retrievedSecretsHideHidden =
        secretManager.listSecrets(account.getUuid(), pageRequest, null, null, false, false, false);
    assertThat(retrievedSecretsHideHidden.getResponse().size()).isEqualTo(1);
    EncryptedData retrievedSecret = retrievedSecretsHideHidden.getResponse().get(0);
    assertThat(retrievedSecret.isHideFromListing()).isEqualTo(false);
    assertThat(retrievedSecret.getName()).isEqualTo("fileName");

    secretManager.deleteSecret(account.getUuid(), hiddenFileRecordId, new HashMap<>(), false);
    secretManager.deleteSecret(account.getUuid(), recordId, new HashMap<>(), false);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testListSecrets_skipRunTimeUsage() throws IllegalAccessException {
    byte[] fileContent = "fileContent".getBytes();
    SecretFile secretFile = SecretFile.builder()
                                .name("fileName")
                                .kmsId(account.getUuid())
                                .usageRestrictions(null)
                                .scopedToAccount(true)
                                .hideFromListing(false)
                                .fileContent(fileContent)
                                .build();
    String recordId = secretManager.saveSecretFile(account.getUuid(), secretFile);

    PageRequest<EncryptedData> pageRequest = new PageRequest<>();
    pageRequest.addFilter("type", SearchFilter.Operator.EQ, SettingVariableTypes.CONFIG_FILE);
    pageRequest.addFilter("accountId", SearchFilter.Operator.EQ, account.getUuid());

    PageResponse<EncryptedData> retrievedSecretsAll =
        secretManager.listSecrets(account.getUuid(), pageRequest, null, null, true, true);
    assertThat(retrievedSecretsAll.getResponse().size()).isEqualTo(1);
    assertNull(retrievedSecretsAll.getResponse().get(0).getRunTimeUsage());
    secretManager.deleteSecret(account.getUuid(), recordId, new HashMap<>(), false);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testEncryptedDataDetails() throws IllegalAccessException {
    String accountId = randomAlphabetic(10);
    String fieldName = randomAlphabetic(10);
    String secret = randomAlphabetic(10);
    SecretText secretText =
        SecretText.builder().name(secret).kmsId(accountId).value("value").scopedToAccount(true).build();
    String encryptedDataId = secretManager.saveSecretText(accountId, secretText, true);
    String workflowExecutionId = wingsPersistence.insert(WorkflowExecution.builder().accountId(accountId).build());
    Optional<EncryptedDataDetail> encryptedDataDetails =
        secretManager.encryptedDataDetails(accountId, fieldName, encryptedDataId, workflowExecutionId);
    assertEncryptedDataDetails(accountId, encryptedDataId, encryptedDataDetails, 1);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testEncryptedDataDetailsWithUpdateSecretUsageAsFalse() throws IllegalAccessException {
    String accountId = randomAlphabetic(10);
    String fieldName = randomAlphabetic(10);
    String secret = randomAlphabetic(10);
    SecretText secretText =
        SecretText.builder().name(secret).kmsId(accountId).value("value").scopedToAccount(true).build();
    String encryptedDataId = secretManager.saveSecretText(accountId, secretText, true);
    String workflowExecutionId = wingsPersistence.insert(WorkflowExecution.builder().accountId(accountId).build());
    Optional<EncryptedDataDetail> encryptedDataDetails =
        secretManager.encryptedDataDetails(accountId, fieldName, encryptedDataId, workflowExecutionId, false);
    assertEncryptedDataDetails(accountId, encryptedDataId, encryptedDataDetails, 0);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetEncryptedDataDetails() throws IllegalAccessException {
    String accountId = randomAlphabetic(10);
    String fieldName = randomAlphabetic(10);
    String secret = randomAlphabetic(10);
    SecretText secretText =
        SecretText.builder().name(secret).kmsId(accountId).value("value").scopedToAccount(true).build();
    String encryptedDataId = secretManager.saveSecretText(accountId, secretText, true);
    EncryptedData encryptedData = secretManager.getSecretById(accountId, encryptedDataId);
    String workflowExecutionId = wingsPersistence.insert(WorkflowExecution.builder().accountId(accountId).build());
    Optional<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptedDataDetails(accountId, fieldName, encryptedData, workflowExecutionId);
    assertEncryptedDataDetails(accountId, encryptedDataId, encryptedDataDetails, 1);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetEncryptedDataDetailsWithUpdateSecretUsageAsFalse() throws IllegalAccessException {
    String accountId = randomAlphabetic(10);
    String fieldName = randomAlphabetic(10);
    String secret = randomAlphabetic(10);
    SecretText secretText =
        SecretText.builder().name(secret).kmsId(accountId).value("value").scopedToAccount(true).build();
    String encryptedDataId = secretManager.saveSecretText(accountId, secretText, true);
    EncryptedData encryptedData = secretManager.getSecretById(accountId, encryptedDataId);
    String workflowExecutionId = wingsPersistence.insert(WorkflowExecution.builder().accountId(accountId).build());
    Optional<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptedDataDetails(accountId, fieldName, encryptedData, workflowExecutionId, false);
    assertEncryptedDataDetails(accountId, encryptedDataId, encryptedDataDetails, 0);
  }

  private void assertEncryptedDataDetails(String accountId, String encryptedDataId,
      Optional<EncryptedDataDetail> encryptedDataDetails, int expectedUsageCount) throws IllegalAccessException {
    PageRequest<SecretUsageLog> pageRequest = new PageRequest<>();
    pageRequest.setPageSize(10);
    PageResponse<SecretUsageLog> secretUsage =
        secretManager.getUsageLogs(pageRequest, accountId, encryptedDataId, SettingVariableTypes.SECRET_TEXT);
    assertThat(encryptedDataDetails.isPresent()).isTrue();
    assertThat(secretUsage.getTotal()).isEqualTo(expectedUsageCount);
  }
}
