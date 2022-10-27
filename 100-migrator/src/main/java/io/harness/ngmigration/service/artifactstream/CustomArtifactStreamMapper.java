/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptInfo;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptSourceWrapper;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScripts;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.bean.yaml.customartifact.FetchAllArtifacts;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.shellscript.ShellType;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CustomArtifactStreamMapper implements ArtifactStreamMapper {
  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    CustomArtifactStream customArtifactStream = (CustomArtifactStream) artifactStream;
    CustomArtifactStream.Script primaryScript = customArtifactStream.getScripts().get(0);
    return PrimaryArtifact.builder()
        .sourceType(ArtifactSourceType.CUSTOM_ARTIFACT)
        .spec(CustomArtifactConfig.builder()
                  .timeout(ParameterField.createValueField(Timeout.fromString(primaryScript.getTimeout() + "s")))
                  .primaryArtifact(true)
                  .scripts(CustomArtifactScripts.builder()
                               .fetchAllArtifacts(
                                   FetchAllArtifacts.builder()
                                       .artifactsArrayPath(ParameterField.createValueField(
                                           primaryScript.getCustomRepositoryMapping().getArtifactRoot()))
                                       .versionPath(ParameterField.createValueField(
                                           primaryScript.getCustomRepositoryMapping().getBuildNoPath()))
                                       .attributes(primaryScript.getCustomRepositoryMapping()
                                                       .getArtifactAttributes()
                                                       .stream()
                                                       .map(attribute
                                                           -> StringNGVariable.builder()
                                                                  .name(attribute.getMappedAttribute())
                                                                  .value(ParameterField.createValueField(
                                                                      attribute.getRelativePath()))
                                                                  .build())
                                                       .collect(Collectors.toList()))
                                       .shellScriptBaseStepInfo(
                                           CustomArtifactScriptInfo.builder()
                                               .shell(ShellType.Bash)
                                               .source(CustomArtifactScriptSourceWrapper.builder()
                                                           .type("Inline")
                                                           .spec(CustomScriptInlineSource.builder()
                                                                     .script(ParameterField.createValueField(
                                                                         primaryScript.getScriptString().replace(
                                                                             "${ARTIFACT_RESULT_PATH}",
                                                                             "$HARNESS_ARTIFACT_RESULT_PATH")))
                                                                     .build())
                                                           .build())
                                               .build())
                                       .build())
                               .build())
                  .version(ParameterField.createValueField("<+input>"))
                  .build())
        .build();
  }
}
