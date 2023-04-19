/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.NoOpCache;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.EncryptedDataDetails;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.LocalEncryptionConfig;

import java.util.Arrays;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CHostedVmSecretManagerFunctorTest extends CIExecutionTestBase {
  @Mock SecretManagerClientService ngSecretService;

  private static NGAccess ngAccess;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final int TOKEN = 123;
  private static final String SECRET = "secret";

  @BeforeClass
  public static void beforeClass() throws Exception {
    ngAccess = BaseNGAccess.builder()
                   .accountIdentifier(ACCOUNT_ID)
                   .orgIdentifier(ORG_ID)
                   .projectIdentifier(PROJECT_ID)
                   .build();
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void obtainSuccess() {
    String key = "abcdefghijklmnopabcdefghijklmnop";
    String secretVal = SECRET;
    SimpleEncryption encryption = new SimpleEncryption(key);
    char[] encryptedChars = encryption.encryptChars(secretVal.toCharArray());
    String encryptedString = new String(encryptedChars);

    EncryptedDataDetail encryptedDataDetail =
        EncryptedDataDetail.builder()
            .encryptedData(EncryptedRecordData.builder()
                               .encryptionKey(key)
                               .encryptionType(EncryptionType.LOCAL)
                               .encryptedValue(encryptedString.toCharArray())
                               .base64Encoded(false)
                               .build())
            .fieldName("secret")
            .encryptionConfig(
                LocalEncryptionConfig.builder().encryptionType(EncryptionType.LOCAL).accountId(ACCOUNT_ID).build())
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(Arrays.asList(encryptedDataDetail));
    HostedVmSecretManagerFunctor functor = getFunctor();
    Object response = functor.obtain("secret", TOKEN);
    assertThat(secretVal).isEqualTo(response);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void obtainFailure() {
    String key = "abcdefghijklmnopabcdefghijklmnop";
    String secretVal = SECRET;
    SimpleEncryption encryption = new SimpleEncryption(key);
    char[] encryptedChars = encryption.encryptChars(secretVal.toCharArray());
    String encryptedString = new String(encryptedChars);

    EncryptedDataDetail encryptedDataDetail =
        EncryptedDataDetail.builder()
            .encryptedData(EncryptedRecordData.builder()
                               .encryptionKey(key)
                               .encryptionType(EncryptionType.CUSTOM)
                               .encryptedValue(encryptedString.toCharArray())
                               .base64Encoded(false)
                               .build())
            .fieldName("secret")
            .encryptionConfig(
                LocalEncryptionConfig.builder().encryptionType(EncryptionType.LOCAL).accountId(ACCOUNT_ID).build())
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(Arrays.asList(encryptedDataDetail));
    HostedVmSecretManagerFunctor functor = getFunctor();
    functor.obtain("secret", TOKEN);
  }

  private HostedVmSecretManagerFunctor getFunctor() {
    Cache<String, EncryptedDataDetails> secretsCache = new NoOpCache<>();
    return HostedVmSecretManagerFunctor.builder()
        .expressionFunctorToken(TOKEN)
        .ngAccess(ngAccess)
        .ngSecretService(ngSecretService)
        .secretsCache(secretsCache)
        .build();
  }
}
