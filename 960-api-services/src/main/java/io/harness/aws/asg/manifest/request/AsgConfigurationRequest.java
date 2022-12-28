package io.harness.aws.asg.manifest.request;

import io.harness.manifest.handler.ManifestRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class AsgConfigurationRequest extends ManifestRequest {
  public boolean useAlreadyRunningInstances;
  @Builder
  public AsgConfigurationRequest(List<String> manifests, boolean useAlreadyRunningInstances) {
    super(manifests);
    this.useAlreadyRunningInstances = useAlreadyRunningInstances;
  }
}
