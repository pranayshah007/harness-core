/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.helper;

import io.harness.beans.DecryptableEntity;
import io.harness.connector.DecryptableEntityHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.DecryptableEntityWithEncryptionConsumers;
import io.harness.remote.client.NGRestClientExecutor;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class DecryptionHelperViaManager implements DecryptionHelper {
  @Inject SecretNGManagerClient ngSecretDecryptionClient;
  @Inject NGRestClientExecutor restClientExecutor;
  @Inject DecryptableEntityHelper decryptableEntityHelper;
  @Override
  public DecryptableEntity decrypt(DecryptableEntity decryptableEntity, List<EncryptedDataDetail> encryptionDetails) {
    final DecryptableEntityWithEncryptionConsumers build =
        decryptableEntityHelper.buildDecryptableEntityWithEncryptionConsumers(decryptableEntity, encryptionDetails);
    // While using connection via harness platform, custom secret managers are not allowed,
    // https://harness.atlassian.net/browse/CI-6003
    validateSecretManager(encryptionDetails);
    return restClientExecutor.getResponse(ngSecretDecryptionClient.decryptEncryptedDetails(build, "random"));
  }

  private void validateSecretManager(List<EncryptedDataDetail> encryptionDetails) {
    List<String> nonHarnessBuiltInManagedSecrets = new ArrayList<>();
    for (EncryptedDataDetail encryptedDataDetail : encryptionDetails) {
      EncryptedRecordData encryptedRecordData = encryptedDataDetail.getEncryptedData();

      if (encryptedRecordData.getEncryptionType() != EncryptionType.LOCAL) {
        if (!nonHarnessBuiltInManagedSecrets.contains(encryptedRecordData.getName())) {
          nonHarnessBuiltInManagedSecrets.add(encryptedRecordData.getName());
        }
      }
    }

    if (!nonHarnessBuiltInManagedSecrets.isEmpty()) {
      throw new InvalidRequestException(
          "Connection via Harness Platform is allowed only if secrets used to connect to the service are saved in Harness Built-in Secret Manager. Review the following secrets to use Connection via Harness Platform : "
          + nonHarnessBuiltInManagedSecrets);
    }
  }
}
