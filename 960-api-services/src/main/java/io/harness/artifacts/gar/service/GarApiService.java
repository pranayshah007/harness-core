package io.harness.artifacts.gar.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.gar.beans.GarInternalConfig;

import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public interface GarApiService {
  List<BuildDetailsInternal> getBuilds(GarInternalConfig garinternalConfig, String versionRegex, int maxNumberOfBuilds);
  BuildDetailsInternal getLastSuccessfulBuildFromRegex(GarInternalConfig garinternalConfig, String versionRegex);

  BuildDetailsInternal verifyBuildNumber(GarInternalConfig garInternalConfig, String version);
}
