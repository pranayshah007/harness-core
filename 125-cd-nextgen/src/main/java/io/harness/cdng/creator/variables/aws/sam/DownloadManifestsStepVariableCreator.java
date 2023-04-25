package io.harness.cdng.creator.variables.aws.sam;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.sam.AwsSamRollbackStepNode;
import io.harness.cdng.aws.sam.DownloadManifestsStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class DownloadManifestsStepVariableCreator extends GenericStepVariableCreator<DownloadManifestsStepNode> {
    @Override
    public Set<String> getSupportedStepTypes() {
        return Collections.singleton(StepSpecTypeConstants.DOWNLOAD_MANIFESTS);
    }

    @Override
    public Class<DownloadManifestsStepNode> getFieldClass() {
        return DownloadManifestsStepNode.class;
    }
}
