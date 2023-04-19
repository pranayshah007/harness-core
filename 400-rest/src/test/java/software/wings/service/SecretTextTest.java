/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.LocalEncryptionConfig.HARNESS_DEFAULT_SECRET_MANAGER;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.persistence.AppContainer.Builder.anAppContainer;
import static software.wings.settings.SettingVariableTypes.CONFIG_FILE;
import static software.wings.settings.SettingVariableTypes.SECRET_TEXT;
import static software.wings.utils.ArtifactType.JAR;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.EnvironmentType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SecretChangeLog;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.CollectionUtils;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.encryptors.clients.LocalEncryptor;
import io.harness.exception.WingsException;
import io.harness.expression.SecretString;
import io.harness.rule.Owner;
import io.harness.secrets.SecretService;
import io.harness.secrets.setupusage.SecretSetupUsage;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.JsonUtils;
import io.harness.testlib.RealMongo;

import software.wings.EncryptTestUtils;
import software.wings.SecretManagementTestHelper;
import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.ConfigFile;
import software.wings.beans.ConfigFile.ConfigOverrideType;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.KmsConfig;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.OverrideType;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.beans.VaultConfig;
import software.wings.beans.WorkflowExecution;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.resources.ServiceVariableResource;
import software.wings.resources.secretsmanagement.SecretManagementResource;
import software.wings.security.UsageRestrictions;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.UsageRestrictionsServiceImplTestBase;
import software.wings.service.impl.security.auth.ConfigFileAuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.KmsService;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.service.intfc.security.SSHVaultService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.security.VaultService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.core.util.Time;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by rsingh on 11/3/17.
 */
@RunWith(Parameterized.class)
@Slf4j
public class SecretTextTest extends WingsBaseTest {
  @Parameter public EncryptionType encryptionType;
  @Mock private AccountService accountService;
  @Mock private YamlPushService yamlPushService;
  @Inject @InjectMocks private VaultService vaultService;
  @Inject @InjectMocks private KmsService kmsService;
  @Inject @InjectMocks private SecretService secretService;
  @Inject private SecretManager secretManager;
  @Inject private WingsPersistence wingsPersistence;
  @Inject @InjectMocks private ConfigService configService;
  @Inject private EncryptionService encryptionService;
  @Inject private ServiceVariableService serviceVariableService;
  @Inject private ServiceVariableResource serviceVariableResource;
  @Inject private FileService fileService;
  @Inject private SecretManagementResource secretManagementResource;
  @Inject private AppService appService;
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private LocalEncryptor localEncryptor;
  @Inject private SecretManagementTestHelper secretManagementTestHelper;
  @Inject private LocalSecretManagerService localSecretManagerService;

  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private SecretManagementDelegateService secretManagementDelegateService;
  @Mock private ConfigFileAuthHandler configFileAuthHandler;
  @Mock private KmsEncryptor kmsEncryptor;
  @Mock private VaultEncryptor vaultEncryptor;
  @Mock private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Mock private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Mock private SSHVaultService sshVaultService;

  private final String userEmail = "rsingh@harness.io";
  private final String userName = "raghu";
  private final User user = User.Builder.anUser().email(userEmail).name(userName).build();
  private String accountId;
  private String appId;
  private String workflowExecutionId;
  private String workflowName;
  private String kmsId;
  private String envId;
  private String encryptedBy;
  private PageRequest<EncryptedData> pageRequest;

  @Parameters
  public static Collection<Object[]> data() {
    return asList(new Object[][] {{EncryptionType.LOCAL}, {EncryptionType.KMS}, {EncryptionType.VAULT}});
  }

  @Before
  public void setup() throws IOException, IllegalAccessException {
    initMocks(this);

    Account account = getAccount(AccountType.PAID);
    accountId = account.getUuid();

    when(accountService.get(accountId)).thenReturn(account);

    doNothing().when(configFileAuthHandler).authorize(any());

    appId = wingsPersistence.save(anApplication().accountId(accountId).name(generateUuid()).build());
    workflowName = generateUuid();
    envId = wingsPersistence.save(anEnvironment()
                                      .accountId(accountId)
                                      .name(generateUuid())
                                      .appId(appId)
                                      .environmentType(EnvironmentType.NON_PROD)
                                      .build());
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

    when(kmsEncryptorsRegistry.getKmsEncryptor(any())).thenReturn(kmsEncryptor);
    when(vaultEncryptorsRegistry.getVaultEncryptor(any())).thenReturn(vaultEncryptor);

    when(delegateProxyFactory.getV2(eq(SecretManagementDelegateService.class), any(SyncTaskContext.class)))
        .thenReturn(secretManagementDelegateService);
    when(delegateProxyFactory.getV2(eq(EncryptionService.class), any(SyncTaskContext.class)))
        .thenReturn(encryptionService);
    FieldUtils.writeField(secretService, "kmsRegistry", kmsEncryptorsRegistry, true);
    FieldUtils.writeField(secretService, "vaultRegistry", vaultEncryptorsRegistry, true);
    FieldUtils.writeField(encryptionService, "kmsEncryptorsRegistry", kmsEncryptorsRegistry, true);
    FieldUtils.writeField(encryptionService, "vaultEncryptorsRegistry", vaultEncryptorsRegistry, true);
    FieldUtils.writeField(vaultService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(kmsService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(secretManager, "vaultService", vaultService, true);
    FieldUtils.writeField(wingsPersistence, "secretManager", secretManager, true);
    FieldUtils.writeField(configService, "secretManager", secretManager, true);
    FieldUtils.writeField(secretManagementResource, "secretManager", secretManager, true);
    wingsPersistence.save(user);
    UserThreadLocal.set(user);

    switch (encryptionType) {
      case LOCAL:
        kmsId = accountId;
        encryptedBy = HARNESS_DEFAULT_SECRET_MANAGER;
        break;

      case KMS:
        KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
        kmsId = kmsService.saveKmsConfig(accountId, kmsConfig);
        encryptedBy = kmsConfig.getName();
        break;

      case VAULT:
        VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken();
        kmsId = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);
        encryptedBy = vaultConfig.getName();
        break;

      default:
        throw new IllegalArgumentException("Invalid type " + encryptionType);
    }

    pageRequest = new PageRequest<>();
    pageRequest.addFilter("accountId", Operator.IN, accountId);
    pageRequest.addFilter("type", Operator.IN, "SECRET_TEXT");
  }

  private String getRandomServiceVariableName() {
    return generateUuid().replaceAll("-", "_");
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void saveSecret() throws IllegalAccessException {
    String secretName = generateUuid();
    String secretValue = generateUuid();
    String secretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();
    testSaveSecret(secretName, secretValue, secretId);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void saveAndUpdateSecret() throws IllegalAccessException {
    UsageRestrictions usageRestrictions =
        UsageRestrictionsServiceImplTestBase.getUsageRestrictionsForAppIdAndEnvId(appId, envId);

    String secretName = generateUuid();
    String secretValue = generateUuid();
    String secretId = secretManagementResource
                          .saveSecret(accountId,
                              SecretText.builder()
                                  .name(secretName)
                                  .value(secretValue)
                                  .kmsId(kmsId)
                                  .usageRestrictions(usageRestrictions)
                                  .build())
                          .getResource();
    List<SecretChangeLog> changeLogs =
        secretManagementResource.getChangeLogs(accountId, secretId, SECRET_TEXT).getResource();
    assertThat(changeLogs).hasSize(1);
    SecretChangeLog secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getAccountId()).isEqualTo(accountId);
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");
    assertThat(secretChangeLog.getEncryptedDataId()).isEqualTo(secretId);
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(userName);
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(userEmail);
    assertThat(wingsPersistence.get(EncryptedData.class, secretId).getUsageRestrictions()).isEqualTo(usageRestrictions);

    final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(generateUuid())
                                                .envId(generateUuid())
                                                .entityType(EntityType.APPLICATION)
                                                .entityId(generateUuid())
                                                .parentServiceVariableId(generateUuid())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(generateUuid()))
                                                .expression(generateUuid())
                                                .accountId(accountId)
                                                .name(getRandomServiceVariableName())
                                                .value(secretId.toCharArray())
                                                .encryptedValue(secretId)
                                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                .build();

    String savedAttributeId = wingsPersistence.save(serviceVariable);
    ServiceVariable savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(savedVariable.getValue()).isNull();
    assertThat(savedVariable.getEncryptedValue()).isEqualTo(secretId);

    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue);

    envId = wingsPersistence.save(anEnvironment()
                                      .accountId(accountId)
                                      .name(generateUuid())
                                      .appId(appId)
                                      .environmentType(EnvironmentType.PROD)
                                      .build());

    usageRestrictions = UsageRestrictionsServiceImplTestBase.getUsageRestrictionsForAppIdAndEnvId(appId, envId);

    // check only change in usage restrictions triggers change logs
    secretManagementResource.updateSecret(accountId, secretId,
        SecretText.builder()
            .name(secretName)
            .value(SecretString.SECRET_MASK)
            .usageRestrictions(usageRestrictions)
            .build());
    changeLogs = secretManagementResource.getChangeLogs(accountId, secretId, SECRET_TEXT).getResource();
    assertThat(changeLogs).hasSize(2);
    secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getAccountId()).isEqualTo(accountId);
    assertThat(secretChangeLog.getDescription()).isEqualTo("Changed usage restrictions");
    secretChangeLog = changeLogs.get(1);
    assertThat(secretChangeLog.getAccountId()).isEqualTo(accountId);
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");

    // check just changing the name still gives old value
    String newSecretName = generateUuid();
    secretManagementResource.updateSecret(accountId, secretId,
        SecretText.builder().name(newSecretName).value(secretValue).usageRestrictions(usageRestrictions).build());

    savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(savedVariable.getValue()).isNull();
    assertThat(savedVariable.getEncryptedValue()).isEqualTo(secretId);

    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue);

    changeLogs = secretManagementResource.getChangeLogs(accountId, secretId, SECRET_TEXT).getResource();
    assertThat(changeLogs).hasSize(3);
    assertThat(changeLogs.get(0).getDescription()).isEqualTo("Changed name & secret");
    assertThat(changeLogs.get(1).getDescription()).isEqualTo("Changed usage restrictions");
    assertThat(changeLogs.get(2).getDescription()).isEqualTo("Created");

    // change both name and value and test
    newSecretName = generateUuid();
    String newSecretValue = generateUuid();
    secretManagementResource.updateSecret(accountId, secretId,
        SecretText.builder().name(newSecretName).value(newSecretValue).usageRestrictions(usageRestrictions).build());
    changeLogs = secretManagementResource.getChangeLogs(accountId, secretId, SECRET_TEXT).getResource();
    assertThat(changeLogs).hasSize(4);
    assertThat(changeLogs.get(0).getDescription()).isEqualTo("Changed name & secret");
    assertThat(changeLogs.get(1).getDescription()).isEqualTo("Changed name & secret");
    assertThat(changeLogs.get(2).getDescription()).isEqualTo("Changed usage restrictions");
    assertThat(changeLogs.get(3).getDescription()).isEqualTo("Created");

    savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(savedVariable.getValue()).isNull();
    assertThat(savedVariable.getEncryptedValue()).isEqualTo(secretId);

    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(newSecretValue);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void saveSecretUsingLocalMode() throws IllegalAccessException {
    if (encryptionType != EncryptionType.LOCAL) {
      return;
    }
    String secretName = generateUuid();
    String secretValue = generateUuid();
    String secretId =
        secretManagementResource
            .saveSecretUsingLocalMode(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();
    testSaveSecret(secretName, secretValue, secretId);
  }

  private void testSaveSecret(String secretName, String secretValue, String secretId) throws IllegalAccessException {
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .filter(EncryptedDataKeys.type, SECRET_TEXT)
                                     .filter(EncryptedDataKeys.accountId, accountId);
    List<EncryptedData> encryptedDataList = query.asList();
    assertThat(encryptedDataList).hasSize(1);
    EncryptedData encryptedData = encryptedDataList.get(0);
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
    assertThat(encryptedData.getType()).isEqualTo(SECRET_TEXT);

    final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(generateUuid())
                                                .envId(generateUuid())
                                                .entityType(EntityType.APPLICATION)
                                                .entityId(generateUuid())
                                                .parentServiceVariableId(generateUuid())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(generateUuid()))
                                                .expression(generateUuid())
                                                .accountId(accountId)
                                                .name(getRandomServiceVariableName())
                                                .value(secretId.toCharArray())
                                                .encryptedValue(secretId)
                                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                .build();

    String savedAttributeId = wingsPersistence.save(serviceVariable);

    query = wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.type, SECRET_TEXT);
    assertThat(query.count()).isEqualTo(1);
    encryptedData = query.get();
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
    assertThat(encryptedData.getType()).isEqualTo(SECRET_TEXT);
    assertThat(encryptedData.getParents()).hasSize(1);
    assertThat(encryptedData.containsParent(savedAttributeId, serviceVariable.getSettingType())).isTrue();

    ServiceVariable savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(savedVariable.getValue()).isNull();
    assertThat(savedVariable.getEncryptedValue()).isEqualTo(secretId);

    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue);

    List<SecretChangeLog> changeLogs =
        secretManagementResource.getChangeLogs(accountId, secretId, SECRET_TEXT).getResource();
    assertThat(changeLogs).hasSize(1);
    SecretChangeLog secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getAccountId()).isEqualTo(accountId);
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");
    assertThat(secretChangeLog.getEncryptedDataId()).isEqualTo(secretId);
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(userName);
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(userEmail);

    String newSecretName = generateUuid();
    String newSecretValue = generateUuid();
    secretManagementResource.updateSecret(
        accountId, secretId, SecretText.builder().name(newSecretName).value(newSecretValue).build());

    changeLogs = secretManagementResource.getChangeLogs(accountId, secretId, SECRET_TEXT).getResource();
    assertThat(changeLogs).hasSize(2);

    secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getAccountId()).isEqualTo(accountId);
    assertThat(secretChangeLog.getDescription()).isEqualTo("Changed name & secret");
    assertThat(secretChangeLog.getEncryptedDataId()).isEqualTo(secretId);
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(userName);
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(userEmail);

    secretChangeLog = changeLogs.get(1);
    assertThat(secretChangeLog.getAccountId()).isEqualTo(accountId);
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");
    assertThat(secretChangeLog.getEncryptedDataId()).isEqualTo(secretId);
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(userName);
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(userEmail);

    query = wingsPersistence.createQuery(EncryptedData.class)
                .filter(EncryptedDataKeys.type, SECRET_TEXT)
                .filter(EncryptedDataKeys.accountId, accountId);
    encryptedDataList = query.asList();
    assertThat(encryptedDataList).hasSize(1);
    encryptedData = encryptedDataList.get(0);
    assertThat(encryptedData.getName()).isEqualTo(newSecretName);
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
    assertThat(encryptedData.getType()).isEqualTo(SECRET_TEXT);
    assertThat(encryptedData.getParents()).hasSize(1);
    assertThat(encryptedData.containsParent(savedAttributeId, serviceVariable.getSettingType())).isTrue();

    savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    assertThat(savedVariable.getValue()).isNull();
    assertThat(savedVariable.getEncryptedValue()).isEqualTo(secretId);

    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(newSecretValue);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @RealMongo
  public void updateSecretRef() {
    String secretName1 = "s1" + generateUuid();
    String secretValue1 = "v2";
    String secretId1 = secretManagementResource
                           .saveSecret(accountId, SecretText.builder().name(secretName1).value(secretValue1).build())
                           .getResource();

    String secretName2 = "s2" + generateUuid();
    String secretValue2 = "v2";
    String secretId2 = secretManagementResource
                           .saveSecret(accountId, SecretText.builder().name(secretName2).value(secretValue2).build())
                           .getResource();

    String secretName3 = "s3" + generateUuid();
    String secretValue3 = "v3";
    String secretId3 = secretManagementResource
                           .saveSecret(accountId, SecretText.builder().name(secretName3).value(secretValue3).build())
                           .getResource();

    final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(generateUuid())
                                                .entityType(EntityType.SERVICE)
                                                .parentServiceVariableId(generateUuid())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(generateUuid()))
                                                .expression(generateUuid())
                                                .accountId(accountId)
                                                .name("service_var" + getRandomServiceVariableName())
                                                .value(secretId1.toCharArray())
                                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                .build();
    Application application = anApplication().accountId(accountId).name(generateUuid()).build();
    String appId = wingsPersistence.save(application);
    Service service = Service.builder()
                          .appId(appId)
                          .name("SERVICE_NAME")
                          .description("SERVICE_DESC")
                          .artifactType(JAR)
                          .appContainer(anAppContainer().withUuid("APP_CONTAINER_ID").build())
                          .accountId(accountId)
                          .build();
    String serviceId = wingsPersistence.save(service);
    Environment environment = anEnvironment().accountId(accountId).appId(appId).name(generateUuid()).build();
    String environmentId = wingsPersistence.save(environment);

    serviceVariable.setAppId(appId);
    serviceVariable.setEnvId(environmentId);
    serviceVariable.setServiceId(serviceId);
    serviceVariable.setEntityId(serviceId);
    String savedAttributeId = serviceVariableService.save(serviceVariable).getUuid();

    Time.sleep(2, TimeUnit.SECONDS);
    ServiceVariable savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue1);
    EncryptedData encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                                      .filter(EncryptedDataKeys.name, secretName1)
                                      .filter(EncryptedDataKeys.accountId, accountId)
                                      .get();
    assertThat(encryptedData.getParents()).hasSize(1);
    assertThat(encryptedData.containsParent(serviceVariable.getUuid(), serviceVariable.getSettingType())).isTrue();
    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName2)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();

    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName3)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();

    savedVariable.setValue(secretId2.toCharArray());
    serviceVariableService.update(savedVariable);
    Time.sleep(2, TimeUnit.SECONDS);
    savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue2);

    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName1)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();

    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName2)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(encryptedData.getParents()).hasSize(1);
    assertThat(encryptedData.containsParent(serviceVariable.getUuid(), serviceVariable.getSettingType())).isTrue();

    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName3)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();

    String updatedName = "updatedName" + getRandomServiceVariableName();
    String updatedAppId = wingsPersistence.save(anApplication().accountId(accountId).name(generateUuid()).build());
    final Map<String, Object> keyValuePairs = new HashMap<>();
    keyValuePairs.put("name", updatedName);
    keyValuePairs.put("appId", updatedAppId);
    keyValuePairs.put("type", ServiceVariableType.ENCRYPTED_TEXT);
    keyValuePairs.put("value", secretId3.toCharArray());

    wingsPersistence.updateFields(ServiceVariable.class, savedAttributeId, keyValuePairs);
    savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue3);

    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName1)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();

    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName2)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();

    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName3)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(encryptedData.getParents()).hasSize(1);
    assertThat(encryptedData.containsParent(serviceVariable.getUuid(), serviceVariable.getSettingType())).isTrue();

    savedVariable.setValue(secretId1.toCharArray());
    serviceVariableService.update(savedVariable);
    Time.sleep(2, TimeUnit.SECONDS);

    savedVariable = wingsPersistence.get(ServiceVariable.class, savedAttributeId);
    encryptionService.decrypt(
        savedVariable, secretManager.getEncryptionDetails(savedVariable, appId, workflowExecutionId), false);
    assertThat(String.valueOf(savedVariable.getValue())).isEqualTo(secretValue1);

    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName1)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(encryptedData.getParents()).hasSize(1);
    assertThat(encryptedData.containsParent(serviceVariable.getUuid(), serviceVariable.getSettingType())).isTrue();

    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName2)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();

    encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.name, secretName3)
                        .filter(EncryptedDataKeys.accountId, accountId)
                        .get();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void multipleVariableReference() {
    String secretName = generateUuid();
    String secretValue = generateUuid();
    String secretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();

    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .filter(EncryptedDataKeys.type, SECRET_TEXT)
                                     .filter(EncryptedDataKeys.accountId, accountId);
    List<EncryptedData> encryptedDataList = query.asList();
    assertThat(encryptedDataList).hasSize(1);
    EncryptedData encryptedData = encryptedDataList.get(0);
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
    assertThat(encryptedData.getType()).isEqualTo(SECRET_TEXT);

    int numOfVariable = 10;
    Set<String> variableIds = new HashSet<>();
    for (int i = 0; i < numOfVariable; i++) {
      final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                  .templateId(generateUuid())
                                                  .envId(generateUuid())
                                                  .entityType(EntityType.APPLICATION)
                                                  .entityId(generateUuid())
                                                  .parentServiceVariableId(generateUuid())
                                                  .overrideType(OverrideType.ALL)
                                                  .instances(Collections.singletonList(generateUuid()))
                                                  .expression(generateUuid())
                                                  .accountId(accountId)
                                                  .name(generateUuid())
                                                  .value(secretId.toCharArray())
                                                  .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                  .build();

      String savedAttributeId = wingsPersistence.save(serviceVariable);
      variableIds.add(savedAttributeId);

      query = wingsPersistence.createQuery(EncryptedData.class)
                  .filter(EncryptedDataKeys.type, SECRET_TEXT)
                  .filter(EncryptedDataKeys.accountId, accountId);
      assertThat(query.count()).isEqualTo(1);
      encryptedData = query.get();
      assertThat(encryptedData.getName()).isEqualTo(secretName);
      assertThat(encryptedData.getEncryptionKey()).isNotNull();
      assertThat(encryptedData.getEncryptedValue()).isNotNull();
      assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
      assertThat(encryptedData.isEnabled()).isTrue();
      assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
      assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
      assertThat(encryptedData.getType()).isEqualTo(SECRET_TEXT);
      assertThat(encryptedData.getParents()).hasSize(i + 1);
      assertThat(encryptedData.containsParent(savedAttributeId, serviceVariable.getSettingType())).isTrue();
    }

    int i = numOfVariable - 1;
    for (String variableId : variableIds) {
      wingsPersistence.delete(ServiceVariable.class, variableId);

      query = wingsPersistence.createQuery(EncryptedData.class)
                  .filter(EncryptedDataKeys.type, SECRET_TEXT)
                  .filter(EncryptedDataKeys.accountId, accountId);
      assertThat(query.count()).isEqualTo(1);
      encryptedData = query.get();
      assertThat(encryptedData.getName()).isEqualTo(secretName);
      assertThat(encryptedData.getEncryptionKey()).isNotNull();
      assertThat(encryptedData.getEncryptedValue()).isNotNull();
      assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
      assertThat(encryptedData.isEnabled()).isTrue();
      assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
      assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
      assertThat(encryptedData.getType()).isEqualTo(SECRET_TEXT);

      if (i == 0) {
        assertThat(isEmpty(encryptedData.getParents())).isTrue();
      } else {
        assertThat(encryptedData.getParents()).hasSize(i);
        assertThat(encryptedData.containsParent(variableId, SettingVariableTypes.SERVICE_VARIABLE)).isFalse();
      }
      i--;
    }

    query = wingsPersistence.createQuery(EncryptedData.class)
                .filter(EncryptedDataKeys.type, SECRET_TEXT)
                .filter(EncryptedDataKeys.accountId, accountId);
    assertThat(query.count()).isEqualTo(1);
    encryptedData = query.get();
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
    assertThat(encryptedData.getType()).isEqualTo(SECRET_TEXT);
    assertThat(isEmpty(encryptedData.getParents())).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void deleteSecret() {
    String secretName = generateUuid();
    String secretValue = generateUuid();
    String secretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .filter(EncryptedDataKeys.type, SECRET_TEXT)
                                     .filter(EncryptedDataKeys.accountId, accountId);
    List<EncryptedData> encryptedDataList = query.asList();
    assertThat(encryptedDataList).hasSize(1);
    EncryptedData encryptedData = encryptedDataList.get(0);
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
    assertThat(encryptedData.getType()).isEqualTo(SECRET_TEXT);

    int numOfVariable = 10;
    Set<String> variableIds = new HashSet<>();
    for (int i = 0; i < numOfVariable; i++) {
      final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                  .templateId(generateUuid())
                                                  .envId(generateUuid())
                                                  .entityType(EntityType.APPLICATION)
                                                  .entityId(generateUuid())
                                                  .parentServiceVariableId(generateUuid())
                                                  .overrideType(OverrideType.ALL)
                                                  .instances(Collections.singletonList(generateUuid()))
                                                  .expression(generateUuid())
                                                  .accountId(accountId)
                                                  .name(generateUuid())
                                                  .value(secretId.toCharArray())
                                                  .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                  .build();

      String savedAttributeId = wingsPersistence.save(serviceVariable);
      variableIds.add(savedAttributeId);

      query = wingsPersistence.createQuery(EncryptedData.class)
                  .filter(EncryptedDataKeys.type, SECRET_TEXT)
                  .filter(EncryptedDataKeys.accountId, accountId);
      assertThat(query.count()).isEqualTo(1);
      encryptedData = query.get();
      assertThat(encryptedData.getName()).isEqualTo(secretName);
      assertThat(encryptedData.getEncryptionKey()).isNotNull();
      assertThat(encryptedData.getEncryptedValue()).isNotNull();
      assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
      assertThat(encryptedData.isEnabled()).isTrue();
      assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
      assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
      assertThat(encryptedData.getType()).isEqualTo(SECRET_TEXT);
      assertThat(encryptedData.getParents()).hasSize(i + 1);
      assertThat(encryptedData.containsParent(savedAttributeId, serviceVariable.getSettingType())).isTrue();
    }

    int i = numOfVariable - 1;
    for (String variableId : variableIds) {
      wingsPersistence.delete(ServiceVariable.class, variableId);

      if (i == 0) {
        secretManagementResource.deleteSecret(accountId, secretId);
        query = wingsPersistence.createQuery(EncryptedData.class)
                    .filter(EncryptedDataKeys.type, SECRET_TEXT)
                    .filter(EncryptedDataKeys.accountId, accountId);
        assertThat(query.asList().isEmpty()).isTrue();
      } else {
        try {
          secretManagementResource.deleteSecret(accountId, secretId);
          fail("Deleted referenced secret");
        } catch (WingsException e) {
          // expected
        }
        query = wingsPersistence.createQuery(EncryptedData.class)
                    .filter(EncryptedDataKeys.type, SECRET_TEXT)
                    .filter(EncryptedDataKeys.accountId, accountId);
        assertThat(query.count()).isEqualTo(1);
      }
      i--;
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void listSecrets() {
    int numOfSecrets = 3;
    int numOfVariable = 4;
    int numOfAccess = 3;
    int numOfUpdates = 2;
    PageResponse<EncryptedData> pageResponse =
        (PageResponse<EncryptedData>) secretManagementResource
            .listSecrets(accountId, SECRET_TEXT, null, null, true, false, pageRequest)
            .getResource();
    List<EncryptedData> secrets = pageResponse.getResponse();

    assertThat(secrets.isEmpty()).isTrue();
    for (int i = 0; i < numOfSecrets; i++) {
      String secretName = generateUuid();
      String secretValue = generateUuid();
      String secretId = secretManagementResource
                            .saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
                            .getResource();

      for (int j = 0; j < numOfVariable; j++) {
        final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                    .templateId(generateUuid())
                                                    .envId(generateUuid())
                                                    .entityType(EntityType.APPLICATION)
                                                    .entityId(generateUuid())
                                                    .parentServiceVariableId(generateUuid())
                                                    .overrideType(OverrideType.ALL)
                                                    .instances(Collections.singletonList(generateUuid()))
                                                    .expression(generateUuid())
                                                    .accountId(accountId)
                                                    .name(generateUuid())
                                                    .value(secretId.toCharArray())
                                                    .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                    .build();

        wingsPersistence.save(serviceVariable);
        for (int k = 0; k < numOfAccess; k++) {
          secretManager.getEncryptionDetails(serviceVariable, appId, workflowExecutionId);
        }

        for (int l = 0; l < numOfUpdates; l++) {
          secretManagementResource.updateSecret(
              accountId, secretId, SecretText.builder().name(generateUuid()).value(generateUuid()).build());
        }
      }

      pageResponse = (PageResponse<EncryptedData>) secretManagementResource
                         .listSecrets(accountId, SECRET_TEXT, null, null, true, false, pageRequest)
                         .getResource();
      secrets = pageResponse.getResponse();
      assertThat(secrets).hasSize(i + 1);

      for (EncryptedData secret : secrets) {
        assertThat(secret.getEncryptionKey()).isEqualTo(SECRET_MASK);
        assertThat(String.valueOf(secret.getEncryptedValue())).isEqualTo(SECRET_MASK);
        assertThat(secret.getAccountId()).isEqualTo(accountId);
        assertThat(secret.isEnabled()).isTrue();
        assertThat(secret.getKmsId()).isEqualTo(kmsId);
        assertThat(secret.getEncryptionType()).isEqualTo(encryptionType);
        assertThat(secret.getType()).isEqualTo(SECRET_TEXT);
        assertThat(secret.getEncryptedBy()).isEqualTo(encryptedBy);
      }
    }

    pageResponse = (PageResponse<EncryptedData>) secretManagementResource
                       .listSecrets(accountId, SECRET_TEXT, null, null, true, false, pageRequest)
                       .getResource();
    secrets = pageResponse.getResponse();

    assertThat(isEmpty(secrets)).isFalse();
    for (EncryptedData secret : secrets) {
      assertThat(secret.getSetupUsage()).isEqualTo(numOfVariable);
      assertThat(secret.getRunTimeUsage()).isEqualTo(numOfAccess * numOfVariable);
      assertThat(secret.getChangeLog()).isEqualTo(numOfUpdates * numOfVariable + 1);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void listSecretsWithSummary() throws IOException, IllegalAccessException {
    int numOfSecrets = 3;
    int numOfVariable = 4;
    int numOfAccess = 3;
    int numOfUpdates = 2;
    PageResponse<EncryptedData> pageResponse =
        (PageResponse<EncryptedData>) secretManagementResource
            .listSecrets(accountId, SECRET_TEXT, null, null, false, false, pageRequest)
            .getResource();
    List<EncryptedData> secrets = pageResponse.getResponse();

    assertThat(secrets.isEmpty()).isTrue();
    for (int i = 0; i < numOfSecrets; i++) {
      String secretName = generateUuid();
      String secretValue = generateUuid();
      String secretId = secretManagementResource
                            .saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
                            .getResource();

      for (int j = 0; j < numOfVariable; j++) {
        final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                    .templateId(generateUuid())
                                                    .envId(generateUuid())
                                                    .entityType(EntityType.APPLICATION)
                                                    .entityId(generateUuid())
                                                    .parentServiceVariableId(generateUuid())
                                                    .overrideType(OverrideType.ALL)
                                                    .instances(Collections.singletonList(generateUuid()))
                                                    .expression(generateUuid())
                                                    .accountId(accountId)
                                                    .name(generateUuid())
                                                    .value(secretId.toCharArray())
                                                    .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                    .build();

        wingsPersistence.save(serviceVariable);
        for (int k = 0; k < numOfAccess; k++) {
          secretManager.getEncryptionDetails(serviceVariable, appId, workflowExecutionId);
        }

        for (int l = 0; l < numOfUpdates; l++) {
          secretManagementResource.updateSecret(
              accountId, secretId, SecretText.builder().name(generateUuid()).value(generateUuid()).build());
        }
      }

      pageResponse = (PageResponse<EncryptedData>) secretManagementResource
                         .listSecrets(accountId, SECRET_TEXT, null, null, false, false, pageRequest)
                         .getResource();
      secrets = pageResponse.getResponse();
      assertThat(secrets).hasSize(i + 1);

      for (EncryptedData secret : secrets) {
        assertThat(secret.getEncryptionKey()).isEqualTo(SECRET_MASK);
        assertThat(String.valueOf(secret.getEncryptedValue())).isEqualTo(SECRET_MASK);
        assertThat(secret.getAccountId()).isEqualTo(accountId);
        assertThat(secret.isEnabled()).isTrue();
        assertThat(secret.getKmsId()).isEqualTo(kmsId);
        assertThat(secret.getEncryptionType()).isEqualTo(encryptionType);
        assertThat(secret.getType()).isEqualTo(SECRET_TEXT);
        assertThat(secret.getEncryptedBy()).isNull();
        assertThat(secret.getSetupUsage()).isEqualTo(0);
        assertThat(secret.getRunTimeUsage()).isEqualTo(0);
        assertThat(secret.getChangeLog()).isEqualTo(0);
      }
    }

    pageResponse = (PageResponse<EncryptedData>) secretManagementResource
                       .listSecrets(accountId, SECRET_TEXT, null, null, false, false, pageRequest)
                       .getResource();
    secrets = pageResponse.getResponse();

    assertThat(isEmpty(secrets)).isFalse();
    for (EncryptedData secret : secrets) {
      assertThat(secret.getSetupUsage()).isEqualTo(0);
      assertThat(secret.getRunTimeUsage()).isEqualTo(0);
      assertThat(secret.getChangeLog()).isEqualTo(0);
    }
  }
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void secretTextUsage() throws IOException, IllegalAccessException {
    String secretName = generateUuid();
    String secretValue = generateUuid();
    String secretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();

    int numOfVariable = 10;
    Set<ServiceVariable> serviceVariables = new HashSet<>();
    for (int i = 0; i < numOfVariable; i++) {
      final ServiceVariable serviceVariable = ServiceVariable.builder()
                                                  .templateId(generateUuid())
                                                  .envId(generateUuid())
                                                  .entityType(EntityType.APPLICATION)
                                                  .entityId(generateUuid())
                                                  .parentServiceVariableId(generateUuid())
                                                  .overrideType(OverrideType.ALL)
                                                  .instances(Collections.singletonList(generateUuid()))
                                                  .expression(generateUuid())
                                                  .accountId(accountId)
                                                  .name(generateUuid())
                                                  .value(secretId.toCharArray())
                                                  .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                  .build();

      wingsPersistence.save(serviceVariable);
      serviceVariable.setValue(SECRET_MASK.toCharArray());
      serviceVariable.setEncryptedBy(encryptedBy);
      serviceVariable.setEncryptionType(encryptionType);
      serviceVariables.add(serviceVariable);

      Set<SecretSetupUsage> usages = secretManagementResource.getSecretSetupUsage(accountId, secretId).getResource();
      assertThat(usages.stream().map(SecretSetupUsage::getEntity).collect(Collectors.toSet()))
          .isEqualTo(serviceVariables);
    }

    Set<ServiceVariable> remainingVariables = new HashSet<>(serviceVariables);
    for (ServiceVariable serviceVariable : serviceVariables) {
      remainingVariables.remove(serviceVariable);
      wingsPersistence.delete(ServiceVariable.class, serviceVariable.getUuid());

      Set<SecretSetupUsage> usages = secretManagementResource.getSecretSetupUsage(accountId, secretId).getResource();
      assertThat(usages.stream().map(SecretSetupUsage::getEntity).collect(Collectors.toSet()))
          .isEqualTo(remainingVariables);
    }

    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .filter(EncryptedDataKeys.type, SECRET_TEXT)
                                     .filter(EncryptedDataKeys.accountId, accountId);
    assertThat(query.count()).isEqualTo(1);
    EncryptedData encryptedData = query.get();
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
    assertThat(encryptedData.getType()).isEqualTo(SECRET_TEXT);
    assertThat(isEmpty(encryptedData.getParents())).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveAndUpdateFile() throws IOException, IllegalAccessException {
    doReturn(null)
        .when(yamlPushService)
        .pushYamlChangeSet(anyString(), any(), any(), any(), anyBoolean(), anyBoolean());
    final long seed = System.currentTimeMillis();
    log.info("seed: " + seed);
    Random r = new Random(seed);
    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

    String secretName = generateUuid();
    File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
    when(httpServletRequest.getContentLengthLong()).thenReturn(fileToSave.length());
    String secretFileId =
        secretManagementResource
            .saveFile(httpServletRequest, accountId, kmsId, secretName, new FileInputStream(fileToSave), null,
                JsonUtils.asJson(AdditionalMetadata.builder().build()), JsonUtils.asJson(new HashMap<>()), false, false)
            .getResource();

    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .filter(EncryptedDataKeys.type, CONFIG_FILE)
                                     .filter(EncryptedDataKeys.accountId, accountId);
    List<EncryptedData> encryptedDataList = query.asList();
    assertThat(encryptedDataList).hasSize(1);
    EncryptedData encryptedData = encryptedDataList.get(0);
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
    assertThat(encryptedData.getType()).isEqualTo(CONFIG_FILE);

    Service service = Service.builder().name(generateUuid()).appId(appId).build();
    wingsPersistence.save(service);

    ConfigFile configFile = generateConfigFile(r, secretFileId, service);

    configFile.setAccountId(accountId);
    configFile.setAppId(appId);
    configFile.setFileName("FileName");
    String configFileId = configService.save(configFile, null);

    query = wingsPersistence.createQuery(EncryptedData.class)
                .filter(EncryptedDataKeys.type, CONFIG_FILE)
                .filter(EncryptedDataKeys.accountId, accountId);
    assertThat(query.count()).isEqualTo(1);
    encryptedData = query.get();
    assertSecretData(secretName, encryptedData);
    assertThat(encryptedData.getParents()).hasSize(1);
    assertThat(encryptedData.containsParent(configFileId, CONFIG_FILE)).isTrue();

    String encryptedUuid = encryptedData.getUuid();

    File download = configService.download(appId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToSave, Charset.defaultCharset()));

    List<SecretChangeLog> changeLogs =
        secretManagementResource.getChangeLogs(accountId, secretFileId, CONFIG_FILE).getResource();
    assertThat(changeLogs).hasSize(1);
    SecretChangeLog secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getAccountId()).isEqualTo(accountId);
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");
    assertThat(secretChangeLog.getEncryptedDataId()).isEqualTo(secretFileId);
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(userName);
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(userEmail);

    String newSecretName = generateUuid();
    File fileToUpdate = new File("400-rest/src/test/resources/encryption/file_to_update.txt");
    when(httpServletRequest.getContentLengthLong()).thenReturn(fileToUpdate.length());

    secretManagementResource.updateFile(httpServletRequest, accountId, newSecretName, null,
        JsonUtils.asJson(AdditionalMetadata.builder().build()), JsonUtils.asJson(new HashMap<>()), encryptedUuid,
        new FileInputStream(fileToUpdate), false, false);

    query = wingsPersistence.createQuery(EncryptedData.class)
                .filter(EncryptedDataKeys.type, CONFIG_FILE)
                .filter(EncryptedDataKeys.accountId, accountId);
    assertThat(query.count()).isEqualTo(1);
    encryptedData = query.get();
    assertSecretData(newSecretName, encryptedData);
    assertThat(encryptedData.getParents()).hasSize(1);
    assertThat(encryptedData.containsParent(configFileId, CONFIG_FILE)).isTrue();

    download = configService.download(appId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(fileToUpdate, Charset.defaultCharset()));

    changeLogs = secretManagementResource.getChangeLogs(accountId, secretFileId, SECRET_TEXT).getResource();
    assertThat(changeLogs).hasSize(2);

    secretChangeLog = changeLogs.get(0);
    assertThat(secretChangeLog.getAccountId()).isEqualTo(accountId);
    assertThat(secretChangeLog.getDescription()).isEqualTo("Changed name & secret");
    assertThat(secretChangeLog.getEncryptedDataId()).isEqualTo(secretFileId);
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(userName);
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(userEmail);

    secretChangeLog = changeLogs.get(1);
    assertThat(secretChangeLog.getAccountId()).isEqualTo(accountId);
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");
    assertThat(secretChangeLog.getEncryptedDataId()).isEqualTo(secretFileId);
    assertThat(secretChangeLog.getUser().getName()).isEqualTo(userName);
    assertThat(secretChangeLog.getUser().getEmail()).isEqualTo(userEmail);

    File newFileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
    when(httpServletRequest.getContentLengthLong()).thenReturn(newFileToSave.length());

    String newSecretFileId =
        secretManagementResource
            .saveFile(httpServletRequest, accountId, kmsId, secretName, new FileInputStream(fileToSave), null,
                JsonUtils.asJson(AdditionalMetadata.builder().build()), JsonUtils.asJson(new HashMap<>()), false, false)
            .getResource();
    configFile.setEncryptedFileId(newSecretFileId);
    configService.update(configFile, null);

    download = configService.download(appId, configFileId);
    assertThat(FileUtils.readFileToString(download, Charset.defaultCharset()))
        .isEqualTo(FileUtils.readFileToString(newFileToSave, Charset.defaultCharset()));
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @RealMongo
  public void multipleFileRefrence() throws IOException {
    final long seed = System.currentTimeMillis();
    log.info("seed: " + seed);
    Random r = new Random(seed);

    String secretName = generateUuid();
    File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
    SecretFile secretFile = SecretFile.builder()
                                .name(secretName)
                                .kmsId(kmsId)
                                .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToSave)))
                                .inheritScopesFromSM(true)
                                .build();
    String secretFileId = secretManager.saveSecretFile(accountId, secretFile);

    Query<EncryptedData> dataQuery = wingsPersistence.createQuery(EncryptedData.class)
                                         .filter(EncryptedDataKeys.type, CONFIG_FILE)
                                         .filter(EncryptedDataKeys.accountId, accountId);
    assertEncryptedData(secretName, secretFileId, dataQuery);

    int numOfVariable = 10;
    Set<String> variableIds = new HashSet<>();
    for (int i = 0; i < numOfVariable; i++) {
      Service service = Service.builder().name(generateUuid()).appId(appId).build();
      wingsPersistence.save(service);

      ConfigFile configFile = generateConfigFile(r, secretFileId, service);
      configFile.setAccountId(accountId);
      configFile.setAppId(appId);

      String configFileId = configService.save(configFile, null);
      variableIds.add(configFileId);

      dataQuery = wingsPersistence.createQuery(EncryptedData.class)
                      .filter(EncryptedDataKeys.type, CONFIG_FILE)
                      .filter(EncryptedDataKeys.accountId, accountId);
      assertThat(dataQuery.count()).isEqualTo(1);
      EncryptedData encryptedData = dataQuery.get();
      assertSecretData(secretName, encryptedData);
      assertThat(encryptedData.getParents()).hasSize(i + 1);
      assertThat(encryptedData.containsParent(configFileId, CONFIG_FILE)).isTrue();
    }

    int j = numOfVariable - 1;
    for (String variableId : variableIds) {
      configService.delete(appId, variableId);

      dataQuery = wingsPersistence.createQuery(EncryptedData.class)
                      .filter(EncryptedDataKeys.type, CONFIG_FILE)
                      .filter(EncryptedDataKeys.accountId, accountId);
      assertThat(dataQuery.count()).isEqualTo(1);
      EncryptedData encryptedData = dataQuery.get();
      assertSecretData(secretName, encryptedData);

      if (j == 0) {
        assertThat(isEmpty(encryptedData.getParents())).isTrue();
      } else {
        assertThat(encryptedData.getParents()).hasSize(j);
        assertThat(encryptedData.containsParent(variableId, CONFIG_FILE)).isFalse();
      }
      j--;
    }

    dataQuery = wingsPersistence.createQuery(EncryptedData.class)
                    .filter(EncryptedDataKeys.type, CONFIG_FILE)
                    .filter(EncryptedDataKeys.accountId, accountId);
    assertThat(dataQuery.count()).isEqualTo(1);
    EncryptedData data = dataQuery.get();
    assertSecretData(secretName, data);
    assertThat(isEmpty(data.getParents())).isTrue();
  }

  private ConfigFile generateConfigFile(Random r, String secretFileId, Service service) {
    return ConfigFile.builder()
        .templateId(generateUuid())
        .envId(generateUuid())
        .entityType(EntityType.SERVICE)
        .entityId(service.getUuid())
        .description(generateUuid())
        .parentConfigFileId(generateUuid())
        .relativeFilePath(generateUuid())
        .targetToAllEnv(r.nextBoolean())
        .defaultVersion(r.nextInt())
        .envIdVersionMapString(generateUuid())
        .setAsDefault(r.nextBoolean())
        .notes(generateUuid())
        .overridePath(generateUuid())
        .configOverrideType(ConfigOverrideType.CUSTOM)
        .configOverrideExpression(generateUuid())
        .encryptedFileId(secretFileId)
        .encrypted(true)
        .build();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @RealMongo
  public void deleteSecretFile() throws IOException, InterruptedException {
    final long seed = System.currentTimeMillis();
    log.info("seed: " + seed);
    Random r = new Random(seed);

    String secretName = generateUuid();
    File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
    SecretFile secretFile = SecretFile.builder()
                                .name(secretName)
                                .kmsId(kmsId)
                                .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToSave)))
                                .inheritScopesFromSM(true)
                                .build();
    String secretFileId = secretManager.saveSecretFile(accountId, secretFile);

    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .filter(EncryptedDataKeys.type, CONFIG_FILE)
                                     .filter(EncryptedDataKeys.accountId, accountId);
    assertEncryptedData(secretName, secretFileId, query);
    EncryptedData encryptedData;

    int numOfVariable = 10;
    Set<String> variableIds = new HashSet<>();
    for (int i = 0; i < numOfVariable; i++) {
      Service service = Service.builder().name(generateUuid()).appId(appId).build();
      wingsPersistence.save(service);

      ConfigFile configFile = generateConfigFile(r, secretFileId, service);
      configFile.setAccountId(accountId);
      configFile.setAppId(appId);

      String configFileId = configService.save(configFile, null);
      variableIds.add(configFileId);

      query = wingsPersistence.createQuery(EncryptedData.class)
                  .filter(EncryptedDataKeys.type, CONFIG_FILE)
                  .filter(EncryptedDataKeys.accountId, accountId);
      assertThat(query.count()).isEqualTo(1);
      encryptedData = query.get();
      assertSecretData(secretName, encryptedData);
      assertThat(encryptedData.getParents()).hasSize(i + 1);
      assertThat(encryptedData.containsParent(configFileId, CONFIG_FILE)).isTrue();
    }

    Set<String> remainingVariables = new HashSet<>(variableIds);
    int i = numOfVariable - 1;
    for (String variableId : variableIds) {
      Thread.sleep(50);

      remainingVariables.remove(variableId);
      configService.delete(appId, variableId);

      if (i == 0) {
        secretManagementResource.deleteSecret(accountId, secretFileId);
        query = wingsPersistence.createQuery(EncryptedData.class)
                    .filter(EncryptedDataKeys.type, CONFIG_FILE)
                    .filter(EncryptedDataKeys.accountId, accountId);
        assertThat(query.asList().isEmpty()).isTrue();
      } else {
        try {
          secretManagementResource.deleteFile(accountId, secretFileId);
          fail("Deleted referenced secret");
        } catch (WingsException e) {
          // expected
        }
        query = wingsPersistence.createQuery(EncryptedData.class)
                    .filter(EncryptedDataKeys.type, CONFIG_FILE)
                    .filter(EncryptedDataKeys.accountId, accountId);
        assertThat(query.count()).isEqualTo(1);
      }
      i--;
    }
  }

  private void assertEncryptedData(String secretName, String secretFileId, Query<EncryptedData> query) {
    List<EncryptedData> encryptedDataList = query.asList();
    assertThat(encryptedDataList).hasSize(1);
    EncryptedData encryptedData = encryptedDataList.get(0);
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();
    assertThat(isEmpty(encryptedData.getParents())).isTrue();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
    assertThat(encryptedData.getType()).isEqualTo(CONFIG_FILE);
    assertThat(encryptedData.getUuid()).isEqualTo(secretFileId);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @RealMongo
  public void deleteEncryptedConfigFile() throws IOException, InterruptedException {
    final long seed = System.currentTimeMillis();
    log.info("seed: " + seed);
    Random r = new Random(seed);

    String secretName = generateUuid();
    File fileToSave = new File("400-rest/src/test/resources/encryption/file_to_encrypt.txt");
    SecretFile secretFile = SecretFile.builder()
                                .name(secretName)
                                .kmsId(kmsId)
                                .fileContent(ByteStreams.toByteArray(new FileInputStream(fileToSave)))
                                .inheritScopesFromSM(true)
                                .build();
    String secretFileId = secretManager.saveSecretFile(accountId, secretFile);

    Query<EncryptedData> encrDataQuery = wingsPersistence.createQuery(EncryptedData.class)
                                             .filter(EncryptedDataKeys.type, CONFIG_FILE)
                                             .filter(EncryptedDataKeys.accountId, accountId);
    assertEncryptedData(secretName, secretFileId, encrDataQuery);

    Service service = Service.builder().name(generateUuid()).appId(appId).build();
    wingsPersistence.save(service);

    ConfigFile configFile = generateConfigFile(r, secretFileId, service);
    configFile.setAccountId(accountId);
    configFile.setAppId(appId);

    String configFileId = configService.save(configFile, null);

    encrDataQuery = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.type, CONFIG_FILE)
                        .filter(EncryptedDataKeys.accountId, accountId);
    assertThat(encrDataQuery.count()).isEqualTo(1);
    EncryptedData data = encrDataQuery.get();
    assertSecretData(secretName, data);

    try {
      secretManagementResource.deleteFile(accountId, secretFileId);
      fail("Deleted referenced secret");
    } catch (WingsException e) {
      // expected
    }

    configService.delete(appId, configFileId);
    Thread.sleep(2000);

    secretManagementResource.deleteSecret(accountId, secretFileId);
    encrDataQuery = wingsPersistence.createQuery(EncryptedData.class)
                        .filter(EncryptedDataKeys.type, CONFIG_FILE)
                        .filter(EncryptedDataKeys.accountId, accountId);
    assertThat(encrDataQuery.asList().isEmpty()).isTrue();
  }

  private void assertSecretData(String secretName, EncryptedData encryptedData) {
    assertThat(encryptedData.getName()).isEqualTo(secretName);
    assertThat(encryptedData.getEncryptionKey()).isNotNull();
    assertThat(encryptedData.getEncryptedValue()).isNotNull();
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.isEnabled()).isTrue();
    assertThat(encryptedData.getKmsId()).isEqualTo(kmsId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(encryptionType);
    assertThat(encryptedData.getType()).isEqualTo(CONFIG_FILE);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @RealMongo
  public void serviceVariableSearchTags() throws InterruptedException {
    String secretName = "name1";
    String secretValue = generateUuid();
    String secretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();

    int numOfServices = 2;
    int numOfServiceVariables = 3;

    List<String> serviceNames = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<String> appIds = new ArrayList<>();
    List<String> serviceVariableIds = new ArrayList<>();
    Set<String> serviceVariableNames = new HashSet<>();
    for (int i = 0; i < numOfServices; i++) {
      Service service = Service.builder().appId(appId).name("service-" + i).build();
      String serviceId = wingsPersistence.save(service);

      for (int j = 0; j < numOfServiceVariables; j++) {
        ServiceVariable serviceVariable = ServiceVariable.builder()
                                              .templateId(UUID.randomUUID().toString())
                                              .envId(GLOBAL_ENV_ID)
                                              .entityType(EntityType.SERVICE)
                                              .entityId(service.getUuid())
                                              .parentServiceVariableId(UUID.randomUUID().toString())
                                              .overrideType(OverrideType.ALL)
                                              .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                              .expression(UUID.randomUUID().toString())
                                              .accountId(accountId)
                                              .name("service_variable_" + i + "_j" + j)
                                              .value(secretId.toCharArray())
                                              .type(ServiceVariableType.ENCRYPTED_TEXT)
                                              .build();
        serviceVariableNames.add(serviceVariable.getName());
        serviceVariableIds.add(serviceVariableResource.save(appId, serviceVariable).getResource().getUuid());
        serviceIds.add(serviceId);
        serviceNames.add(service.getName());
        appIds.add(appId);
      }
    }

    Thread.sleep(2000);
    List<EncryptedData> encryptedDataList = wingsPersistence.createQuery(EncryptedData.class)
                                                .filter(EncryptedDataKeys.accountId, accountId)
                                                .filter(EncryptedDataKeys.type, SettingVariableTypes.SECRET_TEXT)
                                                .asList();
    assertThat(encryptedDataList).hasSize(1);

    EncryptedData encryptedData = encryptedDataList.get(0);
    assertThat(encryptedData.getAppIds()).isEqualTo(appIds);
    assertThat(encryptedData.getAppIds()).hasSize(numOfServices * numOfServiceVariables);
    Map<String, AtomicInteger> searchTags = encryptedData.getSearchTags();

    String appName = appService.get(appId).getName();
    assertThat(searchTags.containsKey(appName)).isTrue();
    assertThat(searchTags.get(appName).get()).isEqualTo(numOfServices * numOfServiceVariables);

    assertThat(encryptedData.getServiceIds()).isEqualTo(serviceIds);
    serviceNames.forEach(serviceName -> {
      assertThat(searchTags.containsKey(serviceName)).isTrue();
      assertThat(searchTags.get(serviceName).get()).isEqualTo(numOfServiceVariables);
    });

    assertThat(encryptedData.getServiceVariableIds().containsAll(serviceVariableIds)).isTrue();
    serviceVariableNames.forEach(servicevariableName -> {
      assertThat(searchTags.containsKey(servicevariableName)).isTrue();
      assertThat(searchTags.get(servicevariableName).get()).isEqualTo(1);
    });

    // update and test
    secretName = "name2";
    secretValue = generateUuid();
    String newSecretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();

    for (int i = 0; i < numOfServices; i++) {
      for (int j = 0; j < numOfServiceVariables; j++) {
        int serviceVariableIndex = i * numOfServiceVariables + j + 1;
        log.info("loop i: " + i + " j: " + j + " index: " + serviceVariableIndex);
        String serviceVariableId = serviceVariableIds.get(serviceVariableIndex - 1);
        ServiceVariable serviceVariable = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
        serviceVariable.setValue(newSecretId.toCharArray());
        serviceVariableResource.update(appId, serviceVariableId, serviceVariable);

        EncryptedData oldEncryptedData = wingsPersistence.get(EncryptedData.class, secretId);
        EncryptedData newEncryptedData = wingsPersistence.get(EncryptedData.class, newSecretId);
        if (serviceVariableIndex == numOfServices * numOfServiceVariables) {
          assertThat(isEmpty(oldEncryptedData.getAppIds())).isTrue();
        } else {
          assertThat(oldEncryptedData.getAppIds().size())
              .isEqualTo(numOfServices * numOfServiceVariables - serviceVariableIndex);
        }
        assertThat(newEncryptedData.getAppIds()).hasSize(serviceVariableIndex);

        if (serviceVariableIndex != numOfServices * numOfServiceVariables) {
          assertThat(oldEncryptedData.getSearchTags().get(appName).get())
              .isEqualTo(numOfServices * numOfServiceVariables - serviceVariableIndex);
          assertThat(newEncryptedData.getSearchTags().get(appName).get()).isEqualTo(serviceVariableIndex);
          String serviceId = serviceVariable.getEntityId();
          String serviceName = serviceResourceService.getWithDetails(appId, serviceId).getName();

          if (j != numOfServiceVariables - 1) {
            assertThat(oldEncryptedData.getSearchTags().get(serviceName).get())
                .isEqualTo(numOfServiceVariables - j - 1);
          } else {
            assertThat(oldEncryptedData.getSearchTags().get(serviceName)).isNull();
          }
          assertThat(newEncryptedData.getSearchTags().get(serviceName).get()).isEqualTo(j + 1);

          assertThat(oldEncryptedData.getSearchTags().get(serviceVariable.getName())).isNull();
          assertThat(newEncryptedData.getSearchTags().get(serviceVariable.getName()).get()).isEqualTo(1);
        } else {
          assertThat(oldEncryptedData.getSearchTags()).hasSize(1);
          assertThat(oldEncryptedData.getSearchTags().get("name1").get()).isEqualTo(1);
          assertThat(isEmpty(oldEncryptedData.getAppIds())).isTrue();
          assertThat(isEmpty(oldEncryptedData.getServiceIds())).isTrue();
        }
      }
    }

    // delete service variable and test
    for (int i = 0; i < numOfServices; i++) {
      for (int j = 0; j < numOfServiceVariables; j++) {
        int serviceVariableIndex = i * numOfServiceVariables + j + 1;
        log.info("loop i: " + i + " j: " + j + " index: " + serviceVariableIndex);
        String serviceVariableId = serviceVariableIds.get(serviceVariableIndex - 1);
        ServiceVariable serviceVariable = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
        serviceVariable.setValue(newSecretId.toCharArray());
        serviceVariableResource.delete(appId, serviceVariableId);

        EncryptedData newEncryptedData = wingsPersistence.get(EncryptedData.class, newSecretId);
        if (serviceVariableIndex == numOfServices * numOfServiceVariables) {
          assertThat(isEmpty(newEncryptedData.getAppIds())).isTrue();
        } else {
          assertThat(newEncryptedData.getAppIds().size())
              .isEqualTo(numOfServices * numOfServiceVariables - serviceVariableIndex);
        }
        if (serviceVariableIndex != numOfServices * numOfServiceVariables) {
          assertThat(newEncryptedData.getSearchTags().get(appName).get())
              .isEqualTo(numOfServices * numOfServiceVariables - serviceVariableIndex);
          String serviceId = serviceVariable.getEntityId();
          String serviceName = serviceResourceService.getWithDetails(appId, serviceId).getName();

          if (j != numOfServiceVariables - 1) {
            assertThat(newEncryptedData.getSearchTags().get(serviceName).get())
                .isEqualTo(numOfServiceVariables - j - 1);
          }

          assertThat(newEncryptedData.getSearchTags().get(serviceVariable.getName())).isNull();
        } else {
          assertThat(newEncryptedData.getSearchTags()).hasSize(1);
          assertThat(newEncryptedData.getSearchTags().get("name2").get()).isEqualTo(1);
          assertThat(isEmpty(newEncryptedData.getAppIds())).isTrue();
          assertThat(isEmpty(newEncryptedData.getServiceIds())).isTrue();
        }
      }
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @RealMongo
  public void serviceVariableTemplateSearchTags() {
    String secretName = "name1";
    String secretValue = generateUuid();
    String secretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();

    int numOfServices = 2;
    int numOfEnvs = 3;
    int numOfServiceVariables = 4;

    List<String> serviceNames = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<String> appIds = new ArrayList<>();
    List<String> serviceVariableTemplateIds = new ArrayList<>();
    List<String> serviceVariableIds = new ArrayList<>();
    List<String> envIds = new ArrayList<>();
    List<String> envNames = new ArrayList<>();
    Set<String> serviceVariableTemplateNames = new HashSet<>();
    for (int i = 0; i < numOfServices; i++) {
      Service service = Service.builder().appId(appId).name("service-" + i).build();
      String serviceId = wingsPersistence.save(service);

      for (int j = 0; j < numOfEnvs; j++) {
        String envId =
            wingsPersistence.save(Environment.Builder.anEnvironment().appId(appId).name("env-" + i + "-j" + j).build());
        ServiceTemplate serviceTemplate = aServiceTemplate()
                                              .withAppId(appId)
                                              .withServiceId(serviceId)
                                              .withEnvId(envId)
                                              .withName("serviceTemplate-" + i + "-j" + j)
                                              .build();
        String serviceTemplateId = wingsPersistence.save(serviceTemplate);
        envNames.add(serviceTemplate.getName());

        for (int k = 0; k < numOfServiceVariables; k++) {
          ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(UUID.randomUUID().toString())
                                                .envId(envId)
                                                .entityType(EntityType.SERVICE_TEMPLATE)
                                                .entityId(serviceTemplateId)
                                                .templateId(serviceTemplateId)
                                                .parentServiceVariableId(UUID.randomUUID().toString())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                                .expression(UUID.randomUUID().toString())
                                                .accountId(accountId)
                                                .name("service_variable_" + i + "_j" + j + "_k" + k)
                                                .value(secretId.toCharArray())
                                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                .build();
          serviceVariableTemplateNames.add(serviceTemplate.getName());
          serviceVariableIds.add(serviceVariableResource.save(appId, serviceVariable).getResource().getUuid());
          serviceVariableTemplateIds.add(serviceTemplateId);
          serviceIds.add(serviceId);
          serviceNames.add(service.getName());
          appIds.add(appId);
          envIds.add(envId);
        }
      }
    }

    EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedData.getAppIds()).isEqualTo(appIds);
    assertThat(encryptedData.getAppIds()).hasSize(numOfServices * numOfServiceVariables * numOfEnvs);
    Map<String, AtomicInteger> searchTags = encryptedData.getSearchTags();

    String appName = appService.get(appId).getName();
    assertThat(searchTags.containsKey(appName)).isTrue();
    assertThat(searchTags.get(appName).get()).isEqualTo(numOfServices * numOfServiceVariables * numOfEnvs);

    assertThat(encryptedData.getEnvIds()).isEqualTo(envIds);
    envNames.forEach(envName -> {
      assertThat(searchTags.containsKey(envName)).isTrue();
      assertThat(searchTags.get(envName).get()).isEqualTo(numOfServiceVariables);
    });

    assertThat(encryptedData.getServiceIds()).isEqualTo(serviceIds);
    serviceNames.forEach(serviceName -> {
      assertThat(searchTags.containsKey(serviceName)).isTrue();
      assertThat(searchTags.get(serviceName).get()).isEqualTo(numOfEnvs * numOfServiceVariables);
    });

    assertThat(encryptedData.getServiceVariableIds().containsAll(serviceVariableTemplateIds)).isTrue();
    serviceVariableTemplateNames.forEach(servicevariableName -> {
      assertThat(searchTags.containsKey(servicevariableName)).isTrue();
      assertThat(searchTags.get(servicevariableName).get()).isEqualTo(numOfServiceVariables);
    });

    // update and test
    secretName = "name2";
    secretValue = generateUuid();
    String newSecretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();

    for (int i = 0; i < numOfServices; i++) {
      for (int j = 0; j < numOfEnvs; j++) {
        for (int k = 0; k < numOfServiceVariables; k++) {
          int serviceVariableIndex = i * numOfEnvs * numOfServiceVariables + j * numOfServiceVariables + k + 1;
          log.info("loop i: " + i + " j: " + j + " k: " + k + " index: " + serviceVariableIndex);
          String serviceVariableId = serviceVariableIds.get(serviceVariableIndex - 1);
          ServiceVariable serviceVariable = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
          serviceVariable.setValue(newSecretId.toCharArray());
          serviceVariableResource.update(appId, serviceVariableId, serviceVariable);

          EncryptedData oldEncryptedData = wingsPersistence.get(EncryptedData.class, secretId);
          EncryptedData newEncryptedData = wingsPersistence.get(EncryptedData.class, newSecretId);
          assertThat(newEncryptedData.getAppIds()).hasSize(serviceVariableIndex);

          if (serviceVariableIndex != numOfServices * numOfEnvs * numOfServiceVariables) {
            assertThat(oldEncryptedData.getAppIds().size())
                .isEqualTo(numOfServices * numOfEnvs * numOfServiceVariables - serviceVariableIndex);
            assertThat(oldEncryptedData.getSearchTags().get(appName).get())
                .isEqualTo(numOfServices * numOfEnvs * numOfServiceVariables - serviceVariableIndex);
            assertThat(newEncryptedData.getSearchTags().get(appName).get()).isEqualTo(serviceVariableIndex);
            String serviceTemplateId = serviceVariable.getEntityId();
            ServiceTemplate serviceTemplate = wingsPersistence.get(ServiceTemplate.class, serviceTemplateId);
            String serviceId = serviceTemplate.getServiceId();
            String serviceName = serviceResourceService.getWithDetails(appId, serviceId).getName();
            if (j == numOfEnvs - 1 && k == numOfServiceVariables - 1) {
              assertThat(oldEncryptedData.getSearchTags().get(serviceName)).isNull();
            } else {
              assertThat(oldEncryptedData.getSearchTags().get(serviceName).get())
                  .isEqualTo(numOfEnvs * numOfServiceVariables - j * numOfServiceVariables - k - 1);
            }
            assertThat(newEncryptedData.getSearchTags().get(serviceName).get())
                .isEqualTo(j * numOfServiceVariables + k + 1);

            assertThat(oldEncryptedData.getSearchTags().get(serviceVariable.getName())).isNull();
            assertThat(newEncryptedData.getSearchTags().get(serviceTemplate.getName()).get()).isEqualTo(k + 1);
          } else {
            assertThat(oldEncryptedData.getSearchTags()).hasSize(1);
            assertThat(oldEncryptedData.getSearchTags().get("name1").get()).isEqualTo(1);
            assertThat(isEmpty(oldEncryptedData.getAppIds())).isTrue();
            assertThat(isEmpty(oldEncryptedData.getServiceIds())).isTrue();
          }
        }
      }
    }

    // delete service variable and test
    for (int i = 0; i < numOfServices; i++) {
      for (int j = 0; j < numOfEnvs; j++) {
        for (int k = 0; k < numOfServiceVariables; k++) {
          int serviceVariableIndex = i * numOfEnvs * numOfServiceVariables + j * numOfServiceVariables + k + 1;
          log.info("loop i: " + i + " j: " + j + " k: " + k + " index: " + serviceVariableIndex);
          String serviceVariableId = serviceVariableIds.get(serviceVariableIndex - 1);
          ServiceVariable serviceVariable = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
          serviceVariable.setValue(newSecretId.toCharArray());
          serviceVariableResource.delete(appId, serviceVariableId);

          EncryptedData newEncryptedData = wingsPersistence.get(EncryptedData.class, newSecretId);

          if (serviceVariableIndex != numOfServices * numOfEnvs * numOfServiceVariables) {
            assertThat(newEncryptedData.getAppIds().size())
                .isEqualTo(numOfServices * numOfEnvs * numOfServiceVariables - serviceVariableIndex);
            assertThat(newEncryptedData.getSearchTags().get(appName).get())
                .isEqualTo(numOfServices * numOfEnvs * numOfServiceVariables - serviceVariableIndex);
            String serviceTemplateId = serviceVariable.getEntityId();
            ServiceTemplate serviceTemplate = wingsPersistence.get(ServiceTemplate.class, serviceTemplateId);
            String serviceId = serviceTemplate.getServiceId();
            String serviceName = serviceResourceService.getWithDetails(appId, serviceId).getName();

            if (j == numOfEnvs - 1 && k == numOfServiceVariables - 1) {
              assertThat(newEncryptedData.getSearchTags().get(serviceName)).isNull();
            } else {
              assertThat(newEncryptedData.getSearchTags().get(serviceName).get())
                  .isEqualTo(numOfEnvs * numOfServiceVariables - j * numOfServiceVariables - k - 1);
            }

            assertThat(newEncryptedData.getSearchTags().get(serviceVariable.getName())).isNull();
          } else {
            assertThat(newEncryptedData.getSearchTags()).hasSize(1);
            assertThat(newEncryptedData.getSearchTags().get("name2").get()).isEqualTo(1);
            assertThat(isEmpty(newEncryptedData.getAppIds())).isTrue();
            assertThat(isEmpty(newEncryptedData.getServiceIds())).isTrue();
          }
        }
      }
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @RealMongo
  public void serviceVariableEnvironmentSearchTags() {
    String secretName = "name1";
    String secretValue = generateUuid();
    String secretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();

    EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedData.getSearchTags()).hasSize(1);
    assertThat(encryptedData.getKeywords()).hasSize(1);
    assertThat(encryptedData.getKeywords().get(0)).isEqualTo(secretName);
    int numOfEnvs = 3;
    int numOfServiceVariables = 4;

    List<String> appIds = new ArrayList<>();
    List<String> serviceVariableIds = new ArrayList<>();
    List<String> envIds = new ArrayList<>();
    List<String> envNames = new ArrayList<>();

    for (int j = 0; j < numOfEnvs; j++) {
      String envId = wingsPersistence.save(Environment.Builder.anEnvironment().appId(appId).name("env-j" + j).build());
      envNames.add("env-j" + j);

      for (int k = 0; k < numOfServiceVariables; k++) {
        ServiceVariable serviceVariable = ServiceVariable.builder()
                                              .templateId(UUID.randomUUID().toString())
                                              .envId(envId)
                                              .entityType(EntityType.ENVIRONMENT)
                                              .entityId(envId)
                                              .parentServiceVariableId(UUID.randomUUID().toString())
                                              .overrideType(OverrideType.ALL)
                                              .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                              .expression(UUID.randomUUID().toString())
                                              .accountId(accountId)
                                              .name("service_variable_j" + j + "_k" + k)
                                              .value(secretId.toCharArray())
                                              .type(ServiceVariableType.ENCRYPTED_TEXT)
                                              .build();
        serviceVariableIds.add(serviceVariableResource.save(appId, serviceVariable).getResource().getUuid());
        appIds.add(appId);
        envIds.add(envId);
      }
    }

    encryptedData = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedData.getAppIds()).isEqualTo(appIds);
    assertThat(encryptedData.getAppIds()).hasSize(numOfServiceVariables * numOfEnvs);
    Map<String, AtomicInteger> searchTags = encryptedData.getSearchTags();

    String appName = appService.get(appId).getName();
    assertThat(searchTags.containsKey(appName)).isTrue();
    assertThat(searchTags.get(appName).get()).isEqualTo(numOfServiceVariables * numOfEnvs);

    assertThat(encryptedData.getEnvIds()).isEqualTo(envIds);
    envNames.forEach(envName -> {
      assertThat(searchTags.containsKey(envName)).isTrue();
      assertThat(searchTags.get(envName).get()).isEqualTo(numOfServiceVariables);
    });

    // update and test
    secretName = "name2";
    secretValue = generateUuid();
    String newSecretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();

    for (int j = 0; j < numOfEnvs; j++) {
      for (int k = 0; k < numOfServiceVariables; k++) {
        int serviceVariableIndex = j * numOfServiceVariables + k + 1;
        log.info("loop j: " + j + " k: " + k + " index: " + serviceVariableIndex);
        String serviceVariableId = serviceVariableIds.get(serviceVariableIndex - 1);
        ServiceVariable serviceVariable = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
        serviceVariable.setValue(newSecretId.toCharArray());
        serviceVariableResource.update(appId, serviceVariableId, serviceVariable);

        EncryptedData oldEncryptedData = wingsPersistence.get(EncryptedData.class, secretId);
        EncryptedData newEncryptedData = wingsPersistence.get(EncryptedData.class, newSecretId);
        assertThat(newEncryptedData.getAppIds()).hasSize(serviceVariableIndex);

        if (serviceVariableIndex != numOfEnvs * numOfServiceVariables) {
          assertThat(oldEncryptedData.getAppIds()).hasSize(numOfEnvs * numOfServiceVariables - serviceVariableIndex);
          assertThat(oldEncryptedData.getSearchTags().get(appName).get())
              .isEqualTo(numOfEnvs * numOfServiceVariables - serviceVariableIndex);
          assertThat(newEncryptedData.getSearchTags().get(appName).get()).isEqualTo(serviceVariableIndex);
          String envId = serviceVariable.getEntityId();
          String envName = environmentService.get(appId, envId).getName();
          if (k == numOfServiceVariables - 1) {
            assertThat(oldEncryptedData.getSearchTags().get(envName)).isNull();
          } else {
            assertThat(oldEncryptedData.getSearchTags().get(envName).get()).isEqualTo(numOfServiceVariables - k - 1);
          }
          assertThat(oldEncryptedData.getSearchTags().get(serviceVariable.getName())).isNull();
          assertThat(newEncryptedData.getSearchTags().get(envName).get()).isEqualTo(k + 1);
        } else {
          assertThat(oldEncryptedData.getSearchTags()).hasSize(1);
          assertThat(oldEncryptedData.getKeywords()).hasSize(1);
          assertThat(oldEncryptedData.getKeywords().get(0)).isEqualTo("name1");
          assertThat(isEmpty(oldEncryptedData.getAppIds())).isTrue();
          assertThat(isEmpty(oldEncryptedData.getServiceIds())).isTrue();
        }
      }
    }

    // delete service variable and test
    for (int j = 0; j < numOfEnvs; j++) {
      for (int k = 0; k < numOfServiceVariables; k++) {
        int serviceVariableIndex = j * numOfServiceVariables + k + 1;
        log.info("loop  j: " + j + " k: " + k + " index: " + serviceVariableIndex);
        String serviceVariableId = serviceVariableIds.get(serviceVariableIndex - 1);
        ServiceVariable serviceVariable = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
        serviceVariable.setValue(newSecretId.toCharArray());
        serviceVariableResource.delete(appId, serviceVariableId);

        EncryptedData newEncryptedData = wingsPersistence.get(EncryptedData.class, newSecretId);

        if (serviceVariableIndex != numOfEnvs * numOfServiceVariables) {
          assertThat(newEncryptedData.getAppIds()).hasSize(numOfEnvs * numOfServiceVariables - serviceVariableIndex);
          assertThat(newEncryptedData.getSearchTags().get(appName).get())
              .isEqualTo(numOfEnvs * numOfServiceVariables - serviceVariableIndex);
          String envId = serviceVariable.getEntityId();
          String envName = environmentService.get(appId, envId).getName();

          if (k == numOfServiceVariables - 1) {
            assertThat(newEncryptedData.getSearchTags().get(envName)).isNull();
          } else {
            assertThat(newEncryptedData.getSearchTags().get(envName).get()).isEqualTo(numOfServiceVariables - k - 1);
          }

          assertThat(newEncryptedData.getSearchTags().get(serviceVariable.getName())).isNull();
        } else {
          assertThat(newEncryptedData.getSearchTags()).hasSize(1);
          assertThat(newEncryptedData.getKeywords()).hasSize(1);
          assertThat(newEncryptedData.getKeywords().get(0)).isEqualTo("name2");
          assertThat(isEmpty(newEncryptedData.getAppIds())).isTrue();
          assertThat(isEmpty(newEncryptedData.getServiceIds())).isTrue();
        }
      }
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void serviceVariableSyncSearchTags() {
    String secretName = generateUuid();
    String secretValue = generateUuid();
    String secretId =
        secretManagementResource.saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
            .getResource();

    int numOfServices = 3;
    int numOfServiceVariables = 3;

    List<String> serviceNames = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<String> appIds = new ArrayList<>();
    List<String> serviceVariableIds = new ArrayList<>();
    Set<String> serviceVariableNames = new HashSet<>();
    for (int i = 0; i < numOfServices; i++) {
      Service service = Service.builder().appId(appId).name("service-" + i).build();
      String serviceId = wingsPersistence.save(service);

      for (int j = 0; j < numOfServiceVariables; j++) {
        ServiceVariable serviceVariable = ServiceVariable.builder()
                                              .templateId(UUID.randomUUID().toString())
                                              .envId(GLOBAL_ENV_ID)
                                              .entityType(EntityType.SERVICE)
                                              .entityId(service.getUuid())
                                              .parentServiceVariableId(UUID.randomUUID().toString())
                                              .overrideType(OverrideType.ALL)
                                              .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                              .expression(UUID.randomUUID().toString())
                                              .accountId(accountId)
                                              .name("service_variable_" + i + "_j" + j)
                                              .value(secretId.toCharArray())
                                              .type(ServiceVariableType.ENCRYPTED_TEXT)
                                              .build();
        serviceVariableNames.add(serviceVariable.getName());
        serviceVariableIds.add(serviceVariableResource.save(appId, serviceVariable).getResource().getUuid());
        serviceIds.add(serviceId);
        serviceNames.add(service.getName());
        appIds.add(appId);
      }
    }

    EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, secretId);
    encryptedData.clearSearchTags();
    wingsPersistence.save(encryptedData);

    encryptedData = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(isEmpty(encryptedData.getAppIds())).isTrue();
    assertThat(isEmpty(encryptedData.getServiceIds())).isTrue();
    assertThat(isEmpty(encryptedData.getEnvIds())).isTrue();
    assertThat(encryptedData.getServiceVariableIds()).isNull();
    assertThat(encryptedData.getSearchTags()).isNull();

    serviceVariableService.updateSearchTagsForSecrets(accountId);

    encryptedData = wingsPersistence.get(EncryptedData.class, secretId);
    assertThat(encryptedData.getAppIds()).isEqualTo(appIds);
    assertThat(encryptedData.getAppIds()).hasSize(numOfServices * numOfServiceVariables);
    Map<String, AtomicInteger> searchTags = encryptedData.getSearchTags();

    String appName = appService.get(appId).getName();
    assertThat(searchTags.containsKey(appName)).isTrue();
    assertThat(searchTags.get(appName).get()).isEqualTo(numOfServices * numOfServiceVariables);

    assertThat(CollectionUtils.isEqualCollection(serviceIds, encryptedData.getServiceIds())).isTrue();
    serviceNames.forEach(serviceName -> {
      assertThat(searchTags.containsKey(serviceName)).isTrue();
      assertThat(searchTags.get(serviceName).get()).isEqualTo(numOfServiceVariables);
    });

    assertThat(encryptedData.getServiceVariableIds().containsAll(serviceVariableIds)).isTrue();
    serviceVariableNames.forEach(servicevariableName -> {
      assertThat(searchTags.containsKey(servicevariableName)).isTrue();
      assertThat(searchTags.get(servicevariableName).get()).isEqualTo(1);
    });
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void filterSecretSearchTags() {
    int numOfServiceVariables = 6;
    int numOfSecrets = numOfServiceVariables * 5;
    List<String> secretIds = new ArrayList<>();
    List<SecretText> secretTexts = new ArrayList<>();
    for (int i = 0; i < numOfSecrets; i++) {
      SecretText secretText = SecretText.builder().name(generateUuid()).value(generateUuid()).build();
      secretIds.add(secretManagementResource.saveSecret(accountId, secretText).getResource());
      secretTexts.add(secretText);
    }
    int numOfServices = 5;
    int numOfEnvs = 4;

    List<String> serviceNames = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<String> appIds = new ArrayList<>();
    List<String> serviceVariableTemplateIds = new ArrayList<>();
    List<String> serviceVariableIds = new ArrayList<>();
    List<String> envIds = new ArrayList<>();
    List<String> envNames = new ArrayList<>();
    Set<String> serviceVariableTemplateNames = new HashSet<>();
    for (int i = 0; i < numOfServices; i++) {
      Service service = Service.builder().appId(appId).name("service-" + i).build();
      String serviceId = wingsPersistence.save(service);

      for (int j = 0; j < numOfEnvs; j++) {
        String envId = wingsPersistence.save(
            Environment.Builder.anEnvironment().appId(appId).name("environment-" + i + "-" + j).build());
        ServiceTemplate serviceTemplate = aServiceTemplate()
                                              .withAppId(appId)
                                              .withServiceId(serviceId)
                                              .withEnvId(envId)
                                              .withName("serviceTemplate-" + i + "-" + j)
                                              .build();
        String serviceTemplateId = wingsPersistence.save(serviceTemplate);
        envNames.add(serviceTemplate.getName());

        for (int k = 0; k < numOfServiceVariables; k++) {
          ServiceVariable serviceVariable = ServiceVariable.builder()
                                                .templateId(UUID.randomUUID().toString())
                                                .envId(envId)
                                                .entityType(EntityType.SERVICE_TEMPLATE)
                                                .entityId(serviceTemplateId)
                                                .templateId(serviceTemplateId)
                                                .parentServiceVariableId(UUID.randomUUID().toString())
                                                .overrideType(OverrideType.ALL)
                                                .instances(Collections.singletonList(UUID.randomUUID().toString()))
                                                .expression(UUID.randomUUID().toString())
                                                .accountId(accountId)
                                                .name("service_variable_" + i + "_" + j + "_" + k)
                                                .value(secretIds.get(j * numOfServiceVariables).toCharArray())
                                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                                .build();
          serviceVariableTemplateNames.add(serviceTemplate.getName());
          serviceVariableIds.add(serviceVariableResource.save(appId, serviceVariable).getResource().getUuid());
          serviceVariableTemplateIds.add(serviceTemplateId);
          serviceIds.add(serviceId);
          serviceNames.add(service.getName());
          appIds.add(appId);
          envIds.add(envId);
        }
      }
    }

    String appName = appService.get(appId).getName();
    PageRequest<EncryptedData> pageRequest = aPageRequest()
                                                 .addFilter("accountId", Operator.EQ, accountId)
                                                 .addFilter("keywords", Operator.CONTAINS, appName)
                                                 .build();
    List<EncryptedData> encryptedDataList =
        secretManagementResource
            .listSecrets(accountId, SettingVariableTypes.SECRET_TEXT, null, null, true, false, pageRequest)
            .getResource();
    assertThat(encryptedDataList).hasSize(numOfEnvs);

    pageRequest = aPageRequest()
                      .addFilter("accountId", Operator.EQ, accountId)
                      .addFilter("keywords", Operator.CONTAINS, "service-variable")
                      .build();

    encryptedDataList =
        secretManagementResource
            .listSecrets(accountId, SettingVariableTypes.SECRET_TEXT, null, null, true, false, pageRequest)
            .getResource();
    assertThat(encryptedDataList).isEmpty();

    pageRequest = aPageRequest()
                      .addFilter("accountId", Operator.EQ, accountId)
                      .addFilter("keywords", Operator.CONTAINS, "environment")
                      .build();
    encryptedDataList =
        secretManagementResource
            .listSecrets(accountId, SettingVariableTypes.SECRET_TEXT, null, null, true, false, pageRequest)
            .getResource();
    assertThat(encryptedDataList).hasSize(numOfEnvs);

    pageRequest = aPageRequest()
                      .addFilter("accountId", Operator.EQ, accountId)
                      .addFilter("keywords", Operator.CONTAINS, "environment-0")
                      .build();
    encryptedDataList =
        secretManagementResource
            .listSecrets(accountId, SettingVariableTypes.SECRET_TEXT, null, null, true, false, pageRequest)
            .getResource();
    assertThat(encryptedDataList).hasSize(numOfEnvs);
    for (int i = 0; i < numOfServices; i++) {
      for (int j = 0; j < numOfEnvs; j++) {
        pageRequest = aPageRequest()
                          .addFilter("accountId", Operator.EQ, accountId)
                          .addFilter("keywords", Operator.CONTAINS, "environment-" + i + "-" + j)
                          .build();
        encryptedDataList =
            secretManagementResource
                .listSecrets(accountId, SettingVariableTypes.SECRET_TEXT, null, null, true, false, pageRequest)
                .getResource();
        assertThat(encryptedDataList).hasSize(1);
        assertThat(encryptedDataList.get(0).getName()).isEqualTo(secretTexts.get(j * numOfServiceVariables).getName());

        pageRequest = aPageRequest()
                          .addFilter("accountId", Operator.EQ, accountId)
                          .addFilter("keywords", Operator.CONTAINS, "serviceTemplate-" + i + "-" + j)
                          .build();
        encryptedDataList =
            secretManagementResource
                .listSecrets(accountId, SettingVariableTypes.SECRET_TEXT, null, null, true, false, pageRequest)
                .getResource();
        assertThat(encryptedDataList).hasSize(1);
        assertThat(encryptedDataList.get(0).getName()).isEqualTo(secretTexts.get(j * numOfServiceVariables).getName());
      }
    }
  }
}
