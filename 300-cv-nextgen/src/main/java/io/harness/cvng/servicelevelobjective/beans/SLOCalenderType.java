package io.harness.cvng.servicelevelobjective.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SLOCalenderType {
  @JsonProperty("Weekly") WEEKLY,
  @JsonProperty("Monthly") MONTHLY,
  @JsonProperty("Quarterly") QUARTERLY
}
