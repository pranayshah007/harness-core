/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.importset;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ServiceNowException;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.servicenow.ServiceNowImportSetTransformMapResult;

import com.nimbusds.oauth2.sdk.util.StringUtils;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(CDC)
public class ServiceNowImportSetTransformMapOutcome implements Outcome {
  String targetRecordURL;
  String targetTable;
  @NotNull String transformMap;
  @NotNull String status;
  String displayName;
  String displayValue;
  String errorMessage;
  String statusMessage;

  private static final String SERVICENOW_ERROR_STATUS = "error";

  public static ServiceNowImportSetTransformMapOutcome fromServiceNowImportSetTransformMapResult(
      ServiceNowImportSetTransformMapResult result) {
    if (isNull(result.getStatus()) || isNull(result.getTransformMap())) {
      throw new ServiceNowException(
          "Invalid transform map details received from ServiceNow, missing transform map or status field",
          SERVICENOW_ERROR, USER);
    }

    if (StringUtils.isBlank(result.getTransformMap()) && SERVICENOW_ERROR_STATUS.equals(result.getStatus())) {
      result.setErrorMessage(result.getErrorMessage()
          + ", please ensure that transform map is defined corresponding to the staging table");
    }

    return ServiceNowImportSetTransformMapOutcome.builder()
        .targetRecordURL(result.getTargetRecordURL())
        .targetTable(result.getTargetTable())
        .transformMap(result.getTransformMap())
        .status(result.getStatus())
        .displayName(result.getDisplayName())
        .displayValue(result.getDisplayValue())
        .errorMessage(result.getErrorMessage())
        .statusMessage(result.getStatusMessage())
        .build();
  }
}
