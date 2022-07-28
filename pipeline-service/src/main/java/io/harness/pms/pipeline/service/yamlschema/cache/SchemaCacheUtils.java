/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema.cache;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class SchemaCacheUtils {
  public PartialSchemaDTO getPartialSchemaDTO(PartialSchemaDTOValue partialSchemaValue) {
    JsonNode node = JsonUtils.readTree(partialSchemaValue.getSchema());
    return PartialSchemaDTO.builder()
        .schema(node)
        .namespace(partialSchemaValue.getNamespace())
        .nodeName(partialSchemaValue.getNodeName())
        .nodeType(partialSchemaValue.getNodeType())
        .moduleType(partialSchemaValue.getModuleType())
        .build();
  }

  public PartialSchemaDTOValue getPartialSchemaValue(PartialSchemaDTO partialSchemaDTO) {
    return PartialSchemaDTOValue.builder()
        .schema(partialSchemaDTO.getSchema().toString())
        .namespace(partialSchemaDTO.getNamespace())
        .nodeName(partialSchemaDTO.getNodeName())
        .nodeType(partialSchemaDTO.getNodeType())
        .moduleType(partialSchemaDTO.getModuleType())
        .build();
  }

  public List<PartialSchemaDTO> getPartialSchemaDTOList(PartialSchemaDTOWrapperValue partialSchemaWrapperValue) {
    return partialSchemaWrapperValue.getPartialSchemaValueList()
        .stream()
        .map(SchemaCacheUtils::getPartialSchemaDTO)
        .collect(Collectors.toList());
  }

  public PartialSchemaDTOWrapperValue getPartialSchemaWrapperValue(List<PartialSchemaDTO> partialSchemaDTOS) {
    return PartialSchemaDTOWrapperValue.builder()
        .partialSchemaValueList(
            partialSchemaDTOS.stream().map(SchemaCacheUtils::getPartialSchemaValue).collect(Collectors.toList()))
        .build();
  }

  public YamlSchemaDetailsValue toYamlSchemaWithDetailsCache(YamlSchemaWithDetails yamlSchemaWithDetails) {
    return YamlSchemaDetailsValue.builder()
        .schema(yamlSchemaWithDetails.getSchema().toString())
        .schemaClassName(yamlSchemaWithDetails.getSchemaClassName())
        .yamlSchemaMetadata(yamlSchemaWithDetails.getYamlSchemaMetadata())
        .moduleType(yamlSchemaWithDetails.getModuleType())
        .isAvailableAtOrgLevel(yamlSchemaWithDetails.isAvailableAtOrgLevel())
        .isAvailableAtAccountLevel(yamlSchemaWithDetails.isAvailableAtAccountLevel())
        .isAvailableAtProjectLevel(yamlSchemaWithDetails.isAvailableAtProjectLevel())
        .build();
  }

  public YamlSchemaWithDetails toYamlSchemaWithDetails(YamlSchemaDetailsValue yamlSchemaWithDetailsCache) {
    JsonNode node = JsonUtils.readTree(yamlSchemaWithDetailsCache.getSchema());
    return YamlSchemaWithDetails.builder()
        .schema(node)
        .schemaClassName(yamlSchemaWithDetailsCache.getSchemaClassName())
        .yamlSchemaMetadata(yamlSchemaWithDetailsCache.getYamlSchemaMetadata())
        .moduleType(yamlSchemaWithDetailsCache.getModuleType())
        .isAvailableAtOrgLevel(yamlSchemaWithDetailsCache.isAvailableAtOrgLevel())
        .isAvailableAtAccountLevel(yamlSchemaWithDetailsCache.isAvailableAtAccountLevel())
        .isAvailableAtProjectLevel(yamlSchemaWithDetailsCache.isAvailableAtProjectLevel())
        .build();
  }

  public YamlSchemaDetailsWrapperValue toYamlSchemaDetailCacheValue(YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper) {
    return YamlSchemaDetailsWrapperValue.builder()
        .yamlSchemaWithDetailsList(yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList()
                                       .stream()
                                       .map(SchemaCacheUtils::toYamlSchemaWithDetailsCache)
                                       .collect(Collectors.toList()))
        .build();
  }

  public YamlSchemaDetailsWrapper toYamlSchemaDetailsWrapper(YamlSchemaDetailsWrapperValue yamlSchemaDetailsWrapper) {
    return YamlSchemaDetailsWrapper.builder()
        .yamlSchemaWithDetailsList(yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList()
                                       .stream()
                                       .map(SchemaCacheUtils::toYamlSchemaWithDetails)
                                       .collect(Collectors.toList()))
        .build();
  }
}
