package io.harness.cdng.aws.sam;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.aws.sam.AwsSamDeployStepInfoVisitorHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SimpleVisitorHelper(helperClass = AwsSamDeployStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.DOWNLOAD_MANIFESTS)
@TypeAlias("downloadManifestsStepInfo")
@RecasterAlias("io.harness.cdng.aws.sam.DownloadManifestsStepInfo")
public class DownloadManifestsStepInfo extends DownloadManifestsBaseStepInfo implements CDAbstractStepInfo, Visitable {
    @Override
    public StepType getStepType() {
        return DownloadManifestsStep.STEP_TYPE;
    }

    @Override
    public String getFacilitatorType() {
        return null;
    }

    @Override
    public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
        return null;
    }

    @Override
    public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {

    }
}
