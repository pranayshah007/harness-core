/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;
import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SimpleVisitorHelper(helperClass = AzureWebAppSlotDeploymentStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.AZURE_SLOT_DEPLOYMENT)
@TypeAlias("azureWebAppSlotDeploymentStepInfo")
@RecasterAlias("io.harness.cdng.azure.webapp.AzureWebAppSlotDeploymentStepInfo")
public class AzureWebAppSlotDeploymentStepInfo
    extends AzureWebAppSlotDeploymentBaseStepInfo implements CDAbstractStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @NotNull @NotEmpty @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> webApp;
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<String> deploymentSlot;

  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH) @YamlSchemaTypes({expression}) ParameterField<Boolean> clean;

  @Builder(builderMethodName = "infoBuilder")
  public AzureWebAppSlotDeploymentStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<String> webApp, ParameterField<String> deploymentSlot, ParameterField<Boolean> clean) {
    super(delegateSelectors);
    this.webApp = webApp;
    this.deploymentSlot = deploymentSlot;
    this.clean = clean;
  }

  @Override
  public StepType getStepType() {
    return AzureWebAppSlotDeploymentStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return AzureWebAppSlotDeploymentStepParameters.infoBuilder()
        .webApp(this.getWebApp())
        .deploymentSlot(this.getDeploymentSlot())
        .delegateSelectors(this.getDelegateSelectors())
        .clean(this.getClean())
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
