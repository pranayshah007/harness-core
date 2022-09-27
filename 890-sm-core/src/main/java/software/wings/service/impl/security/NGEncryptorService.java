/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import java.util.List;

@OwnedBy(PL)
public interface NGEncryptorService {
  void decryptEncryptionConfigSecrets(ConnectorConfigDTO connectorConfigDTO, String accountIdentifier,
      String projectIdentifier, String orgIdentifier, boolean maskSecrets);

  DecryptableEntity decryptEncryptedDetails(
      DecryptableEntity decryptableEntity, List<EncryptedDataDetail> encryptedDataDetailList, String accountIdentifier);

  char[] fetchSecretValue(
      String accountIdentifier, EncryptedRecordData encryptedData, EncryptionConfig secretManagerConfig);
}
