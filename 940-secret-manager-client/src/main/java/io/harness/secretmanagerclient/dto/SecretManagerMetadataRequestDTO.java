/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.security.encryption.EncryptionType;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Data
@NoArgsConstructor
@Schema(name = "Invite", description = "This is the view of the SecretManagerMetadataRequest entity defined in Harness")
public class SecretManagerMetadataRequestDTO implements DecryptableEntity {
  @Schema(description = "This specifies the type of encryption used by the Secret Manager to encrypt Secrets.")
  @NotNull
  private EncryptionType encryptionType;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) private String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) private String projectIdentifier;
  @Schema(description = "Identifier of the SecretManager metadata.") @NotNull private String identifier;

  @Schema(description = "Spec of the Secret Manager.")
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "encryptionType",
      visible = true)
  @JsonSubTypes(value = { @JsonSubTypes.Type(name = "VAULT", value = VaultMetadataRequestSpecDTO.class) })
  @Valid
  @NotNull
  private SecretManagerMetadataRequestSpecDTO spec;

  @Builder
  public SecretManagerMetadataRequestDTO(EncryptionType encryptionType, String orgIdentifier, String projectIdentifier,
      String identifier, SecretManagerMetadataRequestSpecDTO spec) {
    this.encryptionType = encryptionType;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.identifier = identifier;
    this.spec = spec;
  }
}
