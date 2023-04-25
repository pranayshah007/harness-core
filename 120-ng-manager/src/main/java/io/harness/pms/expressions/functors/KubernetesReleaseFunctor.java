/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.k8s.KubernetesPlaceholder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
public class KubernetesReleaseFunctor implements SdkFunctor {
  @VisibleForTesting
  static final Set<String> SUPPORTED_STEP_TYPES = ImmutableSet.of(StepSpecTypeConstants.K8S_ROLLING_DEPLOY,
      StepSpecTypeConstants.K8S_BLUE_GREEN_DEPLOY, StepSpecTypeConstants.K8S_APPLY,
      StepSpecTypeConstants.K8S_BG_SWAP_SERVICES, StepSpecTypeConstants.K8S_CANARY_DEPLOY,
      StepSpecTypeConstants.K8S_DELETE, StepSpecTypeConstants.K8S_DRY_RUN_MANIFEST);
  private static final Map<String, String> VALUES =
      Arrays.stream(Field.values()).collect(Collectors.toUnmodifiableMap(Field::getKey, Field::getValue));
  private static final int EXPECTED_ARGS_COUNT = 1;

  public static final String KUBERNETES_RELEASE_FUNCTOR_NAME = "kubernetes.release";

  @Override
  public Object get(Ambiance ambiance, String... args) {
    StepType stepType = AmbianceUtils.getCurrentStepType(ambiance);

    if (stepType == null) {
      throw new InvalidRequestException(
          format("Expression <+%s> cannot be use outside of Execution Steps", KUBERNETES_RELEASE_FUNCTOR_NAME));
    }

    if (!SUPPORTED_STEP_TYPES.contains(stepType.getType())) {
      throw new InvalidRequestException(
          format("Expression <+%s> is not yet supported for the step type [%s]. Supported step types are [%s]",
              KUBERNETES_RELEASE_FUNCTOR_NAME, stepType.getType(), String.join(", ", SUPPORTED_STEP_TYPES)));
    }

    if (args.length != EXPECTED_ARGS_COUNT) {
      throw new InvalidArgumentsException(
          Pair.of("args", format("Unexpected arguments count. Expected %d, got %d", EXPECTED_ARGS_COUNT, args.length)));
    }

    return resolve(args);
  }

  private Object resolve(String[] args) {
    String field = args[0];
    if (!VALUES.containsKey(field)) {
      throw new InvalidArgumentsException(Pair.of(
          "field", format("Field [%s] doesn't exist for <+%s> expression", field, KUBERNETES_RELEASE_FUNCTOR_NAME)));
    }

    return VALUES.get(field);
  }

  enum Field {
    REVISION("revision", KubernetesPlaceholder.REVISION_NUMBER.getPlaceholder()),
    STAGE_COLOR("stageColor", KubernetesPlaceholder.RELEASE_COLOR.getPlaceholder());

    private final String key;
    private final String value;

    Field(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public String getKey() {
      return this.key;
    }

    public String getValue() {
      return this.value;
    }
  }
}
