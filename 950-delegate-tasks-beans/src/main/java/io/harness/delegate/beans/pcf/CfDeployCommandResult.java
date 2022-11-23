package io.harness.delegate.beans.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfDeployCommandResult {
    /**
     * This list represents apps updated by deploy state,
     * AppName : previousCount : DesiredCount (one updated by deploy)
     * Rollback will use this data but will reverse counts
     */
    private List<CfServiceData> instanceDataUpdated;
    private List<CfInternalInstanceElement> cfInstanceElements;
    private CfInBuiltVariablesUpdateValues updatedValues;
}
