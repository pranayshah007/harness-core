/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.exception.JsonSchemaException;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pipeline.yamlschema.YamlSchemaServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.helpers.YamlSchemaMergeHelper;
import io.harness.yaml.schema.YamlSchemaProvider;
import lombok.extern.slf4j.Slf4j;

import static io.harness.annotations.dev.HarnessTeam.CDC;

@Singleton
@Slf4j
@OwnedBy(CDC)
public class NGTemplateSchemaServiceImpl implements NGTemplateSchemaService {
    @Inject private YamlSchemaServiceClient yamlSchemaServiceClient;
    @Inject private YamlSchemaProvider yamlSchemaProvider;

    @Override
    public JsonNode getTemplateSchema(String accountIdentifier, String projectIdentifier, String orgIdentifier, String yamlGroup, Scope scope, EntityType entityType, TemplateEntityType templateEntityType) {
        try {
            return getTemplateYamlSchemaInternal(accountIdentifier, projectIdentifier, orgIdentifier, yamlGroup, scope, entityType, templateEntityType);
        } catch (Exception e) {
            log.error("[Template] Failed to get pipeline yaml schema", e);
            throw new JsonSchemaException(e.getMessage());
        }
    }

    private JsonNode getTemplateYamlSchemaInternal(String accountIdentifier, String projectIdentifier, String orgIdentifier, String yamlGroup, Scope scope, EntityType entityType, TemplateEntityType templateEntityType) {

        if(!schemaValidationSupported(templateEntityType)){
            return null;
        }

        JsonNode templateSchema =
                yamlSchemaProvider.getYamlSchema(EntityType.TEMPLATE, orgIdentifier, projectIdentifier, scope);

        //TODO: add a handler here to fetch for schemas that we can't get from pipeline as discussed. and refactor
        JsonNode specSchema = NGRestUtils
                .getResponse(yamlSchemaServiceClient.getYamlSchema(accountIdentifier, orgIdentifier, projectIdentifier, yamlGroup, entityType, scope)).getSchema();

        YamlSchemaMergeHelper.mergeYamlSchema(templateSchema, specSchema, entityType, templateEntityType);
        return templateSchema;
    }

    private boolean schemaValidationSupported(TemplateEntityType templateEntityType){
        switch (templateEntityType){
            case MONITORED_SERVICE_TEMPLATE:
                return false;
            case PIPELINE_TEMPLATE:
            case STEP_TEMPLATE:
            case STAGE_TEMPLATE:
                return true;
        }
        return false;
    }
}
