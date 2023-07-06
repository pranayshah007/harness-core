/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans.host.dto;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.infra.beans.host.dto.HostFilterDTO")
public class HostFilterDTO {
  HostFilterType type;
  HostFilterSpecDTO spec;

  @Builder
  public HostFilterDTO(HostFilterType type, HostFilterSpecDTO spec) {
    this.type = type;
    this.spec = spec;
  }
}
