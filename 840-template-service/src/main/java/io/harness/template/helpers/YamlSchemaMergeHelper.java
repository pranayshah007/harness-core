/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.harness.EntityType;
import io.harness.jackson.JsonNodeUtils;
import io.harness.ng.core.template.TemplateEntityType;

import java.util.*;

public class YamlSchemaMergeHelper {

    public static void mergeYamlSchema(JsonNode templateSchema, JsonNode specSchema, EntityType entityType, TemplateEntityType templateEntityType){
        JsonNode nGTemplateInfoConfig = templateSchema.get("definitions").get("NGTemplateInfoConfig");
        Set<String> keys = getKeysToRemoveFromTemplateSpec(templateEntityType);

        //TODO: create constants for these
        if(EntityType.PIPELINES.equals(entityType)){
            //TODO: remove one of for those once we add them in the ticket CDS-.
            String pipelineSpecKey = specSchema.get("properties").get("pipeline").get("$ref").asText();
            JsonNodeUtils.upsertPropertyInObjectNode(nGTemplateInfoConfig.get("properties").get("spec"), "$ref", pipelineSpecKey);
            JsonNode refNode = getJsonNodeViaRef(pipelineSpecKey, specSchema);
            JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) refNode.get("properties"), keys);
            JsonNodeUtils.deletePropertiesInArrayNode((ArrayNode) refNode.get("required"), keys);
            JsonNodeUtils.merge(templateSchema.get("definitions"), specSchema.get("definitions"));
        }else{
            ObjectNode definitionSchema = (ObjectNode) templateSchema.get("definitions");
            definitionSchema.putIfAbsent("specNode",definitionSchema.get("JsonNode").deepCopy());
            JsonNodeUtils.upsertPropertyInObjectNode(nGTemplateInfoConfig.get("properties").get("spec"), "$ref", "#/definitions/specNode");
            JsonNode specJsonNode = templateSchema.get("definitions").get("specNode");
            JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) specSchema.get("properties"), keys);
            JsonNodeUtils.deletePropertiesInArrayNode((ArrayNode) specSchema.get("required"), keys);
            JsonNodeUtils.merge(specJsonNode, specSchema);
            JsonNodeUtils.merge(templateSchema.get("definitions"), specSchema.get("definitions"));
            JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) specJsonNode, "definitions");
        }

    }

    private static JsonNode getJsonNodeViaRef(String ref, JsonNode rootNode){
        ref = ref.subSequence(2,ref.length()).toString();
        String[] orderKeys = ref.split("/");
        JsonNode refNode = rootNode;
        for(String str : orderKeys){
            refNode = refNode.get(str);
        }
        return refNode;
    }

    private static Set<String> getKeysToRemoveFromTemplateSpec(TemplateEntityType templateEntityType){
        switch (templateEntityType){
            case STAGE_TEMPLATE:
            case STEP_TEMPLATE:
                return new HashSet<>(Arrays.asList("name", "identifier", "description", "orgIdentifier", "projectIdentifier", "template"));
            case PIPELINE_TEMPLATE:
                return new HashSet<>(Arrays.asList("name", "identifier", "description", "type", "tags", "orgIdentifier", "projectIdentifier", "template"));
        }
        return new HashSet<>();
    }
}
