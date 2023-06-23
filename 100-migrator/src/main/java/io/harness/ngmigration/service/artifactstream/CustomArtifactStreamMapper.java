/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;

import static software.wings.ngmigration.NGMigrationEntityType.TEMPLATE;

import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
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
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.shellscript.ShellType;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.yaml.TemplateLinkConfig;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.Variable;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.template.Template;
import software.wings.beans.trigger.Trigger;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

public class CustomArtifactStreamMapper implements ArtifactStreamMapper {
  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    CustomArtifactStream customArtifactStream = (CustomArtifactStream) artifactStream;
    if (isNotEmpty(customArtifactStream.getTemplateUuid())) {
      TemplateLinkConfig templateLinkConfig = new TemplateLinkConfig();
      templateLinkConfig.setTemplateRef(MigratorUtility.getIdentifierWithScope(
          migratedEntities, customArtifactStream.getTemplateUuid(), NGMigrationEntityType.TEMPLATE));
      templateLinkConfig.setVersionLabel(
          ((NGTemplateConfig) migratedEntities
                  .get(CgEntityId.builder().id(customArtifactStream.getTemplateUuid()).type(TEMPLATE).build())
                  .getYaml())
              .getTemplateInfoConfig()
              .getVersionLabel());
      JsonNode inputs = generateInput(entities, customArtifactStream);
      if (inputs != null) {
        templateLinkConfig.setTemplateInputs(inputs);
      }
      return PrimaryArtifact.builder()
          .primaryArtifactRef(ParameterField.createValueField("<+input>"))
          .sources(Collections.singletonList(ArtifactSource.builder()
                                                 .name(MigratorUtility.generateName(artifactStream.getName()))
                                                 .identifier(MigratorUtility.generateIdentifier(
                                                     artifactStream.getName(), inputDTO.getIdentifierCaseFormat()))
                                                 .template(templateLinkConfig)
                                                 .build()))
          .build();
    } else {
      CustomArtifactStream.Script primaryScript = customArtifactStream.getScripts().get(0);
      return PrimaryArtifact.builder()
          .sourceType(ArtifactSourceType.CUSTOM_ARTIFACT)
          .spec(CustomArtifactConfig.builder()
                    .timeout(ParameterField.createValueField(Timeout.fromString(primaryScript.getTimeout() + "s")))
                    .scripts(CustomArtifactScripts.builder()
                                 .fetchAllArtifacts(
                                     FetchAllArtifacts.builder()
                                         .artifactsArrayPath(ParameterField.createValueField(
                                             primaryScript.getCustomRepositoryMapping().getArtifactRoot()))
                                         .versionPath(ParameterField.createValueField(
                                             primaryScript.getCustomRepositoryMapping().getBuildNoPath()))
                                         .attributes(
                                             ListUtils
                                                 .emptyIfNull(
                                                     primaryScript.getCustomRepositoryMapping().getArtifactAttributes())
                                                 .stream()
                                                 .map(attribute
                                                     -> StringNGVariable.builder()
                                                            .name(attribute.getMappedAttribute())
                                                            .type(NGVariableType.STRING)
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

  @Override
  public ArtifactType getArtifactType(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream) {
    return ArtifactType.CUSTOM_ARTIFACT;
  }

  @Override
  public ArtifactTypeSpec getTriggerSpec(Map<CgEntityId, CgEntityNode> entities, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities, Trigger trigger) {
    return null;
  }

  private JsonNode generateInput(Map<CgEntityId, CgEntityNode> entities, CustomArtifactStream customArtifactStream) {
    CgEntityId cgEntityId = CgEntityId.builder().id(customArtifactStream.getTemplateUuid()).type(TEMPLATE).build();
    if (!entities.containsKey(cgEntityId)) {
      return null;
    }

    List<Variable> variables = ListUtils.emptyIfNull(customArtifactStream.getTemplateVariables());
    Map<String, String> varMap =
        variables.stream()
            .filter(variable -> StringUtils.isNoneBlank(variable.getName(), variable.getValue()))
            .collect(Collectors.toMap(Variable::getName, Variable::getValue));

    Template template = (Template) entities.get(cgEntityId).getEntity();
    ObjectMapper mapper = new ObjectMapper();

    ArrayNode inputs = mapper.createArrayNode();
    if (isNotEmpty(template.getVariables())) {
      template.getVariables().stream().filter(v -> StringUtils.isBlank(v.getValue())).forEach(v -> {
        ObjectNode variable = mapper.createObjectNode();
        variable.put("name", v.getName());
        variable.put("type", "String");
        variable.put("value", varMap.getOrDefault(v.getName(), RUNTIME_INPUT));
        inputs.add(variable);
      });
    }
    ObjectNode spec = mapper.createObjectNode();
    spec.put("version", RUNTIME_INPUT);
    spec.set("inputs", inputs);

    ObjectNode templateInputs = mapper.createObjectNode();
    templateInputs.put("type", "CustomArtifact");
    templateInputs.set("spec", spec);
    return templateInputs;
  }
}
