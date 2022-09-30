package io.harness.cvng.servicelevelobjective.beans;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceLevelObjectiveDetailsDTO {
  String serviceLevelObjectiveRef;
  Double weightagePercentage;
}
