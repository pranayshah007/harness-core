/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.data;

import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PmsCommonsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializerWrapper;

import com.google.inject.Inject;
import com.mongodb.MongoClient;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class OrchestrationMapTest extends PmsCommonsTestBase {
  private static final String KEY = "a";
  private static final Integer VALUE = 11;

  @Inject private KryoSerializerWrapper kryoSerializerWrapper;

  /**
   * This method tests value persisted in database for {@link OrchestrationMap}
   * <br>
   * In order to see what is the value, u need to copy <b>$binary</b> value from db and
   * <br>
   * put it to <i>binaryWithoutEqualsSignAtTheEnd</i>
   * <br>
   * <b>WITHOUT = (equals) mark</b> at the end
   */
  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestDeserialization() {
    final String binaryWithoutEqualsSignAtTheEnd =
        "eJxlj0FOwzAQRbEsOI6VkKZN9rBAlQBxgWg6nhSLZBzZDoJrcRWuAEhsWJRu2DFBggjYWf/9P/7/"
        + "8eFQHWnVNIEQ4l4r5801BKYYDVreGsdtALMh4GjWVTxxYkxnkxhTGDGNgS7GhL5/1go9s1AfruhF"
        + "K0D0IycDHd01N1VsvvFOK4ae4gAoIUstjJ38HKgjiHQO06kpRK9aEd+64Lkn3i+kqP5fcHYkczq/"
        + "f0rx172ZTButGFzraPcHWIoY3JCcfzvQKt0PkrwM3spOkUSB7bsA92v+mj60WpQrsEXZLouirstNg"
        + "ZTbvK6K4zzLLFTlsmpXGdqnT+Tahnc";

    byte[] bytes = obtainDbObject(binaryWithoutEqualsSignAtTheEnd);
    Object deserializedObject = kryoSerializerWrapper.asInflatedObject(bytes);

    assertThat(deserializedObject).isInstanceOf(OrchestrationMap.class);
    OrchestrationMap orchestrationMap = (OrchestrationMap) deserializedObject;

    log.info("Deserialize value: {}", orchestrationMap);

    assertThat(orchestrationMap).isNotEmpty();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestParseStringWithNull() {
    assertThat(OrchestrationMap.parse((String) null)).isEqualTo(null);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestParseString() {
    OrchestrationMap orchestrationMap = constructOrchestrationMap();
    assertThat(OrchestrationMap.parse(getJsonString())).isEqualTo(orchestrationMap);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestParseMapWithNull() {
    assertThat(OrchestrationMap.parse((Map<String, Object>) null)).isEqualTo(null);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestParseMap() {
    Map<String, Object> map = new HashMap<>();
    map.put(KEY, VALUE);

    OrchestrationMap orchestrationMap = constructOrchestrationMap();
    assertThat(OrchestrationMap.parse(map)).isEqualTo(orchestrationMap);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestToJson() {
    OrchestrationMap orchestrationMap = constructOrchestrationMap();
    assertThat(orchestrationMap.toJson()).isEqualTo(getJsonString());
  }

  private byte[] obtainDbObject(final String binary) {
    String dbJsonData = "{ \"_data\" : { \"$binary\" : \"" + binary + "\", \"$type\" : \"00\" } }";

    BsonDocument bsonDocument =
        Document.parse(dbJsonData).toBsonDocument(BsonDocument.class, MongoClient.getDefaultCodecRegistry());
    return bsonDocument.get("_data").asBinary().getData();
  }

  private OrchestrationMap constructOrchestrationMap() {
    OrchestrationMap orchestrationMap = new OrchestrationMap();
    orchestrationMap.put(KEY, VALUE);
    return orchestrationMap;
  }

  private String getJsonString() {
    return "{\"" + KEY + "\":" + VALUE + "}";
  }
}
