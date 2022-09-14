package io.harness.delegate.ecs;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsBlueGreenSwapTargetGroupsRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.LogCallback;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBlueGreenSwapTargetGroupsCommandTaskHandler extends EcsCommandTaskNGHandler {
    @Inject private EcsTaskHelperBase ecsTaskHelperBase;
    @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
    @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
    private EcsInfraConfig ecsInfraConfig;
    private long timeoutInMillis;

    @Override
    protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest, ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
        if (!(ecsCommandRequest instanceof EcsBlueGreenSwapTargetGroupsRequest)) {
            throw new InvalidArgumentsException(Pair.of("ecsCommandRequest", "Must be instance of EcsBlueGreenSwapTargetGroupsRequest"));
        }
        EcsBlueGreenSwapTargetGroupsRequest ecsBlueGreenSwapTargetGroupsRequest = (EcsBlueGreenSwapTargetGroupsRequest)
                ecsCommandRequest;

        timeoutInMillis = ecsBlueGreenSwapTargetGroupsRequest.getTimeoutIntervalInMin() * 60000;
        ecsInfraConfig = ecsBlueGreenSwapTargetGroupsRequest.getEcsInfraConfig();

        LogCallback swapTargetGroupLogCallback = ecsTaskHelperBase.getLogCallback(
                iLogStreamingTaskClient, EcsCommandUnitConstants.swapTargetGroup.toString(), true, commandUnitsProgress);

        try{

        }
        catch(Exception e) {

        }
    }
}
