package io.harness.steps.plugin;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public interface ContainerCommandUnitConstants {
  String InitContainer = "Initialize";
  String ContainerStep = "Container Step";
  String CleanContainer = "Cleanup";
}
