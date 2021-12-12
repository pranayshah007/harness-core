package io.harness.pms.sdk.core.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
public class StringExpandedValue implements ExpandedValue {
  String key;
  String value;

  @Override
  public String toJson() {
    return value;
  }
}
