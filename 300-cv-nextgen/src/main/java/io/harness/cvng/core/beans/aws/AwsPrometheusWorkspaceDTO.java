package io.harness.cvng.core.beans.aws;

import lombok.Value;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Value
public class AwsPrometheusWorkspaceDTO {
  String name;
  String workspaceId;
}