/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.exception.JsonSchemaException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.freeze.service.FreezeSchemaService;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_FREEZE})
@Singleton
@Slf4j
@OwnedBy(CDC)
public class FreezeSchemaServiceImpl implements FreezeSchemaService {
  private YamlSchemaProvider yamlSchemaProvider;
  private YamlSchemaValidator yamlSchemaValidator;

  @Inject
  public FreezeSchemaServiceImpl(YamlSchemaProvider yamlSchemaProvider, YamlSchemaValidator yamlSchemaValidator) {
    this.yamlSchemaProvider = yamlSchemaProvider;
    this.yamlSchemaValidator = yamlSchemaValidator;
  }

  @Override
  public JsonNode getFreezeSchema(
      String accountIdentifier, String projectIdentifier, String orgIdentifier, Scope scope) {
    try {
      if (scope == null) {
        scope = NGFreezeDtoMapper.getScopeFromFreezeDto(orgIdentifier, projectIdentifier);
      }
      return yamlSchemaProvider.getYamlSchema(EntityType.FREEZE, orgIdentifier, projectIdentifier, scope);
    } catch (Exception e) {
      log.error("[Freeze] Failed to get freeze yaml schema", e);
      throw new JsonSchemaException(e.getMessage());
    }
  }

  @Override
  public void validateYamlSchema(FreezeConfigEntity freezeConfig) throws IOException {
    String freezeConfigYaml = freezeConfig.getYaml();
    validateYamlSchema(freezeConfigYaml);
  }

  @Override
  public void validateYamlSchema(String freezeConfigYaml) throws IOException {
    long start = System.currentTimeMillis();
    try {
      JsonNode yamlNode = YamlPipelineUtils.getMapper().readTree(freezeConfigYaml);
      Set<ValidationMessage> validationMessages =
          yamlSchemaValidator.validateWithDetailedMessage(freezeConfigYaml, EntityType.FREEZE);
      yamlSchemaValidator.processAndHandleValidationMessage(yamlNode, validationMessages, freezeConfigYaml);
    } catch (io.harness.yaml.validator.InvalidYamlException e) {
      log.info("[FREEZE_SCHEMA] Schema validation took total time {}ms", System.currentTimeMillis() - start);
      throw e;
    } catch (Exception ex) {
      log.error(ex.getMessage(), ex);
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(
                  YamlSchemaErrorDTO.builder().message(ex.getMessage()).fqn("$.freeze").build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(ex.getMessage(), ex, errorWrapperDTO, freezeConfigYaml);
    }
  }
}
