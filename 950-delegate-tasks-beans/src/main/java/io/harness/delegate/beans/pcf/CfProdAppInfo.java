package io.harness.delegate.beans.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfProdAppInfo {
  int runningCount;
  String applicationName;
  String applicationGuid;
  List<String> attachedRoutes;
}
