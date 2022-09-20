/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.encryption.Scope;
import io.harness.pms.pipeline.service.yamlschema.exception.YamlSchemaCacheException;
import io.harness.remote.client.NGRestUtils;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.schema.client.YamlSchemaClient;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoteSchemaGetter implements SchemaGetter {
  private final YamlSchemaClient schemaClient;
  private final ModuleType moduleType;
  private final String accountIdentifier;

  public RemoteSchemaGetter(YamlSchemaClient schemaClient, ModuleType moduleType, String accountIdentifier) {
    this.schemaClient = schemaClient;
    this.moduleType = moduleType;
    this.accountIdentifier = accountIdentifier;
  }

  @Override
  public List<PartialSchemaDTO> getSchema(List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    try {
      return NGRestUtils.getResponse(
          schemaClient.get(accountIdentifier, null, null, null,
              YamlSchemaDetailsWrapper.builder().yamlSchemaWithDetailsList(yamlSchemaWithDetailsList).build()),
          format("[PMS] Error while calling %s service", moduleType.name()));
    } catch (Exception e) {
      log.error(format("[PMS] Unable to get %s schema information", moduleType.name()), e);
    }
    return null;
  }

  @Override
  public YamlSchemaDetailsWrapper getSchemaDetails() {
    try {
      return NGRestUtils.getResponse(schemaClient.getSchemaDetails(accountIdentifier, null, null, null),
          format("[PMS] Error while calling %s service", moduleType.name()));
    } catch (Exception e) {
      throw new YamlSchemaCacheException(
          format("[PMS] Unable to get %s schema information", moduleType.name()), e.getCause());
    }
  }

  @Override
  public JsonNode fetchStepYamlSchema(String orgIdentifier, String projectIdentifier, Scope scope,
      EntityType entityType, String yamlGroup, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    try {
      return NGRestUtils.getResponse(
          schemaClient.getStepSchema(accountIdentifier, projectIdentifier, orgIdentifier, scope, entityType, yamlGroup,
              YamlSchemaDetailsWrapper.builder().yamlSchemaWithDetailsList(yamlSchemaWithDetailsList).build()),
          format("[PMS] Error while calling %s service", entityType.getEntityProduct().name()));
    } catch (Exception e) {
      throw new YamlSchemaCacheException(
          format("[PMS] Unable to get %s %s schema information", entityType.getYamlName(), yamlGroup), e.getCause());
    }
  }
}
