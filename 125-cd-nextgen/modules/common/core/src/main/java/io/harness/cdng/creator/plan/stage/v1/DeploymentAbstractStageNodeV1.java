/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.v1;

import io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public abstract class DeploymentAbstractStageNodeV1 extends AbstractStageNodeV1 {
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  @NotNull
  ParameterField<List<FailureConfigV1>> failure;
  @JsonProperty("skip_instances") ParameterField<Boolean> skipInstances;
}
