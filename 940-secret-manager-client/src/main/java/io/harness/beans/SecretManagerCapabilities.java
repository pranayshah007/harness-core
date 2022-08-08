/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

public enum SecretManagerCapabilities {
  CREATE_INLINE_SECRET,
  CREATE_REFERENCE_SECRET,
  CREATE_PARAMETERIZED_SECRET,
  CREATE_FILE_SECRET,
  TRANSITION_SECRET_TO_SM,
  TRANSITION_SECRET_FROM_SM,
  CAN_BE_DEFAULT_SM;
}
