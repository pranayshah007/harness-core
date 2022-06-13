/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static java.util.Collections.singletonList;

import software.wings.service.impl.instance.DeploymentInfoExtractor;
import software.wings.sm.StepExecutionSummary;

import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class InstanceFetchStateExecutionSummary extends StepExecutionSummary implements DeploymentInfoExtractor {
  private String instanceFetchScript;
  private String scriptOutput;
  private String activityId;
  private List<String> tags;

  @Override
  public Optional<List<DeploymentInfo>> extractDeploymentInfo() {
    return Optional.of(singletonList(CustomDeploymentTypeInfo.builder()
                                         .scriptOutput(scriptOutput)
                                         .instanceFetchScript(instanceFetchScript)
                                         .tags(tags)
                                         .build()));
  }
}
