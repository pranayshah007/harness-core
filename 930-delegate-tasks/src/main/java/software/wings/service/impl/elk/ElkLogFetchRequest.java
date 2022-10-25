/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.elk;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;
import io.harness.serializer.JsonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import lombok.Builder;
import lombok.Data;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by rsingh on 8/3/17.
 */

@Data
@OwnedBy(HarnessTeam.CV)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ElkLogFetchRequest {
  private final String query;
  private final String indices;
  private final String hostnameField;
  private final String messageField;
  private String timestampField = "@timestamp";
  private final Set<String> hosts;
  private final long startTime;
  private final long endTime;
  private ElkQueryType queryType = ElkQueryType.TERM;

  @Builder
  private ElkLogFetchRequest(String query, String indices, String hostnameField, String messageField,
      String timestampField, Set<String> hosts, long startTime, long endTime, ElkQueryType queryType) {
    this.query = query;
    this.indices = indices;
    this.hostnameField = hostnameField;
    this.messageField = messageField;
    this.timestampField = isEmpty(timestampField) ? this.timestampField : timestampField;
    this.hosts = hosts;
    this.startTime = startTime;
    this.endTime = endTime;
    this.queryType = queryType == null ? this.queryType : queryType;
  }

  public void setQueryType(ElkQueryType queryType) {
    this.queryType = queryType == null ? this.queryType : queryType;
  }

  public Object toElasticSearchJsonObject() {
    List<JSONObject> hostJsonObjects = new ArrayList<>();
    for (String host : hosts) {
      hostJsonObjects.add(
          new JSONObject().put(queryType.name().toLowerCase(), new JSONObject().put(hostnameField, host)));
    }

    JSONObject regexObject = new JSONObject();
    regexObject.put("regexp", new JSONObject().put(messageField, new JSONObject().put("value", query.toLowerCase())));

    JSONObject rangeObject = new JSONObject();
    rangeObject.put("range",
        new JSONObject().put(
            timestampField, new JSONObject().put("gte", startTime).put("lt", endTime).put("format", "epoch_millis")));

    Map<String, List<JSONObject>> mustArrayObjects = new HashMap<>();
    mustArrayObjects.put("filter",
        asList(new JSONObject().put("bool", new JSONObject().put("should", hostJsonObjects)), rangeObject, eval()));

    JSONObject queryObject =
        new JSONObject().put("query", new JSONObject().put("bool", mustArrayObjects)).put("size", 10000);
    String jsonOut = queryObject.toString();

    return JsonUtils.asObject(jsonOut, Object.class);
  }

  public static Object lastInsertedRecordObject(boolean shouldSort) {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("size", 1);

    jsonObject.put("query", new JSONObject().put("match_all", new HashMap<>()));
    if (shouldSort) {
      jsonObject.put("sort", new JSONObject().put("@timestamp", "desc"));
    }

    return JsonUtils.asObject(jsonObject.toString(), Object.class);
  }

  private String insertSpaces(String expr) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < expr.length(); i++) {
      if (expr.charAt(i) == '(') {
        result.append(expr.charAt(i));
        result.append(' ');
      } else if (expr.charAt(i) == ')') {
        result.append(' ');
        result.append(expr.charAt(i));
      } else {
        result.append(expr.charAt(i));
      }
    }
    return result.toString();
  }

  protected JSONObject eval() {
    if (query.charAt(0) == '{' && query.charAt(query.length() - 1) == '}') {
      return getJSONQuery();
    } else {
      try {
        String[] tokens = insertSpaces(query.toLowerCase()).split(" ");
        Stack<JSONObject> operandStack = new Stack<>();
        Stack<String> operatorStack = new Stack<>();
        JSONObject rval, lval;
        String operator;
        for (String token : tokens) {
          if (")".equals(token)) {
            Stack<JSONObject> result = new Stack<>();
            while (!(rval = operandStack.pop())
                        .toString()
                        .equals("{\"" + queryType.name().toLowerCase() + "\":{\"(\":\"(\"}}")) {
              if (operatorStack.isEmpty()) {
                result.push(rval);
              } else {
                lval = operandStack.pop();
                operator = operatorStack.pop();
                result.push(eval(lval, rval, operator));
              }
            }
            result.forEach(operandStack::push);
          } else {
            if ("or".equals(token.toLowerCase()) || "and".equals(token.toLowerCase())) {
              operatorStack.push(token);
            } else {
              operandStack.push(stringToJson(token));
            }
          }
        }

        while (!operatorStack.empty()) {
          rval = operandStack.pop();
          lval = operandStack.pop();
          operator = operatorStack.pop();
          operandStack.push(eval(lval, rval, operator));
        }

        if (operandStack.size() > 1) {
          List<JSONObject> mustObjectList = new ArrayList<>();
          while (!operandStack.isEmpty()) {
            if (operandStack.peek().toString().equals("{\"" + queryType.name().toLowerCase() + "\":{\"(\":\"(\"}}")) {
              throw new WingsException("Unmatched open braces `(`");
            }
            mustObjectList.add(operandStack.pop());
          }
          return new JSONObject().put("bool", new JSONObject().put("must", mustObjectList));
        }
        return operandStack.pop();

      } catch (RuntimeException ex) {
        throw new WingsException(
            "Malformed Query. Braces should be matching. Only supported operators are 'or' and 'and' ");
      }
    }
  }

  private JSONObject getJSONQuery() {
    try {
      return new JSONObject(query);
    } catch (JSONException ex) {
      throw new WingsException("Invalid JSON Query Passed : " + ex.getMessage());
    }
  }

  private JSONObject eval(JSONObject lval, JSONObject rval, String operator) {
    if ("or".equals(operator.toLowerCase())) {
      return evalOrExpression(lval, rval);
    } else if ("and".equals(operator.toLowerCase())) {
      return evalAndExpression(lval, rval);
    } else {
      throw new WingsException("Unknown operator in expression " + operator);
    }
  }

  private JSONObject evalOrExpression(JSONObject left, JSONObject right) {
    List<JSONObject> jsonObjects = asList(left, right);
    return new JSONObject().put("bool", new JSONObject().put("should", jsonObjects));
  }

  private JSONObject evalAndExpression(JSONObject left, JSONObject right) {
    List<JSONObject> jsonObjects = asList(left, right);
    return new JSONObject().put("bool", new JSONObject().put("must", jsonObjects));
  }

  private JSONObject stringToJson(String str) {
    if (str.equals("(") || str.equals(")")) {
      return termToJson(str, str);
    } else if (str.contains(":")) {
      String[] terms = str.split(":");
      if (terms.length > 2) {
        throw new WingsException("Cannot parse " + str + " . Should be value or key:value");
      }
      return termToJson(terms[0], terms[1]);
    } else {
      return messageToJson(messageField, str);
    }
  }

  private JSONObject messageToJson(String term, String val) {
    JSONObject regexObject = new JSONObject();
    regexObject.put("regexp", new JSONObject().put(term, new JSONObject().put("value", val)));
    return regexObject;
  }

  private JSONObject termToJson(String term, String val) {
    return new JSONObject().put(queryType.name().toLowerCase(), new JSONObject().put(term, val));
  }
}
