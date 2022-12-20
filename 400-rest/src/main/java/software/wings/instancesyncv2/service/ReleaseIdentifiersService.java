package software.wings.instancesyncv2.service;

import software.wings.api.DeploymentInfo;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;

import java.util.Set;

public interface ReleaseIdentifiersService {
  Set<CgReleaseIdentifiers> mergeReleaseIdentifiers(
      Set<CgReleaseIdentifiers> releaseIdentifiers, Set<CgReleaseIdentifiers> buildReleaseIdentifiers);
  Set<CgReleaseIdentifiers> buildReleaseIdentifiers(DeploymentInfo deploymentInfo);
}
