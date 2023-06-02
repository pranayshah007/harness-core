/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.logging.CommandExecutionStatus;
import io.harness.reflection.ExpressionReflectionUtils;

import software.wings.beans.Variable;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@JsonTypeName("COMMAND")
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@AllArgsConstructor
@OwnedBy(CDC)
public class Command implements CommandUnit, ExpressionReflectionUtils.NestedAnnotationResolver {
  private String name;
  private CommandUnitType commandUnitType;
  private CommandExecutionStatus commandExecutionStatus = CommandExecutionStatus.QUEUED;

  @Getter(AccessLevel.NONE) private boolean artifactNeeded;
  @Deprecated private String deploymentType;

  @Expression(ALLOW_SECRETS) private List<CommandUnit> commandUnits = Lists.newArrayList();

  private CommandType commandType = CommandType.OTHER;
  private List<Variable> templateVariables = new ArrayList<>();

  private List<Variable> variables = new ArrayList<>();

  public Command() {
    this.commandUnitType = CommandUnitType.COMMAND;
  }

  @Override
  public CommandExecutionStatus execute(CommandExecutionContext context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isArtifactNeeded() {
    Set<String> serviceArtifactVariableNames = new HashSet<>();
    updateServiceArtifactVariableNames(serviceArtifactVariableNames);
    return isNotEmpty(serviceArtifactVariableNames);
  }

  @Override
  public void updateServiceArtifactVariableNames(Set<String> serviceArtifactVariableNames) {
    if (isNotEmpty(templateVariables)) { // when command linked to service
      for (Variable variable : templateVariables) {
        ExpressionEvaluator.updateServiceArtifactVariableNames(variable.getValue(), serviceArtifactVariableNames);
      }
    }
    if (isNotEmpty(commandUnits)) {
      commandUnits.forEach(commandUnit -> commandUnit.updateServiceArtifactVariableNames(serviceArtifactVariableNames));
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(commandUnits);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final Command other = (Command) obj;
    return Objects.equals(this.commandUnits, other.commandUnits);
  }
}
