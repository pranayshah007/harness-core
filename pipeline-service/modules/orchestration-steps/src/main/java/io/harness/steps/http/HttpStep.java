/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.http;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.common.NGTimeConversionHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.http.HttpTaskNG;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.delegate.task.http.HttpTaskParametersNg.HttpTaskParametersNgBuilder;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.http.HttpHeaderConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@Slf4j
public class HttpStep extends PipelineTaskExecutable<HttpStepResponse> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.HTTP_STEP_TYPE;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private StepHelper stepHelper;
  @Inject HttpStepUtils httpStepUtils;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    int socketTimeoutMillis = (int) NGTimeConversionHelper.convertTimeStringToMilliseconds("10m");
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(HttpTaskNG.COMMAND_UNIT);

    if (stepParameters.getTimeout() != null && stepParameters.getTimeout().getValue() != null) {
      socketTimeoutMillis =
          (int) NGTimeConversionHelper.convertTimeStringToMilliseconds(stepParameters.getTimeout().getValue());
    }
    HttpStepParameters httpStepParameters = (HttpStepParameters) stepParameters.getSpec();

    String url = (String) httpStepParameters.getUrl().fetchFinalValue();

    NGLogCallback logCallback =
        httpStepUtils.getNGLogCallback(logStreamingStepClientFactory, ambiance, HttpTaskNG.COMMAND_UNIT, false);
    url = httpStepUtils.encodeURL(url, logCallback);

    HttpTaskParametersNgBuilder httpTaskParametersNgBuilder = HttpTaskParametersNg.builder()
                                                                  .url(url)
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

    boolean isIgnoreResponseCode =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_USE_HTTP_CHECK_IGNORE_RESPONSE_INSTEAD_OF_SOCKET_NG);
    httpTaskParametersNgBuilder.isIgnoreResponseCode(isIgnoreResponseCode);

    httpTaskParametersNgBuilder.isCertValidationRequired(
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.ENABLE_CERT_VALIDATION));

    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_HTTP_STEP_NG_CERTIFICATE)) {
      httpStepUtils.createCertificate(httpStepParameters.getCertificate(), httpStepParameters.getCertificateKey())
          .ifPresent(cert -> { httpTaskParametersNgBuilder.certificateNG(cert); });
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

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      ThrowingSupplier<HttpStepResponse> responseSupplier) throws Exception {
    try {
      NGLogCallback logCallback =
          httpStepUtils.getNGLogCallback(logStreamingStepClientFactory, ambiance, HttpTaskNG.COMMAND_UNIT, false);

      StepResponseBuilder responseBuilder = StepResponse.builder();
      HttpStepResponse httpStepResponse = responseSupplier.get();

      HttpStepParameters httpStepParameters = (HttpStepParameters) stepParameters.getSpec();
      logCallback.saveExecutionLog(String.format(
          "Successfully executed the http request %s .", httpStepUtils.fetchFinalValue(httpStepParameters.getUrl())));

      Map<String, Object> outputVariables =
          httpStepParameters.getOutputVariables() == null ? null : httpStepParameters.getOutputVariables().getValue();
      Map<String, String> outputVariablesEvaluated =
          httpStepUtils.evaluateOutputVariables(outputVariables, httpStepResponse, ambiance);

      logCallback.saveExecutionLog("Validating the assertions...");
      boolean assertionSuccessful =
          HttpStepUtils.validateAssertions(httpStepResponse, httpStepParameters.getAssertion());
      HttpOutcome executionData = HttpOutcome.builder()
                                      .httpUrl(httpStepUtils.fetchFinalValue(httpStepParameters.getUrl()))
                                      .httpMethod(httpStepUtils.fetchFinalValue(httpStepParameters.getMethod()))
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
      httpStepUtils.closeLogStream(ambiance);
    }
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      TaskExecutableResponse executableResponse, boolean userMarked) {
    httpStepUtils.closeLogStream(ambiance);
  }
}
