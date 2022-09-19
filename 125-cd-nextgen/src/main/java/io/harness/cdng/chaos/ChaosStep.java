package io.harness.cdng.chaos;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.chaos.client.remote.ChaosHttpClient;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;

public class ChaosStep implements AsyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.CHAOS_STEP).setStepCategory(StepCategory.STEP).build();

  @Inject private ChaosHttpClient client;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    String uuid = generateUuid();
    ChaosStepParameters parameters = (ChaosStepParameters) stepParameters.getSpec();
    // TODO: Trigger chaos using the client
    return AsyncExecutableResponse.newBuilder().addCallbackIds(uuid).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // TODO: On receiving chaos hook trigger this
    return null;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {
    // TODO : Call chaos client abort hook
  }
}
