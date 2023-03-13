/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.cdng.services.impl.CVNGStep;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName("Verify")
@TypeAlias("verificationStepInfo")
@OwnedBy(HarnessTeam.CV)
@RecasterAlias("io.harness.cvng.cdng.beans.CVNGStepInfo")
public class CVNGStepInfo implements CVStepInfoBase {
  @VariableExpression(skipVariableExpression = true)
  private static final String SERVICE_IDENTIFIER_EXPRESSION = "<+service.identifier>";
  @VariableExpression(skipVariableExpression = true)
  private static final String ENV_IDENTIFIER_EXPRESSION = "<+env.identifier>";

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull String type;
  @NotNull
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  VerificationJobSpec spec;

  MonitoredServiceNode monitoredService;

  @Builder
  @ConstructorProperties({"type", "spec"})
  public CVNGStepInfo(String type, VerificationJobSpec spec) {
    this.type = spec.getType();
    this.spec = spec;
  }

  @Override
  public StepType getStepType() {
    return CVNGStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  private ParameterField<String> createExpressionField(String expression) {
    return ParameterField.createExpressionField(true, expression, null, true);
  }

  public void validate() {
    spec.validate();
  }

  @Override
  public SpecParameters getSpecParameters() {
    return CVNGStepParameter.builder()
        .serviceIdentifier(createExpressionField(SERVICE_IDENTIFIER_EXPRESSION))
        .envIdentifier(createExpressionField(ENV_IDENTIFIER_EXPRESSION))
        .deploymentTag(spec.getDeploymentTag())
        .failOnNoAnalysis(spec.getFailOnNoAnalysis())
        .sensitivity(spec.getSensitivity())
        .spec(spec)
        .monitoredService(monitoredService)
        .build();
  }
}
