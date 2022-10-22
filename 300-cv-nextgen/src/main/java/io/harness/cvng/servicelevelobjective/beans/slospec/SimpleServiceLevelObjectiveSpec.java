package io.harness.cvng.servicelevelobjective.beans.slospec;

import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
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
public class SimpleServiceLevelObjectiveSpec extends ServiceLevelObjectiveSpec {
  @ApiModelProperty(required = true) @NotNull String monitoredServiceRef;
  @ApiModelProperty(required = true) @NotNull String healthSourceRef;
  @Valid @NotNull ServiceLevelIndicatorType serviceLevelIndicatorType;
  @Valid @NotNull List<ServiceLevelIndicatorDTO> serviceLevelIndicators;

  @Override
  public ServiceLevelObjectiveType getType() {
    return ServiceLevelObjectiveType.SIMPLE;
  }
}
