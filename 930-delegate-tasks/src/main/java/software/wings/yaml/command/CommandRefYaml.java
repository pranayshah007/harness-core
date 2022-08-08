/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.yaml.command;

import software.wings.beans.command.AbstractCommandUnit;
import software.wings.beans.command.CommandUnitType;
import software.wings.yaml.templatelibrary.TemplateVariableYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This yaml is used to represent a command reference. A command could be referred from another command, in that case,
 * we need a ref.
 *
 * @author rktummala on 11/16/17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("COMMAND")
public class CommandRefYaml extends AbstractCommandUnit.Yaml {
  private List<TemplateVariableYaml> variables;
  private String templateUri;

  public CommandRefYaml() {
    super(CommandUnitType.COMMAND.name());
  }

  @Builder
  public CommandRefYaml(String name, String deploymentType, String templateUri, List<TemplateVariableYaml> variables) {
    super(name, CommandUnitType.COMMAND.name(), deploymentType);
    setVariables(variables);
    setTemplateUri(templateUri);
  }
}
