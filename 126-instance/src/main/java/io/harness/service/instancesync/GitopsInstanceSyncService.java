/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesync;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@OwnedBy(HarnessTeam.GITOPS)
public interface GitopsInstanceSyncService {
  void processInstanceSync(@NotEmpty String accountIdentifier, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String agentIdentifier, @NotNull List<InstanceDTO> instanceList);

    void deleteInstancesForAgent(String accountId, String orgIdentifier, String projectIdentifier, String agentIdentifier);
}
