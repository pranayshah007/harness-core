package io.harness.cvng.servicelevelobjective.beans.slimetricspec;

import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class ThresholdSLIMetricSpec extends SLIMetricSpec {
  @NotNull String metric1;
  @NotNull Double thresholdValue;
  @NotNull ThresholdType thresholdType;

  @Override
  public SLIMetricType getType() {
    return SLIMetricType.THRESHOLD;
  }

  @Override
  public String getMetricName() {
    return metric1;
  }
}
