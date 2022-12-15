/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.spot.elastigroup.rollback;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.spot.elastigroup.deploy.ElastigroupSpecParameters;
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
@TypeAlias("ElastigroupRollbackStepParameters")
@RecasterAlias("io.harness.cdng.spot.elastigroup.rollback.ElastigroupRollbackStepParameters")
public class ElastigroupRollbackStepParameters implements ElastigroupSpecParameters {
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @Builder
  public ElastigroupRollbackStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    this.delegateSelectors = delegateSelectors;
  }
}
