/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.constants;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class Inputs {
  public static final String BRANCH_NAME = "branchName";
  public static final String FILE_PATH = "filePath";
  public static final String SEVERITY_TYPE = "severityType";
  public static final String ACCOUNT_NAME = "accountName";
  public static final String JQL = "jql";
  public static final String PATTERN = "pattern";
}
