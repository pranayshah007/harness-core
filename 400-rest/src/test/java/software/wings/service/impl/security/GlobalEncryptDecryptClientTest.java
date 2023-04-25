/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.rule.Owner;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import software.wings.WingsBaseTest;
import software.wings.beans.GcpKmsConfig;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class GlobalEncryptDecryptClientTest extends WingsBaseTest {
  @Mock private KmsEncryptor kmsEncryptor;
  @Mock private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Inject @InjectMocks private GlobalEncryptDecryptClient globalEncryptDecryptClient;

  @Before
  public void setup() throws Exception {
    initMocks(this);
    when(kmsEncryptorsRegistry.getKmsEncryptor(any())).thenReturn(kmsEncryptor);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testConvertEncryptedRecordToLocallyEncrypted_usingGcpKms() {
    String accountId = "accountId";
    char[] value = "value".toCharArray();
    GcpKmsConfig gcpKmsConfig = mock(GcpKmsConfig.class);
    when(gcpKmsConfig.getAccountId()).thenReturn(GLOBAL_ACCOUNT_ID);
    when(gcpKmsConfig.getEncryptionType()).thenReturn(EncryptionType.GCP_KMS);

    EncryptedData gcpKmsEncryptedData = EncryptedData.builder()
                                            .name("gcpName")
                                            .accountId(accountId)
                                            .encryptionKey("gcpEncryptionKey")
                                            .encryptedValue("gcpEncryptionValue".toCharArray())
                                            .kmsId("gcpKmsId")
                                            .enabled(true)
                                            .path("path")
                                            .encryptionType(EncryptionType.GCP_KMS)
                                            .build();
    gcpKmsEncryptedData.setUuid("uuid");
    when(kmsEncryptor.fetchSecretValue(accountId, gcpKmsEncryptedData, gcpKmsConfig)).thenReturn(value);
    EncryptedRecordData encryptedRecordData = globalEncryptDecryptClient.convertEncryptedRecordToLocallyEncrypted(
        gcpKmsEncryptedData, accountId, gcpKmsConfig);

    assertThat(encryptedRecordData.getEncryptionType()).isEqualTo(EncryptionType.LOCAL);
    char[] returnedValue = new SimpleEncryption(encryptedRecordData.getEncryptionKey())
                               .decryptChars(encryptedRecordData.getEncryptedValue());
    assertThat(returnedValue).isEqualTo(value);
  }
}
