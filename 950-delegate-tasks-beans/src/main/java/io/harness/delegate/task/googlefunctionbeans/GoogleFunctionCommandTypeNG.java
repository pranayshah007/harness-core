/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunctionbeans;

public enum GoogleFunctionCommandTypeNG {
  GOOGLE_FUNCTION_DEPLOY,
  GOOGLE_FUNCTION_PREPARE_ROLLBACK,
  GOOGLE_FUNCTION_WITHOUT_TRAFFIC_DEPLOY,
  GOOGLE_FUNCTION_TRAFFIC_SHIFT,
  GOOGLE_FUNCTION_ROLLBACK,
  GOOGLE_FUNCTION_GEN_ONE_DEPLOY,
  GOOGLE_FUNCTION_GEN_ONE_PREPARE_ROLLBACK,
  GOOGLE_FUNCTION_GEN_ONE_ROLLBACK
}
