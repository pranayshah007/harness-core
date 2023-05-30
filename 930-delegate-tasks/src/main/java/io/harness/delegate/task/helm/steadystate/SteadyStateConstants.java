/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm.steadystate;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public final class SteadyStateConstants {
  public static final String STATEFULSET_KIND = "StatefulSet";
  public static final String STATEFULSET_UPDATE_STRATEGY_PATH = "spec.updateStrategy.type";
  public static final String ON_DELETE_STRATEGY = "OnDelete";
}
