package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.CreatePRStepVisitorHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.GITOPS_CREATE_PR)
@SimpleVisitorHelper(helperClass = CreatePRStepVisitorHelper.class)
@TypeAlias("CreatePRStepInfo")
@RecasterAlias("io.harness.cdng.gitops.CreatePRStepInfo")
public class CreatePRStepInfo extends CreatePRBaseStepInfo implements CDStepInfo, Visitable {
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public CreatePRStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<Map<String, String>> stringMap, ParameterField<StoreConfigWrapper> store,
      ParameterField<String> commitMessage, ParameterField<String> targetBranch, ParameterField<Boolean> isNewBranch,
      ParameterField<String> prTitle) {
    super(delegateSelectors, stringMap, store, commitMessage, targetBranch, isNewBranch, prTitle);
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public StepType getStepType() {
    return CreatePRStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return CreatePRStepParams.infoBuilder()
        .delegateSelectors(delegateSelectors)
        .stringMap(stringMap)
        .store(store)
        .commitMessage(commitMessage)
        .isNewBranch(isNewBranch)
        .prTitle(prTitle)
        .targetBranch(targetBranch)
        .build();
  }
}
