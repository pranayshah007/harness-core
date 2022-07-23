package io.harness.ci.creator.variables;

import com.google.common.collect.Sets;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.BuildAndPushACRNode;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Set;

public class BuildAndPushACRStepVariableCreator extends GenericStepVariableCreator<BuildAndPushACRNode> {
    @Override
    public Set<String> getSupportedStepTypes() {
        return Sets.newHashSet(CIStepInfoType.ACR.getDisplayName());
    }

    @Override
    public Class<BuildAndPushACRNode> getFieldClass() {
        return BuildAndPushACRNode.class;
    }
}
