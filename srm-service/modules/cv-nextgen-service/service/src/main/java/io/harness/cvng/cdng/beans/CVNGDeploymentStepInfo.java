/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cvng.cdng.services.impl.CVNGAnalyzeDeploymentStep;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
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
@JsonTypeName("AnalyzeDeploymentImpact")
@TypeAlias("deploymentImpactStepInfo")
@OwnedBy(HarnessTeam.CV)
@RecasterAlias("io.harness.cvng.cdng.beans.CVNGDeploymentStepInfo")
public class CVNGDeploymentStepInfo implements CVStepInfoBase {
  @VariableExpression(skipVariableExpression = true)
  private static final String SERVICE_IDENTIFIER_EXPRESSION = "<+service.identifier>";
  @VariableExpression(skipVariableExpression = true)
  private static final String ENV_IDENTIFIER_EXPRESSION = "<+env.identifier>";

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull
  @ApiModelProperty(
      dataType = SwaggerConstants.STRING_CLASSPATH, value = "Format example: 12H, 1D, please put either Hours or days")
  ParameterField<String> duration;

  DefaultAndConfiguredMonitoredServiceNode monitoredService;

  @Builder
  @ConstructorProperties({"duration", "monitoredService"})
  public CVNGDeploymentStepInfo(
      ParameterField<String> duration, DefaultAndConfiguredMonitoredServiceNode monitoredService) {
    this.duration = duration;
    this.monitoredService = monitoredService;
  }
  @Override
  public StepType getStepType() {
    return CVNGAnalyzeDeploymentStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  public void validate() {
    if (duration.getValue() != null) {
      Preconditions.checkState(!duration.getValue().isEmpty(), "Value can not be empty");
      if (duration.getValue().charAt(duration.getValue().length() - 1) != 'H'
          && duration.getValue().charAt(duration.getValue().length() - 1) != 'D') {
        throw new IllegalArgumentException("duration should end with H or D, ex: 12H, 1D etc.");
      }
      String number = duration.getValue().substring(0, duration.getValue().length() - 1);
      try {
        Integer.parseInt(number);
      } catch (NumberFormatException numberFormatException) {
        throw new IllegalArgumentException(
            "can not parse duration please check format for duration., ex: 12H, 1D etc.", numberFormatException);
      }
    }
  }

  private ParameterField<String> createExpressionField(String expression) {
    return ParameterField.createExpressionField(true, expression, null, true);
  }

  private ParameterField<Integer> createValueField(Integer value) {
    return ParameterField.createValueField(value);
  }

  @Override
  public SpecParameters getSpecParameters() {
    return CVNGDeploymentImpactStepParameter.builder()
        .serviceIdentifier(createExpressionField(SERVICE_IDENTIFIER_EXPRESSION))
        .envIdentifier(createExpressionField(ENV_IDENTIFIER_EXPRESSION))
        .monitoredService(monitoredService)
        .duration(duration)
        .build();
  }
}
