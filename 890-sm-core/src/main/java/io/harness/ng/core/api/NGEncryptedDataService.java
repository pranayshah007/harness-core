/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DecryptedSecretValue;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.security.encryption.EncryptedDataDetail;

import java.io.InputStream;
import java.util.List;

@OwnedBy(PL)
public interface NGEncryptedDataService {
  NGEncryptedData createSecretText(String accountIdentifier, SecretDTOV2 dto);

  NGEncryptedData createSecretFile(String accountIdentifier, SecretDTOV2 dto, InputStream inputStream);

  NGEncryptedData createSecretFile(
      String accountIdentifier, SecretDTOV2 dto, String encryptionKey, String encryptedValue);

  List<EncryptedDataDetail> getEncryptionDetails(NGAccess ngAccess, DecryptableEntity object);

  NGEncryptedData get(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  NGEncryptedData getFromReferenceExpression(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String secretIdentifier);

  boolean delete(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, boolean forceDelete);

  NGEncryptedData hardDelete(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  NGEncryptedData updateSecretText(String accountIdentifier, SecretDTOV2 dto);

  NGEncryptedData updateSecretFile(String accountIdentifier, SecretDTOV2 dto, InputStream inputStream);

  DecryptedSecretValue decryptSecret(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  boolean validateSecretRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SecretDTOV2 secretDTO);

  boolean isSecretManagerReadOnly(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String secretManagerId);
}
