/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.http.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.http.HttpHeaderConfig;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.StepParametersUtils;
import io.harness.plancreator.steps.http.HttpStepInfo;
import io.harness.plancreator.steps.internal.v1.PmsAbstractStepNodeV1;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.http.HttpStepParameters;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.HTTP)
@TypeAlias("HttpStepNodeV1")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.plancreator.steps.http.v1.HttpStepNodeV1")
public class HttpStepNodeV1 extends PmsAbstractStepNodeV1 {
  StepType type = StepType.Http;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  HttpStepInfo httpStepInfo;

  // will re-iterate
  enum StepType {
    Http(StepSpecTypeConstants.HTTP);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }

  // TODO: set rollback parameters
  public StepElementParameters getStepParameters(PlanCreationContext ctx) {
    StepElementParameters.StepElementParametersBuilder stepBuilder = StepParametersUtils.getStepParametersV1(this);
    stepBuilder.spec(getSpecParameters(getHttpStepInfo()));
    stepBuilder.type(StepSpecTypeConstants.HTTP);
    StepUtils.appendDelegateSelectorsToSpecParameters(httpStepInfo, ctx);
    return stepBuilder.build();
  }

  public SpecParameters getSpecParameters(HttpStepInfo httpStepInfo) {
    return HttpStepParameters.infoBuilder()
        .assertion(httpStepInfo.getAssertion())
        .headers(EmptyPredicate.isEmpty(httpStepInfo.getHeaders())
                ? Collections.emptyMap()
                : httpStepInfo.getHeaders().stream().collect(
                    Collectors.toMap(HttpHeaderConfig::getKey, HttpHeaderConfig::getValue)))
        .certificate(httpStepInfo.getCertificate())
        .certificateKey(httpStepInfo.getCertificateKey())
        .method(httpStepInfo.getMethod())
        .outputVariables(NGVariablesUtils.getMapOfVariables(httpStepInfo.getOutputVariables(), 0L))
        .inputVariables(NGVariablesUtils.getMapOfVariables(httpStepInfo.getInputVariables(), 0L))
        .requestBody(httpStepInfo.getRequestBody())
        .delegateSelectors(ParameterField.createValueField(CollectionUtils.emptyIfNull(
            httpStepInfo.getDelegateSelectors() != null ? httpStepInfo.getDelegateSelectors().getValue() : null)))
        .url(httpStepInfo.getUrl())
        .build();
  }
}
