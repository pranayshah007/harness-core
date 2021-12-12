package io.harness.pms.sdk.core.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestBatch;
import io.harness.pms.contracts.governance.ExpansionRequestProto;
import io.harness.pms.contracts.governance.ExpansionResponseBatch;
import io.harness.pms.contracts.governance.ExpansionResponseProto;
import io.harness.pms.sdk.core.registries.JsonExpansionHandlerRegistry;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import java.util.List;
import lombok.Getter;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class JsonExpansionServiceTest extends CategoryTest {
  @InjectMocks JsonExpansionService jsonExpansionService;
  @Mock JsonExpansionHandlerRegistry expansionHandlerRegistry;
  @Mock ExceptionManager exceptionManager;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doReturn(new NoOpExpansionHandler()).when(expansionHandlerRegistry).obtain("connectorRef");
    doReturn(new NoOpExpansionHandler()).when(expansionHandlerRegistry).obtain("serviceRef");
    doReturn(new NoOpExpansionHandler()).when(expansionHandlerRegistry).obtain("serviceRef");
    InvalidRequestException invalidRequestException = new InvalidRequestException("Not present");
    when(expansionHandlerRegistry.obtain("fqn")).thenThrow(invalidRequestException);
    doReturn(invalidRequestException).when(exceptionManager).processException(invalidRequestException);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExpand() {
    ExpansionRequestProto req1 = ExpansionRequestProto.newBuilder()
                                     .setFqn("fqn/connectorRef")
                                     .setValue(ByteString.copyFromUtf8("value1"))
                                     .build();
    ExpansionRequestProto req2 = ExpansionRequestProto.newBuilder()
                                     .setFqn("fqn/connectorRef")
                                     .setValue(ByteString.copyFromUtf8("value2"))
                                     .build();
    ExpansionRequestBatch requestBatch =
        ExpansionRequestBatch.newBuilder().addExpansionRequestProto(req1).addExpansionRequestProto(req2).build();
    DummyStreamObserver<ExpansionResponseBatch> responseObserver = new DummyStreamObserver<>();

    jsonExpansionService.expand(requestBatch, responseObserver);
    assertThat(responseObserver.getExpansionResponseBatch()).isNotNull();
    List<ExpansionResponseProto> responseProtoList =
        responseObserver.getExpansionResponseBatch().getExpansionResponseProtoList();
    assertThat(responseProtoList).hasSize(2);
    assertThat(responseProtoList.get(0).getSuccess()).isTrue();
    assertThat(responseProtoList.get(1).getSuccess()).isTrue();

    ExpansionRequestProto req3 = ExpansionRequestProto.newBuilder()
                                     .setFqn("connectorRef/fqn")
                                     .setValue(ByteString.copyFromUtf8("value3"))
                                     .build();

    ExpansionRequestBatch requestBatch2 =
        ExpansionRequestBatch.newBuilder().addExpansionRequestProto(req3).addExpansionRequestProto(req1).build();
    DummyStreamObserver<ExpansionResponseBatch> responseObserver2 = new DummyStreamObserver<>();
    jsonExpansionService.expand(requestBatch2, responseObserver2);
    List<ExpansionResponseProto> responseProtoList2 =
        responseObserver2.getExpansionResponseBatch().getExpansionResponseProtoList();
    assertThat(responseProtoList2).hasSize(2);
    assertThat(responseProtoList2.get(0).getSuccess()).isFalse();
    assertThat(responseProtoList2.get(0).getErrorMessage()).isEqualTo("INVALID_REQUEST");
    assertThat(responseProtoList2.get(0).getFqn()).isEqualTo("connectorRef/fqn");
    assertThat(responseProtoList2.get(1).getSuccess()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testConvertToProto() {
    String fqn = "fqn";
    ExpansionResponse fullResponse = ExpansionResponse.builder()
                                         .success(true)
                                         .errorMessage("")
                                         .key("key")
                                         .value(StringExpandedValue.builder().value("val").build())
                                         .placement(ExpansionPlacementStrategy.PARALLEL)
                                         .build();
    ExpansionResponseProto proto1 = jsonExpansionService.convertToResponseProto(fullResponse, fqn);
    assertThat(proto1.getFqn()).isEqualTo(fqn);
    assertThat(proto1.getSuccess()).isEqualTo(true);
    assertThat(proto1.getErrorMessage()).isEmpty();
    assertThat(proto1.getKey()).isEqualTo("key");
    assertThat(proto1.getPlacement()).isEqualTo(ExpansionPlacementStrategy.PARALLEL);
    assertThat(proto1.getValue()).isEqualTo("val");
  }

  private static class DummyStreamObserver<T> implements StreamObserver<T> {
    @Getter ExpansionResponseBatch expansionResponseBatch;
    @Override
    public void onNext(Object o) {
      expansionResponseBatch = (ExpansionResponseBatch) o;
    }
    @Override
    public void onError(Throwable throwable) {}
    @Override
    public void onCompleted() {}
  }
}