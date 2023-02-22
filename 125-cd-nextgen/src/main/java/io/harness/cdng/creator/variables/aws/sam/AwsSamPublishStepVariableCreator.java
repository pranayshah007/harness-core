/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.variables.aws.sam;

import io.harness.cdng.aws.sam.publish.AwsSamPublishStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

public class AwsSamPublishStepVariableCreator extends GenericStepVariableCreator<AwsSamPublishStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.AWS_SAM_PUBLISH);
  }

  @Override
  public Class<AwsSamPublishStepNode> getFieldClass() {
    return AwsSamPublishStepNode.class;
  }
}
