/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.delegatetasks.tasks.ElkLogzDataCollectionTask.parseElkResponse;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.delegatetasks.tasks.ElkLogzDataCollectionTask;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.elk.ElkLogFetchRequest;
import software.wings.service.impl.elk.ElkQueryType;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by rsingh on 8/3/17.
 */
public class ElkLogFetchRequestTest extends CategoryTest {
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testJsonFormat() {
    Set<String> hosts = new HashSet<>();
    hosts.add("cdcd");
    hosts.add("csdcd");
    String query = "dskcdsds";
    String indices = "_all";
    long startTime = System.currentTimeMillis();
    long endTime = startTime + TimeUnit.HOURS.toMillis(34);

    ElkLogFetchRequest elkLogFetchRequest = ElkLogFetchRequest.builder()
                                                .query(query)
                                                .indices(null)
                                                .hostnameField("beat.hostname")
                                                .messageField("message")
                                                .timestampField("")
                                                .hosts(hosts)
                                                .startTime(startTime)
                                                .endTime(endTime)
                                                .queryType(ElkQueryType.TERM)
                                                .build();

    List<JSONObject> hostJsonObjects = new ArrayList<>();
    for (String host : hosts) {
      hostJsonObjects.add(new JSONObject().put("term", new JSONObject().put("beat.hostname", host)));
    }

    JSONObject boolObject = new JSONObject().put("bool", new JSONObject().put("should", hostJsonObjects));

    JSONObject regexObject = new JSONObject();
    regexObject.put("regexp", new JSONObject().put("message", new JSONObject().put("value", query)));

    JSONObject rangeObject = new JSONObject();
    rangeObject.put("range",
        new JSONObject().put(
            "@timestamp", new JSONObject().put("gte", startTime).put("lt", endTime).put("format", "epoch_millis")));

    Map<String, List<JSONObject>> mustArrayObjects = new HashMap<>();
    mustArrayObjects.put("must", new ArrayList<>());
    mustArrayObjects.get("must").add(regexObject);
    mustArrayObjects.get("must").add(boolObject);
    mustArrayObjects.get("must").add(rangeObject);

    String expectedJson = new JSONObject().put("query", new JSONObject().put("bool", mustArrayObjects)).toString();

    //    assertThat( JsonUtils.asJson(elkLogFetchRequest.toElasticSearchJsonObject())).isEqualTo(expectedJson);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testJsonFormatWithIndices() {
    Set<String> hosts = new HashSet<>();
    hosts.add("cdcd");
    hosts.add("csdcd");
    String query = "dskcdsds";
    String indices = " _all, _none ";
    long startTime = System.currentTimeMillis();
    long endTime = startTime + TimeUnit.HOURS.toMillis(34);

    ElkLogFetchRequest elkLogFetchRequest = ElkLogFetchRequest.builder()
                                                .query(query)
                                                .indices(indices)
                                                .hostnameField("beat.hostname")
                                                .messageField("message")
                                                .timestampField("")
                                                .hosts(hosts)
                                                .startTime(startTime)
                                                .endTime(endTime)
                                                .queryType(ElkQueryType.MATCH)
                                                .build();

    List<JSONObject> hostJsonObjects = new ArrayList<>();
    for (String host : hosts) {
      hostJsonObjects.add(new JSONObject().put("match", new JSONObject().put("beat.hostname", host)));
    }

    JSONObject boolObject = new JSONObject().put("bool", new JSONObject().put("should", hostJsonObjects));

    JSONObject regexObject = new JSONObject();
    regexObject.put("regexp", new JSONObject().put("message", new JSONObject().put("value", query)));

    JSONObject rangeObject = new JSONObject();
    rangeObject.put("range",
        new JSONObject().put(
            "@timestamp", new JSONObject().put("gte", startTime).put("lt", endTime).put("format", "epoch_millis")));

    Map<String, List<JSONObject>> mustArrayObjects = new HashMap<>();
    mustArrayObjects.put("must", new ArrayList<>());
    mustArrayObjects.get("must").add(regexObject);
    mustArrayObjects.get("must").add(boolObject);
    mustArrayObjects.get("must").add(rangeObject);

    JSONObject indicesArray = new JSONObject();
    for (String index : indices.split(",")) {
      indicesArray.append("indices", index.trim());
    }

    indicesArray.put("query", new JSONObject().put("bool", mustArrayObjects));
    JSONObject indicesObject = new JSONObject().put("indices", indicesArray);

    String expectedJson = new JSONObject().put("query", indicesObject).toString();

    //    assertThat( JsonUtils.asJson(elkLogFetchRequest.toElasticSearchJsonObject())).isEqualTo(expectedJson);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testParse() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    LinkedHashMap map = mapper.readValue(new File("400-rest/src/test/resources//elk/elk.txt"), LinkedHashMap.class);
    List<LogElement> logElements = parseElkResponse(map, "info", "@timestamp", "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "kubernetes.pod.name", "rddashboard-prod-5-67d88f4657-ff7k9", "log", 0, false, -1, -1);
    assertThat(logElements.isEmpty()).isFalse();
    assertThat(logElements.get(0).getHost()).isEqualTo("rddashboard-prod-5-67d88f4657-ff7k9");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testJsonParsing() {
    String jsonString =
        "{\"a\" : \"this is a test\", \"b\":[\"test1\", \"test2\", \"test3\"], \"c\":{ \"d\": \"another test\"}}";
    JSONObject jsonObject = new JSONObject(jsonString);
    String aValue = ElkLogzDataCollectionTask.parseAndGetValue(jsonObject, "a");
    String bValue = ElkLogzDataCollectionTask.parseAndGetValue(jsonObject, "b.0");
    String cValue = ElkLogzDataCollectionTask.parseAndGetValue(jsonObject, "c.d");

    assertThat(aValue).isEqualTo("this is a test");
    assertThat(bValue).isEqualTo("test1");
    assertThat(cValue).isEqualTo("another test");
  }
}
