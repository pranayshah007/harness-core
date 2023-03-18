package io.harness.advisers.prb;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.advisers.CommonAdviserTypes;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class OnFailPipelineRollbackAdviser implements Adviser {
  @Inject private KryoSerializer kryoSerializer;
  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(CommonAdviserTypes.ON_FAIL_PIPELINE_ROLLBACK.name()).build();

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    OnFailPipelineRollbackParameters parameters = extractParameters(advisingEvent);
    String pipelineRollbackStageId = parameters.getPipelineRollbackStageId();
    // todo: implement
    return null;
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    if (!StatusUtils.brokeStatuses().contains(advisingEvent.getToStatus())) {
      return false;
    }
    OnFailPipelineRollbackParameters parameters = extractParameters(advisingEvent);
    List<FailureType> failureTypesList = getAllFailureTypes(advisingEvent);
    return isEmpty(failureTypesList) || !Collections.disjoint(parameters.getApplicableFailureTypes(), failureTypesList);
  }

  @NotNull
  private OnFailPipelineRollbackParameters extractParameters(AdvisingEvent advisingEvent) {
    return (OnFailPipelineRollbackParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
