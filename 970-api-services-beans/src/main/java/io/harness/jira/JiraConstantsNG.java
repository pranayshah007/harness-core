/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.utils.DateTimeUtils;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(CDC)
public interface JiraConstantsNG {
  DateTimeFormatter DATE_FORMATTER = DateTimeUtils.DATE_FORMATTER;
  DateTimeFormatter[] DATE_FORMATTERS = {
      DATE_FORMATTER, DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.BASIC_ISO_DATE};

  DateTimeFormatter DATETIME_FORMATTER = DateTimeUtils.DATETIME_FORMATTER;
  DateTimeFormatter[] DATETIME_FORMATTERS = {DATETIME_FORMATTER, DateTimeFormatter.ISO_DATE_TIME,
      DateTimeFormatter.ISO_OFFSET_DATE_TIME, DateTimeFormatter.ISO_ZONED_DATE_TIME, DateTimeFormatter.ISO_INSTANT};

  Pattern COMMA_SPLIT_PATTERN = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

  // Special fields
  String PROJECT_KEY = "project";
  String PROJECT_INTERNAL_NAME = "__" + PROJECT_KEY;

  String ISSUE_TYPE_KEY = "issuetype";
  String ISSUE_TYPE_NAME = "Issue Type";
  String ISSUE_TYPE_INTERNAL_NAME = "__" + ISSUE_TYPE_KEY;

  String STATUS_KEY = "status";
  String STATUS_NAME = "Status";
  String STATUS_INTERNAL_NAME = "__" + STATUS_KEY;

  String TIME_TRACKING_KEY = "timetracking";
  String ORIGINAL_ESTIMATE_NAME = "Original Estimate";
  String REMAINING_ESTIMATE_NAME = "Remaining Estimate";
  String ORIGINAL_ESTIMATE_KEY = "originalEstimate";
  String REMAINING_ESTIMATE_KEY = "remainingEstimate";
}
