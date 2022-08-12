/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.releasehistory;

import java.util.Map;

public class ReleaseConstants {
  public static final String RELEASE_KEY = "release";
  public static final String RELEASE_NAME_DELIMITER = ".";
  public static final String SECRET_LABEL_DELIMITER = ",";
  public static final String RELEASE_NUMBER_LABEL_KEY = "release-number";
  public static final String RELEASE_OWNER_LABEL_KEY = "owner";
  public static final String RELEASE_OWNER_LABEL_VALUE = "harness";
  public static final String RELEASE_STATUS_LABEL_KEY = "status";
  public static final String RELEASE_SECRET_TYPE_KEY = "type";
  public static final String RELEASE_SECRET_TYPE_VALUE = "harness.io/release/v2";
  public static final Map<String, String> RELEASE_HARNESS_SECRET_TYPE =
      Map.of(RELEASE_SECRET_TYPE_KEY, RELEASE_SECRET_TYPE_VALUE);
  public static final int RELEASE_HISTORY_LIMIT = 5;
}
