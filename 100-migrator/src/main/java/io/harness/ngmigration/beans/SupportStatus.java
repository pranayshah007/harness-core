/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDC)
public enum SupportStatus {
  // We can migrate this step over
  SUPPORTED,
  // We cannot migrate this step. Workflow migration will not happen if this step exists
  UNSUPPORTED,
  // There is no equivalent step in NG. We will remove such workflows & proceed
  NO_LONGER_NEEDED,
  // Requires Manual intervention. We will create such steps but will require manual effort by the customers
  MANUAL_EFFORT
}
