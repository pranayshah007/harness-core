package io.harness.cdng.chaos;

import io.harness.chaos.client.remote.ChaosHttpClient;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChaosStep implements AsyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.CHAOS_STEP).setStepCategory(StepCategory.STEP).build();

  @Inject private ChaosHttpClient client;

  private static final String BODY =
      "{\"query\":\"mutation{ reRunChaosWorkFlow(workflowID: \"%s\", identifiers: { orgIdentifier: \"%s\", accountIdentifier: \"%s\", projectIdentifier: \"%s\" }) }\"";

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ChaosStepParameters params = (ChaosStepParameters) stepParameters.getSpec();
    String callbackId = triggerWorkflow(ambiance, params);
    log.info("Triggered chaos experiment with ref: {}, workflowRunId: {}", params.getExperimentRef(), callbackId);
    return AsyncExecutableResponse.newBuilder().addCallbackIds(callbackId).build();
  }

  private String triggerWorkflow(Ambiance ambiance, ChaosStepParameters params) {
    try {
      return NGRestUtils.getResponse(client.reRunWorkflow(buildPayload(ambiance, params.getExperimentRef())));
    } catch (Exception ex) {
      log.error("Unable to trigger chaos experiment", ex);
      throw ex;
    }
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    ChaosStepNotifyData data = (ChaosStepNotifyData) responseDataMap.values().iterator().next();
    Status status = data.isFailed() ? Status.FAILED : Status.SUCCEEDED;
    return StepResponse.builder().status(status).build();
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {
    // TODO : Call chaos client abort hook
  }

  private String buildPayload(Ambiance ambiance, String experimentRef) {
    return String.format(BODY, experimentRef, AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
  }
}
