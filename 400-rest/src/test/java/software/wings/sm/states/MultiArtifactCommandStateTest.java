/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.rule.OwnerRule.AADITI;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.api.ServiceTemplateElement.Builder.aServiceTemplateElement;
import static software.wings.api.SimpleWorkflowParam.Builder.aSimpleWorkflowParam;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping;
import static software.wings.beans.ServiceInstance.Builder.aServiceInstance;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.StringValue.Builder.aStringValue;
import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ExecCommandUnit.Builder.anExecCommandUnit;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.COMMAND_UNIT_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.HOST_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_INSTANCE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.shell.ScriptType;

import software.wings.api.DeploymentType;
import software.wings.api.SimpleWorkflowParam;
import software.wings.beans.Activity;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.Service;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.command.AbstractCommandUnit;
import software.wings.beans.command.Command;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.template.TemplateUtils;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.service.impl.ActivityHelperService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class MultiArtifactCommandStateTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private ExecutionContextImpl executionContext;
  @Mock private WorkflowStandardParams workflowStandardParams;
  @Mock private ServiceInstanceService serviceInstanceService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private HostService hostService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private SettingsService settingsService;
  @Mock private SecretManager secretManager;
  @Mock private AppService appService;
  @Mock private TemplateUtils templateUtils;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactStream artifactStream1;
  @Mock private ArtifactStream artifactStream2;
  @Mock private ArtifactService artifactService;
  @Mock private ActivityHelperService activityHelperService;
  @Mock private AwsCommandHelper mockAwsCommandHelper;
  @Mock private DelegateService delegateService;
  @Mock private StateExecutionService stateExecutionService;

  @InjectMocks private CommandState commandState = new CommandState(COMMAND_NAME, COMMAND_NAME);

  public static final String RUNTIME_PATH = "$HOME/${app.name}/${service.name}/${serviceTemplate.name}/runtime";
  /**
   * The constant BACKUP_PATH.
   */
  public static final String BACKUP_PATH =
      "$HOME/${app.name}/${service.name}/${serviceTemplate.name}/backup/${timestampId}";
  /**
   * The constant STAGING_PATH.
   */
  public static final String STAGING_PATH =
      "$HOME/${app.name}/${service.name}/${serviceTemplate.name}/staging/${timestampId}";
  /**
   * The constant WINDOWS_RUNTIME_PATH.
   */
  public static final String WINDOWS_RUNTIME_PATH_TEST =
      "%USERPROFILE%/${app.name}/${service.name}/${env.name}/runtime/test";
  private static final String WINDOWS_RUNTIME_PATH = "WINDOWS_RUNTIME_PATH";

  private static final Service SERVICE = Service.builder().uuid(SERVICE_ID).build();
  private static final ServiceTemplate SERVICE_TEMPLATE =
      aServiceTemplate().withUuid(TEMPLATE_ID).withServiceId(SERVICE.getUuid()).build();
  private static final Host HOST =
      aHost().withUuid(HOST_ID).withHostName(HOST_NAME).withHostConnAttr("1").withBastionConnAttr("1").build();
  private static final ServiceInstance SERVICE_INSTANCE = aServiceInstance()
                                                              .withUuid(SERVICE_INSTANCE_ID)
                                                              .withAppId(APP_ID)
                                                              .withEnvId(ENV_ID)
                                                              .withServiceTemplate(SERVICE_TEMPLATE)
                                                              .withHost(HOST)
                                                              .build();
  private static final SimpleWorkflowParam SIMPLE_WORKFLOW_PARAM = aSimpleWorkflowParam().build();
  private static final AbstractCommandUnit commandUnit1 = anExecCommandUnit()
                                                              .withName(COMMAND_UNIT_NAME)
                                                              .withScriptType(ScriptType.BASH)
                                                              .withCommandPath("/tmp")
                                                              .withCommandString("echo ${artifacts.artifact1.buildNo}")
                                                              .build();
  private static final AbstractCommandUnit commandUnit2 = anExecCommandUnit()
                                                              .withName(COMMAND_UNIT_NAME + "-2")
                                                              .withScriptType(ScriptType.BASH)
                                                              .withCommandPath("/tmp")
                                                              .withCommandString("echo ${artifacts.artifact2.buildNo}")
                                                              .build();
  private static Command command =
      aCommand().withName(COMMAND_NAME).withArtifactNeeded(true).addCommandUnits(commandUnit1, commandUnit2).build();
  private SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().build();

  private static final Activity ACTIVITY_WITH_ID = Activity.builder()
                                                       .applicationName(APP_NAME)
                                                       .environmentId(SERVICE_INSTANCE.getEnvId())
                                                       .serviceTemplateId(SERVICE_INSTANCE.getServiceTemplateId())
                                                       .serviceTemplateName(null)
                                                       .serviceId(SERVICE_ID)
                                                       .serviceName(SERVICE_NAME)
                                                       .commandName(command.getName())
                                                       .commandType(command.getCommandUnitType().name())
                                                       .hostName(HOST_NAME)
                                                       .serviceInstanceId(SERVICE_INSTANCE_ID)
                                                       .uuid(ACTIVITY_ID)
                                                       .build();

  private static final String ARTIFACT_ID_1 = "ARTIFACT_ID_1";
  private static final String ARTIFACT_ID_2 = "ARTIFACT_ID_2";
  private static final String ARTIFACT_STREAM_ID_1 = "ARTIFACT_STREAM_ID_1";
  private static final String ARTIFACT_STREAM_ID_2 = "ARTIFACT_STREAM_ID_2";
  private static final String SETTING_ID_1 = "SETTING_ID_1";
  private static final String SETTING_ID_2 = "SETTING_ID_2";
  private static final String GLOBAL_APP_ID = "GLOBAL_APP_ID";

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testExecuteCommandCreatedInServiceWithMultipleArtifacts() {
    commandState.setHost(HOST_NAME);
    commandState.setSshKeyRef("ssh_key_ref");
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setValue(HostConnectionAttributes.Builder.aHostConnectionAttributes().build());
    settingAttribute.setAppId(GLOBAL_APP_ID);
    when(settingsService.get("ssh_key_ref")).thenReturn(settingAttribute);
    when(serviceResourceService.getWithDetails(APP_ID, null)).thenReturn(SERVICE);

    on(commandState).set("templateUtils", templateUtils);
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(workflowStandardParams.getAppId()).thenReturn(APP_ID);
    when(workflowStandardParams.getEnvId()).thenReturn(ENV_ID);
    when(executionContext.getContextElement(ContextElementType.INSTANCE))
        .thenReturn(anInstanceElement()
                        .uuid(SERVICE_INSTANCE_ID)
                        .serviceTemplateElement(aServiceTemplateElement().withUuid(TEMPLATE_ID).build())
                        .build());
    when(serviceInstanceService.get(APP_ID, ENV_ID, SERVICE_INSTANCE_ID)).thenReturn(SERVICE_INSTANCE);
    when(executionContext.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(aPhysicalInfrastructureMapping()
                        .withAppId(APP_ID)
                        .withUuid(INFRA_MAPPING_ID)
                        .withDeploymentType("SSH")
                        .build());
    when(executionContext.getContextElementList(ContextElementType.PARAM))
        .thenReturn(singletonList(SIMPLE_WORKFLOW_PARAM));
    ServiceTemplate serviceTemplate = aServiceTemplate().withUuid(TEMPLATE_ID).withServiceId(SERVICE.getUuid()).build();
    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(serviceTemplate);
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(SERVICE);
    when(serviceResourceService.getCommandByName(APP_ID, SERVICE_ID, ENV_ID, COMMAND_NAME))
        .thenReturn(aServiceCommand().withTargetToAllEnv(true).withCommand(command).build());
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, ACCOUNT_ID)).thenReturn(true);
    Map<String, Artifact> map = new HashMap<>();
    Artifact artifact1 =
        anArtifact().withUuid(ARTIFACT_ID_1).withAppId(APP_ID).withArtifactStreamId(ARTIFACT_STREAM_ID_1).build();
    Artifact artifact2 =
        anArtifact().withUuid(ARTIFACT_ID_2).withAppId(APP_ID).withArtifactStreamId(ARTIFACT_STREAM_ID_2).build();

    map.put("artifact1", artifact1);
    map.put("artifact2", artifact2);
    when(executionContext.getArtifactsForService(SERVICE_ID)).thenReturn(map);
    when(executionContext.getServiceVariables()).thenReturn(emptyMap());
    when(executionContext.getSafeDisplayServiceVariables()).thenReturn(emptyMap());
    when(executionContext.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArguments()[0]);
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.SSH);
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, ENV_ID, CommandState.RUNTIME_PATH))
        .thenReturn(aSettingAttribute().withValue(aStringValue().withValue(RUNTIME_PATH).build()).build());
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, ENV_ID, CommandState.BACKUP_PATH))
        .thenReturn(aSettingAttribute().withValue(aStringValue().withValue(BACKUP_PATH).build()).build());
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, ENV_ID, CommandState.STAGING_PATH))
        .thenReturn(aSettingAttribute().withValue(aStringValue().withValue(STAGING_PATH).build()).build());
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, ENV_ID, WINDOWS_RUNTIME_PATH))
        .thenReturn(aSettingAttribute().withValue(aStringValue().withValue(WINDOWS_RUNTIME_PATH_TEST).build()).build());
    when(settingsService.get(HOST.getHostConnAttr()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withValue(HostConnectionAttributes.Builder.aHostConnectionAttributes().build())
                        .build());
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(Collections.emptyList());
    when(appService.get(APP_ID)).thenReturn(anApplication().uuid(APP_ID).accountId(ACCOUNT_ID).name(APP_NAME).build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_1)).thenReturn(artifactStream1);
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder().metadataOnly(false).build();
    when(artifactStream1.fetchArtifactStreamAttributes(featureFlagService)).thenReturn(artifactStreamAttributes);
    when(artifactStream1.getSettingId()).thenReturn(SETTING_ID_1);
    when(artifactStream1.getUuid()).thenReturn(ARTIFACT_STREAM_ID_1);
    when(settingsService.get(SETTING_ID_1)).thenReturn(settingAttribute);

    when(artifactStreamService.get(ARTIFACT_STREAM_ID_2)).thenReturn(artifactStream2);
    when(artifactStream2.fetchArtifactStreamAttributes(featureFlagService)).thenReturn(artifactStreamAttributes);
    when(artifactStream2.getSettingId()).thenReturn(SETTING_ID_2);
    when(artifactStream2.getUuid()).thenReturn(ARTIFACT_STREAM_ID_2);
    when(settingsService.get(SETTING_ID_2)).thenReturn(settingAttribute);

    when(activityHelperService.createAndSaveActivity(any(), any(), any(), any(), any(), any()))
        .thenReturn(ACTIVITY_WITH_ID);
    doReturn(null).when(mockAwsCommandHelper).getAwsConfigTagsFromContext(any());
    doReturn("TASKID").when(delegateService).queueTask(any());
    ExecutionResponse executionResponse = commandState.execute(executionContext);
    assertThat(executionResponse).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);
    verify(serviceResourceService).getCommandByName(APP_ID, SERVICE_ID, ENV_ID, COMMAND_NAME);
    verify(serviceResourceService).getFlattenCommandUnitList(APP_ID, SERVICE_ID, ENV_ID, COMMAND_NAME);
    verify(executionContext, times(2)).getContextElement(ContextElementType.STANDARD);
    verify(executionContext, times(1)).getContextElement(ContextElementType.INSTANCE);
    verify(executionContext, times(1)).fetchInfraMappingId();
    verify(executionContext, times(1)).getContextElementList(ContextElementType.PARAM);
    verify(executionContext, times(6)).renderExpression(anyString());
    verify(executionContext, times(1)).getServiceVariables();
    verify(executionContext, times(1)).getSafeDisplayServiceVariables();
    verify(executionContext, times(4)).getAppId();

    verify(settingsService, times(4)).getByName(eq(ACCOUNT_ID), eq(APP_ID), eq(ENV_ID), anyString());
    verify(settingsService, times(4)).get(anyString());

    verify(workflowExecutionService).incrementInProgressCount(eq(APP_ID), any(), eq(1));
    verify(artifactStreamService).get(ARTIFACT_STREAM_ID_1);
    verify(artifactStreamService).get(ARTIFACT_STREAM_ID_2);
  }
}
