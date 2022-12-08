/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.managerproxy;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.delegatetasks.ValidateSecretReferenceTaskParameters;
import io.harness.encryptors.CustomEncryptor;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.common.EncryptedDataParams;
import io.harness.security.encryption.common.EncryptedRecord;
import io.harness.security.encryption.common.EncryptionConfig;

import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.service.intfc.security.CustomEncryptedDataDetailBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;

@ValidateOnExecution
@OwnedBy(PL)
@Singleton
public class ManagerCustomEncryptor implements CustomEncryptor {
  private final ManagerEncryptorHelper managerEncryptorHelper;
  private final CustomEncryptedDataDetailBuilder customEncryptedDataDetailBuilder;

  @Inject
  public ManagerCustomEncryptor(ManagerEncryptorHelper managerEncryptorHelper,
      CustomEncryptedDataDetailBuilder customEncryptedDataDetailBuilder) {
    this.managerEncryptorHelper = managerEncryptorHelper;
    this.customEncryptedDataDetailBuilder = customEncryptedDataDetailBuilder;
  }

  @Override
  public boolean validateReference(
      String accountId, Set<EncryptedDataParams> params, EncryptionConfig encryptionConfig) {
    customEncryptedDataDetailBuilder.buildEncryptedDataDetail(
        EncryptedData.builder().name("Test Secret").parameters(params).build(),
        (CustomSecretsManagerConfig) encryptionConfig);
    ValidateSecretReferenceTaskParameters parameters =
        ValidateSecretReferenceTaskParameters.builder()
            .encryptedRecord(EncryptedRecordData.builder().parameters(params).build())
            .encryptionConfig(encryptionConfig)
            .build();
    return managerEncryptorHelper.validateReference(accountId, parameters);
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    customEncryptedDataDetailBuilder.buildEncryptedDataDetail(
        EncryptedData.builder().name("Test Secret").parameters(encryptedRecord.getParameters()).build(),
        (CustomSecretsManagerConfig) encryptionConfig);
    return managerEncryptorHelper.fetchSecretValue(accountId, encryptedRecord, encryptionConfig);
  }
}
