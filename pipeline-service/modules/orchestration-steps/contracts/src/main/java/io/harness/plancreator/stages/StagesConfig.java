/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("stagesConfig")
public class StagesConfig {
  List<StageElementWrapperConfig> stages;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public StagesConfig(List<StageElementWrapperConfig> stages) {
    this.stages = stages;
  }
}
