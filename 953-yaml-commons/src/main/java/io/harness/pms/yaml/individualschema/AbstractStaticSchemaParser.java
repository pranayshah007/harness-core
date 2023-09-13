/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.individualschema;

import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_STRING_PREFIX;
import static io.harness.yaml.schema.beans.SchemaConstants.HASH_SYMBOL;
import static io.harness.yaml.schema.beans.SchemaConstants.PROPERTIES_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.REF_NODE;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.yaml.schema.beans.SchemaConstants;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/***
 * checkIfRootNodeAndAddIntoFqnToNodeMap: Each implementation of this abstract class must implement this method and
 * define the rules through which any node would be considered as rootNode. And then for all those root nodes only, the
 * individual schema will be initialised.
 *
 * nodeToResolvedSchemaMap: All initialised individual schema nodes will be stored in this map.
 */
public abstract class AbstractStaticSchemaParser implements SchemaParserInterface {
  // This fqnToNodeMap map has the raw unresolved values of ObjectNode for any fqn present in the complete schema. eg:
  // pipeline.stages.cd.DeploymentStageNode, pipeline.steps.ci.RunStepInfo
  Map<String, ObjectNodeWithMetadata> fqnToNodeMap = new HashMap<>();
  private boolean isInitialised;

  Map<String, ObjectNode> nodeToResolvedSchemaMap =
      new HashMap<>(); // It contains the complete resolved schema for a node. A node can be for any
                       // step/stage/stepGroup or pipeline or for any object like matrix/strategy.

  // This is the input root schema JsonNode. This will never change is value and will be the source of truth. All the
  // processing happens on top of this schema.
  JsonNode rootSchemaJsonNode;

  abstract void init(JsonNode rootSchemaNode);

  abstract IndividualSchemaGenContext getIndividualSchemaGenContext();

  abstract void checkIfRootNodeAndAddIntoFqnToNodeMap(
      String currentFqn, String childNodeRefValue, ObjectNode objectNode);

  public AbstractStaticSchemaParser getInstance(JsonNode rootSchemaNode) {
    if (!isInitialised) {
      rootSchemaJsonNode = rootSchemaNode;
      init(rootSchemaNode);
      isInitialised = true;
    }
    return this;
  }

  void traverseNodeAndExtractAllRefsRecursively(JsonNode jsonNode, String currentFqn) {
    if (jsonNode == null) {
      return;
    }
    // Calling the method recursively for array and object only. For primitives we don't need to handle specially.
    if (jsonNode.isArray()) {
      for (JsonNode arrayElement : jsonNode) {
        traverseNodeAndExtractAllRefsRecursively(arrayElement, currentFqn);
      }
    } else if (jsonNode instanceof ObjectNode) {
      for (Iterator<Map.Entry<String, JsonNode>> it = jsonNode.fields(); it.hasNext();) {
        Map.Entry<String, JsonNode> entryIterator = it.next();
        String childFieldName = entryIterator.getKey();
        JsonNode childNode = entryIterator.getValue();
        // PROPERTIES_NODE contains all fields so calling the method recursively. And this is internal node so its name
        // should not be appended in the currentFqn.
        if (childFieldName.equals(PROPERTIES_NODE)) {
          traverseNodeAndExtractAllRefsRecursively(childNode, currentFqn);
          continue;
        }
        // The REF_NODE is the textNode having the reference path to some other definition. So we get the referenced
        // node using getReferredJsonNodeByPath method and call this method recursively on the referenced node.
        if (childFieldName.equals(REF_NODE)) {
          // This conditions makes sure that only those refs that are present in our schema will be traversed.
          if (!childNode.asText().startsWith(DEFINITIONS_STRING_PREFIX)) {
            continue;
          }
          // Skip if the referenceNode has already been traversed.
          if (fqnToNodeMap.containsKey(childNode.asText())) {
            continue;
          }
          JsonNode referredNode = getReferredJsonNodeByPath(childNode.asText());
          if (referredNode != null) {
            // Process the node recursively so that all nested refs inside this node would also be traversed.
            traverseNodeAndExtractAllRefsRecursively(referredNode, childNode.asText());
            checkIfRootNodeAndAddIntoFqnToNodeMap(currentFqn, childNode.asText(), (ObjectNode) referredNode);
          }
        } else {
          traverseNodeAndExtractAllRefsRecursively(childNode, currentFqn + "/" + childFieldName);
        }
      }
    }
  }

  JsonNode getReferredJsonNodeByPath(String path) {
    if (EmptyPredicate.isEmpty(path)) {
      return null;
    }
    JsonNode currentJsonNode = rootSchemaJsonNode;
    String[] pathComponents = path.split("/");
    StringBuilder currentPath = new StringBuilder();
    int index = 0;
    if (HASH_SYMBOL.equals(pathComponents[0])) {
      currentPath.append(HASH_SYMBOL);
      index++;
    }
    for (; index < pathComponents.length; index++) {
      if (currentJsonNode == null) {
        break;
      }
      currentJsonNode = currentJsonNode.get(pathComponents[index]);
      currentPath.append('/').append(pathComponents[index]);
      // Here we are adding the intermediate schema paths into the fqnToNodeMap. Example if complete path is
      // pipeline.stages.cd.DeploymentStepInfo then pipeline.stages.cd and pipeline.stages keys would be present in the
      // map.
      if (!fqnToNodeMap.containsKey(currentPath.toString())) {
        fqnToNodeMap.put(
            currentPath.toString(), ObjectNodeWithMetadata.builder().objectNode((ObjectNode) currentJsonNode).build());
      }
    }
    return currentJsonNode;
  }

  private JsonNode getFieldNode(JsonNode jsonNode, String targetFieldName) {
    if (jsonNode == null) {
      return null;
    }
    Iterator<String> fieldNames = jsonNode.fieldNames();
    for (Iterator<String> it = fieldNames; it.hasNext();) {
      String fieldName = it.next();
      if (fieldName.equals(targetFieldName)) {
        return jsonNode.get(fieldName);
      }
      if (JsonPipelineUtils.isObjectTypeField(jsonNode, fieldName)) {
        JsonNode resultNode = getFieldNode(jsonNode.get(fieldName), targetFieldName);
        if (resultNode != null) {
          return resultNode;
        }
      }
      if (JsonPipelineUtils.isArrayNodeField(jsonNode, fieldName)) {
        ArrayNode elements = (ArrayNode) jsonNode.get(fieldName);
        for (int i = 0; i < elements.size(); i++) {
          JsonNode resultNode = getFieldNode(elements.get(i), targetFieldName);
          if (resultNode != null) {
            return resultNode;
          }
        }
      }
    }
    return null;
  }

  /***
   * @param objectNode Input objectNode that might have some unresolved references
   * @param individualSchemaMetadata metadata for the objectNode for which we need to generate the individual schema.
   * @return schema for the requested node.
   */
  void initIndividualSchema(ObjectNode objectNode, IndividualSchemaMetadata individualSchemaMetadata) {
    String schemaKey = individualSchemaMetadata.generateSchemaKey();
    if (nodeToResolvedSchemaMap.containsKey(schemaKey)) {
      return;
    }
    ObjectNode finalSchema = objectNode.deepCopy();
    IndividualSchemaGenContext context = getIndividualSchemaGenContext();
    resolveRefsInNodeRecursively(finalSchema, objectNode, context);
    finalSchema.set(SchemaConstants.SCHEMA_NODE, TextNode.valueOf(SchemaConstants.JSON_SCHEMA_7));
    nodeToResolvedSchemaMap.put(schemaKey, finalSchema);
  }

  /***
   * @param rootIndividualSchemaNode The root node for the individualSchema. All the referenced entities will be added
   *     to its definitions.
   * @param currentJsonNode The current node which is being traversed. It does not change during the traversal.
   * @param context The context for individual schema generation. This has scope until schema is created.
   */
  private void resolveRefsInNodeRecursively(
      ObjectNode rootIndividualSchemaNode, final JsonNode currentJsonNode, IndividualSchemaGenContext context) {
    if (currentJsonNode == null) {
      return;
    }
    if (currentJsonNode.isArray()) {
      for (JsonNode arrayElement : currentJsonNode) {
        resolveRefsInNodeRecursively(rootIndividualSchemaNode, arrayElement, context);
      }
    }
    if (currentJsonNode instanceof ObjectNode) {
      for (Iterator<Map.Entry<String, JsonNode>> it = currentJsonNode.fields(); it.hasNext();) {
        Map.Entry<String, JsonNode> entryIterator = it.next();
        String childFieldName = entryIterator.getKey();
        if (childFieldName.equals(DEFINITIONS_NODE)) {
          continue;
        }
        JsonNode childNode = entryIterator.getValue();
        if (childFieldName.equals(REF_NODE)) {
          if (!childNode.asText().startsWith(DEFINITIONS_STRING_PREFIX)) {
            continue;
          }
          if (context.getResolvedFqnSet().contains(childNode.asText())) {
            continue;
          }
          JsonNode resolvedRefNode = getJsonNodeByPathForIndividualSchema(childNode.asText(), context);
          if (resolvedRefNode != null) {
            // Adding into the context map so that when this same node comes next time, we will not process again.
            context.addFqnIntoResolvedFqnMap(childNode.asText());
            // Adding the referenced node definitions into the individual schema.
            addDefinitionNodeIntoSchema(rootIndividualSchemaNode, resolvedRefNode, childNode.asText());
            // Process the node recursively so that all nested refs inside this node would also be processed.
            resolveRefsInNodeRecursively(rootIndividualSchemaNode, resolvedRefNode, context);
          }
        } else {
          resolveRefsInNodeRecursively(rootIndividualSchemaNode, childNode, context);
        }
      }
    }
  }

  /***
   * @param jsonNode The root node for IndividualSchema. New node will be added into this schema node.
   * @param defNode The definitionNode that was being referenced in the IndividualSchema jsonNode so its definitions
   *     need to be added in the final schema.
   * @param path The path to the defNode from the starting of the complete schema root node.
   */
  private void addDefinitionNodeIntoSchema(ObjectNode jsonNode, JsonNode defNode, String path) {
    String[] pathComponents = path.split("/");
    for (int i = 1; i < pathComponents.length - 1; i++) {
      // If the intermediate node is not present then add an empty node. Example: if path is
      // `pipeline.stages.cd.DeploymentStageConfig` then if `pipeline.stages` does not have `cd` field then add an empty
      // field for key `cd`
      if (!jsonNode.has(pathComponents[i])) {
        jsonNode.set(pathComponents[i], JsonPipelineUtils.readTree("  {}"));
      }
      jsonNode = (ObjectNode) jsonNode.get(pathComponents[i]);
    }
    jsonNode.set(pathComponents[pathComponents.length - 1], defNode);
  }

  /***
   * @param path This is the original ref path for the referenced node.
   * @param context We maintain a schema generation context to keep track of which nodes have been already traversed and
   *     the root jsonNode for individual schema.
   * @return returns the JsonNode present at the ref `path`
   */
  private JsonNode getJsonNodeByPathForIndividualSchema(String path, IndividualSchemaGenContext context) {
    if (EmptyPredicate.isEmpty(path)) {
      return null;
    }
    JsonNode jsonNode = context.getRootSchemaNode();
    String[] pathComponents = path.split("/");
    int index = 0;
    if (HASH_SYMBOL.equals(pathComponents[0])) {
      index++;
    }
    for (; index < pathComponents.length; index++) {
      if (jsonNode == null) {
        break;
      }
      jsonNode = jsonNode.get(pathComponents[index]);
    }
    return jsonNode;
  }

  // This method acts as an interface to get the individual schema for any node.
  @Override
  public ObjectNode getIndividualSchema(IndividualSchemaRequest individualSchemaRequest) {
    if (!isInitialised) {
      throw new InvalidRequestException("Parser not yet initialised.");
    }
    String schemaKey = individualSchemaRequest.getIndividualSchemaMetadata().generateSchemaKey();
    return nodeToResolvedSchemaMap.get(schemaKey);
  }

  public JsonNode getFieldNode(InputFieldMetadata inputFieldMetadata, IndividualSchemaRequest schemaRequest) {
    String[] fqnParts = inputFieldMetadata.getFqnFromParentNode().split("\\.");
    // Here fqnParts[0] would be the parent node type. Like in fqn step.spec.url, the fqnParts[0] would be step.
    String fieldPath = schemaRequest.getIndividualSchemaMetadata().generateSchemaKey();
    JsonNode targetFieldNode = fqnToNodeMap.get(fieldPath).getObjectNode();
    for (int i = 1; i < fqnParts.length; i++) {
      targetFieldNode = getFieldNode(targetFieldNode, fqnParts[i]);
      if (targetFieldNode.has(REF_NODE)) {
        String ref = targetFieldNode.get(REF_NODE).asText();
        targetFieldNode = fqnToNodeMap.get(ref).getObjectNode();
      }
    }
    return targetFieldNode;
  }

  String getTypeFromObjectNode(ObjectNode objectNode) {
    if (JsonPipelineUtils.isPresent(objectNode, SchemaConstants.PROPERTIES_NODE)
        && objectNode.get(SchemaConstants.PROPERTIES_NODE).get(SchemaConstants.TYPE_NODE) != null) {
      JsonNode typeEnumArray =
          objectNode.get(SchemaConstants.PROPERTIES_NODE).get(SchemaConstants.TYPE_NODE).get(SchemaConstants.ENUM_NODE);
      if (typeEnumArray != null && typeEnumArray.size() > 0) {
        return typeEnumArray.get(0).asText();
      }
    }
    return null;
  }
}