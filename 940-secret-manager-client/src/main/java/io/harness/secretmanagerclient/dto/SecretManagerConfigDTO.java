/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonSubTypes.Type;

import io.harness.NGCommonEntityConstants;
import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigDTO;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMConfigDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigDTO;
import io.harness.security.encryption.EncryptionType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@OwnedBy(PL)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "encryptionType",
    visible = true)
@JsonSubTypes({
  @Type(name = "LOCAL", value = LocalConfigDTO.class)
  , @Type(name = "VAULT", value = VaultConfigDTO.class), @Type(name = "GCP_KMS", value = GcpKmsConfigDTO.class),
      @Type(name = "KMS", value = AwsKmsConfigDTO.class),
      @Type(name = "AZURE_VAULT", value = AzureKeyVaultConfigDTO.class),
      @Type(name = "AWS_SECRETS_MANAGER", value = AwsSMConfigDTO.class),
      @Type(name = "CUSTOM_SECRET_MANAGER", value = CustomSecretManagerConfigDTO.class)
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "SecretManagerConfig", description = "This has the details of the Secret Manager entity defined in Harness.")
public abstract class SecretManagerConfigDTO {
  @Schema(description = "Name of the Secret Manager.") private String name;
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) private String accountIdentifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) private String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) private String projectIdentifier;
  @Schema(description = "Tags for the Secret Manager.") private Map<String, String> tags;
  @Schema(description = "Identifier of the Secret Manager.") private String identifier;
  @Schema(description = "Description of the Secret Manager.") private String description;
  @Schema(description = "This specifies the type of encryption used by the Secret Manager to encrypt Secrets.")
  private EncryptionType encryptionType;

  @JsonProperty("default") @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;
  @Schema(description = SecretManagerDescriptionConstants.HARNESS_MANAGED) private boolean harnessManaged;
}
