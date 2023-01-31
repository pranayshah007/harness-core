/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.terraformcloud;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialSpecDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.TerraformCloudCredentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class TerraformCloudConfigMapper {
  @Inject private DecryptionHelper decryptionHelper;

  public TerraformCloudConfig mapTerraformCloudConfigWithDecryption(
      TerraformCloudConnectorDTO terraformCloudConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    TerraformCloudCredentialDTO credential = terraformCloudConnectorDTO.getCredential();
    TerraformCloudCredentialType credentialType = credential.getType();
    TerraformCloudCredentialSpecDTO decryptedTerraformCloudCredentials =
        (TerraformCloudCredentialSpecDTO) decryptionHelper.decrypt(credential.getSpec(), encryptionDetails);
    TerraformCloudCredentials terraformCloudCredentials;
    if (credentialType == TerraformCloudCredentialType.API_TOKEN) {
      TerraformCloudTokenCredentialsDTO terraformCloudTokenCredentialsDTO =
          (TerraformCloudTokenCredentialsDTO) decryptedTerraformCloudCredentials;
      terraformCloudCredentials =
          TerraformCloudApiTokenCredentials.builder()
              .url(terraformCloudConnectorDTO.getTerraformCloudUrl())
              .token(String.valueOf(terraformCloudTokenCredentialsDTO.getApiToken().getDecryptedValue()))
              .build();
    } else {
      throw new IllegalStateException("Unexpected Terraform cloud credential type : " + credentialType);
    }
    return TerraformCloudConfig.builder().terraformCloudCredentials(terraformCloudCredentials).build();
  }
}
