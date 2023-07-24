/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HelmCommandResponseNG {
  private CommandExecutionStatus commandExecutionStatus;
  private String output;

  public List<K8sPod> getPreviousK8sPodList() {
    return Collections.emptyList();
  }

  public List<K8sPod> getTotalK8sPodList() {
    return Collections.emptyList();
  }
}
