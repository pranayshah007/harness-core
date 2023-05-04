/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.http;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.HttpCertificateNG;
import io.harness.common.NGTimeConversionHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.http.HttpTaskNG;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.delegate.task.http.HttpTaskParametersNg.HttpTaskParametersNgBuilder;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.http.HttpHeaderConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.executables.PipelineTaskExecutable;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.PmsFeatureFlagHelper;

import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class HttpStep extends PipelineTaskExecutable<HttpStepResponse> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.HTTP_STEP_TYPE;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private StepHelper stepHelper;
  @Inject private EngineExpressionService engineExpressionService;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private NGLogCallback getNGLogCallback(LogStreamingStepClientFactory logStreamingStepClientFactory, Ambiance ambiance,
      String logFix, boolean openStream) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, logFix, openStream);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    int socketTimeoutMillis = (int) NGTimeConversionHelper.convertTimeStringToMilliseconds("10m");
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(HttpTaskNG.COMMAND_UNIT);

    if (stepParameters.getTimeout() != null && stepParameters.getTimeout().getValue() != null) {
      socketTimeoutMillis =
          (int) NGTimeConversionHelper.convertTimeStringToMilliseconds(stepParameters.getTimeout().getValue());
    }
    HttpStepParameters httpStepParameters = (HttpStepParameters) stepParameters.getSpec();

    HttpTaskParametersNgBuilder httpTaskParametersNgBuilder =
        HttpTaskParametersNg.builder()
            .url((String) httpStepParameters.getUrl().fetchFinalValue())
            .method(httpStepParameters.getMethod().getValue())
            .socketTimeoutMillis(socketTimeoutMillis);

    if (EmptyPredicate.isNotEmpty(httpStepParameters.getHeaders())) {
      List<HttpHeaderConfig> headers = new ArrayList<>();
      httpStepParameters.getHeaders().forEach(
          (key, value) -> headers.add(HttpHeaderConfig.builder().key(key).value(value).build()));
      httpTaskParametersNgBuilder.requestHeader(headers);
    }

    if (httpStepParameters.getRequestBody() != null) {
      httpTaskParametersNgBuilder.body((String) httpStepParameters.getRequestBody().fetchFinalValue());
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);
    boolean shouldAvoidCapabilityUsingHeaders =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_NOT_USE_HEADERS_FOR_HTTP_CAPABILITY);
    httpTaskParametersNgBuilder.shouldAvoidHeadersInCapability(shouldAvoidCapabilityUsingHeaders);

    httpTaskParametersNgBuilder.isCertValidationRequired(
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.ENABLE_CERT_VALIDATION));

    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_HTTP_STEP_NG_CERTIFICATE)) {
      createCertificate(httpStepParameters).ifPresent(cert -> { httpTaskParametersNgBuilder.certificateNG(cert); });
    }

    final TaskData taskData =
        TaskData.builder()
            .async(true)
            .timeout(NGTimeConversionHelper.convertTimeStringToMilliseconds(stepParameters.getTimeout().getValue()))
            .taskType(TaskType.HTTP_TASK_NG.name())
            .parameters(new Object[] {httpTaskParametersNgBuilder.build()})
            .build();

    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
            StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT))),
        null, null, TaskSelectorYaml.toTaskSelector(httpStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @VisibleForTesting
  protected Optional<HttpCertificateNG> createCertificate(HttpStepParameters httpStepParameters) {
    if (isEmpty(httpStepParameters.getCertificate().getValue())
        && isEmpty(httpStepParameters.getCertificateKey().getValue())) {
      return Optional.empty();
    }

    if (isEmpty(httpStepParameters.getCertificate().getValue())
        && isNotEmpty(httpStepParameters.getCertificateKey().getValue())) {
      throw new InvalidRequestException(
          "Only certificateKey is provided, we need both certificate and certificateKey or only certificate", USER);
    }

    return Optional.of(HttpCertificateNG.builder()
                           .certificate(httpStepParameters.getCertificate().getValue())
                           .certificateKey(httpStepParameters.getCertificateKey().getValue())
                           .build());
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<HttpStepResponse> responseSupplier) throws Exception {
    try {
      NGLogCallback logCallback =
          getNGLogCallback(logStreamingStepClientFactory, ambiance, HttpTaskNG.COMMAND_UNIT, false);

      StepResponseBuilder responseBuilder = StepResponse.builder();
      HttpStepResponse httpStepResponse = responseSupplier.get();

      HttpStepParameters httpStepParameters = (HttpStepParameters) stepParameters.getSpec();
      logCallback.saveExecutionLog(
          String.format("Successfully executed the http request %s .", fetchFinalValue(httpStepParameters.getUrl())));

      Map<String, Object> outputVariables =
          httpStepParameters.getOutputVariables() == null ? null : httpStepParameters.getOutputVariables().getValue();
      Map<String, String> outputVariablesEvaluated =
          evaluateOutputVariables(outputVariables, httpStepResponse, ambiance);

      logCallback.saveExecutionLog("Validating the assertions...");
      boolean assertionSuccessful = validateAssertions(httpStepResponse, httpStepParameters);
      HttpOutcome executionData = HttpOutcome.builder()
                                      .httpUrl(fetchFinalValue(httpStepParameters.getUrl()))
                                      .httpMethod(fetchFinalValue(httpStepParameters.getMethod()))
                                      .httpResponseCode(httpStepResponse.getHttpResponseCode())
                                      .httpResponseBody(httpStepResponse.getHttpResponseBody())
                                      .status(httpStepResponse.getCommandExecutionStatus())
                                      .errorMsg(httpStepResponse.getErrorMessage())
                                      .outputVariables(outputVariablesEvaluated)
                                      .build();

      if (!assertionSuccessful) {
        responseBuilder.status(Status.FAILED);
        responseBuilder.failureInfo(FailureInfo.newBuilder().setErrorMessage("assertion failed").build());
        logCallback.saveExecutionLog("Assertions failed", LogLevel.INFO, CommandExecutionStatus.FAILURE);
      } else {
        responseBuilder.status(Status.SUCCEEDED);
        logCallback.saveExecutionLog("Assertions passed", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      }
      responseBuilder.stepOutcome(
          StepOutcome.builder().name(YAMLFieldNameConstants.OUTPUT).outcome(executionData).build());

      return responseBuilder.build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  private String fetchFinalValue(ParameterField<String> field) {
    return (String) field.fetchFinalValue();
  }

  private void closeLogStream(Ambiance ambiance) {
    try {
      Thread.sleep(500, 0);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Close Log Stream was interrupted", e);
    } finally {
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      logStreamingStepClient.closeAllOpenStreamsWithPrefix(StepUtils.generateLogKeys(ambiance, emptyList()).get(0));
    }
  }

  public static boolean validateAssertions(HttpStepResponse httpStepResponse, HttpStepParameters stepParameters) {
    if (ParameterField.isNull(stepParameters.getAssertion())) {
      return true;
    }

    HttpExpressionEvaluator evaluator = new HttpExpressionEvaluator(httpStepResponse);
    String assertion = (String) stepParameters.getAssertion().fetchFinalValue();
    if (assertion == null || isEmpty(assertion.trim())) {
      return true;
    }

    try {
      Object value = evaluator.evaluateExpression(assertion);
      if (!(value instanceof Boolean)) {
        throw new InvalidRequestException(String.format(
            "Expected boolean assertion, got %s value", value == null ? "null" : value.getClass().getSimpleName()));
      }
      return (boolean) value;
    } catch (Exception e) {
      throw new InvalidRequestException("Assertion provided is not a valid expression", e);
    }
  }

  public Map<String, String> evaluateOutputVariables(
      Map<String, Object> outputVariables, HttpStepResponse httpStepResponse, Ambiance ambiance) {
    Map<String, String> outputVariablesEvaluated = new LinkedHashMap<>();
    if (outputVariables != null) {
      Map<String, String> contextMap = buildContextMapFromResponse(httpStepResponse);
      outputVariables.keySet().forEach(name -> {
        Object expression = outputVariables.get(name);
        if (expression instanceof ParameterField) {
          ParameterField<?> expr = (ParameterField<?>) expression;
          if (expr.isExpression()) {
            Object evaluatedValue = engineExpressionService.evaluateExpression(
                ambiance, expr.getExpressionValue(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED, contextMap);
            if (evaluatedValue != null) {
              outputVariablesEvaluated.put(name, evaluatedValue.toString());
            }
          } else if (expr.getValue() != null) {
            outputVariablesEvaluated.put(name, expr.getValue().toString());
          }
        }
      });
    }
    return outputVariablesEvaluated;
  }

  private Map<String, String> buildContextMapFromResponse(HttpStepResponse httpStepResponse) {
    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("httpResponseBody", httpStepResponse.getHttpResponseBody());
    contextMap.put("httpResponseCode", String.valueOf(httpStepResponse.getHttpResponseCode()));
    return contextMap;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, TaskExecutableResponse executableResponse) {
    closeLogStream(ambiance);
  }
}
