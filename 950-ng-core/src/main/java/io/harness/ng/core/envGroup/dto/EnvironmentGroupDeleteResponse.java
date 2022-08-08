/*
 * Copyrignull021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.envGroup.dto;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "EnvironmentGroupDelete",
    description = "This is the view of Environment Group Delete Response defined in Harness")
public class EnvironmentGroupDeleteResponse {
  @Schema(description = "Value true, when the Entity is deleted") Boolean deleted;
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) String accountId;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE) String identifier;
}
