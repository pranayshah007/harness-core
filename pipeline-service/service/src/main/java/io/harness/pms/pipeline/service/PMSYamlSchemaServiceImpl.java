/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.pms.yaml.YAMLFieldNameConstants.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.TRIGGER;
import static io.harness.yaml.schema.beans.SchemaConstants.PIPELINE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.STAGES_NODE;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.JsonSchemaValidationException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PipelineVersionConstants;
import io.harness.yaml.individualschema.PipelineSchemaMetadata;
import io.harness.yaml.individualschema.PipelineSchemaParserFactory;
import io.harness.yaml.individualschema.PipelineSchemaRequest;
import io.harness.yaml.individualschema.SchemaParserInterface;
import io.harness.yaml.schema.inputs.InputsSchemaServiceImpl;
import io.harness.yaml.schema.inputs.beans.YamlInputDetails;
import io.harness.yaml.utils.JsonPipelineUtils;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PMSYamlSchemaServiceImpl implements PMSYamlSchemaService {
  public static final long SCHEMA_TIMEOUT = 10;

  private final YamlSchemaValidator yamlSchemaValidator;
  private final PmsYamlSchemaHelper pmsYamlSchemaHelper;
  private final SchemaFetcher schemaFetcher;
  private final InputsSchemaServiceImpl inputsSchemaService;

  private ExecutorService yamlSchemaExecutor;

  @Inject PipelineSchemaParserFactory pipelineSchemaParserFactory;
  Integer allowedParallelStages;

  private final String PIPELINE_VERSION_V0 = "v0";
  private final String PIPELINE_VERSION_V1 = "v1";

  @Inject
  public PMSYamlSchemaServiceImpl(YamlSchemaValidator yamlSchemaValidator, PmsYamlSchemaHelper pmsYamlSchemaHelper,
      SchemaFetcher schemaFetcher, @Named("allowedParallelStages") Integer allowedParallelStages,
      @Named("YamlSchemaExecutorService") ExecutorService executor, InputsSchemaServiceImpl inputsSchemaService) {
    this.yamlSchemaValidator = yamlSchemaValidator;
    this.pmsYamlSchemaHelper = pmsYamlSchemaHelper;
    this.schemaFetcher = schemaFetcher;
    this.allowedParallelStages = allowedParallelStages;
    this.yamlSchemaExecutor = executor;
    this.inputsSchemaService = inputsSchemaService;
  }

  @Override
  public boolean validateYamlSchema(String accountId, String orgId, String projectId, JsonNode jsonNode) {
    // Keeping pipeline yaml schema validation behind ff. If ff is disabled then schema validation will happen. Will
    // remove after finding the root cause of invalid schema generation and fixing it.
    if (!pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DISABLE_PIPELINE_SCHEMA_VALIDATION, accountId)) {
      Future<Boolean> future =
          yamlSchemaExecutor.submit(() -> validateYamlSchemaInternal(accountId, orgId, projectId, jsonNode));
      try (AutoLogContext accountLogContext =
               new AccountLogContext(accountId, AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
        return future.get(SCHEMA_TIMEOUT, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        // If e.getCause() instance of InvalidYamlException then it means we got some legit schema-validation errors and
        // it has error info according to the schema-error-experience.
        if (e.getCause() != null && e.getCause() instanceof io.harness.yaml.validator.InvalidYamlException) {
          throw(io.harness.yaml.validator.InvalidYamlException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      } catch (TimeoutException | InterruptedException e) {
        log.error(format("Timeout while validating schema for accountId: %s, orgId: %s, projectId: %s", accountId,
                      orgId, projectId),
            e);
        // if validation does not happen before timeout, we will skip the validation and allow the operations(Pipeline
        // save/execute).
        return true;
      }
    }
    return true;
  }

  @VisibleForTesting
  boolean validateYamlSchemaInternal(String accountIdentifier, String orgId, String projectId, JsonNode jsonNode) {
    long start = System.currentTimeMillis();
    try {
      JsonNode schema = schemaFetcher.fetchPipelineStaticYamlSchema(PIPELINE_VERSION_V0);

      String schemaString = JsonPipelineUtils.writeJsonString(schema);
      yamlSchemaValidator.validate(jsonNode, schemaString,
          pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DONT_RESTRICT_PARALLEL_STAGE_COUNT, accountIdentifier),
          allowedParallelStages, PIPELINE_NODE + "/" + STAGES_NODE);
      return true;
    } catch (io.harness.yaml.validator.InvalidYamlException e) {
      log.info("[PMS_SCHEMA] Schema validation took total time {}ms", System.currentTimeMillis() - start);
      throw e;
    } catch (Exception ex) {
      if (ex instanceof NullPointerException
          || ex.getCause() != null && ex.getCause() instanceof NullPointerException) {
        log.error(format(
            "Schema validation thrown NullPointerException. Please check the generated schema for account: %s, org: %s, project: %s",
            accountIdentifier, orgId, projectId));
        return false;
      }
      log.error(ex.getMessage(), ex);
      throw new JsonSchemaValidationException(ex.getMessage(), ex);
    }
  }

  @Override
  public void validateUniqueFqn(String yaml) {
    try {
      FQNMapGenerator.generateFQNMap(YamlUtils.readTree(yaml).getNode().getCurrJsonNode());
    } catch (IOException ex) {
      log.error(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
    }
  }

  @Override
  public ObjectNode getStaticSchemaForAllEntities(
      String nodeGroup, String nodeType, String nodeGroupDifferentiator, String version) {
    JsonNode jsonNode;
    switch (nodeGroup) {
      case PIPELINE:
        jsonNode = schemaFetcher.fetchPipelineStaticYamlSchema(version);
        return (ObjectNode) jsonNode;
      case TRIGGER:
        jsonNode = schemaFetcher.fetchTriggerStaticYamlSchema();
        return (ObjectNode) jsonNode;
      default:
        return getIndividualSchema(nodeGroup, nodeType, nodeGroupDifferentiator, version);
    }
  }

  private ObjectNode getIndividualSchema(
      String nodeGroup, String nodeType, String nodeGroupDifferentiator, String version) {
    SchemaParserInterface pipelineSchemaParser = pipelineSchemaParserFactory.getPipelineSchemaParser(version);
    return pipelineSchemaParser.getIndividualSchema(
        PipelineSchemaRequest.builder()
            .individualSchemaMetadata(PipelineSchemaMetadata.builder()
                                          .nodeGroup(nodeGroup)
                                          .nodeGroupDifferentiator(nodeGroupDifferentiator)
                                          .nodeType(nodeType)
                                          .build())
            .build());
  }

  @Override
  @SneakyThrows
  public List<YamlInputDetails> getInputSchemaDetails(String yaml) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    SchemaParserInterface staticSchemaParser = getStaticSchemaParser(yamlConfig);

    return inputsSchemaService.getInputsSchemaRelations(staticSchemaParser, yaml);
  }

  private SchemaParserInterface getStaticSchemaParser(YamlConfig yamlConfig) {
    if (yamlConfig.getYamlMap().get("pipeline") != null) {
      return pipelineSchemaParserFactory.getPipelineSchemaParser(
          PipelineVersionConstants.PIPELINE_VERSION_V0.getValue());
    }

    JsonNode versionNode = yamlConfig.getYamlMap().get("version");
    if (versionNode != null) {
      switch (versionNode.asText()) {
        case "1":
          return pipelineSchemaParserFactory.getPipelineSchemaParser(
              PipelineVersionConstants.PIPELINE_VERSION_V1.getValue());
        default:
          throw new InvalidRequestException("Invalid version found in yaml : " + versionNode.asText());
      }
    } else {
      throw new InvalidRequestException("No version field found in yaml");
    }
  }
}
