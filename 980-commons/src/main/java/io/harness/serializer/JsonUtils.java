/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.reinert.jjschema.v1.JsonSchemaFactory;
import com.github.reinert.jjschema.v1.JsonSchemaV4Factory;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * The Class JsonUtils.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class JsonUtils {
  /**
   * The constant mapperForCloning.
   */
  public static final ObjectMapper mapperForCloning;
  public static final ObjectMapper mapperForInternalUse;
  private static final ObjectMapper mapper;

  static {
    // json-path initialization
    Configuration.setDefaults(new Configuration.Defaults() {
      private final JsonProvider jsonProvider = new JacksonJsonProvider();
      private final MappingProvider mappingProvider = new JacksonMappingProvider();

      @Override
      public JsonProvider jsonProvider() {
        return jsonProvider;
      }

      @Override
      public MappingProvider mappingProvider() {
        return mappingProvider;
      }

      @Override
      public Set<Option> options() {
        return EnumSet.noneOf(Option.class);
      }
    });

    mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
    mapper.setSerializationInclusion(Include.NON_NULL);
    mapper.setSubtypeResolver(new JsonSubtypeResolver(mapper.getSubtypeResolver()));
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new JavaTimeModule());

    mapperForCloning = new ObjectMapper();
    mapperForCloning.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapperForCloning.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    mapperForCloning.setSerializationInclusion(Include.NON_NULL);
    mapperForCloning.enableDefaultTyping();
    mapperForCloning.setSubtypeResolver(new JsonSubtypeResolver(mapperForCloning.getSubtypeResolver()));
    mapperForCloning.registerModule(new Jdk8Module());
    mapperForCloning.registerModule(new GuavaModule());
    mapperForCloning.registerModule(new JavaTimeModule());

    mapperForInternalUse = new ObjectMapper();
    mapperForInternalUse.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapperForInternalUse.setSerializationInclusion(Include.NON_NULL);
    mapperForInternalUse.enableDefaultTyping();
    mapperForInternalUse.setSubtypeResolver(new JsonSubtypeResolver(mapperForCloning.getSubtypeResolver()));
    mapperForInternalUse.registerModule(new Jdk8Module());
    mapperForInternalUse.registerModule(new GuavaModule());
    mapperForInternalUse.registerModule(new JavaTimeModule());
  }

  /**
   * Parses the json.
   *
   * @param json the json
   * @return the document context
   */
  public static DocumentContext parseJson(String json) {
    return JsonPath.parse(json);
  }

  public static <T> T read(Object json, String jsonPath) {
    return JsonPath.read(json, jsonPath);
  }

  /**
   * Json path.
   *
   * @param <T>  the generic type
   * @param ctx  the ctx
   * @param path the path
   * @return the t
   */
  public static <T> T jsonPath(DocumentContext ctx, String path) {
    return ctx.read(path);
  }

  /**
   * Json path.
   *
   * @param <T>  the generic type
   * @param ctx  the ctx
   * @param path the path
   * @param cls  the cls
   * @return the t
   */
  public static <T> T jsonPath(DocumentContext ctx, String path, Class<T> cls) {
    return ctx.read(path, cls);
  }

  /**
   * Json path.
   *
   * @param <T>  the generic type
   * @param json the json
   * @param path the path
   * @return the t
   */
  public static <T> T jsonPath(String json, String path) {
    return JsonPath.read(json, path);
  }

  /**
   * Json path.
   *
   * @param <T>  the generic type
   * @param json the json
   * @param path the path
   * @param cls  the cls
   * @return the t
   */
  public static <T> T jsonPath(String json, String path, Class<T> cls) {
    return JsonPath.parse(json).read(path, cls);
  }

  /**
   * Converts object to json.
   *
   * @param obj Object to be converted.
   * @return json string.
   */
  public static String asJson(Object obj) {
    return asJson(obj, mapper);
  }

  public static String asPrettyJson(Object obj) throws JsonProcessingException {
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
  }

  /**
   * As json.
   *
   * @param obj          the obj
   * @param objectMapper the object mapper
   * @return the string
   */
  public static String asJson(Object obj, ObjectMapper objectMapper) {
    try {
      SimpleFilterProvider filterProvider = new SimpleFilterProvider();
      // Do not fail if no filter is set
      filterProvider.setFailOnUnknownId(false);

      // No filters used in this.
      return objectMapper.writer(filterProvider).writeValueAsString(obj);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  /**
   * Deserializes json string to object of given type.
   *
   * @param <T>            target class type.
   * @param jsonString     json to deserialize.
   * @param classToConvert target class type.
   * @return Deserialized object.
   * This is deprecated as it bypasses all the exception mappers and cast it is as Runtime Exception
   */
  @JsonDeserialize
  @Deprecated
  public static <T> T asObject(String jsonString, Class<T> classToConvert) {
    return asObject(jsonString, classToConvert, mapper);
  }

  /**
   * As object.
   *
   * @param <T>            the generic type
   * @param jsonString     the json string
   * @param classToConvert the class to convert
   * @param objectMapper   the object mapper
   * @return the t
   * This is deprecated as it bypasses all the exception mappers and cast it is as Runtime Exception
   */
  @JsonDeserialize
  @Deprecated
  public static <T> T asObject(String jsonString, Class<T> classToConvert, ObjectMapper objectMapper) {
    try {
      return objectMapper.readValue(jsonString, classToConvert);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  public static <T> T read(String jsonString, Class<T> classToConvert) {
    try {
      return mapper.readValue(jsonString, classToConvert);
    } catch (Exception exception) {
      throw new InvalidRequestException("Couldn't convert jsonString to object", exception);
    }
  }

  /**
   * Deserializes json string to object of given type reference.
   *
   * @param <T>          target class type.
   * @param jsonString   json to deserialize.
   * @param valueTypeRef target class type reference.
   * @return Deserialized object.
   */
  @JsonDeserialize
  public static <T> T asObject(String jsonString, TypeReference<T> valueTypeRef) {
    return asObject(jsonString, valueTypeRef, mapper);
  }

  /**
   * Deserializes json string to object of given type reference.
   *
   * @param <T>          target class type.
   * @param jsonString   json to deserialize.
   * @param valueTypeRef target class type reference.
   * @param objectMapper the object mapper
   * @return Deserialized object.
   */
  @JsonDeserialize
  public static <T> T asObject(String jsonString, TypeReference<T> valueTypeRef, ObjectMapper objectMapper) {
    try {
      return objectMapper.readValue(jsonString, valueTypeRef);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  /**
   * Deserializes json to List of given type.
   *
   * @param <T>          Type of list
   * @param jsonString   json to deserialize.
   * @param valueTypeRef TypeReference for the list.
   * @return deserialized list.
   */
  @JsonDeserialize
  public static <T> List<T> asList(String jsonString, TypeReference<List<T>> valueTypeRef) {
    try {
      if (EmptyPredicate.isEmpty(jsonString)) {
        return Collections.emptyList();
      }

      return mapper.readValue(jsonString, valueTypeRef);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  @JsonDeserialize
  public static Map<String, Object> asMap(String jsonString) {
    try {
      return mapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  /**
   * validates a json string.
   *
   * @param <T>          collection type.
   * @param jsonString   json to deserialize.
   * @param valueTypeRef target class type.
   * @throws JsonParseException   the json parse exception
   * @throws JsonMappingException the json mapping exception
   * @throws IOException          Signals that an I/O exception has occurred.
   */
  public static <T> void validateJson(String jsonString, TypeReference<T> valueTypeRef)
      throws JsonParseException, JsonMappingException, IOException {
    mapper.readValue(jsonString, valueTypeRef);
  }

  /**
   * Clone.
   *
   * @param <T> the generic type
   * @param t   the t
   * @param cls the cls
   * @return the t
   */
  public static <T> T clone(T t, Class<T> cls) {
    String json = asJson(t, mapperForCloning);
    log.debug("Cloning Object - json: {}", json);
    return asObject(json, cls, mapperForCloning);
  }

  /**
   * Json schema.
   *
   * @param clazz the clazz
   * @return the json node
   */
  public static JsonNode jsonSchema(Class<?> clazz) {
    return jsonSchema(mapper, clazz);
  }

  /**
   * Json schema.
   *
   * @param objectMapper the object mapper
   * @param clazz        the clazz
   * @return the json node
   */
  public static JsonNode jsonSchema(ObjectMapper objectMapper, Class<?> clazz) {
    JsonSchemaFactory schemaFactory = new JsonSchemaV4Factory();
    return schemaFactory.createSchema(clazz);
  }

  /**
   * Converts object to jsonNode for advanced processing.
   *
   * @param object the object
   * @return the json node
   */
  public static JsonNode asTree(Object object) {
    return asTree(mapper, object);
  }

  /**
   * Converts object to jsonNode for advanced processing.
   *
   * @param objectMapper the object mapper
   * @param object       the object
   * @return the json node
   */
  public static JsonNode asTree(ObjectMapper objectMapper, Object object) {
    return objectMapper.valueToTree(object);
  }

  /**
   * Converts object to jsonNode for advanced processing.
   *
   * @param json string
   * @return the json node
   */
  public static JsonNode readTree(String json) {
    return readTree(mapper, json);
  }

  /**
   * Converts object to jsonNode for advanced processing.
   *
   * @param objectMapper the object mapper
   * @param json         String
   * @return the json node
   */
  public static JsonNode readTree(ObjectMapper objectMapper, String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      for (StackTraceElement elem : e.getStackTrace()) {
        log.error("Trace: {}", elem);
      }
      throw new RuntimeException(e);
    }
  }

  public static <T> T treeToValue(TreeNode node, Class<T> cls) {
    return treeToValue(mapper, node, cls);
  }

  public static <T> T treeToValue(ObjectMapper objectMapper, TreeNode node, Class<T> cls) {
    try {
      return objectMapper.treeToValue(node, cls);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> jsonNodeToMap(JsonNode node) {
    return mapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  /**
   * Deserializes json string to list of objects of given type.
   *
   * @param <T>            collection type.
   * @param <U>            targetClassType.
   * @param jsonString     json to deserialize.
   * @param collectionType collection type. i.e. List, Set etc.
   * @param classToConvert target class type.
   * @return Deserialized Collection object.
   */
  @JsonDeserialize
  public static <T extends Collection<U>, U> T asObject(
      String jsonString, Class<T> collectionType, Class<U> classToConvert) {
    try {
      return mapper.readValue(
          jsonString, mapper.getTypeFactory().constructCollectionType(collectionType, classToConvert));
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  /**
   * Read resource object.
   *
   * @param file the file
   * @return the object
   */
  public static Object readResource(String file) {
    try {
      URL url = JsonUtils.class.getResource(file);
      String json = Resources.toString(url, Charsets.UTF_8);
      return JsonUtils.asObject(json, HashMap.class);
    } catch (Exception exception) {
      throw new RuntimeException("Error in initializing file-" + file, exception);
    }
  }
  public static Object readFromFile(File file, Class<?> clazz) {
    try {
      return mapper.readValue(file, clazz);
    } catch (Exception exception) {
      throw new RuntimeException("Error reading the file -" + file.getAbsolutePath(), exception);
    }
  }

  public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
    return mapper.convertValue(fromValue, toValueType);
  }

  public static <T> T convertValue(Object fromValue, TypeReference<T> toValueType) {
    return mapper.convertValue(fromValue, toValueType);
  }

  public static String prettifyJsonString(String jsonString) {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    JsonParser jsonParser = new JsonParser();
    JsonElement jsonElement = jsonParser.parse(jsonString);
    return gson.toJson(jsonElement);
  }

  @JsonDeserialize
  public static <T> T asObjectWithExceptionHandlingType(String jsonString, Class<T> classToConvert)
      throws JsonProcessingException {
    return mapper.readValue(jsonString, classToConvert);
  }
}
