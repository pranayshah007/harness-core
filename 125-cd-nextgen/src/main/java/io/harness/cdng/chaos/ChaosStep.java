package io.harness.cdng.chaos;

import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.chaos.client.beans.ChaosQuery;
import io.harness.chaos.client.beans.ChaosRerunResponse;
import io.harness.chaos.client.remote.ChaosHttpClient;
import io.harness.eraro.Level;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.remote.client.NGRestUtils;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChaosStep implements AsyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.CHAOS_STEP).setStepCategory(StepCategory.STEP).build();

  @Inject private ChaosHttpClient client;

  private static final String BODY =
      "mutation{reRunChaosWorkFlow(workflowID: \"%s\",identifiers:{orgIdentifier: \"%s\",projectIdentifier: \"%s\",accountIdentifier: \"%s\"}){notifyID}}";

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

  @SneakyThrows
  private String triggerWorkflow(Ambiance ambiance, ChaosStepParameters params) {
    try {
      ChaosRerunResponse response =
          NGRestUtils.getResponse(client.reRunWorkflow(buildPayload(ambiance, params.getExperimentRef())));
      if (response != null && response.isSuccessful()) {
        return response.getNotifyId();
      }
      throw new ChaosRerunException("Error talking ot chaos service");
    } catch (Exception ex) {
      log.error("Unable to trigger chaos experiment", ex);
      throw ex;
    }
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    ChaosStepParameters params = (ChaosStepParameters) stepParameters.getSpec();
    ChaosStepNotifyData data = (ChaosStepNotifyData) responseDataMap.values().iterator().next();
    StepResponseBuilder responseBuilder =
        StepResponse.builder().stepOutcome(StepOutcome.builder().outcome(data).name("output").build());

    if (!data.isSuccess()) {
      return responseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .addFailureData(FailureData.newBuilder()
                                               .setLevel(Level.ERROR.name())
                                               .setCode(GENERAL_ERROR.name())
                                               .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                               .setMessage("Experiment did not finish Successfully")
                                               .build())
                           .build())
          .build();
    }

    if (params.getExpectedResilienceScore() > data.getResiliencyScore()) {
      return responseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .addFailureData(FailureData.newBuilder()
                                               .setLevel(Level.ERROR.name())
                                               .setCode(GENERAL_ERROR.name())
                                               .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                               .setMessage(String.format(
                                                   "Score %2.2f is less than expected Resiliency Score of %2.2f",
                                                   data.getResiliencyScore(), params.getExpectedResilienceScore()))
                                               .build())
                           .build())
          .build();
    }

    return responseBuilder.status(Status.SUCCEEDED).build();
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {
    log.info("Abort Called for chaos Step");
  }

  private ChaosQuery buildPayload(Ambiance ambiance, String experimentRef) {
    String query = String.format(BODY, experimentRef, AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance), AmbianceUtils.getAccountId(ambiance));
    return ChaosQuery.builder().query(query).build();
  }
}
