/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.creator.variables;

import com.google.common.collect.Sets;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.SSCAEnforceStepNode;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Set;

public class SSCAEnforceStepVariableCreator extends GenericStepVariableCreator<SSCAEnforceStepNode> {
    @Override
    public Set<String> getSupportedStepTypes() {
        return Sets.newHashSet(CIStepInfoType.SSCAEnforce.getDisplayName());
    }

    @Override
    public Class<SSCAEnforceStepNode> getFieldClass() {
        return SSCAEnforceStepNode.class;
    }
}
