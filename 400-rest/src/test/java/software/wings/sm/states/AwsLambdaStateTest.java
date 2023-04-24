/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.VAIBHAV_KUMAR;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_FILE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.BUCKET_NAME;
import static software.wings.utils.WingsTestConstants.BUILD_NO;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.S3_URL;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsLambdaInfraStructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.LambdaSpecification;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.command.AwsLambdaCommandUnit;
import software.wings.beans.command.Command;
import software.wings.beans.command.ServiceCommand;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfRequest;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.utils.WingsTestConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsLambdaStateTest extends CategoryTest {
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SettingsService settingsService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ActivityService activityService;
  @Mock private LogService logService;
  @Mock private SecretManager secretManager;
  @Mock private ServiceTemplateHelper serviceTemplateHelper;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private DelegateService delegateService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @Spy @InjectMocks AwsLambdaState awsLambdaState;
  private SettingAttribute awsSetting =
      aSettingAttribute()
          .withUuid(SETTING_ID)
          .withValue(AwsConfig.builder().secretKey(SECRET_KEY).accessKey(ACCESS_KEY.toCharArray()).build())
          .build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testExecute_fail() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Application app = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(app).when(mockContext).getApp();

    final PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid("uuid").build()).build();
    doReturn(phaseElement).when(mockContext).getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    doReturn("infraid").when(mockContext).fetchInfraMappingId();

    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    EmbeddedUser mockCurrentUser = mock(EmbeddedUser.class);
    doReturn(mockCurrentUser).when(mockParams).getCurrentUser();
    doReturn(mockParams).when(mockContext).getContextElement(ContextElementType.STANDARD);
    doReturn(app).when(workflowStandardParamsExtensionService).fetchRequiredApp(mockParams);
    doReturn(env).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    doReturn(env).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    doReturn(mock(Service.class)).when(serviceResourceService).getWithDetails(anyString(), anyString());
    final ServiceCommand serviceCommandMock = mock(ServiceCommand.class);
    doReturn(serviceCommandMock)
        .when(serviceResourceService)
        .getCommandByName(anyString(), anyString(), anyString(), anyString());
    doReturn(mock(Command.class)).when(serviceCommandMock).getCommand();
    doReturn(null).when(infrastructureMappingService).get(anyString(), anyString());

    awsLambdaState.execute(mockContext);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecute() {
    AwsLambdaInfraStructureMapping mapping = AwsLambdaInfraStructureMapping.builder()
                                                 .uuid(INFRA_MAPPING_ID)
                                                 .appId(APP_ID)
                                                 .computeProviderSettingId(SETTING_ID)
                                                 .envId(ENV_ID)
                                                 .build();
    mapping.setInfrastructureDefinitionId(INFRA_DEFINITION_ID);
    when(infrastructureMappingService.get(anyString(), anyString())).thenReturn(mapping);
    when(settingsService.get(SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(AwsConfig.builder().build()).build());

    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Application app = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(app).when(mockContext).getApp();

    Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();

    final PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    doReturn(phaseElement).when(mockContext).getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    doReturn(INFRA_MAPPING_ID).when(mockContext).fetchInfraMappingId();

    doReturn(Service.builder().uuid(SERVICE_ID).build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    EmbeddedUser mockCurrentUser = mock(EmbeddedUser.class);
    doReturn(mockCurrentUser).when(mockParams).getCurrentUser();
    doReturn(mockParams).when(mockContext).getContextElement(ContextElementType.STANDARD);
    doReturn(app).when(workflowStandardParamsExtensionService).fetchRequiredApp(mockParams);
    doReturn(env).when(workflowStandardParamsExtensionService).getEnv(mockParams);

    DockerArtifactStream mockDockerArtifactStream = mock(DockerArtifactStream.class);
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .artifactStreamType(ArtifactStreamType.AMAZON_S3.name())
                                                            .metadataOnly(true)
                                                            .metadata(mockMetadata(ArtifactStreamType.AMAZON_S3))
                                                            .serverSetting(awsSetting.toDTO())
                                                            .artifactServerEncryptedDataDetails(Collections.emptyList())
                                                            .build();

    ServiceCommand serviceCommand =
        aServiceCommand().withCommand(aCommand().withCommandUnits(asList(new AwsLambdaCommandUnit())).build()).build();
    doReturn(serviceCommand)
        .when(serviceResourceService)
        .getCommandByName(anyString(), anyString(), anyString(), anyString());
    when(((DeploymentExecutionContext) mockContext).getDefaultArtifactForService(SERVICE_ID))
        .thenReturn(anArtifact().withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(mockDockerArtifactStream);
    when(mockDockerArtifactStream.fetchArtifactStreamAttributes(any())).thenReturn(artifactStreamAttributes);
    when(mockDockerArtifactStream.getArtifactStreamType()).thenReturn(ArtifactStreamType.AMAZON_S3.name());
    when(mockDockerArtifactStream.getSettingId()).thenReturn(SETTING_ID);
    when(serviceResourceService.getFlattenCommandUnitList(APP_ID, SERVICE_ID, ENV_ID, null))
        .thenReturn(asList(new AwsLambdaCommandUnit()));
    ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);
    when(activityService.save(activityCaptor.capture())).thenReturn(Activity.builder().build());
    when(serviceResourceService.getLambdaSpecification(APP_ID, SERVICE_ID))
        .thenReturn(LambdaSpecification.builder()
                        .functions(asList(
                            LambdaSpecification.FunctionSpecification.builder().functionName("functionName").build()))
                        .build());
    when(mockContext.renderExpression("functionName")).thenReturn("functionName");
    List<String> aliases = Mockito.mock(List.class);
    doReturn(true).when(aliases).isEmpty();
    on(awsLambdaState).set("aliases", aliases);

    doReturn(true).when(featureFlagService).isEnabled(any(), anyString());

    awsLambdaState.execute(mockContext);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD))
        .isEqualTo(INFRA_MAPPING_ID);
    Activity activity = activityCaptor.getValue();
    assertThat(activity.getInfrastructureDefinitionId()).isEqualTo(INFRA_DEFINITION_ID);
    verify(aliases, atLeastOnce()).isEmpty();
    AwsLambdaExecuteWfRequest parameter = (AwsLambdaExecuteWfRequest) delegateTask.getData().getParameters()[0];
    assertThat(parameter.getEvaluatedAliases()).isEmpty();
  }

  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testArtifactPathForSource() {
    // Case1: when jobName occurs as a substring inside artifactPath
    String path = "harness-internal/harness-internal/20211208.2.zip";
    String jobName1 = "harness-internal";
    String url1 = "https://harness.jfrog.io/artifactory/harness-internal/harness-internal/20211208.2.zip";
    Map<String, String> metadata = new HashMap<String, String>() {
      {
        put("artifactPath", path);
        put("url", url1);
      }
    };

    Artifact artifact = new Artifact();
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .artifactStreamType(SettingVariableTypes.ARTIFACTORY.name())
                                                            .jobName(jobName1)
                                                            .metadata(metadata)
                                                            .build();
    String result = awsLambdaState.artifactPathForSource(artifact, artifactStreamAttributes);
    assertThat(result).isEqualTo(path);

    // Case2: when there is no overlap between jobName and artifactPath
    String jobName2 = "test";
    String url2 = "https://harness.jfrog.io/artifactory/test/harness-internal/20211208.2.zip";
    artifactStreamAttributes.setJobName(jobName2);
    metadata.put("url", url2);
    result = awsLambdaState.artifactPathForSource(artifact, artifactStreamAttributes);
    assertThat(result).isEqualTo(path);
  }

  private Map<String, String> mockMetadata(ArtifactStreamType artifactStreamType) {
    Map<String, String> map = new HashMap<>();
    switch (artifactStreamType) {
      case AMAZON_S3:
        map.put(ArtifactMetadataKeys.bucketName, BUCKET_NAME);
        map.put(ArtifactMetadataKeys.artifactFileName, ARTIFACT_FILE_NAME);
        map.put(ArtifactMetadataKeys.artifactPath, ARTIFACT_PATH);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        map.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(WingsTestConstants.ARTIFACT_FILE_SIZE));
        map.put(ArtifactMetadataKeys.key, ACCESS_KEY);
        map.put(ArtifactMetadataKeys.url, S3_URL);
        break;
      case ARTIFACTORY:
        map.put(ArtifactMetadataKeys.artifactFileName, ARTIFACT_FILE_NAME);
        map.put(ArtifactMetadataKeys.artifactPath, ARTIFACT_PATH);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        map.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(WingsTestConstants.ARTIFACT_FILE_SIZE));
        break;
      case AZURE_ARTIFACTS:
        map.put(ArtifactMetadataKeys.version, BUILD_NO);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        break;
      case JENKINS:
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        break;
      case BAMBOO:
        map.put(ArtifactMetadataKeys.buildNo, "11");
        break;
      case NEXUS:
        map.put(ArtifactMetadataKeys.buildNo, "7.0");
        break;
      default:
        break;
    }
    return map;
  }
}
