/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionInterruptType.ABORT;
import static io.harness.beans.ExecutionInterruptType.END_EXECUTION;
import static io.harness.beans.ExecutionInterruptType.IGNORE;
import static io.harness.beans.ExecutionInterruptType.MARK_SUCCESS;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.RepairActionCode;
import io.harness.eraro.ErrorCode;
import io.harness.exception.FailureType;
import io.harness.exception.HarnessException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;

import software.wings.beans.ExecutionScope;
import software.wings.beans.FailureStrategy;
import software.wings.beans.FailureStrategy.Yaml;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.utils.Utils;

import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;

/**
 * @author rktummala on 10/28/17
 */
@OwnedBy(CDC)
@Singleton
public class FailureStrategyYamlHandler extends BaseYamlHandler<FailureStrategy.Yaml, FailureStrategy> {
  private FailureStrategy toBean(ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    RepairActionCode repairActionCode = Utils.getEnumFromString(RepairActionCode.class, yaml.getRepairActionCode());
    ExecutionScope executionScope = Utils.getEnumFromString(ExecutionScope.class, yaml.getExecutionScope());
    RepairActionCode repairActionCodeAfterRetry =
        Utils.getEnumFromString(RepairActionCode.class, yaml.getRepairActionCodeAfterRetry());
    ExecutionInterruptType actionAfterTimeout =
        Utils.getEnumFromString(ExecutionInterruptType.class, yaml.getActionAfterTimeout());
    Long manualInterventionTimeout = yaml.getManualInterventionTimeout();

    boolean isManualIntervention = RepairActionCode.MANUAL_INTERVENTION.equals(repairActionCode);

    if (isManualIntervention) {
      if (manualInterventionTimeout == null || manualInterventionTimeout < 60000) {
        throw new InvalidArgumentsException("\"manualInterventionTimeout\" should not be less than 1m (60000)");
      }
      List<ExecutionInterruptType> allowedActions = Arrays.asList(ABORT, END_EXECUTION, IGNORE, MARK_SUCCESS, ROLLBACK);
      if (!allowedActions.contains(actionAfterTimeout)) {
        throw new InvalidArgumentsException(String.format(
            "\"actionAfterTimeout\" should not be empty. Please provide valid value: %s", allowedActions));
      }
    }

    if (RepairActionCode.RETRY.equals(repairActionCode)) {
      if (yaml.getRetryCount() <= 0) {
        throw new InvalidArgumentsException("\"retryCount\" should be greater than 0");
      }
    }

    return FailureStrategy.builder()
        .executionScope(executionScope)
        .repairActionCode(repairActionCode)
        .repairActionCodeAfterRetry(repairActionCodeAfterRetry)
        .retryCount(yaml.getRetryCount())
        .retryIntervals(yaml.getRetryIntervals())
        .failureTypes(yaml.getFailureTypes() != null
                ? yaml.getFailureTypes()
                      .stream()
                      .map(failureTypeString -> Utils.getEnumFromString(FailureType.class, failureTypeString))
                      .collect(toList())
                : null)
        .specificSteps(yaml.getSpecificSteps())
        .actionAfterTimeout(isManualIntervention ? actionAfterTimeout : null)
        .manualInterventionTimeout(isManualIntervention ? manualInterventionTimeout : null)
        .build();
  }

  @Override
  public Yaml toYaml(FailureStrategy bean, String appId) {
    List<String> failureTypeList = null;
    if (bean.getFailureTypes() != null) {
      failureTypeList = bean.getFailureTypes().stream().map(Enum::name).collect(toList());
    }
    String repairActionCode = Utils.getStringFromEnum(bean.getRepairActionCode());
    String repairActionCodeAfterRetry = Utils.getStringFromEnum(bean.getRepairActionCodeAfterRetry());
    String executionScope = Utils.getStringFromEnum(bean.getExecutionScope());
    String actionAfterTimeout = Utils.getStringFromEnum(bean.getActionAfterTimeout());

    return FailureStrategy.Yaml.builder()
        .executionScope(executionScope)
        .failureTypes(failureTypeList)
        .repairActionCode(repairActionCode)
        .repairActionCodeAfterRetry(repairActionCodeAfterRetry)
        .retryCount(bean.getRetryCount())
        .retryIntervals(bean.getRetryIntervals())
        .specificSteps(bean.getSpecificSteps())
        .actionAfterTimeout(actionAfterTimeout)
        .manualInterventionTimeout(bean.getManualInterventionTimeout())
        .build();
  }

  @Override
  public FailureStrategy upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext)
      throws HarnessException {
    return toBean(changeContext);
  }

  @Override
  public Class getYamlClass() {
    return FailureStrategy.Yaml.class;
  }

  @Override
  public FailureStrategy get(String accountId, String yamlFilePath) {
    throw new WingsException(ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION);
  }

  @Override
  public void delete(ChangeContext<Yaml> changeContext) {
    // DO nothing
  }
}
