/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.ng;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AzureWebAppInfraDelegateConfig {
  private AzureConnectorDTO azureConnectorDTO;
  private String appName;
  private String subscription;
  private String resourceGroup;
  private String deploymentSlot;
  private List<EncryptedDataDetail> encryptionDataDetails;

  @NotNull
  public List<DecryptableEntity> getDecryptableEntities() {
    if (azureConnectorDTO != null) {
      return azureConnectorDTO.getDecryptableEntities();
    }

    return Collections.emptyList();
  }
}
