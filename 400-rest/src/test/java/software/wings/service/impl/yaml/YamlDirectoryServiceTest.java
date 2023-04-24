/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.HarnessStringUtils.join;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.DHRUV;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.Service.GLOBAL_SERVICE_NAME_FOR_YAML;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.SettingAttribute.SettingCategory.CONNECTOR;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.command.CommandType.START;
import static software.wings.beans.yaml.YamlConstants.APPLICATIONS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.ARTIFACT_SOURCES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.CLOUD_PROVIDERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.CONFIG_FILES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.CV_CONFIG_FOLDER;
import static software.wings.beans.yaml.YamlConstants.ENVIRONMENTS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.INFRA_DEFINITION_FOLDER;
import static software.wings.beans.yaml.YamlConstants.INFRA_MAPPING_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PCF_OVERRIDES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PIPELINES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PROVISIONERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.SERVICES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.SETUP_FOLDER;
import static software.wings.beans.yaml.YamlConstants.TRIGGER_FOLDER;
import static software.wings.beans.yaml.YamlConstants.WORKFLOWS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.YAML_EXTENSION;
import static software.wings.settings.SettingVariableTypes.AMAZON_S3_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.DOCKER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.INFRA_NAME;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_NAME;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.PROVISIONER_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_COMMAND_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.CloudProviderType;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.ConfigFile;
import software.wings.beans.DockerConfig;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.JiraConfig;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.ServiceNowConfig;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.Workflow;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.command.Command;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.ScpCommandUnit;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.PcfServiceSpecification;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.yaml.YamlConstants;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PcfInfraStructure;
import software.wings.security.AppPermissionSummary;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.ArtifactType;
import software.wings.verification.CVConfiguration;
import software.wings.yaml.directory.AppLevelYamlNode;
import software.wings.yaml.directory.DirectoryNode;
import software.wings.yaml.directory.DirectoryNode.NodeType;
import software.wings.yaml.directory.DirectoryPath;
import software.wings.yaml.directory.EnvLevelYamlNode;
import software.wings.yaml.directory.FolderNode;
import software.wings.yaml.directory.YamlNode;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class YamlDirectoryServiceTest extends WingsBaseTest {
  public static final String GLOBAL_APP_ID = "__GLOBAL_APP_ID__";
  private static final String APP_MANIFEST_NAME = "APP_MANIFEST_NAME";
  @Mock private AppService appService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ConfigService configService;
  @Mock private EnvironmentService environmentService;
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private WorkflowService workflowService;
  @Mock private PipelineService pipelineService;
  @Mock private InfrastructureProvisionerService provisionerService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private SettingsService settingsService;
  @Mock ApplicationManifestService applicationManifestService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private GovernanceConfigService governanceConfigService;
  @Inject @InjectMocks private YamlDirectoryServiceImpl yamlDirectoryService;

  private DirectoryPath directoryPath = new DirectoryPath(SETUP_FOLDER);

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testDoApplications() {
    Map<String, AppPermissionSummary> appPermissionSummaryMap = new HashMap<>();
    appPermissionSummaryMap.put(APP_ID, null);
    performMocking();
    FolderNode directoryNode =
        yamlDirectoryService.doApplications(ACCOUNT_ID, directoryPath, false, appPermissionSummaryMap);
    assertThat(directoryNode).isNotNull();
    assertThat(directoryNode.getChildren()).isNotNull();
    assertThat(directoryNode.getChildren()).hasSize(1);

    FolderNode appNode = (FolderNode) directoryNode.getChildren().get(0);
    assertThat(appNode.getAppId()).isEqualTo(APP_ID);
    assertThat(appNode.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(appNode.getShortClassName()).isEqualTo("Application");

    for (DirectoryNode node : appNode.getChildren()) {
      if (node == null) {
        continue;
      }
      assertThat(node.getAccountId()).isEqualTo(ACCOUNT_ID);

      switch (node.getName()) {
        case "Index.yaml": {
          assertThat(node.getDirectoryPath().getPath()).isEqualTo("Setup/Applications/APP_NAME/Index.yaml");
          YamlNode yamlNode = (YamlNode) node;
          assertThat(yamlNode.getUuid()).isEqualTo(APP_ID);
          assertThat(yamlNode.getType()).isEqualTo(NodeType.YAML);
          break;
        }
        case "Defaults.yaml": {
          assertThat(node.getDirectoryPath().getPath()).isEqualTo("Setup/Applications/APP_NAME/Defaults.yaml");
          YamlNode yamlNode = (YamlNode) node;
          assertThat(yamlNode.getUuid()).isEqualTo(APP_ID);
          assertThat(yamlNode.getType()).isEqualTo(NodeType.YAML);
          break;
        }
        case "Services":
          FolderNode serviceFolderNode =
              validateFolderNodeGotAppAccId((FolderNode) node, "Setup/Applications/APP_NAME/Services");
          performServiceNodeValidation(serviceFolderNode);
          break;
        case "Environments":
          FolderNode envFolderNode =
              validateFolderNodeGotAppAccId((FolderNode) node, "Setup/Applications/APP_NAME/Environments");
          performEnvironmentNodeValidation(envFolderNode);
          break;
        case "Workflows":
          FolderNode workflowNode =
              validateFolderNodeGotAppAccId((FolderNode) node, "Setup/Applications/APP_NAME/Workflows");
          performWorkflowNodeValidation(workflowNode);
          break;
        case "Pipelines":
          FolderNode pipelineFolderNode =
              validateFolderNodeGotAppAccId((FolderNode) node, "Setup/Applications/APP_NAME/Pipelines");
          performPipelineNodeValidation(pipelineFolderNode);
          break;
        case "Provisioners":
          FolderNode provisionerFolderNode =
              validateFolderNodeGotAppAccId((FolderNode) node, "Setup/Applications/APP_NAME/Provisioners");
          performProvisionerNodeValidation(provisionerFolderNode);
          break;
        case "Triggers":
          FolderNode triggerFolderNode =
              validateFolderNodeGotAppAccId((FolderNode) node, "Setup/Applications/APP_NAME/Triggers");
          performTriggerNodeValidation(triggerFolderNode);
          break;
        case "Event Rules":
          FolderNode cgEventConfigFolderNode =
              validateFolderNodeGotAppAccId((FolderNode) node, "Setup/Applications/APP_NAME/Event Rules");
          break;
        default:
          throw new IllegalArgumentException("Unknown node name: " + node.getName());
      }
    }
  }

  private void performWorkflowNodeValidation(FolderNode workflowNode) {
    assertThat(workflowNode.getChildren()).isNotNull();
    assertThat(workflowNode.getChildren()).hasSize(1);
    AppLevelYamlNode workflowYamlNode = (AppLevelYamlNode) workflowNode.getChildren().get(0);
    assertThat(workflowYamlNode.getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/APP_NAME/Workflows/WORKFLOW_NAME.yaml");
    assertThat(workflowYamlNode.getAppId()).isEqualTo(APP_ID);
    assertThat(workflowYamlNode.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(workflowYamlNode.getUuid()).isEqualTo(WORKFLOW_ID);
    assertThat(workflowYamlNode.getName()).isEqualTo("WORKFLOW_NAME.yaml");
  }

  private void performPipelineNodeValidation(FolderNode pipelineNode) {
    assertThat(pipelineNode.getChildren()).isNotNull();
    assertThat(pipelineNode.getChildren()).hasSize(1);
    AppLevelYamlNode pipelineYamlNode = (AppLevelYamlNode) pipelineNode.getChildren().get(0);
    assertThat(pipelineYamlNode.getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/APP_NAME/Pipelines/PIPELINE_NAME.yaml");
    assertThat(pipelineYamlNode.getAppId()).isEqualTo(APP_ID);
    assertThat(pipelineYamlNode.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(pipelineYamlNode.getUuid()).isEqualTo(PIPELINE_ID);
    assertThat(pipelineYamlNode.getName()).isEqualTo("PIPELINE_NAME.yaml");
  }

  private void performProvisionerNodeValidation(FolderNode provisionerFolderNode) {
    assertThat(provisionerFolderNode.getChildren()).isNotNull();
    assertThat(provisionerFolderNode.getChildren()).hasSize(1);
    AppLevelYamlNode provisionerYamlNode = (AppLevelYamlNode) provisionerFolderNode.getChildren().get(0);
    assertThat(provisionerYamlNode.getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/APP_NAME/Provisioners/PROVISIONER_NAME.yaml");
    assertThat(provisionerYamlNode.getAppId()).isEqualTo(APP_ID);
    assertThat(provisionerYamlNode.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(provisionerYamlNode.getUuid()).isEqualTo(PROVISIONER_ID);
    assertThat(provisionerYamlNode.getName()).isEqualTo("PROVISIONER_NAME.yaml");
  }
  private void performTriggerNodeValidation(FolderNode triggerFolderNode) {
    assertThat(triggerFolderNode.getChildren()).isNotNull();
    assertThat(triggerFolderNode.getChildren()).hasSize(1);
    AppLevelYamlNode triggerYamlNode = (AppLevelYamlNode) triggerFolderNode.getChildren().get(0);
    assertThat(triggerYamlNode.getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/APP_NAME/Triggers/TRIGGER_NAME.yaml");
    assertThat(triggerYamlNode.getAppId()).isEqualTo(APP_ID);
    assertThat(triggerYamlNode.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(triggerYamlNode.getUuid()).isEqualTo(TRIGGER_ID);
    assertThat(triggerYamlNode.getName()).isEqualTo("TRIGGER_NAME.yaml");
  }

  private void performEnvironmentNodeValidation(FolderNode envFolderNode) {
    assertThat(envFolderNode.getChildren()).isNotNull();
    assertThat(envFolderNode.getChildren()).hasSize(1);

    // This is actual Service Folder for service with Name "SERVICE_NAME"
    assertThat(envFolderNode.getChildren().get(0).getName()).isEqualTo(ENV_NAME);
    assertThat(envFolderNode.getChildren().get(0).getType()).isEqualTo(NodeType.FOLDER);

    // These are nested yaml strcutures for service like index.yaml, config files, commands etc.
    Set<String> expectedDirPaths =
        new HashSet<>(Arrays.asList("Setup/Applications/APP_NAME/Environments/ENV_NAME/Index.yaml",
            "Setup/Applications/APP_NAME/Environments/ENV_NAME/Infrastructure Definitions",
            "Setup/Applications/APP_NAME/Environments/ENV_NAME/Service Verification",
            "Setup/Applications/APP_NAME/Environments/ENV_NAME/Config Files"));

    assertThat(((FolderNode) envFolderNode.getChildren().get(0)).getChildren()).hasSize(expectedDirPaths.size());
    for (DirectoryNode envChildNode : ((FolderNode) envFolderNode.getChildren().get(0)).getChildren()) {
      assertThat(expectedDirPaths.contains(envChildNode.getDirectoryPath().getPath())).isTrue();
      expectedDirPaths.remove(envChildNode.getDirectoryPath().getPath());
    }

    assertThat(expectedDirPaths).isEmpty();
  }

  private void performServiceNodeValidation(FolderNode serviceFolderNode) {
    assertThat(serviceFolderNode.getChildren()).isNotNull();
    assertThat(serviceFolderNode.getChildren()).hasSize(1);

    // This is actual Service Folder for service with Name "SERVICE_NAME"
    assertThat(serviceFolderNode.getChildren().get(0).getName()).isEqualTo(SERVICE_NAME);
    assertThat(serviceFolderNode.getChildren().get(0).getType()).isEqualTo(NodeType.FOLDER);

    // These are nested yaml structures for service like index.yaml, config files, commands etc.
    Set<String> expectedDirPaths =
        new HashSet<>(Arrays.asList("Setup/Applications/APP_NAME/Services/SERVICE_NAME/Index.yaml",
            "Setup/Applications/APP_NAME/Services/SERVICE_NAME/Commands",
            "Setup/Applications/APP_NAME/Services/SERVICE_NAME/Deployment Specifications",
            "Setup/Applications/APP_NAME/Services/SERVICE_NAME/Artifact Servers",
            "Setup/Applications/APP_NAME/Services/SERVICE_NAME/Config Files"));

    assertThat(((FolderNode) serviceFolderNode.getChildren().get(0)).getChildren()).hasSize(expectedDirPaths.size());
    for (DirectoryNode serviceChildNode : ((FolderNode) serviceFolderNode.getChildren().get(0)).getChildren()) {
      assertThat(expectedDirPaths.contains(serviceChildNode.getDirectoryPath().getPath())).isTrue();
      expectedDirPaths.remove(serviceChildNode.getDirectoryPath().getPath());
    }

    assertThat(expectedDirPaths).isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDoEnvironments() {
    final Application application =
        anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).appId(APP_ID).build();
    final Service service = Service.builder().name("service").appId(APP_ID).uuid("serviceId").build();
    final Environment environment_1 = anEnvironment().name("env-1").uuid("envId-1").appId(APP_ID).build();
    final Environment environment_2 = anEnvironment().name("env-2").uuid("envId-2").appId(APP_ID).build();
    final InfrastructureDefinition infraDefinition_1 = InfrastructureDefinition.builder()
                                                           .uuid("uuid-1")
                                                           .name("i-1")
                                                           .envId(environment_1.getUuid())
                                                           .appId(environment_1.getAppId())
                                                           .build();
    final InfrastructureDefinition infraDefinition_2 = InfrastructureDefinition.builder()
                                                           .uuid("uuid-2")
                                                           .name("i-2")
                                                           .envId(environment_2.getUuid())
                                                           .appId(environment_2.getAppId())
                                                           .build();

    final String relativeFilePath = "abc/xyz";
    final String relativeFilePathForYaml = "abc_xyz";
    final ConfigFile configFile = ConfigFile.builder()
                                      .entityType(EntityType.SERVICE_TEMPLATE)
                                      .entityId(SERVICE_ID)
                                      .relativeFilePath(relativeFilePath)
                                      .build();
    final ConfigFile configFileGlobal =
        ConfigFile.builder().entityType(EntityType.ENVIRONMENT).relativeFilePath(relativeFilePath).build();
    final ServiceTemplate serviceTemplate = ServiceTemplate.Builder.aServiceTemplate().withName(SERVICE_NAME).build();

    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .storeType(StoreType.Local)
                                                  .envId(environment_1.getUuid())
                                                  .serviceId(SERVICE_ID)
                                                  .build();
    applicationManifest.setUuid("app-manifest-id");
    applicationManifest.setAppId(environment_1.getAppId());
    ManifestFile manifestFile = ManifestFile.builder()
                                    .fileContent("I am a manifest file")
                                    .fileName("manifest-file-name")
                                    .applicationManifestId(applicationManifest.getUuid())
                                    .build();
    // Mocking stuff
    doReturn(Arrays.asList()).when(environmentService).getEnvByApp(any());
    when(infrastructureDefinitionService.list(any(PageRequest.class)))
        .thenReturn(aPageResponse().withResponse(Arrays.asList(infraDefinition_1)).build(),
            aPageResponse().withResponse(Arrays.asList(infraDefinition_2)).build());
    when(environmentService.list(any(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(aPageResponse().withResponse(Arrays.asList(environment_1, environment_2)).build());
    when(applicationManifestService.getAllByEnvIdAndKind(
             environment_1.getAppId(), environment_1.getUuid(), AppManifestKind.VALUES))
        .thenReturn(Arrays.asList(applicationManifest));
    when(applicationManifestService.getManifestFilesByAppManifestId(
             applicationManifest.getAppId(), applicationManifest.getUuid()))
        .thenReturn(Arrays.asList(manifestFile));
    when(serviceResourceService.get(environment_1.getAppId(), applicationManifest.getServiceId(), false))
        .thenReturn(service);
    when(configService.getConfigFileOverridesForEnv(application.getUuid(), environment_1.getUuid()))
        .thenReturn(Arrays.asList(configFile, configFileGlobal));
    when(serviceTemplateService.get(application.getUuid(), SERVICE_ID)).thenReturn(serviceTemplate);
    final FolderNode envNode = yamlDirectoryService.doEnvironments(application, directoryPath, false, null);
    assertThat(envNode).isNotNull();
    List<DirectoryNode> envChildren = envNode.getChildren();
    assertThat(envChildren).hasSize(2);
    FolderNode envFolderNode_1 = (FolderNode) envChildren.get(0);
    FolderNode envFolderNode_2 = (FolderNode) envChildren.get(1);

    // Verify returned folder node
    List<DirectoryNode> infraDefEnv_1 = getNodesOfClass(envFolderNode_1, InfrastructureDefinition.class);
    assertThat(infraDefEnv_1).hasSize(1);
    String nodeName = infraDefEnv_1.get(0).getName();
    assertThat(nodeName).isEqualTo(infraDefinition_1.getName() + YAML_EXTENSION);

    List<DirectoryNode> infraDefEnv_2 = getNodesOfClass(envFolderNode_2, InfrastructureDefinition.class);
    assertThat(infraDefEnv_2).hasSize(1);
    nodeName = infraDefEnv_2.get(0).getName();
    assertThat(nodeName).isEqualTo(infraDefinition_2.getName() + YAML_EXTENSION);

    List<DirectoryNode> configFileFolder = getNodesOfClass(envFolderNode_1, ConfigFile.class);
    assertThat(configFileFolder).hasSize(2);
    nodeName = configFileFolder.get(0).getName();
    assertThat(nodeName).isEqualTo(SERVICE_NAME);
    assertThat(((FolderNode) configFileFolder.get(0)).getChildren()).hasSize(1);
    assertThat(((FolderNode) configFileFolder.get(0)).getChildren().get(0).getName())
        .isEqualTo(relativeFilePathForYaml + YAML_EXTENSION);

    nodeName = configFileFolder.get(1).getName();
    assertThat(nodeName).isEqualTo(GLOBAL_SERVICE_NAME_FOR_YAML);
    assertThat(((FolderNode) configFileFolder.get(1)).getChildren()).hasSize(1);
    assertThat(((FolderNode) configFileFolder.get(1)).getChildren().get(0).getName())
        .isEqualTo(relativeFilePathForYaml + YAML_EXTENSION);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDoCollaborationProviders() {
    SettingAttribute jiraConnector = aSettingAttribute()
                                         .withAccountId(ACCOUNT_ID)
                                         .withName("jira-connector")
                                         .withCategory(CONNECTOR)
                                         .withValue(JiraConfig.builder()
                                                        .baseUrl("https://test"
                                                            + ".com")
                                                        .username("test")
                                                        .password("test".toCharArray())
                                                        .build())
                                         .build();
    SettingAttribute snowConnector = aSettingAttribute()
                                         .withAccountId(ACCOUNT_ID)
                                         .withName("snow-connector")
                                         .withCategory(CONNECTOR)
                                         .withValue(ServiceNowConfig.builder()
                                                        .baseUrl("http://test.com")
                                                        .username("test")
                                                        .password("test".toCharArray())
                                                        .build())
                                         .build();
    // mock stuff
    when(settingsService.listAllSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.JIRA.name()))
        .thenReturn(Arrays.asList(jiraConnector));
    when(settingsService.listAllSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.SERVICENOW.name()))
        .thenReturn(Arrays.asList(snowConnector));

    final FolderNode collabFolderNode = yamlDirectoryService.doCollaborationProviders(ACCOUNT_ID, directoryPath);
    List<DirectoryNode> collabDirNode = getNodesOfClass(collabFolderNode, SettingAttribute.class);
    assertThat(collabDirNode).hasSize(2);
    assertThat(Arrays.asList(collabDirNode.get(0).getName(), collabDirNode.get(1).getName()))
        .containsExactlyInAnyOrder(jiraConnector.getName() + YAML_EXTENSION, snowConnector.getName() + YAML_EXTENSION);
  }

  @Test
  @Owner(developers = DHRUV)
  @Category(UnitTests.class)
  public void testDoSourceRepoProviders() {
    SettingAttribute gitConnector = aSettingAttribute()
                                        .withAccountId(ACCOUNT_ID)
                                        .withName("git-connector")
                                        .withCategory(CONNECTOR)
                                        .withValue(GitConfig.builder()
                                                       .repoUrl("https://test"
                                                           + ".com")
                                                       .username("test")
                                                       .password("test".toCharArray())
                                                       .build())
                                        .build();

    // mock stuff

    when(settingsService.listAllSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.GIT.name()))
        .thenReturn(Arrays.asList(gitConnector));

    final FolderNode sourceRepoFolderNode = yamlDirectoryService.doSourceRepoProviders(ACCOUNT_ID, directoryPath);
    List<DirectoryNode> sourceRepoDirNode = getNodesOfClass(sourceRepoFolderNode, SettingAttribute.class);
    assertThat(sourceRepoDirNode).hasSize(1);
    assertThat(Arrays.asList(sourceRepoDirNode.get(0).getName()))
        .containsExactlyInAnyOrder(gitConnector.getName() + YAML_EXTENSION);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDoVerificationProviders() {
    SettingAttribute jenkinsProvider = aSettingAttribute()
                                           .withName("jenkins")
                                           .withAccountId(ACCOUNT_ID)
                                           .withCategory(CONNECTOR)
                                           .withValue(JenkinsConfig.builder()
                                                          .jenkinsUrl("https://jenkinsk8s.harness.io")
                                                          .username("default")
                                                          .password("default".toCharArray())
                                                          .build())
                                           .build();
    SettingAttribute appdConnector = aSettingAttribute()
                                         .withName("appd")
                                         .withAccountId(ACCOUNT_ID)
                                         .withCategory(CONNECTOR)
                                         .withValue(AppDynamicsConfig.builder()
                                                        .controllerUrl("http://test.com")
                                                        .username("default")
                                                        .password("default".toCharArray())
                                                        .build())
                                         .build();
    // mock stuff
    when(settingsService.listAllSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.JENKINS.name()))
        .thenReturn(Arrays.asList(jenkinsProvider));
    when(settingsService.listAllSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.APP_DYNAMICS.name()))
        .thenReturn(Arrays.asList(appdConnector));

    final FolderNode verificationFolderNode = yamlDirectoryService.doVerificationProviders(ACCOUNT_ID, directoryPath);
    List<DirectoryNode> verificationDirectoryNode = getNodesOfClass(verificationFolderNode, SettingAttribute.class);
    assertThat(verificationDirectoryNode).hasSize(2);
    assertThat(Arrays.asList(verificationDirectoryNode.get(0).getName(), verificationDirectoryNode.get(1).getName()))
        .containsExactlyInAnyOrder(
            jenkinsProvider.getName() + YAML_EXTENSION, appdConnector.getName() + YAML_EXTENSION);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDoArtifactServers() {
    SettingAttribute dockerConnector = aSettingAttribute()
                                           .withAccountId(ACCOUNT_ID)
                                           .withName("docker")
                                           .withCategory(CONNECTOR)
                                           .withValue(DockerConfig.builder()
                                                          .dockerRegistryUrl("https://hub.docker.com/_/registry")
                                                          .username("default")
                                                          .password("default".toCharArray())
                                                          .build())
                                           .build();
    SettingAttribute awsS3HelmConnector = aSettingAttribute()
                                              .withAccountId(ACCOUNT_ID)
                                              .withName("aws-helm")
                                              .withCategory(CONNECTOR)
                                              .withValue(AmazonS3HelmRepoConfig.builder()
                                                             .bucketName("default")
                                                             .region("us-east-1")
                                                             .connectorId("default")
                                                             .build())
                                              .build();

    // do mocking
    when(settingsService.listAllSettingAttributesByType(ACCOUNT_ID, DOCKER.name()))
        .thenReturn(Arrays.asList(dockerConnector));
    when(settingsService.listAllSettingAttributesByType(ACCOUNT_ID, AMAZON_S3_HELM_REPO.name()))
        .thenReturn(Arrays.asList(awsS3HelmConnector));

    FolderNode artifactServerFolderNode = yamlDirectoryService.doArtifactServers(ACCOUNT_ID, directoryPath);
    List<DirectoryNode> artifactServersDirectoryNode =
        getNodesOfClass(artifactServerFolderNode, SettingAttribute.class);
    assertThat(artifactServersDirectoryNode).hasSize(2);
    assertThat(
        Arrays.asList(artifactServersDirectoryNode.get(0).getName(), artifactServersDirectoryNode.get(1).getName()))
        .containsExactlyInAnyOrder(
            dockerConnector.getName() + YAML_EXTENSION, awsS3HelmConnector.getName() + YAML_EXTENSION);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testObtainEntityRootPath() {
    // create entities
    Application app = anApplication().name(APP_NAME).uuid(APP_ID).build();
    Service service = Service.builder().appId(app.getUuid()).uuid(SERVICE_ID).name(SERVICE_NAME).build();
    Environment env = anEnvironment().appId(app.getUuid()).name(ENV_NAME).uuid(ENV_ID).build();
    Pipeline pipeline = Pipeline.builder().name(PIPELINE_NAME).appId(app.getUuid()).build();
    InfrastructureMapping infraMapping =
        anAwsInfrastructureMapping().withName(INFRA_NAME).withEnvId(env.getUuid()).withAppId(app.getUuid()).build();
    InfrastructureDefinition infraDefinition =
        InfrastructureDefinition.builder().name("infra-def").envId(env.getUuid()).appId(app.getUuid()).build();
    Workflow workflow = aWorkflow().name(WORKFLOW_NAME).appId(app.getUuid()).build();
    ArtifactStream artifactStream = DockerArtifactStream.builder()
                                        .name(ARTIFACT_STREAM_NAME)
                                        .uuid(ARTIFACT_STREAM_ID)
                                        .serviceId(service.getUuid())
                                        .appId(app.getUuid())
                                        .build();
    InfrastructureProvisioner tfProvisioner =
        TerraformInfrastructureProvisioner.builder().name(PROVISIONER_NAME).appId(app.getUuid()).build();
    SettingAttribute awsCp =
        aSettingAttribute()
            .withName("aws-cp")
            .withCategory(SettingCategory.CLOUD_PROVIDER)
            .withValue(
                AwsConfig.builder().accessKey("access-key".toCharArray()).secretKey("secret-key".toCharArray()).build())
            .build();
    ApplicationManifest appManifest =
        ApplicationManifest.builder().storeType(StoreType.Local).envId(env.getUuid()).serviceId(SERVICE_ID).build();
    ManifestFile manifestFile =
        ManifestFile.builder().fileName("manifest-file-name").applicationManifestId(appManifest.getUuid()).build();
    CVConfiguration cvConfiguration = new CVConfiguration();
    cvConfiguration.setAppId(app.getUuid());
    cvConfiguration.setEnvId(env.getUuid());

    ConfigFile globalConfigFile = new ConfigFile();
    globalConfigFile.setEnvId(env.getUuid());
    globalConfigFile.setAppId(app.getUuid());
    globalConfigFile.setEntityType(EntityType.ENVIRONMENT);

    final String entityId = "1";
    ConfigFile configFile = new ConfigFile();
    configFile.setEnvId(env.getUuid());
    configFile.setAppId(app.getUuid());
    configFile.setEntityType(EntityType.SERVICE_TEMPLATE);
    configFile.setEntityId(entityId);

    Trigger trigger = Trigger.builder().name(TRIGGER_NAME).appId(app.getUuid()).build();

    // mock stuff
    when(appService.get(APP_ID)).thenReturn(app);
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(env);
    when(artifactStreamServiceBindingService.getService(app.getUuid(), artifactStream.getUuid(), true))
        .thenReturn(service);
    when(serviceTemplateService.get(app.getUuid(), entityId))
        .thenReturn(ServiceTemplate.Builder.aServiceTemplate().withName(SERVICE_NAME).build());

    String path = null;

    path = yamlDirectoryService.obtainEntityRootPath(null, app);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName()));

    path = yamlDirectoryService.obtainEntityRootPath(null, env);
    assertThat(path).isEqualTo(
        join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), ENVIRONMENTS_FOLDER, env.getName()));

    path = yamlDirectoryService.obtainEntityRootPath(null, pipeline);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), PIPELINES_FOLDER));

    path = yamlDirectoryService.obtainEntityRootPath(null, infraMapping);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), ENVIRONMENTS_FOLDER,
        env.getName(), INFRA_MAPPING_FOLDER));

    path = yamlDirectoryService.obtainEntityRootPath(null, infraDefinition);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), ENVIRONMENTS_FOLDER,
        env.getName(), INFRA_DEFINITION_FOLDER));

    path = yamlDirectoryService.obtainEntityRootPath(null, workflow);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), WORKFLOWS_FOLDER));

    path = yamlDirectoryService.obtainEntityRootPath(null, artifactStream);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), SERVICES_FOLDER,
        service.getName(), ARTIFACT_SOURCES_FOLDER));

    path = yamlDirectoryService.obtainEntityRootPath(null, tfProvisioner);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), PROVISIONERS_FOLDER));

    path = yamlDirectoryService.obtainEntityRootPath(null, service);
    assertThat(path).isEqualTo(
        join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), SERVICES_FOLDER, service.getName()));

    path = yamlDirectoryService.obtainEntityRootPath(null, awsCp);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, CLOUD_PROVIDERS_FOLDER));

    //    path = yamlDirectoryService.obtainEntityRootPath(null, appManifest);
    //    assertThat(path).isEqualTo(
    //        join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), ENVIRONMENTS_FOLDER, env.getName()));
    //    path = yamlDirectoryService.obtainEntityRootPath(null, manifestFile);
    //    assertThat(path).isEqualTo(
    //        join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), ENVIRONMENTS_FOLDER, env.getName()));

    path = yamlDirectoryService.obtainEntityRootPath(null, cvConfiguration);
    assertThat(path).isEqualTo(join(
        "/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), ENVIRONMENTS_FOLDER, env.getName(), CV_CONFIG_FOLDER));

    path = yamlDirectoryService.obtainEntityRootPath(env, configFile);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), ENVIRONMENTS_FOLDER,
        env.getName(), CONFIG_FILES_FOLDER, SERVICE_NAME));

    path = yamlDirectoryService.obtainEntityRootPath(env, globalConfigFile);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), ENVIRONMENTS_FOLDER,
        env.getName(), CONFIG_FILES_FOLDER, GLOBAL_SERVICE_NAME_FOR_YAML));

    path = yamlDirectoryService.obtainEntityRootPath(null, trigger);
    assertThat(path).isEqualTo(join("/", SETUP_FOLDER, APPLICATIONS_FOLDER, app.getName(), TRIGGER_FOLDER));
  }

  private List<DirectoryNode> getNodesOfClass(FolderNode folderNode, Class givenClass) {
    List<DirectoryNode> directoryNodes = new ArrayList<>();
    for (DirectoryNode node : folderNode.getChildren()) {
      if (node instanceof FolderNode && node.getTheClass() == givenClass) {
        directoryNodes.addAll(((FolderNode) node).getChildren());
      } else if (node.getTheClass() == givenClass) {
        directoryNodes.add(node);
      } else if (node instanceof FolderNode) {
        directoryNodes.addAll(getNodesOfClass((FolderNode) node, givenClass));
      }
    }
    return directoryNodes;
  }

  private void performMocking() {
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();

    doReturn(Arrays.asList(application)).when(appService).getAppsByAccountId(anyString());

    doReturn(application).when(appService).get(anyString());

    doReturn(aPageResponse()
                 .withResponse(Arrays.asList(Service.builder()
                                                 .uuid(SERVICE_ID)
                                                 .name(SERVICE_NAME)
                                                 .appId(APP_ID)
                                                 .artifactType(ArtifactType.PCF)
                                                 .build()))
                 .build())
        .when(serviceResourceService)
        .list(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyString());

    final Command command =
        Command.Builder.aCommand()
            .withCommandType(START)
            .withName("start")
            .withCommandUnits(Arrays.asList(ExecCommandUnit.Builder.anExecCommandUnit().withName("exec").build(),
                ScpCommandUnit.Builder.aScpCommandUnit().withName("scp").build()))
            .build();
    final ServiceCommand serviceCommand = ServiceCommand.Builder.aServiceCommand()
                                              .withUuid(SERVICE_COMMAND_ID)
                                              .withAppId(APP_ID)
                                              .withServiceId(SERVICE_ID)
                                              .withName(command.getName())
                                              .withTargetToAllEnv(true)
                                              .withCommand(command)
                                              .build();
    doReturn(false).when(serviceResourceService).hasInternalCommands(any());
    doReturn(Arrays.asList(serviceCommand))
        .when(serviceResourceService)
        .getServiceCommands(serviceCommand.getAppId(), serviceCommand.getServiceId());

    doReturn(PcfServiceSpecification.builder().serviceId(SERVICE_ID).manifestYaml("Fake Manifest.yaml").build())
        .when(serviceResourceService)
        .getPcfServiceSpecification(anyString(), anyString());

    ArtifactStream artifactStream = DockerArtifactStream.builder()
                                        .name("docker-stream")
                                        .appId(APP_ID)
                                        .uuid(ARTIFACT_STREAM_ID)
                                        .serviceId(SERVICE_ID)
                                        .build();
    //    doReturn(Collections.EMPTY_LIST).when(artifactStreamService).getArtifactStreamsForService(any(), any());
    doReturn(Arrays.asList(artifactStream)).when(artifactStreamService).getArtifactStreamsForService(any(), any());

    doReturn(Collections.EMPTY_LIST).when(configService).getConfigFilesForEntity(anyString(), anyString(), anyString());

    doReturn(aPageResponse()
                 .withResponse(Arrays.asList(anEnvironment().name(ENV_NAME).uuid(ENV_ID).appId(APP_ID).build()))
                 .build())
        .when(environmentService)
        .list(any(), anyBoolean(), anyString(), anyBoolean());

    PageResponse<InfrastructureDefinition> mappingResponse =
        aPageResponse()
            .withResponse(Arrays.asList(InfrastructureDefinition.builder()
                                            .accountId(ACCOUNT_ID)
                                            .appId(APP_ID)
                                            .envId(ENV_ID)
                                            .cloudProviderType(CloudProviderType.PCF)
                                            .infrastructure(PcfInfraStructure.builder()
                                                                .organization("ORG")
                                                                .space("SPACE")
                                                                .routeMaps(Arrays.asList("url1.com"))
                                                                .build())
                                            .build()))
            .build();

    doReturn(mappingResponse).when(infrastructureDefinitionService).list(any());

    doReturn(Collections.EMPTY_LIST).when(configService).getConfigFileOverridesForEnv(anyString(), anyString());

    PageResponse<Workflow> workflowsResponse = aPageResponse()
                                                   .withResponse(Arrays.asList(aWorkflow()
                                                                                   .appId(APP_ID)
                                                                                   .name(WORKFLOW_NAME)
                                                                                   .envId(ENV_ID)
                                                                                   .serviceId(SERVICE_ID)
                                                                                   .infraMappingId(INFRA_MAPPING_ID)
                                                                                   .uuid(WORKFLOW_ID)
                                                                                   .build()))
                                                   .build();
    doReturn(workflowsResponse).when(workflowService).listWorkflows(any());

    PageResponse<Pipeline> pipelineResponse =
        aPageResponse()
            .withResponse(Arrays.asList(Pipeline.builder().appId(APP_ID).uuid(PIPELINE_ID).name(PIPELINE_NAME).build()))
            .build();
    doReturn(pipelineResponse).when(pipelineService).listPipelines(any());

    PageResponse<InfrastructureProvisioner> provisionerResponse =
        aPageResponse()
            .withResponse(Arrays.asList(CloudFormationInfrastructureProvisioner.builder()
                                            .appId(APP_ID)
                                            .uuid(PROVISIONER_ID)
                                            .name(PROVISIONER_NAME)
                                            .build()))
            .build();
    PageResponse<InfrastructureProvisioner> response = aPageResponse().withResponse(provisionerResponse).build();
    doReturn(response).when(provisionerService).list(any());
  }

  private FolderNode validateFolderNodeGotAppAccId(FolderNode node, String dirPath) {
    FolderNode currentFolderNode = node;
    assertThat(currentFolderNode.getType()).isEqualTo(NodeType.FOLDER);
    assertThat(currentFolderNode.getAppId()).isEqualTo(APP_ID);
    assertThat(currentFolderNode.getDirectoryPath().getPath()).isEqualTo(dirPath);
    return currentFolderNode;
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateEnvPcfOverridesFolder() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .storeType(StoreType.Local)
                                                  .kind(AppManifestKind.PCF_OVERRIDE)
                                                  .envId(ENV_ID)
                                                  .serviceId(SERVICE_ID)
                                                  .build();
    doReturn(emptyList()).when(applicationManifestService).getAllByEnvIdAndKind(any(), any(), any());

    Environment environment = anEnvironment().name(ENV_NAME).uuid(ENV_ID).build();
    assertThat(yamlDirectoryService.generateEnvPcfOverridesFolder(ACCOUNT_ID, environment, directoryPath)).isNull();

    doReturn(applicationManifest).when(applicationManifestService).getByEnvId(any(), any(), any());

    doReturn(Arrays.asList(applicationManifest))
        .when(applicationManifestService)
        .getAllByEnvIdAndKind(any(), any(), any());
    doReturn(Arrays.asList(ManifestFile.builder().fileName("a.yml").fileContent("abc").build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());

    doReturn(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build())
        .when(serviceResourceService)
        .get(any(), any(), anyBoolean());

    DirectoryPath directoryPath = new DirectoryPath("Setup/Applications/App1/Environments/env1");
    FolderNode folderNode = yamlDirectoryService.generateEnvPcfOverridesFolder(ACCOUNT_ID, environment, directoryPath);

    // PCF Overrides Folder
    assertThat(folderNode.getName()).isEqualTo(PCF_OVERRIDES_FOLDER);
    assertThat(folderNode.getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/App1/Environments/env1/PCF Overrides");
    assertThat(folderNode.getChildren()).isNotNull();
    assertThat(folderNode.getChildren().size()).isEqualTo(3);

    // Index.yaml file for ENV Override
    assertThat(folderNode.getChildren().get(0) instanceof EnvLevelYamlNode).isTrue();
    assertThat(folderNode.getChildren().get(0).getName()).isEqualTo("Index.yaml");
    assertThat(folderNode.getChildren().get(0).getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/App1/Environments/env1/PCF Overrides/Index.yaml");
    // Existing local manifest file for ENV Override
    assertThat(folderNode.getChildren().get(1) instanceof EnvLevelYamlNode).isTrue();
    assertThat(folderNode.getChildren().get(1).getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/App1/Environments/env1/PCF Overrides/a.yml");
    assertThat(folderNode.getChildren().get(1).getName()).isEqualTo("a.yml");

    // For Env_Service
    DirectoryNode directoryNode = folderNode.getChildren().get(2);
    assertThat(directoryNode instanceof FolderNode).isTrue();
    FolderNode envServiceFolderNode = (FolderNode) directoryNode;
    assertThat(envServiceFolderNode.getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/App1/Environments/env1/PCF Overrides/Services");
    assertThat(envServiceFolderNode.getChildren()).isNotNull();
    assertThat(envServiceFolderNode.getChildren().size()).isEqualTo(1);

    FolderNode serviceNodeUnderEnv = (FolderNode) envServiceFolderNode.getChildren().get(0);
    assertThat(serviceNodeUnderEnv).isNotNull();
    assertThat(serviceNodeUnderEnv.getChildren()).isNotNull();
    assertThat(serviceNodeUnderEnv.getChildren().size()).isEqualTo(2);

    // ENV_SERVICE_OVERRIDE index.yaml
    assertThat(serviceNodeUnderEnv.getChildren().get(0).getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/App1/Environments/env1/PCF Overrides/Services/SERVICE_NAME/Index.yaml");
    // ENV_SERVICE_OVERRIDE local manifest file
    assertThat(serviceNodeUnderEnv.getChildren().get(1).getDirectoryPath().getPath())
        .isEqualTo("Setup/Applications/App1/Environments/env1/PCF Overrides/Services/SERVICE_NAME/a.yml");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testDoGovernanceConfig() {
    GovernanceConfig governanceConfig = GovernanceConfig.builder().accountId(ACCOUNT_ID).deploymentFreeze(true).build();
    governanceConfig.setUuid(UUID);

    doReturn(governanceConfig).when(governanceConfigService).get(anyString());
    when(featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, ACCOUNT_ID)).thenReturn(true);

    final FolderNode governanceConfigFolderNode = yamlDirectoryService.doGovernance(ACCOUNT_ID, UUID, directoryPath);
    List<DirectoryNode> governanceConfigDirectoryNode =
        getNodesOfClass(governanceConfigFolderNode, GovernanceConfig.class);

    assertThat(governanceConfigDirectoryNode).hasSize(1);
    assertThat(governanceConfigDirectoryNode.get(0).getName())
        .isEqualTo(YamlConstants.DEPLOYMENT_GOVERNANCE_FOLDER + YAML_EXTENSION);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGenerateMultipleApplicationManifestYaml() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .name(APP_MANIFEST_NAME)
            .serviceId(SERVICE_ID)
            .storeType(StoreType.HelmChartRepo)
            .kind(AppManifestKind.K8S_MANIFEST)
            .helmChartConfig(HelmChartConfig.builder().connectorId(SETTING_ID).build())
            .build();
    ApplicationManifest applicationManifest2 =
        ApplicationManifest.builder()
            .name(APP_MANIFEST_NAME + 2)
            .serviceId(SERVICE_ID)
            .storeType(StoreType.HelmChartRepo)
            .kind(AppManifestKind.K8S_MANIFEST)
            .helmChartConfig(HelmChartConfig.builder().connectorId(SETTING_ID).build())
            .build();

    when(applicationManifestService.getManifestsByServiceId(any(), any(), eq(AppManifestKind.K8S_MANIFEST)))
        .thenReturn(Arrays.asList(applicationManifest, applicationManifest2));
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    Service service = Service.builder().name(SERVICE_NAME).appId(APP_ID).uuid(SERVICE_ID).build();
    FolderNode folderNode =
        yamlDirectoryService.generateApplicationManifestNodeForService(ACCOUNT_ID, service, directoryPath);
    assertThat(folderNode.getChildren()).hasSize(2);
    assertThat(folderNode.getChildren().get(0).getName()).isEqualTo(APP_MANIFEST_NAME + ".yaml");
    assertThat(folderNode.getChildren().get(1).getName()).isEqualTo(APP_MANIFEST_NAME + "2.yaml");
  }
}
