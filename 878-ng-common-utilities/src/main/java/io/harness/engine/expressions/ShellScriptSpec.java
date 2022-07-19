/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.integer;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.ng.core.template.TemplateEntityConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonTypeName(TemplateEntityConstants.SCRIPT)
@TypeAlias("ShellScriptSpec")
@OwnedBy(HarnessTeam.PL)
public class ShellScriptSpec implements Visitable, WithDelegateSelector {
  // TIMEOUT
  @NotNull @ApiModelProperty(dataType = INTEGER_CLASSPATH) @YamlSchemaTypes({integer}) ParameterField<Integer> timeout;

  // UUID
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  // SHELL TYPE
  @NotNull ShellType shell;

  // ON DELEGATE
  @NotNull
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  @YamlSchemaTypes({string})
  ParameterField<Boolean> onDelegate;

  // EXECUTION TARGET
  ExecutionTarget executionTarget;

  // DELEGATE SELECTOR
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  // OUTPUT VARIABLES
  @VariableExpression(skipVariableExpression = true) List<NGVariable> outputVariables;

  // ENVIRONMENT VARIABLES
  List<NGVariable> environmentVariables;

  // SOURCE
  @NotNull ShellScriptSourceWrapper source;

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return delegateSelectors;
  }
}
