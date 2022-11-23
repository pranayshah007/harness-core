package io.harness.delegate.task.pcf.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.CfDeployCommandResult;
import io.harness.logging.CommandExecutionStatus;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.NonFinal;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfDeployCommandResponseNG implements CfCommandResponseNG{
    @NonFinal
    DelegateMetaInfo delegateMetaInfo;
    @NonFinal
    UnitProgressData unitProgressData;
    CommandExecutionStatus commandExecutionStatus;
    String errorMessage;
    CfDeployCommandResult cfDeployCommandResult;
    @Override
    public void setDelegateMetaInfo(DelegateMetaInfo metaInfo) {
        this.delegateMetaInfo = metaInfo;
    }

    @Override
    public void setCommandUnitsProgress(UnitProgressData unitProgressData) {
        this.unitProgressData = unitProgressData;
    }

}
