/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.rancher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.rancher.RancherConfigType;
import io.harness.delegate.beans.connector.rancher.RancherConnectorBearerTokenAuthenticationDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigAuthDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

// Specific to Manager side connector validation
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class RancherNgConfigMapper {
  @Inject private DecryptionHelper decryptionHelper;

  public RancherConfig rancherConnectorDTOToConfig(
      RancherConnectorDTO rancherConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    RancherConnectorConfigDTO rancherConnectorConfigDTO = rancherConnectorDTO.getConfig();
    RancherConfigType rancherConfigType = rancherConnectorConfigDTO.getConfigType();

    if (rancherConfigType == RancherConfigType.MANUAL_CONFIG) {
      RancherConnectorConfigAuthDTO configDto = rancherConnectorConfigDTO.getConfig();
      RancherConnectorBearerTokenAuthenticationDTO authDTO =
          (RancherConnectorBearerTokenAuthenticationDTO) configDto.getCredentials().getAuth();
      authDTO = validateAndDecryptCredentials(authDTO, encryptionDetails);
      return RancherConfig.builder()
          .manualConfig(RancherManualConfig.builder()
                            .rancherUrl(configDto.getRancherUrl())
                            .password(RancherBearerTokenAuthPassword.builder()
                                          .rancherPassword(getDecryptedValueWithNullCheck(authDTO.getPasswordRef()))
                                          .build())
                            .build())
          .build();
    }

    throw new IllegalArgumentException("Unsupported rancher connector type provided: " + rancherConfigType);
  }

  private RancherConnectorBearerTokenAuthenticationDTO validateAndDecryptCredentials(
      RancherConnectorBearerTokenAuthenticationDTO authDTO, List<EncryptedDataDetail> encryptionDetails) {
    RancherConnectorBearerTokenAuthenticationDTO authenticationDTO =
        (RancherConnectorBearerTokenAuthenticationDTO) decryptionHelper.decrypt(authDTO, encryptionDetails);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(authenticationDTO, encryptionDetails);
    return authenticationDTO;
  }

  private String getDecryptedValueWithNullCheck(SecretRefData passwordRef) {
    if (passwordRef != null && passwordRef.getDecryptedValue() != null) {
      return String.valueOf(passwordRef.getDecryptedValue());
    }
    return null;
  }
}
