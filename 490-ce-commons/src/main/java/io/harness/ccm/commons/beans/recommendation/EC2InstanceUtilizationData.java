package io.harness.ccm.commons.beans.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(CE)
public class EC2InstanceUtilizationData {
  long starttime;
  long endtime;
  double maxcpu;
  double maxmemory;
  double avgcpu;
  double avgmemory;
}
