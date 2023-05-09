/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awskmsconnector.outcome;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AwsKmsConnectorCredentialOutcomeDTO {
  @Schema(description = SecretManagerDescriptionConstants.AWS_CREDENTIAL) @NotNull AwsKmsCredentialType type;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  AwsKmsCredentialSpecDTO spec;

  @Builder
  public AwsKmsConnectorCredentialOutcomeDTO(AwsKmsCredentialType type, AwsKmsCredentialSpecDTO config) {
    this.type = type;
    this.spec = config;
  }
}
