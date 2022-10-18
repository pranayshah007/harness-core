/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.models;

import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.secretmanagerclient.ValueType;
import io.harness.security.encryption.AdditionalMetadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("SecretText")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecretTextSpec extends SecretSpec {
  private String secretManagerIdentifier;
  private ValueType valueType;
  private String value;
  private AdditionalMetadata additionalMetadata;

  @Override
  public SecretSpecDTO toDTO() {
    return SecretTextSpecDTO.builder()
        .secretManagerIdentifier(getSecretManagerIdentifier())
        .valueType(getValueType())
        .additionalMetadata(getAdditionalMetadata())
        .value(value)
        .build();
  }
}
