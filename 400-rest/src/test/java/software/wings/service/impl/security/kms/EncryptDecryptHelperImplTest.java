/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.kms;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.beans.DelegateFile.Builder.aDelegateFile;
import static io.harness.helpers.EncryptDecryptHelperImpl.ON_FILE_STORAGE;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.security.encryption.SecretManagerType.VAULT;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.SecretManagerType;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.beans.KmsConfig;

import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class EncryptDecryptHelperImplTest extends WingsBaseTest {
  @Mock private EncryptionConfig encryptionConfig;
  @Mock private EncryptedRecord encryptedRecord;
  @Mock private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Mock private KmsEncryptor kmsEncryptor;
  @Mock private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Mock private VaultEncryptor vaultEncryptor;
  @Mock private DelegateFileManagerBase delegateFileManager;

  @Inject @InjectMocks private EncryptDecryptHelper encryptDecryptHelperImpl;
  private String accountId;
  private final byte[] fileContent = "TerraformPlan".getBytes();
  private final char[] terraformPlan = "TerraformPlan".toCharArray();
  private final String encodedTfPlan = encodeBase64(terraformPlan);

  @Before
  public void setup() {
    when(kmsEncryptorsRegistry.getKmsEncryptor(any())).thenReturn(kmsEncryptor);
    when(vaultEncryptorsRegistry.getVaultEncryptor(any())).thenReturn(vaultEncryptor);
    when(encryptionConfig.getAccountId()).thenReturn(accountId);
    accountId = UUIDGenerator.generateUuid();
    when(encryptionConfig.getAccountId()).thenReturn(accountId);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testEncryptTerraformPlanForKms() {
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    EncryptedRecordData data = EncryptedRecordData.builder().name("data").build();
    when(kmsEncryptor.encryptSecret(eq(accountId), any(), eq(encryptionConfig))).thenReturn(data);
    EncryptedRecord record = encryptDecryptHelperImpl.encryptContent(fileContent, "TerraformPlan", encryptionConfig);

    assertThat(record).isEqualTo(data);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testEncryptTerraformPlanForKmsOptimized() throws IOException {
    DelegateFile delegateFile = aDelegateFile().withFileId("fileId").build();
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    EncryptedRecordData data = EncryptedRecordData.builder().name("data").encryptedValue("plan".toCharArray()).build();
    when(kmsEncryptor.encryptSecret(eq(accountId), any(), eq(encryptionConfig))).thenReturn(data);
    EncryptedRecord record =
        encryptDecryptHelperImpl.encryptFile(fileContent, "TerraformPlan", encryptionConfig, delegateFile);
    verify(delegateFileManager, times(1)).upload(any(), any());
    assertThat(record.getEncryptedValue()).isEqualTo("fileId".toCharArray());
    assertThat(record.getAdditionalMetadata().getValues().get(ON_FILE_STORAGE)).isEqualTo(TRUE);
    assertThat(record).isEqualTo(data);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testEncryptTerraformPlanForVault() {
    when(encryptionConfig.getType()).thenReturn(VAULT);
    EncryptedRecordData data = EncryptedRecordData.builder().name("data").build();
    when(vaultEncryptor.createSecret(eq(accountId), eq("TerraformPlan"), any(), eq(encryptionConfig))).thenReturn(data);
    EncryptedRecord record = encryptDecryptHelperImpl.encryptContent(fileContent, "TerraformPlan", encryptionConfig);

    assertThat(record).isEqualTo(data);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testEncryptTerraformPlanUnsupportedSecretManager() {
    when(encryptionConfig.getType()).thenReturn(null);
    assertThatThrownBy(() -> encryptDecryptHelperImpl.encryptContent(fileContent, "TerraformPlan", encryptionConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessage("Encryptor for fetch secret task for encryption config null not configured");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDecryptedTerraformPlanFromKms() {
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    when(kmsEncryptor.fetchSecretValue(accountId, encryptedRecord, encryptionConfig))
        .thenReturn(encodedTfPlan.toCharArray());
    byte[] result = encryptDecryptHelperImpl.getDecryptedContent(encryptionConfig, encryptedRecord);

    assertThat(result).isEqualTo(fileContent);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetDecryptedTerraformPlanFromKmsOptimized() throws IOException {
    EncryptedRecordData encryptedRecordData =
        EncryptedRecordData.builder()
            .encryptedValue("fileId".toCharArray())
            .additionalMetadata(AdditionalMetadata.builder().value(ON_FILE_STORAGE, TRUE).build())
            .build();
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    when(kmsEncryptor.fetchSecretValue(accountId, encryptedRecordData, encryptionConfig))
        .thenReturn(encodedTfPlan.toCharArray());
    doReturn(new ByteArrayInputStream("terraformPlanContent".getBytes()))
        .when(delegateFileManager)
        .downloadByFileId(any(), any(), any());
    byte[] result = encryptDecryptHelperImpl.getDecryptedContent(encryptionConfig, encryptedRecordData, "accountId");
    verify(delegateFileManager, times(1)).downloadByFileId(any(), any(), any());
    assertThat(result).isEqualTo(fileContent);
    assertThat(encryptedRecordData.getEncryptedValue()).isEqualTo("fileId".toCharArray());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetDecryptedTerraformPlanFromKmsNotOptimized() throws IOException {
    EncryptedRecordData encryptedRecordData =
        EncryptedRecordData.builder().encryptedValue("fileId".toCharArray()).build();
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    when(kmsEncryptor.fetchSecretValue(accountId, encryptedRecordData, encryptionConfig))
        .thenReturn(encodedTfPlan.toCharArray());
    doReturn(new ByteArrayInputStream("terraformPlanContent".getBytes()))
        .when(delegateFileManager)
        .downloadByFileId(any(), any(), any());
    byte[] result = encryptDecryptHelperImpl.getDecryptedContent(encryptionConfig, encryptedRecordData, "accountId");
    assertThat(result).isEqualTo(fileContent);
    assertThat(encryptedRecordData.getEncryptedValue()).isEqualTo("fileId".toCharArray());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDecryptedTerraformPlanFromVault() {
    when(encryptionConfig.getType()).thenReturn(VAULT);
    when(vaultEncryptor.fetchSecretValue(accountId, encryptedRecord, encryptionConfig))
        .thenReturn(encodedTfPlan.toCharArray());

    byte[] result = encryptDecryptHelperImpl.getDecryptedContent(encryptionConfig, encryptedRecord);

    assertThat(result).isEqualTo(fileContent);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDecryptedTerraformPlanUnsupportedSecretManager() {
    when(encryptionConfig.getType()).thenReturn(null);
    assertThatThrownBy(() -> encryptDecryptHelperImpl.getDecryptedContent(encryptionConfig, encryptedRecord))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessage("Encryptor for fetch secret task for encryption config null not configured");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testdeleteTfPlanFromVaultWithAwsSecretManager() {
    doReturn(vaultEncryptor).when(vaultEncryptorsRegistry).getVaultEncryptor(any());
    doReturn(true).when(vaultEncryptor).deleteSecret(any(), any(), any());
    boolean isPlanDeleted = encryptDecryptHelperImpl.deleteEncryptedRecord(
        AwsSecretsManagerConfig.builder().accountId(ACCOUNT_ID).build(), encryptedRecord);
    verify(vaultEncryptor, times(1)).deleteSecret(any(), any(), any());
    assertThat(isPlanDeleted).isTrue();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testdeleteTfPlanFromVaultWithKms() {
    boolean isPlanDeleted = encryptDecryptHelperImpl.deleteEncryptedRecord(
        KmsConfig.builder().accountId(ACCOUNT_ID).build(), encryptedRecord);

    verify(vaultEncryptor, never()).deleteSecret(any(), any(), any());
    assertThat(isPlanDeleted).isFalse();
  }
}
