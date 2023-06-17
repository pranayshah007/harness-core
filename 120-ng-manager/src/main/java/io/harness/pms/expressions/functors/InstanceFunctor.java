/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.instance.outcome.InstanceOutcome;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.steps.OutputExpressionConstants;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class InstanceFunctor implements SdkFunctor {
  public static final String INSTANCE = "instance";

  private static final String INSTANCE_NAME_PROPERTY = "name";
  private static final String INSTANCE_HOST_NAME_PROPERTY = "hostName";
  private static final String INSTANCE_HOST_PROPERTY = "host";

  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    log.info("Resolving instance properties with args: {}", Arrays.asList(args));
    List<InstanceOutcome> instances = getInstancesFromSweepingOutput(ambiance);
    String hostName = getRepeatStrategyItem(ambiance);
    InstanceOutcome instance = findInstanceByHostNameOrThrow(instances, hostName);

    if (args.length == 0 || isEmpty(args[0])) {
      return instance;
    }
    String instanceProperty = args[0];

    if (INSTANCE_NAME_PROPERTY.equals(instanceProperty)) {
      return instance.getName();
    } else if (INSTANCE_HOST_NAME_PROPERTY.equals(instanceProperty)) {
      return instance.getHostName();
    } else if (INSTANCE_HOST_PROPERTY.equals(instanceProperty)) {
      return instance.getHost();
    }

    throw new InvalidArgumentsException(format("Unsupported instance property, property: %s", instanceProperty));
  }

  private List<InstanceOutcome> getInstancesFromSweepingOutput(Ambiance ambiance) {
    OptionalSweepingOutput instancesOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.INSTANCES));

    if (!instancesOutput.isFound()) {
      throw new InvalidRequestException("Unable to read instances output");
    }
    return ((InstancesOutcome) instancesOutput.getOutput()).getInstances();
  }

  private String getRepeatStrategyItem(Ambiance ambiance) {
    List<Level> stepLevelsWithStrategyMetadata =
        ambiance.getLevelsList()
            .stream()
            .filter(level -> level.hasStrategyMetadata() && level.hasStepType())
            .collect(Collectors.toList());

    Map<String, Object> strategyObjectMap = StrategyUtils.fetchStrategyObjectMap(
        stepLevelsWithStrategyMetadata, AmbianceUtils.shouldUseMatrixFieldName(ambiance));
    if (strategyObjectMap == null) {
      throw new InvalidRequestException("Not found step level strategy");
    }

    Object repeatStrategy = strategyObjectMap.get("repeat");
    if (!(repeatStrategy instanceof HashMap)) {
      throw new InvalidRequestException("Not found step level repeat strategy");
    }

    Object repeatStrategyItem = ((HashMap<String, Object>) repeatStrategy).get("item");
    if (!(repeatStrategyItem instanceof String)) {
      throw new InvalidRequestException("Not found step level repeat strategy item");
    }

    return (String) repeatStrategyItem;
  }

  private InstanceOutcome findInstanceByHostNameOrThrow(
      List<InstanceOutcome> instances, @NotNull final String hostName) {
    return instances.stream()
        .filter(instanceOutcome -> hostName.equals(instanceOutcome.getHostName()))
        .findFirst()
        .orElseThrow(() -> new InvalidRequestException(format("Not found instance by host name, %s", hostName)));
  }
}
