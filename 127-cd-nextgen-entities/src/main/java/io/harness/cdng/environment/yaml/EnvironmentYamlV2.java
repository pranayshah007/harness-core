/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.environment.helper.EnvironmentYamlV2VisitorHelper;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@SimpleVisitorHelper(helperClass = EnvironmentYamlV2VisitorHelper.class)
@TypeAlias("environmentYamlV2")
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.environment.yaml.EnvironmentYamlV2")
public class EnvironmentYamlV2 implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Pattern(regexp = NGRegexValidatorConstants.RUNTIME_OR_FIXED_IDENTIFIER_PATTERN)
  private ParameterField<String> environmentRef;

  List<InfraStructureDefinitionYaml> infrastructureDefinitions;

  // environmentInputs
  Map<String, Object> environmentInputs;

  @DefaultValue("false") Boolean deployToAll;
  List<ClusterYaml> gitOpsClusters;
}
