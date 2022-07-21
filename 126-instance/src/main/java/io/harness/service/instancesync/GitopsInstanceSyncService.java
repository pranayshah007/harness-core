package io.harness.service.instancesync;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.GITOPS)
public interface GitopsInstanceSyncService {
  void processInstanceSync(@NotEmpty String accountIdentifier, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotNull List<InstanceDTO> instanceList);
}
