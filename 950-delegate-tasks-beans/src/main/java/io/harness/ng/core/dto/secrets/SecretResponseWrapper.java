/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PL)
@Data
@Builder
@Schema(name = "SecretResponse", description = "This has details of the Secret along with its metadata.")
public class SecretResponseWrapper {
  @NotNull private SecretDTOV2 secret;
  @Schema(description = "This is the time at which the Secret was created.") private Long createdAt;
  @Schema(description = "This is the time at which the Secret was last updated.") private Long updatedAt;
  private boolean draft;
  GovernanceMetadata governanceMetadata;
}
