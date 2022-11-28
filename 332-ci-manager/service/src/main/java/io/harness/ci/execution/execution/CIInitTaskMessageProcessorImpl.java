package io.harness.ci.execution;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

@Slf4j
public class CIInitTaskMessageProcessorImpl implements CIInitTaskMessageProcessor{
    @Inject InitializeTaskStepV2 initializeTaskStepV2;
    @Inject CIBuildEnforcer buildEnforcer;
    @Inject @Named("ciInitTaskExecutor") private ExecutorService initTaskExecutor;

    @Override
    public Boolean processMessage(DequeueResponse dequeueResponse) {
        try {
            byte[] payload = dequeueResponse.getPayload();
            CIExecutionArgs ciExecutionArgs = RecastOrchestrationUtils.fromBytes(payload, CIExecutionArgs.class);
            Ambiance ambiance = ciExecutionArgs.getAmbiance();
            if (buildEnforcer.checkBuildEnforcement(AmbianceUtils.getAccountId(ambiance))) {
                log.info(String.format("skipping execution for account id: %s because of concurrency enforcement failure", AmbianceUtils.getAccountId(ambiance)));
                return false;
            }
            initTaskExecutor.submit(() -> initializeTaskStepV2.executeBuild(ambiance, ciExecutionArgs.getStepElementParameters(), ciExecutionArgs.getCallbackId()))
            return true;
        } catch (Exception ex) {
            log.info("ci init task processing failed", ex);
            return false;
        }
    }
}
