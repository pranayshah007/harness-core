/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awssecretmanager;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("AwsSecretManagerCredential")
@JsonDeserialize(using = AwsSMCredentialDTODeserializer.class)
@Schema(name = "AwsSecretManagerCredential",
    description = "This contains the credential type and configuration of the AWS Secret Manager.")
public class AwsSecretManagerCredentialDTO {
  @NotNull
  @JsonProperty("type")
  @Schema(description = SecretManagerDescriptionConstants.AWS_CREDENTIAL)
  AwsSecretManagerCredentialType credentialType;
  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  AwsSecretManagerCredentialSpecDTO config;

  @Builder
  public AwsSecretManagerCredentialDTO(
      AwsSecretManagerCredentialType credentialType, AwsSecretManagerCredentialSpecDTO config) {
    this.credentialType = credentialType;
    this.config = config;
  }
}
