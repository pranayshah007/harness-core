package io.harness.cvng.servicelevelobjective.beans;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MSDropdownResponse {
  @NotNull String identifier;
  @NotNull String name;
  String serviceRef;
  String environmentRef;
}