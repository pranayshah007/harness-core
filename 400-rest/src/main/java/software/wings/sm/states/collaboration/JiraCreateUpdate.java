/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.collaboration;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.TaskType.JIRA;

import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.HarnessJiraException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.jira.JiraAction;
import io.harness.jira.JiraCreateMetaResponse;
import io.harness.jira.JiraCustomFieldValue;
import io.harness.jira.JiraField;
import io.harness.jira.JiraIssueType;
import io.harness.jira.JiraProjectData;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.api.jira.JiraExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.JiraConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.jira.JiraTaskParameters;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.JiraHelperService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.states.mixin.SweepingOutputStateMixin;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.atteo.evo.inflector.English;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
@FieldNameConstants(innerTypeName = "JiraCreateUpdateKeys")
public class JiraCreateUpdate extends State implements SweepingOutputStateMixin {
  public static final String DATE_ISO_FORMAT = "yyyy-MM-dd";
  private static final long JIRA_TASK_TIMEOUT_MILLIS = 60 * 1000 * 5;
  private static final String JIRA_ISSUE_ID = "issueId";
  private static final String JIRA_ISSUE_KEY = "issueKey";
  private static final String JIRA_ISSUE = "issue";
  private static final String DATETIME_ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXX";
  private static final String DATETIME_UI_FORMAT = "yyyy/MM/dd HH:mm";
  private static final String DATETIME_UI_CAL_FORMAT = "MM/dd/yyyy HH:mm";
  private static final String MULTISELECT = "multiselect";
  private static final String ARRAY = "array";
  private static final String OPTION = "option";
  private static final String INVALID_VALUES_ERROR_MESSAGE =
      "Invalid values %s provided for custom field: %s. Please, check out allowed values: %s.";
  private static final String INVALID_VALUE_ERROR_MESSAGE =
      "Invalid value [%s] provided for custom field: %s. Please, check out allowed values: %s.";
  private static final String RESOLUTION = "resolution";
  private static final String INVALID_FIELD_NAME_ERROR_MESSAGE =
      "Invalid custom field %s %s. Please, check out allowed names: %s.";
  private static final String VALUE = "value";
  private static final String FAILING_JIRA_STEP_DUE_TO = "Failing Jira step due to: ";
  private static final String TIMETRACKING = "timetracking";
  private static final String TIME_TRACKING_ORIGINAL_ESTIMATE = "TimeTracking:OriginalEstimate";
  private static final String TIME_TRACKING_REMAINING_ESTIMATE = "TimeTracking:RemainingEstimate";
  private static final String NUMBER = "number";

  private static final HashMap<String, Long> MILLISECONDS_PER_UNIT = new HashMap<>() {
    {
      put("ms", Duration.ofMillis(1).toMillis());
      put("s", Duration.ofSeconds(1).toMillis());
      put("m", Duration.ofMinutes(1).toMillis());
      put("h", Duration.ofHours(1).toMillis());
      put("d", Duration.ofDays(1).toMillis());
      put("w", Duration.ofDays(7).toMillis());
    }
  };

  @Inject private transient ActivityService activityService;
  @Inject @Transient private LogService logService;
  @Inject @Transient private JiraHelperService jiraHelperService;
  @Inject FeatureFlagService featureFlagService;

  @Inject @Transient private transient WingsPersistence wingsPersistence;
  @Inject @Transient private DelegateService delegateService;
  @Inject @Transient private transient SecretManager secretManager;
  @Inject @Transient private SweepingOutputService sweepingOutputService;
  @Inject @Transient private SettingsService settingsService;
  @Transient @Inject KryoSerializer kryoSerializer;

  @Getter @Setter @NotNull private JiraAction jiraAction;
  @Getter @Setter @NotNull String jiraConnectorId;
  @Getter @Setter @NotNull private String project;
  @Getter @Setter private String issueType;
  @Getter @Setter private String priority;
  @Getter @Setter private List<String> labels;
  @Getter @Setter private String summary;
  @Getter @Setter private String description;
  @Getter @Setter private String status;
  @Getter @Setter private String comment;
  @Getter @Setter private String issueId;
  private Map<String, JiraCustomFieldValue> customFields;
  private static final Pattern currentPattern =
      Pattern.compile("(current\\(\\))(\\s*([+-])\\s*(\\d{0,13}))*", Pattern.CASE_INSENSITIVE);

  private static final Pattern uiPattern =
      Pattern.compile("^\\d{4}\\/\\d{2}\\/\\d{2}\\s*\\d{2}:\\d{2}$", Pattern.CASE_INSENSITIVE);
  private static final Pattern uiCalPattern =
      Pattern.compile("^\\d{2}\\/\\d{2}\\/\\d{4}\\s*\\d{2}:\\d{2}$", Pattern.CASE_INSENSITIVE);
  private static final Pattern uiCurrentPattern = Pattern.compile(
      "(current\\(\\))(?:\\s*)([+\\-])((?:(\\d{1,2}(?:ms|[smhdw]|)(?:\\s*)))*)", Pattern.CASE_INSENSITIVE);

  private static final Pattern ISO8601Format = Pattern.compile(
      "^([\\+-]?\\d{4}(?!\\d{2}\\b))((-?)((0[1-9]|1[0-2])(\\3([12]\\d|0[1-9]|3[01]))?|W([0-4]\\d|5[0-2])(-?[1-7])?|(00[1-9]|0[1-9]\\d|[12]\\d{2}|3([0-5]\\d|6[1-6])))([T\\s]((([01]\\d|2[0-3])((:?)[0-5]\\d)?|24\\:?00)([\\.,]\\d+(?!:))?)?(\\17[0-5]\\d([\\.,]\\d+)?)?([zZ]|([\\+-])([01]\\d|2[0-3]):?([0-5]\\d)?)?)?)?(?:\\s*)$");
  private static final Pattern varPattern =
      Pattern.compile("^(\\$\\{[a-z\\d._]*})(\\s*([+-])\\s*(\\d{0,13}))?", Pattern.CASE_INSENSITIVE);
  private static final Pattern fixedDatePattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
  private static final Pattern fixedDatetimePattern = Pattern.compile("(\\d{13})");

  public Map<String, JiraCustomFieldValue> fetchCustomFields() {
    return customFields;
  }

  public void setCustomFields(Map<String, JiraCustomFieldValue> customFields) {
    this.customFields = customFields;
  }

  @SchemaIgnore
  public Map<String, JiraCustomFieldValue> getCustomFieldsMap() {
    return this.customFields;
  }

  @Getter @Setter private SweepingOutputInstance.Scope sweepingOutputScope;
  @Getter @Setter private String sweepingOutputName;

  public JiraCreateUpdate(String name) {
    super(name, StateType.JIRA_CREATE_UPDATE.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String activityId = createActivity(context);
    return executeInternal(context, activityId);
  }

  private ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    boolean areRequiredFieldsTemplatized = checkIfRequiredFieldsAreTemplatized();
    String accountId = context.getAccountId();
    JiraConfig jiraConfig = getJiraConfig(jiraConnectorId, accountId);
    JiraCreateMetaResponse createMeta = null;
    renderExpressions(context);

    if (featureFlagService.isEnabled(FeatureName.ALLOW_USER_TYPE_FIELDS_JIRA, accountId)) {
      if (jiraAction == JiraAction.UPDATE_TICKET) {
        jiraAction = JiraAction.UPDATE_TICKET_NG;
      } else if (jiraAction == JiraAction.CREATE_TICKET) {
        jiraAction = JiraAction.CREATE_TICKET_NG;
      }
    }

    final long timeoutMillis = getTimeoutMillis() != null ? getTimeoutMillis() : JIRA_TASK_TIMEOUT_MILLIS;
    if (areRequiredFieldsTemplatized) {
      createMeta = jiraHelperService.getCreateMetadata(
          jiraConnectorId, null, project, accountId, context.getAppId(), timeoutMillis, issueType);
      try {
        validateRequiredFields(createMeta, context);
      } catch (HarnessJiraException e) {
        log.error(FAILING_JIRA_STEP_DUE_TO, e);
        return ExecutionResponse.builder().errorMessage(e.getMessage()).executionStatus(FAILED).build();
      }
    }

    if (EmptyPredicate.isNotEmpty(customFields)) {
      JiraCreateMetaResponse createMetadata = createMeta == null
          ? jiraHelperService.getCreateMetadata(
              jiraConnectorId, null, project, accountId, context.getAppId(), timeoutMillis, issueType)
          : createMeta;

      Map<String, String> customFieldsIdToNameMap = mapCustomFieldsIdsToNames(createMetadata);
      Map<String, Map<Object, Object>> customFieldsValueToIdMap =
          mapCustomFieldsValuesToId(createMetadata, customFieldsIdToNameMap.values());

      if (areRequiredFieldsTemplatized) {
        try {
          inferCustomFieldsTypes(createMetadata);
        } catch (HarnessJiraException e) {
          log.error(FAILING_JIRA_STEP_DUE_TO, e);
          return ExecutionResponse.builder().errorMessage(e.getMessage()).executionStatus(FAILED).build();
        }
      }

      parseCustomFieldsDateTimeNumber(context, customFieldsIdToNameMap);

      try {
        resolveCustomFieldsVars(customFieldsIdToNameMap, customFieldsValueToIdMap);
      } catch (HarnessJiraException e) {
        log.error(FAILING_JIRA_STEP_DUE_TO, e);
        return ExecutionResponse.builder().errorMessage(e.getMessage()).executionStatus(FAILED).build();
      }
    }

    if (ExpressionEvaluator.containsVariablePattern(issueId)) {
      return ExecutionResponse.builder()
          .executionStatus(FAILED)
          .errorMessage("Expression not rendered for issue Id: " + issueId)
          .stateExecutionData(JiraExecutionData.builder().activityId(activityId).build())
          .build();
    }

    JiraTaskParameters parameters =
        JiraTaskParameters.builder()
            .jiraConfig(jiraConfig)
            .jiraAction(jiraAction)
            .issueId(issueId)
            .project(project)
            .issueType(issueType)
            .summary(summary)
            .status(status)
            .description(description)
            .useNewMeta(featureFlagService.isEnabled(FeatureName.SPG_USE_NEW_METADATA, accountId))
            .labels(labels)
            .customFields(customFields)
            .comment(comment)
            .priority(priority)
            .encryptionDetails(secretManager.getEncryptionDetails(
                jiraConfig, executionContext.getAppId(), executionContext.getWorkflowExecutionId()))
            .accountId(((ExecutionContextImpl) context).getApp().getAccountId())
            .activityId(activityId)
            .appId(context.getAppId())
            .build();

    if (jiraAction == JiraAction.UPDATE_TICKET || jiraAction == JiraAction.UPDATE_TICKET_NG) {
      List<String> issueIds = parseExpression(issueId);
      if (EmptyPredicate.isEmpty(issueIds)) {
        return ExecutionResponse.builder()
            .executionStatus(FAILED)
            .errorMessage("No valid issueId after parsing: " + issueId)
            .stateExecutionData(JiraExecutionData.builder().activityId(activityId).build())
            .build();
      }
      parameters.setUpdateIssueIds(issueIds);
    }

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(executionContext.getApp().getAccountId())
            .waitId(activityId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, ((ExecutionContextImpl) context).getApp().getAppId())
            .description(jiraAction != null ? jiraAction.getDisplayName() : "Jira Task")
            .data(TaskData.builder()
                      .async(true)
                      .taskType(JIRA.name())
                      .parameters(new Object[] {parameters})
                      .timeout(timeoutMillis)
                      .build())
            .tags(jiraConfig.getDelegateSelectors())
            .workflowExecutionId(context.getWorkflowExecutionId())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();
    String delegateTaskId = delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .stateExecutionData(JiraExecutionData.builder().activityId(activityId).build())
        .build();
  }

  void validateRequiredFields(JiraCreateMetaResponse createMeta, ExecutionContext context) {
    validateProject(context);
    validateIssueType(createMeta);
    validateStatus(context);
    validatePriority(createMeta);
  }

  private void validateProject(ExecutionContext context) {
    Map<String, String> projects =
        Arrays
            .stream(
                jiraHelperService.getProjects(jiraConnectorId, context.getAccountId(), context.getAppId()).toArray())
            .map(ob -> (JSONObject) ob)
            .map(existingProjects -> (String) existingProjects.get("key"))
            .collect(Collectors.toMap(String::toLowerCase, projectKey -> projectKey));
    if (!projects.containsKey(project.toLowerCase())) {
      throw new HarnessJiraException(
          String.format("Invalid project key [%s]. Please, check out allowed values: %s", project, projects.values()),
          null);
    } else {
      setProject(projects.get(project.toLowerCase()));
    }
  }

  private void validateStatus(ExecutionContext context) {
    if (StringUtils.isNotBlank(status)) {
      Map<String, String> allowedStatuses =
          Arrays
              .stream(((JSONArray) jiraHelperService.getStatuses(
                           jiraConnectorId, project, context.getAccountId(), context.getAppId()))
                          .toArray())
              .map(ob -> (JSONObject) ob)
              .filter(issue -> issue.get("name").equals(issueType))
              .flatMap(issue -> Arrays.stream(((JSONArray) issue.get("statuses")).toArray()))
              .map(ob -> (JSONObject) ob)
              .map(statuses -> (String) statuses.get("name"))
              .collect(Collectors.toMap(String::toLowerCase, statusValue -> statusValue));
      if (!allowedStatuses.containsKey(status.toLowerCase())) {
        throw new HarnessJiraException(
            String.format("Invalid status [%s]. Please, check out allowed values %s", status, allowedStatuses.values()),
            null);
      } else {
        setStatus(allowedStatuses.get(status.toLowerCase()));
      }
    }
  }

  private void validatePriority(JiraCreateMetaResponse createMetadata) {
    if (StringUtils.isNotBlank(priority)) {
      Map<String, String> priorities =
          createMetadata.getProjects()
              .stream()
              .filter(jiraProjectData -> jiraProjectData.getKey().equals(project))
              .map(JiraProjectData::getIssueTypes)
              .flatMap(Collection::stream)
              .filter(jiraIssue -> jiraIssue.getName().equals(issueType))
              .flatMap(jiraIssue -> jiraIssue.getJiraFields().entrySet().stream())
              .map(Entry::getValue)
              .filter(jiraField -> jiraField.getKey().equals("priority"))
              .flatMap(jiraField -> Arrays.stream(jiraField.getAllowedValues().toArray()))
              .map(json -> (JSONObject) json)
              .map(ob -> ob.get(VALUE) != null ? ((String) ob.get(VALUE)) : ((String) ob.get("name")))
              .collect(Collectors.toMap(String::toLowerCase, priorityValue -> priorityValue));
      if (priorities.isEmpty()) {
        throw new HarnessJiraException(
            String.format(
                "[%s] issue type does not support [priority] field. Please, remove provided value.", issueType),
            null);
      }
      if (!priorities.containsKey(priority.toLowerCase())) {
        throw new HarnessJiraException(
            String.format("Invalid priority: [%s]. Please check out allowed values: %s", priority, priorities.values()),
            null);
      } else {
        setPriority(priorities.get(priority.toLowerCase()));
      }
    }
  }

  private void validateIssueType(JiraCreateMetaResponse createMetadata) {
    Map<String, String> issueTypes = createMetadata.getProjects()
                                         .stream()
                                         .filter(jiraProjectData -> jiraProjectData.getKey().equals(project))
                                         .flatMap(jiraProjectData -> jiraProjectData.getIssueTypes().stream())
                                         .map(JiraIssueType::getName)
                                         .collect(toMap(String::toLowerCase, name -> name));
    if (!issueTypes.containsKey(issueType.toLowerCase())) {
      throw new HarnessJiraException(
          String.format(
              "Invalid issue type: [%s]. Please, check out existing issue types: %s", issueType, issueTypes.values()),
          null);
    } else {
      setIssueType(issueTypes.get(issueType.toLowerCase()));
    }
  }

  private boolean checkIfRequiredFieldsAreTemplatized() {
    return ExpressionEvaluator.containsVariablePattern(project)
        || ExpressionEvaluator.containsVariablePattern(issueType);
  }

  void inferCustomFieldsTypes(JiraCreateMetaResponse createMetaResponse) {
    Map<String, String> allCustomFieldsIdToNameMap =
        createMetaResponse.getProjects()
            .stream()
            .filter(jiraProjectData -> jiraProjectData.getKey().equals(project))
            .map(JiraProjectData::getIssueTypes)
            .flatMap(Collection::stream)
            .filter(jiraIssue -> jiraIssue.getName().equals(issueType))
            .flatMap(jiraIssue -> jiraIssue.getJiraFields().entrySet().stream())
            .map(Entry::getValue)
            .filter(jiraField -> !jiraField.getSchema().get("type").equals("priority"))
            .collect(toMap(JiraField::getKey, JiraField::getName));

    if (allCustomFieldsIdToNameMap.containsKey(TIMETRACKING)) {
      allCustomFieldsIdToNameMap.remove(TIMETRACKING);
      allCustomFieldsIdToNameMap.put(TIME_TRACKING_ORIGINAL_ESTIMATE, TIME_TRACKING_ORIGINAL_ESTIMATE);
      allCustomFieldsIdToNameMap.put(TIME_TRACKING_REMAINING_ESTIMATE, TIME_TRACKING_REMAINING_ESTIMATE);
    }

    validateCustomFieldsNames(allCustomFieldsIdToNameMap);
    replaceCustomFieldNameWithId(allCustomFieldsIdToNameMap);
    Map<String, String> fieldIdToTypeMap = mapCustomFieldIdsToTypes(createMetaResponse);
    fieldIdToTypeMap.put(TIME_TRACKING_ORIGINAL_ESTIMATE, TIMETRACKING);
    fieldIdToTypeMap.put(TIME_TRACKING_REMAINING_ESTIMATE, TIMETRACKING);
    setCustomFieldTypes(fieldIdToTypeMap);
  }

  private void setCustomFieldTypes(Map<String, String> fieldIdToTypeMap) {
    customFields.forEach((key, value) -> value.setFieldType(fieldIdToTypeMap.get(key)));
  }

  private Map<String, String> mapCustomFieldIdsToTypes(JiraCreateMetaResponse createMetaResponse) {
    return createMetaResponse.getProjects()
        .stream()
        .filter(jiraProjectData -> jiraProjectData.getKey().equals(project))
        .map(JiraProjectData::getIssueTypes)
        .flatMap(Collection::stream)
        .filter(jiraIssue -> jiraIssue.getName().equals(issueType))
        .flatMap(jiraIssue -> jiraIssue.getJiraFields().entrySet().stream())
        .map(Entry::getValue)
        .collect(Collectors.toMap(JiraField::getKey, field -> {
          if (field.getSchema().get("type").equals(ARRAY) && field.getAllowedValues() != null) {
            return MULTISELECT;
          } else {
            return (String) field.getSchema().get("type");
          }
        }));
  }

  private void replaceCustomFieldNameWithId(Map<String, String> customFieldsIdToNamesMap) {
    Map<String, String> filteredIdToNameMap = customFieldsIdToNamesMap.entrySet()
                                                  .stream()
                                                  .filter(entry -> customFields.containsKey(entry.getValue()))
                                                  .collect(toMap(Entry::getKey, Entry::getValue));
    Map<String, String> nameToIdsMap =
        filteredIdToNameMap.entrySet().stream().collect(toMap(Entry::getValue, Entry::getKey, (key, duplicateKey) -> {
          throw new HarnessJiraException(
              String.format("Can not not process field [%s] since there are another fields with the same name.",
                  customFieldsIdToNamesMap.get(duplicateKey)),
              null);
        }));

    setCustomFields(customFields.entrySet().stream().collect(Collectors.toMap(entry -> {
      if (entry.getKey().contains("TimeTracking:")) {
        return entry.getKey();
      } else {
        return nameToIdsMap.get(entry.getKey());
      }
    }, Entry::getValue)));
  }

  private void validateCustomFieldsNames(Map<String, String> customFieldsIdToNamesMap) {
    Set<String> invalidCustomFieldNames = new HashSet<>();
    Collection<String> allowedCustomFieldNames = customFieldsIdToNamesMap.values();
    for (String fieldName : customFields.keySet()) {
      if (!allowedCustomFieldNames.contains(fieldName)) {
        invalidCustomFieldNames.add(fieldName);
      }
    }
    if (!invalidCustomFieldNames.isEmpty()) {
      throw new HarnessJiraException(
          String.format(INVALID_FIELD_NAME_ERROR_MESSAGE, English.plural("name", invalidCustomFieldNames.size()),
              invalidCustomFieldNames, allowedCustomFieldNames),
          null);
    }
  }

  void resolveCustomFieldsVars(
      Map<String, String> customFieldsIdToNameMap, Map<String, Map<Object, Object>> customFieldsValueToIdMap) {
    for (Entry<String, JiraCustomFieldValue> customFieldValueEntry : customFields.entrySet()) {
      if (customFieldsIdToNameMap.get(customFieldValueEntry.getKey()) != null
          && !customFieldValueEntry.getValue().getFieldType().equals(NUMBER)) {
        Set<Object> allowedValues = customFieldsValueToIdMap.get(customFieldValueEntry.getKey()).keySet();
        Collection<Object> allowedIds = customFieldsValueToIdMap.get(customFieldValueEntry.getKey()).values();
        String customFieldName = customFieldsIdToNameMap.get(customFieldValueEntry.getKey());
        Map<Object, Object> customField = customFieldsValueToIdMap.get(customFieldValueEntry.getKey());
        List<String> fieldValues;
        StringJoiner fieldIds = new StringJoiner(",");
        if (customFieldValueEntry.getValue().getFieldType().equals(MULTISELECT)) {
          Set<String> invalidValues = new HashSet<>();
          fieldValues = Arrays.asList(customFieldValueEntry.getValue().getFieldValue().replace(" ,", ",").split(","));
          for (String value : fieldValues) {
            String fieldId = (String) customField.get(value.trim().toLowerCase());
            if (fieldId == null) {
              if (allowedIds.contains(value)) {
                fieldId = value;
              } else {
                invalidValues.add(value);
              }
            }
            fieldIds.add(fieldId);
          }
          if (!invalidValues.isEmpty()) {
            throw new HarnessJiraException(
                String.format(INVALID_VALUES_ERROR_MESSAGE, invalidValues, customFieldName, allowedValues), null);
          }
        } else if (customFieldValueEntry.getValue().getFieldType().equals(OPTION)
            || customFieldValueEntry.getValue().getFieldType().equals(RESOLUTION)) {
          String value = customFieldValueEntry.getValue().getFieldValue().trim();
          String fieldId = (String) customField.get(value.toLowerCase());
          if (fieldId == null) {
            if (allowedIds.contains(value)) {
              fieldId = value;
            } else {
              throw new HarnessJiraException(
                  String.format(INVALID_VALUE_ERROR_MESSAGE, value, customFieldName, allowedValues), null);
            }
          }
          fieldIds.add(fieldId);
        }
        customFieldValueEntry.getValue().setFieldValue(fieldIds.toString());
      }
    }
  }

  private void parseCustomFieldsDateTimeNumber(ExecutionContext context, Map<String, String> idToNameMap) {
    if (isNotEmpty(customFields)) {
      for (Entry<String, JiraCustomFieldValue> customField : customFields.entrySet()) {
        JiraCustomFieldValue value = customField.getValue();
        String customFieldName = idToNameMap.get(customField.getKey());
        if (value.getFieldType().equals("date")) {
          String parsedDateValue = parseDateValue(value.getFieldValue(), context);
          value.setFieldValue(parsedDateValue);
        }
        if (value.getFieldType().equals("datetime")) {
          String parsedDateTimeValue = parseDateTimeValue(value.getFieldValue(), context);
          value.setFieldValue(parsedDateTimeValue);
        }
        if (value.getFieldType().equals(NUMBER)) {
          String parsedNumber = parseNumberValue(value.getFieldValue(), context, customFieldName);
          value.setFieldValue(parsedNumber);
        }
      }
    }
  }

  Map<String, String> mapCustomFieldsIdsToNames(JiraCreateMetaResponse createMetadata) {
    return createMetadata.getProjects()
        .stream()
        .filter(jiraProjectData -> jiraProjectData.getKey().equals(project))
        .map(JiraProjectData::getIssueTypes)
        .flatMap(Collection::stream)
        .filter(jiraIssue -> jiraIssue.getName().equals(issueType))
        .flatMap(jiraIssue -> jiraIssue.getJiraFields().entrySet().stream())
        .map(Entry::getValue)
        .filter(
            jiraField -> customFields.containsKey(jiraField.getKey()) || customFields.containsKey(jiraField.getName()))
        .filter(jiraField
            -> jiraField.getSchema().get("type").equals(OPTION) || jiraField.getSchema().get("type").equals(RESOLUTION)
                || (jiraField.getSchema().get("type").equals(ARRAY) && jiraField.getAllowedValues() != null)
                || jiraField.getSchema().get("type").equals(NUMBER))
        .collect(toMap(JiraField::getKey, JiraField::getName));
  }

  Map<String, Map<Object, Object>> mapCustomFieldsValuesToId(
      JiraCreateMetaResponse createMetadata, Collection<String> customFieldNames) {
    return createMetadata.getProjects()
        .stream()
        .filter(jiraProjectData -> jiraProjectData.getKey().equals(project))
        .map(JiraProjectData::getIssueTypes)
        .flatMap(Collection::stream)
        .filter(jiraIssue -> jiraIssue.getName().equals(issueType))
        .flatMap(jiraIssue -> jiraIssue.getJiraFields().entrySet().stream())
        .map(Entry::getValue)
        .filter(
            jiraField -> customFields.containsKey(jiraField.getKey()) || customFieldNames.contains(jiraField.getName()))
        .filter(jiraField
            -> jiraField.getSchema().get("type").equals(OPTION) || jiraField.getSchema().get("type").equals(RESOLUTION)
                || (jiraField.getSchema().get("type").equals(ARRAY) && jiraField.getAllowedValues() != null))
        .collect(toMap(JiraField::getKey,
            jiraField
            -> Arrays.stream(jiraField.getAllowedValues().toArray())
                   .map(json -> (JSONObject) json)
                   .collect(toMap(ob
                       -> (ob.get(VALUE) != null && !((String) ob.get(VALUE)).isEmpty())
                           ? ((String) ob.get(VALUE)).toLowerCase()
                           : ((String) ob.get("name")).toLowerCase(),
                       ob -> ob.get("id"), (id, duplicateId) -> {
                         throw new HarnessJiraException(
                             String.format(
                                 "Can not process value for field [%s] since there are multiple values with the same name.",
                                 jiraField.getName()),
                             null);
                       }))));
  }

  String parseUIDateTimeValue(String fieldValue) {
    DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                                      // date/time
                                      .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                      // offset (hh:mm - "+00:00" when it's zero)
                                      .optionalStart()
                                      .appendOffset("+HH:MM", "+00:00")
                                      .optionalEnd()
                                      // offset (hhmm - "+0000" when it's zero)
                                      .optionalStart()
                                      .appendOffset("+HHMM", "+0000")
                                      .optionalEnd()
                                      // offset (hh - "Z" when it's zero)
                                      .optionalStart()
                                      .appendOffset("+HH", "Z")
                                      .optionalEnd()
                                      // create formatter
                                      .toFormatter();
    Matcher matcher = uiPattern.matcher(fieldValue);
    if (matcher.matches()) {
      try {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATETIME_UI_FORMAT);
        return String.valueOf(dateFormat.parse(matcher.group(0)).getTime());
      } catch (ParseException e) {
        throw new InvalidRequestException("Cannot parse date time value from " + fieldValue, USER);
      }
    }
    matcher = uiCalPattern.matcher(fieldValue);
    if (matcher.matches()) {
      try {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATETIME_UI_CAL_FORMAT);
        return String.valueOf(dateFormat.parse(matcher.group(0)).getTime());
      } catch (ParseException e) {
        throw new InvalidRequestException("Cannot parse date time value from " + fieldValue, USER);
      }
    }
    matcher = uiCurrentPattern.matcher(fieldValue);
    if (matcher.matches() && matcher.groupCount() >= 3) {
      long timestampAcc;
      List<String> acc = List.of(matcher.group(3).trim().split(" "));
      long msToAcc = acc.stream()
                         .map(v -> {
                           String[] part = v.split("(?<=\\d)(?=\\D)");
                           return Long.parseLong(part[0]) * MILLISECONDS_PER_UNIT.get(part[1]);
                         })
                         .reduce(0L, Long::sum)
                         .longValue();
      if (matcher.group(2).equals("+")) {
        timestampAcc = System.currentTimeMillis() + msToAcc;
      } else if (matcher.group(2).equals("-")) {
        timestampAcc = System.currentTimeMillis() - msToAcc;
      } else {
        throw new InvalidRequestException("Cannot parse date time value from " + fieldValue, USER);
      }
      return String.valueOf(timestampAcc);
    }
    matcher = ISO8601Format.matcher(fieldValue);
    if (matcher.matches()) {
      try {
        OffsetDateTime odt = OffsetDateTime.parse(matcher.group(0), formatter);
        return String.valueOf(
            odt.atZoneSameInstant(odt.toZonedDateTime().getZone()).toEpochSecond() * Duration.ofSeconds(1).toMillis());
      } catch (DateTimeException e) {
        throw new InvalidRequestException("Cannot parse date time value from " + fieldValue, USER);
      }
    }
    throw new InvalidRequestException("Cannot parse date time value from " + fieldValue, USER);
  }

  String parseDateTimeValue(String fieldValue, ExecutionContext context) {
    Matcher matcher = fixedDatetimePattern.matcher(fieldValue);
    if (matcher.matches()) {
      return fieldValue;
    }
    long val1;
    matcher = currentPattern.matcher(fieldValue);
    if (matcher.matches()) {
      val1 = System.currentTimeMillis();
      return getDateTime(matcher, val1);
    }
    matcher = varPattern.matcher(fieldValue);
    if (matcher.matches()) {
      String renderedVal = context.renderExpression(matcher.group(1));
      renderedVal = renderedVal.replaceAll("\\s+", "");
      try {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATETIME_ISO_FORMAT);
        val1 = dateFormat.parse(renderedVal).getTime();
      } catch (ParseException e) {
        try {
          val1 = Long.parseLong(renderedVal);
        } catch (NumberFormatException ne) {
          throw new InvalidRequestException("Cannot parse date time value from " + renderedVal, ne, USER);
        }
      }
      return getDateTime(matcher, val1);
    } else {
      if (featureFlagService.isEnabled(FeatureName.SPG_ALLOW_UI_JIRA_CUSTOM_DATETIME_FIELD, context.getAccountId())) {
        return parseUIDateTimeValue(fieldValue);
      }
      throw new InvalidRequestException("Cannot parse date time value from " + fieldValue, USER);
    }
  }

  String parseNumberValue(String fieldValue, ExecutionContext context, String customFieldName) {
    double parsedNumber;
    try {
      parsedNumber = Double.parseDouble(context.renderExpression(fieldValue));
    } catch (NumberFormatException e) {
      throw new InvalidRequestException(
          String.format("Invalid value provided for field: %1$s. %1$s field is of type 'number'.", customFieldName));
    }
    return String.valueOf(parsedNumber);
  }

  private String getDateTime(Matcher matcher, Long val1) {
    long val2;
    String group4 = matcher.group(4);
    if (isNotEmpty(group4)) {
      val2 = Long.parseLong(group4);
      if (matcher.group(3).equals("+")) {
        return String.valueOf(val1 + val2);
      } else {
        return String.valueOf(val1 - val2);
      }
    } else {
      return String.valueOf(val1);
    }
  }

  String parseDateValue(String fieldValue, ExecutionContext context) {
    Matcher matcher = fixedDatePattern.matcher(fieldValue);
    if (matcher.matches()) {
      return fieldValue;
    }
    long val1;
    matcher = currentPattern.matcher(fieldValue);
    if (matcher.matches()) {
      val1 = System.currentTimeMillis();
      return getDateValue(matcher, val1);
    }
    matcher = varPattern.matcher(fieldValue);
    if (matcher.matches()) {
      String renderedVal = context.renderExpression(matcher.group(1));
      renderedVal = renderedVal.replaceAll("\\s+", "");
      try {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_ISO_FORMAT);
        val1 = dateFormat.parse(renderedVal).getTime();
        return getDateValue(matcher, val1);
      } catch (ParseException e) {
        try {
          val1 = Long.parseLong(renderedVal);
          return getDateValue(matcher, val1);
        } catch (NumberFormatException ne) {
          throw new InvalidRequestException("Cannot parse date value from " + renderedVal, ne, USER);
        }
      }
    }
    throw new InvalidRequestException("Cannot parse date value from " + fieldValue
            + ". Update the value to match the following format: " + DATE_ISO_FORMAT,
        USER);
  }

  private String getDateValue(Matcher matcher, Long val1) {
    long val2;
    String group4 = matcher.group(4);
    if (isNotEmpty(group4)) {
      val2 = Long.parseLong(group4);
      if (matcher.group(3).equals("+")) {
        return new SimpleDateFormat(DATE_ISO_FORMAT).format(new Date(val1 + val2));
      } else {
        return new SimpleDateFormat(DATE_ISO_FORMAT).format(new Date(val1 - val2));
      }
    } else {
      return new SimpleDateFormat(DATE_ISO_FORMAT).format(new Date(val1));
    }
  }

  private List<String> parseExpression(String issueId) {
    List<String> issueIds;
    issueId = issueId.replaceAll("[\\[\\](){}?:!.,;]+", " ").trim();
    issueIds = Arrays.asList(issueId.split(" "));
    return issueIds.stream().filter(EmptyPredicate::isNotEmpty).map(String::trim).collect(Collectors.toList());
  }

  private void renderExpressions(ExecutionContext context) {
    project = context.renderExpression(project);
    issueType = context.renderExpression(issueType);
    priority = context.renderExpression(priority);
    status = context.renderExpression(status);
    issueId = context.renderExpression(issueId);
    labels = context.renderExpressionList(labels);
    summary = context.renderExpression(summary);
    description = context.renderExpression(description);
    comment = context.renderExpression(comment);
    if (EmptyPredicate.isNotEmpty(customFields)) {
      Map<String, JiraCustomFieldValue> renderedCustomFields = new HashMap<>();
      customFields.forEach((key, value) -> {
        JiraCustomFieldValue rendered = new JiraCustomFieldValue();
        rendered.setFieldType(value.getFieldType());
        rendered.setFieldValue(context.renderExpression(value.getFieldValue()));
        renderedCustomFields.put(context.renderExpression(key), rendered);
      });
      customFields = renderedCustomFields;
    }
  }

  private JiraConfig getJiraConfig(String jiraConnectorId, String accountId) {
    SettingAttribute jiraSettingAttribute = settingsService.getByAccountAndId(accountId, jiraConnectorId);
    notNullCheck("Jira ConnectorDisconnectHandler doesn't exist", jiraSettingAttribute);

    if (!(jiraSettingAttribute.getValue() instanceof JiraConfig)) {
      throw new InvalidRequestException("Type of Setting Attribute Value is not JiraConfig");
    }

    return (JiraConfig) jiraSettingAttribute.getValue();
  }

  private String createActivity(ExecutionContext executionContext) {
    Application app = ((ExecutionContextImpl) executionContext).fetchRequiredApp();

    ActivityBuilder activityBuilder = Activity.builder()
                                          .applicationName(app.getName())
                                          .commandName(getName())
                                          .type(Type.Command)
                                          .workflowType(executionContext.getWorkflowType())
                                          .workflowExecutionName(executionContext.getWorkflowExecutionName())
                                          .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
                                          .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
                                          .commandType(getStateType())
                                          .workflowExecutionId(executionContext.getWorkflowExecutionId())
                                          .workflowId(executionContext.getWorkflowId())
                                          .commandUnits(Arrays.asList())
                                          .status(ExecutionStatus.RUNNING);

    if (executionContext.getOrchestrationWorkflowType() != null
        && executionContext.getOrchestrationWorkflowType() == BUILD) {
      activityBuilder.environmentId(GLOBAL_ENV_ID).environmentName(GLOBAL_ENV_ID).environmentType(ALL);
    } else {
      Environment env = ((ExecutionContextImpl) executionContext).fetchRequiredEnvironment();
      activityBuilder.environmentId(env.getUuid())
          .environmentName(env.getName())
          .environmentType(env.getEnvironmentType());
    }

    Activity activity = activityBuilder.build();
    activity.setAppId(app.getUuid());

    return activityService.save(activity).getUuid();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    Entry<String, ResponseData> responseEntry = response.entrySet().iterator().next();
    String activityId = responseEntry.getKey();

    JiraExecutionData jiraExecutionData = (JiraExecutionData) responseEntry.getValue();
    jiraExecutionData.setActivityId(activityId);

    if (jiraExecutionData.getExecutionStatus() == ExecutionStatus.SUCCESS) {
      Map<String, Object> sweepingOutputMap = new HashMap<>();
      sweepingOutputMap.put(JIRA_ISSUE_ID, jiraExecutionData.getIssueId());
      sweepingOutputMap.put(JIRA_ISSUE_KEY, jiraExecutionData.getIssueKey());
      sweepingOutputMap.put(JIRA_ISSUE, jiraExecutionData.getJiraIssueData());
      handleSweepingOutput(sweepingOutputService, context, sweepingOutputMap);
    }

    return ExecutionResponse.builder()
        .stateExecutionData(jiraExecutionData)
        .executionStatus(jiraExecutionData.getExecutionStatus())
        .errorMessage(jiraExecutionData.getErrorMessage())
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    if (context == null || context.getStateExecutionData() == null) {
      return;
    }
    context.getStateExecutionData().setErrorMsg(
        "Jira create or update did not complete within timeout " + (getTimeoutMillis() / 1000) + " (s)");
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> results = new HashMap<>();
    if (isEmpty(jiraConnectorId) || isEmpty(project) || isEmpty(issueType)) {
      log.info("Connector Id not present in Jira State");
      results.put("Required Fields missing", "Connector, Project and IssueType must be provided.");
      return results;
    }
    if (isNotEmpty(customFields)) {
      for (Entry<String, JiraCustomFieldValue> customField : customFields.entrySet()) {
        if (customField.getValue() == null) {
          log.info("Field value null for a custom field selected: " + customField.getKey());
          results.put("Field value missing", "Value must be provided for " + customField.getKey());
          continue;
        }
        if (StringUtils.isNotBlank(customField.getKey()) && customField.getKey().startsWith("TimeTracking:")
            && StringUtils.isNotBlank(customField.getValue().getFieldValue())
            && !ExpressionEvaluator.containsVariablePattern(customField.getValue().getFieldValue())
            && !customField.getValue().getFieldValue().matches("^(\\d+(w ?))?(\\d+(d ?))?(\\d+(h ?))?(\\d+(m ?))?$")) {
          log.info(String.format("Invalid value format for %s field", customField.getKey()));
          results.put("Invalid value format provided for field: " + customField.getKey(),
              "Verify provided value: " + customField.getValue().getFieldValue());
        }
        if (!checkIfRequiredFieldsAreTemplatized()) {
          JiraCustomFieldValue value = customField.getValue();
          if (value.getFieldType() == null) {
            log.info("Field Type null for a custom field selected: " + customField.getKey());
            results.put("Field Type missing", "Type must be provided for " + customField.getKey());
            continue;
          }
          if (value.getFieldType().equals("datetime") || value.getFieldType().equals("date")) {
            String fieldValue = value.getFieldValue();
            if (!validateDateFieldValue(fieldValue, value.getFieldType())) {
              log.info("Field value not valid for a custom field selected: {} {} ", customField.getKey(),
                  customField.getValue().getFieldValue());
              results.put("Invalid field value", "Value provided for " + customField.getKey() + " is not valid");
            }
          }
        }
      }
    }

    log.info("Jira State Validated");
    return results;
  }

  private boolean validateDateFieldValue(String fieldValue, String fieldType) {
    Matcher matcher = null;
    if (fieldType.equals("date")) {
      matcher = fixedDatePattern.matcher(fieldValue);
    } else {
      matcher = fixedDatetimePattern.matcher(fieldValue);
    }

    if (matcher.matches()) {
      return true;
    }
    matcher = currentPattern.matcher(fieldValue);
    if (matcher.matches()) {
      return true;
    }
    matcher = varPattern.matcher(fieldValue);
    return matcher.matches();
  }

  @Override
  public KryoSerializer getKryoSerializer() {
    return kryoSerializer;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
