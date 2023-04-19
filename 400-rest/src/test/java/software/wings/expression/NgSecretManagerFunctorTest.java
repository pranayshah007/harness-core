/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.utils.SecretUtils.getBase64SecretIdentifier;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.data.algorithm.HashGenerator;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FunctorException;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.ng.core.BaseNGAccess;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.VaultConfig;
import software.wings.service.intfc.security.SecretManager;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CDP)
public class NgSecretManagerFunctorTest extends WingsBaseTest {
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private DelegateMetricsService delegateMetricsService;
  @Inject private SecretManager secretManager;
  @Mock private Cache<String, EncryptedDataDetails> secretsCache;
  private static String ORG_ID = "orgId";
  private static String PROJECT_ID = "projectId";

  private NgSecretManagerFunctor buildFunctor(int token) {
    return NgSecretManagerFunctor.builder()
        .secretManager(secretManager)
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .secretsCache(secretsCache)
        .expressionFunctorToken(token)
        .ngSecretService(ngSecretService)
        .delegateMetricsService(delegateMetricsService)
        .build();
  }

  @Test(expected = FunctorException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testRejectInternalFunctor() {
    final String secretName = "MySecretName";
    int token = HashGenerator.generateIntegerHash();

    NgSecretManagerFunctor ngSecretManagerFunctor = buildFunctor(token);
    ngSecretManagerFunctor.obtain(secretName, HashGenerator.generateIntegerHash());
  }

  private void assertFunctor(NgSecretManagerFunctor ngSecretManagerFunctor) {
    assertThat(ngSecretManagerFunctor.getEvaluatedSecrets()).isNotNull();
    assertThat(ngSecretManagerFunctor.getEvaluatedDelegateSecrets()).isNotNull();
    assertThat(ngSecretManagerFunctor.getEncryptionConfigs()).isNotNull();
    assertThat(ngSecretManagerFunctor.getSecretDetails()).isNotNull();
  }

  private List<EncryptedDataDetail> generateEncryptedDataDetails() {
    VaultConfig vaultConfig = VaultConfig.builder().build();

    EncryptedData encryptedData = EncryptedData.builder().accountId(ACCOUNT_ID).build();
    encryptedData.setUuid(UUIDGenerator.generateUuid());
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(EncryptedDataDetail.builder()
                                 .encryptedData(SecretManager.buildRecordData(encryptedData))
                                 .encryptionConfig(vaultConfig)
                                 .build());

    return encryptedDataDetails;
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDecryptVaultEncryptedText() {
    final String secretName = "MySecretName";
    int token = HashGenerator.generateIntegerHash();
    NgSecretManagerFunctor ngSecretManagerFunctor = buildFunctor(token);
    assertFunctor(ngSecretManagerFunctor);

    List<EncryptedDataDetail> encryptedDataDetails = generateEncryptedDataDetails();
    when(ngSecretService.getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class)))
        .thenReturn(encryptedDataDetails);

    String decryptedValue = (String) ngSecretManagerFunctor.obtain(secretName, token);
    assertDelegateDecryptedValue(secretName, ngSecretManagerFunctor, decryptedValue);
    verify(ngSecretService, times(1)).getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class));

    decryptedValue = (String) ngSecretManagerFunctor.obtain(secretName, token);
    assertDelegateDecryptedValue(secretName, ngSecretManagerFunctor, decryptedValue);
    verify(ngSecretService, times(1)).getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testMultipleEncryptionConfigs() {
    String[] sampleSecretNames = new String[] {"secret1", "secret2"};
    int token = HashGenerator.generateIntegerHash();
    NgSecretManagerFunctor ngSecretManagerFunctor = buildFunctor(token);
    assertFunctor(ngSecretManagerFunctor);

    List<EncryptedDataDetail> encryptedDataDetails = generateEncryptedDataDetails();
    when(ngSecretService.getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class)))
        .thenReturn(encryptedDataDetails);

    for (String secretName : sampleSecretNames) {
      String decryptedValue = (String) ngSecretManagerFunctor.obtain(secretName, token);
      assertDelegateDecryptedValue(secretName, ngSecretManagerFunctor, decryptedValue);
    }

    assertThat(ngSecretManagerFunctor.getEncryptionConfigs().size()).isEqualTo(sampleSecretNames.length);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDecryptVaultEncryptedTextWithDryRunMode() {
    final String secretName = "MySecretName";
    int token = HashGenerator.generateIntegerHash();
    NgSecretManagerFunctor ngSecretManagerFunctor = buildFunctor(token);
    assertFunctor(ngSecretManagerFunctor);

    on(ngSecretManagerFunctor).set("mode", SecretManagerMode.DRY_RUN);
    List<EncryptedDataDetail> encryptedDataDetails = generateEncryptedDataDetails();
    when(ngSecretService.getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class)))
        .thenReturn(encryptedDataDetails);

    String decryptedValue = (String) ngSecretManagerFunctor.obtain(secretName, token);
    assertThat(decryptedValue).isNotNull().isEqualTo("${ngSecretManager.obtain(\"MySecretName\", " + token + ")}");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testErrorCasesInDecryptVaultEncryptedText() {
    int token = HashGenerator.generateIntegerHash();
    NgSecretManagerFunctor ngSecretManagerFunctor = buildFunctor(token);
    assertFunctor(ngSecretManagerFunctor);

    try {
      ngSecretManagerFunctor.obtain("invalidSecretName", HashGenerator.generateIntegerHash());
      fail("Should not reach here.");
    } catch (Exception ex) {
      assertThat(ExceptionUtils.getMessage(ex))
          .isEqualTo(
              "There was a critical error due to Inappropriate usage of internal functor while evaluating expression ${}");
    }

    when(ngSecretService.getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class)))
        .thenReturn(Collections.emptyList());
    try {
      ngSecretManagerFunctor.obtain("invalidSecretName", token);
      fail("Should not reach here.");
    } catch (Exception ex) {
      assertThat(ExceptionUtils.getMessage(ex.getCause()))
          .isEqualTo("Invalid request: No secret found with identifier + [invalidSecretName]");
    }

    List<EncryptedDataDetail> encryptedDataDetails = generateEncryptedDataDetails();
    encryptedDataDetails.add(encryptedDataDetails.get(0));
    when(ngSecretService.getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class)))
        .thenReturn(encryptedDataDetails);
    try {
      ngSecretManagerFunctor.obtain("invalidSecretName", token);
      fail("Should not reach here.");
    } catch (Exception ex) {
      assertThat(ExceptionUtils.getMessage(ex.getCause()))
          .isEqualTo("Invalid request: More than one encrypted records associated with + [invalidSecretName]");
    }
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testObtainSecretFileAsString() {
    final String secretIdentifier = "fileIdentifier";
    int token = HashGenerator.generateIntegerHash();
    NgSecretManagerFunctor ngSecretManagerFunctor = buildFunctor(token);
    assertFunctor(ngSecretManagerFunctor);

    List<EncryptedDataDetail> encryptedDataDetails = generateEncryptedDataDetails();
    when(ngSecretService.getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class)))
        .thenReturn(encryptedDataDetails);

    String decryptedValue = (String) ngSecretManagerFunctor.obtainSecretFileAsString(secretIdentifier, token);
    assertThat(decryptedValue).isNotNull().startsWith("${secretDelegate.obtain(");
    assertDelegateDecryptedSecretFile(secretIdentifier, ngSecretManagerFunctor, decryptedValue);
    verify(ngSecretService, times(1)).getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testObtainSecretFileAsBase64() {
    final String secretIdentifier = "secretIdentifier";
    int token = HashGenerator.generateIntegerHash();
    NgSecretManagerFunctor ngSecretManagerFunctor = buildFunctor(token);
    assertFunctor(ngSecretManagerFunctor);

    List<EncryptedDataDetail> encryptedDataDetails = generateEncryptedDataDetails();
    when(ngSecretService.getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class)))
        .thenReturn(encryptedDataDetails);

    String decryptedValue = (String) ngSecretManagerFunctor.obtainSecretFileAsBase64(secretIdentifier, token);
    assertThat(decryptedValue).isNotNull().startsWith("${secretDelegate.obtainBase64(");
    assertDelegateDecryptedSecretFile(
        getBase64SecretIdentifier(secretIdentifier), ngSecretManagerFunctor, decryptedValue);
    verify(ngSecretService, times(1)).getEncryptionDetails(any(BaseNGAccess.class), any(SecretVariableDTO.class));
  }

  private void assertDelegateDecryptedValue(
      String secretName, NgSecretManagerFunctor ngSecretManagerFunctor, String decryptedValue) {
    assertThat(decryptedValue).isNotNull().startsWith("${secretDelegate.obtain(");
    assertThat(decryptedValue).isNotNull().contains(String.valueOf(ngSecretManagerFunctor.getExpressionFunctorToken()));
    assertThat(ngSecretManagerFunctor.getEvaluatedDelegateSecrets()).isNotEmpty().containsKey(secretName);
    assertThat(ngSecretManagerFunctor.getEvaluatedDelegateSecrets().get(secretName)).isNotEmpty();
    assertThat(ngSecretManagerFunctor.getEncryptionConfigs()).isNotEmpty();
  }

  private void assertDelegateDecryptedSecretFile(
      String secretIdentifier, NgSecretManagerFunctor ngSecretManagerFunctor, String decryptedValue) {
    assertThat(decryptedValue).isNotNull().contains(String.valueOf(ngSecretManagerFunctor.getExpressionFunctorToken()));
    assertThat(ngSecretManagerFunctor.getEvaluatedDelegateSecrets()).isNotEmpty().containsKey(secretIdentifier);
    assertThat(ngSecretManagerFunctor.getEvaluatedDelegateSecrets().get(secretIdentifier)).isNotEmpty();
    assertThat(ngSecretManagerFunctor.getEncryptionConfigs()).isNotEmpty();
  }
}
