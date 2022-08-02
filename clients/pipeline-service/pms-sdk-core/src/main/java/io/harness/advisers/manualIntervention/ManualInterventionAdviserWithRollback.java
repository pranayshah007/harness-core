/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.manualIntervention;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;

import io.harness.advisers.CommonAdviserTypes;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.InterventionWaitAdvise;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.core.failurestrategy.NGFailureActionTypeConstants;

import com.google.inject.Inject;
import com.google.protobuf.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class ManualInterventionAdviserWithRollback implements Adviser {
  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(CommonAdviserTypes.MANUAL_INTERVENTION_WITH_ROLLBACK.name()).build();

  @Inject private KryoSerializer kryoSerializer;

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    ManualInterventionAdviserRollbackParameters parameters = extractParameters(advisingEvent);
    Duration timeout = Duration.newBuilder().setSeconds(java.time.Duration.ofDays(1).toMinutes() * 60).build();
    if (parameters != null && parameters.getTimeout() != null) {
      timeout = Duration.newBuilder().setSeconds(parameters.getTimeout()).build();
    }
    RepairActionCode repairActionCode = parameters == null ? null : parameters.getTimeoutAction();
    return AdviserResponse.newBuilder()
        .setInterventionWaitAdvise(
            InterventionWaitAdvise.newBuilder()
                .setTimeout(timeout)
                .setRepairActionCode(
                    repairActionCode == null ? RepairActionCode.UNKNOWN : getReformedRepairActionCode(repairActionCode))
                .putAllMetadata(getRollbackMetadataMap(repairActionCode))
                .setFromStatus(advisingEvent.getToStatus())
                .build())
        .setType(AdviseType.INTERVENTION_WAIT)
        .build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    if (advisingEvent.isPreviousAdviserExpired() && advisingEvent.getFromStatus() == INTERVENTION_WAITING) {
      return false;
    }
    boolean canAdvise = StatusUtils.brokeStatuses().contains(advisingEvent.getToStatus())
        && advisingEvent.getFromStatus() != INTERVENTION_WAITING;
    ManualInterventionAdviserRollbackParameters parameters = extractParameters(advisingEvent);
    List<FailureType> failureTypesList = getAllFailureTypes(advisingEvent);
    if (parameters != null && !isEmpty(failureTypesList)) {
      return canAdvise && !Collections.disjoint(parameters.getApplicableFailureTypes(), failureTypesList);
    }
    return canAdvise;
  }

  private ManualInterventionAdviserRollbackParameters extractParameters(AdvisingEvent advisingEvent) {
    byte[] adviserParameters = advisingEvent.getAdviserParameters();
    if (isEmpty(adviserParameters)) {
      return null;
    }
    return (ManualInterventionAdviserRollbackParameters) kryoSerializer.asObject(adviserParameters);
  }

  private RepairActionCode getReformedRepairActionCode(RepairActionCode repairActionCode) {
    switch (repairActionCode) {
      case STAGE_ROLLBACK:
      case STEP_GROUP_ROLLBACK:
        return RepairActionCode.CUSTOM_FAILURE;
      default:
        return repairActionCode;
    }
  }

  private String getRollbackStrategy(RepairActionCode repairActionCode) {
    switch (repairActionCode) {
      case STEP_GROUP_ROLLBACK:
        return NGFailureActionTypeConstants.STEP_GROUP_ROLLBACK;
      case STAGE_ROLLBACK:
        return NGFailureActionTypeConstants.STAGE_ROLLBACK;
      default:
        return "";
    }
  }

  private Map<String, String> getRollbackMetadataMap(RepairActionCode repairActionCode) {
    String rollbackStrategy = getRollbackStrategy(repairActionCode);
    if (EmptyPredicate.isNotEmpty(rollbackStrategy)) {
      return Collections.singletonMap("ROLLBACK", rollbackStrategy);
    }
    return Collections.emptyMap();
  }
}
