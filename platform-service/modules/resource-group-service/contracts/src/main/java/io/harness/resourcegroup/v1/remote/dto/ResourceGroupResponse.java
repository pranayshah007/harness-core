/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.v1.remote.dto;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResourceGroupResponse {
  @NotNull private ResourceGroupDTO resourceGroup;
  private Long createdAt;
  private Long lastModifiedAt;
  private boolean harnessManaged;

  @Builder
  public ResourceGroupResponse(
      ResourceGroupDTO resourceGroup, Long createdAt, Long lastModifiedAt, boolean harnessManaged) {
    this.resourceGroup = resourceGroup;
    this.createdAt = createdAt;
    this.lastModifiedAt = lastModifiedAt;
    this.harnessManaged = harnessManaged;
  }
}
