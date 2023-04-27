/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.security.SimpleEncryption.CHARSET;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.rule.Owner;
import io.harness.secrets.SecretsDelegateCacheService;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.SecretManagerType;
import io.harness.security.encryption.setting.EncryptableSettingWithEncryptionDetails;

import software.wings.beans.KmsConfig;
import software.wings.beans.LocalEncryptionConfig;

import com.google.common.collect.Lists;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

/**
 * @author marklu on 10/14/19
 */
public class EncryptionServiceTest extends CategoryTest {
  @Mock private SecretsDelegateCacheService secretsDelegateCacheService;
  @Mock private EncryptedDataDetail encryptedDataDetail1;
  @Mock private EncryptedDataDetail encryptedDataDetail2;
  @Mock private KmsEncryptorsRegistry kmsRegistry;
  @Mock private VaultEncryptorsRegistry vaultRegistry;
  @Mock private CustomEncryptorsRegistry customRegistry;
  @Mock private KmsEncryptor kmsEncryptor;

  private EncryptionServiceImpl encryptionService;
  private ScheduledThreadPoolExecutor threadPoolExecutor = new ScheduledThreadPoolExecutor(4);

  @Before
  public void setUp() {
    initMocks(this);
    encryptionService = new EncryptionServiceImpl(
        vaultRegistry, kmsRegistry, customRegistry, threadPoolExecutor, secretsDelegateCacheService);
    EncryptionConfig encryptionConfig = mock(KmsConfig.class);
    when(encryptionConfig.getEncryptionType()).thenReturn(EncryptionType.KMS);
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    when(kmsRegistry.getKmsEncryptor(any())).thenReturn(kmsEncryptor);

    when(encryptedDataDetail1.getFieldName()).thenReturn("value");
    when(encryptedDataDetail1.getEncryptionConfig()).thenReturn(encryptionConfig);
    when(encryptedDataDetail1.getEncryptedData()).thenReturn(mock(EncryptedRecordData.class));

    when(encryptedDataDetail2.getFieldName()).thenReturn("value");
    when(encryptedDataDetail2.getEncryptionConfig()).thenReturn(encryptionConfig);
    when(encryptedDataDetail2.getEncryptedData()).thenReturn(mock(EncryptedRecordData.class));
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBatchDecryption() {
    String accountId = UUIDGenerator.generateUuid();
    when(kmsEncryptor.fetchSecretValue(any(), any(), any())).thenReturn("YWRzYXNk".toCharArray());
    List<EncryptableSettingWithEncryptionDetails> encryptableSettingWithEncryptionDetails =
        EncryptionUtils.getEncryptableSettingWithEncryptionDetailsList(
            accountId, Lists.newArrayList(encryptedDataDetail1, encryptedDataDetail2));

    List<EncryptableSettingWithEncryptionDetails> resultDetailsList =
        encryptionService.decrypt(encryptableSettingWithEncryptionDetails, false);
    assertThat(resultDetailsList).isNotEmpty();
    assertThat(resultDetailsList.size()).isEqualTo(encryptableSettingWithEncryptionDetails.size());

    for (EncryptableSettingWithEncryptionDetails details : resultDetailsList) {
      assertThat(details.getEncryptableSetting().isDecrypted()).isTrue();
    }
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testGetDecryptedValue() {
    byte[] encryptedBytes =
        new SimpleEncryption("TestEncryptionKey").encrypt(EncodingUtils.encodeBase64("Dummy").getBytes(CHARSET));
    when(kmsEncryptor.fetchSecretValue(any(), any(), any()))
        .thenReturn(EncodingUtils.encodeBase64("Dummy").toCharArray());
    EncryptedRecordData encryptedRecordData =
        EncryptedRecordData.builder()
            .encryptionType(EncryptionType.LOCAL)
            .encryptionKey("TestEncryptionKey")
            .base64Encoded(true)
            .encryptedValue(CHARSET.decode(ByteBuffer.wrap(encryptedBytes)).array())
            .build();
    LocalEncryptionConfig localEncryptionConfig = LocalEncryptionConfig.builder().build();
    EncryptedDataDetail build = EncryptedDataDetail.builder()
                                    .encryptionConfig(localEncryptionConfig)
                                    .encryptedData(encryptedRecordData)
                                    .build();

    assertEquals("Dummy", new String(encryptionService.getDecryptedValue(build, false)));
  }
}
