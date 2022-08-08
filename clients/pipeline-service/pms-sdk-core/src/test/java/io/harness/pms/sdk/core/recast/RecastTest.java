/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.recast;

import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionErrorInfo;
import io.harness.pms.contracts.execution.NodeExecutionProto;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ShortNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RecastTest extends PmsSdkCoreTestBase {
  private static final String RECAST_KEY = "__recast";
  private static final String ENCODED_VALUE = "__encodedValue";

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecastWithProtoAsAField() throws InvalidProtocolBufferException {
    ExecutionErrorInfo executionErrorInfo = ExecutionErrorInfo.newBuilder().setMessage("some-message").build();
    ProtoAsAFieldClass protoAsAFieldClass =
        ProtoAsAFieldClass.builder()
            .executionErrorInfo(executionErrorInfo)
            .failureTypeSet(Sets.newHashSet(FailureType.APPLICATION_FAILURE, FailureType.AUTHORIZATION_FAILURE))
            .build();

    Map<Object, Object> expectedDocument =
        ImmutableMap.builder()
            .put(RECAST_KEY, ProtoAsAFieldClass.class.getName())
            .put("executionErrorInfo",
                ImmutableMap.builder()
                    .put(RECAST_KEY, ExecutionErrorInfo.class.getName())
                    .put(ENCODED_VALUE, JsonFormat.printer().print(executionErrorInfo))
                    .build())
            .put("failureTypeSet",
                Sets.newHashSet(ImmutableMap.builder()
                                    .put(RECAST_KEY, FailureType.class.getName())
                                    .put(ENCODED_VALUE, FailureType.APPLICATION_FAILURE.name())
                                    .build(),
                    ImmutableMap.builder()
                        .put(RECAST_KEY, FailureType.class.getName())
                        .put(ENCODED_VALUE, FailureType.AUTHORIZATION_FAILURE.name())
                        .build()))
            .build();

    Map<String, Object> map = RecastOrchestrationUtils.toMap(protoAsAFieldClass);
    assertThat(map).isNotNull();
    assertThat(map).isEqualTo(expectedDocument);

    ProtoAsAFieldClass recastedClass = RecastOrchestrationUtils.fromMap(map, ProtoAsAFieldClass.class);
    assertThat(recastedClass).isEqualTo(protoAsAFieldClass);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  private static class ProtoAsAFieldClass {
    private ExecutionErrorInfo executionErrorInfo;
    private Set<FailureType> failureTypeSet;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecastWithProtoExecutionErrorInfo() throws InvalidProtocolBufferException {
    ExecutionErrorInfo executionErrorInfo = ExecutionErrorInfo.newBuilder().setMessage("some-message").build();

    Map<Object, Object> expectedDocument = ImmutableMap.builder()
                                               .put(RECAST_KEY, ExecutionErrorInfo.class.getName())
                                               .put(ENCODED_VALUE, JsonFormat.printer().print(executionErrorInfo))
                                               .build();

    Map<String, Object> document = RecastOrchestrationUtils.toMap(executionErrorInfo);
    assertThat(document).isNotNull();
    assertThat(document).isEqualTo(expectedDocument);

    ExecutionErrorInfo recastedClass = RecastOrchestrationUtils.fromMap(document, ExecutionErrorInfo.class);
    assertThat(recastedClass).isEqualTo(executionErrorInfo);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecastWithProtoNodeExecution() throws InvalidProtocolBufferException {
    NodeExecutionProto nodeExecutionProto =
        NodeExecutionProto.newBuilder()
            .setAmbiance(
                Ambiance.newBuilder()
                    .addLevels(Level.newBuilder().setRuntimeId("runtimeId").setIdentifier("identifier").build())
                    .setPlanExecutionId("planExecutionId")
                    .build())
            .setStartTs(Timestamp.newBuilder().setSeconds(15).build())
            .setEndTs(Timestamp.newBuilder().setSeconds(20).build())
            .setStatus(Status.SUCCEEDED)
            .addExecutableResponses(
                ExecutableResponse.newBuilder()
                    .setAsync(AsyncExecutableResponse.newBuilder().addCallbackIds("callbackId").build())
                    .build())
            .build();

    Map<Object, Object> expectedDocument = ImmutableMap.builder()
                                               .put(RECAST_KEY, NodeExecutionProto.class.getName())
                                               .put(ENCODED_VALUE, JsonFormat.printer().print(nodeExecutionProto))
                                               .build();

    Map<String, Object> document = RecastOrchestrationUtils.toMap(nodeExecutionProto);
    assertThat(document).isNotNull();
    assertThat(document).isEqualTo(expectedDocument);

    NodeExecutionProto recastedClass = RecastOrchestrationUtils.fromMap(document, NodeExecutionProto.class);
    assertThat(recastedClass).isEqualTo(nodeExecutionProto);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecastWithProtoNodeExecutionFromOnly() throws InvalidProtocolBufferException {
    NodeExecutionProto nodeExecutionProto =
        NodeExecutionProto.newBuilder()
            .setAmbiance(
                Ambiance.newBuilder()
                    .addLevels(Level.newBuilder().setRuntimeId("runtimeId").setIdentifier("identifier").build())
                    .setPlanExecutionId("planExecutionId")
                    .build())
            .setStartTs(Timestamp.newBuilder().setSeconds(15).build())
            .setEndTs(Timestamp.newBuilder().setSeconds(20).build())
            .setStatus(Status.SUCCEEDED)
            .addExecutableResponses(
                ExecutableResponse.newBuilder()
                    .setAsync(AsyncExecutableResponse.newBuilder().addCallbackIds("callbackId").build())
                    .build())
            .build();

    Map<Object, Object> document = ImmutableMap.builder()
                                       .put(RECAST_KEY, NodeExecutionProto.class.getName())
                                       .put(ENCODED_VALUE, JsonFormat.printer().print(nodeExecutionProto))
                                       .build();

    NodeExecutionProto recastedClass = RecastOrchestrationUtils.fromMap((Map) document, NodeExecutionProto.class);
    assertThat(recastedClass).isEqualTo(nodeExecutionProto);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldRecastWithYamlField() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));

    ObjectNode objectNode = new ObjectNode(JsonNodeFactory.instance);
    objectNode.set("stage", yamlField.getNode().getCurrJsonNode());

    Map<String, Object> document = RecastOrchestrationUtils.toMap(objectNode);
    ObjectNode objectNode1 = RecastOrchestrationUtils.fromMap(document, ObjectNode.class);
    assertThat(objectNode1).isEqualTo(objectNode);

    Map<String, Object> doc = RecastOrchestrationUtils.toMap(yamlField);
    YamlField yamlField1 = RecastOrchestrationUtils.fromMap(doc, YamlField.class);
    assertThat(yamlField1).isEqualTo(yamlField);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldRecastWithYamlNodeWrapperConfigList() {
    ObjectMapper objectMapper = new ObjectMapper();

    YamlNodeWrapperConfig yamlNodeWrapperConfig = YamlNodeWrapperConfig.builder()
                                                      .step(objectMapper.createArrayNode())
                                                      .parallel(objectMapper.createObjectNode())
                                                      .nullNode(NullNode.getInstance())
                                                      .stepGroup(NullNode.getInstance())
                                                      .booleanNode(BooleanNode.getTrue())
                                                      .bigIntegerNode(BigIntegerNode.valueOf(BigInteger.TEN))
                                                      .binaryNode(BinaryNode.valueOf(ALEXEI.getBytes()))
                                                      .doubleNode(DoubleNode.valueOf(Double.MAX_VALUE))
                                                      .intNode(IntNode.valueOf(Integer.MAX_VALUE))
                                                      .longNode(LongNode.valueOf(Long.MAX_VALUE))
                                                      .objectNode(objectMapper.createObjectNode())
                                                      .shortNode(ShortNode.valueOf((short) 1))
                                                      .textNode(TextNode.valueOf(ALEXEI))
                                                      .build();

    YamlNodeWrapperConfig yamlNodeWrapperConfig1 = YamlNodeWrapperConfig.builder()
                                                       .step(objectMapper.createArrayNode())
                                                       .parallel(objectMapper.createObjectNode())
                                                       .nullNode(NullNode.getInstance())
                                                       .stepGroup(NullNode.getInstance())
                                                       .booleanNode(BooleanNode.getTrue())
                                                       .bigIntegerNode(BigIntegerNode.valueOf(BigInteger.TEN))
                                                       .binaryNode(BinaryNode.valueOf(ALEXEI.getBytes()))
                                                       .doubleNode(DoubleNode.valueOf(Double.MAX_VALUE))
                                                       .intNode(IntNode.valueOf(Integer.MAX_VALUE))
                                                       .longNode(LongNode.valueOf(Long.MAX_VALUE))
                                                       .objectNode(objectMapper.createObjectNode())
                                                       .shortNode(ShortNode.valueOf((short) 1))
                                                       .textNode(TextNode.valueOf(ALEXEI))
                                                       .build();

    YamlNodeWrapperConfigList wrapperConfigListi = new YamlNodeWrapperConfigList();
    wrapperConfigListi.setList(ImmutableList.of(yamlNodeWrapperConfig, yamlNodeWrapperConfig1));

    Map<String, Object> document = RecastOrchestrationUtils.toMap(wrapperConfigListi);
    YamlNodeWrapperConfigList objectNode1 = RecastOrchestrationUtils.fromMap(document, YamlNodeWrapperConfigList.class);
    assertThat(objectNode1).isEqualTo(wrapperConfigListi);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  private static class YamlNodeWrapperConfig {
    String uuid;
    @Setter JsonNode step;
    @Setter JsonNode parallel;
    @Setter JsonNode stepGroup;
    @Setter JsonNode nullNode;
    @Setter JsonNode booleanNode;
    @Setter JsonNode bigIntegerNode;
    @Setter JsonNode binaryNode;
    @Setter JsonNode doubleNode;
    @Setter JsonNode intNode;
    @Setter JsonNode longNode;
    @Setter JsonNode objectNode;
    @Setter JsonNode shortNode;
    @Setter JsonNode textNode;
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  private static class YamlNodeWrapperConfigList {
    @Setter List<YamlNodeWrapperConfig> list;
  }
}
