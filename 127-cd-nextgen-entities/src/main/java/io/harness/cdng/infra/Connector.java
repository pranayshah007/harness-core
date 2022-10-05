package io.harness.cdng.infra;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDC)
public class Connector {
  String name;
}
