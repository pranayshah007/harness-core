package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.cvng.CVConstants.SLO_TARGET_TYPE;

import io.harness.cvng.servicelevelobjective.beans.slotargetspec.SLOTargetSpec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SLOTargetFilterDTO {
  @JsonProperty(SLO_TARGET_TYPE) SLOTargetType type;
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = SLO_TARGET_TYPE, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      visible = true)
  @Valid
  @NotNull
  SLOTargetSpec spec;

  public SLOTargetType getType() {
    return this.spec.getType();
  }
}
