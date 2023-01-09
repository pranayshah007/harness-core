/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.SecretTestUtils.getDummyEncryptedData;
import static io.harness.SecretTestUtils.getInlineSecretText;
import static io.harness.SecretTestUtils.getParameterizedSecretText;
import static io.harness.SecretTestUtils.getReferencedSecretText;
import static io.harness.SecretTestUtils.getSecretFile;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.rule.OwnerRule.MOHIT_GARG;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.SMCoreTestBase;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretText;
import io.harness.beans.SecretUpdateData;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.SecretManagementException;
import io.harness.persistence.HIterator;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionType;

import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretsDaoImplTest extends SMCoreTestBase {
  @Inject private SecretsDaoImpl secretsDao;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetSecretById() {
    EncryptedData encryptedData = getDummyEncryptedData();
    String configId = secretsDao.saveSecret(encryptedData);
    assertThat(configId).isNotNull();
    Optional<EncryptedData> encryptedDataOptional = secretsDao.getSecretById(encryptedData.getAccountId(), configId);
    assertThat(encryptedDataOptional.isPresent()).isTrue();
    assertThat(encryptedDataOptional.get()).isEqualTo(encryptedData);

    encryptedDataOptional = secretsDao.getSecretById(UUIDGenerator.generateUuid(), configId);
    assertThat(encryptedDataOptional.isPresent()).isFalse();

    secretsDao.deleteSecret(encryptedData.getAccountId(), configId);

    encryptedDataOptional = secretsDao.getSecretById(encryptedData.getAccountId(), configId);
    assertThat(encryptedDataOptional.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetSecretByName() {
    EncryptedData encryptedData = getDummyEncryptedData();
    String configId = secretsDao.saveSecret(encryptedData);
    Optional<EncryptedData> encryptedDataOptional =
        secretsDao.getSecretByName(encryptedData.getAccountId(), encryptedData.getName());
    assertThat(encryptedDataOptional.isPresent()).isTrue();
    assertThat(encryptedDataOptional.get()).isEqualTo(encryptedData);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetSecretByKeyOrPath() {
    EncryptedData encryptedData = getInlineSecretText();
    secretsDao.saveSecret(encryptedData);
    Optional<EncryptedData> encryptedDataOptional = secretsDao.getSecretByKeyOrPath(encryptedData.getAccountId(),
        encryptedData.getEncryptionType(), encryptedData.getEncryptionKey(), encryptedData.getPath());
    assertThat(encryptedDataOptional.isPresent()).isTrue();
    assertThat(encryptedDataOptional.get()).isEqualTo(encryptedData);

    encryptedData = getReferencedSecretText();
    secretsDao.saveSecret(encryptedData);
    encryptedDataOptional = secretsDao.getSecretByKeyOrPath(encryptedData.getAccountId(),
        encryptedData.getEncryptionType(), encryptedData.getEncryptionKey(), encryptedData.getPath());
    assertThat(encryptedDataOptional.isPresent()).isTrue();
    assertThat(encryptedDataOptional.get()).isEqualTo(encryptedData);

    encryptedDataOptional = secretsDao.getSecretByKeyOrPath(encryptedData.getAccountId(),
        encryptedData.getEncryptionType(), UUIDGenerator.generateUuid(), encryptedData.getPath());
    assertThat(encryptedDataOptional.isPresent()).isTrue();
    assertThat(encryptedDataOptional.get()).isEqualTo(encryptedData);

    encryptedDataOptional = secretsDao.getSecretByKeyOrPath(encryptedData.getAccountId(),
        encryptedData.getEncryptionType(), UUIDGenerator.generateUuid(), UUIDGenerator.generateUuid());
    assertThat(encryptedDataOptional.isPresent()).isFalse();

    try {
      secretsDao.getSecretByKeyOrPath(encryptedData.getAccountId(), encryptedData.getEncryptionType(), null, null);
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testMigrateSecret() {
    EncryptedData encryptedData = getDummyEncryptedData();
    secretsDao.saveSecret(encryptedData);
    Optional<EncryptedData> encryptedDataOptional =
        secretsDao.getSecretById(encryptedData.getAccountId(), encryptedData.getUuid());
    assertThat(encryptedDataOptional.isPresent()).isTrue();
    assertThat(encryptedDataOptional.get()).isEqualTo(encryptedData);

    EncryptedData newEncryptedData = getInlineSecretText();
    EncryptedData updatedEncryptedData =
        secretsDao.migrateSecret(encryptedData.getAccountId(), encryptedData.getUuid(), newEncryptedData);
    assertThat(updatedEncryptedData.getEncryptionType()).isEqualTo(newEncryptedData.getEncryptionType());
    assertThat(updatedEncryptedData.getEncryptionKey()).isEqualTo(newEncryptedData.getEncryptionKey());
    assertThat(updatedEncryptedData.getEncryptedValue()).isEqualTo(newEncryptedData.getEncryptedValue());
    assertThat(updatedEncryptedData.getKmsId()).isEqualTo(newEncryptedData.getKmsId());
    assertThat(updatedEncryptedData.getUuid()).isEqualTo(encryptedData.getUuid());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret_InlineSecret() {
    EncryptedData encryptedData = getInlineSecretText();
    secretsDao.saveSecret(encryptedData);
    Optional<EncryptedData> encryptedDataOptional =
        secretsDao.getSecretById(encryptedData.getAccountId(), encryptedData.getUuid());
    assertThat(encryptedDataOptional.isPresent()).isTrue();
    encryptedData = encryptedDataOptional.get();

    SecretText secretText = SecretText.builder()
                                .value(UUIDGenerator.generateUuid())
                                .kmsId(encryptedData.getKmsId())
                                .hideFromListing(encryptedData.isHideFromListing())
                                .name(UUIDGenerator.generateUuid())
                                .scopedToAccount(true)
                                .usageRestrictions(null)
                                .build();
    SecretUpdateData secretUpdateData = new SecretUpdateData(secretText, encryptedData);
    EncryptedRecord encryptedRecord = EncryptedData.builder()
                                          .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                          .encryptionKey(UUIDGenerator.generateUuid())
                                          .build();
    EncryptedData updatedEncryptedData = secretsDao.updateSecret(secretUpdateData, encryptedRecord);
    assertThat(updatedEncryptedData.getKmsId()).isEqualTo(secretText.getKmsId());
    assertThat(updatedEncryptedData.getEncryptedValue()).isEqualTo(encryptedRecord.getEncryptedValue());
    assertThat(updatedEncryptedData.getEncryptionKey()).isEqualTo(encryptedRecord.getEncryptionKey());
    assertThat(updatedEncryptedData.getName()).isEqualTo(secretText.getName());
    assertThat(updatedEncryptedData.getUsageRestrictions()).isEqualTo(secretText.getUsageRestrictions());
    assertThat(updatedEncryptedData.isScopedToAccount()).isEqualTo(secretText.isScopedToAccount());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret_ReferencedSecret() {
    EncryptedData encryptedData = getReferencedSecretText();
    secretsDao.saveSecret(encryptedData);
    Optional<EncryptedData> encryptedDataOptional =
        secretsDao.getSecretById(encryptedData.getAccountId(), encryptedData.getUuid());
    assertThat(encryptedDataOptional.isPresent()).isTrue();
    encryptedData = encryptedDataOptional.get();

    SecretText secretText =
        SecretText.builder()
            .path(UUIDGenerator.generateUuid())
            .kmsId(encryptedData.getKmsId())
            .hideFromListing(encryptedData.isHideFromListing())
            .name(UUIDGenerator.generateUuid())
            .scopedToAccount(false)
            .usageRestrictions(UsageRestrictions.builder()
                                   .appEnvRestrictions(Sets.newHashSet(
                                       encryptedData.getUsageRestrictions().getAppEnvRestrictions().iterator().next()))
                                   .build())
            .build();
    SecretUpdateData secretUpdateData = new SecretUpdateData(secretText, encryptedData);
    EncryptedData updatedEncryptedData = secretsDao.updateSecret(secretUpdateData, null);
    assertThat(updatedEncryptedData.getKmsId()).isEqualTo(secretText.getKmsId());
    assertThat(updatedEncryptedData.getEncryptedValue()).isNull();
    assertThat(updatedEncryptedData.getEncryptionKey()).isNull();
    assertThat(updatedEncryptedData.getPath()).isEqualTo(secretText.getPath());
    assertThat(updatedEncryptedData.getName()).isEqualTo(secretText.getName());
    assertThat(updatedEncryptedData.getUsageRestrictions()).isEqualTo(secretText.getUsageRestrictions());
    assertThat(updatedEncryptedData.isScopedToAccount()).isEqualTo(secretText.isScopedToAccount());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret_ParameterizedSecret() {
    EncryptedData encryptedData = getParameterizedSecretText();
    secretsDao.saveSecret(encryptedData);
    Optional<EncryptedData> encryptedDataOptional =
        secretsDao.getSecretById(encryptedData.getAccountId(), encryptedData.getUuid());
    assertThat(encryptedDataOptional.isPresent()).isTrue();
    encryptedData = encryptedDataOptional.get();

    SecretText secretText = SecretText.builder()
                                .parameters(Sets.newHashSet(EncryptedDataParams.builder()
                                                                .name(UUIDGenerator.generateUuid())
                                                                .value(UUIDGenerator.generateUuid())
                                                                .build(),
                                    EncryptedDataParams.builder()
                                        .name(UUIDGenerator.generateUuid())
                                        .value(UUIDGenerator.generateUuid())
                                        .build()))
                                .kmsId(encryptedData.getKmsId())
                                .hideFromListing(encryptedData.isHideFromListing())
                                .name(UUIDGenerator.generateUuid())
                                .scopedToAccount(true)
                                .usageRestrictions(null)
                                .build();
    SecretUpdateData secretUpdateData = new SecretUpdateData(secretText, encryptedData);
    EncryptedData updatedEncryptedData = secretsDao.updateSecret(secretUpdateData, null);
    assertThat(updatedEncryptedData.getKmsId()).isEqualTo(secretText.getKmsId());
    assertThat(updatedEncryptedData.getEncryptedValue()).isNull();
    assertThat(updatedEncryptedData.getEncryptionKey()).isNull();
    assertThat(updatedEncryptedData.getPath()).isNull();
    assertThat(updatedEncryptedData.getParameters()).isEqualTo(secretText.getParameters());
    assertThat(updatedEncryptedData.getName()).isEqualTo(secretText.getName());
    assertThat(updatedEncryptedData.getUsageRestrictions()).isEqualTo(secretText.getUsageRestrictions());
    assertThat(updatedEncryptedData.isScopedToAccount()).isEqualTo(secretText.isScopedToAccount());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret_SecretFile() {
    EncryptedData encryptedData = getSecretFile();
    secretsDao.saveSecret(encryptedData);
    Optional<EncryptedData> encryptedDataOptional =
        secretsDao.getSecretById(encryptedData.getAccountId(), encryptedData.getUuid());
    assertThat(encryptedDataOptional.isPresent()).isTrue();
    encryptedData = encryptedDataOptional.get();

    SecretFile secretFile = SecretFile.builder()
                                .kmsId(encryptedData.getKmsId())
                                .hideFromListing(encryptedData.isHideFromListing())
                                .name(UUIDGenerator.generateUuid())
                                .scopedToAccount(true)
                                .usageRestrictions(null)
                                .fileContent(UUIDGenerator.generateUuid().getBytes())
                                .build();
    SecretUpdateData secretUpdateData = new SecretUpdateData(secretFile, encryptedData);
    EncryptedRecord encryptedRecord = EncryptedData.builder()
                                          .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                          .encryptionKey(UUIDGenerator.generateUuid())
                                          .build();
    EncryptedData updatedEncryptedData = secretsDao.updateSecret(secretUpdateData, encryptedRecord);
    assertThat(updatedEncryptedData.getKmsId()).isEqualTo(secretFile.getKmsId());
    assertThat(updatedEncryptedData.getEncryptedValue()).isEqualTo(encryptedRecord.getEncryptedValue());
    assertThat(updatedEncryptedData.getEncryptionKey()).isEqualTo(encryptedRecord.getEncryptionKey());
    assertThat(updatedEncryptedData.getName()).isEqualTo(secretFile.getName());
    assertThat(updatedEncryptedData.getFileSize()).isEqualTo(secretFile.getFileContent().length);
    assertThat(updatedEncryptedData.getUsageRestrictions()).isEqualTo(secretFile.getUsageRestrictions());
    assertThat(updatedEncryptedData.isScopedToAccount()).isEqualTo(secretFile.isScopedToAccount());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testListSecretsBySecretManager() {
    String kmsId = "kmsId";
    EncryptedData encryptedData = getInlineSecretText();
    encryptedData.setKmsId(kmsId);
    secretsDao.saveSecret(encryptedData);
    encryptedData = getReferencedSecretText();
    encryptedData.setKmsId(kmsId);
    secretsDao.saveSecret(encryptedData);
    encryptedData = getSecretFile();
    encryptedData.setKmsId(kmsId);
    secretsDao.saveSecret(encryptedData);

    encryptedData = getInlineSecretText();
    encryptedData.setKmsId(kmsId);
    encryptedData.setType(SettingVariableTypes.KMS);
    secretsDao.saveSecret(encryptedData);

    AtomicReference<Integer> count = new AtomicReference<>(0);
    try (HIterator<EncryptedData> iterator =
             new HIterator<>(secretsDao.listSecretsBySecretManager(encryptedData.getAccountId(), kmsId, true))) {
      iterator.forEach(entity -> count.getAndSet(count.get() + 1));
    }
    assertThat(count.get()).isEqualTo(4);

    AtomicReference<Integer> countWhenFalse = new AtomicReference<>(0);
    try (HIterator<EncryptedData> iterator =
             new HIterator<>(secretsDao.listSecretsBySecretManager(encryptedData.getAccountId(), kmsId, false))) {
      iterator.forEach(entity -> countWhenFalse.getAndSet(countWhenFalse.get() + 1));
    }
    assertThat(countWhenFalse.get()).isEqualTo(3);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateSecret() {
    EncryptedData encryptedData = getSecretFile();
    secretsDao.saveSecret(encryptedData);
    String encryptionKey = encryptedData.getEncryptionKey();
    Optional<EncryptedData> encryptedDataOptional =
        secretsDao.getSecretById(encryptedData.getAccountId(), encryptedData.getUuid());
    assertThat(encryptedDataOptional.get().getEncryptionKey()).isEqualTo(encryptionKey);
    assertThat(encryptedDataOptional.get().getEncryptionType()).isEqualTo(EncryptionType.KMS);

    UpdateOperations<EncryptedData> updateOperations = secretsDao.getUpdateOperations();
    String sampleEncryptionKey = "SampleEncryptionKey";
    EncryptionType sampleEncryptionType = EncryptionType.CUSTOM;
    updateOperations.set(EncryptedDataKeys.encryptionKey, sampleEncryptionKey)
        .set(EncryptedDataKeys.encryptionType, sampleEncryptionType);
    secretsDao.updateSecret(encryptedDataOptional.get(), updateOperations);

    encryptedDataOptional = secretsDao.getSecretById(encryptedData.getAccountId(), encryptedData.getUuid());
    assertThat(encryptedDataOptional.get().getEncryptionKey()).isEqualTo(sampleEncryptionKey);
    assertThat(encryptedDataOptional.get().getEncryptionType()).isEqualTo(EncryptionType.CUSTOM);
  }
}
