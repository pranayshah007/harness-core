/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.snippets.impl;

import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.yaml.snippets.YamlSnippetException;
import io.harness.yaml.snippets.bean.YamlSnippetMetaData;
import io.harness.yaml.snippets.dto.YamlSnippetMetaDataDTO;
import io.harness.yaml.snippets.dto.YamlSnippetsDTO;
import io.harness.yaml.snippets.helper.YamlSnippetHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(DX)
public class YamlSnippetProvider {
  YamlSnippetHelper yamlSnippetHelper;

  /**
   * @param tags
   * @return metadata {@link YamlSnippetMetaDataDTO} which contains all the tags.
   */
  public YamlSnippetsDTO getYamlSnippetMetaData(List<String> tags) {
    Set<YamlSnippetMetaData> yamlSnippetMetaData = getYamlSnippetMetaDataContainingTag(tags);
    if (yamlSnippetMetaData == null) {
      return null;
    }
    final List<YamlSnippetMetaDataDTO> yamlSnippetMetaDataDTOS =
        yamlSnippetMetaData.stream()
            .map(snippet
                -> YamlSnippetMetaDataDTO.builder()
                       .name(snippet.getName())
                       .description(snippet.getDescription())
                       .version(snippet.getVersion())
                       .identifier(yamlSnippetHelper.getIdentifier(snippet))
                       .tags(snippet.getTags())
                       .iconTag(snippet.getIconTag())
                       .build())
            .collect(Collectors.toList());
    return YamlSnippetsDTO.builder().yamlSnippets(yamlSnippetMetaDataDTOS).build();
  }

  private Set<YamlSnippetMetaData> getYamlSnippetMetaDataContainingTag(List<String> tags) {
    if (isEmpty(tags)) {
      return null;
    }
    final Map<String, Set<YamlSnippetMetaData>> tagMap = yamlSnippetHelper.getTagSnippetMap();

    Set<YamlSnippetMetaData> yamlSnippetMetaData = tagMap.getOrDefault(tags.get(0), null);
    if (isEmpty(yamlSnippetMetaData)) {
      return null;
    }
    for (int i = 1; i < tags.size(); i++) {
      yamlSnippetMetaData =
          Sets.intersection(yamlSnippetMetaData, tagMap.getOrDefault(tags.get(i), Collections.emptySet()));
    }
    return yamlSnippetMetaData;
  }

  /**
   * @param snippetIdentifier {@link YamlSnippetMetaDataDTO}
   */
  private YamlSnippetMetaData getYamlSnippetMetaData(String snippetIdentifier) {
    final Map<String, YamlSnippetMetaData> identifierSnippetMap = yamlSnippetHelper.getIdentifierSnippetMap();
    final YamlSnippetMetaData yamlSnippetMetaData = identifierSnippetMap.get(snippetIdentifier);
    if (yamlSnippetMetaData == null) {
      throwNoYamlSnippetFoundException(null);
    }
    return yamlSnippetMetaData;
  }

  /**
   * @param snippetIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param scope
   * @return snippet if no scope given, else updates or removes properties.
   */
  public JsonNode getYamlSnippet(
      String snippetIdentifier, String orgIdentifier, String projectIdentifier, Scope scope) {
    YamlSnippetMetaData yamlSnippetMetaData = getYamlSnippetMetaData(snippetIdentifier);
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    JsonNode genericSnippetNode = null;
    try {
      String snippet = IOUtils.resourceToString(yamlSnippetMetaData.getResourcePath(), StandardCharsets.UTF_8,
          yamlSnippetMetaData.getClass().getClassLoader());
      genericSnippetNode = objectMapper.readTree(snippet);
    } catch (Exception e) {
      throwNoYamlSnippetFoundException(e);
    }

    ObjectNode yamlWithoutWrapper;
    try {
      yamlWithoutWrapper = getObjectNodeRemovingWrapper(genericSnippetNode);

      if (scope == Scope.ACCOUNT && yamlSnippetMetaData.isAvailableAtAccountLevel()) {
        JsonNodeUtils.deletePropertiesInJsonNode(yamlWithoutWrapper, PROJECT_KEY, ORG_KEY);
      } else if (scope == Scope.ORG && yamlSnippetMetaData.isAvailableAtOrgLevel()) {
        JsonNodeUtils.deletePropertiesInJsonNode(yamlWithoutWrapper, PROJECT_KEY);
        if (isNotEmpty(orgIdentifier)) {
          Map<String, String> propertiesToBeModified = new HashMap<>();
          propertiesToBeModified.put(ORG_KEY, orgIdentifier);
          JsonNodeUtils.updatePropertiesInJsonNode(yamlWithoutWrapper, propertiesToBeModified);
        }
      } else if (scope == Scope.PROJECT && yamlSnippetMetaData.isAvailableAtProjectLevel()) {
        Map<String, String> propertiesToBeModified = new HashMap<>();
        if (isNotEmpty(orgIdentifier)) {
          propertiesToBeModified.put(ORG_KEY, orgIdentifier);
        }
        if (isNotEmpty(projectIdentifier)) {
          propertiesToBeModified.put(PROJECT_KEY, projectIdentifier);
        }
        JsonNodeUtils.updatePropertiesInJsonNode(yamlWithoutWrapper, propertiesToBeModified);
      }
    } catch (Exception e) {
      log.warn("Encountered error while modifying snippet", e);
    }
    // returning original snippet even in worst case.
    return genericSnippetNode;
  }

  private void throwNoYamlSnippetFoundException(Exception e) {
    throw new InvalidRequestException("Couldn't find snippet for given identifier.", e);
  }

  ObjectNode getObjectNodeRemovingWrapper(JsonNode genericSnippetNode) throws YamlSnippetException {
    // Assumption is top level will have just one node. (like ConnectorDisconnectHandler:, pipeline: etc)
    // In case we have some other kind of node at root or first level it should be handled here and we can avoid
    // throwing exception.
    if (!genericSnippetNode.isObject()) {
      throw new YamlSnippetException("Snippet isn't expected to have non object node at root level.");
    }

    ObjectNode snippetNode = (ObjectNode) genericSnippetNode;

    if (!snippetNode.fields().next().getValue().isObject()) {
      throw new YamlSnippetException("Snippet isn't expected to have non object node at first level.");
    }
    return (ObjectNode) snippetNode.fields().next().getValue();
  }
}
