/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import io.harness.beans.DecryptableEntity;
import io.harness.ng.core.models.SecretSpec;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Optional;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type", visible = true)
@JsonSubTypes(value =
    {
      @JsonSubTypes.Type(value = SecretTextSpecDTO.class, name = "SecretText")
      , @JsonSubTypes.Type(value = SecretFileSpecDTO.class, name = "SecretFile"),
          @JsonSubTypes.Type(value = SSHKeySpecDTO.class, name = "SSHKey"),
          @JsonSubTypes.Type(value = WinRmCredentialsSpecDTO.class, name = "WinRmCredentials"),
    })
@Schema(name = "SecretSpec", description = "This has details of the Secret defined in Harness.")
public abstract class SecretSpecDTO {
  public abstract Optional<String> getErrorMessageForInvalidYaml();

  public abstract SecretSpec toEntity();

  @JsonIgnore
  public Optional<List<DecryptableEntity>> getDecryptableEntities() {
    return Optional.empty();
  }
}
