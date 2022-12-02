package io.harness.delegate.beans.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfSwapRouteCommandResult {
    private List<CfServiceData> instanceDataUpdated;
    private List<CfInternalInstanceElement> cfInstanceElements;
    private CfInBuiltVariablesUpdateValues updatedValues;
}