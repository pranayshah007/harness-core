/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.schema.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
@OwnedBy(DX)
public enum SupportedPossibleFieldTypes {
  string,
  number,
  integer,
  bool,
  list,
  map,
  expression,
  runtime, // to support runtime field type, like <+input>
  /**
   * Only used for setting default.
   */
  none
}
