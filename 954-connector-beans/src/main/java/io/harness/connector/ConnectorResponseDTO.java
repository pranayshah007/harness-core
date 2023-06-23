/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.ConnectorConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.governance.GovernanceMetadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("ConnectorResponse")
@OwnedBy(DX)
@Schema(name = "ConnectorResponse", description = "This has the Connector details along with its metadata.")
public class ConnectorResponseDTO {
  ConnectorInfoDTO connector;
  @Schema(description = ConnectorConstants.CREATED_AT) Long createdAt;
  @Schema(description = ConnectorConstants.LAST_MODIFIED_AT) Long lastModifiedAt;
  ConnectorConnectivityDetails status;
  ConnectorActivityDetails activityDetails;
  @Schema(description = ConnectorConstants.HARNESS_MANAGED) boolean harnessManaged;
  EntityGitDetails gitDetails;
  EntityValidityDetails entityValidityDetails;
  GovernanceMetadata governanceMetadata;
  Boolean isFavorite;
}
