/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.manifests.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.Cd1ApplicationAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.manifest.CustomManifestSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class CustomManifestValuesFetchParams
    implements TaskParameters, ActivityAccess, Cd1ApplicationAccess, ExecutionCapabilityDemander {
  @Expression(ALLOW_SECRETS) private List<CustomManifestFetchConfig> fetchFilesList;
  private String activityId;
  private String commandUnitName;
  private String accountId;
  private String appId;
  private Set<String> delegateSelectors;
  @Expression(ALLOW_SECRETS) private CustomManifestSource customManifestSource;
  boolean shouldOpenLogStream;
  boolean shouldCloseLogStream;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();

    if (isNotEmpty(delegateSelectors)) {
      executionCapabilities.add(SelectorCapability.builder().selectors(delegateSelectors).build());
    }

    return executionCapabilities;
  }
}
