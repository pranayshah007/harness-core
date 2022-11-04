/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.spot.elastigroup.deploy;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.common.capacity.Capacity;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@TypeAlias("ElastigroupDeployStepParameters")
@RecasterAlias("io.harness.cdng.spot.elastigroup.deploy.ElastigroupDeployStepParameters")
public class ElastigroupDeployStepParameters implements ElastigroupSpecParameters {
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  Capacity newService;
  Capacity oldService;

  @Builder
  public ElastigroupDeployStepParameters(
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, Capacity newService, Capacity oldService) {
    this.delegateSelectors = delegateSelectors;
    this.newService = newService;
    this.oldService = oldService;
  }
}
