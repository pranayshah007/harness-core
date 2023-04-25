package io.harness.cdng.creator.plan.steps.aws.sam;

import com.google.common.collect.Sets;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.sam.AwsSamDeployStepNode;
import io.harness.cdng.aws.sam.DownloadManifestsStepNode;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.executions.steps.StepSpecTypeConstants;

import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class DownloadManifestsStepPlanCreator extends CDPMSStepPlanCreatorV2<DownloadManifestsStepNode> {
    @Override
    public Set<String> getSupportedStepTypes() {
        return Sets.newHashSet(StepSpecTypeConstants.DOWNLOAD_MANIFESTS);
    }

    @Override
    public Class<DownloadManifestsStepNode> getFieldClass() {
        return DownloadManifestsStepNode.class;
    }
}
