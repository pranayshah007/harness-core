/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptInfo;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptSourceWrapper;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScripts;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.bean.yaml.customartifact.FetchAllArtifacts;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.shellscript.ShellType;
import io.harness.yaml.core.variables.NGVariable;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class CustomArtifactInfo implements ArtifactInfo {
  String version;
  String artifactsArrayPath;
  String script;
  String versionPath;
  Map<String, String> metadata;
  List<NGVariable> inputs;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.CUSTOM_ARTIFACT;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    CustomArtifactScriptSourceWrapper customArtifactScriptSourceWrapper =
        CustomArtifactScriptSourceWrapper.builder()
            .type("Inline")
            .spec(CustomScriptInlineSource.builder()
                      .script(ParameterField.<String>builder().value(script).build())
                      .build())
            .build();
    CustomArtifactScriptInfo customArtifactScriptInfo =
        CustomArtifactScriptInfo.builder().shell(ShellType.Bash).source(customArtifactScriptSourceWrapper).build();
    FetchAllArtifacts fetchAllArtifacts =
        FetchAllArtifacts.builder()
            .artifactsArrayPath(ParameterField.<String>builder().value(artifactsArrayPath).build())
            .versionPath(ParameterField.<String>builder().value(versionPath).build())
            .shellScriptBaseStepInfo(customArtifactScriptInfo)
            .build();

    CustomArtifactScripts customArtifactScripts =
        CustomArtifactScripts.builder().fetchAllArtifacts(fetchAllArtifacts).build();
    return CustomArtifactConfig.builder()
        .version(ParameterField.<String>builder().value(version).build())
        .scripts(customArtifactScripts)
        .versionRegex(ParameterField.<String>builder().value(version).build())
        .inputs(inputs)
        .build();
  }
}
