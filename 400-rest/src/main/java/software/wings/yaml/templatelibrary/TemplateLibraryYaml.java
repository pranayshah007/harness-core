/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.templatelibrary;

import static software.wings.common.TemplateConstants.ARTIFACT_SOURCE;
import static software.wings.common.TemplateConstants.CUSTOM_DEPLOYMENT_TYPE;
import static software.wings.common.TemplateConstants.HTTP;
import static software.wings.common.TemplateConstants.PCF_PLUGIN;
import static software.wings.common.TemplateConstants.SHELL_SCRIPT;
import static software.wings.common.TemplateConstants.SSH;

import software.wings.yaml.BaseEntityYaml;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author abhinav
 */

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeInfo(use = Id.NAME, property = "type", include = As.EXISTING_PROPERTY)
@JsonSubTypes({
  @Type(value = ShellScriptTemplateYaml.class, name = SHELL_SCRIPT)
  , @Type(value = CommandTemplateYaml.class, name = SSH), @Type(value = HttpTemplateYaml.class, name = HTTP),
      @Type(value = ArtifactSourceTemplateYaml.class, name = ARTIFACT_SOURCE),
      @Type(value = PcfCommandTemplateYaml.class, name = PCF_PLUGIN),
      @Type(value = CustomDeploymentTypeTemplateYaml.class, name = CUSTOM_DEPLOYMENT_TYPE)
})
public abstract class TemplateLibraryYaml extends BaseEntityYaml {
  private String description;
  private List<TemplateVariableYaml> variables;

  public TemplateLibraryYaml(
      String type, String harnessApiVersion, String description, List<TemplateVariableYaml> variables) {
    super(type, harnessApiVersion);
    this.description = description;
    this.variables = variables;
  }
}
