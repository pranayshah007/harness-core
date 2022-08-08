/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

public interface NotificationConstants {
  /**
   * The constant RESUMED_COLOR.
   */
  String RESUMED_COLOR = "#1DAEE2";
  /**
   * The constant COMPLETED_COLOR.
   */
  String COMPLETED_COLOR = "#5CB04D";
  /**
   * The constant FAILED_COLOR.
   */
  String FAILED_COLOR = "#EC372E";
  /**
   * The constant PAUSED_COLOR.
   */
  String PAUSED_COLOR = "#FBB731";
  /**
   * The constant ABORTED_COLOR.
   */
  String ABORTED_COLOR = "#77787B";
  /**
   * The constant WHITE_COLOR.
   */
  String WHITE_COLOR = "#FFFFFF";
  /**
   * The constant BLUE_COLOR.
   */
  String BLUE_COLOR = "0078D7";

  String FAILED_STATUS = "failed";

  String SLACK_WEBHOOK_URL_PREFIX = "https://hooks.slack.com/services/";

  static String getThemeColor(String status, String defaultColor) {
    switch (status) {
      case "completed":
        return COMPLETED_COLOR;
      case "expired":
      case "rejected":
      case FAILED_STATUS:
        return FAILED_COLOR;
      case "paused":
        return PAUSED_COLOR;
      case "resumed":
        return RESUMED_COLOR;
      case "aborted":
        return ABORTED_COLOR;
      default:
        return defaultColor;
    }
  }
}
