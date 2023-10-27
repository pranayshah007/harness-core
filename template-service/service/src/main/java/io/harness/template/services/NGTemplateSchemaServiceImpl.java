/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SchemaConstants.PIPELINE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.SPEC_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.STAGES_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.TEMPLATE_NODE;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.CGRestUtils;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.utils.TemplateSchemaFetcher;
import io.harness.yaml.individualschema.AbstractStaticSchemaParser;
import io.harness.yaml.individualschema.PipelineSchemaRequest;
import io.harness.yaml.individualschema.TemplateSchemaMetadata;
import io.harness.yaml.individualschema.TemplateSchemaParserFactory;
import io.harness.yaml.schema.client.YamlSchemaClient;
import io.harness.yaml.utils.JsonPipelineUtils;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY, HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(CDC)
public class NGTemplateSchemaServiceImpl implements NGTemplateSchemaService {
  @Inject TemplateSchemaFetcher templateSchemaFetcher;

  @Inject TemplateSchemaParserFactory templateSchemaParserFactory;
  Map<String, YamlSchemaClient> yamlSchemaClientMapper;
  private YamlSchemaValidator yamlSchemaValidator;
  private AccountClient accountClient;
  Integer allowedParallelStages;

  @Inject
  public NGTemplateSchemaServiceImpl(Map<String, YamlSchemaClient> yamlSchemaClientMapper,
      YamlSchemaValidator yamlSchemaValidator, AccountClient accountClient,
      @Named("allowedParallelStages") Integer allowedParallelStages) {
    this.yamlSchemaClientMapper = yamlSchemaClientMapper;
    this.yamlSchemaValidator = yamlSchemaValidator;
    this.accountClient = accountClient;
    this.allowedParallelStages = allowedParallelStages;
  }

  public void validateYamlSchemaInternal(TemplateEntity templateEntity) {
    validateYamlSchema(templateEntity.getAccountIdentifier(), templateEntity.getProjectIdentifier(),
        templateEntity.getOrgIdentifier(), templateEntity.getYaml(), templateEntity.getTemplateScope(),
        templateEntity.getChildType(), templateEntity.getTemplateEntityType(),
        getSchemaVersionFromHarnessYamlVersion(templateEntity.getHarnessVersion()));
  }

  public void validateYamlSchemaInternal(GlobalTemplateEntity globalTemplateEntity) {
    validateYamlSchema(globalTemplateEntity.getAccountIdentifier(), globalTemplateEntity.getProjectIdentifier(),
        globalTemplateEntity.getOrgIdentifier(), globalTemplateEntity.getYaml(),
        globalTemplateEntity.getTemplateScope(), globalTemplateEntity.getChildType(),
        globalTemplateEntity.getTemplateEntityType(),
        getSchemaVersionFromHarnessYamlVersion(globalTemplateEntity.getHarnessVersion()));
  }

  @Override
  public ObjectNode getIndividualStaticSchema(String nodeGroup, String nodeType, String version) {
    AbstractStaticSchemaParser templateSchemaParser = templateSchemaParserFactory.getTemplateSchemaParser(version);
    return templateSchemaParser.getIndividualSchema(
        PipelineSchemaRequest.builder()
            .individualSchemaMetadata(TemplateSchemaMetadata.builder().nodeGroup(nodeGroup).nodeType(nodeType).build())
            .build());
  }

  public boolean isFeatureFlagEnabled(FeatureName featureName, String accountIdentifier, AccountClient accountClient) {
    return CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountIdentifier));
  }

  void validateYamlSchema(String accountIdentifier, String projectIdentifier, String orgIdentifier, String templateYaml,
      Scope templateScope, String childType, TemplateEntityType templateEntityType, String version) {
    long start = System.currentTimeMillis();
    if (isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, accountIdentifier, accountClient)) {
      return;
    }
    try {
      Scope scope = templateScope != null ? templateScope
          : projectIdentifier != null     ? Scope.PROJECT
          : orgIdentifier != null         ? Scope.ORG
                                          : Scope.ACCOUNT;

      String nodeGroup = templateEntityType.getNodeGroup();
      String nodeType = getNodeType(templateEntityType, childType);
      JsonNode schema = getIndividualStaticSchema(nodeGroup, nodeType, version);
      if (EmptyPredicate.isEmpty(schema)) {
        // FallBack logic - If we didn't find 'nodeGroup/nodeType' key in the parser, we will use template.json
        log.warn(String.format(
            "Individual Schema not found for v0 Templates with %s nodeGroup and %s nodeType", nodeGroup, nodeType));
        schema = templateSchemaFetcher.getStaticYamlSchema(version);
      }

      String schemaString = JsonPipelineUtils.writeJsonString(schema);
      if (templateEntityType.equals(TemplateEntityType.PIPELINE_TEMPLATE)) {
        String pathToJsonNode = TEMPLATE_NODE + "/" + SPEC_NODE + "/" + STAGES_NODE;
        yamlSchemaValidator.validate(templateYaml, schemaString,
            isFeatureFlagEnabled(FeatureName.DONT_RESTRICT_PARALLEL_STAGE_COUNT, accountIdentifier, accountClient),
            allowedParallelStages, pathToJsonNode);
      } else {
        yamlSchemaValidator.validate(
            templateYaml, schemaString, true, allowedParallelStages, PIPELINE_NODE + "/" + STAGES_NODE);
      }
    } catch (io.harness.yaml.validator.InvalidYamlException e) {
      log.info("[TEMPLATE_SCHEMA] Schema validation took total time {}ms", System.currentTimeMillis() - start);
      throw e;
    } catch (Exception ex) {
      log.error(ex.getMessage(), ex);
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(
                  YamlSchemaErrorDTO.builder().message(ex.getMessage()).fqn("$.pipeline").build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(ex.getMessage(), ex, errorWrapperDTO, templateYaml);
    }
  }

  private String getNodeType(TemplateEntityType templateEntityType, String childType) {
    Set<TemplateEntityType> templatesWithoutNodeType = new HashSet<>(
        Arrays.asList(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE, TemplateEntityType.MONITORED_SERVICE_TEMPLATE,
            TemplateEntityType.SECRET_MANAGER_TEMPLATE, TemplateEntityType.STEPGROUP_TEMPLATE,
            TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE, TemplateEntityType.PIPELINE_TEMPLATE));
    if (templatesWithoutNodeType.contains(templateEntityType)) {
      return null;
    }
    return childType;
  }

  private String getSchemaVersionFromHarnessYamlVersion(String harnessYamlVersion) {
    if (harnessYamlVersion.equals(HarnessYamlVersion.V0)) {
      return "v0";
    }
    return "v1";
  }
}
