/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.connector.task.tas;

import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getValueFromPlainTextOrSecretRef;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TasNgConfigMapper {
  @Inject private DecryptionHelper decryptionHelper;

  public CloudFoundryConfig mapTasConfigWithDecryption(
      TasConnectorDTO tasConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    TasCredentialDTO credential = tasConnectorDTO.getCredential();
    TasCredentialType credentialType = credential.getType();
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().build();

    if (credentialType == TasCredentialType.MANUAL_CREDENTIALS) {
      TasManualDetailsDTO tasManualDetailsForDecryption = (TasManualDetailsDTO) credential.getSpec();
      TasManualDetailsDTO tasManualDetailsDTO =
          (TasManualDetailsDTO) decryptionHelper.decrypt(tasManualDetailsForDecryption, encryptionDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(tasManualDetailsDTO, encryptionDetails);
      cfConfig.setEndpointUrl(reformatEndpointURL(tasManualDetailsDTO.getEndpointUrl()));
      cfConfig.setUserName(
          getValueFromPlainTextOrSecretRef(tasManualDetailsDTO.getUsername(), tasManualDetailsDTO.getUsernameRef()));
      cfConfig.setPassword(getDecryptedValueWithNullCheck(tasManualDetailsDTO.getPasswordRef()));
    } else {
      throw new IllegalStateException("Unexpected Tas credential type : " + credentialType);
    }
    return cfConfig;
  }

  private char[] getDecryptedValueWithNullCheck(SecretRefData passwordRef) {
    if (passwordRef != null) {
      return passwordRef.getDecryptedValue();
    }
    return null;
  }
  private String reformatEndpointURL(String endpointUrl) {
    int colonIndex = endpointUrl.indexOf("://");
    if (colonIndex > 0) {
      return endpointUrl.substring(colonIndex + 3);
    }
    return endpointUrl;
  }
}
