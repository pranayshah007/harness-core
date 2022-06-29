/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.serverless.ServerlessCommandUnitConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

@OwnedBy(HarnessTeam.CDP)
public interface ServerlessSpecParameters extends SpecParameters {
  @JsonIgnore ParameterField<List<TaskSelectorYaml>> getDelegateSelectors();

  @Nonnull
  @JsonIgnore
  default List<String> getCommandUnits() {
    return Arrays.asList(ServerlessCommandUnitConstants.fetchFiles.toString(),
        ServerlessCommandUnitConstants.rollbackData.toString(),
        ServerlessCommandUnitConstants.setupDirectory.toString(), ServerlessCommandUnitConstants.artifact.toString(),
        ServerlessCommandUnitConstants.configureCred.toString(), ServerlessCommandUnitConstants.plugin.toString(),
        ServerlessCommandUnitConstants.deploy.toString());
  }
}
