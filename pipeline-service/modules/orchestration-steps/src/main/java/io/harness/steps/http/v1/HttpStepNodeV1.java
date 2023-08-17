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
import io.harness.plancreator.steps.http.HttpStepInfo;
import io.harness.plancreator.steps.internal.v1.PmsAbstractStepNodeV1;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.http.HttpStepParameters;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import java.util.stream.Collectors;
import lombok.Data;

@OwnedBy(PIPELINE)
@JsonTypeName(StepSpecTypeConstants.HTTP)
@Data
public class HttpStepNodeV1 extends PmsAbstractStepNodeV1 {
  String type = StepSpecTypeConstants.HTTP;

  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true) HttpStepInfo spec;

  // TODO: set rollback parameters
  public StepElementParametersV1 getStepParameters(PlanCreationContext ctx) {
    StepElementParametersV1Builder stepBuilder = StepParametersUtilsV1.getStepParameters(this);
    stepBuilder.spec(getSpecParameters());
    stepBuilder.type(StepSpecTypeConstants.HTTP);
    StepUtils.appendDelegateSelectorsToSpecParameters(spec, ctx);
    return stepBuilder.build();
  }

  public SpecParameters getSpecParameters() {
    return HttpStepParameters.infoBuilder()
        .assertion(spec.getAssertion())
        .headers(EmptyPredicate.isEmpty(spec.getHeaders()) ? Collections.emptyMap()
                                                           : spec.getHeaders().stream().collect(Collectors.toMap(
                                                               HttpHeaderConfig::getKey, HttpHeaderConfig::getValue)))
        .certificate(spec.getCertificate())
        .certificateKey(spec.getCertificateKey())
        .method(spec.getMethod())
        .outputVariables(NGVariablesUtils.getMapOfVariables(spec.getOutputVariables(), 0L))
        .inputVariables(NGVariablesUtils.getMapOfVariables(spec.getInputVariables(), 0L))
        .requestBody(spec.getRequestBody())
        .delegateSelectors(ParameterField.createValueField(CollectionUtils.emptyIfNull(
            spec.getDelegateSelectors() != null ? spec.getDelegateSelectors().getValue() : null)))
        .url(spec.getUrl())
        .build();
  }
}
