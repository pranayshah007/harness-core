/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunction;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionInfraConfig;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionInfraConfigHelper {
  @Inject private SecretDecryptionService secretDecryptionService;

  public void decryptInfraConfig(GoogleFunctionInfraConfig googleFunctionInfraConfig) {
    GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig =
        (GcpGoogleFunctionInfraConfig) googleFunctionInfraConfig;
    decryptInfraConfig(
        gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(), gcpGoogleFunctionInfraConfig.getEncryptionDataDetails());
  }

  private void decryptInfraConfig(GcpConnectorDTO gcpConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    if (gcpConnectorDTO.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO gcpManualDetailsDTO = (GcpManualDetailsDTO) gcpConnectorDTO.getCredential().getConfig();
      secretDecryptionService.decrypt(gcpManualDetailsDTO, encryptedDataDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(gcpManualDetailsDTO, encryptedDataDetails);
    }
  }
}
