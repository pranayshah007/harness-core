/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import io.harness.beans.DecryptableEntity;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.models.SSHCredentialSpec;
import io.harness.ng.core.models.SSHKeyPathCredential;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("KeyPath")
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "SSHKeyPathCredential", description = "This is SSH KeyPath credential specification as defined in harness")
public class SSHKeyPathCredentialDTO extends SSHCredentialSpecDTO implements DecryptableEntity {
  @Schema(description = "SSH Username.") @NotNull private String userName;
  @Schema(description = "Path of the key file.") @NotNull private String keyPath;
  @ApiModelProperty(dataType = "string")
  @SecretReference
  @Schema(description = "This is the passphrase provided while creating the SSH key for local encryption.")
  private SecretRefData encryptedPassphrase;

  @Override
  public SSHCredentialSpec toEntity() {
    return SSHKeyPathCredential.builder()
        .userName(getUserName())
        .keyPath(getKeyPath())
        .encryptedPassphrase(getEncryptedPassphrase())
        .build();
  }
}
