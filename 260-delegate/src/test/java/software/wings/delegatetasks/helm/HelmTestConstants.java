/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static lombok.AccessLevel.PUBLIC;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.FieldDefaults;

@OwnedBy(CDP)
@FieldDefaults(level = PUBLIC)
public class HelmTestConstants {
  public static String ACCOUNT_ID = "ACCOUNT_ID";
  public static String APP_ID = "APP_ID";
  public static String MANIFEST_ID = "MANIFEST_ID";
  public static String SERVICE_ID = "SERVICE_ID";

  public static long LONG_TIMEOUT_INTERVAL = 60 * 1000L;
}
