/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.collaboration;

import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.RAFAEL;

import static software.wings.beans.TaskType.JIRA;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.JIRA_CONNECTOR_ID;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.GeneralException;
import io.harness.exception.HarnessJiraException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.jira.JiraAction;
import io.harness.jira.JiraCreateMetaResponse;
import io.harness.jira.JiraCustomFieldValue;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.jira.JiraExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.JiraConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.JiraHelperService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class JiraCreateUpdateTest extends WingsBaseTest {
  private static final String MULTI = "multi";
  private static final String ISSUE_TYPE = "Issue Type";

  private static final String ISSUE_ID = "IssueId";
  private static final String CUSTOMFIELD_OPTION = "customfield_option";
  private static final String CUSTOMFIELD_OPTION_2 = "customfield_option_2";
  private static final String CUSTOMFIELD_OPTION_3 = "customfield_option_3";
  private static final String MULTISELECTNAME = "multiselectname1";
  private static final String MULTISELECT_ID = "multiselect_id_1";
  private static final String MULTISELECTNAME_2 = "multiselectname2";
  private static final String MULTISELECT_ID_2 = "multiselect_id_2";
  private static final String OPTIONVALUE = "optionvalue1";
  private static final String OPTION_ID = "option_id_1";
  private static final String OPTIONVALUE_2 = "optionvalue2";
  private static final String OPTION_ID_2 = "option_id_2";
  private static final String OPTION_NAME = "Option Name";
  private static final String UNKNOWN = "UNKNOWN";
  private static final String TO_DO = "To Do";
  private static final String MULTISELECT = "multiselect";
  private static final String CUSTOMFIELD_OPTION_NUMBER = "customfield_option_number";
  @Mock private ExecutionContextImpl context;
  @Mock private JiraHelperService jiraHelperService;
  @Mock private SettingsService settingsService;
  @Mock private ActivityService activityService;
  @Mock private DelegateService delegateService;
  @Mock private SecretManager secretManager;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @InjectMocks JiraCreateUpdate jiraCreateUpdateState = new JiraCreateUpdate("Jira");
  private static JiraCreateMetaResponse createMetaResponse;
  private static JSONArray projects;
  private static Object statuses;

  @BeforeClass
  public static void setup() throws IOException {
    JSONObject jsonObject =
        new ObjectMapper().readValue(new File("400-rest/src/test/resources/mock_create_meta"), JSONObject.class);
    createMetaResponse = new JiraCreateMetaResponse(jsonObject);
    projects = new ObjectMapper().readValue(new File("400-rest/src/test/resources/mock_projects"), JSONArray.class);
    statuses = new ObjectMapper().readValue(new File("400-rest/src/test/resources/mock_statuses"), JSONArray.class);
  }

  @Before
  public void setUpMocks() {
    when(context.getAccountId()).thenReturn(ACCOUNT_ID);
    when(context.getAppId()).thenReturn(APP_ID);
    when(jiraHelperService.getProjects(any(), any(), any())).thenReturn(projects);
    when(jiraHelperService.getStatuses(any(), any(), any(), any())).thenReturn(statuses);
    jiraCreateUpdateState.setTimeoutMillis(0);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testValidateFields() {
    Map<String, String> results = jiraCreateUpdateState.validateFields();
    assertThat(results).isNotEmpty();
    assertThat(results.values().iterator().next()).isEqualTo("Connector, Project and IssueType must be provided.");
    jiraCreateUpdateState.setJiraConnectorId("CONNECTOR_ID");
    results = jiraCreateUpdateState.validateFields();
    assertThat(results).isNotEmpty();
    assertThat(results.values().iterator().next()).isEqualTo("Connector, Project and IssueType must be provided.");

    jiraCreateUpdateState.setProject("TJI");
    jiraCreateUpdateState.setIssueType("Story");
    Map<String, JiraCustomFieldValue> customFieldValueMap = new HashMap<>();
    jiraCreateUpdateState.setCustomFields(customFieldValueMap);
    JiraCustomFieldValue date = new JiraCustomFieldValue("date", "2014-04-05");
    JiraCustomFieldValue datetime = new JiraCustomFieldValue("datetime", "1234567891011");
    customFieldValueMap.put("due date", date);
    customFieldValueMap.put("start date time", datetime);
    results = jiraCreateUpdateState.validateFields();
    assertThat(results).isEmpty();

    date = new JiraCustomFieldValue("date", "current() + 123456789");
    datetime = new JiraCustomFieldValue("datetime", "${test.var1}+123456789");
    customFieldValueMap.put("due date", date);
    customFieldValueMap.put("start date time", datetime);
    results = jiraCreateUpdateState.validateFields();
    assertThat(results).isEmpty();

    date = new JiraCustomFieldValue("date", "Current() + 123456789");
    datetime = new JiraCustomFieldValue("datetime", "${test.var1}+123456789");
    customFieldValueMap.put("due date", date);
    customFieldValueMap.put("start date time", datetime);
    results = jiraCreateUpdateState.validateFields();
    assertThat(results).isEmpty();

    date = new JiraCustomFieldValue("date", "CURRENT() + 123456789");
    datetime = new JiraCustomFieldValue("datetime", "${test.Var1}+123456789");
    customFieldValueMap.put("due date", date);
    customFieldValueMap.put("start date time", datetime);
    results = jiraCreateUpdateState.validateFields();
    assertThat(results).isEmpty();

    date = new JiraCustomFieldValue("date", "Current() + 123456789");
    datetime = new JiraCustomFieldValue("datetime", "${test.var 1}+123456789");
    customFieldValueMap.put("due date", date);
    customFieldValueMap.put("start date time", datetime);
    results = jiraCreateUpdateState.validateFields();
    assertThat(results).isNotEmpty();

    JiraCustomFieldValue timeTracking = new JiraCustomFieldValue("timetracking", "1day");
    customFieldValueMap.put("TimeTracking:OriginalEstimate", timeTracking);
    results = jiraCreateUpdateState.validateFields();
    assertThat(results.get("Invalid value format provided for field: TimeTracking:OriginalEstimate"))
        .isEqualTo("Verify provided value: 1day");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testParseDateTimeValue() {
    String fieldValue = "2014-03-03";
    String parsedVal = jiraCreateUpdateState.parseDateValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(fieldValue);

    fieldValue = "current() +172800000";
    parsedVal = jiraCreateUpdateState.parseDateValue(fieldValue, context);
    String expectedVal = new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis() + 172800000));
    assertThat(parsedVal).isEqualTo(expectedVal);

    fieldValue = "Current() -172800000";
    parsedVal = jiraCreateUpdateState.parseDateValue(fieldValue, context);
    expectedVal = new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis() - 172800000));
    assertThat(parsedVal).isEqualTo(expectedVal);

    fieldValue = "${workflow.variables.date}+172800000";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("2014-03-03");
    parsedVal = jiraCreateUpdateState.parseDateValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo("2014-03-05");

    fieldValue = "${workflow.variables.date}-172800000";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("2014-03-03");
    parsedVal = jiraCreateUpdateState.parseDateValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo("2014-03-01");

    fieldValue = "${workflow.variables.date}-172800000";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("1393785000000");
    parsedVal = jiraCreateUpdateState.parseDateValue(fieldValue, context);
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date(1393785000000L - 172800000L);
    assertThat(parsedVal).isEqualTo(f.format(date));

    fieldValue = "${workflow.variables.date} - ";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("2014-03-03");
    parsedVal = jiraCreateUpdateState.parseDateValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo("2014-03-03");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testParseDateTimeValueWhenFFEnables() {
    when(featureFlagService.isEnabled(eq(FeatureName.SPG_ALLOW_UI_JIRA_CUSTOM_DATETIME_FIELD), any())).thenReturn(true);
    String expectedVal = "";

    String fieldValue = "1234567891011";
    String parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(fieldValue);

    fieldValue = "${workflow.variables.date}+172800000";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("1234567891911");
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(String.valueOf(1234567891911L + 172800000L));

    fieldValue = "${workflow.variables.Date}-172800000";
    when(context.renderExpression("${workflow.variables.Date}")).thenReturn("1234567891911");
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(String.valueOf(1234567891911L - 172800000L));

    fieldValue = "${workflow.variables.date} -";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("2010-01-29T12:20:00Z");
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(String.valueOf(1264767600000L));

    fieldValue = "current() *2d";
    String finalFieldValue = fieldValue;
    assertThatThrownBy(() -> jiraCreateUpdateState.parseDateTimeValue(finalFieldValue, context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot parse date time value from current() *2d");

    fieldValue = "2022/12/24 10:00";
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    expectedVal = "1671876000000";
    assertThat(parsedVal).isEqualTo(expectedVal);

    fieldValue = "01/23/2023 10:00";
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    expectedVal = "1674468000000";
    assertThat(parsedVal).isEqualTo(expectedVal);

    fieldValue = "2023-01-23T10:10:00.0000-03:00";
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    expectedVal = "1674479400000";
    assertThat(parsedVal).isEqualTo(expectedVal);

    JiraCreateUpdate jiraObjSpy = Mockito.spy(new JiraCreateUpdate("test"));
    fieldValue = "current() +172800000";
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    verify(jiraObjSpy, times(0)).parseUIDateTimeValue(any());

    fieldValue = "current() -172800000";
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    verify(jiraObjSpy, times(0)).parseUIDateTimeValue(any());

    fieldValue = "current() +2d";
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    verify(jiraObjSpy, times(0)).parseUIDateTimeValue(any());

    fieldValue = "current() -2d";
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    verify(jiraObjSpy, times(0)).parseUIDateTimeValue(any());
  }

  @Test
  @Owner(developers = POOJA, intermittent = true)
  @Category(UnitTests.class)
  public void testParseDateValue() {
    Instant.now(Clock.fixed(Instant.parse("2023-01-23T10:00:00Z"), ZoneOffset.UTC));
    String expectedVal = "";
    String fieldValue = "1234567891011";
    String parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(fieldValue);

    fieldValue = "${workflow.variables.date}+172800000";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("1234567891911");
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(String.valueOf(1234567891911L + 172800000L));

    fieldValue = "${workflow.variables.Date}-172800000";
    when(context.renderExpression("${workflow.variables.Date}")).thenReturn("1234567891911");
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(String.valueOf(1234567891911L - 172800000L));

    fieldValue = "${workflow.variables.date} -";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("2010-01-29T12:20:00Z");
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(String.valueOf(1264767600000L));

    fieldValue = "current() +2d";
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    String finalFieldValue1 = fieldValue;
    assertThatThrownBy(() -> jiraCreateUpdateState.parseDateTimeValue(finalFieldValue1, context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot parse date time value from current() *2d");

    fieldValue = "current() -2d";
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    String finalFieldValue2 = fieldValue;
    assertThatThrownBy(() -> jiraCreateUpdateState.parseDateTimeValue(finalFieldValue2, context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot parse date time value from current() *2d");

    fieldValue = "current() *2d";
    String finalFieldValue3 = fieldValue;
    assertThatThrownBy(() -> jiraCreateUpdateState.parseDateTimeValue(finalFieldValue3, context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot parse date time value from current() *2d");

    fieldValue = "2022/12/24 10:00";
    String finalFieldValue4 = fieldValue;
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThatThrownBy(() -> jiraCreateUpdateState.parseDateTimeValue(finalFieldValue4, context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot parse date time value from current() *2d");

    fieldValue = "01/23/2023 10:00";
    String finalFieldValue5 = fieldValue;
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThatThrownBy(() -> jiraCreateUpdateState.parseDateTimeValue(finalFieldValue5, context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot parse date time value from current() *2d");

    fieldValue = "2014-12-03T10:05:59.5646+09:00";
    String finalFieldValue6 = fieldValue;
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThatThrownBy(() -> jiraCreateUpdateState.parseDateTimeValue(finalFieldValue6, context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot parse date time value from current() *2d");

    JiraCreateUpdate jiraObjSpy = Mockito.spy(new JiraCreateUpdate("test"));
    fieldValue = "current() +172800000";
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    verify(jiraObjSpy, times(0)).parseUIDateTimeValue(any());

    fieldValue = "current() -172800000";
    jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    verify(jiraObjSpy, times(0)).parseUIDateTimeValue(any());
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testParseDateTimeZoneValue() {
    String fieldValue = "${workflow.variables.date}";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("2010-01-29T 00:20:00-0800");
    String parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(String.valueOf(1264753200000L));

    fieldValue = "${workflow.variables.date}";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("2010-01-29T00:20:00+0530");
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(String.valueOf(1264704600000L));

    fieldValue = "${workflow.variables.date}";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("2010-01-29T00:20:00+0000");
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(String.valueOf(1264724400000L));

    fieldValue = "${workflow.variables.date}";
    when(context.renderExpression("${workflow.variables.date}")).thenReturn("2010-01-29T00:20:00Z");
    parsedVal = jiraCreateUpdateState.parseDateTimeValue(fieldValue, context);
    assertThat(parsedVal).isEqualTo(String.valueOf(1264724400000L));
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnCustomFieldIdsToNamesMap() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    customFields.put(MULTI, null);
    customFields.put(CUSTOMFIELD_OPTION, null);
    customFields.put(CUSTOMFIELD_OPTION_2, null);
    customFields.put(CUSTOMFIELD_OPTION_3, null);
    customFields.put(CUSTOMFIELD_OPTION_NUMBER, null);
    jiraCreateUpdateState.setCustomFields(customFields);
    Map<String, String> map = new HashMap<>();
    map.put(MULTI, "Multiselect");
    map.put(CUSTOMFIELD_OPTION, OPTION_NAME);
    map.put(CUSTOMFIELD_OPTION_2, "Option Name2");
    map.put(CUSTOMFIELD_OPTION_3, "Option Name3");
    map.put(CUSTOMFIELD_OPTION_NUMBER, "Number");

    assertThat(jiraCreateUpdateState.mapCustomFieldsIdsToNames(createMetaResponse)).isEqualTo(map);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnCustomFieldsValuesToIdMap() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    customFields.put(MULTI, null);
    customFields.put(CUSTOMFIELD_OPTION, null);
    customFields.put(CUSTOMFIELD_OPTION_2, null);
    jiraCreateUpdateState.setCustomFields(customFields);
    Map<String, Map<String, String>> map = new HashMap<>();
    Map<String, String> multi = new HashMap<>();
    multi.put(MULTISELECTNAME, MULTISELECT_ID);
    multi.put(MULTISELECTNAME_2, MULTISELECT_ID_2);
    Map<String, String> option = new HashMap<>();
    option.put(OPTIONVALUE, OPTION_ID);
    option.put(OPTIONVALUE_2, OPTION_ID_2);
    Map<String, String> option2 = new HashMap<>();
    option2.put(OPTIONVALUE, OPTION_ID);
    option2.put(OPTIONVALUE_2, OPTION_ID_2);
    map.put(MULTI, multi);
    map.put(CUSTOMFIELD_OPTION, option);
    map.put(CUSTOMFIELD_OPTION_2, option2);

    assertThat(jiraCreateUpdateState.mapCustomFieldsValuesToId(createMetaResponse, Collections.emptyList()))
        .isEqualTo(map);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnCustomFieldsValuesToIdMapForOnlyUsedCustomFields() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    customFields.put(MULTI, null);
    customFields.put(CUSTOMFIELD_OPTION_2, null);
    jiraCreateUpdateState.setCustomFields(customFields);
    Map<String, Map<String, String>> map = new HashMap<>();
    Map<String, String> multi = new HashMap<>();
    multi.put(MULTISELECTNAME, MULTISELECT_ID);
    multi.put(MULTISELECTNAME_2, MULTISELECT_ID_2);
    Map<String, String> option = new HashMap<>();
    option.put(OPTIONVALUE, OPTION_ID);
    option.put(OPTIONVALUE_2, OPTION_ID_2);
    map.put(MULTI, multi);
    map.put(CUSTOMFIELD_OPTION_2, option);

    assertThat(jiraCreateUpdateState.mapCustomFieldsValuesToId(createMetaResponse, Collections.emptyList()))
        .isEqualTo(map);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnCustomFieldsValuesToIdMapForOnlyUsedCustomFieldsWithTemplatizedRequiredFields() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    customFields.put("Multiselect", null);
    jiraCreateUpdateState.setCustomFields(customFields);
    Map<String, Map<String, String>> map = new HashMap<>();
    Map<String, String> multi = new HashMap<>();
    multi.put(MULTISELECTNAME, MULTISELECT_ID);
    multi.put(MULTISELECTNAME_2, MULTISELECT_ID_2);
    map.put(MULTI, multi);

    assertThat(
        jiraCreateUpdateState.mapCustomFieldsValuesToId(createMetaResponse, Collections.singletonList("Multiselect")))
        .isEqualTo(map);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenThereAreMultipleCustomFieldValuesWithTheSameName() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    customFields.put(MULTI, null);
    customFields.put(CUSTOMFIELD_OPTION_3, null);
    jiraCreateUpdateState.setCustomFields(customFields);

    assertThatThrownBy(
        () -> jiraCreateUpdateState.mapCustomFieldsValuesToId(createMetaResponse, Collections.emptyList()))
        .isInstanceOf(HarnessJiraException.class)
        .hasMessage(
            "Can not process value for field [Option Name3] since there are multiple values with the same name.");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldResolveCustomOptionFieldsVars() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    JiraCustomFieldValue customFieldOptionValue = new JiraCustomFieldValue();
    customFieldOptionValue.setFieldType("option");
    customFieldOptionValue.setFieldValue("OptionValue2");
    customFields.put(CUSTOMFIELD_OPTION, customFieldOptionValue);
    jiraCreateUpdateState.setCustomFields(customFields);

    jiraCreateUpdateState.resolveCustomFieldsVars(jiraCreateUpdateState.mapCustomFieldsIdsToNames(createMetaResponse),
        jiraCreateUpdateState.mapCustomFieldsValuesToId(createMetaResponse, Collections.emptyList()));
    String resolvedExpression = jiraCreateUpdateState.getCustomFieldsMap().get(CUSTOMFIELD_OPTION).getFieldValue();

    assertThat(resolvedExpression).isEqualTo(OPTION_ID_2);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldResolveCustomMultiFieldsVars() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    JiraCustomFieldValue customFieldMultiValue = new JiraCustomFieldValue();
    customFieldMultiValue.setFieldType(MULTISELECT);
    customFieldMultiValue.setFieldValue("MultiselectName1");
    customFields.put(MULTI, customFieldMultiValue);
    jiraCreateUpdateState.setCustomFields(customFields);

    jiraCreateUpdateState.resolveCustomFieldsVars(jiraCreateUpdateState.mapCustomFieldsIdsToNames(createMetaResponse),
        jiraCreateUpdateState.mapCustomFieldsValuesToId(createMetaResponse, Collections.emptyList()));
    String resolvedExpression = jiraCreateUpdateState.getCustomFieldsMap().get(MULTI).getFieldValue();

    assertThat(resolvedExpression).isEqualTo(MULTISELECT_ID);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldResolveMultipleCustomMultiFieldsVars() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    JiraCustomFieldValue multipleCustomFieldMultiValue = new JiraCustomFieldValue();
    multipleCustomFieldMultiValue.setFieldType(MULTISELECT);
    multipleCustomFieldMultiValue.setFieldValue("MultiselectName1,  multiselectName2 ");
    customFields.put(MULTI, multipleCustomFieldMultiValue);
    jiraCreateUpdateState.setCustomFields(customFields);

    jiraCreateUpdateState.resolveCustomFieldsVars(jiraCreateUpdateState.mapCustomFieldsIdsToNames(createMetaResponse),
        jiraCreateUpdateState.mapCustomFieldsValuesToId(createMetaResponse, Collections.emptyList()));
    String resolvedExpression = jiraCreateUpdateState.getCustomFieldsMap().get(MULTI).getFieldValue();

    assertThat(resolvedExpression).isEqualTo("multiselect_id_1,multiselect_id_2");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldIgnoreNumberFieldTypeWhenResolvingCustomFieldVars() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    JiraCustomFieldValue numberTypeField = new JiraCustomFieldValue();
    numberTypeField.setFieldType("number");
    numberTypeField.setFieldValue("3");
    customFields.put(CUSTOMFIELD_OPTION_NUMBER, numberTypeField);
    jiraCreateUpdateState.setCustomFields(customFields);

    assertThatCode(
        ()
            -> jiraCreateUpdateState.resolveCustomFieldsVars(
                jiraCreateUpdateState.mapCustomFieldsIdsToNames(createMetaResponse),
                jiraCreateUpdateState.mapCustomFieldsValuesToId(createMetaResponse, Collections.emptyList())))
        .doesNotThrowAnyException();
  }

  @Test(expected = HarnessJiraException.class)
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenProvidedValueIsNotAllowed() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    JiraCustomFieldValue multipleCustomFieldMultiValue = new JiraCustomFieldValue();
    multipleCustomFieldMultiValue.setFieldType(MULTISELECT);
    multipleCustomFieldMultiValue.setFieldValue("Not Allowed");
    customFields.put(MULTI, multipleCustomFieldMultiValue);
    jiraCreateUpdateState.setCustomFields(customFields);

    jiraCreateUpdateState.resolveCustomFieldsVars(jiraCreateUpdateState.mapCustomFieldsIdsToNames(createMetaResponse),
        jiraCreateUpdateState.mapCustomFieldsValuesToId(createMetaResponse, Collections.emptyList()));
  }

  @Test(expected = HarnessJiraException.class)
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailProjectValidation() {
    jiraCreateUpdateState.setProject(UNKNOWN);
    jiraCreateUpdateState.setStatus(TO_DO);
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    jiraCreateUpdateState.setPriority("High");

    jiraCreateUpdateState.validateRequiredFields(createMetaResponse, context);
  }

  @Test(expected = HarnessJiraException.class)
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailStatusValidation() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setStatus(UNKNOWN);
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    jiraCreateUpdateState.setPriority("High");

    jiraCreateUpdateState.validateRequiredFields(createMetaResponse, context);
  }

  @Test(expected = HarnessJiraException.class)
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailIssueTypeValidation() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setStatus(TO_DO);
    jiraCreateUpdateState.setIssueType(UNKNOWN);
    jiraCreateUpdateState.setPriority("High");

    jiraCreateUpdateState.validateRequiredFields(createMetaResponse, context);
  }

  @Test(expected = HarnessJiraException.class)
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailPriorityValidation() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setStatus(TO_DO);
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    jiraCreateUpdateState.setPriority(UNKNOWN);

    jiraCreateUpdateState.validateRequiredFields(createMetaResponse, context);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldPassRequiredFieldsValidation() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setStatus(TO_DO);
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    jiraCreateUpdateState.setPriority("High");

    jiraCreateUpdateState.validateRequiredFields(createMetaResponse, context);
  }

  @Test(expected = HarnessJiraException.class)
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailCustomFieldsNameValidation() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);

    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    JiraCustomFieldValue customFieldValue = new JiraCustomFieldValue();
    customFieldValue.setFieldValue("Some Value");
    customFields.put(UNKNOWN, customFieldValue);
    jiraCreateUpdateState.setCustomFields(customFields);

    jiraCreateUpdateState.inferCustomFieldsTypes(createMetaResponse);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldInferCustomFieldType() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);

    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    JiraCustomFieldValue customFieldValue = new JiraCustomFieldValue();
    customFieldValue.setFieldValue("MultiselectName1");
    customFields.put("Multiselect", customFieldValue);
    jiraCreateUpdateState.setCustomFields(customFields);

    jiraCreateUpdateState.inferCustomFieldsTypes(createMetaResponse);
    assertThat(jiraCreateUpdateState.getCustomFieldsMap().get(MULTI).getFieldType()).isEqualTo(MULTISELECT);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldInferTypeForTimetrackingField() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);

    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    JiraCustomFieldValue customFieldValue = new JiraCustomFieldValue();
    customFieldValue.setFieldValue("1d 4h");
    customFields.put("TimeTracking:OriginalEstimate", customFieldValue);
    jiraCreateUpdateState.setCustomFields(customFields);

    jiraCreateUpdateState.inferCustomFieldsTypes(createMetaResponse);
    assertThat(jiraCreateUpdateState.getCustomFieldsMap().get("TimeTracking:OriginalEstimate").getFieldType())
        .isEqualTo("timetracking");
  }

  @Test(expected = HarnessJiraException.class)
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailProcessingIfProvidedCustomFieldHasDuplicatesByName() {
    jiraCreateUpdateState.setProject("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);

    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    JiraCustomFieldValue customFieldValue = new JiraCustomFieldValue();
    customFields.put("Option Name 3", customFieldValue);
    jiraCreateUpdateState.setCustomFields(customFields);

    jiraCreateUpdateState.inferCustomFieldsTypes(createMetaResponse);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionIfConnectorIsOutOfAccountScope() {
    jiraCreateUpdateState.setJiraConnectorId(JIRA_CONNECTOR_ID);
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);

    when(context.fetchRequiredApp()).thenReturn(Application.Builder.anApplication().name(APP_NAME).build());
    when(context.fetchRequiredEnvironment()).thenReturn(Environment.Builder.anEnvironment().build());
    when(settingsService.getByAccountAndId(ACCOUNT_ID, JIRA_CONNECTOR_ID)).thenReturn(null);
    when(activityService.save(any())).thenReturn(Activity.builder().uuid(ACTIVITY_ID).build());
    assertThatThrownBy(() -> jiraCreateUpdateState.execute(context))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Jira connector doesn't exist");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenInvalidArgumentIsPassedForNumberFieldType() {
    String fieldValue = "wrongFormatForNumberType";
    String fieldName = "fieldName";
    when(context.renderExpression(fieldValue)).thenReturn(fieldValue);

    assertThatThrownBy(() -> jiraCreateUpdateState.parseNumberValue(fieldValue, context, fieldName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid value provided for field: fieldName. fieldName field is of type 'number'.");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnParsedNumericValueConvertedBackToString() {
    String fieldValue = "10";
    String fieldName = "fieldName";
    when(context.renderExpression(fieldValue)).thenReturn(fieldValue);
    String numericValue = jiraCreateUpdateState.parseNumberValue(fieldValue, context, fieldName);
    assertThat(Double.parseDouble(numericValue)).isEqualTo(Double.parseDouble(fieldValue));
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldQueueDelegateTaskAndReturnExecutionResponse() {
    setUpMocksForEntireExecutionFlow();
    when(featureFlagService.isEnabled(eq(FeatureName.ALLOW_USER_TYPE_FIELDS_JIRA), any())).thenReturn(false);
    ExecutionResponse expectedExecutionResponse =
        ExecutionResponse.builder()
            .async(true)
            .correlationIds(Collections.singletonList(ACTIVITY_ID))
            .delegateTaskId(UUID)
            .stateExecutionData(JiraExecutionData.builder().activityId(ACTIVITY_ID).build())
            .build();
    ExecutionResponse executionResponse = jiraCreateUpdateState.execute(context);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("data.taskType", JIRA.name());
    assertThat(delegateTaskArgumentCaptor.getValue().isSelectionLogsTrackingEnabled()).isTrue();
    verify(stateExecutionService).appendDelegateTaskDetails(eq(null), any(DelegateTaskDetails.class));
    assertThat(executionResponse).isEqualTo(expectedExecutionResponse);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldQueueJiraUpdateTaskWithNGClient() {
    setUpMocksForEntireExecutionFlow();
    when(featureFlagService.isEnabled(eq(FeatureName.ALLOW_USER_TYPE_FIELDS_JIRA), any())).thenReturn(true);
    when(context.renderExpression(jiraCreateUpdateState.getIssueId())).thenReturn(ISSUE_ID);
    jiraCreateUpdateState.setJiraAction(JiraAction.UPDATE_TICKET);
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    ExecutionResponse expectedExecutionResponse =
        ExecutionResponse.builder()
            .async(true)
            .correlationIds(Collections.singletonList(ACTIVITY_ID))
            .delegateTaskId(UUID)
            .stateExecutionData(JiraExecutionData.builder().activityId(ACTIVITY_ID).build())
            .build();
    ExecutionResponse executionResponse = jiraCreateUpdateState.execute(context);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("data.taskType", JIRA.name());
    assertThat(delegateTaskArgumentCaptor.getValue().isSelectionLogsTrackingEnabled()).isTrue();
    verify(stateExecutionService).appendDelegateTaskDetails(eq(null), any(DelegateTaskDetails.class));
    assertThat(executionResponse).isEqualTo(expectedExecutionResponse);
    assertThat(jiraCreateUpdateState.getJiraAction() == JiraAction.UPDATE_TICKET_NG);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldQueueJiraCreateTaskWithNGClient() {
    setUpMocksForEntireExecutionFlow();
    when(featureFlagService.isEnabled(eq(FeatureName.ALLOW_USER_TYPE_FIELDS_JIRA), any())).thenReturn(true);
    when(context.renderExpression(jiraCreateUpdateState.getIssueId())).thenReturn(ISSUE_ID);
    jiraCreateUpdateState.setJiraAction(JiraAction.CREATE_TICKET);
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    ExecutionResponse expectedExecutionResponse =
        ExecutionResponse.builder()
            .async(true)
            .correlationIds(Collections.singletonList(ACTIVITY_ID))
            .delegateTaskId(UUID)
            .stateExecutionData(JiraExecutionData.builder().activityId(ACTIVITY_ID).build())
            .build();
    ExecutionResponse executionResponse = jiraCreateUpdateState.execute(context);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("data.taskType", JIRA.name());
    assertThat(delegateTaskArgumentCaptor.getValue().isSelectionLogsTrackingEnabled()).isTrue();
    verify(stateExecutionService).appendDelegateTaskDetails(eq(null), any(DelegateTaskDetails.class));
    assertThat(executionResponse).isEqualTo(expectedExecutionResponse);
    assertThat(jiraCreateUpdateState.getJiraAction() == JiraAction.CREATE_TICKET_NG);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponse() {
    JiraExecutionData jiraExecutionData = JiraExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).build();
    Map<String, ResponseData> response = Collections.singletonMap(ACTIVITY_ID, jiraExecutionData);
    ExecutionResponse expectedExecutionResponse = ExecutionResponse.builder()
                                                      .stateExecutionData(jiraExecutionData)
                                                      .executionStatus(jiraExecutionData.getExecutionStatus())
                                                      .errorMessage(jiraExecutionData.getErrorMessage())
                                                      .build();
    ExecutionResponse executionResponse = jiraCreateUpdateState.handleAsyncResponse(context, response);
    assertThat(executionResponse).isEqualTo(expectedExecutionResponse);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailExecutionWhenIssueIdIsNotRendered() {
    setUpMocksForEntireExecutionFlow();
    when(featureFlagService.isEnabled(eq(FeatureName.ALLOW_USER_TYPE_FIELDS_JIRA), any())).thenReturn(false);
    jiraCreateUpdateState.setJiraAction(JiraAction.UPDATE_TICKET);
    jiraCreateUpdateState.setIssueId(StringUtils.EMPTY);
    when(context.renderExpression(StringUtils.EMPTY)).thenReturn(StringUtils.EMPTY);
    ExecutionResponse expectedExecutionResponse =
        ExecutionResponse.builder()
            .executionStatus(FAILED)
            .errorMessage("No valid issueId after parsing: " + jiraCreateUpdateState.getIssueId())
            .stateExecutionData(JiraExecutionData.builder().activityId(ACTIVITY_ID).build())
            .build();
    ExecutionResponse executionResponse = jiraCreateUpdateState.execute(context);
    assertThat(executionResponse).isEqualTo(expectedExecutionResponse);
  }

  private void setUpMocksForEntireExecutionFlow() {
    jiraCreateUpdateState.setProject("PN");
    when(context.renderExpression(jiraCreateUpdateState.getProject())).thenReturn("PN");
    jiraCreateUpdateState.setIssueType(ISSUE_TYPE);
    when(context.renderExpression(jiraCreateUpdateState.getIssueType())).thenReturn(ISSUE_TYPE);
    jiraCreateUpdateState.setStatus(TO_DO);
    when(context.renderExpression(jiraCreateUpdateState.getStatus())).thenReturn(TO_DO);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    JiraCustomFieldValue customFieldOptionValue = new JiraCustomFieldValue();
    customFieldOptionValue.setFieldType("option");
    customFieldOptionValue.setFieldValue("OptionValue2");
    customFields.put(CUSTOMFIELD_OPTION, customFieldOptionValue);
    when(context.renderExpression(CUSTOMFIELD_OPTION)).thenReturn(CUSTOMFIELD_OPTION);
    when(context.renderExpression("OptionValue2")).thenReturn("OptionValue2");
    jiraCreateUpdateState.setCustomFields(customFields);
    jiraCreateUpdateState.setJiraConnectorId(JIRA_CONNECTOR_ID);
    Application application = Application.Builder.anApplication().accountId(ACCOUNT_ID).name(APP_NAME).build();
    when(context.fetchRequiredApp()).thenReturn(application);
    when(context.getApp()).thenReturn(application);
    when(context.fetchRequiredEnvironment()).thenReturn(Environment.Builder.anEnvironment().build());
    when(settingsService.getByAccountAndId(ACCOUNT_ID, JIRA_CONNECTOR_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withValue(JiraConfig.builder().password(PASSWORD).build())
                        .build());
    when(activityService.save(any())).thenReturn(Activity.builder().uuid(ACTIVITY_ID).build());
    when(jiraHelperService.getCreateMetadata(
             JIRA_CONNECTOR_ID, null, jiraCreateUpdateState.getProject(), ACCOUNT_ID, APP_ID, 0, ISSUE_TYPE))
        .thenReturn(createMetaResponse);
    when(delegateService.queueTaskV2(any())).thenReturn(UUID);
  }
}
