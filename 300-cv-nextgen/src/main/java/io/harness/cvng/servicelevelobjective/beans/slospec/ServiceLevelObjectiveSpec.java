package io.harness.cvng.servicelevelobjective.beans.slospec;

import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SimpleServiceLevelObjectiveSpec.class, name = "Simple")
  , @JsonSubTypes.Type(value = CompositeServiceLevelObjectiveSpec.class, name = "Composite"),
})
public abstract class ServiceLevelObjectiveSpec {
  @JsonIgnore public abstract ServiceLevelObjectiveType getType();
}
