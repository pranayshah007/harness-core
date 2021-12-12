package io.harness.cvng.servicelevelobjective.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceLevelIndicatorDTO {
  String name;
  String identifier;
  @NotNull ServiceLevelIndicatorType type;
  @NotNull ServiceLevelIndicatorSpec spec;
  @NotNull SLIMissingDataType sliMissingDataType;
  @JsonIgnore String healthSourceRef; // TODO: we need to move health source ref to this level.
}
