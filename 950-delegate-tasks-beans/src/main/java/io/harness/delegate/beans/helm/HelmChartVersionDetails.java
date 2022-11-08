package io.harness.delegate.beans.helm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
public class HelmChartVersionDetails {
  String version;
  String appVersion;
  String description;
}
