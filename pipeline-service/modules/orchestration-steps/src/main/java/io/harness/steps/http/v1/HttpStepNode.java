/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.http.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.http.HttpHeaderConfig;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1.StepElementParametersV1Builder;
import io.harness.plancreator.steps.common.v1.StepParametersUtilsV1;
import io.harness.plancreator.steps.internal.v1.PmsAbstractStepNodeV1;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.StepUtils;
import io.harness.yaml.utils.v1.NGVariablesUtilsV1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import java.util.stream.Collectors;
import lombok.Builder;

@OwnedBy(PIPELINE)
@JsonTypeName(StepSpecTypeConstantsV1.HTTP)
@Builder
public class HttpStepNode extends PmsAbstractStepNodeV1 {
  String type = StepSpecTypeConstantsV1.HTTP;

  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true) HttpStepInfo spec;

  // TODO: set rollback parameters
  public StepElementParametersV1 getStepParameters(PlanCreationContext ctx) {
    StepElementParametersV1Builder stepBuilder = StepParametersUtilsV1.getStepParameters(this);
    stepBuilder.spec(getSpecParameters());
    stepBuilder.type(StepSpecTypeConstantsV1.HTTP);
    StepUtils.appendDelegateSelectorsToSpecParameters(spec, ctx);
    return stepBuilder.build();
  }

  public SpecParameters getSpecParameters() {
    return HttpStepParameters.infoBuilder()
        .assertion(spec.getAssertion())
        .headers(EmptyPredicate.isEmpty(spec.getHeaders()) ? Collections.emptyMap()
                                                           : spec.getHeaders().stream().collect(Collectors.toMap(
                                                               HttpHeaderConfig::getKey, HttpHeaderConfig::getValue)))
        .cert(spec.getCert())
        .cert_key(spec.getCert_key())
        .method(spec.getMethod())
        .output_vars(NGVariablesUtilsV1.getMapOfVariables(
            spec.getOutput_vars() != null ? spec.getOutput_vars().getMap() : null, 0L))
        .input_vars(NGVariablesUtilsV1.getMapOfVariables(
            spec.getInput_vars() != null ? spec.getInput_vars().getMap() : null, 0L))
        .body(spec.getBody())
        .delegate(ParameterField.createValueField(
            CollectionUtils.emptyIfNull(spec.getDelegate() != null ? spec.getDelegate().getValue() : null)))
        .url(spec.getUrl())
        .build();
  }
}
