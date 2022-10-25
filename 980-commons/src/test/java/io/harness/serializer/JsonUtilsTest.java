/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtilsTest.CustomResponse.Result;
import io.harness.serializer.TestJsonBase.BaseType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jayway.jsonpath.DocumentContext;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.jsonunit.fluent.JsonFluentAssert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * The Class JsonUtilsTest.
 *
 * @author Rishi.
 */
@Slf4j
public class JsonUtilsTest extends CategoryTest {
  private static final String json =
      "{\"store\":{\"book\":[{\"category\":\"reference\",\"author\":\"NigelRees\",\"title\":\"SayingsoftheCentury\","
      + "\"price\":8.95},{\"category\":\"fiction\",\"author\":\"EvelynWaugh\",\"title\":\"SwordofHonour\",\"price\":12.99},{\"category\":\"fiction\","
      + "\"author\":\"HermanMelville\",\"title\":\"MobyDick\",\"isbn\":\"0-553-21311-3\",\"price\":8.99},{\"category\":\"fiction\""
      + ",\"author\":\"J.R.R.Tolkien\",\"title\":\"TheLordoftheRings\",\"isbn\":\"0-395-19395-8\",\"price\":22.99}]"
      + ",\"bicycle\":{\"color\":\"red\",\"price\":19.95}},\"expensive\":10}";

  /**
   * Should get authors.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetAuthors() {
    List<String> authors = JsonUtils.jsonPath(json, "$.store.book[*].author");
    log.debug("authors: {}", authors);
    assertThat(authors).isNotNull().hasSize(4);
  }

  /**
   * Should get title and cheap books.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetTitleAndCheapBooks() {
    DocumentContext ctx = JsonUtils.parseJson(json);
    List<String> titles = JsonUtils.jsonPath(ctx, "$.store.book[*].title");
    log.debug("authors: {}", titles);
    assertThat(titles).isNotNull().hasSize(4);

    List<Object> cheapBooks = JsonUtils.jsonPath(ctx, "$.store.book[?(@.price < 10)]");
    log.debug("cheapBooks: {}", cheapBooks);
    assertThat(cheapBooks).isNotNull().hasSize(2);
  }

  /**
   * Should return correct object in case of inheritence.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldReturnCorrectObjectInCaseOfInheritence() {
    TestJsonA baseA = new TestJsonA();
    String jsona = JsonUtils.asJson(baseA);

    JsonFluentAssert.assertThatJson(jsona).isEqualTo(
        "{\"baseType\":\"A\",\"name\":\"io.harness.serializer.TestJsonA\"}");

    TestJsonB baseB = new TestJsonB();
    String jsonb = JsonUtils.asJson(baseB);

    JsonFluentAssert.assertThatJson(jsonb).isEqualTo(
        "{\"baseType\":\"B\",\"name\":\"io.harness.serializer.TestJsonB\"}");

    assertThat(
        JsonUtils.asObject("{\"baseType\":\"A\",\"name\":\"io.harness.serializer.TestJsonA\"}", TestJsonBase.class))
        .isInstanceOf(TestJsonA.class)
        .extracting(TestJsonBase::getBaseType)
        .isEqualTo(BaseType.A);
    assertThat(
        JsonUtils.asObject("{\"baseType\":\"B\",\"name\":\"io.harness.serializer.TestJsonB\"}", TestJsonBase.class))
        .isInstanceOf(TestJsonB.class)
        .extracting(TestJsonBase::getBaseType)
        .isEqualTo(BaseType.B);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldReturnCorrectObjectInCaseOfInheritanceWithoutInterface() {
    TestJsonTypeA typeA = new TestJsonTypeA();
    typeA.setX("A");
    String jsona = JsonUtils.asJson(typeA);

    JsonFluentAssert.assertThatJson(jsona).isEqualTo("{\"eventType\":\"A\", \"x\": \"A\"}");

    TestJsonTypeB typeB = new TestJsonTypeB();
    typeB.setX("B");
    String jsonb = JsonUtils.asJson(typeB);

    JsonFluentAssert.assertThatJson(jsonb).isEqualTo("{\"eventType\":\"B\", \"x\": \"B\"}");

    assertThat(JsonUtils.asObject("{\"eventType\":\"A\", \"x\": \"A\"}", TestJsonTypeA.class))
        .isInstanceOf(TestJsonTypeA.class)
        .extracting(TestJsonTypeA::getX)
        .isEqualTo("A");
    assertThat(JsonUtils.asObject("{\"eventType\":\"B\", \"x\": \"B\"}", TestJsonTypeA.class))
        .isInstanceOf(TestJsonTypeB.class)
        .extracting(TestJsonTypeA::getX)
        .isEqualTo("B");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldUseClassNameWhenUsingMapperForCloning() {
    TestJsonA baseA = new TestJsonA();
    String jsona = JsonUtils.asJson(new Object[] {baseA}, JsonUtils.mapperForCloning);

    JsonFluentAssert.assertThatJson(jsona).isEqualTo(
        "[[\"io.harness.serializer.TestJsonA\",{\"baseType\":\"A\",\"name\":\"io.harness.serializer.TestJsonA\"}]]");

    TestJsonB baseB = new TestJsonB();
    String jsonb = JsonUtils.asJson(new Object[] {baseB}, JsonUtils.mapperForCloning);

    JsonFluentAssert.assertThatJson(jsonb).isEqualTo(
        "[[\"io.harness.serializer.TestJsonB\",{\"baseType\":\"B\",\"name\":\"io.harness.serializer.TestJsonB\"}]]");

    assertThat(JsonUtils.asObject(jsona, new TypeReference<Object[]>() {}, JsonUtils.mapperForCloning))
        .hasSize(1)
        .hasOnlyElementsOfType(TestJsonA.class)
        .extracting(o -> ((TestJsonBase) o).getBaseType())
        .containsExactly(BaseType.A);

    assertThat(JsonUtils.asObject(jsonb, new TypeReference<Object[]>() {}, JsonUtils.mapperForCloning))
        .hasSize(1)
        .hasOnlyElementsOfType(TestJsonB.class)
        .extracting(o -> ((TestJsonBase) o).getBaseType())
        .containsExactly(BaseType.B);
  }

  /**
   * Should generate json schema.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGenerateJsonSchema() {
    JsonFluentAssert.assertThatJson(JsonUtils.jsonSchema(TestJsonA.class))
        .isEqualTo(
            "{\"type\":\"object\",\"properties\":{\"baseType\":{\"enum\":[\"A\",\"B\",\"C\"],\"type\":\"string\"},\"name\":{\"type\":\"string\"}},"
            + "\"title\":\"BaseA\",\"required\":[\"name\"]}");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void testGetBuildDetails() throws IOException {
    File file = new File(System.getProperty("java.io.tmpdir") + "/"
        + "mapped.json");
    String json = "{\n"
        + "  \"total\": 100,\n"
        + "  \"offset\": 20,\n"
        + "  \"limit\": 40,\n"
        + "  \"result\": [\n"
        + "    {\n"
        + "      \"buildNo\": \"21\",\n"
        + "      \"metadata\": {\n"
        + "        \"tag1\": \"value1\"\n"
        + "      }\n"
        + "    },\n"
        + "    {\n"
        + "      \"buildNo\": \"22\",\n"
        + "      \"metadata\": {\n"
        + "        \"tag1\": \"value1\"\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    writer.write(json);

    writer.close();

    final CustomResponse customResponse = (CustomResponse) JsonUtils.readFromFile(file, CustomResponse.class);

    assertThat(customResponse).isNotNull();
    assertThat(customResponse.getResult()).isNotEmpty();
    assertThat(customResponse.getResult()).extracting(Result::getBuildNo).contains("21", "22");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCustomArtifactMapping() {
    String json =
        "{\"items\":[{\"id\":\"bWF2ZW4tcmVsZWFzZXM6MWM3ODdhMDNkYjgyMDllYjhjY2IyMDYwMTJhMWU0MmI\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"group\":\"mygroup\",\"name\":\"myartifact\",\"version\":\"1.0\",\"assets\":[{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war\",\"path\":\"mygroup/myartifact/1.0/myartifact-1.0.war\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6ZDQ4MTE3NTQxZGNiODllYzYxM2IyMzk3MzIwMWQ3YmE\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"da39a3ee5e6b4b0d3255bfef95601890afd80709\",\"md5\":\"d41d8cd98f00b204e9800998ecf8427e\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war.md5\",\"path\":\"mygroup/myartifact/1.0/myartifact-1.0.war.md5\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6MGFiODBhNzQzOTIxZTQyNjYxOWJlZjJiYmRhYTU5MWQ\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"67a74306b06d0c01624fe0d0249a570f4d093747\",\"md5\":\"74be16979710d4c4e7c6647856088456\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war.sha1\",\"path\":\"mygroup/myartifact/1.0/myartifact-1.0.war.sha1\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6MTNiMjllNDQ5ZjBlM2I4ZDM5OTY0ZWQzZTExMGUyZTM\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"10a34637ad661d98ba3344717656fcc76209c2f8\",\"md5\":\"0144712dd81be0c3d9724f5e56ce6685\"}}]},{\"id\":\"bWF2ZW4tcmVsZWFzZXM6ZGZiZWYwOWVmZTE2NDRlYTYzNTAwMWQ3MjVhYzgxMTY\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"group\":\"mygroup\",\"name\":\"myartifact\",\"version\":\"1.1\",\"assets\":[{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.1/myartifact-1.1.war\",\"path\":\"mygroup/myartifact/1.1/myartifact-1.1.war\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6MGFiODBhNzQzOTIxZTQyNmQ1ZThjYjBmNWY0ODYwODc\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"da39a3ee5e6b4b0d3255bfef95601890afd80709\",\"md5\":\"d41d8cd98f00b204e9800998ecf8427e\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.1/myartifact-1.1.war.md5\",\"path\":\"mygroup/myartifact/1.1/myartifact-1.1.war.md5\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6ZDQ4MTE3NTQxZGNiODllYzlhMzlhNjIzMGVkMzI2ZTY\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"67a74306b06d0c01624fe0d0249a570f4d093747\",\"md5\":\"74be16979710d4c4e7c6647856088456\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.1/myartifact-1.1.war.sha1\",\"path\":\"mygroup/myartifact/1.1/myartifact-1.1.war.sha1\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6ODUxMzU2NTJhOTc4YmU5YTRjOWY0MGI0ZWY0MjM1NTk\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"10a34637ad661d98ba3344717656fcc76209c2f8\",\"md5\":\"0144712dd81be0c3d9724f5e56ce6685\"}}]},{\"id\":\"bWF2ZW4tcmVsZWFzZXM6NzZkN2Q3ZTQxODZhMzkwZmQ5NmRiMjk1YjgwOTg2YWI\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"group\":\"mygroup\",\"name\":\"myartifact\",\"version\":\"1.2\",\"assets\":[{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.2/myartifact-1.2.war\",\"path\":\"mygroup/myartifact/1.2/myartifact-1.2.war\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6MTNiMjllNDQ5ZjBlM2I4ZDYwZGQ0ZjAyNmY4ZjVkYWU\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"da39a3ee5e6b4b0d3255bfef95601890afd80709\",\"md5\":\"d41d8cd98f00b204e9800998ecf8427e\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.2/myartifact-1.2.war.md5\",\"path\":\"mygroup/myartifact/1.2/myartifact-1.2.war.md5\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6ODUxMzU2NTJhOTc4YmU5YWZhNjRiNTEwYzAwODUzOGU\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"67a74306b06d0c01624fe0d0249a570f4d093747\",\"md5\":\"74be16979710d4c4e7c6647856088456\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.2/myartifact-1.2.war.sha1\",\"path\":\"mygroup/myartifact/1.2/myartifact-1.2.war.sha1\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6MGFiODBhNzQzOTIxZTQyNjRkOTU3MWZkZTEzNTJmYzQ\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"10a34637ad661d98ba3344717656fcc76209c2f8\",\"md5\":\"0144712dd81be0c3d9724f5e56ce6685\"}}]}],\"continuationToken\":null}";
    DocumentContext ctx = JsonUtils.parseJson(json);

    CustomResponse response = new CustomResponse();
    List<Result> result = new ArrayList<>();

    LinkedList<LinkedHashMap> results = JsonUtils.jsonPath(ctx, "$.items[*]");
    for (int i = 0; i < results.size(); i++) {
      Result res = new Result();
      Map<String, String> metadata = new HashMap<>();
      res.setBuildNo(JsonUtils.jsonPath(ctx, "$.items[" + i + "].version"));
      metadata.put("downloadUrl", JsonUtils.jsonPath(ctx, "$.items[" + i + "].assets[0].downloadUrl"));
      res.setMetadata(metadata);
      result.add(res);
    }
    response.setResult(result);
    assertThat(response).isNotNull();
    assertThat(response.getResult()).extracting(Result::getBuildNo).contains("1.0", "1.1", "1.2");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testGetPrettyJsonString() {
    String jsonString = "{\"x\": \"abc\", \"y\": 3}";
    String prettyJsonString = "{\n"
        + "  \"x\": \"abc\",\n"
        + "  \"y\": 3\n"
        + "}";
    assertThat(JsonUtils.prettifyJsonString(jsonString)).isEqualTo(prettyJsonString);
  }

  public static class CustomResponse {
    private List<Result> result = new ArrayList<>();

    public List<Result> getResult() {
      return result;
    }

    public void setResult(List<Result> result) {
      this.result = result;
    }

    public static class Result {
      String buildNo;
      Map<String, String> metadata = new HashMap<>();

      public String getBuildNo() {
        return buildNo;
      }

      public void setBuildNo(String buildNo) {
        this.buildNo = buildNo;
      }

      public Map<String, String> getMetadata() {
        return metadata;
      }

      public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
      }
    }
  }
}
