/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.NodeExecutionProto;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeProjectionUtilsTest extends PmsSdkCoreTestBase {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testResolveObject() {
    Dummy dummyInner = Dummy.builder()
                           .strVal1("a")
                           .intVal1(1)
                           .strVal2(ParameterField.createValueField("b"))
                           .intVal2(ParameterField.createExpressionField(true, "<+tmp1>", null, false))
                           .dummy(ParameterField.createExpressionField(true, "<+tmp2>", null, false))
                           .build();
    Dummy dummy = Dummy.builder()
                      .strVal1("c")
                      .intVal1(2)
                      .strVal2(ParameterField.createExpressionField(true, "<+tmp3>", null, true))
                      .intVal2(ParameterField.createValueField(3))
                      .dummy(ParameterField.createValueField(dummyInner))
                      .build();

    Map<String, Object> map = RecastOrchestrationUtils.toMap(dummy);
    Object resp = NodeExecutionUtils.resolveObject(map);
    assertThat(resp).isNotNull();
    assertThat(resp).isInstanceOf(Map.class);

    map = (Map<String, Object>) resp;
    assertThat(map.get("strVal1")).isEqualTo("c");
    assertThat(map.get("intVal1")).isEqualTo(2);
    assertThat(map.get("strVal2")).isEqualTo("<+tmp3>");
    assertThat(map.get("intVal2")).isEqualTo(3);

    map = (Map<String, Object>) map.get("dummy");
    assertThat(map).isNotNull();
    assertThat(map.get("strVal1")).isEqualTo("a");
    assertThat(map.get("intVal1")).isEqualTo(1);
    assertThat(map.get("strVal2")).isEqualTo("b");
    assertThat(map.get("intVal2")).isEqualTo("<+tmp1>");
    assertThat(map.get("dummy")).isEqualTo("<+tmp2>");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRetryCount() {
    NodeExecutionProto nodeExecutionProto = NodeExecutionProto.newBuilder().addRetryIds("retry1").build();
    assertThat(NodeExecutionUtils.retryCount(nodeExecutionProto)).isEqualTo(1);

    nodeExecutionProto = NodeExecutionProto.newBuilder().build();
    assertThat(NodeExecutionUtils.retryCount(nodeExecutionProto)).isEqualTo(0);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainLatestExecutableResponse() {
    NodeExecutionProto nodeExecutionProto =
        NodeExecutionProto.newBuilder().addExecutableResponses(ExecutableResponse.newBuilder().build()).build();
    assertThat(NodeExecutionUtils.obtainLatestExecutableResponse(nodeExecutionProto))
        .isEqualTo(ExecutableResponse.newBuilder().build());

    nodeExecutionProto = NodeExecutionProto.newBuilder().build();
    assertThat(NodeExecutionUtils.obtainLatestExecutableResponse(nodeExecutionProto)).isEqualTo(null);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testConstructFailureInfo() {
    Exception ex = new InvalidRequestException("Invalid Request");
    assertThat(NodeExecutionUtils.constructFailureInfo(ex))
        .isEqualTo(FailureInfo.newBuilder().setErrorMessage("INVALID_REQUEST").build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testConstructStepResponse() {
    Exception ex = new InvalidRequestException("Invalid Request");
    assertThat(NodeExecutionUtils.constructStepResponse(ex))
        .isEqualTo(StepResponseProto.newBuilder()
                       .setStatus(Status.FAILED)
                       .setFailureInfo(FailureInfo.newBuilder().setErrorMessage("INVALID_REQUEST").build())
                       .build());
  }

  @Data
  @Builder
  private static class Dummy {
    private String strVal1;
    private Integer intVal1;
    private ParameterField<String> strVal2;
    private ParameterField<Integer> intVal2;
    private ParameterField<Dummy> dummy;
  }
}
