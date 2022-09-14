package io.harness.delegate.task.ecs.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.ecs.EcsBlueGreenCreateServiceResult;
import io.harness.delegate.beans.ecs.EcsBlueGreenSwapTargetGroupsResult;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.logging.CommandExecutionStatus;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenSwapTargetGroupsResponse  implements EcsCommandResponse {
    @NonFinal
    DelegateMetaInfo delegateMetaInfo;
    @NonFinal
    UnitProgressData unitProgressData;
    CommandExecutionStatus commandExecutionStatus;
    String errorMessage;
    EcsBlueGreenSwapTargetGroupsResult ecsBlueGreenSwapTargetGroupsResult;

    @Override
    public void setDelegateMetaInfo(DelegateMetaInfo metaInfo) {
        this.delegateMetaInfo = metaInfo;
    }

    @Override
    public void setCommandUnitsProgress(UnitProgressData unitProgressData) {
        this.unitProgressData = unitProgressData;
    }
}
