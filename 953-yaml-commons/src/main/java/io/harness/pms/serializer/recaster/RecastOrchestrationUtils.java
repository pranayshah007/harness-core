/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.serializer.recaster;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.core.Recast;
import io.harness.core.Recaster;
import io.harness.core.RecasterOptions;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exceptions.RecasterException;
import io.harness.packages.HarnessPackages;
import io.harness.pms.yaml.ParameterDocumentField;
import io.harness.pms.yaml.ParameterDocumentFieldMapper;
import io.harness.pms.yaml.ParameterField;
import io.harness.reflection.HarnessReflections;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.recaster.JsonObjectRecastTransformer;
import io.harness.serializer.recaster.ParameterFieldRecastTransformer;
import io.harness.serializer.recaster.proto.ProtoEnumRecastTransformer;
import io.harness.serializer.recaster.proto.ProtoRecastTransformer;
import io.harness.utils.RecastReflectionUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;
import org.json.JSONException;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class RecastOrchestrationUtils {
  private static final Recast recast =
      new Recast(new Recaster(RecasterOptions.builder().workWithMaps(true).build()), new HashSet<>());

  static {
    recast.registerAliases(HarnessReflections.get(), HarnessPackages.IO_HARNESS, HarnessPackages.SOFTWARE_WINGS);
    recast.addTransformer(new JsonObjectRecastTransformer());
    recast.addTransformer(new ProtoRecastTransformer());
    recast.addTransformer(new ProtoEnumRecastTransformer());
    recast.addTransformer(new ParameterFieldRecastTransformer());
  }

  public <T> Map<String, Object> toMap(T entity) {
    return recast.toMap(entity);
  }

  public <T> byte[] toBytes(T entity) {
    return toJson(entity).getBytes(StandardCharsets.UTF_8);
  }

  public <T> T fromBytes(byte[] bytes, Class<T> entityClass) {
    String json = new String(bytes, StandardCharsets.UTF_8);
    return fromJson(json, entityClass);
  }

  public <T> String toJson(T entity) {
    Map<String, Object> map = recast.toMap(entity);
    return toJson(map);
  }

  public String toJson(Map<String, Object> map) {
    return toJsonInternal(map);
  }

  public Map<String, Object> fromJson(String json) {
    if (EmptyPredicate.isEmpty(json)) {
      return null;
    }

    try {
      return JsonUtils.asMap(json);
    } catch (Exception e) {
      throw new RecasterException("Cannot deserialize from json : " + json, e);
    }
  }

  public <T> T fromMap(Map<String, Object> map, Class<T> entityClass) {
    return recast.fromMap(map, entityClass);
  }

  public <T> T fromJson(String json, Class<T> entityClass) {
    if (EmptyPredicate.isEmpty(json)) {
      return null;
    }

    return fromMap(fromJson(json), entityClass);
  }

  public Object getEncodedValue(Map<String, Object> map) {
    return map.get(Recaster.ENCODED_VALUE);
  }

  public Object setEncodedValue(Map<String, Object> map, Object newValue) {
    return map.put(Recaster.ENCODED_VALUE, newValue);
  }

  /**
   * This function removes the uuid field and also the keys added by the recaster framework
   * Currently not being used but can be used if we want to filter uuid and recaster annotation from a given string.
   */
  public <T> String pruneRecasterAdditions(T entity) {
    Map<String, Object> document = recast.toMap(entity);
    return pruneRecasterAdditions(document);
  }

  public String pruneRecasterAdditions(Map<String, Object> document) {
    if (document == null) {
      return null;
    }
    if (document.containsKey(Recaster.RECAST_CLASS_KEY)) {
      return traverseForRemovingRecasterAdditions(document);
    }
    return toJsonInternal(document);
  }

  public <T> String toSimpleJson(T entity) {
    Map<String, Object> document = recast.toMap(entity);
    return toSimpleJson(document);
  }

  private String traverseForRemovingRecasterAdditions(Map<String, Object> document) {
    for (Map.Entry<String, Object> entry : document.entrySet()) {
      if (entry.getKey().equals(Recaster.RECAST_CLASS_KEY)) {
        continue;
      }
      if (entry.getKey().equals(Recaster.ENCODED_VALUE)) {
        return processEncodedValue(document);
      }
      Object obj = checkForRecasterAdditions(entry.getValue());
      if (!Objects.equals(obj, "{}")) {
        document.put(entry.getKey(), obj);
      }
    }
    document.remove("uuid");
    document.remove(Recaster.RECAST_CLASS_KEY);

    return toJsonInternal(document);
  }

  public String toSimpleJson(Map<String, Object> document) {
    if (document == null) {
      return "{}";
    }
    if (document.containsKey(Recaster.RECAST_CLASS_KEY)) {
      return traverse(document);
    }
    return toJsonInternal(document);
  }

  private <T> String toJsonInternal(T object) {
    try {
      return object == null ? null : JsonUtils.asJson(object);
    } catch (Exception e) {
      throw new RecasterException("Cannot serialize to json : " + object, e);
    }
  }

  private String traverse(Map<String, Object> document) {
    for (Map.Entry<String, Object> entry : document.entrySet()) {
      if (entry.getKey().equals(Recaster.RECAST_CLASS_KEY)) {
        continue;
      }
      if (entry.getKey().equals(Recaster.ENCODED_VALUE)) {
        return processEncodedValue(document);
      }
      document.put(entry.getKey(), check(entry.getValue()));
    }
    document.remove(Recaster.RECAST_CLASS_KEY);

    return toJsonInternal(document);
  }

  private List<Object> traverseIterable(Iterable<Object> collection) {
    List<Object> documentList = new ArrayList<>();
    for (Object currentValue : collection) {
      documentList.add(check(currentValue));
    }
    return documentList;
  }

  private Object checkForRecasterAdditions(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Map) {
      Map<String, Object> value1 = (Map<String, Object>) value;
      if (isParameterField(value1)) {
        return handleParameterFieldForPruning(value1);
      }
      if (value1.containsKey(Recaster.ENCODED_VALUE)) {
        return handleEncodeValue(value1);
      }
      traverseForRemovingRecasterAdditions(value1);
    } else if (RecastReflectionUtils.implementsInterface(value.getClass(), Iterable.class)) {
      return traverseIterable((Iterable<Object>) value);
    } else if (needConversion(value.getClass())) {
      value = RecastOrchestrationUtils.toMap(value);
      traverse((Map<String, Object>) value);
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private Object check(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Map) {
      Map<String, Object> value1 = (Map<String, Object>) value;
      if (isParameterField(value1)) {
        return handleParameterField(value1);
      }
      if (value1.containsKey(Recaster.ENCODED_VALUE)) {
        return handleEncodeValue(value1);
      }
      traverse(value1);
    } else if (RecastReflectionUtils.implementsInterface(value.getClass(), Iterable.class)) {
      return traverseIterable((Iterable<Object>) value);
    } else if (needConversion(value.getClass())) {
      value = RecastOrchestrationUtils.toMap(value);
      traverse((Map<String, Object>) value);
    }
    return value;
  }

  private static Object handleEncodeValue(Map<String, Object> value1) {
    Object encodedValue = value1.get(Recaster.ENCODED_VALUE);
    if (encodedValue == null) {
      return null;
    }
    if (encodedValue instanceof String) {
      try {
        return check(fromJson((String) encodedValue));
      } catch (RecasterException ex) {
        return value1.get(Recaster.ENCODED_VALUE);
      } catch (ClassCastException ex) {
        throw new JSONException("Cannot parse encoded value");
      }
    }
    if (needConversion(encodedValue.getClass())) {
      return check(encodedValue);
    }
    return value1.get(Recaster.ENCODED_VALUE);
  }

  private static boolean isParameterField(Map<String, Object> value1) {
    String documentIdentifier = (String) RecastReflectionUtils.getIdentifier(value1);
    return documentIdentifier != null
        && (documentIdentifier.equals(ParameterField.class.getName())
            || documentIdentifier.equals(
                Optional.ofNullable(RecastReflectionUtils.obtainRecasterAliasValueOrNull(ParameterField.class))
                    .orElse("")));
  }

  private Object handleParameterFieldForPruning(Map<String, Object> value1) {
    Object obj = handleParameterField(value1);
    if (obj == null) {
      return null;
    }
    if (obj instanceof String) {
      return obj;
    }
    if (ClassUtils.isPrimitiveOrWrapper(obj.getClass())) {
      return obj;
    }
    if (obj instanceof List) {
      return obj;
    }
    if (obj instanceof String[]) {
      return obj;
    }
    Map<String, Object> value = RecastOrchestrationUtils.toMap(obj);
    traverse(value);
    return value;
  }

  @SuppressWarnings("unchecked")
  private Object handleParameterField(Map<String, Object> value1) {
    Optional<ParameterDocumentField> parameterDocumentField =
        ParameterDocumentFieldMapper.fromParameterFieldMap(value1);
    if (parameterDocumentField.isEmpty()) {
      return null;
    }
    Object jsonFieldValue = parameterDocumentField.get().fetchFinalValue();
    if (jsonFieldValue == null) {
      return null;
    }
    if (RecastReflectionUtils.implementsInterface(jsonFieldValue.getClass(), Iterable.class)) {
      return traverseIterable((Iterable<Object>) jsonFieldValue);
    } else {
      return needConversion(jsonFieldValue.getClass()) ? RecastOrchestrationUtils.toMap(jsonFieldValue)
                                                       : jsonFieldValue;
    }
  }

  @SuppressWarnings("unchecked")
  private String processEncodedValue(Map<String, Object> document) {
    Object encodedValue = document.get(Recaster.ENCODED_VALUE);
    if (encodedValue instanceof Collection) {
      List<Object> objects = traverseIterable((Iterable<Object>) encodedValue);
      return toJsonInternal(objects);
    }
    if (encodedValue instanceof Map) {
      return toJsonInternal(encodedValue);
    }
    return encodedValue.toString();
  }

  private boolean needConversion(Class<?> type) {
    return !(type != null
            && (type == String.class || type == char.class || type == Character.class || type == short.class
                || type == Short.class || type == Integer.class || type == int.class || type == Long.class
                || type == long.class || type == Double.class || type == double.class || type == float.class
                || type == Float.class || type == Boolean.class || type == boolean.class || type == Byte.class
                || type == byte.class || type == Date.class || type == Locale.class || type == Class.class
                || type == UUID.class || type == URI.class || type.isEnum())
        || type == String[].class || type == List.class);
  }
}
