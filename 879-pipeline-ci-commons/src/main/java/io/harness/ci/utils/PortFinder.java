/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;

@Builder
@OwnedBy(HarnessTeam.CI)
public class PortFinder {
  @NotNull private Set<Integer> usedPorts;
  @NotNull private Integer startingPort;

  public Integer getNextPort() {
    while (usedPorts.contains(startingPort)) {
      startingPort++;
    }
    return startingPort++;
  }

  public Set<Integer> getUsedPorts() {
    return this.usedPorts;
  }
}
