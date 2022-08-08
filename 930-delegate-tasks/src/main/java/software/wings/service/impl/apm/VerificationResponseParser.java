/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.apm;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.exception.WingsException;
import io.harness.expression.RegexFunctor;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class VerificationResponseParser {
  private static Pattern p = Pattern.compile("\\[(.*?)\\]");
  private static RegexFunctor regexFunctor = new RegexFunctor();
  private List<String> value;
  private Map<String, VerificationResponseParser> children;
  private List<List<String>> regex;

  public VerificationResponseParser() {
    children = new HashMap<>();
  }

  public void put(String[] keys, String value) {
    putHelper(this, keys, value, 0, Collections.EMPTY_LIST);
  }

  public void put(String[] keys, String value, List<String> regex) {
    putHelper(this, keys, value, 0, regex);
  }

  private void putHelper(VerificationResponseParser node, String[] keys, String value, int index, List<String> regex) {
    if (index == keys.length) {
      if (node.value == null) {
        node.value = new ArrayList<>();
        node.regex = new ArrayList<>();
      }
      node.value.add(value);
      if (regex != null) {
        node.regex.add(regex);
      } else {
        node.regex.add(Collections.EMPTY_LIST);
      }
      return;
    }
    if (!node.children.containsKey(keys[index])) {
      node.children.put(keys[index], new VerificationResponseParser());
    }
    putHelper(node.children.get(keys[index]), keys, value, index + 1, regex);
  }

  public List<Multimap<String, Object>> extract(String text) {
    Object jsonObject =
        text.charAt(0) == '[' && text.charAt(text.length() - 1) == ']' ? new JSONArray(text) : new JSONObject(text);
    List<Multimap<String, Object>> output = new ArrayList<>();
    /*
     * Pass in the root as level 1. There are 2 secnarios.
     *
     * scenario a) no common ancestor
     *
     *                  .  ====> make level 1
     *                / | \
     *           scope ts value
     *
     *
     * scenario b) single common ancestor. make this node level 1
     *
     *                   .  ====> make level 0
     *                   |
     *                series[*] ===> make level 1
     *				/		\
     *			scope		pointlist[*]
     *						/			\
     *					   0			1
     */
    // scenario a
    if (children.size() > 1) {
      process(this, jsonObject, 1, output);
    } else { // scenario b
      process(this, jsonObject, 0, output);
    }

    return output;
  }

  public static Object cast(Object val, String field) {
    if (field.equals("timestamp")) {
      if (val instanceof Double) {
        return ((Double) val).longValue();
      } else if (val instanceof Integer) {
        return ((Integer) val).longValue();
      } else if (val instanceof String) {
        try {
          return Long.parseLong((String) val);
        } catch (Exception ex) {
          throw new WingsException("Exception while casting the String to a Long : " + val);
        }
      } else {
        return val;
      }
    } else if (field.equals("value")) {
      if (val.toString().equals("null")) {
        return -1.0;
      }
      if (val instanceof Integer) {
        return ((Integer) val).doubleValue();
      } else if (val instanceof Long) {
        return ((Long) val).doubleValue();
      } else if (val instanceof String) {
        try {
          return Double.parseDouble((String) val);
        } catch (Exception ex) {
          throw new WingsException("Exception while casting the String to a Double : " + val);
        }
      } else {
        return val;
      }
    }

    return val;
  }

  public static Multimap<String, Object> process(
      VerificationResponseParser node, Object body, int level, List<Multimap<String, Object>> output) {
    Multimap<String, Object> result = ArrayListMultimap.create();
    Iterator<Entry<String, VerificationResponseParser>> iterator = node.children.entrySet().iterator();
    //  no children leaf node
    if (!iterator.hasNext()) {
      for (int i = 0; i < node.value.size(); ++i) {
        Object evaluatedBody = body;
        if (isNotEmpty(node.regex) && isNotEmpty(node.regex.get(i))) {
          for (String expr : node.regex.get(i)) {
            String extractedValue = regexFunctor.extract(expr, (String) evaluatedBody);
            if (isNotEmpty(extractedValue)) {
              evaluatedBody = extractedValue;
              result.put(node.value.get(i), evaluatedBody);
            }
          }
        } else {
          result.put(node.value.get(i), evaluatedBody);
        }
      }
    } else {
      while (iterator.hasNext()) {
        Map.Entry<String, VerificationResponseParser> trieEntry = iterator.next();
        VerificationResponseParser childNode = trieEntry.getValue();
        Object childBody = getValue(body, trieEntry.getKey());
        if (childBody == null) {
          break;
        }
        if (childBody instanceof JSONArray) {
          for (Object val : (JSONArray) childBody) {
            result.putAll(process(childNode, val, level + 1, output));
          }
        } else {
          result.putAll(process(childNode, childBody, level + 1, output));
        }
      }
    }
    if (level == 1) {
      output.add(result);
    }
    return result;
  }

  public static Object getValue(Object jsonObject, String field) {
    if (field.contains("[")) {
      if (field.charAt(0) == '[') {
        Matcher matcher = p.matcher(field);
        matcher.find();
        String group = matcher.group(1);
        if (group.equals("*")) {
          return jsonObject;
        }
        return ((JSONArray) jsonObject).get(Integer.parseInt(group));

      } else {
        JSONArray array = ((JSONObject) jsonObject).getJSONArray(field.substring(0, field.length() - 3));
        if (field.endsWith("[*]")) {
          return array;
        }
        // return the actual value of the element instead of the whole object.
        Matcher matcher = p.matcher(field);
        matcher.find();
        String group = matcher.group(1);
        return array.get(Integer.parseInt(group));
      }
    } else {
      return ((JSONObject) jsonObject).get(field);
    }
  }
}
