/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.http;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.HttpCertificateNG;
import io.harness.category.element.UnitTests;
import io.harness.common.NGTimeConversionHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import software.wings.beans.TaskType;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.joor.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class HttpStepTest extends CategoryTest {
  @Inject private StepElementParameters stepElementParameters;
  @Inject private Ambiance ambiance;

  @Mock EngineExpressionService engineExpressionService;
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  @InjectMocks private HttpStepParameters httpStepParameters;

  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ILogStreamingStepClient iLogStreamingStepClient;
  @Mock private NGLogCallback ngLogCallback;
  @InjectMocks HttpStep httpStep;
  @Mock private StepHelper stepHelper;
  private MockedStatic<TaskRequestsUtils> aStatic;

  private String TEST_URL = "https://www.google.com";

  @Before
  public void setup() {
    aStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    Mockito.when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logClient);
    Reflect.on(httpStep).set("engineExpressionService", engineExpressionService);

    ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", "accountId")
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    Mockito.when(pmsFeatureFlagHelper.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
  }

  @After
  public void cleanup() {
    aStatic.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testOutputVariablesEvaluation() {
    String body = "{\n"
        + "    \"status\": \"SUCCESS\",\n"
        + "    \"metaData\": \"metadataValue\",\n"
        + "    \"correlationId\": \"333333344444444\"\n"
        + "}";
    HttpStepResponse response1 = HttpStepResponse.builder().httpResponseBody(body).build();
    ParameterField<Object> var1 =
        ParameterField.createExpressionField(true, "<+json.object(httpResponseBody).metaData>", null, true);
    ParameterField<Object> var2 =
        ParameterField.createExpressionField(true, "<+json.object(httpResponseBody).notPresent>", null, true);
    ParameterField<Object> var3 = ParameterField.createExpressionField(true, "<+json.not.a.valid.expr>", null, true);
    ParameterField<Object> var4 = ParameterField.createValueField("directValue");
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("name1", var1);
    variables.put("name4", var4);

    // mocked pms evaluator
    doReturn("metadataValue")
        .when(engineExpressionService)
        .evaluateExpression(any(), eq("<+json.object(httpResponseBody).metaData>"), any(), any());

    Map<String, String> evaluatedVariables = httpStep.evaluateOutputVariables(variables, response1, ambiance);
    assertThat(evaluatedVariables).isNotEmpty();
    assertThat(evaluatedVariables.get("name1")).isEqualTo("metadataValue");
    assertThat(evaluatedVariables.get("name4")).isEqualTo("directValue");

    variables.put("name2", var2);
    variables.put("name3", var3);

    HttpStepResponse response2 = HttpStepResponse.builder().httpResponseBody(body).build();
    evaluatedVariables = httpStep.evaluateOutputVariables(variables, response2, ambiance);
    assertThat(evaluatedVariables).isNotEmpty();
    assertThat(evaluatedVariables.get("name2")).isNull();
    assertThat(evaluatedVariables.get("name3")).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateAssertions() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String httpResponseBodyFile = "httpResponseBody.txt";
    String httpResponseBody = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(httpResponseBodyFile)), StandardCharsets.UTF_8);
    HttpStepResponse response =
        HttpStepResponse.builder().httpResponseBody(httpResponseBody).httpResponseCode(200).build();
    HttpStepParameters stepParameters = HttpStepParameters.infoBuilder().build();

    // no assertion
    boolean assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    // not a valid assertion
    stepParameters.setAssertion(ParameterField.createValueField("<+httpResponseCode> 200"));
    assertThatThrownBy(() -> HttpStep.validateAssertions(response, stepParameters))
        .hasMessage("Assertion provided is not a valid expression");

    // status code assertion
    stepParameters.setAssertion(ParameterField.createValueField("<+httpResponseCode> == 200"));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    stepParameters.setAssertion(ParameterField.createValueField("<+httpResponseCode> > 200"));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isFalse();

    // json.select() assertions
    stepParameters.setAssertion(ParameterField.createValueField(
        "<+json.select(\"support.url\", httpResponseBody)> == \"https://reqres.in/#support-heading\""));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    //    stepParameters.setAssertion(ParameterField.createValueField(
    //            "<+json.select(\"data[0].id\", httpResponseBody)> == 1"));
    //    assertion = HttpStep.validateAssertions(response, stepParameters);
    //    assertThat(assertion).isTrue();

    stepParameters.setAssertion(ParameterField.createValueField("\"<+pipeline.name>\" == \"http\""));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isFalse();

    // json.object() assertions
    stepParameters.setAssertion(ParameterField.createValueField(
        "<+json.object(httpResponseBody).support.url> == \"https://reqres.in/#support-heading\""));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    stepParameters.setAssertion(ParameterField.createValueField("<+json.object(httpResponseBody).data[0].id> == 1"));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    stepParameters.setAssertion(ParameterField.createValueField("<+json.object(httpResponseBody).page> == 1"));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    // json.list() assertions
    stepParameters.setAssertion(
        ParameterField.createValueField("<+json.list(\"data\", httpResponseBody).get(1).name> == \"fuchsia rose\""));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    stepParameters.setAssertion(
        ParameterField.createValueField("<+json.list(\"data\", httpResponseBody).get(5).id> == 5"));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isFalse();

    // null case
    stepParameters.setAssertion(ParameterField.createValueField(null));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    // empty string case
    stepParameters.setAssertion(ParameterField.createValueField("  "));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    // non expression true case
    stepParameters.setAssertion(ParameterField.createValueField("true"));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    // non expression true case
    stepParameters.setAssertion(ParameterField.createValueField("1 == 5"));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isFalse();

    // boolean expression field
    stepParameters.setAssertion(
        ParameterField.createExpressionField(true, "<+json.object(httpResponseBody).page> == 1", null, false));
    assertion = HttpStep.validateAssertions(response, stepParameters);
    assertThat(assertion).isTrue();

    // non boolean expression field
    stepParameters.setAssertion(
        ParameterField.createExpressionField(true, "<+json.object(httpResponseBody).page>", null, false));
    assertThatThrownBy(() -> HttpStep.validateAssertions(response, stepParameters))
        .hasMessage("Assertion provided is not a valid expression");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testObtainTask() {
    aStatic.when(() -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    httpStepParameters = HttpStepParameters.infoBuilder()
                             .method(ParameterField.createValueField("GET"))
                             .url(ParameterField.createValueField(TEST_URL))
                             .delegateSelectors(ParameterField.createValueField(CollectionUtils.emptyIfNull(
                                 delegateSelectors != null ? delegateSelectors.getValue() : null)))
                             .build();
    stepElementParameters = StepElementParameters.builder().spec(httpStepParameters).build();

    // adding a timeout field
    stepElementParameters = StepElementParameters.builder()
                                .spec(httpStepParameters)
                                .timeout(ParameterField.createValueField("20m"))
                                .build();
    assertThat(httpStep.obtainTask(ambiance, stepElementParameters, null)).isEqualTo(TaskRequest.newBuilder().build());

    // adding headers
    Map<String, String> headers = new HashMap<>();
    headers.put("authorization", "token");
    httpStepParameters.setHeaders(headers);
    stepElementParameters.setSpec(httpStepParameters);
    assertThat(httpStep.obtainTask(ambiance, stepElementParameters, null)).isEqualTo(TaskRequest.newBuilder().build());

    // adding request body
    httpStepParameters.setRequestBody(ParameterField.createValueField("this is the request body"));
    stepElementParameters.setSpec(httpStepParameters);
    assertThat(httpStep.obtainTask(ambiance, stepElementParameters, null)).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleTask() throws Exception {
    ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(iLogStreamingStepClient);

    httpStepParameters = HttpStepParameters.infoBuilder()
                             .method(ParameterField.createValueField("GET"))
                             .url(ParameterField.createValueField(TEST_URL))
                             .delegateSelectors(ParameterField.createValueField(CollectionUtils.emptyIfNull(
                                 delegateSelectors != null ? delegateSelectors.getValue() : null)))
                             .assertion(ParameterField.createValueField("<+httpResponseCode> == 200"))
                             .build();
    stepElementParameters = StepElementParameters.builder().spec(httpStepParameters).build();

    // assertion true
    HttpStepResponse httpStepResponse = HttpStepResponse.builder()
                                            .httpResponseCode(200)
                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                            .errorMessage("No error message")
                                            .build();
    StepResponse stepResponse = httpStep.handleTaskResult(ambiance, stepElementParameters, () -> httpStepResponse);
    HttpOutcome outcome = (HttpOutcome) stepResponse.getStepOutcomes().iterator().next().getOutcome();

    assertThat(stepResponse.getStatus().name().equals(Status.SUCCEEDED.name())).isEqualTo(true);
    assertThat(outcome.getHttpMethod().equals("GET")).isEqualTo(true);
    assertThat(outcome.getHttpUrl().equals(TEST_URL)).isEqualTo(true);
    assertThat(outcome.getHttpResponseCode()).isEqualTo(200);
    assertThat(outcome.getStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(outcome.getErrorMsg()).isEqualTo("No error message");

    // assertion false
    httpStepParameters.setAssertion(ParameterField.createValueField("<+httpResponseCode> > 201"));
    stepElementParameters = StepElementParameters.builder().spec(httpStepParameters).build();

    stepResponse = httpStep.handleTaskResult(ambiance, stepElementParameters, () -> httpStepResponse);
    assertThat(stepResponse.getStatus().name().equals(Status.FAILED.name())).isEqualTo(true);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("assertion failed");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testObtainTaskWithExpressionBody() {
    // expected task parameters as argument matchers
    HttpTaskParametersNg expectedTaskParams = HttpTaskParametersNg.builder()
                                                  .body("namespace: <+input>")
                                                  .url(TEST_URL)
                                                  .method("GET")
                                                  .socketTimeoutMillis(1200000)
                                                  .build();
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(NGTimeConversionHelper.convertTimeStringToMilliseconds("20m"))
                                  .taskType(TaskType.HTTP_TASK_NG.name())
                                  .parameters(new Object[] {expectedTaskParams})
                                  .build();

    aStatic.when(() -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    httpStepParameters = HttpStepParameters.infoBuilder()
                             .method(ParameterField.createValueField("GET"))
                             .url(ParameterField.createValueField(TEST_URL))
                             .requestBody(ParameterField.createExpressionField(true, "namespace: <+input>", null, true))
                             .delegateSelectors(ParameterField.createValueField(CollectionUtils.emptyIfNull(
                                 delegateSelectors != null ? delegateSelectors.getValue() : null)))
                             .build();
    stepElementParameters = StepElementParameters.builder().spec(httpStepParameters).build();

    // adding a timeout field
    stepElementParameters = StepElementParameters.builder()
                                .spec(httpStepParameters)
                                .timeout(ParameterField.createValueField("20m"))
                                .build();

    // non null task request
    assertThat(httpStep.obtainTask(ambiance, stepElementParameters, null)).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateCertificateReturnsEmptyIfCertAndCertKeyIsEmpty() {
    HttpStepParameters httpStepParameters = HttpStepParameters.infoBuilder()
                                                .certificate(ParameterField.createValueField(""))
                                                .certificateKey(ParameterField.createValueField(""))
                                                .build();
    Optional<HttpCertificateNG> certificate = httpStep.createCertificate(httpStepParameters);
    assertThat(certificate).isEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateCertificateCertKeyCanBeEmpty() {
    HttpStepParameters httpStepParameters = HttpStepParameters.infoBuilder()
                                                .certificate(ParameterField.createValueField("value"))
                                                .certificateKey(ParameterField.createValueField(""))
                                                .build();
    Optional<HttpCertificateNG> certificate = httpStep.createCertificate(httpStepParameters);
    assertThat(certificate).isNotEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateCertificateCertCannotBeEmpty() {
    assertThatThrownBy(()
                           -> httpStep.createCertificate(HttpStepParameters.infoBuilder()
                                                             .certificate(ParameterField.createValueField(""))
                                                             .certificateKey(ParameterField.createValueField("value"))
                                                             .build()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testObtainTaskWithInputVariables() {
    aStatic.when(() -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    List<NGVariable> inputVariables = new ArrayList<>();
    inputVariables.add(
        StringNGVariable.builder().name("URL").value(ParameterField.createValueField("https://www.abc.xyz")).build());
    inputVariables.add(
        StringNGVariable.builder().name("MY_CODE").value(ParameterField.createValueField("200")).build());

    httpStepParameters =
        HttpStepParameters.infoBuilder()
            .method(ParameterField.createValueField("GET"))
            .url(ParameterField.createValueField(TEST_URL))
            .delegateSelectors(ParameterField.createValueField(
                CollectionUtils.emptyIfNull(delegateSelectors != null ? delegateSelectors.getValue() : null)))
            .inputVariables(NGVariablesUtils.getMapOfVariables(inputVariables))
            .assertion(ParameterField.createValueField("<+httpResponseCode>==<+spec.inputVariables.MY_CODE>"))
            .build();

    assertThat(httpStepParameters.getInputVariables().getValue().get("MY_CODE")).isNotNull();
    assertThat(httpStepParameters.getInputVariables().getValue().get("URL")).isNotNull();

    // adding a timeout field
    stepElementParameters = StepElementParameters.builder()
                                .spec(httpStepParameters)
                                .timeout(ParameterField.createValueField("20m"))
                                .build();

    assertThat(httpStep.obtainTask(ambiance, stepElementParameters, null)).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testEncodeURL() {
    NGLogCallback logCallback = mock(NGLogCallback.class);
    String url1 = "https://www.example.com/path%20with%20encoded%20spaces";
    assertThat(httpStep.encodeURL(url1, logCallback)).isEqualTo(url1);

    String url2 =
        "https://www.example.com/Apply MS patches AMA Prod servers (Monthly-Sun)?api-version=2017-05-15-preview";
    String expected2 =
        "https://www.example.com/Apply%20MS%20patches%20AMA%20Prod%20servers%20(Monthly-Sun)?api-version=2017-05-15-preview";
    assertThat(httpStep.encodeURL(url2, logCallback)).isEqualTo(expected2);
    verify(logCallback)
        .saveExecutionLog(eq(
            "Encoded URL: https://www.example.com/Apply%20MS%20patches%20AMA%20Prod%20servers%20(Monthly-Sun)?api-version=2017-05-15-preview"));

    String url3 = "https://www.example.com/@user?param=value";
    assertThat(httpStep.encodeURL(url3, logCallback)).isEqualTo(url3);
    verify(logCallback).saveExecutionLog(eq("Encoded URL: https://www.example.com/@user?param=value"));

    String url4 = "https://www.example.com/already%20encoded?param=value";
    assertThat(httpStep.encodeURL(url4, logCallback)).isEqualTo(url4);

    String url5 = "";
    assertThat(httpStep.encodeURL(url5, logCallback)).isEqualTo(url5);
  }
}
