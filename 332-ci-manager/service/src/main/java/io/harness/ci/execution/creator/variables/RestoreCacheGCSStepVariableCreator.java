/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.creator.variables;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.RestoreCacheGCSNode;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import com.google.common.collect.Sets;
import java.util.Set;

public class RestoreCacheGCSStepVariableCreator extends GenericStepVariableCreator<RestoreCacheGCSNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.RESTORE_CACHE_GCS.getDisplayName());
  }

  @Override
  public Class<RestoreCacheGCSNode> getFieldClass() {
    return RestoreCacheGCSNode.class;
  }
}
