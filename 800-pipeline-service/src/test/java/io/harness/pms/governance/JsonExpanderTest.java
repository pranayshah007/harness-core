package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.governance.ExpansionRequestBatch;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.contracts.governance.ExpansionResponseBatch;
import io.harness.pms.contracts.governance.ExpansionResponseProto;
import io.harness.pms.contracts.governance.JsonExpansionServiceGrpc;
import io.harness.pms.contracts.governance.JsonExpansionServiceGrpc.JsonExpansionServiceBlockingStub;
import io.harness.pms.contracts.governance.JsonExpansionServiceGrpc.JsonExpansionServiceImplBase;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class JsonExpanderTest extends CategoryTest {
  @InjectMocks JsonExpander jsonExpander;
  JsonExpansionServiceBlockingStub blockingStub;
  @Rule public GrpcCleanupRule grpcCleanup;
  JsonExpansionServiceImplBase jsonExpansionServiceImplBase;

  @Before
  public void setUp() {
    grpcCleanup = new GrpcCleanupRule();

    jsonExpansionServiceImplBase = new JsonExpansionServiceImplBase() {
      @Override
      public void expand(ExpansionRequestBatch request, StreamObserver<ExpansionResponseBatch> responseObserver) {
        responseObserver.onNext(ExpansionResponseBatch.newBuilder()
                                    .addExpansionResponseProto(ExpansionResponseProto.newBuilder()
                                                                   .setFqn("fqn/connectorRef")
                                                                   .setKey("proofThatItIsFromHere")
                                                                   .setSuccess(true)
                                                                   .build())
                                    .build());
        responseObserver.onCompleted();
      }
    };
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchExpansionResponses() throws IOException {
    grpcCleanup.register(InProcessServerBuilder.forName("expander")
                             .directExecutor()
                             .addService(jsonExpansionServiceImplBase)
                             .build()
                             .start());
    ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName("expander").directExecutor().build());
    blockingStub = JsonExpansionServiceGrpc.newBlockingStub(channel);
    on(jsonExpander).set("jsonExpansionServiceBlockingStubMap", Collections.singletonMap(ModuleType.PMS, blockingStub));
    on(jsonExpander).set("executor", Executors.newFixedThreadPool(5));

    Set<ExpansionResponseBatch> empty =
        jsonExpander.fetchExpansionResponses(Collections.emptySet(), ExpansionRequestMetadata.getDefaultInstance());
    assertThat(empty).isEmpty();
    ExpansionRequest expansionRequest = ExpansionRequest.builder()
                                            .module(ModuleType.PMS)
                                            .fqn("fqn/connectorRef")
                                            .fieldValue(new TextNode("k8sConn"))
                                            .build();
    Set<ExpansionRequest> oneRequest = Collections.singleton(expansionRequest);
    Set<ExpansionResponseBatch> oneBatch =
        jsonExpander.fetchExpansionResponses(oneRequest, ExpansionRequestMetadata.getDefaultInstance());
    assertThat(oneBatch).hasSize(1);
    ExpansionResponseBatch responseBatch = new ArrayList<>(oneBatch).get(0);
    List<ExpansionResponseProto> batchList = responseBatch.getExpansionResponseProtoList();
    assertThat(batchList).hasSize(1);
    ExpansionResponseProto response = batchList.get(0);
    assertThat(response.getSuccess()).isTrue();
    assertThat(response.getKey()).isEqualTo("proofThatItIsFromHere");
    assertThat(response.getFqn()).isEqualTo("fqn/connectorRef");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBatchExpansionRequests() {
    ExpansionRequest jiraConn1 = ExpansionRequest.builder()
                                     .module(ModuleType.PMS)
                                     .fqn("jir1/connectorRef")
                                     .fieldValue(new TextNode("jiraConn1"))
                                     .build();
    ExpansionRequest jiraConn2 = ExpansionRequest.builder()
                                     .module(ModuleType.PMS)
                                     .fqn("jir2/connectorRef")
                                     .fieldValue(new TextNode("jiraConn2"))
                                     .build();
    ExpansionRequest k8sConn = ExpansionRequest.builder()
                                   .module(ModuleType.CD)
                                   .fqn("k8s/connectorRef")
                                   .fieldValue(new TextNode("k8sConn"))
                                   .build();
    Set<ExpansionRequest> requests = new HashSet<>(Arrays.asList(jiraConn1, jiraConn2, k8sConn));
    Map<ModuleType, ExpansionRequestBatch> expansionRequestBatches =
        jsonExpander.batchExpansionRequests(requests, ExpansionRequestMetadata.getDefaultInstance());
    assertThat(expansionRequestBatches).hasSize(2);
    ExpansionRequestBatch pmsBatch = expansionRequestBatches.get(ModuleType.PMS);
    assertThat(pmsBatch.getExpansionRequestProtoList()).hasSize(2);
    ExpansionRequestBatch cdBatch = expansionRequestBatches.get(ModuleType.CD);
    assertThat(cdBatch.getExpansionRequestProtoList()).hasSize(1);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testConvertToByteString() throws IOException {
    String yaml = "pipeline:\n"
        + "  identifier: s1\n"
        + "  tags:\n"
        + "    a: b\n"
        + "    c: d\n"
        + "  list:\n"
        + "    - l1\n"
        + "    - l2";
    YamlField yamlField = YamlUtils.readTree(yaml);
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();
    JsonNode idNode = pipelineNode.getField("identifier").getNode().getCurrJsonNode();
    JsonNode tagsNode = pipelineNode.getField("tags").getNode().getCurrJsonNode();
    JsonNode listNode = pipelineNode.getField("list").getNode().getCurrJsonNode();
    ByteString idBytes = jsonExpander.convertToByteString(idNode);
    ByteString tagBytes = jsonExpander.convertToByteString(tagsNode);
    ByteString listBytes = jsonExpander.convertToByteString(listNode);
    assertThat(idBytes).isNotNull();
    assertThat(tagBytes).isNotNull();
    assertThat(listBytes).isNotNull();
    assertThat(YamlUtils.readTree(idBytes.toStringUtf8()).getNode().getCurrJsonNode()).isEqualTo(idNode);
    assertThat(YamlUtils.readTree(tagBytes.toStringUtf8()).getNode().getCurrJsonNode()).isEqualTo(tagsNode);
    assertThat(YamlUtils.readTree(listBytes.toStringUtf8()).getNode().getCurrJsonNode()).isEqualTo(listNode);
  }
}