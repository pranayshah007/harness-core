/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static com.google.common.collect.ImmutableSortedMap.of;
import static java.util.Arrays.asList;

import io.harness.beans.KeyValuePair;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.api.HttpStateExecutionData;
import software.wings.api.SplunkStateExecutionData;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SplunkConfig;
import software.wings.beans.TaskType;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

/**
 * Created by peeyushaggarwal on 7/15/16.
 */
@Slf4j
public class SplunkState extends HttpState {
  @Transient @Inject private SettingsService settingsService;

  @Attributes(required = true, title = "Query") private String query;

  /**
   * Create a new Http State with given name.
   *
   * @param name name of the state.
   */
  public SplunkState(String name) {
    super(name);
    setStateType("Splunk-Deprecated");
  }

  @Override
  protected ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    String evaluatedQuery = context.renderExpression(query);
    log.info("evaluatedQuery: {}", evaluatedQuery);

    ExecutionResponse executionResponse = super.executeInternal(context, activityId);

    return executionResponse.toBuilder()
        .stateExecutionData(
            SplunkStateExecutionData.builder().query(evaluatedQuery).assertionStatement(getAssertion()).build())
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    ExecutionResponse executionResponse = super.handleAsyncResponse(context, response);

    HttpStateExecutionData httpStateExecutionData = (HttpStateExecutionData) executionResponse.getStateExecutionData();

    SplunkStateExecutionData splunkStateExecutionData = (SplunkStateExecutionData) context.getStateExecutionData();

    return executionResponse.toBuilder()
        .stateExecutionData(SplunkStateExecutionData.builder()
                                .query(splunkStateExecutionData.getQuery())
                                .assertionStatement(getAssertion())
                                .assertionStatus(httpStateExecutionData.getAssertionStatus())
                                .response(httpStateExecutionData.getHttpResponseBody())
                                .build())
        .build();
  }

  /**
   * Getter for property 'query'.
   *
   * @return Value for property 'query'.
   */
  public String getQuery() {
    return query;
  }

  /**
   * Setter for property 'query'.
   *
   * @param query Value to set for property 'query'.
   */
  public void setQuery(String query) {
    this.query = query;
  }

  @SchemaIgnore
  @Override
  public List<String> getPatternsForRequiredContextElementType() {
    return asList(query, getAssertion());
  }

  @Override
  @Attributes(title = "Wait interval before execution(in seconds)")
  public Integer getWaitInterval() {
    return super.getWaitInterval();
  }

  @SchemaIgnore
  @Override
  public String getBody() {
    return super.getBody();
  }

  @SchemaIgnore
  @Override
  public String getHeader() {
    return super.getHeader();
  }

  @SchemaIgnore
  @Override
  public String getMethod() {
    return super.getMethod();
  }

  @SchemaIgnore
  @Override
  public String getUrl() {
    return super.getUrl();
  }

  @Attributes(required = true, title = "Assertion")
  @Override
  public String getAssertion() {
    return super.getAssertion();
  }

  @Override
  protected TaskType getTaskType() {
    return TaskType.SPLUNK;
  }

  @Override
  protected String getFinalMethod(ExecutionContext context) {
    return "POST";
  }

  @Override
  protected List<KeyValuePair> getFinalHeaders(ExecutionContext context) {
    SettingAttribute splunkSettingAttribute =
        settingsService
            .getGlobalSettingAttributesByType(
                ((ExecutionContextImpl) context).getApp().getAccountId(), SettingVariableTypes.SPLUNK.name())
            .get(0);
    SplunkConfig splunkConfig = (SplunkConfig) splunkSettingAttribute.getValue();
    managerDecryptionService.decrypt(splunkConfig,
        secretManager.getEncryptionDetails(splunkConfig, context.getAppId(), context.getWorkflowExecutionId()));
    return Collections.singletonList(KeyValuePair.builder()
                                         .key("Authorization")
                                         .value("Basic "
                                             + Base64.encodeBase64URLSafeString((splunkConfig.getUsername() + ":"
                                                 + new String(splunkConfig.getPassword()))
                                                                                    .getBytes(StandardCharsets.UTF_8)))
                                         .build());
  }

  @Override
  protected String getFinalBody(ExecutionContext context) throws UnsupportedEncodingException {
    String evaluatedQuery = context.renderExpression(query);
    log.info("evaluatedQuery: {}", evaluatedQuery);
    return toPostBody(of("search", "search " + evaluatedQuery, "exec_mode", "oneshot"));
  }

  @Override
  protected String getFinalUrl(ExecutionContext context) {
    SettingAttribute splunkSettingAttribute =
        settingsService
            .getGlobalSettingAttributesByType(
                ((ExecutionContextImpl) context).getApp().getAccountId(), SettingVariableTypes.SPLUNK.name())
            .get(0);
    SplunkConfig splunkConfig = (SplunkConfig) splunkSettingAttribute.getValue();
    return splunkConfig.getSplunkUrl() + "/services/search/jobs";
  }

  private String toPostBody(Map<String, String> params) throws UnsupportedEncodingException {
    StringBuilder postData = new StringBuilder();
    for (Entry<String, String> param : params.entrySet()) {
      if (postData.length() != 0) {
        postData.append('&');
      }
      postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
      postData.append('=');
      postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
    }
    return postData.toString();
  }

  @Override
  public KryoSerializer getKryoSerializer() {
    return kryoSerializer;
  }
}
