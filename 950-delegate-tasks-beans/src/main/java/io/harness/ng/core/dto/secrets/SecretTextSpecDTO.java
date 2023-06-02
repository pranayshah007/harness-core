/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import io.harness.SecretConstants;
import io.harness.ng.core.models.SecretSpec;
import io.harness.ng.core.models.SecretTextSpec;
import io.harness.secretmanagerclient.ValueType;
import io.harness.security.encryption.AdditionalMetadata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("SecretText")
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "SecretTextSpec", description = "This has details of encrypted text secret.")
public class SecretTextSpecDTO extends SecretSpecDTO {
  @NotNull @Schema(description = SecretConstants.SECRET_MANAGER_IDENTIFIER) private String secretManagerIdentifier;
  @Schema(description = "This has details to specify if the secret value is inline or referenced.")
  @NotNull
  private ValueType valueType;
  @Schema(description = "Value of the Secret [Required]") private String value;
  @Schema(description = "Additional metadata for the secret") private AdditionalMetadata additionalMetadata;

  @Override
  @JsonIgnore
  public Optional<String> getErrorMessageForInvalidYaml() {
    if (valueType == ValueType.Inline && value != null) {
      return Optional.of("Inline secret text cannot be provided in YAML.");
    }
    if (valueType == ValueType.Reference && value == null) {
      return Optional.of("value cannot be empty for reference secret text in YAML.");
    }
    return Optional.empty();
  }

  @Override
  public SecretSpec toEntity() {
    return SecretTextSpec.builder()
        .secretManagerIdentifier(getSecretManagerIdentifier())
        .valueType(getValueType())
        .additionalMetadata(getAdditionalMetadata())
        .build();
  }
}
