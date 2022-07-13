/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@OwnedBy(HarnessTeam.DX)
@Data
@Builder
public class InfrastructureMappingDTO {
  @Nullable private String id;
  @NonNull private String accountIdentifier;
  @NonNull private String orgIdentifier;
  @NonNull private String projectIdentifier;
  @NonNull private String infrastructureKind;
  @NonNull private String envIdentifier;
  @NonNull private String serviceIdentifier;
  @NonNull private String infrastructureKey;
  private String connectorRef;
}
