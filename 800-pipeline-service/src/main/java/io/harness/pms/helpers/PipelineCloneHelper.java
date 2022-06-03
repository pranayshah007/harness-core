/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.exception.InvalidRequestException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.pipeline.ClonePipelineDTO;
import io.harness.pms.pipeline.DestinationPipelineConfig;
import io.harness.pms.pipeline.SourceIdentifierConfig;
import io.harness.pms.rbac.PipelineRbacPermissions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class PipelineCloneHelper {
  private final AccessControlClient accessControlClient;

  public void checkAccess(ClonePipelineDTO clonePipelineDTO, String accountId) {
    DestinationPipelineConfig destIdentifierConfig = clonePipelineDTO.getDestinationConfig();
    SourceIdentifierConfig sourceIdentifierConfig = clonePipelineDTO.getSourceConfig();

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, destIdentifierConfig.getOrgIdentifier(),
                                                  destIdentifierConfig.getProjectIdentifier()),
        Resource.of("PIPELINE", destIdentifierConfig.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, sourceIdentifierConfig.getOrgIdentifier(),
                                                  sourceIdentifierConfig.getProjectIdentifier()),
        Resource.of("PIPELINE", sourceIdentifierConfig.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_VIEW);
  }

  public String updatePipelineMetadataInSourceYaml(
      ClonePipelineDTO clonePipelineDTO, String sourcePipelineEntityYaml, String accountId) {
    String destOrgId = clonePipelineDTO.getDestinationConfig().getOrgIdentifier();
    String destProjectId = clonePipelineDTO.getDestinationConfig().getProjectIdentifier();
    String destPipelineName = clonePipelineDTO.getDestinationConfig().getPipelineName();

    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    JsonNode jsonNode;
    try {
      jsonNode = objectMapper.readTree(sourcePipelineEntityYaml);
    } catch (JsonProcessingException e) {
      log.error(String.format("Error while processing source yaml to json for pipeline [%s]",
                    clonePipelineDTO.getSourceConfig().getPipelineIdentifier()),
          e);
      throw new InvalidRequestException(
          String.format("Generic Backend Error occurred for pipeline [%s] org [%s] project [%s]",
              clonePipelineDTO.getSourceConfig().getPipelineIdentifier(),
              clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
              clonePipelineDTO.getSourceConfig().getProjectIdentifier()),
          e);
    }

    // Resolve source yaml Params
    if (destProjectId != null && !destProjectId.equals(clonePipelineDTO.getSourceConfig().getProjectIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(jsonNode.get("pipeline"), "projectIdentifier", destProjectId);
    }
    if (destOrgId != null && !destOrgId.equals(clonePipelineDTO.getSourceConfig().getOrgIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(jsonNode.get("pipeline"), "orgIdentifier", destOrgId);
    }
    if (clonePipelineDTO.getDestinationConfig().getPipelineIdentifier() != null
        && !clonePipelineDTO.getDestinationConfig().getPipelineIdentifier().equals(
            clonePipelineDTO.getSourceConfig().getPipelineIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("pipeline"), "identifier", clonePipelineDTO.getDestinationConfig().getPipelineIdentifier());
    }
    if (clonePipelineDTO.getDestinationConfig().getDescription() != null) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("pipeline"), "description", clonePipelineDTO.getDestinationConfig().getDescription());
    }
    if (clonePipelineDTO.getDestinationConfig().getTags() != null) {
      Map<String, String> tags = clonePipelineDTO.getDestinationConfig().getTags();
      ObjectMapper jsonMapper = new ObjectMapper();
      ((ObjectNode) jsonNode.get("pipeline")).set("tags", jsonMapper.convertValue(tags, JsonNode.class));
    }
    if (destPipelineName != null) {
      JsonNodeUtils.updatePropertyInObjectNode(jsonNode.get("pipeline"), "name", destPipelineName);
    } else {
      log.error(String.format("Error Destination Pipeline is null for pipeline [%s]",
          clonePipelineDTO.getDestinationConfig().getPipelineIdentifier()));
      throw new InvalidRequestException(String.format("Destination Pipeline Name should not be null for pipeline [%s]",
          clonePipelineDTO.getDestinationConfig().getPipelineIdentifier()));
    }

    String modifiedSourceYaml;
    try {
      modifiedSourceYaml = new YAMLMapper().writeValueAsString(jsonNode);
    } catch (IOException e) {
      log.error(String.format("Unable to convert json to yaml for pipeline [%s] org [%s] project [%s]",
                    clonePipelineDTO.getSourceConfig().getPipelineIdentifier(),
                    clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
                    clonePipelineDTO.getSourceConfig().getProjectIdentifier()),
          e);
      throw new InvalidRequestException(
          String.format("Generic Backend Error occurred for pipeline [%s] org [%s] project [%s]",
              clonePipelineDTO.getSourceConfig().getPipelineIdentifier(),
              clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
              clonePipelineDTO.getSourceConfig().getProjectIdentifier()),
          e);
    }
    return modifiedSourceYaml;
  }
}
