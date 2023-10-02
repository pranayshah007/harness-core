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
import io.harness.ng.core.models.WinRmAuth;
import io.harness.secretmanagerclient.WinRmAuthScheme;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(name = "WinRmAuth", description = "This is the WinRm Authentication specification defined in Harness.")
@OwnedBy(CDP)
@RecasterAlias("io.harness.ng.core.dto.secrets.WinRmAuthDTO")
public class WinRmAuthDTO {
  @JsonProperty("type")
  @NotNull
  @Schema(description = "Specifies authentication scheme, NTLM or Kerberos")
  private WinRmAuthScheme authScheme;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type", visible = true)
  @NotNull
  @Valid
  private BaseWinRmSpecDTO spec;

  @Builder
  public WinRmAuthDTO(WinRmAuthScheme type, BaseWinRmSpecDTO spec) {
    this.authScheme = type;
    this.spec = spec;
  }

  public WinRmAuth toEntity() {
    return WinRmAuth.builder().type(authScheme).spec(spec.toEntity()).build();
  }
}
