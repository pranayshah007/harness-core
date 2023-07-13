/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.models.BaseSSHSpec;
import io.harness.ng.core.models.SSHConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonTypeName("SSH")
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "SSHConfig", description = "This is the SSH configuration details defined in Harness.")
@OwnedBy(CDP)
@RecasterAlias("io.harness.ng.core.dto.secrets.SSHConfigDTO")
public class SSHConfigDTO implements BaseSSHSpecDTO {
  @Schema(description = "This specifies SSH credential type as Password, KeyPath or KeyReference")
  @NotNull
  SSHCredentialType credentialType;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "credentialType",
      visible = true)
  @Valid
  @NotNull
  private SSHCredentialSpecDTO spec;

  @Builder
  public SSHConfigDTO(SSHCredentialType credentialType, SSHCredentialSpecDTO spec) {
    this.credentialType = credentialType;
    this.spec = spec;
  }

  @Override
  public BaseSSHSpec toEntity() {
    return SSHConfig.builder().credentialType(getCredentialType()).spec(getSpec().toEntity()).build();
  }
}
