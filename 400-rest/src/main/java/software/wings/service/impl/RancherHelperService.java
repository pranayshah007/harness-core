/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.EncryptedData;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.KeyValuePair;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.HttpStateExecutionResponse;
import software.wings.beans.RancherConfig;
import software.wings.beans.TaskType;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.jose4j.json.JsonUtil;
import org.jose4j.lang.JoseException;

@Singleton
@Slf4j
public class RancherHelperService {
  @Inject private DelegateService delegateService;
  @Inject private SecretManager secretManager;

  public boolean validateRancherConfig(RancherConfig rancherConfig) {
    StringBuffer urlBuffer = new StringBuffer(rancherConfig.getRancherUrl());
    urlBuffer.append("/v3/clusters");
    List<KeyValuePair> headers = new ArrayList<>();
    int expressionFunctorToken = HashGenerator.generateIntegerHash();

    EncryptedData rancherBearerTokenSecretEncryptedData =
        secretManager.getSecretById(rancherConfig.getAccountId(), rancherConfig.getEncryptedBearerToken());

    headers.add(KeyValuePair.builder()
                    .key(HttpHeaders.AUTHORIZATION)
                    .value("Bearer ${secretManager.obtain(\"" + rancherBearerTokenSecretEncryptedData.getName() + "\", "
                        + expressionFunctorToken + ")}")
                    .build());

    HttpTaskParameters httpTaskParameters = HttpTaskParameters.builder()
                                                .method("GET")
                                                .url(urlBuffer.toString())
                                                .headers(headers)
                                                .socketTimeoutMillis(10000)
                                                .isCertValidationRequired(rancherConfig.isCertValidationRequired())
                                                .build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(rancherConfig.getAccountId())
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.HTTP.name())
                                              .parameters(new Object[] {httpTaskParameters})
                                              .timeout(TimeUnit.MINUTES.toMillis(10))
                                              .expressionFunctorToken(expressionFunctorToken)
                                              .build())
                                    .build();

    DelegateResponseData notifyResponseData;
    try {
      notifyResponseData = delegateService.executeTask(delegateTask);
      validateRancherDelegateResponse((HttpStateExecutionResponse) notifyResponseData);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(e.getMessage(), USER);
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage(), USER);
    }
    return true;
  }

  private void validateRancherDelegateResponse(HttpStateExecutionResponse delegateResponseData) throws JoseException {
    if (!(delegateResponseData.getExecutionStatus() == ExecutionStatus.SUCCESS
            && delegateResponseData.getHttpResponseCode() == 200)) {
      String errorMessage = "";
      if (delegateResponseData.getErrorMessage() != null) {
        errorMessage = delegateResponseData.getErrorMessage();
      } else {
        Map<String, Object> httpResponseBody = JsonUtil.parseJson(delegateResponseData.getHttpResponseBody());
        if (httpResponseBody != null && httpResponseBody.containsKey("message")) {
          errorMessage = "Rancher error: " + httpResponseBody.get("message");
        }
      }
      throw new InvalidRequestException(errorMessage, WingsException.USER);
    }
  }
}
