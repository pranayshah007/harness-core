/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.SecretConstants.LATEST;
import static io.harness.SecretConstants.REGIONS;
import static io.harness.SecretConstants.VERSION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.secretmanagerclient.ValueType.Inline;

import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.security.encryption.AdditionalMetadata;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AdditionalMetadataValidationHelper {
  public void validateAdditionalMetadataForGcpSecretManager(SecretSpecDTO secretSpecDTO) {
    if (secretSpecDTO instanceof SecretTextSpecDTO) {
      validateAdditionalMetadataInSecretTextSpecDTOForGcpSecretManager((SecretTextSpecDTO) secretSpecDTO);
    } else if (secretSpecDTO instanceof SecretFileSpecDTO) {
      validateAdditionalMetadataInSecretFileSpecDTOForGcpSecretManager((SecretFileSpecDTO) secretSpecDTO);
    } else {
      log.warn(String.format("Additional metadata validation does not exist for %s", secretSpecDTO.getClass()));
    }
  }

  private void validateAdditionalMetadataInSecretFileSpecDTOForGcpSecretManager(SecretFileSpecDTO secret) {
    if (secret.getAdditionalMetadata() == null) {
      return;
    }
    validateExpectedKeyInValuesMap(secret.getAdditionalMetadata(), REGIONS);
  }

  private void validateExpectedKeyInValuesMap(AdditionalMetadata additionalMetadata, String key) {
    Map<String, Object> values = additionalMetadata.getValues();
    if (isEmpty(values)) {
      throw new InvalidRequestException("Additional metadata must have values map");
    }
    if (values.size() > 1 || !values.containsKey(key)) {
      throw new InvalidRequestException(String.format(
          "Additional metadata values map must have only one key - %s for secrets created using google secret manager.",
          key));
    }
    if (values.get(key) == null || isEmpty(String.valueOf(values.get(key)))) {
      throw new InvalidRequestException(
          String.format("%s should not be empty for secret created using google secret manager.", key));
    }
  }

  private void validateVersionInformation(Object version) {
    String versionString = String.valueOf(version);
    if (version.equals(LATEST)) {
      return;
    }
    try {
      Integer.parseInt(versionString);
    } catch (NumberFormatException numberFormatException) {
      throw new InvalidRequestException("Version should be either latest or an integer.");
    }
  }

  private void validateAdditionalMetadataInSecretTextSpecDTOForGcpSecretManager(SecretTextSpecDTO secretTextSpecDTO) {
    if (Inline.equals(secretTextSpecDTO.getValueType())) {
      if (secretTextSpecDTO.getAdditionalMetadata() == null) {
        return;
      }
      validateExpectedKeyInValuesMap(secretTextSpecDTO.getAdditionalMetadata(), REGIONS);
    } else {
      if (secretTextSpecDTO.getAdditionalMetadata() == null) {
        throw new InvalidRequestException(
            "Additional metadata must be present for reference secret created using google secret manager connector");
      }
      validateExpectedKeyInValuesMap(secretTextSpecDTO.getAdditionalMetadata(), VERSION);
      validateVersionInformation(secretTextSpecDTO.getAdditionalMetadata().getValues().get(VERSION));
    }
  }
}
