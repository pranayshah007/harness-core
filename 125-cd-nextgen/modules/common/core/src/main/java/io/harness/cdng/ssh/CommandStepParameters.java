/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("CommandStepParameters")
@RecasterAlias("io.harness.cdng.ssh.CommandStepParameters")
public class CommandStepParameters extends CommandBaseStepInfo implements SshSpecParameters {
  Map<String, Object> environmentVariables;
  Map<String, Object> outputVariables;
  @JsonIgnore boolean isRollback;
  @JsonIgnore String commandDeployFqn;
  @JsonIgnore Set<String> secretOutputVariablesNames;

  @Builder(builderMethodName = "infoBuilder")
  public CommandStepParameters(String uuid, ParameterField<Boolean> onDelegate,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, Map<String, Object> environmentVariables,
      List<CommandUnitWrapper> commandUnits, Map<String, Object> outputVariables, ParameterField<String> host,
      boolean isRollback, Set<String> secretOutputVariablesNames) {
    super(uuid, onDelegate, delegateSelectors, commandUnits, host);
    this.environmentVariables = environmentVariables;
    this.outputVariables = outputVariables;
    this.isRollback = isRollback;
    this.secretOutputVariablesNames = secretOutputVariablesNames;
  }
}
