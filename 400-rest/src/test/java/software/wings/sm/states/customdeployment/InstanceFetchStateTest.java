/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.customdeployment;

import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.SweepingOutputInstance.Scope.WORKFLOW;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.sm.states.customdeployment.InstanceFetchState.OUTPUT_PATH_KEY;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskData.TaskDataKeys;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.deployment.InstanceDetails;
import io.harness.expression.ExpressionEvaluator;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.HostElement;
import software.wings.api.InfraMappingElement;
import software.wings.api.InfraMappingElement.Custom;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ServiceTemplateElement;
import software.wings.api.customdeployment.InstanceFetchStateExecutionData;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.api.shellscript.provision.ShellScriptProvisionExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.CustomInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.TaskType;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.shellscript.provisioner.ShellScriptProvisionParameters;
import software.wings.beans.template.deploymenttype.CustomDeploymentTypeTemplate;
import software.wings.service.impl.ActivityHelperService;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.customdeploymentng.InstanceMapperUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import dev.morphia.Key;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceFetchStateTest extends WingsBaseTest {
  @Mock private ExecutionContext context;
  @Mock private ActivityHelperService activityHelperService;
  @Mock private CustomDeploymentTypeService customDeploymentTypeService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private DelegateService delegateService;
  @Mock private ExpressionEvaluator expressionEvaluator;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceTemplateService mockServiceTemplateService;
  @Mock private ServiceTemplateHelper serviceTemplateHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @InjectMocks private InstanceFetchState state = new InstanceFetchState("Fetch Instances");

  private static String resourcePath = "400-rest/src/test/resources/software/wings/customdeployment";

  @Before
  public void setUp() throws Exception {
    String timeoutExpr = "${workflow.variables.timeout}";
    state.setStateTimeoutInMinutes(timeoutExpr);

    final CustomInfrastructureMapping infraMapping = CustomInfrastructureMapping.builder().build();
    infraMapping.setCustomDeploymentTemplateId(TEMPLATE_ID);
    infraMapping.setDeploymentTypeTemplateVersion("1");
    infraMapping.setServiceId(SERVICE_ID);

    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SERVICE_TEMPLATE_ID);
    when(environmentService.get(anyString(), anyString(), anyBoolean()))
        .thenReturn(anEnvironment().environmentType(PROD).build());

    Key<ServiceTemplate> serviceTemplateKey = new Key<>(ServiceTemplate.class, "collection", "id");
    doReturn(singletonList(serviceTemplateKey))
        .when(mockServiceTemplateService)
        .getTemplateRefKeysByService(any(), any(), any());
    final PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    doReturn(phaseElement).when(context).getContextElement(any(), any());
    WorkflowStandardParams workflowStandardParams =
        aWorkflowStandardParams().withAppId(APP_ID).withEnvId(ENV_ID).build();
    doReturn(workflowStandardParams).when(context).getContextElement(ContextElementType.STANDARD);

    on(workflowStandardParamsExtensionService).set("environmentService", environmentService);
    on(state).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);

    doReturn(ACCOUNT_ID).when(context).getAccountId();
    doReturn(APP_ID).when(context).getAppId();
    doReturn(INFRA_MAPPING_ID).when(context).fetchInfraMappingId();
    doReturn(
        InfraMappingElement.builder().custom(Custom.builder().vars(ImmutableMap.of("key", "value")).build()).build())
        .when(context)
        .fetchInfraMappingElement();
    doReturn(infraMapping).when(infrastructureMappingService).get(APP_ID, INFRA_MAPPING_ID);
    doReturn(CustomDeploymentTypeTemplate.builder()
                 .fetchInstanceScript("echo Hello")
                 .hostObjectArrayPath("items")
                 .hostAttributes(ImmutableMap.of("key", "value"))
                 .build())
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplate(ACCOUNT_ID, TEMPLATE_ID, "1");
    doReturn(Activity.builder().uuid(ACTIVITY_ID).build())
        .when(activityHelperService)
        .createAndSaveActivity(eq(context), eq(Activity.Type.Command), anyString(),
            eq(CommandUnitType.CUSTOM_DEPLOYMENT_FETCH_INSTANCES.getName()), anyList());
    doReturn("some-string").when(context).appendStateExecutionId(anyString());
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(WORKFLOW);
    doAnswer(invocation -> invocation.getArgument(0, String.class)).when(context).renderExpression(anyString());
    doAnswer(invocation -> invocation.getArgument(0, String.class))
        .when(context)
        .renderExpression(anyString(), any(StateExecutionContext.class));
    doReturn("5").when(context).renderExpression(timeoutExpr);
    doAnswer(invocation -> invocation.getArgument(0, String.class))
        .when(expressionEvaluator)
        .substitute(anyString(), anyMap());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void execute() {
    state.setTags(Arrays.asList("tag1", null, " tag2 ", "tag1 ", "", null));
    final ExecutionResponse response = state.execute(context);

    final ShellScriptProvisionParameters expectedTaskParams =
        ShellScriptProvisionParameters.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .scriptBody("echo Hello")
            .textVariables(ImmutableMap.of("key", "value"))
            .commandUnit(CommandUnitType.CUSTOM_DEPLOYMENT_FETCH_INSTANCES.getName())
            .outputPathKey(OUTPUT_PATH_KEY)
            .workflowExecutionId(context.getWorkflowExecutionId())
            .build();

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    final DelegateTask expected =
        DelegateTask.builder()
            .accountId(ACCOUNT_ID)
            .description("Fetch Instances")
            .waitId(ACTIVITY_ID)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD, SERVICE_TEMPLATE_ID)
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, PROD.name())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, INFRA_MAPPING_ID)
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, SERVICE_ID)
            .selectionLogsTrackingEnabled(true)
            .tags(Arrays.asList("tag1", "tag2"))
            .data(TaskData.builder()
                      .async(true)
                      .parameters(new Object[] {expectedTaskParams})
                      .taskType(TaskType.SHELL_SCRIPT_PROVISION_TASK.name())
                      .timeout(5 * 60 * 1000)
                      .build())
            .build();
    verify(delegateService).queueTaskV2(captor.capture());
    verify(expressionEvaluator, times(1)).substitute(anyString(), anyMap());

    final DelegateTask task = captor.getValue();
    assertThat(task).isEqualToIgnoringGivenFields(
        expected, DelegateTaskKeys.uuid, DelegateTaskKeys.data, DelegateTaskKeys.validUntil);
    assertThat(task.getData()).isEqualToIgnoringGivenFields(expected.getData(), TaskDataKeys.expressionFunctorToken);
    assertThat(task.getData().getExpressionFunctorToken()).isNotEqualTo(0);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponse() {
    doReturn(InstanceFetchStateExecutionData.builder()
                 .activityId(ACTIVITY_ID)
                 .hostObjectArrayPath("Instances")
                 .hostAttributes(ImmutableMap.of("hostname", "ip"))
                 .build())
        .when(context)
        .getStateExecutionData();
    Map<String, ResponseData> response = ImmutableMap.of(ACTIVITY_ID,
        ShellScriptProvisionExecutionData.builder()
            .activityId(ACTIVITY_ID)
            .output("{\"Instances\": [{\"ip\":\"1.1\"},{\"ip\":\"2.2\"}]}")
            .executionStatus(SUCCESS)
            .build());

    final ExecutionResponse executionResponse = state.handleAsyncResponse(context, response);

    verify(activityHelperService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, SUCCESS);

    assertThat(executionResponse.getContextElements()).hasSize(1);
    assertThat(executionResponse.getNotifyElements()).hasSize(1);
    InstanceElementListParam elementListParam =
        (InstanceElementListParam) executionResponse.getContextElements().get(0);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(elementListParam.getInstanceElements()).hasSize(2);
    assertThat(
        elementListParam.getInstanceElements().stream().map(InstanceElement::getHostName).collect(Collectors.toList()))
        .containsExactly("1.1", "2.2");
    assertThat(
        elementListParam.getInstanceElements().stream().map(InstanceElement::getName).collect(Collectors.toList()))
        .containsExactly("1.1", "2.2");
    assertThat(executionResponse.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponseWithInvalidJson() {
    doReturn(InstanceFetchStateExecutionData.builder()
                 .hostObjectArrayPath("Instances")
                 .hostAttributes(ImmutableMap.of("hostname", "ip"))
                 .build())
        .when(context)
        .getStateExecutionData();
    Map<String, ResponseData> response = ImmutableMap.of(ACTIVITY_ID,
        ShellScriptProvisionExecutionData.builder()
            .activityId(ACTIVITY_ID)
            .output("{\"Instances\": [\"ip\":\"1.1\"},{\"ip\":\"2.2\"}]}")
            .executionStatus(SUCCESS)
            .build());

    final ExecutionResponse executionResponse = state.handleAsyncResponse(context, response);

    assertThat(executionResponse.getContextElements()).isEmpty();
    assertThat(executionResponse.getNotifyElements()).isEmpty();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(executionResponse.getErrorMessage())
        .contains(
            "JsonParseException: Unexpected character (':' (code 58)): was expecting comma to separate Array entries");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponseFailure() {
    doReturn(InstanceFetchStateExecutionData.builder()
                 .activityId(ACTIVITY_ID)
                 .hostObjectArrayPath("Instances")
                 .hostAttributes(ImmutableMap.of("hostname", "ip"))
                 .build())
        .when(context)
        .getStateExecutionData();
    Map<String, ResponseData> response = ImmutableMap.of(ACTIVITY_ID,
        ShellScriptProvisionExecutionData.builder()
            .activityId(ACTIVITY_ID)
            .errorMsg("invalid script")
            .executionStatus(FAILED)
            .build());

    final ExecutionResponse executionResponse = state.handleAsyncResponse(context, response);

    verify(activityHelperService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, FAILED);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("invalid script");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testMapJsonToInstanceElements() throws IOException {
    InstanceFetchStateExecutionData executionData = InstanceFetchStateExecutionData.builder()
                                                        .hostObjectArrayPath("hosts.value")
                                                        .hostAttributes(ImmutableMap.of("hostname", "access_ip_v4"))
                                                        .build();
    String output = readFile("Instances.json");

    final List<InstanceElement> instanceElements =
        InstanceMapperUtils.mapJsonToInstanceElements(executionData.getHostAttributes(),
            executionData.getHostObjectArrayPath(), output, InstanceFetchState.instanceElementMapper);

    assertThat(instanceElements).hasSize(3);
    assertThat(instanceElements.stream().map(InstanceElement::getHostName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("10.244.12.15", "10.244.12.13", "10.244.12.17");
  }

  /*
  Test with output of kubectl get pod -l app=nginx -o json
   */
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testMapJsonToInstanceElementsNestedAttributes() throws IOException {
    InstanceFetchStateExecutionData executionData =
        InstanceFetchStateExecutionData.builder()
            .hostObjectArrayPath("items")
            .hostAttributes(ImmutableMap.of("hostname", "metadata.name", "uuid", "metadata.uid"))
            .build();
    String output = readFile("NestedAttributesInstance.json");

    final List<InstanceElement> instanceElements =
        InstanceMapperUtils.mapJsonToInstanceElements(executionData.getHostAttributes(),
            executionData.getHostObjectArrayPath(), output, InstanceFetchState.instanceElementMapper);

    assertThat(instanceElements).hasSize(5);
    assertThat(instanceElements.stream().map(InstanceElement::getHostName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            "cd-statefulset-0", "cd-statefulset-1", "k8sv2-statefulset-0", "statefulset-test-0", "statefulset-test-1");
    assertThat(instanceElements.stream()
                   .map(InstanceElement::getHost)
                   .map(HostElement::getProperties)
                   .map(propertiesMap -> propertiesMap.get("uuid")))
        .containsExactlyInAnyOrder("1a86e740-32b3-467d-a3fa-e6c4af8bab7a", "2a3e8a66-439a-4a82-ac9a-e2e53e577266",
            "3af21535-c9fd-4b0d-98a5-06a0cb692803", "3c800ea6-7f46-4281-a351-1c436858b563",
            "cdff119a-790d-4f32-95eb-56a41c363856");
  }

  /*
  Test with some empty hostnames (Weblogic Deployments)
   */
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testMapJsonToInstanceElementsWeblogicSample() throws IOException {
    InstanceFetchStateExecutionData executionData = InstanceFetchStateExecutionData.builder()
                                                        .hostObjectArrayPath("items")
                                                        .hostAttributes(ImmutableMap.of("hostname", "listenAddress"))
                                                        .build();
    String output = readFile("Weblogic.json");
    final List<InstanceElement> instanceElements =
        InstanceMapperUtils.mapJsonToInstanceElements(executionData.getHostAttributes(),
            executionData.getHostObjectArrayPath(), output, InstanceFetchState.instanceElementMapper);

    assertThat(instanceElements).hasSize(1);
    assertThat(instanceElements.stream().map(InstanceElement::getHostName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("172.17.0.3");
  }

  private String readFile(String fileName) throws IOException {
    File file = null;
    file = new File(resourcePath + PATH_DELIMITER + fileName);

    assertThat(file).isNotNull();
    return FileUtils.readFileToString(file, "UTF-8");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMapJsonToInstanceElementsWithEmptyOrNullProperties() throws IOException {
    InstanceFetchStateExecutionData executionData = InstanceFetchStateExecutionData.builder()
                                                        .hostObjectArrayPath("hosts.value")
                                                        .hostAttributes(ImmutableMap.of("hostname", "access_ip_v4",
                                                            "ipv6", "access_ip_v6", "adminPass", "admin_pass"))
                                                        .build();

    String output = readFile("Instances.json");

    final List<InstanceElement> instanceElements =
        InstanceMapperUtils.mapJsonToInstanceElements(executionData.getHostAttributes(),
            executionData.getHostObjectArrayPath(), output, InstanceFetchState.instanceElementMapper);

    assertThat(instanceElements.get(0).getHost().getProperties()).hasSize(3);
    assertThat(instanceElements.get(0).getHost().getProperties().keySet())
        .containsExactlyInAnyOrder("hostname", "ipv6", "adminPass");
    assertThat(instanceElements.stream()
                   .map(InstanceElement::getHost)
                   .map(HostElement::getProperties)
                   .map(v -> v.get("adminPass"))
                   .collect(Collectors.toList()))
        .containsOnly("", null);
    assertThat(instanceElements.stream()
                   .map(InstanceElement::getHost)
                   .map(HostElement::getProperties)
                   .map(v -> v.get("ipv6"))
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(StringUtils.EMPTY, StringUtils.EMPTY, "10.20.30.40");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void validateEmptyHostObjectArrayPath() {
    // valid case
    doReturn(CustomDeploymentTypeTemplate.builder()
                 .fetchInstanceScript("hello")
                 .hostObjectArrayPath("items")
                 .hostAttributes(ImmutableMap.of("key", "value"))
                 .build())
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplate(anyString(), anyString(), anyString());

    state.execute(context);

    verify(delegateService).queueTaskV2(any(DelegateTask.class));

    // empty host object array path
    doReturn(CustomDeploymentTypeTemplate.builder()
                 .fetchInstanceScript("hello")
                 .hostObjectArrayPath("")
                 .hostAttributes(ImmutableMap.of("key", "value"))
                 .build())
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplate(anyString(), anyString(), anyString());

    ExecutionResponse response = state.execute(context);

    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("Prerequisites not met.\n"
            + "Host Object Array Path Cannot Be Empty");
    // null case
    doReturn(CustomDeploymentTypeTemplate.builder()
                 .fetchInstanceScript("hello")
                 .hostObjectArrayPath(null)
                 .hostAttributes(ImmutableMap.of("key", "value"))
                 .build())
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplate(anyString(), anyString(), anyString());

    response = state.execute(context);

    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("Prerequisites not met.\n"
            + "Host Object Array Path Cannot Be Empty");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void validateEmptyScript() {
    // valid case
    doReturn(CustomDeploymentTypeTemplate.builder()
                 .fetchInstanceScript("echo hello")
                 .hostObjectArrayPath("items")
                 .hostAttributes(ImmutableMap.of("key", "value"))
                 .build())
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplate(anyString(), anyString(), anyString());

    state.execute(context);

    verify(delegateService).queueTaskV2(any(DelegateTask.class));

    doReturn(CustomDeploymentTypeTemplate.builder()
                 .fetchInstanceScript("")
                 .hostObjectArrayPath("items")
                 .hostAttributes(ImmutableMap.of("key", "value"))
                 .build())
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplate(anyString(), anyString(), anyString());

    ExecutionResponse response = state.execute(context);

    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("Prerequisites not met.\n"
            + "Fetch Instance Command Script Cannot Be Empty");

    // null case
    doReturn(CustomDeploymentTypeTemplate.builder()
                 .fetchInstanceScript(null)
                 .hostObjectArrayPath("items")
                 .hostAttributes(ImmutableMap.of("key", "value"))
                 .build())
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplate(anyString(), anyString(), anyString());

    response = state.execute(context);

    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("Prerequisites not met.\n"
            + "Fetch Instance Command Script Cannot Be Empty");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void validateEmptyFieldValuesForHostAttributes() {
    doReturn(CustomDeploymentTypeTemplate.builder()
                 .fetchInstanceScript("echo hello")
                 .hostObjectArrayPath("items")
                 .hostAttributes(ImmutableMap.of("k", "v"))
                 .build())
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplate(anyString(), anyString(), anyString());

    state.execute(context);

    verify(delegateService, times(1)).queueTaskV2(any(DelegateTask.class));

    Map<String, String> attributes = new HashMap<>();
    attributes.put("key", "value");
    attributes.put("key-1", "");
    attributes.put("key-2", "value");
    attributes.put("key-3", null);
    doReturn(CustomDeploymentTypeTemplate.builder()
                 .fetchInstanceScript("")
                 .hostObjectArrayPath("items")
                 .hostAttributes(attributes)
                 .build())
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplate(anyString(), anyString(), anyString());

    ExecutionResponse response = state.execute(context);

    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("Prerequisites not met.\n"
            + "Fetch Instance Command Script Cannot Be Empty");

    // null case
    doReturn(CustomDeploymentTypeTemplate.builder()
                 .fetchInstanceScript(null)
                 .hostObjectArrayPath("items")
                 .hostAttributes(null)
                 .build())
        .when(customDeploymentTypeService)
        .fetchDeploymentTemplate(anyString(), anyString(), anyString());

    response = state.execute(context);

    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("Prerequisites not met.\n"
            + "Fetch Instance Command Script Cannot Be Empty");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMapJsonToInstanceElementsWithPathNotFoundinJsonFail() throws IOException {
    InstanceFetchStateExecutionData executionData =
        InstanceFetchStateExecutionData.builder()
            .hostObjectArrayPath("hosts.value")
            .hostAttributes(ImmutableMap.of("hostname", "access_ip_v4", "name", "name"))
            .build();

    String output = readFile("Instances.json");

    assertThatThrownBy(
        ()
            -> InstanceMapperUtils.mapJsonToInstanceElements(executionData.getHostAttributes(),
                executionData.getHostObjectArrayPath(), output, InstanceFetchState.instanceElementMapper))
        .hasMessage("No results for path: $['name']");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSetServiceElement() {
    List<InstanceElement> instanceElements = new ArrayList<>();
    instanceElements.add(anInstanceElement().uuid("uuid1").hostName("1.1").build());
    instanceElements.add(anInstanceElement().uuid("uuid2").hostName("2.2").build());
    ServiceElement serviceElement = ServiceElement.builder().build();
    state.setServiceElement(instanceElements, serviceElement, "serviceTemplateId");
    List<ServiceTemplateElement> serviceTemplateElements =
        instanceElements.stream()
            .map(instanceElement -> instanceElement.getServiceTemplateElement())
            .collect(Collectors.toList());
    assertThat(serviceTemplateElements).doesNotContainNull();
    assertThat(serviceTemplateElements.stream().map(element -> element.getUuid()).collect(Collectors.toList()))
        .contains("serviceTemplateId", "serviceTemplateId");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSetServiceElementId() {
    List<InstanceDetails> instanceDetails = new ArrayList<>();
    instanceDetails.add(InstanceDetails.builder().hostName("host1").build());
    instanceDetails.add(InstanceDetails.builder().hostName("host2").build());
    state.setServiceTemplateId(instanceDetails, "serviceTemplateId");
    assertThat(instanceDetails.stream().map(details -> details.getServiceTemplateId()).collect(Collectors.toList()))
        .doesNotContainNull();
    assertThat(instanceDetails.stream().map(details -> details.getServiceTemplateId()).collect(Collectors.toList()))
        .contains("serviceTemplateId", "serviceTemplateId");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSaveInstanceInfoToSweepingOutputDontSkipVerification() {
    on(state).set("sweepingOutputService", sweepingOutputService);
    state.saveInstanceInfoToSweepingOutput(context, asList(anInstanceElement().dockerId("dockerId").build()),
        asList(InstanceDetails.builder().hostName("hostName").newInstance(true).build(),
            InstanceDetails.builder().hostName("hostName").newInstance(false).build()));

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(1)).save(argumentCaptor.capture());

    InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) argumentCaptor.getValue().getValue();
    assertThat(instanceInfoVariables.isSkipVerification()).isEqualTo(false);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testsaveInstanceInfoToSweepingOutputSkipVerification() {
    on(state).set("sweepingOutputService", sweepingOutputService);
    state.saveInstanceInfoToSweepingOutput(context, asList(anInstanceElement().dockerId("dockerId").build()),
        asList(InstanceDetails.builder().hostName("hostName").newInstance(false).build(),
            InstanceDetails.builder().hostName("hostName").newInstance(false).build()));

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(1)).save(argumentCaptor.capture());

    InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) argumentCaptor.getValue().getValue();
    assertThat(instanceInfoVariables.isSkipVerification()).isEqualTo(true);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldDetectInvalidInfraDef() {
    DirectKubernetesInfrastructureMapping infraMapping = DirectKubernetesInfrastructureMapping.builder().build();
    infraMapping.setDisplayName("infraDef DisplayName");
    doReturn(infraMapping).when(infrastructureMappingService).get(APP_ID, INFRA_MAPPING_ID);

    final ExecutionResponse response = state.execute(context);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("Prerequisites not met.\n"
            + "Infrastructure definition infraDef DisplayName is not supported on fetch instance");
  }
}
