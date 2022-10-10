/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InfrastructureResponse {
  InfrastructureResponseDTO infrastructure;
  Long createdAt;
  Long lastModifiedAt;

  @Builder
  public InfrastructureResponse(InfrastructureResponseDTO infrastructure, Long createdAt, Long lastModifiedAt) {
    this.infrastructure = infrastructure;
    this.createdAt = createdAt;
    this.lastModifiedAt = lastModifiedAt;
  }
}
