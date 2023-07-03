/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileMetadata;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.SecretManagerType;

import software.wings.DelegateFileEncryptedRecordDataPackage;
import software.wings.beans.DecryptedRecord;
import software.wings.beans.DelegateFileMetadata;
import software.wings.beans.GcpKmsConfig;
import software.wings.service.impl.DelegateManagerEncryptionDecryptionHarnessSMServiceNGImpl;
import software.wings.service.impl.FileServiceImpl;

import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class DelegateManagerEncryptionDecryptionHarnessSMServiceNGImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private EncryptionConfig encryptionConfig;
  @Mock private EncryptedRecord encryptedRecord;
  @Mock private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Mock private KmsEncryptor kmsEncryptor;
  @Mock private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Mock private VaultEncryptor vaultEncryptor;
  @Mock private DelegateFileManagerBase delegateFileManager;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock private FileServiceImpl fileService;

  @Spy
  @InjectMocks
  DelegateManagerEncryptionDecryptionHarnessSMServiceNGImpl delegateManagerEncryptionDecryptionHarnessSMServiceNG;
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
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testEncryptTerraformPlanForKms() {
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    EncryptedRecordData data = EncryptedRecordData.builder().name("data").build();
    when(kmsEncryptor.encryptSecret(any(), any(), any())).thenReturn(data);
    MockedStatic<SecretManagerConfigMapper> secretManagerConfigMapperMock = mockStatic(SecretManagerConfigMapper.class);
    secretManagerConfigMapperMock.when(() -> SecretManagerConfigMapper.fromDTO(any()))
        .thenReturn(GcpKmsConfig.builder().build());
    EncryptedRecord record =
        delegateManagerEncryptionDecryptionHarnessSMServiceNG.encryptDataNG(accountId, fileContent);

    assertThat(record).isEqualTo(data);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testDecryptTerraformPlanForKms() {
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    when(kmsEncryptor.fetchSecretValue(any(), any(), any())).thenReturn(terraformPlan);
    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder().build();
    MockedStatic<SecretManagerConfigMapper> secretManagerConfigMapperMock = mockStatic(SecretManagerConfigMapper.class);
    secretManagerConfigMapperMock.when(() -> SecretManagerConfigMapper.fromDTO(any()))
        .thenReturn(GcpKmsConfig.builder().build());
    DecryptedRecord record =
        delegateManagerEncryptionDecryptionHarnessSMServiceNG.decryptDataNG(accountId, encryptedRecordData);

    assertThat(record.getDecryptedValue()).isEqualTo(terraformPlan);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testEncryptTerraformPlanWithFileUpload() throws IOException {
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    EncryptedRecordData data = EncryptedRecordData.builder().name("data").encryptedValue(terraformPlan).build();
    when(kmsEncryptor.encryptSecret(any(), any(), any())).thenReturn(data);
    when(fileService.saveFile((FileMetadata) any(), any(), any())).thenReturn("fileId");
    DelegateFileMetadata delegateFile = DelegateFileMetadata.builder().build();
    MockedStatic<SecretManagerConfigMapper> secretManagerConfigMapperMock = mockStatic(SecretManagerConfigMapper.class);
    secretManagerConfigMapperMock.when(() -> SecretManagerConfigMapper.fromDTO(any()))
        .thenReturn(GcpKmsConfig.builder().build());
    DelegateFileEncryptedRecordDataPackage record =
        delegateManagerEncryptionDecryptionHarnessSMServiceNG.encryptDataNGWithFileUpload(
            accountId, fileContent, delegateFile);

    assertThat(record.getDelegateFileId()).isEqualTo("fileId");
  }
}
