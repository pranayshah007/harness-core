/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.enforcement;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;

@OwnedBy(HarnessTeam.CI)
public interface CIBuildEnforcer {
  default boolean shouldQueue(String accountID, Infrastructure infrastructure) {
    return false;
  }
  default boolean shouldRun(String accountID, Infrastructure infrastructure) {
    return true;
  }
}
