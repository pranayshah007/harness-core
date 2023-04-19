/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import io.harness.ng.core.models.SSHAuth;
import io.harness.secretmanagerclient.SSHAuthScheme;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(name = "SSHAuth", description = "This is the SSH Authentication specification defined in Harness.")
public class SSHAuthDTO {
  @JsonProperty("type")
  @NotNull
  @Schema(description = "Specifies authentication scheme, SSH or Kerberos")
  private SSHAuthScheme authScheme;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type", visible = true)
  @NotNull
  @Valid
  private BaseSSHSpecDTO spec;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private boolean useSshClient;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private boolean useSshj;

  @Builder
  public SSHAuthDTO(SSHAuthScheme type, BaseSSHSpecDTO spec) {
    this.authScheme = type;
    this.spec = spec;
  }

  public SSHAuth toEntity() {
    return SSHAuth.builder().type(authScheme).sshSpec(spec.toEntity()).build();
  }
}
