/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.beans.SecretManagerCapabilities.CREATE_PARAMETERIZED_SECRET;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.MigrateSecretTask;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretScopeMetadata;
import io.harness.beans.SecretText;
import io.harness.beans.SecretText.SecretTextBuilder;
import io.harness.category.element.UnitTests;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueuePublisher;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secrets.SecretService;
import io.harness.secrets.SecretServiceImpl;
import io.harness.secrets.SecretsAuditService;
import io.harness.secrets.SecretsDao;
import io.harness.secrets.SecretsDaoImpl;
import io.harness.secrets.SecretsFileService;
import io.harness.secrets.SecretsRBACService;
import io.harness.secrets.setupusage.SecretSetupUsageBuilderRegistry;
import io.harness.secrets.setupusage.SecretSetupUsageService;
import io.harness.secrets.setupusage.SecretSetupUsageServiceImpl;
import io.harness.secrets.validation.SecretValidatorsRegistry;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.SecretManagerType;
import io.harness.serializer.KryoSerializerWrapper;

import software.wings.beans.KmsConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.VaultConfig;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.UsageRestrictions;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(HarnessTeam.PL)
public class SecretTextTest extends SMCoreTestBase {
  @Inject private HPersistence hPersistence;
  @Inject private KryoSerializerWrapper kryoSerializerWrapper;
  @Inject private SecretValidatorsRegistry secretValidatorsRegistry;
  @Inject private SecretSetupUsageBuilderRegistry secretSetupUsageBuilderRegistry;
  @Inject private QueuePublisher<MigrateSecretTask> queuePublisher;
  private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  private CustomEncryptorsRegistry customEncryptorsRegistry;
  private SecretsRBACService mockSecretsRBACService;
  private SecretSetupUsageService secretSetupUsageService;
  private SecretManagerConfigService mockSecretManagerConfigService;
  private SecretsFileService mockSecretsFileService;
  private SecretsAuditService mockSecretsAuditService;
  private SecretService secretService;

  @Before
  public void setup() {
    SecretsDao secretsDao = new SecretsDaoImpl(hPersistence);
    kmsEncryptorsRegistry = mock(KmsEncryptorsRegistry.class);
    vaultEncryptorsRegistry = mock(VaultEncryptorsRegistry.class);
    customEncryptorsRegistry = mock(CustomEncryptorsRegistry.class);
    mockSecretsRBACService = mock(SecretsRBACService.class);
    mockSecretManagerConfigService = mock(SecretManagerConfigService.class);
    mockSecretsFileService = mock(SecretsFileService.class);
    mockSecretsAuditService = mock(SecretsAuditService.class);
    secretSetupUsageService =
        new SecretSetupUsageServiceImpl(secretsDao, mockSecretManagerConfigService, secretSetupUsageBuilderRegistry);
    secretService =
        new SecretServiceImpl(kryoSerializerWrapper, secretsDao, mockSecretsRBACService, secretSetupUsageService,
            mockSecretsFileService, mockSecretManagerConfigService, secretValidatorsRegistry, mockSecretsAuditService,
            kmsEncryptorsRegistry, vaultEncryptorsRegistry, customEncryptorsRegistry, queuePublisher);
  }

  private SecretTextBuilder getBaseSecretText() {
    return SecretText.builder()
        .name(generateUuid())
        .kmsId(generateUuid())
        .hideFromListing(false)
        .scopedToAccount(false)
        .usageRestrictions(
            UsageRestrictions.builder()
                .appEnvRestrictions(Sets.newHashSet(
                    UsageRestrictions.AppEnvRestriction.builder()
                        .appFilter(GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build())
                        .envFilter(EnvFilter.builder().filterTypes(Sets.newHashSet(EnvFilter.FilterType.PROD)).build())
                        .build(),
                    UsageRestrictions.AppEnvRestriction.builder()
                        .appFilter(GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build())
                        .envFilter(
                            EnvFilter.builder().filterTypes(Sets.newHashSet(EnvFilter.FilterType.NON_PROD)).build())
                        .build()))
                .build())
        .inheritScopesFromSM(false)
        .runtimeParameters(new HashMap<>());
  }

  private SecretText getInlineSecretText() {
    return getBaseSecretText().value(generateUuid()).build();
  }

  private SecretText getReferenceSecretText() {
    return getBaseSecretText().path(generateUuid() + "#" + generateUuid()).build();
  }

  private SecretText getParameterizedSecretText() {
    return getBaseSecretText().parameters(new HashSet<>()).build();
  }

  private KmsConfig getKmsConfig(String accountId, String uuid) {
    return KmsConfig.builder()
        .uuid(uuid)
        .accountId(accountId)
        .region(generateUuid())
        .secretKey(generateUuid())
        .accessKey(generateUuid())
        .kmsArn(generateUuid())
        .isDefault(false)
        .scopedToAccount(false)
        .usageRestrictions(UsageRestrictions.builder().build())
        .build();
  }

  private VaultConfig getVaultConfig(String accountId, String uuid) {
    return VaultConfig.builder()
        .uuid(uuid)
        .accountId(accountId)
        .secretEngineName(generateUuid())
        .name(generateUuid())
        .vaultUrl(generateUuid())
        .authToken(generateUuid())
        .usageRestrictions(UsageRestrictions.builder().build())
        .build();
  }

  private LocalEncryptionConfig getLocalEncryptionConfig(String accountId) {
    return LocalEncryptionConfig.builder()
        .uuid(accountId)
        .accountId(accountId)
        .scopedToAccount(false)
        .usageRestrictions(UsageRestrictions.builder().build())
        .build();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateInlineKMSSecret() {
    String accountId = generateUuid();
    SecretText secretText = getInlineSecretText();
    SecretManagerConfig config = getKmsConfig(accountId, secretText.getKmsId());
    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder()
                                                  .encryptedValue(generateUuid().toCharArray())
                                                  .encryptionKey(generateUuid())
                                                  .build();
    KmsEncryptor kmsEncryptor = mock(KmsEncryptor.class);
    when(kmsEncryptor.encryptSecret(accountId, secretText.getValue(), config)).thenReturn(encryptedRecordData);
    when(kmsEncryptorsRegistry.getKmsEncryptor(config)).thenReturn(kmsEncryptor);
    when(mockSecretManagerConfigService.getSecretManager(
             accountId, secretText.getKmsId(), null, secretText.getRuntimeParameters()))
        .thenReturn(config);
    EncryptedData encryptedData = secretService.createSecret(accountId, secretText, true);
    assertThat(encryptedData).isNotNull();
    assertThat(encryptedData.getName()).isEqualTo(secretText.getName());
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.getEncryptionKey()).isEqualTo(encryptedRecordData.getEncryptionKey());
    assertThat(encryptedData.getEncryptedValue()).isEqualTo(encryptedRecordData.getEncryptedValue());
    assertThat(encryptedData.isScopedToAccount()).isEqualTo(secretText.isScopedToAccount());
    assertThat(encryptedData.isInheritScopesFromSM()).isEqualTo(secretText.isInheritScopesFromSM());
    assertThat(encryptedData.getUsageRestrictions()).isEqualTo(secretText.getUsageRestrictions());
    assertThat(encryptedData.getKmsId()).isEqualTo(secretText.getKmsId());
    assertThat(encryptedData.getEncryptionType()).isEqualTo(config.getEncryptionType());
    assertThat(encryptedData.isInlineSecret()).isTrue();

    verify(mockSecretManagerConfigService, times(1))
        .getSecretManager(accountId, secretText.getKmsId(), null, secretText.getRuntimeParameters());
    verify(mockSecretsRBACService, times(1))
        .canSetPermissions(accountId,
            SecretScopeMetadata.builder()
                .secretsManagerScopes(config)
                .secretScopes(secretText)
                .inheritScopesFromSM(secretText.isInheritScopesFromSM())
                .build());
    verify(kmsEncryptorsRegistry, times(1)).getKmsEncryptor(config);
    verify(kmsEncryptor, times(1)).encryptSecret(accountId, secretText.getValue(), config);
    ArgumentCaptor<EncryptedData> captor = ArgumentCaptor.forClass(EncryptedData.class);
    verify(mockSecretsAuditService, times(1)).logSecretCreateEvent(captor.capture());
    EncryptedData capturedRecord = captor.getValue();
    assertThat(capturedRecord).isNotNull();
    assertThat(capturedRecord.getUuid()).isEqualTo(encryptedData.getUuid());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testEncryptSecret() {
    String accountId = generateUuid();
    SecretText secretText = getInlineSecretText();
    SecretManagerConfig config = getLocalEncryptionConfig(accountId);
    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder()
                                                  .encryptedValue(generateUuid().toCharArray())
                                                  .encryptionKey(generateUuid())
                                                  .build();

    KmsEncryptor kmsEncryptor = mock(KmsEncryptor.class);
    when(kmsEncryptor.encryptSecret(accountId, secretText.getValue(), config)).thenReturn(encryptedRecordData);
    when(kmsEncryptorsRegistry.getKmsEncryptor(config)).thenReturn(kmsEncryptor);
    when(mockSecretManagerConfigService.getSecretManager(accountId, accountId)).thenReturn(config);
    secretText.setKmsId(accountId);
    EncryptedData encryptedData = secretService.encryptSecret(accountId, secretText, false);
    assertThat(encryptedData).isNotNull();
    assertThat(encryptedData.getName()).isEqualTo(secretText.getName());
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.getEncryptionKey()).isEqualTo(encryptedRecordData.getEncryptionKey());
    assertThat(encryptedData.getEncryptedValue()).isEqualTo(encryptedRecordData.getEncryptedValue());
    assertThat(encryptedData.isScopedToAccount()).isEqualTo(secretText.isScopedToAccount());
    assertThat(encryptedData.isInheritScopesFromSM()).isEqualTo(secretText.isInheritScopesFromSM());
    assertThat(encryptedData.getUsageRestrictions()).isEqualTo(secretText.getUsageRestrictions());
    assertThat(encryptedData.getKmsId()).isEqualTo(accountId);
    assertThat(encryptedData.getEncryptionType()).isEqualTo(config.getEncryptionType());
    assertThat(encryptedData.isInlineSecret()).isTrue();

    verify(mockSecretManagerConfigService, times(1)).getSecretManager(accountId, accountId);
    verify(kmsEncryptorsRegistry, times(1)).getKmsEncryptor(config);
    verify(kmsEncryptor, times(1)).encryptSecret(accountId, secretText.getValue(), config);
    ArgumentCaptor<EncryptedData> captor = ArgumentCaptor.forClass(EncryptedData.class);
    verify(mockSecretsAuditService, times(1)).logSecretCreateEvent(captor.capture());
    EncryptedData capturedRecord = captor.getValue();
    assertThat(capturedRecord).isNotNull();
    assertThat(capturedRecord.getUuid()).isEqualTo(encryptedData.getUuid());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateInlineVaultSecret() {
    String accountId = generateUuid();
    SecretText secretText = getInlineSecretText();
    SecretManagerConfig config = getVaultConfig(accountId, secretText.getKmsId());
    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder()
                                                  .encryptedValue(generateUuid().toCharArray())
                                                  .encryptionKey(generateUuid())
                                                  .build();
    VaultEncryptor vaultEncryptor = mock(VaultEncryptor.class);
    when(vaultEncryptor.createSecret(accountId, secretText, config)).thenReturn(encryptedRecordData);
    when(vaultEncryptorsRegistry.getVaultEncryptor(config.getEncryptionType())).thenReturn(vaultEncryptor);
    when(mockSecretManagerConfigService.getSecretManager(
             accountId, secretText.getKmsId(), null, secretText.getRuntimeParameters()))
        .thenReturn(config);
    EncryptedData encryptedData = secretService.createSecret(accountId, secretText, true);
    assertThat(encryptedData).isNotNull();
    assertThat(encryptedData.getName()).isEqualTo(secretText.getName());
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.getEncryptionKey()).isEqualTo(encryptedRecordData.getEncryptionKey());
    assertThat(encryptedData.getEncryptedValue()).isEqualTo(encryptedRecordData.getEncryptedValue());
    assertThat(encryptedData.isScopedToAccount()).isEqualTo(secretText.isScopedToAccount());
    assertThat(encryptedData.isInheritScopesFromSM()).isEqualTo(secretText.isInheritScopesFromSM());
    assertThat(encryptedData.getUsageRestrictions()).isEqualTo(secretText.getUsageRestrictions());
    assertThat(encryptedData.getKmsId()).isEqualTo(secretText.getKmsId());
    assertThat(encryptedData.getEncryptionType()).isEqualTo(config.getEncryptionType());
    assertThat(encryptedData.isInlineSecret()).isTrue();

    verify(mockSecretManagerConfigService, times(1))
        .getSecretManager(accountId, secretText.getKmsId(), null, secretText.getRuntimeParameters());
    verify(mockSecretsRBACService, times(1))
        .canSetPermissions(accountId,
            SecretScopeMetadata.builder()
                .secretsManagerScopes(config)
                .secretScopes(secretText)
                .inheritScopesFromSM(secretText.isInheritScopesFromSM())
                .build());
    verify(vaultEncryptorsRegistry, times(1)).getVaultEncryptor(config.getEncryptionType());
    verify(vaultEncryptor, times(1)).createSecret(accountId, secretText, config);
    ArgumentCaptor<EncryptedData> captor = ArgumentCaptor.forClass(EncryptedData.class);
    verify(mockSecretsAuditService, times(1)).logSecretCreateEvent(captor.capture());
    EncryptedData capturedRecord = captor.getValue();
    assertThat(capturedRecord).isNotNull();
    assertThat(capturedRecord.getUuid()).isEqualTo(encryptedData.getUuid());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateParameterizedCustomSecret() {
    String accountId = generateUuid();
    SecretText secretText = getParameterizedSecretText();
    SecretManagerConfig config = mock(SecretManagerConfig.class);
    when(config.getEncryptionType()).thenReturn(EncryptionType.CUSTOM);
    when(config.getType()).thenReturn(SecretManagerType.CUSTOM);
    when(config.getSecretManagerCapabilities()).thenReturn(Lists.newArrayList(CREATE_PARAMETERIZED_SECRET));
    when(config.getUuid()).thenReturn(secretText.getKmsId());
    CustomEncryptor customEncryptor = mock(CustomEncryptor.class);
    when(customEncryptor.validateReference(accountId, secretText.getParameters(), config)).thenReturn(true);
    when(customEncryptorsRegistry.getCustomEncryptor(config.getEncryptionType())).thenReturn(customEncryptor);
    when(mockSecretManagerConfigService.getSecretManager(
             accountId, secretText.getKmsId(), null, secretText.getRuntimeParameters()))
        .thenReturn(config);
    EncryptedData encryptedData = secretService.createSecret(accountId, secretText, true);
    assertThat(encryptedData).isNotNull();
    assertThat(encryptedData.getName()).isEqualTo(secretText.getName());
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.getPath()).isEqualTo(secretText.getPath());
    assertThat(encryptedData.isScopedToAccount()).isEqualTo(secretText.isScopedToAccount());
    assertThat(encryptedData.isInheritScopesFromSM()).isEqualTo(secretText.isInheritScopesFromSM());
    assertThat(encryptedData.getUsageRestrictions()).isEqualTo(secretText.getUsageRestrictions());
    assertThat(encryptedData.getKmsId()).isEqualTo(secretText.getKmsId());
    assertThat(encryptedData.getEncryptionType()).isEqualTo(config.getEncryptionType());
    assertThat(encryptedData.isParameterizedSecret()).isTrue();

    verify(mockSecretManagerConfigService, times(1))
        .getSecretManager(accountId, secretText.getKmsId(), null, secretText.getRuntimeParameters());
    verify(mockSecretsRBACService, times(1))
        .canSetPermissions(accountId,
            SecretScopeMetadata.builder()
                .secretsManagerScopes(config)
                .secretScopes(secretText)
                .inheritScopesFromSM(secretText.isInheritScopesFromSM())
                .build());
    verify(customEncryptorsRegistry, times(1)).getCustomEncryptor(config.getEncryptionType());
    verify(customEncryptor, times(1)).validateReference(accountId, secretText.getParameters(), config);
    ArgumentCaptor<EncryptedData> captor = ArgumentCaptor.forClass(EncryptedData.class);
    verify(mockSecretsAuditService, times(1)).logSecretCreateEvent(captor.capture());
    EncryptedData capturedRecord = captor.getValue();
    assertThat(capturedRecord).isNotNull();
    assertThat(capturedRecord.getUuid()).isEqualTo(encryptedData.getUuid());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateReferencedVaultSecret() {
    String accountId = generateUuid();
    SecretText secretText = getReferenceSecretText();
    SecretManagerConfig config = getVaultConfig(accountId, secretText.getKmsId());
    VaultEncryptor vaultEncryptor = mock(VaultEncryptor.class);
    when(vaultEncryptor.validateReference(accountId, secretText, config)).thenReturn(true);
    when(vaultEncryptorsRegistry.getVaultEncryptor(config.getEncryptionType())).thenReturn(vaultEncryptor);
    when(mockSecretManagerConfigService.getSecretManager(
             accountId, secretText.getKmsId(), null, secretText.getRuntimeParameters()))
        .thenReturn(config);
    EncryptedData encryptedData = secretService.createSecret(accountId, secretText, true);
    assertThat(encryptedData).isNotNull();
    assertThat(encryptedData.getName()).isEqualTo(secretText.getName());
    assertThat(encryptedData.getAccountId()).isEqualTo(accountId);
    assertThat(encryptedData.getPath()).isEqualTo(secretText.getPath());
    assertThat(encryptedData.isScopedToAccount()).isEqualTo(secretText.isScopedToAccount());
    assertThat(encryptedData.isInheritScopesFromSM()).isEqualTo(secretText.isInheritScopesFromSM());
    assertThat(encryptedData.getUsageRestrictions()).isEqualTo(secretText.getUsageRestrictions());
    assertThat(encryptedData.getKmsId()).isEqualTo(secretText.getKmsId());
    assertThat(encryptedData.getEncryptionType()).isEqualTo(config.getEncryptionType());
    assertThat(encryptedData.isReferencedSecret()).isTrue();

    verify(mockSecretManagerConfigService, times(1))
        .getSecretManager(accountId, secretText.getKmsId(), null, secretText.getRuntimeParameters());
    verify(mockSecretsRBACService, times(1))
        .canSetPermissions(accountId,
            SecretScopeMetadata.builder()
                .secretsManagerScopes(config)
                .secretScopes(secretText)
                .inheritScopesFromSM(secretText.isInheritScopesFromSM())
                .build());
    verify(vaultEncryptorsRegistry, times(1)).getVaultEncryptor(config.getEncryptionType());
    verify(vaultEncryptor, times(1)).validateReference(accountId, secretText, config);
    ArgumentCaptor<EncryptedData> captor = ArgumentCaptor.forClass(EncryptedData.class);
    verify(mockSecretsAuditService, times(1)).logSecretCreateEvent(captor.capture());
    EncryptedData capturedRecord = captor.getValue();
    assertThat(capturedRecord).isNotNull();
    assertThat(capturedRecord.getUuid()).isEqualTo(encryptedData.getUuid());
  }
}
