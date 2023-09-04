/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.events;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PmsEventMonitoringConstants {
  public String ACCOUNT_ID = "accountId";
  public String ORG_ID = "orgIdentifier";
  public String PROJECT_ID = "projectIdentifier";
  public String MODULE = "module";
  public String STEP_TYPE = "stepType";
  public String STATUS = "status";
  public String PIPELINE_IDENTIFIER = "pipelineIdentifier";
}
