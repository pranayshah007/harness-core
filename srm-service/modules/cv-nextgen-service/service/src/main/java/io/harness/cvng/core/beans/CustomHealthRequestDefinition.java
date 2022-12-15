/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "CustomHealthRequestDefinitionKeys")
public class CustomHealthRequestDefinition {
  String urlPath;
  String requestBody;
  CustomHealthMethod method;
  TimestampInfo startTimeInfo;
  TimestampInfo endTimeInfo;

  public void validateParams() {
    checkNotNull(getUrlPath(), generateErrorMessageFromParam(CustomHealthRequestDefinitionKeys.urlPath));
    checkNotNull(getMethod(), generateErrorMessageFromParam(CustomHealthRequestDefinitionKeys.method));
    checkNotNull(getStartTimeInfo(), generateErrorMessageFromParam(CustomHealthRequestDefinitionKeys.startTimeInfo));
    checkNotNull(getEndTimeInfo(), generateErrorMessageFromParam(CustomHealthRequestDefinitionKeys.endTimeInfo));
  }
}
