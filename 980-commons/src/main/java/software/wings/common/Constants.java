/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

/**
 * Common constants across application.
 */
// TODO: Do not use one centralized place for different semantics. Instead define your constants in the context they
//       make sense. When the proper layering is in place well located constant will be acceptable everywhere it is
//       needed.
@Deprecated
public interface Constants {
  /**
   * The constant KUBERNETES_SWAP_SERVICE_SELECTORS.
   */
  String KUBERNETES_SWAP_SERVICE_SELECTORS = "Swap Service Selectors";

  /**
   * The constant HARNESS_NAME.
   */
  String HARNESS_NAME = "Harness";

  String WINDOWS_HOME_DIR = "%USERPROFILE%";

  /**
   * Quartz job detail key names
   */
  String ACCOUNT_ID_KEY = "accountId";
  String APP_ID_KEY = "appId";
}
