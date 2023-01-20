/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.utils;

import static io.harness.govern.Switch.noop;

import static java.util.Arrays.asList;

import com.esotericsoftware.yamlbeans.YamlConfig;
import com.esotericsoftware.yamlbeans.YamlConfig.WriteClassName;
import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.esotericsoftware.yamlbeans.YamlWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class ObjectYamlUtils {
  private static final YamlConfig yamlConfig = defaultYamlConfig();

  private static YamlConfig defaultYamlConfig() {
    YamlConfig yamlConfig = new YamlConfig();
    yamlConfig.writeConfig.setIndentSize(2);
    yamlConfig.writeConfig.setWriteClassname(WriteClassName.NEVER);
    return yamlConfig;
  }

  private static final String dotMatchRegex = "\\.";
  private static final String encodedDot = "[dot]";
  public static final String YAML_DOCUMENT_DELIMITER = "---";
  public static final String newLineRegex = "\\r?\\n";

  private static boolean isCollection(String str) {
    return str.endsWith("]");
  }

  private static String getFieldName(String str) {
    return str.split("\\[")[0];
  }

  private static String removeFieldName(String str) {
    return str.substring(str.indexOf('['));
  }

  private static boolean isAllItems(String str) {
    return str.endsWith("[]");
  }

  private static int getIndex(String str) {
    // assumes index is valid in format like: containers[1]
    String num = str.split("\\[")[1].split("]")[0];
    return Integer.parseInt(num);
  }

  private static List<String> getKeyList(String key) {
    return Arrays.stream(key.split(dotMatchRegex))
        .map(str -> str.replace(encodedDot, "."))
        .collect(Collectors.toList());
  }

  public static String encodeDot(String input) {
    return input.replace(".", encodedDot);
  }

  public static List<Object> readYaml(String yamlString) throws YamlException {
    YamlReader reader = new YamlReader(yamlString);

    List<Object> result = new ArrayList<>();
    // A YAML can contain more than one YAML document.
    // Call to YamlReader.read() deserializes the next document into an object.
    // YAML documents are delimited by "---"
    while (true) {
      Object o = reader.read();
      if (o == null) {
        break;
      }
      result.add(o);
    }
    return result;
  }

  public static Object tryReadYaml(String yamlString) {
    try {
      return readYaml(yamlString).get(0);
    } catch (YamlException e) {
      noop();
    }
    return null;
  }

  public static String toYaml(Object resource) throws YamlException {
    YamlConfig yamlConfig = new YamlConfig();
    yamlConfig.writeConfig.setAutoAnchor(false);
    yamlConfig.writeConfig.setIndentSize(2);
    yamlConfig.writeConfig.setWriteClassname(WriteClassName.NEVER);

    StringWriter out = new StringWriter();
    YamlWriter yamlWriter = new YamlWriter(out, yamlConfig);

    yamlWriter.write(resource);
    yamlWriter.close();

    return out.toString();
  }

  public static List<String> splitYamlFile(String yamlString) {
    List<String> results = new ArrayList<>();
    String[] lines = yamlString.split(newLineRegex);
    StringBuilder builder = new StringBuilder();

    for (String line : lines) {
      if ((line.length() >= YAML_DOCUMENT_DELIMITER.length()
              && !line.substring(0, YAML_DOCUMENT_DELIMITER.length()).equals(YAML_DOCUMENT_DELIMITER))
          || (line.length() < YAML_DOCUMENT_DELIMITER.length())) {
        builder.append(line).append(System.lineSeparator());
      } else {
        if (!builder.toString().isEmpty()) {
          results.add(builder.toString());
        }
        builder.setLength(0);
      }
    }

    if (!builder.toString().isEmpty()) {
      results.add(builder.toString());
    }

    return results;
  }

  public static Object getField(Object object, String key) {
    if (StringUtils.isBlank(key)) {
      return null;
    }

    if (key.contains("[]")) {
      throw new IllegalArgumentException("key cannot contain array[]. Use getFields instead.");
    }

    return getField(object, getKeyList(key)).stream().findFirst().orElse(null);
  }

  public static List<Object> getFields(Object object, String key) {
    if (StringUtils.isBlank(key)) {
      return Collections.emptyList();
    }
    return getField(object, asList(key.split(dotMatchRegex)));
  }

  private static List<Object> getField(Object object, List<String> inputList) {
    List<Object> result = new ArrayList<>();

    if (object == null) {
      return result;
    }

    if (inputList.isEmpty()) {
      return asList(object);
    }

    List<String> keyList = new ArrayList<>(inputList);

    String item = keyList.get(0);

    if (object instanceof List) {
      if (isCollection(item)) {
        if (isAllItems(item)) {
          for (Object oItem : (List) object) {
            result.addAll(getField(oItem, keyList.subList(1, keyList.size())));
          }
        } else {
          int index = getIndex(item);
          if (index < ((List) object).size()) {
            object = ((List) object).get(index);
            result.addAll(getField(object, keyList.subList(1, keyList.size())));
          }
        }
      }
    } else if (object instanceof Map) {
      if (isCollection(item)) {
        String fieldName = getFieldName(item);
        object = ((Map) object).get(fieldName);
        keyList.set(0, removeFieldName(item));
        result.addAll(getField(object, keyList));
      } else {
        object = ((Map) object).get(item);
        result.addAll(getField(object, keyList.subList(1, keyList.size())));
      }
    }

    return result;
  }

  public static void setField(Object object, String key, Object value) {
    if (!StringUtils.isBlank(key)) {
      UnaryOperator<Object> setValue = t -> value;

      transformField(object, asList(key.split(dotMatchRegex)), setValue);
    }
  }

  public static void transformField(Object object, String key, UnaryOperator<Object> transformer) {
    if (!StringUtils.isBlank(key)) {
      transformField(object, asList(key.split(dotMatchRegex)), transformer);
    }
  }

  private static void transformField(Object object, List<String> inputList, UnaryOperator<Object> transformer) {
    if (object == null || inputList.isEmpty()) {
      return;
    }

    List<String> keyList = new ArrayList<>(inputList);

    String item = keyList.get(0);

    if (object instanceof List) {
      if (isCollection(item)) {
        if (isAllItems(item)) {
          for (int index = 0; index < ((List) object).size(); index++) {
            Object oItem = ((List) object).get(index);
            if (keyList.size() == 1) {
              ((List) object).set(index, transformer.apply(oItem));
            } else {
              transformField(oItem, keyList.subList(1, keyList.size()), transformer);
            }
          }
        } else {
          int index = getIndex(item);
          if (index < ((List) object).size()) {
            object = ((List) object).get(index);
            if (keyList.size() == 1) {
              ((List) object).set(index, transformer.apply(object));
            } else {
              transformField(object, keyList.subList(1, keyList.size()), transformer);
            }
          }
        }
      }
    } else if (object instanceof Map) {
      if (isCollection(item)) {
        String fieldName = getFieldName(item);
        if (keyList.size() == 1) {
          if (isAllItems(item)) {
            ((Map) object).put(fieldName, transformer.apply(((Map) object).get(fieldName)));
          } else {
            int index = getIndex(item);
            List objectList = (List) ((Map) object).get(fieldName);
            if (index < objectList.size()) {
              objectList.set(index, transformer.apply(objectList.get(index)));
            }
          }
        } else {
          object = ((Map) object).get(fieldName);
          keyList.set(0, removeFieldName(item));
          transformField(object, keyList, transformer);
        }
      } else {
        if (keyList.size() == 1) {
          ((Map) object).put(keyList.get(0), transformer.apply(((Map) object).get(keyList.get(0))));
        } else {
          object = ((Map) object).get(item);
          transformField(object, keyList.subList(1, keyList.size()), transformer);
        }
      }
    }
  }
}
