/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.encryptors;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTaskParameters;
import io.harness.delegatetasks.ValidateSecretReferenceTaskParameters;
import io.harness.encryptors.CustomEncryptor;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;

@ValidateOnExecution
@OwnedBy(PL)
@Singleton
public class NGManagerCustomEncryptor implements CustomEncryptor {
  private final DelegateGrpcClientWrapper delegateService;
  private final NGManagerEncryptorHelper ngManagerEncryptorHelper;

  @Inject
  public NGManagerCustomEncryptor(
      DelegateGrpcClientWrapper delegateService, NGManagerEncryptorHelper ngManagerEncryptorHelper) {
    this.delegateService = delegateService;
    this.ngManagerEncryptorHelper = ngManagerEncryptorHelper;
  }

  @Override
  public boolean validateReference(
      String accountId, Set<EncryptedDataParams> params, EncryptionConfig encryptionConfig) {
    ValidateSecretReferenceTaskParameters parameters =
        ValidateSecretReferenceTaskParameters.builder()
            .encryptedRecord(EncryptedRecordData.builder().parameters(params).build())
            .encryptionConfig(encryptionConfig)
            .build();

    return ngManagerEncryptorHelper.validateReference(accountId, parameters);
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    return ngManagerEncryptorHelper.fetchSecretValue(accountId, encryptedRecord, encryptionConfig);
  }
  @Override
  public boolean validateCustomConfiguration(String accountId, EncryptionConfig encryptionConfig) {
    ValidateSecretManagerConfigurationTaskParameters parameters =
        ValidateSecretManagerConfigurationTaskParameters.builder().encryptionConfig(encryptionConfig).build();
    return ngManagerEncryptorHelper.validateConfiguration(accountId, parameters);
  }
}
