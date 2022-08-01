/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineNotificationConstants {
  static String OUTER_DIV =
      "<div style=\"margin:15px; padding-left:7px; border-left-width:3px; border-radius:3px; border-left-style:solid; font-size:small; border-left-color:";
  static final String RESUMED_COLOR = "#1DAEE2";
  static final String SUCCEEDED_COLOR = "#5CB04D";
  static final String FAILED_COLOR = "#EC372E";
  static final String PAUSED_COLOR = "#FBB731";
  static final String ABORTED_COLOR = "#77787B";
  static final String WHITE_COLOR = "#FFFFFF";
  static final String BLUE_COLOR = "#0078D7";
  static final String DEFAULT_TIME_FORMAT = "MMM dd' 'hh:mm a z";
  static final String FAILED_STATUS = "failed";
}
