package io.harness.aws.asg.manifest.request;

import io.harness.manifest.handler.ManifestRequest;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
public class AsgLaunchTemplateRequest extends ManifestRequest {
  @Builder
  public AsgLaunchTemplateRequest(List<String> manifests) {
    super(manifests);
  }
}
