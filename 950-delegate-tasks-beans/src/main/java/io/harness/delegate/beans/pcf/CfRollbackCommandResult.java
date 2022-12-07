package io.harness.delegate.beans.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfRollbackCommandResult {
  private List<CfServiceData> instanceDataUpdated;
  private List<CfInternalInstanceElement> cfInstanceElements;
  private CfInBuiltVariablesUpdateValues updatedValues;
}