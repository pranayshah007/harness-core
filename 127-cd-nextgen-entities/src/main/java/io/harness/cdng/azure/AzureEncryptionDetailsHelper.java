/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure;

import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import javax.annotation.Nonnull;

@Singleton
public class AzureEncryptionDetailsHelper {
  @Inject @Named("PRIVILEGED") private SecretManagerClientService secretManagerClientService;

  public List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull AzureConnectorDTO azureConnectorDTO, @Nonnull NGAccess ngAccess) {
    return secretManagerClientService.getEncryptionDetails(ngAccess, azureConnectorDTO);
  }
}
