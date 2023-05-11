/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.evaluator;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class KubernetesPlaceholderEvaluator {
  private final String[] keys;
  private final String[] values;

  public KubernetesPlaceholderEvaluator(String[] keys, String[] values) {
    this.keys = keys;
    this.values = values;
  }

  public String evaluate(String input) {
    return StringUtils.replaceEach(input, keys, values);
  }

  public List<String> evaluateAll(List<String> inputs) {
    return inputs.stream().map(this::evaluate).collect(Collectors.toCollection(ArrayList::new));
  }

  public static KubernetesPlaceholderEvaluator from(Map<String, String> values) {
    return new KubernetesPlaceholderEvaluator(
        values.keySet().toArray(new String[0]), values.values().toArray(new String[0]));
  }

  public static List<String> evaluateAllStatic(List<String> inputs, Map<String, String> value) {
    if (isEmpty(value)) {
      return inputs;
    }

    try {
      return from(value).evaluateAll(inputs);
    } catch (Exception e) {
      log.warn("Failed to evaluate placeholder values", e);
      return inputs;
    }
  }
}
