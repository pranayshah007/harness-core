/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.WithFileRefs;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.COMMAND)
@SimpleVisitorHelper(helperClass = CommandStepInfoVisitorHelper.class)
@TypeAlias("commandStepInfo")
@RecasterAlias("io.harness.cdng.ssh.CommandStepInfo")
public class CommandStepInfo extends CommandBaseStepInfo implements CDAbstractStepInfo, Visitable, WithFileRefs {
  List<NGVariable> environmentVariables;
  @VariableExpression(skipVariableExpression = true) List<NGVariable> outputVariables;

  @Builder(builderMethodName = "infoBuilder")
  public CommandStepInfo(String uuid, ParameterField<Boolean> onDelegate,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, List<NGVariable> environmentVariables,
      List<NGVariable> outputVariables, List<CommandUnitWrapper> commandUnits, ParameterField<String> host) {
    super(uuid, onDelegate, delegateSelectors, commandUnits, host);
    this.environmentVariables = environmentVariables;
    this.outputVariables = outputVariables;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return CommandStep.STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return CommandStepParameters.infoBuilder()
        .onDelegate(getOnDelegate())
        .delegateSelectors(getDelegateSelectors())
        .environmentVariables(NGVariablesUtils.getMapOfVariables(environmentVariables, 0L))
        .outputVariables(NGVariablesUtils.getMapOfVariablesWithoutSecretExpression(outputVariables))
        .secretOutputVariablesNames(NGVariablesUtils.getSetOfSecretVars(outputVariables))
        .commandUnits(getCommandUnits())
        .host(host)
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public Map<String, ParameterField<List<String>>> extractFileRefs() {
    Map<String, ParameterField<List<String>>> fileRefMap = new HashMap<>();
    if (commandUnits != null) {
      commandUnits.forEach(commandUnit -> {
        if (commandUnit != null && commandUnit.getSpec() instanceof ScriptCommandUnitSpec) {
          ScriptCommandUnitSpec scriptCommandUnitSpec = (ScriptCommandUnitSpec) commandUnit.getSpec();
          List<String> fileRef = scriptCommandUnitSpec.getSource().fetchFileRefs();
          fileRefMap.put(String.format("commandUnits.%s.spec.source.spec.file", commandUnit.getIdentifier()),
              ParameterField.createValueField(fileRef));
        }
      });
    }
    return fileRefMap;
  }
}
