/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.cyberark;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.UTKARSH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.encryptors.clients.LocalEncryptor;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secrets.SecretService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoSerializer;

import software.wings.EncryptTestUtils;
import software.wings.SecretManagementTestHelper;
import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.CyberArkConfig;
import software.wings.beans.Event.Type;
import software.wings.beans.KmsConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.features.api.PremiumFeature;
import software.wings.resources.secretsmanagement.CyberArkResource;
import software.wings.resources.secretsmanagement.SecretManagementResource;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.security.GlobalEncryptDecryptClient;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.CyberArkService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.KmsService;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
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
 * @author marklu on 2019-08-09
 */
@RunWith(Parameterized.class)
public class CyberArkTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private GlobalEncryptDecryptClient globalEncryptDecryptClient;
  @Mock private KmsEncryptor kmsEncryptor;
  @Mock private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Mock private PremiumFeature secretsManagementFeature;
  @Mock private SecretManagementDelegateService secretManagementDelegateService;
  @Mock private VaultEncryptor vaultEncryptor;
  @Mock private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Mock protected AuditServiceHelper auditServiceHelper;

  @Inject @InjectMocks private CyberArkService cyberArkService;
  @Inject @InjectMocks private KmsService kmsService;
  @Inject @InjectMocks private SecretManagerConfigService secretManagerConfigService;
  @Inject @InjectMocks private SecretService secretService;
  @Inject private CyberArkResource cyberArkResource;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private LocalEncryptor localEncryptor;
  @Inject private LocalSecretManagerService localSecretManagerService;
  @Inject private SecretManagementResource secretManagementResource;
  @Inject private SecretManagementTestHelper secretManagementTestHelper;
  @Inject private SecretManager secretManager;
  @Inject private HPersistence persistence;
  @Inject protected EncryptionService encryptionService;

  private final int numOfEncryptedValsForCyberArk = 1;
  private final int numOfEncryptedValsForCyberKms = 3;
  private final String userEmail = "mark.lu@harness.io";
  private final String userName = "mark.lu";
  private final User user = User.Builder.anUser().email(userEmail).name(userName).build();
  private String userId;
  private String accountId;
  private String kmsId;
  private KmsConfig kmsConfig;
  private LocalEncryptionConfig localEncryptionConfig;

  @Parameter public boolean isGlobalKmsEnabled;

  @Parameters
  public static Collection<Object[]> data() {
    return asList(new Object[][] {{true}, {false}});
  }

  @Before
  public void setup() throws IOException, NoSuchFieldException, IllegalAccessException {
    initMocks(this);

    Account account = getAccount(AccountType.PAID);
    accountId = account.getUuid();
    when(accountService.get(accountId)).thenReturn(account);

    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);

    when(kmsEncryptor.encryptSecret(anyString(), anyObject(), any())).then(invocation -> {
      Object[] args = invocation.getArguments();
      if (args[2] instanceof KmsConfig) {
        return EncryptTestUtils.encrypt((String) args[0], ((String) args[1]).toCharArray(), (KmsConfig) args[2]);
      }
      return localEncryptor.encryptSecret(
          (String) args[0], (String) args[1], localSecretManagerService.getEncryptionConfig((String) args[0]));
    });

    when(kmsEncryptor.fetchSecretValue(anyString(), anyObject(), any())).then(invocation -> {
      Object[] args = invocation.getArguments();
      if (args[2] instanceof KmsConfig) {
        return EncryptTestUtils.decrypt((EncryptedRecord) args[1], (KmsConfig) args[2]);
      }
      return localEncryptor.fetchSecretValue(
          (String) args[0], (EncryptedRecord) args[1], localSecretManagerService.getEncryptionConfig((String) args[0]));
    });

    when(vaultEncryptor.validateReference(anyString(), any(SecretText.class), anyObject())).thenReturn(true);
    when(kmsEncryptorsRegistry.getKmsEncryptor(any())).thenReturn(kmsEncryptor);
    when(vaultEncryptorsRegistry.getVaultEncryptor(any())).thenReturn(vaultEncryptor);
    when(delegateProxyFactory.get(eq(SecretManagementDelegateService.class), any(SyncTaskContext.class)))
        .thenReturn(secretManagementDelegateService);
    when(secretManagementDelegateService.validateCyberArkConfig(any(CyberArkConfig.class))).then(invocation -> {
      Object[] args = invocation.getArguments();
      return secretManagementTestHelper.validateCyberArkConfig((CyberArkConfig) args[0]);
    });

    FieldUtils.writeField(cyberArkService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(kmsService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(persistence, "secretManager", secretManager, true);
    FieldUtils.writeField(cyberArkResource, "cyberArkService", cyberArkService, true);
    FieldUtils.writeField(secretManagementResource, "secretManager", secretManager, true);
    FieldUtils.writeField(secretService, "kmsRegistry", kmsEncryptorsRegistry, true);
    FieldUtils.writeField(secretService, "vaultRegistry", vaultEncryptorsRegistry, true);
    FieldUtils.writeField(encryptionService, "kmsEncryptorsRegistry", kmsEncryptorsRegistry, true);
    FieldUtils.writeField(encryptionService, "vaultEncryptorsRegistry", vaultEncryptorsRegistry, true);
    userId = persistence.save(user);
    UserThreadLocal.set(user);

    if (isGlobalKmsEnabled) {
      kmsConfig = secretManagementTestHelper.getKmsConfig();
      kmsConfig.setName("Global KMS");
      kmsConfig.setAccountId(Account.GLOBAL_ACCOUNT_ID);
      kmsId = kmsService.saveGlobalKmsConfig(accountId, kmsConfig);
      kmsConfig = kmsService.getKmsConfig(accountId, kmsId);
    } else {
      localEncryptionConfig = localSecretManagerService.getEncryptionConfig(accountId);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void validateConfig() {
    CyberArkConfig cyberArkConfig = secretManagementTestHelper.getCyberArkConfig("invalidCertificate");
    cyberArkConfig.setAccountId(accountId);

    try {
      cyberArkResource.saveCyberArkConfig(cyberArkConfig.getAccountId(), cyberArkConfig);
      fail("Saved invalid CyberArk config");
    } catch (WingsException e) {
      assertThat(true).isTrue();
    }

    cyberArkConfig = secretManagementTestHelper.getCyberArkConfig();
    cyberArkConfig.setAccountId(accountId);
    cyberArkConfig.setCyberArkUrl("invalidUrl");

    try {
      cyberArkResource.saveCyberArkConfig(cyberArkConfig.getAccountId(), cyberArkConfig);
      fail("Saved invalid CyberArk config");
    } catch (WingsException e) {
      assertThat(true).isTrue();
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void getCyberArkConfigForAccount() {
    CyberArkConfig cyberArkConfig = secretManagementTestHelper.getCyberArkConfig();
    cyberArkConfig.setAccountId(accountId);

    cyberArkResource.saveCyberArkConfig(cyberArkConfig.getAccountId(), cyberArkConfig);

    CyberArkConfig savedConfig = (CyberArkConfig) secretManagerConfigService.getSecretManager(
        cyberArkConfig.getAccountId(), cyberArkConfig.getUuid());
    assertThat(savedConfig.getName()).isEqualTo(cyberArkConfig.getName());
    assertThat(savedConfig.getAccountId()).isEqualTo(cyberArkConfig.getAccountId());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveAndEditConfig() {
    InputStream inputStream = CyberArkTest.class.getResourceAsStream("/certs/clientCert.pem");
    String clientCertificate = null;
    try {
      clientCertificate = IOUtils.toString(inputStream, "UTF-8");
    } catch (IOException e) {
      // Should not happen.
    }

    CyberArkConfig cyberArkConfig = saveCyberArkConfig(clientCertificate);
    String name = cyberArkConfig.getName();

    CyberArkConfig savedConfig = (CyberArkConfig) secretManagerConfigService.getSecretManager(
        cyberArkConfig.getAccountId(), cyberArkConfig.getUuid());
    assertThat(savedConfig.getName()).isEqualTo(name);
    List<EncryptedData> encryptedDataList = persistence.createQuery(EncryptedData.class, excludeAuthority).asList();
    if (isGlobalKmsEnabled) {
      assertThat(encryptedDataList).hasSize(numOfEncryptedValsForCyberArk + numOfEncryptedValsForCyberKms);
    } else {
      assertThat(encryptedDataList).hasSize(numOfEncryptedValsForCyberArk);
      for (EncryptedData encryptedData : encryptedDataList) {
        assertThat(name + "_clientCertificate").isEqualTo(encryptedData.getName());
        assertThat(encryptedData.getParents()).hasSize(1);
        assertThat(encryptedData.containsParent(savedConfig.getUuid(), SettingVariableTypes.CYBERARK)).isTrue();
      }
    }

    name = UUID.randomUUID().toString();
    CyberArkConfig newConfig = secretManagementTestHelper.getCyberArkConfig();
    savedConfig.setClientCertificate(newConfig.getClientCertificate());
    savedConfig.setName(name);
    cyberArkResource.saveCyberArkConfig(accountId, savedConfig);
    encryptedDataList =
        persistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId).asList();
    assertThat(encryptedDataList).hasSize(numOfEncryptedValsForCyberArk);
    for (EncryptedData encryptedData : encryptedDataList) {
      assertThat(encryptedData.getParents()).hasSize(1);
      assertThat(encryptedData.containsParent(savedConfig.getUuid(), SettingVariableTypes.CYBERARK)).isTrue();
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testEncryptDecryptArtifactoryConfig() {
    String queryAsPath = "Address=components;Username=svc_account";
    String secretName = "TestSecret";

    CyberArkConfig cyberArkConfig = saveCyberArkConfig();
    SecretText secretText = SecretText.builder()
                                .name(secretName)
                                .kmsId(cyberArkConfig.getUuid())
                                .path(queryAsPath)
                                .inheritScopesFromSM(true)
                                .hideFromListing(true)
                                .build();
    // Encrypt of path reference will use a CyberArk decryption to validate the reference
    String encryptedDataId = secretManager.saveSecretText(accountId, secretText, true);
    EncryptedData encryptedData = secretManager.getSecretById(accountId, encryptedDataId);

    assertThat(encryptedData).isNotNull();
    assertThat(encryptedData.getEncryptionType()).isEqualTo(EncryptionType.CYBERARK);
    assertThat(encryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
    assertThat(encryptedData.getEncryptedValue()).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveAndEditConfig_withMaskedSecrets_changeNameDefaultOnly() {
    String name = UUID.randomUUID().toString();
    CyberArkConfig cyberArkConfig = secretManagementTestHelper.getCyberArkConfig();
    cyberArkConfig.setName(name);
    cyberArkConfig.setAccountId(accountId);

    String configId = cyberArkService.saveConfig(accountId, kryoSerializer.clone(cyberArkConfig));

    CyberArkConfig savedConfig = (CyberArkConfig) secretManagerConfigService.getSecretManager(accountId, configId);
    assertThat(savedConfig.getClientCertificate()).isEqualTo(cyberArkConfig.getClientCertificate());
    assertThat(savedConfig.getAppId()).isEqualTo(cyberArkConfig.getAppId());
    assertThat(savedConfig.getName()).isEqualTo(cyberArkConfig.getName());
    assertThat(savedConfig.isDefault()).isEqualTo(false);

    String newName = UUID.randomUUID().toString();
    cyberArkConfig.setUuid(savedConfig.getUuid());
    cyberArkConfig.setName(newName);
    cyberArkConfig.setDefault(false);
    cyberArkConfig.maskSecrets();

    // Masked Secrets, only name and default flag should be updated.
    cyberArkService.saveConfig(accountId, kryoSerializer.clone(cyberArkConfig));

    CyberArkConfig modifiedSavedConfig = cyberArkService.getConfig(accountId, savedConfig.getUuid());
    assertThat(modifiedSavedConfig.getClientCertificate()).isEqualTo(savedConfig.getClientCertificate());
    assertThat(modifiedSavedConfig.getAppId()).isEqualTo(savedConfig.getAppId());
    assertThat(modifiedSavedConfig.getName()).isEqualTo(cyberArkConfig.getName());
    assertThat(modifiedSavedConfig.isDefault()).isEqualTo(false);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void cyberArkSecretManager_Crud_shouldGenerate_Audit() {
    if (isGlobalKmsEnabled) {
      verify(auditServiceHelper)
          .reportForAuditingUsingAccountId(
              eq(Account.GLOBAL_ACCOUNT_ID), eq(null), any(KmsConfig.class), eq(Type.CREATE));
    }

    String name = UUID.randomUUID().toString();
    CyberArkConfig cyberArkConfig = secretManagementTestHelper.getCyberArkConfig();
    cyberArkConfig.setName(name);
    cyberArkConfig.setAccountId(accountId);

    String secretManagerId = cyberArkService.saveConfig(accountId, kryoSerializer.clone(cyberArkConfig));
    verify(auditServiceHelper)
        .reportForAuditingUsingAccountId(eq(accountId), eq(null), any(CyberArkConfig.class), eq(Type.CREATE));

    cyberArkConfig.setUuid(secretManagerId);
    cyberArkConfig.setDefault(false);
    cyberArkConfig.setName(cyberArkConfig.getName() + "_Updated");
    cyberArkService.saveConfig(accountId, kryoSerializer.clone(cyberArkConfig));
    verify(auditServiceHelper)
        .reportForAuditingUsingAccountId(
            eq(accountId), any(CyberArkConfig.class), any(CyberArkConfig.class), eq(Type.UPDATE));

    cyberArkService.deleteConfig(accountId, secretManagerId);
    verify(auditServiceHelper).reportDeleteForAuditingUsingAccountId(eq(accountId), any(CyberArkConfig.class));
  }

  private CyberArkConfig saveCyberArkConfig() {
    return saveCyberArkConfig(null);
  }

  private CyberArkConfig saveCyberArkConfig(String clientCertificate) {
    String name = UUID.randomUUID().toString();
    CyberArkConfig cyberArkConfig = secretManagementTestHelper.getCyberArkConfig();
    cyberArkConfig.setName(name);
    cyberArkConfig.setAccountId(accountId);
    cyberArkConfig.setClientCertificate(clientCertificate);

    cyberArkResource.saveCyberArkConfig(cyberArkConfig.getAccountId(), cyberArkConfig);
    return cyberArkConfig;
  }
}
