/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.spot;

import static io.harness.utils.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotManualConfigSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.govern.Switch;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

// Specific to Manager side connector validation
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class SpotNgConfigMapper {
  @Inject private DecryptionHelper decryptionHelper;

  public SpotConfig mapSpotConfigWithDecryption(
      SpotConnectorDTO spotConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    SpotCredentialDTO credential = spotConnectorDTO.getCredential();
    SpotCredentialType spotCredentialType = credential.getSpotCredentialType();
    SpotConfig spotConfig = null;

    if (spotCredentialType == SpotCredentialType.MANUAL_CREDENTIALS) {
      SpotManualConfigSpecDTO config = (SpotManualConfigSpecDTO) credential.getConfig();
      config = (SpotManualConfigSpecDTO) decryptionHelper.decrypt(config, encryptionDetails);
      spotConfig = SpotConfig.builder()
                       .spotManualCredential(SpotManualCredential.builder()
                                                 .accountId(getSecretAsStringFromPlainTextOrSecretRef(
                                                     config.getAccountId(), config.getAccountIdRef()))
                                                 .appTokenId(getDecryptedValueWithNullCheck(config.getApiTokenRef()))
                                                 .build())
                       .build();
    } else {
      Switch.unhandled(spotCredentialType);
    }
    return spotConfig;
  }

  private String getDecryptedValueWithNullCheck(SecretRefData passwordRef) {
    if (passwordRef != null && passwordRef.getDecryptedValue() != null) {
      return new String(passwordRef.getDecryptedValue());
    }
    return null;
  }
}
