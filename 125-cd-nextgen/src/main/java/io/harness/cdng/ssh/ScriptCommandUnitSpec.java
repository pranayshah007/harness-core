/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonTypeName(CommandUnitSpecType.SCRIPT)
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.ssh.ScriptCommandUnitSpec")
public class ScriptCommandUnitSpec implements CommandUnitBaseSpec {
  @NotNull ShellType shell;
  @NotNull ShellScriptSourceWrapper source;
  List<TailFilePattern> tailFiles;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> workingDirectory;

  @Override
  public String getType() {
    return CommandUnitSpecType.SCRIPT;
  }
}
