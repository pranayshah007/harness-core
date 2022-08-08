/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.models;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.secretmanagerclient.WinRmAuthScheme;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@OwnedBy(CDP)
public class WinRmAuth {
  private WinRmAuthScheme type;
  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
  BaseWinRmSpec spec;

  public WinRmAuthDTO toDTO() {
    return WinRmAuthDTO.builder().type(type).spec(spec.toDTO()).build();
  }

  @Builder
  public WinRmAuth(WinRmAuthScheme type, BaseWinRmSpec spec) {
    this.type = type;
    this.spec = spec;
  }
}
