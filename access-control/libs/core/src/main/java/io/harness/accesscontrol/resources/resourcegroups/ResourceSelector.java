package io.harness.accesscontrol.resources.resourcegroups;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "ResourceSelectorKeys")
@OwnedBy(HarnessTeam.PL)
public class ResourceSelector {
  @NotEmpty String selector;
  boolean conditional;
  String condition;
}
