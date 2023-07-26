/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.expressions.functors.YamlEvaluatorFunctor;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;

/**
  This YamlExpressionEvaluator is used for resolving the expression in the pipeline yaml.
 *
 */

@OwnedBy(HarnessTeam.PIPELINE)
public class YamlExpressionEvaluator extends EngineExpressionEvaluator {
  // inputs in json format.
  private final String yaml;
  public YamlExpressionEvaluator(String yaml) {
    super(null);
    this.yaml = yaml;
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext("yaml", new YamlEvaluatorFunctor(yaml));
  }

  @Override
  @NotEmpty
  protected List<String> fetchPrefixes() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    return listBuilder.add("yaml").build();
  }
}
