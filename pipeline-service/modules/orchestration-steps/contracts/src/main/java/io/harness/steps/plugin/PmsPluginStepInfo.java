package io.harness.steps.plugin;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.TypeAlias;

@Data
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.PLUGIN_STEP)
@SimpleVisitorHelper(helperClass = PmsPluginStepInfoVisitorHelper.class)
@TypeAlias("shellScriptStepInfo")
@OwnedBy(HarnessTeam.CDC)
@RecasterAlias("io.harness.steps.plugin.PmsPluginStepInfo")
public class PmsPluginStepInfo implements PMSStepInfo, Visitable, WithDelegateSelector {
  @VariableExpression(skipVariableExpression = true) List<NGVariable> outputVariables;
  @YamlSchemaTypes(value = {runtime}) ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @Builder(builderMethodName = "infoBuilder")
  public PmsPluginStepInfo() {}

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.PLUGIN_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    // todo(abhinav): implement
    return null;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    // todo(abhinav): implement
    return null;
  }
}
