/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.message;

import static io.harness.delegate.message.MessageServiceImpl.IN;
import static io.harness.delegate.message.MessageServiceImpl.OUT;
import static io.harness.delegate.message.MessageServiceImpl.PRIMARY_DELIMITER;
import static io.harness.delegate.message.MessageServiceImpl.SECONDARY_DELIMITER;
import static io.harness.delegate.message.MessengerType.DELEGATE;
import static io.harness.delegate.message.MessengerType.WATCHER;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.NIKOLA;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MessageServiceTest extends CategoryTest {
  private final MessengerType MESSENGER_TYPE = DELEGATE;
  private final MessengerType OTHER_MESSENGER_TYPE = WATCHER;

  private MessageService messageService;

  private String processId;
  private String otherProcessId;
  private String data1;
  private String data2;
  private File messageFile;
  private File otherMessageFile;
  private File dataFile1;
  private File dataFile2;

  @Before
  public void setUp() {
    processId = UUID.randomUUID().toString();
    otherProcessId = UUID.randomUUID().toString();
    data1 = UUID.randomUUID().toString();
    data2 = UUID.randomUUID().toString();

    Clock clock = mock(Clock.class);
    when(clock.millis()).thenReturn(100L);

    String tempDir = System.getProperty("java.io.tmpdir");

    messageService = new MessageServiceImpl(tempDir, clock, MESSENGER_TYPE, processId);

    messageFile = new File(tempDir + "msg/io/delegate/" + processId, "");
    messageFile.deleteOnExit();

    otherMessageFile = new File(tempDir + "msg/io/watcher/" + otherProcessId);
    otherMessageFile.deleteOnExit();

    dataFile1 = new File(tempDir + "msg/data/" + data1);
    dataFile1.deleteOnExit();

    dataFile2 = new File(tempDir + "msg/data/" + data2);
    dataFile2.deleteOnExit();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldWriteMessage() throws IOException {
    messageService.writeMessage("message-text", "p1", "p2");

    List<String> messageLines = FileUtils.readLines(messageFile, UTF_8);

    String expected = Joiner.on(PRIMARY_DELIMITER)
                          .join(asList(OUT, "100", MESSENGER_TYPE, processId, "message-text",
                              Joiner.on(SECONDARY_DELIMITER).join(asList("p1", "p2"))));

    assertThat(messageLines.size()).isEqualTo(1);
    assertThat(messageLines.get(0)).isEqualTo(expected);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldReadMessage() throws IOException {
    String line = Joiner.on(PRIMARY_DELIMITER)
                      .join(asList(IN, "100", OTHER_MESSENGER_TYPE, otherProcessId, "message-text",
                          Joiner.on(SECONDARY_DELIMITER).join(asList("p1", "p2"))));

    FileUtils.writeLines(messageFile, singletonList(line));

    Message message = messageService.readMessage(1000L);

    assertThat(message.getFromProcess()).isEqualTo(otherProcessId);
    assertThat(message.getFromType()).isEqualTo(OTHER_MESSENGER_TYPE);
    assertThat(message.getTimestamp()).isEqualTo(100L);
    assertThat(message.getMessage()).isEqualTo("message-text");
    assertThat(message.getParams()).containsSequence("p1", "p2");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldSendMessage() throws IOException {
    messageService.writeMessageToChannel(OTHER_MESSENGER_TYPE, otherProcessId, "message-text", "p1", "p2");

    List<String> messageLines = FileUtils.readLines(otherMessageFile, UTF_8);

    String expected = Joiner.on(PRIMARY_DELIMITER)
                          .join(asList(IN, "100", MESSENGER_TYPE, processId, "message-text",
                              Joiner.on(SECONDARY_DELIMITER).join(asList("p1", "p2"))));

    assertThat(messageLines.size()).isEqualTo(1);
    assertThat(messageLines.get(0)).isEqualTo(expected);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldRetrieveMessage() throws IOException {
    String line = Joiner.on(PRIMARY_DELIMITER)
                      .join(asList(OUT, "100", OTHER_MESSENGER_TYPE, otherProcessId, "message-text",
                          Joiner.on(SECONDARY_DELIMITER).join(asList("p1", "p2"))));

    FileUtils.writeLines(otherMessageFile, singletonList(line));

    Message message = messageService.readMessageFromChannel(OTHER_MESSENGER_TYPE, otherProcessId, 1000L);

    assertThat(message.getFromProcess()).isEqualTo(otherProcessId);
    assertThat(message.getFromType()).isEqualTo(OTHER_MESSENGER_TYPE);
    assertThat(message.getTimestamp()).isEqualTo(100L);
    assertThat(message.getMessage()).isEqualTo("message-text");
    assertThat(message.getParams()).containsSequence("p1", "p2");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldCloseChannel() throws IOException {
    FileUtils.writeLines(messageFile,
        singletonList(Joiner.on(PRIMARY_DELIMITER)
                          .join(asList(IN, "100", OTHER_MESSENGER_TYPE, otherProcessId, "message-text",
                              Joiner.on(SECONDARY_DELIMITER).join(asList("p1", "p2"))))));

    FileUtils.writeLines(otherMessageFile,
        singletonList(Joiner.on(PRIMARY_DELIMITER)
                          .join(asList(OUT, "100", OTHER_MESSENGER_TYPE, otherProcessId, "message-text",
                              Joiner.on(SECONDARY_DELIMITER).join(asList("p1", "p2"))))));

    assertThat(messageFile.exists()).isTrue();
    assertThat(otherMessageFile.exists()).isTrue();

    messageService.closeChannel(MESSENGER_TYPE, processId);

    assertThat(messageFile.exists()).isFalse();
    assertThat(otherMessageFile.exists()).isTrue();

    messageService.closeChannel(OTHER_MESSENGER_TYPE, otherProcessId);

    assertThat(messageFile.exists()).isFalse();
    assertThat(otherMessageFile.exists()).isFalse();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldWriteData() throws IOException {
    messageService.putData(data1, "foo", "bar");
    messageService.putData(data1, "baz", "qux");
    messageService.putData(data2, "abc", "123");
    messageService.putData(data2, "xyz", asList("423", "567"));

    String dataContent1 = FileUtils.readFileToString(dataFile1, UTF_8);
    Map<String, Object> map1 = JsonUtils.asObject(dataContent1, HashMap.class);
    assertThat(map1.size()).isEqualTo(2);
    assertThat(map1.get("foo")).isEqualTo("bar");
    assertThat(map1.get("baz")).isEqualTo("qux");

    String dataContent2 = FileUtils.readFileToString(dataFile2, UTF_8);
    Map<String, Object> map2 = JsonUtils.asObject(dataContent2, HashMap.class);
    assertThat(map2.size()).isEqualTo(2);
    assertThat(map2.get("abc")).isEqualTo("123");
    assertThat(map2.get("xyz")).isEqualTo(ImmutableList.of("423", "567"));
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  @SuppressWarnings({"unchecked"})
  public void shouldReadData() throws IOException {
    Map<String, Object> map1 = new HashMap<>();
    map1.put("foo", "bar");
    map1.put("baz", "qux");
    FileUtils.write(dataFile1, JsonUtils.asPrettyJson(map1), UTF_8);

    Map<String, Object> map2 = new HashMap<>();
    map2.put("abc", "123");
    map2.put("xyz", asList("423", "567"));
    FileUtils.write(dataFile2, JsonUtils.asPrettyJson(map2), UTF_8);

    assertThat(messageService.getData(data1, "foo", String.class)).isEqualTo("bar");
    assertThat(messageService.getData(data1, "baz", String.class)).isEqualTo("qux");
    assertThat(messageService.getData(data2, "abc", String.class)).isEqualTo("123");
    assertThat(messageService.getData(data2, "xyz", String.class)).isNull();
    assertThat(messageService.getData(data2, "xyz", List.class)).isEqualTo(ImmutableList.of("423", "567"));
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  @SuppressWarnings({"unchecked"})
  public void shouldRemoveData() throws IOException {
    Map<String, Object> map1 = new HashMap<>();
    map1.put("foo", "bar");
    map1.put("baz", "qux");
    FileUtils.write(dataFile1, JsonUtils.asPrettyJson(map1), UTF_8);

    messageService.removeData(data1, "baz");

    String dataContent1 = FileUtils.readFileToString(dataFile1, UTF_8);
    Map<String, Object> resultMap = JsonUtils.asObject(dataContent1, HashMap.class);
    assertThat(resultMap.size()).isEqualTo(1);
    assertThat(resultMap.get("foo")).isEqualTo("bar");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldCloseData() throws IOException {
    Map<String, Object> map1 = new HashMap<>();
    map1.put("foo", "bar");
    map1.put("baz", "qux");
    FileUtils.write(dataFile1, JsonUtils.asPrettyJson(map1), UTF_8);

    Map<String, Object> map2 = new HashMap<>();
    map2.put("abc", "123");
    map2.put("xyz", asList("423", "567"));
    FileUtils.write(dataFile2, JsonUtils.asPrettyJson(map2), UTF_8);

    assertThat(dataFile1.exists()).isTrue();
    assertThat(dataFile2.exists()).isTrue();

    messageService.closeData(data1);

    assertThat(dataFile1.exists()).isFalse();
    assertThat(dataFile2.exists()).isTrue();

    messageService.closeData(data2);

    assertThat(dataFile1.exists()).isFalse();
    assertThat(dataFile2.exists()).isFalse();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldDeleteDamagedFile() throws IOException {
    Map<String, Object> map1 = new HashMap<>();
    map1.put("foo", "bar");
    map1.put("baz", "qux");
    FileUtils.write(dataFile1, JsonUtils.asPrettyJson(map1), UTF_8);

    Map<String, Object> map2 = new HashMap<>();
    map2.put("abc", "123");
    map2.put("xyz", asList("423", "567"));
    FileUtils.write(dataFile2, JsonUtils.asPrettyJson(map2), UTF_8);

    assertThat(dataFile1.exists()).isTrue();
    assertThat(dataFile2.exists()).isTrue();

    FileUtils.write(dataFile1, "non-readable text");
    map1 = messageService.getAllData(dataFile1.getName());

    assertThat(dataFile1.exists()).isFalse();
    assertThat(dataFile2.exists()).isTrue();

    FileUtils.write(dataFile2, "non-readable text");
    map2 = messageService.getAllData(dataFile2.getName());

    assertThat(dataFile1.exists()).isFalse();
    assertThat(dataFile2.exists()).isFalse();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldRecoverDamagedFile() throws IOException {
    Map<String, Object> map1 = new HashMap<>();
    map1.put("abc", "bar");
    map1.put("baz", "qux");
    FileUtils.write(dataFile1, JsonUtils.asPrettyJson(map1), UTF_8);
    assertThat(dataFile1.exists()).isTrue();

    FileUtils.write(dataFile1, "non-readable text", UTF_8);
    map1 = messageService.getAllData(dataFile1.getName());

    assertThat(dataFile1.exists()).isFalse();

    Map<String, Object> map2 = new HashMap<>();
    map1.put("foo", "123");
    map1.put("xyz", asList("423", "567"));
    FileUtils.write(dataFile1, JsonUtils.asPrettyJson(map2), UTF_8);

    assertThat(dataFile1.exists()).isTrue();
  }
}
