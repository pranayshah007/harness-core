/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import io.harness.beans.DecryptableEntity;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.ng.core.models.SSHExecutionCredentialSpec;
import io.harness.ng.core.models.SecretSpec;
import io.harness.secretmanagerclient.SSHAuthScheme;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("SSHKey")
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "SSHKeySpec", description = "This is the SSH key authentication details defined in Harness.")
public class SSHKeySpecDTO extends SecretSpecDTO {
  @Schema(description = "SSH port") int port;
  @Valid @NotNull SSHAuthDTO auth;

  @Override
  @JsonIgnore
  public Optional<String> getErrorMessageForInvalidYaml() {
    return Optional.empty();
  }

  @Override
  public SecretSpec toEntity() {
    return SSHExecutionCredentialSpec.builder().port(getPort()).auth(this.auth.toEntity()).build();
  }

  @Override
  public Optional<List<DecryptableEntity>> getDecryptableEntities() {
    if (auth.getAuthScheme() == SSHAuthScheme.SSH) {
      List<DecryptableEntity> decryptableEntities = new ArrayList<>();
      SSHConfigDTO spec = (SSHConfigDTO) auth.getSpec();
      switch (spec.getCredentialType()) {
        case KeyPath:
          decryptableEntities.add((SSHKeyPathCredentialDTO) spec.getSpec());
          break;
        case KeyReference:
          decryptableEntities.add((SSHKeyReferenceCredentialDTO) spec.getSpec());
          break;
        case Password:
          decryptableEntities.add((SSHPasswordCredentialDTO) spec.getSpec());
          break;
        default:
          throw new UnknownEnumTypeException("Credential Type", spec.credentialType.name());
      }
      return Optional.of(decryptableEntities);
    } else if (auth.getAuthScheme() == SSHAuthScheme.Kerberos) {
      KerberosConfigDTO spec = (KerberosConfigDTO) auth.getSpec();
      return Optional.ofNullable(spec.getSpec().getDecryptableEntities());
    }
    return Optional.empty();
  }

  @Builder
  public SSHKeySpecDTO(int port, SSHAuthDTO auth) {
    this.port = port;
    this.auth = auth;
  }
}
