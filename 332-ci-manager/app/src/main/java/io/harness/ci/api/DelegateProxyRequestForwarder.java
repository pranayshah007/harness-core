/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.resources;


import com.google.inject.Inject;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.http.HttpHeaderConfig;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.waiter.WaitNotifyEngine;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.TaskType;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.CI;

@Slf4j
@OwnedBy(CI)
@AllArgsConstructor(onConstructor = @__({ @Inject}))
public class DelegateProxyRequestForwarder {
    DelegateGrpcClientWrapper delegateGrpcClientWrapper;
    WaitNotifyEngine waitNotifyEngine;

    public UriBuilder CreateUrlWithQueryParameters(String url,MultivaluedMap<String, String> queryParam ){
        UriBuilder uriBuilder = UriBuilder.fromUri(url);

        for(String key : queryParam.keySet()) {
            uriBuilder.queryParam(key,queryParam.get(key));
        }
        return uriBuilder;
    }
    public List<HttpHeaderConfig> CreateHeaderConfig(HttpHeaders headers){
        List<HttpHeaderConfig> headerList = new ArrayList<>();
        try {
            for(String headerKey : headers.getRequestHeaders().keySet()){
                if(headerKey == "Content-Length")
                {
                    continue;
                }
                String value = headers.getHeaderString(headerKey);
                headerList.add(HttpHeaderConfig.builder().key(headerKey).value(value).build());
                log.info("header {} : {}", headerKey,value);
            }
        } catch (Exception ex){
            log.error("error while mapping the headers", ex);
            throw ex;
        }

        return headerList;
    }

    public HttpStepResponse ForwardRequestToDelegate(String accountId, String url, List<HttpHeaderConfig> headerList, String body,String methodType){

        DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                .accountId(accountId)
                .executionTimeout(java.time.Duration.ofSeconds(60))
                .taskType(TaskType.HTTP_TASK_NG.name())
                .taskParameters(getTaskParams(url, methodType,headerList,body))
                .taskDescription("IDP Proxy Http Task")
                //.eligibleToExecuteDelegateIds(List.of("acctgroup"))
                .taskSetupAbstraction("ng", "true")
                .build();
        HttpStepResponse httpResponse = null;
        try {
            DelegateResponseData responseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
            if (responseData instanceof ErrorNotifyResponseData) {
                ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
                log.error("errorMessage: {}", errorNotifyResponseData.getErrorMessage());
            }
            if (responseData instanceof HttpStepResponse) {
                httpResponse = (HttpStepResponse) responseData;

                log.info("responseData header: {}",httpResponse.getHeader());
                log.info("responseData body: {}",httpResponse.getHttpResponseBody());
            }


        } catch (Exception ex) {
            log.error("Delegate error: ", ex);
            throw ex;
        }

        return httpResponse;
    }



    private HttpTaskParametersNg getTaskParams(String url, String methodType, List<HttpHeaderConfig> headers, String body) {
        HttpTaskParametersNg httpTaskParametersNg = HttpTaskParametersNg.builder()
                .url(url)
                .method(methodType)
                .requestHeader(headers)
                .body(body)
                .socketTimeoutMillis(20000).build();
        return httpTaskParametersNg;
    }

    public List<HttpHeaderConfig> CreateHeaderConfig(Map<String, Object> headers) {
        List<HttpHeaderConfig> headerList = new ArrayList<>();
        try {
            for(String headerKey : headers.keySet()){
                if(headerKey == "Content-Length")
                {
                    continue;
                }
                String value = headers.get(headerKey).toString();
                headerList.add(HttpHeaderConfig.builder().key(headerKey).value(value).build());
                log.info("header {} : {}", headerKey,value);
            }
        } catch (Exception ex){
            log.error("error while mapping the headers", ex);
            throw ex;
        }

        return headerList;
    }
}
