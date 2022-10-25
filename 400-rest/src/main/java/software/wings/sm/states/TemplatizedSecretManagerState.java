/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.exception.utils.ExceptionUtils;
import io.harness.serializer.JsonUtils;

import software.wings.api.TemplatizedSecretManagerStateExecutionData;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class TemplatizedSecretManagerState extends State {
  @Inject private SecretManager secretManager;
  @Attributes(title = "Secret Manager") private String kmsId;
  @Attributes(title = "Run time parameters for secret manager") private String runtimeParametersString;

  public String getKmsId() {
    return kmsId;
  }

  public void setKmsId(String kmsId) {
    this.kmsId = kmsId;
  }

  public String getRuntimeParametersString() {
    return runtimeParametersString;
  }

  public void setRuntimeParametersString(String runtimeParametersString) {
    this.runtimeParametersString = runtimeParametersString;
  }

  public TemplatizedSecretManagerState(String name) {
    super(name, StateType.TEMPLATIZED_SECRET_MANAGER.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    TemplatizedSecretManagerStateExecutionData stateData = TemplatizedSecretManagerStateExecutionData.builder().build();
    Map<String, String> runtimeParameters =
        JsonUtils.asObject(runtimeParametersString, new TypeReference<Map<String, String>>() {});
    try {
      String executionId = context.getWorkflowExecutionId();
      String accountId = context.getAccountId();
      String renderedKmsId = context.renderExpression(kmsId);
      for (Map.Entry<String, String> entry : runtimeParameters.entrySet()) {
        entry.setValue(context.renderExpression(entry.getValue()));
      }
      secretManager.configureSecretManagerRuntimeCredentialsForExecution(
          accountId, renderedKmsId, executionId, runtimeParameters);
      stateData.setKmsId(renderedKmsId);
      stateData.setWorkflowExecutionId(executionId);
    } catch (Exception e) {
      executionResponseBuilder.errorMessage(
          e.getCause() == null ? ExceptionUtils.getMessage(e) : ExceptionUtils.getMessage(e.getCause()));
      executionResponseBuilder.executionStatus(ExecutionStatus.ERROR);
      log.error("Exception while sending email", e);
    }
    executionResponseBuilder.stateExecutionData(stateData);
    return executionResponseBuilder.build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // do nothing when abort called
  }
}
