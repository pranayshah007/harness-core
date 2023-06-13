/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression.service;

import static io.harness.expression.service.ExpressionValue.EvaluationStatus.ERROR;
import static io.harness.expression.service.ExpressionValue.EvaluationStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.expression.ExpressionServiceTestBase;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ExpressionEvaluatorServiceTest extends ExpressionServiceTestBase {
  @Rule public GrpcCleanupRule grpcCleanupRule = new GrpcCleanupRule();
  @Inject ExpressionServiceImpl expressionEvaluatorService;
  private ExpressionEvaulatorServiceGrpc.ExpressionEvaulatorServiceBlockingStub expressionEvaulatorServiceBlockingStub;
  private Server testInProcessServer;

  @Before
  public void doSetup() throws IOException {
    String serverName = InProcessServerBuilder.generateName();

    testInProcessServer = grpcCleanupRule.register(InProcessServerBuilder.forName(serverName)
                                                       .directExecutor()
                                                       .addService(expressionEvaluatorService)
                                                       .build()
                                                       .start());
    expressionEvaulatorServiceBlockingStub = ExpressionEvaulatorServiceGrpc.newBlockingStub(
        grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
  }

  @After
  public void doCleanup() {
    testInProcessServer.shutdown();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldEvaluateVariable() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder().setJexl("<+VAR>").setJsonContext("{\"VAR\":\"VALUE\"}").build())
            .build());

    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("VALUE");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldEvaluateSinglyNestedVariable() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("<+VAR1.VAR2>")
                            .setJsonContext("{\"VAR1\":{\"VAR2\":\"VALUE\"}}")
                            .build())

            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("VALUE");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldEvaluateSingleExpression() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("'hello' == 'hello'")
                            .setJsonContext("{\"VAR1\":{\"VAR2\":\"VALUE\"}}")
                            .setIsSkipCondition(true)
                            .build())

            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("true");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldNotEvaluateSingleExpression() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("'hello' == 'hello'")
                            .setJsonContext("{\"VAR1\":{\"VAR2\":\"VALUE\"}}")
                            .setIsSkipCondition(false)
                            .build())

            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("'hello' == 'hello'");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldEvaluateDoublyNestedVariable() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("<+VAR1.VAR2.VAR4>")
                            .setJsonContext("{\"VAR1\":{\"VAR2\":{\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}}}")
                            .build())

            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("VALUE4");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldEvaluateObject() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("<+VAR1.VAR2>")
                            .setJsonContext("{\"VAR1\":{\"VAR2\":{\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}}}")
                            .build())
            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("{\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldEvaluateJsonPathSelection() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(
                ExpressionQuery.newBuilder()
                    .setJexl("<+json.select(\"status\",httpResponseBody)>")
                    .setJsonContext(
                        "{\"httpResponseBody\":\"{ \\\"status\\\" : \\\"200\\\", \\\"message\\\" : \\\"Success\\\" }\"}")
                    .build())

            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("200");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldEvaluateXMLPathSelection() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(
                ExpressionQuery.newBuilder()
                    .setJexl("<+xml.select(\"/response/status\", httpResponseBody)>")
                    .setJsonContext(
                        "{\"httpResponseBody\":\"<response><message>Success</message><status>200</status></response>\"}")
                    .build())
            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("200");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldEvaluateRegexExpression() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("<+regex.extract(\"[0-9]*\", RPM)>")
                            .setJsonContext("{\"RPM\":\"build-webservices-3935-0.noarch.rpm\"}")
                            .build())
            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("3935");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldThrowStatusRuntimeException() {
    ExpressionRequest expressionRequest =
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder().setJexl("<+VAR>").setJsonContext("VALUE").build())
            .build();
    ExpressionResponse expressionResponse =
        expressionEvaulatorServiceBlockingStub.evaluateExpression(expressionRequest);
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(ERROR);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldEvaluateStrSubstitution() {
    ExpressionRequest expressionRequest = ExpressionRequest.newBuilder()
                                              .addQueries(ExpressionQuery.newBuilder()
                                                              .setJexl("Testing <+STRING> substitution")
                                                              .setJsonContext("{\"STRING\":\"string\"}")
                                                              .build())
                                              .build();
    ExpressionResponse expressionResponse =
        expressionEvaulatorServiceBlockingStub.evaluateExpression(expressionRequest);
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("Testing string substitution");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldEvaluateNestedObject() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("<+VAR1.VAR2>")
                            .setJsonContext("{\"VAR1\":{\"VAR2\":{\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}}}")
                            .build())
            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("{\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldEvaluateBooleanObject1() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("<+2==2>")
                            .setJsonContext("{\"VAR1\":{\"VAR2\":{\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}}}")
                            .build())
            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("true");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldEvaluateBooleanObject() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("<+2==2 && 4==4> <+VAR1.VAR2>")
                            .setJsonContext("{\"VAR1\":{\"VAR2\":{\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}}}")
                            .build())
            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("true {\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldKeepUnresolvedExpressionIntact() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("<+HOME>")
                            .setJsonContext("{\"VAR1\":{\"VAR2\":{\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}}}")
                            .build())
            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue()).isEqualTo("null");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldKeepPartialUnresolvedExpressionIntact() {
    ExpressionResponse expressionResponse = expressionEvaulatorServiceBlockingStub.evaluateExpression(
        ExpressionRequest.newBuilder()
            .addQueries(ExpressionQuery.newBuilder()
                            .setJexl("hello - <+VAR1.VAR2>")
                            .setJsonContext("{\"VAR1\":{\"VAR2\":{\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}}}")
                            .build())
            .build());
    assertThat(expressionResponse).isNotNull();
    assertThat(expressionResponse.getValues(0).getValue())
        .isEqualTo("hello - {\"VAR3\":\"VALUE\",\"VAR4\":\"VALUE4\"}");
    assertThat(expressionResponse.getValues(0).getStatusCode()).isEqualTo(SUCCESS);
  }
}
