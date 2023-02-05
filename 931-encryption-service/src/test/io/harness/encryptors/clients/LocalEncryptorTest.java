/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;

import software.wings.beans.LocalEncryptionConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class LocalEncryptorTest extends CategoryTest {
  private LocalEncryptor localEncryptor;
  private LocalEncryptionConfig localEncryptionConfig;

  @Before
  public void setup() {
    localEncryptor = new LocalEncryptor();
    localEncryptionConfig = LocalEncryptionConfig.builder().accountId(UUIDGenerator.generateUuid()).build();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testEncryptDecrypt() {
    String plainTextValue = UUIDGenerator.generateUuid();
    EncryptedRecord encryptedRecord =
        localEncryptor.encryptSecret(localEncryptionConfig.getAccountId(), plainTextValue, localEncryptionConfig);
    assertThat(encryptedRecord).isNotNull();
    char[] returnedValue =
        localEncryptor.fetchSecretValue(localEncryptionConfig.getAccountId(), encryptedRecord, localEncryptionConfig);
    assertThat(returnedValue).isEqualTo(plainTextValue.toCharArray());
  }
}
