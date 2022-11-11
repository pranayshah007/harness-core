/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.EntityType;
import software.wings.beans.Variable;
import software.wings.beans.VariableDisplayType;
import software.wings.beans.VariableType;
import software.wings.beans.WorkflowExecution;
import software.wings.graphql.schema.type.QLInputVariable;
import software.wings.graphql.schema.type.QLVariable;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class VariableController {
  public static void populateVariables(List<Variable> userVariables, List<QLVariable> qlVariables) {
    if (userVariables == null) {
      return;
    }
    userVariables.stream()
        .map(variable
            -> QLVariable.builder()
                   .name(variable.getName())
                   .type(getStringType(variable.getType(), variable.obtainEntityType(), variable.getName()))
                   .required(variable.isMandatory())
                   .description(variable.getDescription())
                   .allowedValues(variable.getAllowedList())
                   .allowMultipleValues(variable.isAllowMultipleValues())
                   .defaultValue(variable.getValue())
                   .fixed(variable.isFixed())
                   .runtimeInput(variable.getRuntimeInput())
                   .build())
        .forEach(qlVariables::add);
  }

  private static String getStringType(VariableType type, EntityType entityType, String name) {
    if (type == VariableType.ENTITY) {
      if (entityType == null) {
        throw new InvalidRequestException("Entity type should not be null for Entity variable: " + name);
      }
      return VariableDisplayType.valueOf(entityType.name()).getDisplayName();
    }

    return VariableDisplayType.TEXT.getDisplayName();
  }

  public static void populateInputVariables(List<QLInputVariable> variables, WorkflowExecution workflowExecution) {
    if (workflowExecution != null) {
      Map<String, String> workflowVariables = workflowExecution.getExecutionArgs().getWorkflowVariables();
      if (workflowVariables == null) {
        return;
      }
      workflowVariables.entrySet()
          .stream()
          .filter(entry -> entry.getValue() != null)
          .map(entry -> QLInputVariable.builder().name(entry.getKey()).value(entry.getValue()).build())
          .forEach(variables::add);
    }
  }
}
