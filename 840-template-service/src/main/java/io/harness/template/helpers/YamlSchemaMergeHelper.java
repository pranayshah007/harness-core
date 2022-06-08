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
import com.fasterxml.jackson.databind.node.TextNode;
import io.harness.EntityType;
import io.harness.jackson.JsonNodeUtils;

import java.util.*;

public class YamlSchemaMergeHelper {

    public static void mergeYamlSchema(JsonNode templateSchema, JsonNode specSchema, EntityType entityType){
        JsonNode nGTemplateInfoConfig = templateSchema.get("definitions").get("NGTemplateInfoConfig");
        Set<String> keys = new HashSet<>();
        nGTemplateInfoConfig.get("properties").fieldNames().forEachRemaining(keys::add);
        //todo: we create entity to list tht should be removed from schema

        //TODO: create constants for these
        if(EntityType.PIPELINES.equals(entityType)){
            //TODO: remove one of for those
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

    private static void deletePropertiesInArrayNode(ArrayNode arrayNode, Collection<String> keys) {
        for(int i = 0; i < arrayNode.size(); i++){
            TextNode node = (TextNode) arrayNode.get(i);
            if(keys.contains(node.asText())){
                arrayNode.remove(i);
            }
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
}
