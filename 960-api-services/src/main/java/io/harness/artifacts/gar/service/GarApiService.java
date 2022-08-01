package io.harness.artifacts.gar.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.gar.beans.GarInternalConfig;

import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public interface GarApiService {
  int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  List<BuildDetailsInternal> getBuilds(GarInternalConfig garinternalConfig, int maxNumberOfBuilds);
}
