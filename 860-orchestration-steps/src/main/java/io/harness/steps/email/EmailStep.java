package io.harness.steps.email;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.email.EmailStepResponse;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EmailStep extends TaskExecutableWithRollback<EmailStepResponse> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.EMAIL).setStepCategory(StepCategory.STEP).build();

  @Inject private KryoSerializer kryoSerializer;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public TaskRequest obtainTask(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return null;
  }

  @Override
  public StepResponse handleTaskResult(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<EmailStepResponse> responseDataSupplier) throws Exception {
    return null;
  }

  private NGLogCallback getNGLogCallback(LogStreamingStepClientFactory logStreamingStepClientFactory, Ambiance ambiance,
      String logFix, boolean openStream) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, logFix, openStream);
  }
}
