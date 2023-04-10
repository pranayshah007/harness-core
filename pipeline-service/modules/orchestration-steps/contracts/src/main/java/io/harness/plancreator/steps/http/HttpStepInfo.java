/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.http;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.http.HttpHeaderConfig;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.http.HttpBaseStepInfo;
import io.harness.steps.http.HttpStepParameters;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.HTTP)
@SimpleVisitorHelper(helperClass = HttpStepInfoVisitorHelper.class)
@TypeAlias("httpStepInfo")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.plancreator.steps.http.HttpStepInfo")
public class HttpStepInfo extends HttpBaseStepInfo implements PMSStepInfo, Visitable, WithDelegateSelector {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @VariableExpression(skipVariableExpression = true) List<NGVariable> outputVariables;
  List<HttpHeaderConfig> headers;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> certificate;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> certificateKey;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {runtime})
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @Builder(builderMethodName = "infoBuilder")
  public HttpStepInfo(ParameterField<String> url, ParameterField<String> method, ParameterField<String> requestBody,
      ParameterField<String> assertion, String metadata, List<NGVariable> outputVariables,
      List<HttpHeaderConfig> headers, ParameterField<String> certificate, ParameterField<String> certificateKey,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    super(url, method, requestBody, assertion);
    this.metadata = metadata;
    this.outputVariables = outputVariables;
    this.headers = headers;
    this.certificate = certificate;
    this.certificateKey = certificateKey;
    this.delegateSelectors = delegateSelectors;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.HTTP_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    return VisitableChildren.builder().build();
  }

  @Override
  public SpecParameters getSpecParameters() {
    return HttpStepParameters.infoBuilder()
        .assertion(getAssertion())
        .headers(EmptyPredicate.isEmpty(headers)
                ? Collections.emptyMap()
                : headers.stream().collect(Collectors.toMap(HttpHeaderConfig::getKey, HttpHeaderConfig::getValue)))
        .certificate(getCertificate())
        .certificateKey(getCertificateKey())
        .method(getMethod())
        .outputVariables(NGVariablesUtils.getMapOfVariables(outputVariables, 0L))
        .requestBody(getRequestBody())
        .delegateSelectors(ParameterField.createValueField(
            CollectionUtils.emptyIfNull(delegateSelectors != null ? delegateSelectors.getValue() : null)))
        .url(getUrl())
        .build();
  }

  @Override
  public ExpressionMode getExpressionMode() {
    return ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
