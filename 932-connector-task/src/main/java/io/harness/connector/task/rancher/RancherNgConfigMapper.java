/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigAuthCredentialsDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigAuthDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.govern.Switch;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

// Specific to Manager side connector validation
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class RancherNgConfigMapper {
  @Inject private DecryptionHelper decryptionHelper;

  public RancherConfig mapRancherConfigWithDecryption(
      RancherConnectorDTO rancherConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    RancherConnectorConfigDTO rancherConnectorConfigDTO = rancherConnectorDTO.getConfig();
    RancherConfigType rancherConfigType = rancherConnectorConfigDTO.getConfigType();
    RancherConfig rancherConfig = null;

    if (rancherConfigType == RancherConfigType.MANUAL_CONFIG) {
      RancherConnectorConfigAuthDTO rancherConnectorConfigAuthDTO = rancherConnectorConfigDTO.getConfig();
      RancherConnectorConfigAuthCredentialsDTO rancherConnectorConfigAuthCredentialsDTO =
          rancherConnectorConfigAuthDTO.getCredentials();
      RancherConnectorBearerTokenAuthenticationDTO rancherConnectorBearerTokenAuthenticationDTO =
          (RancherConnectorBearerTokenAuthenticationDTO) rancherConnectorConfigAuthCredentialsDTO.getAuth();
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          rancherConnectorBearerTokenAuthenticationDTO, encryptionDetails);
      rancherConfig =
          RancherConfig.builder()
              .credential(RancherManualConfigCredentials.builder()
                              .rancherUrl(rancherConnectorConfigAuthDTO.getRancherUrl())
                              .password(RancherBearerTokenAuthPassword.builder()
                                            .rancherPassword(getDecryptedValueWithNullCheck(
                                                rancherConnectorBearerTokenAuthenticationDTO.getPasswordRef()))
                                            .build())
                              .build())

              /*.spotAccountId(getSecretAsStringFromPlainTextOrSecretRef(
                                           config.getSpotAccountId(), config.getSpotAccountIdRef()))
                                       .appTokenId(getDecryptedValueWithNullCheck(config.getApiTokenRef()))
                                      .build())
           */
              .build();
    } else {
      Switch.unhandled(rancherConfigType);
    }

    return rancherConfig;
  }

  private String getDecryptedValueWithNullCheck(SecretRefData passwordRef) {
    if (passwordRef != null && passwordRef.getDecryptedValue() != null) {
      return String.valueOf(passwordRef.getDecryptedValue());
    }
    return null;
  }
}
