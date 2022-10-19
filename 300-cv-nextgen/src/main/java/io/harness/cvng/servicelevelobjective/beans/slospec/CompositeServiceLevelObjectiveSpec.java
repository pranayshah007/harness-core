package io.harness.cvng.servicelevelobjective.beans.slospec;

import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompositeServiceLevelObjectiveSpec extends ServiceLevelObjectiveSpec {
  @Size(min = 2, max = 20) @Valid @NotNull List<ServiceLevelObjectiveDetailsDTO> serviceLevelObjectivesDetails;

  @Override
  public ServiceLevelObjectiveType getType() {
    return ServiceLevelObjectiveType.COMPOSITE;
  }
}
