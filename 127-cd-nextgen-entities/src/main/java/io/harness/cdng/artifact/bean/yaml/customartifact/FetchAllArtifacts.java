/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml.customartifact;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.variables.NGVariable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

import javax.validation.constraints.NotNull;
import java.util.List;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

@OwnedBy(HarnessTeam.CDC)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SimpleVisitorHelper(helperClass = CustomArtifactSpecVisitorHelper.class)
@TypeAlias("FetchAllArtifacts")
public class FetchAllArtifacts {
  @YamlSchemaTypes({string}) @Wither private ParameterField<String> artifactsArrayPath;
  private List<NGVariable> attributes;
  @YamlSchemaTypes({string}) @Wither private ParameterField<String> versionPath;
  @NotNull @JsonProperty("spec") CustomArtifactScriptInfo shellScriptBaseStepInfo;
}
