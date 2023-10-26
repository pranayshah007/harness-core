/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDC)
public interface FreezeEventTypeConstants {
  String FREEZE_WINDOW_ENABLED = "FreezeWindowEnabled"; // If freeze window is enabled and active
  String ON_ENABLE_FREEZE_WINDOW = "OnEnableFreezeWindow"; // On enabling of the freeze window
  String DEPLOYMENT_REJECTED_DUE_TO_FREEZE = "DeploymentRejectedDueToFreeze";
}
