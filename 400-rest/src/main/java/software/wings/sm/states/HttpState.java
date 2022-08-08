/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.SOCKET_HTTP_STATE_TIMEOUT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.ListUtils.trimStrings;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.template.TemplateHelper.convertToVariableMap;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.exception.ExceptionUtils.getMessage;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.KeyValuePair;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.ff.FeatureFlagService;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.api.HttpStateExecutionData;
import software.wings.api.HttpStateExecutionData.HttpStateExecutionDataBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.HttpStateExecutionResponse;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.NameValuePair;
import software.wings.beans.TaskType;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.template.TemplateUtils;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.expression.ManagerPreviewExpressionEvaluator;
import software.wings.service.impl.AccountServiceImpl;
import software.wings.service.impl.ActivityHelperService;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.mixin.SweepingOutputStateMixin;
import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.MoreObjects;
import com.google.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlException.Parsing;
import org.apache.commons.jexl3.JexlException.Property;
import org.mongodb.morphia.annotations.Transient;

/**
 * Http state which makes a call to http service.
 */
@OwnedBy(CDC)
@FieldNameConstants(innerTypeName = "HttpStateKeys")
@Attributes
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class HttpState extends State implements SweepingOutputStateMixin {
  private static final String ASSERTION_ERROR_MSG =
      "Assertion should return true/false (Expression syntax is based on java language).";

  @Attributes(required = true, title = "URL") private String url;
  @Attributes(required = true, enums = {"GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS"}, title = "Method")
  @DefaultValue("GET")
  private String method;
  @Attributes(title = "Header") private String header;
  @Getter @Setter private List<KeyValuePair> headers;
  @Attributes(title = "Body") private String body;
  @Attributes(title = "Assertion") private String assertion;
  @Getter @Setter @Attributes(title = "Use Delegate Proxy") private boolean useProxy;
  @Getter @Setter @Attributes(title = "Tags") private List<String> tags;

  @Getter @Setter private List<NameValuePair> responseProcessingExpressions;

  @Getter @Setter private String sweepingOutputName;
  @Getter @Setter private SweepingOutputInstance.Scope sweepingOutputScope;

  @SchemaIgnore private int socketTimeoutMillis = 30000;

  @Inject private transient ActivityHelperService activityHelperService;
  @Inject private transient SweepingOutputService sweepingOutputService;
  @Inject private transient TemplateUtils templateUtils;
  @Inject protected transient ManagerDecryptionService managerDecryptionService;
  @Inject protected transient SecretManager secretManager;
  @Inject protected transient DelegateService delegateService;
  @Inject @Transient InfrastructureMappingService infrastructureMappingService;
  @Inject @Transient FeatureFlagService featureFlagService;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Inject private SettingServiceHelper settingServiceHelper;
  @Inject private AccountServiceImpl accountService;
  @Transient @Inject KryoSerializer kryoSerializer;

  public HttpState() {}

  /**
   * Create a new Http State with given name.
   *
   * @param name name of the state.
   */
  public HttpState(String name) {
    super(name, StateType.HTTP.name());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String activityId = createActivity(context);
    return executeInternal(context, activityId);
  }

  /**
   * Handle abort event.
   *
   * @param context the context
   */
  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  /**
   * Gets url.
   *
   * @return the url
   */
  public String getUrl() {
    return url;
  }

  /**
   * Sets url.
   *
   * @param url the url
   */
  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * Gets method.
   *
   * @return the method
   */
  public String getMethod() {
    return method;
  }

  /**
   * Sets method.
   *
   * @param method the method
   */
  public void setMethod(String method) {
    this.method = method;
  }

  /**
   * Gets body.
   *
   * @return the body
   */
  public String getBody() {
    return body;
  }

  /**
   * Sets body.
   *
   * @param body the body
   */
  public void setBody(String body) {
    this.body = trim(body);
  }

  /**
   * Gets header.
   *
   * @return the header
   */
  public String getHeader() {
    return header;
  }

  /**
   * Sets header.
   *
   * @param header the header
   */
  public void setHeader(String header) {
    this.header = header;
  }

  /**
   * Gets assertion.
   *
   * @return the assertion
   */
  public String getAssertion() {
    return assertion;
  }

  /**
   * Sets assertion.
   *
   * @param assertion the assertion
   */
  public void setAssertion(String assertion) {
    this.assertion = assertion;
  }

  /**
   * Gets socket timeout millis.
   *
   * @return the socket timeout millis
   */
  @SchemaIgnore
  public int getSocketTimeoutMillis() {
    return socketTimeoutMillis;
  }

  protected String getFinalMethod(ExecutionContext context) {
    return method;
  }

  protected List<KeyValuePair> getFinalHeaders(ExecutionContext context) {
    return headers;
  }

  protected String getFinalBody(ExecutionContext context) throws UnsupportedEncodingException {
    return body;
  }

  protected String getFinalUrl(ExecutionContext context) {
    return url;
  }
  /**
   * Sets socket timeout millis.
   *
   * @param socketTimeoutMillis the socket timeout millis
   */
  @SchemaIgnore
  public void setSocketTimeoutMillis(int socketTimeoutMillis) {
    this.socketTimeoutMillis = socketTimeoutMillis;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }

  @Override
  @SchemaIgnore
  public List<String> getPatternsForRequiredContextElementType() {
    String resolvedUrl = url;
    String resolvedBody = body;
    String resolvedAssertion = assertion;
    List<Variable> variables = new ArrayList<>();
    if (isNotEmpty(getTemplateVariables())) {
      for (Variable variable : getTemplateVariables()) {
        if (VariableType.ARTIFACT != variable.getType()) {
          variables.add(variable);
        }
      }
      Map<String, Object> templateVariables = convertToVariableMap(variables);
      if (isNotEmpty(templateVariables)) {
        resolvedUrl = fetchTemplatedValue(url, templateVariables);
        resolvedBody = fetchTemplatedValue(body, templateVariables);
        resolvedAssertion = fetchTemplatedValue(assertion, templateVariables);
      }
    }
    return asList(resolvedUrl, resolvedBody, resolvedAssertion);
  }

  private String fetchTemplatedValue(String fieldName, Map<String, Object> templatedVariables) {
    String templatedField = ManagerExpressionEvaluator.getName(fieldName);
    return templatedVariables.containsKey(templatedField) ? (String) templatedVariables.get(templatedField) : fieldName;
  }

  @Attributes(title = "Execute with previous steps")
  public boolean getExecuteWithPreviousSteps() {
    return super.isExecuteWithPreviousSteps();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("url", url)
        .add("method", method)
        .add("headers", headers)
        .add("body", body)
        .add("assertion", assertion)
        .add("socketTimeoutMillis", socketTimeoutMillis)
        .toString();
  }

  /**
   * Execute internal execution response.
   *
   * @param context the contextS
   * @return the execution response
   */
  protected ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    String envId = obtainEnvId(context.getContextElement(ContextElementType.STANDARD));
    String finalUrl = trim(getFinalUrl(context));
    String finalBody = null;
    try {
      finalBody = getFinalBody(context);
    } catch (UnsupportedEncodingException e) {
      log.error("", e);
    }
    List<KeyValuePair> finalHeaders = getFinalHeaders(context);
    String finalMethod = getFinalMethod(context);
    String infrastructureMappingId = context.fetchInfraMappingId();

    int taskSocketTimeout = socketTimeoutMillis;
    String warningMessage = null;
    if (featureFlagService.isEnabled(SOCKET_HTTP_STATE_TIMEOUT, context.getAccountId())) {
      taskSocketTimeout = getTimeoutMillis() - 1000;
    } else {
      Integer stateTimeout = getTimeoutMillis();
      if (stateTimeout != null && taskSocketTimeout >= stateTimeout) {
        taskSocketTimeout = stateTimeout - 1000;
      } else {
        warningMessage = "State timeout is greater than " + socketTimeoutMillis / 1000
            + " sec. Defaulting  socket timeout to " + socketTimeoutMillis / 1000 + " sec.";
      }
    }

    List<String> renderedTags = newArrayList();
    if (isNotEmpty(tags)) {
      for (String tag : tags) {
        renderedTags.add(context.renderExpression(tag));
      }
      renderedTags = trimStrings(renderedTags);
      context.resetPreparedCache();
    }

    boolean isCertValidationRequired = accountService.isCertValidationRequired(context.getAccountId());
    boolean useHeaderForCapabilityCheck =
        featureFlagService.isEnabled(FeatureName.HTTP_HEADERS_CAPABILITY_CHECK, context.getAccountId());

    HttpTaskParameters httpTaskParameters = HttpTaskParameters.builder()
                                                .method(finalMethod)
                                                .body(finalBody)
                                                .url(finalUrl)
                                                .headers(finalHeaders)
                                                .socketTimeoutMillis(taskSocketTimeout)
                                                .useProxy(useProxy)
                                                .isCertValidationRequired(isCertValidationRequired)
                                                .useHeaderForCapabilityCheck(useHeaderForCapabilityCheck)
                                                .build();

    HttpStateExecutionDataBuilder executionDataBuilder =
        HttpStateExecutionData.builder().useProxy(useProxy).templateVariables(
            templateUtils.processTemplateVariables(context, getTemplateVariables()));

    int expressionFunctorToken = HashGenerator.generateIntegerHash();
    renderTaskParameters(context, executionDataBuilder.build(), httpTaskParameters, expressionFunctorToken);

    ManagerPreviewExpressionEvaluator expressionEvaluator = new ManagerPreviewExpressionEvaluator();

    if (isNotEmpty(httpTaskParameters.getHeaders())) {
      List<KeyValuePair> httpHeaders =
          httpTaskParameters.getHeaders()
              .stream()
              .map(pair
                  -> KeyValuePair.builder()
                         .key(expressionEvaluator.substitute(pair.getKey(), Collections.emptyMap()))
                         .value(expressionEvaluator.substitute(pair.getValue(), Collections.emptyMap()))
                         .build())
              .collect(Collectors.toList());
      executionDataBuilder.headers(httpHeaders);
    }
    executionDataBuilder.httpUrl(expressionEvaluator.substitute(httpTaskParameters.getUrl(), Collections.emptyMap()))
        .httpMethod(expressionEvaluator.substitute(httpTaskParameters.getMethod(), Collections.emptyMap()))
        .warningMessage(warningMessage);
    InfrastructureMapping infrastructureMapping =
        infrastructureMappingService.get(context.getAppId(), infrastructureMappingId);
    String serviceId = infrastructureMapping == null ? null : infrastructureMapping.getServiceId();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(((ExecutionContextImpl) context).fetchRequiredApp().getAccountId())
            .description("Http Execution")
            .waitId(activityId)
            .tags(renderedTags)
            .setupAbstraction(
                Cd1SetupFields.APP_ID_FIELD, ((ExecutionContextImpl) context).fetchRequiredApp().getAppId())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(getTaskType().name())
                      .parameters(new Object[] {httpTaskParameters})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .expressionFunctorToken(expressionFunctorToken)
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, context.getEnvType())

            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMappingId)
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, serviceId)

            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .workflowExecutionId(context.getWorkflowExecutionId())
            .build();

    String delegateTaskId = scheduleDelegateTask(delegateTask);

    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(activityId))
        .stateExecutionData(executionDataBuilder.build())
        .delegateTaskId(delegateTaskId)
        .build();
  }

  private String obtainEnvId(WorkflowStandardParams workflowStandardParams) {
    return (workflowStandardParams == null
               || workflowStandardParamsExtensionService.getEnv(workflowStandardParams) == null)
        ? null
        : workflowStandardParamsExtensionService.getEnv(workflowStandardParams).getUuid();
  }

  protected TaskType getTaskType() {
    return TaskType.HTTP;
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      executionResponseBuilder.executionStatus(ExecutionStatus.FAILED);
      executionResponseBuilder.errorMessage(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage());
    } else {
      HttpStateExecutionData executionData = (HttpStateExecutionData) context.getStateExecutionData();
      HttpStateExecutionResponse httpStateExecutionResponse = (HttpStateExecutionResponse) notifyResponseData;
      executionData.setHttpResponseCode(httpStateExecutionResponse.getHttpResponseCode());
      executionData.setHttpResponseBody(httpStateExecutionResponse.getHttpResponseBody());
      executionData.setStatus(httpStateExecutionResponse.getExecutionStatus());
      executionData.setErrorMsg(httpStateExecutionResponse.getErrorMessage());

      String errorMessage = httpStateExecutionResponse.getErrorMessage();
      executionData.setAssertionStatement(assertion);
      executionData.setTemplateVariable(templateUtils.processTemplateVariables(context, getTemplateVariables()));
      ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;
      if (!evaluateAssertion(context, executionData) || executionData.getStatus().equals(ExecutionStatus.ERROR)
          || executionData.getStatus().equals(ExecutionStatus.FAILED)) {
        executionStatus = ExecutionStatus.FAILED;
        appendFailureType(executionResponseBuilder, context, httpStateExecutionResponse);
      }
      executionResponseBuilder.executionStatus(executionStatus);

      executionData.setAssertionStatus(executionStatus.name());
      executionResponseBuilder.stateExecutionData(executionData);
      executionResponseBuilder.errorMessage(errorMessage);

      if (isNotEmpty(responseProcessingExpressions)) {
        Map<String, Object> output = new HashMap<>();

        responseProcessingExpressions.forEach(expression -> {
          output.put(expression.getName(),
              context.renderExpression(
                  expression.getValue(), StateExecutionContext.builder().stateExecutionData(executionData).build()));
        });

        handleSweepingOutput(sweepingOutputService, context, output);
      }
    }
    String activityId = null;

    for (String key : response.keySet()) {
      activityId = key;
    }

    ExecutionResponse executionResponse = executionResponseBuilder.build();

    updateActivityStatus(activityId, ((ExecutionContextImpl) context).fetchRequiredApp().getUuid(),
        executionResponse.getExecutionStatus());

    return executionResponse;
  }

  private boolean evaluateAssertion(ExecutionContext context, HttpStateExecutionData executionData) {
    if (isBlank(assertion)) {
      return true;
    }

    if (executionData.getStatus() == ExecutionStatus.ERROR) {
      return true;
    }

    try {
      // check if the request failed
      boolean assertionStatus = (boolean) context.evaluateExpression(
          assertion, StateExecutionContext.builder().stateExecutionData(executionData).build());
      log.info("assertion status: {}", assertionStatus);
      return assertionStatus;
    } catch (ClassCastException e) {
      log.info("Invalid assertion " + ExceptionUtils.getMessage(e), e);
      executionData.setErrorMsg(ASSERTION_ERROR_MSG);
      throw new InvalidRequestException(ASSERTION_ERROR_MSG, USER);
    } catch (JexlException e) {
      log.info("Error in httpStateAssertion", e);

      String errorMsg;
      if (e instanceof Parsing) {
        Parsing p = (Parsing) e;
        errorMsg = "Parsing error '" + p.getDetail() + "' in assertion.";
      } else if (e instanceof Property) {
        Property pr = (Property) e;
        errorMsg = "Unresolvable property '" + pr.getProperty() + "' in assertion.";
      } else if (e instanceof JexlException.Variable
          && ((JexlException.Variable) e).getVariable().equals("sweepingOutputSecrets")) {
        errorMsg = "Error evaluating assertion condition: " + assertion
            + ": Secret Variables defined in Script output of shell scripts cannot be used in assertions";
      } else {
        errorMsg = getMessage(e);
      }
      executionData.setErrorMsg(errorMsg);
      throw new InvalidRequestException(errorMsg, USER);
    } catch (Exception e) {
      log.info("Error in httpStateAssertion", e);
      executionData.setErrorMsg(getMessage(e));
      throw new InvalidRequestException(getMessage(e), USER);
    }
  }

  /**
   * Create activity string.
   *
   * @param executionContext the execution context
   * @return the string
   */
  protected String createActivity(ExecutionContext executionContext) {
    return activityHelperService
        .createAndSaveActivity(executionContext, Type.Verification, getName(), getStateType(), Collections.emptyList())
        .getUuid();
  }

  /**
   * Update activity status.
   *
   * @param activityId the activity id
   * @param appId      the app id
   * @param status     the status
   */
  protected void updateActivityStatus(String activityId, String appId, ExecutionStatus status) {
    activityHelperService.updateStatus(activityId, appId, status);
  }

  protected String urlEncodeString(String queryString) {
    try {
      return URLEncoder.encode(queryString, "UTF-8");
    } catch (UnsupportedEncodingException ex) {
      throw new InvalidArgumentsException("Couldn't url-encode " + queryString, USER, ex);
    }
  }

  private void renderTaskParameters(ExecutionContext context, StateExecutionData stateExecutionData,
      TaskParameters parameters, int expressionFunctorToken) {
    context.resetPreparedCache();
    ExpressionReflectionUtils.applyExpression(parameters,
        (secretMode, value)
            -> context.renderExpression(value,
                StateExecutionContext.builder()
                    .stateExecutionData(stateExecutionData)
                    .adoptDelegateDecryption(true)
                    .expressionFunctorToken(expressionFunctorToken)
                    .build()));
  }

  private String scheduleDelegateTask(DelegateTask task) {
    return delegateService.queueTask(task);
  }

  private void appendFailureType(ExecutionResponseBuilder executionResponseBuilder, ExecutionContext context,
      HttpStateExecutionResponse httpStateExecutionResponse) {
    if (featureFlagService.isEnabled(FeatureName.TIMEOUT_FAILURE_SUPPORT, context.getAccountId())
        && httpStateExecutionResponse.isTimedOut()) {
      executionResponseBuilder.failureTypes(EnumSet.of(FailureType.TIMEOUT_ERROR));
    }
  }

  @Override
  public KryoSerializer getKryoSerializer() {
    return kryoSerializer;
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String name;
    private String url;
    private String method;
    private String header;
    private List<KeyValuePair> headers;
    private String body;
    private String assertion;
    private boolean useProxy;
    private int socketTimeoutMillis = 10000;
    private List<Variable> templateVariables;

    /**
     * Do not instantiate PageResponseBuilder.
     */
    private Builder() {}

    /**
     * A http state builder.
     *
     * @return the builder
     */
    public static Builder aHttpState() {
      return new Builder();
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * With url builder.
     *
     * @param url the url
     * @return the builder
     */
    public Builder withUrl(String url) {
      this.url = url;
      return this;
    }

    /**
     * With method builder.
     *
     * @param method the method
     * @return the builder
     */
    public Builder withMethod(String method) {
      this.method = method;
      return this;
    }

    /**
     * With header builder.
     *
     * @param header the header
     * @return the builder
     */
    public Builder withHeader(String header) {
      this.header = header;
      return this;
    }

    /**
     * With header builder.
     *
     * @param headers the header
     * @return the builder
     */
    public Builder withHeaders(List<KeyValuePair> headers) {
      this.headers = headers;
      return this;
    }

    /**
     * With body builder.
     *
     * @param body the body
     * @return the builder
     */
    public Builder withBody(String body) {
      this.body = body;
      return this;
    }

    /**
     * With assertion builder.
     *
     * @param assertion the assertion
     * @return the builder
     */
    public Builder withAssertion(String assertion) {
      this.assertion = assertion;
      return this;
    }

    public Builder usesProxy(boolean useProxy) {
      this.useProxy = useProxy;
      return this;
    }

    /**
     * With socket timeout millis builder.
     *
     * @param socketTimeoutMillis the socket timeout millis
     * @return the builder
     */
    public Builder withSocketTimeoutMillis(int socketTimeoutMillis) {
      this.socketTimeoutMillis = socketTimeoutMillis;
      return this;
    }

    public Builder withTemplateVariables(List<Variable> templateVariables) {
      this.templateVariables = templateVariables;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aHttpState()
          .withName(name)
          .withUrl(url)
          .withMethod(method)
          .withHeader(header)
          .withHeaders(headers)
          .withBody(body)
          .withAssertion(assertion)
          .usesProxy(useProxy)
          .withSocketTimeoutMillis(socketTimeoutMillis)
          .withTemplateVariables(templateVariables);
    }

    /**
     * Build http state.
     *
     * @return the http state
     */
    public HttpState build() {
      HttpState httpState = new HttpState(name);
      httpState.setUrl(url);
      httpState.setMethod(method);
      httpState.setHeader(header);
      httpState.setBody(body);
      httpState.setAssertion(assertion);
      httpState.setSocketTimeoutMillis(socketTimeoutMillis);
      httpState.setTemplateVariables(templateVariables);
      httpState.setHeader(header);
      httpState.setUseProxy(useProxy);
      httpState.setHeaders(headers);
      return httpState;
    }
  }
}
