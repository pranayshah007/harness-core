/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector;

import io.harness.ConnectorConstants;
import io.harness.ng.core.dto.ErrorDetail;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "ConnectorConnectivityDetailsKeys")
@Schema(description = ConnectorConstants.STATUS_DETAILS)
public class ConnectorConnectivityDetails {
  @Schema(description = ConnectorConstants.STATUS) ConnectivityStatus status;
  @Schema(description = ConnectorConstants.ERROR_SUMMARY) String errorSummary;
  @Schema(description = ConnectorConstants.ERRORS) List<ErrorDetail> errors;
  @Schema(description = ConnectorConstants.TESTED_AT) long testedAt;
  @Deprecated long lastTestedAt;
  @Schema(description = ConnectorConstants.LAST_CONNECTED_AT) long lastConnectedAt;
  @Schema(description = ConnectorConstants.LAST_ALERT) Long lastAlertSent;
}
