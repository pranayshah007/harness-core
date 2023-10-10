/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.http;

import static io.harness.expression.EngineExpressionEvaluator.PIE_EXECUTION_JSON_SUPPORT;
import static io.harness.rule.OwnerRule.NAMAN;
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

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.HttpCertificateNG;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class HttpStepUtilsTest {
  @InjectMocks HttpStepUtils httpStepUtils;
  @Captor private ArgumentCaptor<Map<String, String>> argCaptor;
  @Mock EngineExpressionService engineExpressionService;
  @Inject private Ambiance ambiance;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Before
  public void setup() {
    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    //        Mockito.when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logClient);

    ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", "accountId")
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    Mockito.when(pmsFeatureFlagHelper.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
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

    Ambiance ambianceBuilder =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .putSettingToValueMap(NGPipelineSettingsConstant.ENABLE_EXPRESSION_ENGINE_V2.getName(), "true")
                    .build())
            .build();

    doReturn("metadataValue")
        .when(engineExpressionService)
        .evaluateExpression(any(), eq("<+json.object(httpResponseBody).metaData>"), any(), any());

    Map<String, String> evaluatedVariables =
        httpStepUtils.evaluateOutputVariables(variables, response1, ambianceBuilder);
    verify(engineExpressionService).evaluateExpression(eq(ambianceBuilder), anyString(), any(), argCaptor.capture());
    Map<String, String> output = argCaptor.getValue();
    assertThat(output).isNotEmpty();
    assertThat(output.get("ENABLED_FEATURE_FLAGS")).isEqualTo(PIE_EXECUTION_JSON_SUPPORT);
    assertThat(evaluatedVariables).isNotEmpty();
    assertThat(evaluatedVariables.get("name1")).isEqualTo("metadataValue");
    assertThat(evaluatedVariables.get("name4")).isEqualTo("directValue");
    assertThat(evaluatedVariables.get("name4")).isEqualTo("directValue");

    variables.put("name2", var2);
    variables.put("name3", var3);

    HttpStepResponse response2 = HttpStepResponse.builder().httpResponseBody(body).build();
    evaluatedVariables = httpStepUtils.evaluateOutputVariables(variables, response2, ambiance);
    assertThat(evaluatedVariables).isNotEmpty();
    assertThat(evaluatedVariables.get("name2")).isNull();
    assertThat(evaluatedVariables.get("name3")).isNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateCertificateReturnsEmptyIfCertAndCertKeyIsEmpty() {
    HttpStepParameters httpStepParameters = HttpStepParameters.infoBuilder()
                                                .certificate(ParameterField.createValueField(""))
                                                .certificateKey(ParameterField.createValueField(""))
                                                .build();
    Optional<HttpCertificateNG> certificate =
        httpStepUtils.createCertificate(httpStepParameters.getCertificate(), httpStepParameters.getCertificateKey());
    assertThat(certificate).isEmpty();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testEncodeURL() {
    NGLogCallback logCallback = mock(NGLogCallback.class);
    String url1 = "https://www.example.com/path%20with%20encoded%20spaces";
    assertThat(httpStepUtils.encodeURL(url1, logCallback)).isEqualTo(url1);

    String url2 =
        "https://www.example.com/Apply MS patches AMA Prod servers (Monthly-Sun)?api-version=2017-05-15-preview";
    String expected2 =
        "https://www.example.com/Apply%20MS%20patches%20AMA%20Prod%20servers%20(Monthly-Sun)?api-version=2017-05-15-preview";
    assertThat(httpStepUtils.encodeURL(url2, logCallback)).isEqualTo(expected2);
    verify(logCallback)
        .saveExecutionLog(eq(
            "Encoded URL: https://www.example.com/Apply%20MS%20patches%20AMA%20Prod%20servers%20(Monthly-Sun)?api-version=2017-05-15-preview"));

    String url3 = "https://www.example.com/@user?param=value";
    assertThat(httpStepUtils.encodeURL(url3, logCallback)).isEqualTo(url3);
    verify(logCallback).saveExecutionLog(eq("Encoded URL: https://www.example.com/@user?param=value"));

    String url4 = "https://www.example.com/already%20encoded?param=value";
    assertThat(httpStepUtils.encodeURL(url4, logCallback)).isEqualTo(url4);

    String url5 = "";
    assertThat(httpStepUtils.encodeURL(url5, logCallback)).isEqualTo(url5);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateCertificateCertKeyCanBeEmpty() {
    HttpStepParameters httpStepParameters = HttpStepParameters.infoBuilder()
                                                .certificate(ParameterField.createValueField("value"))
                                                .certificateKey(ParameterField.createValueField(""))
                                                .build();
    Optional<HttpCertificateNG> certificate =
        httpStepUtils.createCertificate(httpStepParameters.getCertificate(), httpStepParameters.getCertificateKey());
    assertThat(certificate).isNotEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateCertificateCertCannotBeEmpty() {
    assertThatThrownBy(()
                           -> httpStepUtils.createCertificate(
                               ParameterField.createValueField(""), ParameterField.createValueField("value")))
        .isInstanceOf(InvalidRequestException.class);
  }
}
