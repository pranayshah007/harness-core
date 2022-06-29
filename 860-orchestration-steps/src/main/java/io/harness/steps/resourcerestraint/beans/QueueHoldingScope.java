/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.steps.resourcerestraint.ResourceRestraintConstants.YAML_NAME_PIPELINE;
import static io.harness.steps.resourcerestraint.ResourceRestraintConstants.YAML_NAME_STAGE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import lombok.Getter;

/**
 * Follow the same principles from {@link HoldingScope}, but used to hide the {@link HoldingScope#PLAN} from the yaml
 * schema because it is deprecated and should be hidden from the user.
 */
@OwnedBy(CDC)
@RecasterAlias("io.harness.steps.resourcerestraint.beans.QueueHoldingScope")
public enum QueueHoldingScope {
  @JsonProperty(YAML_NAME_PIPELINE) PIPELINE(YAML_NAME_PIPELINE, HoldingScope.PIPELINE),
  @JsonProperty(YAML_NAME_STAGE) STAGE(YAML_NAME_STAGE, HoldingScope.STAGE);

  /** The name to show in yaml file */
  @Getter private final String yamlName;

  @Getter @JsonIgnore private final HoldingScope holdingScope;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static QueueHoldingScope getQueueHoldingScope(@JsonProperty("scope") String yamlName) {
    return Arrays.stream(QueueHoldingScope.values())
        .filter(hs -> hs.yamlName.equalsIgnoreCase(yamlName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid value: " + yamlName));
  }

  QueueHoldingScope(String yamlName, HoldingScope holdingScope) {
    this.yamlName = yamlName;
    this.holdingScope = holdingScope;
  }
}
