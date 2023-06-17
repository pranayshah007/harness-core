/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifactstream;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.ENHANCED_GCR_CONNECTIVITY_CHECK;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO.ImageType.IMAGE_GALLERY;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.VariableType.TEXT;
import static software.wings.beans.artifact.ArtifactStreamCollectionStatus.STOPPED;
import static software.wings.beans.artifact.ArtifactStreamCollectionStatus.UNSTABLE;
import static software.wings.beans.artifact.ArtifactStreamType.AMAZON_S3;
import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_ARTIFACTS;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_MACHINE_IMAGE;
import static software.wings.beans.artifact.ArtifactStreamType.BAMBOO;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;
import static software.wings.beans.artifact.ArtifactStreamType.ECR;
import static software.wings.beans.artifact.ArtifactStreamType.GCR;
import static software.wings.beans.artifact.ArtifactStreamType.JENKINS;
import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;
import static software.wings.beans.config.ArtifactSourceable.ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY;
import static software.wings.beans.config.ArtifactSourceable.ARTIFACT_SOURCE_REGISTRY_URL_KEY;
import static software.wings.beans.config.ArtifactSourceable.ARTIFACT_SOURCE_REPOSITORY_NAME_KEY;
import static software.wings.beans.config.ArtifactSourceable.ARTIFACT_SOURCE_USER_NAME_KEY;
import static software.wings.beans.template.artifactsource.CustomRepositoryMapping.AttributeMapping.builder;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_VERSION;
import static software.wings.utils.WingsTestConstants.TRIGGER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ShellExecutionException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.observer.Subject;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureContainerRegistry;
import software.wings.beans.DockerConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Variable;
import software.wings.beans.artifact.AcrArtifactStream;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.artifact.AzureMachineImageArtifactStream;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream.Action;
import software.wings.beans.artifact.CustomArtifactStream.Script;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.EcrArtifactStream;
import software.wings.beans.artifact.GcrArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.template.artifactsource.CustomRepositoryMapping;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;
import software.wings.scheduler.BackgroundJobScheduler;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.AzureResourceService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.artifact.ArtifactStreamServiceObserver;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryFormat;
import software.wings.utils.RepositoryType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.mongodb.WriteResult;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.ws.rs.NotFoundException;
import org.jetbrains.annotations.NotNull;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@OwnedBy(CDC)
public class ArtifactStreamServiceTest extends WingsBaseTest {
  private static final String GLOBAL_APP_ID = "__GLOBAL_APP_ID__";
  private static final String ANOTHER_SETTING_ID = "ANOTHER_SETTING_ID";
  private static final String FEED = "FEED";
  private static final String PACKAGE_ID = "PACKAGE_ID";
  private static final String PACKAGE_NAME_MAVEN = "GROUP_ID:ARTIFACT_ID";
  private static final String PACKAGE_NAME_NUGET = "PACKAGE_NAME";
  private static final String SCRIPT_STRING = "echo Hello World!! and echo ${secrets.getValue(My Secret)}";
  private static final String SCRIPT_STRING_WITH_ERROR = "echo 'Hello World!!";
  private static final String SCRIPT_STRING_UPDATED = "echo updated script";

  @Inject HPersistence persistence;

  @Mock private BackgroundJobScheduler backgroundJobScheduler;
  @Mock private YamlPushService yamlPushService;
  @Mock private AppService appService;
  @Mock private BuildSourceService buildSourceService;
  @Mock private TriggerService triggerService;
  @Mock private SettingsService settingsService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @InjectMocks @Inject private ArtifactStreamService artifactStreamService;
  @Mock private AzureResourceService azureResourceService;
  @Mock private TemplateService templateService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private AlertService alertService;
  @Mock private Subject<ArtifactStreamServiceObserver> subject;
  @Mock private AuditServiceHelper auditServiceHelper;

  @Before
  public void setUp() {
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    SettingAttribute anotherSettingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(ANOTHER_SETTING_ID)).thenReturn(anotherSettingAttribute);
    when(settingsService.fetchAccountIdBySettingId(SETTING_ID)).thenReturn(ACCOUNT_ID);
    when(azureResourceService.listContainerRegistries(anyString(), anyString()))
        .thenReturn(ImmutableList.of(
            AzureContainerRegistry.builder().name("harnessqa").loginServer("harnessqa.azurecr.io").build(),
            AzureContainerRegistry.builder().name("harnessprod").loginServer("harnessprod.azurecr.io").build()));
    when(featureFlagService.isEnabled(ENHANCED_GCR_CONNECTIVITY_CHECK, APP_ID)).thenReturn(false);
  }

  private ArtifactStream createArtifactStream(ArtifactStream artifactStream) {
    ArtifactStream savedArtifactSteam = artifactStreamService.create(artifactStream);
    when(artifactStreamServiceBindingService.listArtifactStreams(artifactStream.getAppId(), SERVICE_ID))
        .thenReturn(asList(artifactStream));
    when(artifactStreamServiceBindingService.listArtifactStreamIds(artifactStream.getAppId(), SERVICE_ID))
        .thenReturn(asList(artifactStream.getUuid()));

    return savedArtifactSteam;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetSupportedBuildSourceTypesForDocker() {
    // For DOCKER Service Artifact Type
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().appId(APP_ID).artifactType(ArtifactType.DOCKER).uuid(SERVICE_ID).build())
        .thenReturn(Service.builder().appId(APP_ID).artifactType(ArtifactType.OTHER).uuid(SERVICE_ID).build());

    Map<String, String> supportedBuildSourceTypes =
        artifactStreamService.getSupportedBuildSourceTypes(APP_ID, SERVICE_ID);
    assertThat(supportedBuildSourceTypes.containsKey(CUSTOM.name())).isTrue();
    assertThat(supportedBuildSourceTypes.containsValue(CUSTOM.name())).isTrue();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetSupportedBuildSourceTypesForAwsLambda() {
    // For AWS Lambda Service Artifact Type
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().appId(APP_ID).artifactType(ArtifactType.AWS_LAMBDA).uuid(SERVICE_ID).build());

    Map<String, String> supportedBuildSourceTypes =
        artifactStreamService.getSupportedBuildSourceTypes(APP_ID, SERVICE_ID);
    assertThat(supportedBuildSourceTypes.containsKey(AMAZON_S3.name())).isTrue();
    assertThat(supportedBuildSourceTypes.containsValue(AMAZON_S3.name())).isTrue();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetSupportedBuildSourceTypesForAmi() {
    // For AWS Lambda Service Artifact Type
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().appId(APP_ID).artifactType(ArtifactType.AMI).uuid(SERVICE_ID).build());

    Map<String, String> supportedBuildSourceTypes =
        artifactStreamService.getSupportedBuildSourceTypes(APP_ID, SERVICE_ID);
    assertThat(supportedBuildSourceTypes.containsKey(AMI.name())).isTrue();
    assertThat(supportedBuildSourceTypes.containsValue(AMI.name())).isTrue();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void shouldGetSupportedBuildSourceTypesForAzureWebApp() {
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().appId(APP_ID).artifactType(ArtifactType.AZURE_WEBAPP).uuid(SERVICE_ID).build());

    Map<String, String> supportedBuildSourceTypes =
        artifactStreamService.getSupportedBuildSourceTypes(APP_ID, SERVICE_ID);
    assertThat(supportedBuildSourceTypes.containsKey(DOCKER.name())).isTrue();
    assertThat(supportedBuildSourceTypes.containsValue(DOCKER.name())).isTrue();

    assertThat(supportedBuildSourceTypes.containsKey(ARTIFACTORY.name())).isTrue();
    assertThat(supportedBuildSourceTypes.containsValue(ARTIFACTORY.name())).isTrue();

    assertThat(supportedBuildSourceTypes.containsKey(AZURE_ARTIFACTS.name())).isTrue();
    assertThat(supportedBuildSourceTypes.containsValue(AZURE_ARTIFACTS.name())).isTrue();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetSupportedBuildSourceTypesForOther() {
    // For AWS Lambda Service Artifact Type
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().appId(APP_ID).artifactType(ArtifactType.OTHER).uuid(SERVICE_ID).build());

    Map<String, String> supportedBuildSourceTypes =
        artifactStreamService.getSupportedBuildSourceTypes(APP_ID, SERVICE_ID);
    assertThat(supportedBuildSourceTypes.containsKey(JENKINS.name())).isTrue();
    assertThat(supportedBuildSourceTypes.containsValue(JENKINS.name())).isTrue();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetSupportedBuildSourceTypesForWar() {
    // For AWS Lambda Service Artifact Type
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().appId(APP_ID).artifactType(ArtifactType.WAR).uuid(SERVICE_ID).build());

    Map<String, String> supportedBuildSourceTypes =
        artifactStreamService.getSupportedBuildSourceTypes(APP_ID, SERVICE_ID);
    assertThat(supportedBuildSourceTypes.containsKey(JENKINS.name())).isTrue();
    assertThat(supportedBuildSourceTypes.containsValue(JENKINS.name())).isTrue();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetSupportedBuildSourceTypesForAzureMachineImage() {
    // For Azure Machine Image Artifact Type
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(
            Service.builder().appId(APP_ID).artifactType(ArtifactType.AZURE_MACHINE_IMAGE).uuid(SERVICE_ID).build());

    Map<String, String> supportedBuildSourceTypes =
        artifactStreamService.getSupportedBuildSourceTypes(APP_ID, SERVICE_ID);
    assertThat(supportedBuildSourceTypes.containsKey(AZURE_MACHINE_IMAGE.name())).isTrue();
    assertThat(supportedBuildSourceTypes.containsValue(AZURE_MACHINE_IMAGE.name())).isTrue();
    assertThat(supportedBuildSourceTypes.containsKey(CUSTOM.name())).isFalse();
    assertThat(supportedBuildSourceTypes.containsValue(CUSTOM.name())).isFalse();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddJenkinsArtifactStream() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStream();
    ArtifactStream savedArtifactSteam = createArtifactStream(jenkinsArtifactStream);
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(APP_ID);
    validateJenkinsArtifactStreamOnCreate(savedArtifactSteam);
    verify(appService).getAccountIdByAppId(APP_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddJenkinsArtifactStreamAtConnectorLevel() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStreamAtConnectorLevel();
    ArtifactStream savedArtifactSteam = createArtifactStream(jenkinsArtifactStream);
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(GLOBAL_APP_ID);
    validateJenkinsArtifactStreamOnCreate(savedArtifactSteam);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotAddJenkinsNonMetadataArtifactStream() {
    JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                      .sourceName("todolistwar")
                                                      .settingId(SETTING_ID)
                                                      .accountId(ACCOUNT_ID)
                                                      .appId(APP_ID)
                                                      .jobname("todolistwar")
                                                      .autoPopulate(true)
                                                      .serviceId(SERVICE_ID)
                                                      .metadataOnly(false)
                                                      .build();
    createArtifactStream(jenkinsArtifactStream);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldRemoveEmptyPathsInJenkinsArtifactStreamOnCreate() {
    JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                      .sourceName("todolistwar")
                                                      .settingId(SETTING_ID)
                                                      .accountId(ACCOUNT_ID)
                                                      .appId(APP_ID)
                                                      .jobname("todolistwar")
                                                      .autoPopulate(true)
                                                      .serviceId(SERVICE_ID)
                                                      .metadataOnly(true)
                                                      .artifactPaths(asList("", "target/todolist.war"))
                                                      .build();
    JenkinsArtifactStream jenkinsArtifactStream1 = (JenkinsArtifactStream) createArtifactStream(jenkinsArtifactStream);
    assertThat(jenkinsArtifactStream1.getArtifactPaths().size()).isEqualTo(1);
    assertThat(jenkinsArtifactStream1.getArtifactPaths()).containsExactly("target/todolist.war");
  }

  private void validateJenkinsArtifactStreamOnCreate(ArtifactStream savedArtifactSteam) {
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    String artifactDisplayName = savedArtifactSteam.fetchArtifactDisplayName("40");
    assertThat(artifactDisplayName).isNotEmpty().contains("todolistwar");
    String[] values = artifactDisplayName.split("_");
    assertThat(values).hasSize(3);
    assertThat(values[0]).isEqualTo("todolistwar");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("todolistwar");
    assertThat(savedArtifactSteam).isInstanceOf(JenkinsArtifactStream.class);
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(JENKINS.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(JENKINS.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("todolistwar");
    assertThat(savedArtifactSteam.getKeywords().size()).isEqualTo(2);
    assertThat(savedArtifactSteam.getKeywords()).contains("todolistwar", "jenkins");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());
    JenkinsArtifactStream savedJenkinsArtifactStream = (JenkinsArtifactStream) savedArtifactSteam;
    assertThat(savedJenkinsArtifactStream.getJobname()).isEqualTo("todolistwar");
    assertThat(savedJenkinsArtifactStream.getArtifactPaths()).contains("target/todolist.war");
    assertThat(savedJenkinsArtifactStream.isMetadataOnly()).isTrue();
  }

  private JenkinsArtifactStream getJenkinsStream() {
    return JenkinsArtifactStream.builder()
        .sourceName("todolistwar")
        .settingId(SETTING_ID)
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .jobname("todolistwar")
        .autoPopulate(true)
        .serviceId(SERVICE_ID)
        .artifactPaths(asList("target/todolist.war"))
        .metadataOnly(true)
        .build();
  }

  private JenkinsArtifactStream getJenkinsStreamAtConnectorLevel() {
    return JenkinsArtifactStream.builder()
        .sourceName("todolistwar")
        .settingId(SETTING_ID)
        .accountId(ACCOUNT_ID)
        .appId(GLOBAL_APP_ID)
        .jobname("todolistwar")
        .autoPopulate(true)
        .artifactPaths(asList("target/todolist.war"))
        .metadataOnly(true)
        .build();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldUpdateSettingIdOnArtifactStreamAndDeleteArtifacts() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStream();
    ArtifactStream savedArtifactSteam = createArtifactStream(jenkinsArtifactStream);
    JenkinsArtifactStream savedJenkinsArtifactStream = validateJenkinsArtifactStream(savedArtifactSteam, APP_ID);
    savedJenkinsArtifactStream.setSettingId(ANOTHER_SETTING_ID);

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedJenkinsArtifactStream);

    verify(appService, times(2)).getAccountIdByAppId(APP_ID);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
    verify(artifactService).deleteByArtifactStreamId(APP_ID, savedJenkinsArtifactStream.getUuid());
    assertThat(updatedArtifactStream.getCollectionStatus()).isEqualTo(UNSTABLE.name());
  }
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateJenkinsArtifactStream() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStream();
    ArtifactStream savedArtifactSteam = createArtifactStream(jenkinsArtifactStream);
    JenkinsArtifactStream savedJenkinsArtifactStream = validateJenkinsArtifactStream(savedArtifactSteam, APP_ID);
    JenkinsArtifactStream updatedJenkinsArtifactStream =
        updateAndValidateJenkinsArtifactStream(savedJenkinsArtifactStream, APP_ID);

    verify(appService, times(2)).getAccountIdByAppId(APP_ID);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
    verify(artifactService).deleteByArtifactStreamId(APP_ID, savedJenkinsArtifactStream.getUuid());
    verify(triggerService).updateByArtifactStream(savedJenkinsArtifactStream.getUuid());
    assertThat(updatedJenkinsArtifactStream.getCollectionStatus()).isEqualTo(UNSTABLE.name());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotUpdateJenkinsArtifactStreamToMetadataFalse() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStream();
    ArtifactStream savedArtifactSteam = createArtifactStream(jenkinsArtifactStream);
    JenkinsArtifactStream savedJenkinsArtifactStream = validateJenkinsArtifactStream(savedArtifactSteam, APP_ID);
    savedJenkinsArtifactStream.setMetadataOnly(false);
    JenkinsArtifactStream updatedJenkinsArtifactStream =
        updateAndValidateJenkinsArtifactStream(savedJenkinsArtifactStream, APP_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateJenkinsArtifactStreamAtConnectorLevel() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStreamAtConnectorLevel();
    ArtifactStream savedArtifactSteam = createArtifactStream(jenkinsArtifactStream);
    JenkinsArtifactStream savedJenkinsArtifactStream = validateJenkinsArtifactStream(savedArtifactSteam, GLOBAL_APP_ID);
    JenkinsArtifactStream updatedJenkinsArtifactStream =
        updateAndValidateJenkinsArtifactStream(savedJenkinsArtifactStream, GLOBAL_APP_ID);

    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
    verify(artifactService).deleteByArtifactStreamId(GLOBAL_APP_ID, savedArtifactSteam.getUuid());
    //    verify(triggerService).updateByApp(APP_ID);
    assertThat(updatedJenkinsArtifactStream.getCollectionStatus()).isEqualTo(UNSTABLE.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateJenkinsArtifactStreamNonMetadataWithEmptyArtifactPaths() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStream();
    ArtifactStream savedArtifactSteam = createArtifactStream(jenkinsArtifactStream);
    JenkinsArtifactStream savedJenkinsArtifactStream = validateJenkinsArtifactStream(savedArtifactSteam, APP_ID);
    savedJenkinsArtifactStream.setArtifactPaths(asList(" "));
    JenkinsArtifactStream updatedJenkinsArtifactStream =
        (JenkinsArtifactStream) artifactStreamService.update(savedJenkinsArtifactStream);
    assertThat(updatedJenkinsArtifactStream.getArtifactPaths()).isNull();
  }

  @NotNull
  private JenkinsArtifactStream validateJenkinsArtifactStream(ArtifactStream savedArtifactSteam, String appId) {
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(JENKINS.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);

    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("todolistwar");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("todolistwar");
    assertThat(savedArtifactSteam).isInstanceOf(JenkinsArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(JENKINS.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("todolistwar");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());

    JenkinsArtifactStream savedJenkinsArtifactStream = (JenkinsArtifactStream) savedArtifactSteam;
    assertThat(savedJenkinsArtifactStream.getJobname()).isEqualTo("todolistwar");
    assertThat(savedJenkinsArtifactStream.getArtifactPaths()).contains("target/todolist.war");
    return savedJenkinsArtifactStream;
  }

  private JenkinsArtifactStream updateAndValidateJenkinsArtifactStream(
      JenkinsArtifactStream savedJenkinsArtifactStream, String appId) {
    savedJenkinsArtifactStream.setName("JekinsName_Changed");
    savedJenkinsArtifactStream.setJobname("todoliswar_changed");
    savedJenkinsArtifactStream.setArtifactPaths(asList("*WAR_Changed"));

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedJenkinsArtifactStream);

    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isNotEmpty().isEqualTo("JekinsName_Changed");
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(JENKINS.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);

    assertThat(updatedArtifactStream.fetchArtifactDisplayName("")).isNotEmpty().contains("todoliswar_changed");
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo("todoliswar_changed");
    assertThat(updatedArtifactStream).isInstanceOf(JenkinsArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(JENKINS.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("todoliswar_changed");
    assertThat(updatedArtifactStream.getKeywords().size()).isEqualTo(3);
    assertThat(updatedArtifactStream.getKeywords()).contains("jekinsname_changed", "todoliswar_changed", "jenkins");
    JenkinsArtifactStream updatedJenkinsArtifactStream = (JenkinsArtifactStream) updatedArtifactStream;
    assertThat(updatedJenkinsArtifactStream.getJobname()).isEqualTo("todoliswar_changed");
    assertThat(updatedJenkinsArtifactStream.getArtifactPaths()).contains("*WAR_Changed");
    return updatedJenkinsArtifactStream;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddBambooArtifactStream() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .jobname("TOD-TOD")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .metadataOnly(true)
                                                    .build();
    ArtifactStream savedArtifactSteam = createBambooArtifactStream(bambooArtifactStream, APP_ID);
    BambooArtifactStream savedBambooArtifactStream = (BambooArtifactStream) savedArtifactSteam;
    assertThat(savedBambooArtifactStream.getJobname()).isEqualTo("TOD-TOD");
    assertThat(savedBambooArtifactStream.getArtifactPaths()).contains("artifacts/todolist.war");

    verify(appService).getAccountIdByAppId(APP_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddBambooArtifactStreamAtConnectorLevel() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(GLOBAL_APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .jobname("TOD-TOD")
                                                    .autoPopulate(true)
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .metadataOnly(true)
                                                    .build();
    ArtifactStream savedArtifactSteam = createBambooArtifactStream(bambooArtifactStream, GLOBAL_APP_ID);
    BambooArtifactStream savedBambooArtifactStream = (BambooArtifactStream) savedArtifactSteam;
    assertThat(savedBambooArtifactStream.getJobname()).isEqualTo("TOD-TOD");
    assertThat(savedBambooArtifactStream.getArtifactPaths()).contains("artifacts/todolist.war");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotAddBambooNonMetadataArtifactStreamWithoutArtifactPaths() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .jobname("TOD-TOD")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();
    createBambooArtifactStream(bambooArtifactStream, APP_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldRemoveEmptyPathsInBambooArtifactStreamOnCreate() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .jobname("TOD-TOD")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .artifactPaths(asList("", "target/todolist.war"))
                                                    .metadataOnly(true)
                                                    .build();

    BambooArtifactStream savedArtifactStream = (BambooArtifactStream) createArtifactStream(bambooArtifactStream);
    assertThat(savedArtifactStream.getArtifactPaths().size()).isEqualTo(1);
    assertThat(savedArtifactStream.getArtifactPaths()).containsExactly("target/todolist.war");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateBambooArtifactStream() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .jobname("TOD-TOD")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .metadataOnly(true)
                                                    .build();
    ArtifactStream savedArtifactSteam = createBambooArtifactStream(bambooArtifactStream, APP_ID);
    updateAndValidateBambooArtifactStream((BambooArtifactStream) savedArtifactSteam, APP_ID);

    verify(appService, times(2)).getAccountIdByAppId(APP_ID);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
    verify(artifactService).deleteByArtifactStreamId(APP_ID, savedArtifactSteam.getUuid());
    verify(triggerService).updateByArtifactStream(savedArtifactSteam.getUuid());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateBambooArtifactStreamAtConnectorLevel() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(GLOBAL_APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .jobname("TOD-TOD")
                                                    .autoPopulate(true)
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .metadataOnly(true)
                                                    .build();
    ArtifactStream savedArtifactSteam = createBambooArtifactStream(bambooArtifactStream, GLOBAL_APP_ID);

    updateAndValidateBambooArtifactStream((BambooArtifactStream) savedArtifactSteam, GLOBAL_APP_ID);

    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
    verify(artifactService).deleteByArtifactStreamId(GLOBAL_APP_ID, savedArtifactSteam.getUuid());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotUpdateBambooArtifactStreamNonMetadataWithNullArtifactPaths() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .jobname("TOD-TOD")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .build();
    BambooArtifactStream savedArtifactSteam =
        (BambooArtifactStream) createBambooArtifactStream(bambooArtifactStream, APP_ID);
    savedArtifactSteam.setArtifactPaths(null);
    artifactStreamService.update(savedArtifactSteam);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotAddBambooNonMetadataArtifactStream() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .jobname("TOD-TOD")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .metadataOnly(false)
                                                    .build();
    createArtifactStream(bambooArtifactStream);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotUpdateBambooMetadataOnlyArtifactStreamToFalse() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .jobname("TOD-TOD")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .metadataOnly(true)
                                                    .build();
    BambooArtifactStream savedBambooArtifactStream =
        (BambooArtifactStream) createBambooArtifactStream(bambooArtifactStream, APP_ID);
    savedBambooArtifactStream.setMetadataOnly(false);
    artifactStreamService.update(savedBambooArtifactStream);
  }

  private void updateAndValidateBambooArtifactStream(BambooArtifactStream savedArtifactSteam, String appId) {
    BambooArtifactStream savedBambooArtifactStream = savedArtifactSteam;
    assertThat(savedBambooArtifactStream.getJobname()).isEqualTo("TOD-TOD");
    assertThat(savedBambooArtifactStream.getArtifactPaths()).contains("artifacts/todolist.war");

    savedBambooArtifactStream.setName("Bamboo_Changed");
    savedBambooArtifactStream.setJobname("TOD-TOD_Changed");
    savedBambooArtifactStream.setArtifactPaths(asList("artifacts/todolist_changed.war"));

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedBambooArtifactStream);

    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isNotEmpty().isEqualTo("Bamboo_Changed");
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(BAMBOO.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);

    assertThat(updatedArtifactStream.fetchArtifactDisplayName("")).isNotEmpty().contains("TOD-TOD_Changed");
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo("TOD-TOD_Changed");
    assertThat(updatedArtifactStream).isInstanceOf(BambooArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(BAMBOO.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("TOD-TOD_Changed");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());
    BambooArtifactStream updatedBambooArtifactStream = savedArtifactSteam;
    assertThat(updatedBambooArtifactStream.getJobname()).isEqualTo("TOD-TOD_Changed");
    assertThat(updatedBambooArtifactStream.getArtifactPaths()).contains("artifacts/todolist_changed.war");
  }

  private ArtifactStream createBambooArtifactStream(BambooArtifactStream bambooArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(bambooArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(BAMBOO.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("TOD-TOD");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("TOD-TOD");
    assertThat(savedArtifactSteam).isInstanceOf(BambooArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(BAMBOO.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName()).isEqualTo("TOD-TOD");
    return savedArtifactSteam;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddNexusArtifactStream() {
    ArtifactStream savedArtifactSteam = createNexusArtifactStream("nexus1");
    validateNexusArtifactStream(savedArtifactSteam, APP_ID);
    NexusArtifactStream savedNexusArtifactStream = (NexusArtifactStream) savedArtifactSteam;
    assertThat(savedNexusArtifactStream.getJobname()).isEqualTo("releases");
    assertThat(savedNexusArtifactStream.getGroupId()).isEqualTo("io.harness.test");
    assertThat(savedNexusArtifactStream.getArtifactPaths()).contains("todolist");
    assertThat(savedNexusArtifactStream.getRepositoryFormat()).isEqualTo(RepositoryFormat.maven.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddNexusArtifactStreamWithExtensionAndClassifier() {
    createAndValidateNexusArtifactStream();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotUpdateExtensionForNexusArtifactStream() {
    ArtifactStream artifactStream = createAndValidateNexusArtifactStream();
    NexusArtifactStream savedNexusArtifactStream = (NexusArtifactStream) artifactStream;
    savedNexusArtifactStream.setExtension("war");
    artifactStreamService.update(savedNexusArtifactStream);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotUpdateClassifierForNexusArtifactStream() {
    ArtifactStream artifactStream = createAndValidateNexusArtifactStream();
    NexusArtifactStream savedNexusArtifactStream = (NexusArtifactStream) artifactStream;
    savedNexusArtifactStream.setClassifier("binary");
    artifactStreamService.update(savedNexusArtifactStream);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void canUpdateExtensionOnSourceChangedForNexus() {
    ArtifactStream artifactStream = createAndValidateNexusArtifactStream();
    NexusArtifactStream savedNexusArtifactStream = (NexusArtifactStream) artifactStream;
    savedNexusArtifactStream.setJobname("new_releases");
    savedNexusArtifactStream.setClassifier("binary");
    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedNexusArtifactStream);
    NexusArtifactStream updatedNexusArtifactStream = (NexusArtifactStream) updatedArtifactStream;
    assertThat(updatedNexusArtifactStream.getJobname()).isEqualTo("new_releases");
    assertThat(updatedNexusArtifactStream.getClassifier()).isEqualTo("binary");
  }

  private ArtifactStream createAndValidateNexusArtifactStream() {
    ArtifactStream savedArtifactSteam = createNexusArtifactStreamWithExtensionAndClassifier();
    validateNexusArtifactStream(savedArtifactSteam, APP_ID);
    NexusArtifactStream savedNexusArtifactStream = (NexusArtifactStream) savedArtifactSteam;
    assertThat(savedNexusArtifactStream.getJobname()).isEqualTo("releases");
    assertThat(savedNexusArtifactStream.getGroupId()).isEqualTo("io.harness.test");
    assertThat(savedNexusArtifactStream.getArtifactPaths()).contains("todolist");
    assertThat(savedNexusArtifactStream.getRepositoryFormat()).isEqualTo(RepositoryFormat.maven.name());
    assertThat(savedNexusArtifactStream.getExtension()).contains("jar");
    assertThat(savedNexusArtifactStream.getClassifier()).contains("sources");
    return savedArtifactSteam;
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddNexusArtifactStreamAtConnectorLevel() {
    ArtifactStream savedArtifactSteam = createNexusArtifactStreamAtConnectorLevel("conn-nexus1");
    validateNexusArtifactStream(savedArtifactSteam, GLOBAL_APP_ID);
    NexusArtifactStream savedNexusArtifactStream = (NexusArtifactStream) savedArtifactSteam;
    assertThat(savedNexusArtifactStream.getJobname()).isEqualTo("releases");
    assertThat(savedNexusArtifactStream.getGroupId()).isEqualTo("io.harness.test");
    assertThat(savedNexusArtifactStream.getArtifactPaths()).contains("todolist");
    assertThat(savedNexusArtifactStream.getRepositoryFormat()).isEqualTo(RepositoryFormat.maven.name());
  }

  private ArtifactStream createNexusArtifactStream(String name) {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("todolist"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .metadataOnly(true)
                                                  .name(name)
                                                  .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(nexusArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    verify(buildSourceService, times(0))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return savedArtifactSteam;
  }

  private ArtifactStream createNexusArtifactStreamAtConnectorLevelWithMetadataTrue(String name) {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(GLOBAL_APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("todolist"))
                                                  .autoPopulate(false)
                                                  .repositoryType("maven")
                                                  .name(name)
                                                  .metadataOnly(true)
                                                  .build();
    ArtifactStream savedArtifactSteam = artifactStreamService.create(nexusArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    verify(buildSourceService, times(0))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return savedArtifactSteam;
  }

  private ArtifactStream createNexusArtifactStreamAtConnectorLevel(String name) {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(GLOBAL_APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("todolist"))
                                                  .autoPopulate(false)
                                                  .repositoryType("maven")
                                                  .repositoryFormat("maven")
                                                  .name(name)
                                                  .metadataOnly(true)
                                                  .build();
    ArtifactStream savedArtifactSteam = artifactStreamService.create(nexusArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    verify(buildSourceService, times(0))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return savedArtifactSteam;
  }

  private ArtifactStream createNexusArtifactStreamWithExtensionAndClassifier() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("todolist"))
                                                  .autoPopulate(true)
                                                  .serviceId(SERVICE_ID)
                                                  .extension("jar")
                                                  .classifier("sources")
                                                  .metadataOnly(true)
                                                  .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(nexusArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    verify(buildSourceService, times(1))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return savedArtifactSteam;
  }

  private void validateNexusArtifactStream(ArtifactStream savedArtifactSteam, String appId) {
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(NEXUS.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("releases/io.harness.test/todolist__");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("releases/io.harness.test/todolist");
    assertThat(savedArtifactSteam).isInstanceOf(NexusArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(NEXUS.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName()).isEqualTo("releases");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getGroupId())
        .isEqualTo("io.harness.test");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactName())
        .isEqualTo("todolist");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateNexusArtifactStream() {
    ArtifactStream savedArtifactSteam = createNexusArtifactStream("nexus1");
    updateNexusArtifactStreamAndValidate((NexusArtifactStream) savedArtifactSteam, APP_ID, null, null);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateNexusArtifactStreamWithExtensionAndClassifier() {
    ArtifactStream savedArtifactSteam = createNexusArtifactStreamWithExtensionAndClassifier();
    updateNexusArtifactStreamAndValidate((NexusArtifactStream) savedArtifactSteam, APP_ID, "war", null);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateNexusArtifactStreamAtConnectorLevel() {
    ArtifactStream savedArtifactSteam = createNexusArtifactStreamAtConnectorLevel("conn-nexus2");
    updateNexusArtifactStreamAndValidate((NexusArtifactStream) savedArtifactSteam, GLOBAL_APP_ID, null, null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotUpdateNexusArtifactStreamWithDifferentRepositoryFormat() {
    NexusArtifactStream savedNexusArtifactStream =
        (NexusArtifactStream) createNexusArtifactStreamAtConnectorLevel("conn-nexus3");
    assertThat(savedNexusArtifactStream.getJobname()).isEqualTo("releases");
    assertThat(savedNexusArtifactStream.getArtifactPaths()).contains("todolist");
    assertThat(savedNexusArtifactStream.getRepositoryFormat()).isEqualTo(RepositoryFormat.maven.name());

    savedNexusArtifactStream.setRepositoryFormat(RepositoryFormat.docker.name());
    artifactStreamService.update(savedNexusArtifactStream);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotUpdateNexusArtifactStreamWithDifferentExtension() {
    NexusArtifactStream savedNexusArtifactStream =
        (NexusArtifactStream) createNexusArtifactStreamWithExtensionAndClassifier();
    assertThat(savedNexusArtifactStream.getJobname()).isEqualTo("releases");
    assertThat(savedNexusArtifactStream.getArtifactPaths()).contains("todolist");
    assertThat(savedNexusArtifactStream.getRepositoryFormat()).isEqualTo(RepositoryFormat.maven.name());

    savedNexusArtifactStream.setExtension("war");
    artifactStreamService.update(savedNexusArtifactStream);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotUpdateNexusArtifactStreamWithDifferentClassifier() {
    NexusArtifactStream savedNexusArtifactStream =
        (NexusArtifactStream) createNexusArtifactStreamWithExtensionAndClassifier();
    assertThat(savedNexusArtifactStream.getJobname()).isEqualTo("releases");
    assertThat(savedNexusArtifactStream.getArtifactPaths()).contains("todolist");
    assertThat(savedNexusArtifactStream.getRepositoryFormat()).isEqualTo(RepositoryFormat.maven.name());

    savedNexusArtifactStream.setClassifier(null);
    artifactStreamService.update(savedNexusArtifactStream);
  }

  private void updateNexusArtifactStreamAndValidate(
      NexusArtifactStream savedArtifactSteam, String appId, String extension, String classifier) {
    NexusArtifactStream savedNexusArtifactStream = savedArtifactSteam;
    assertThat(savedNexusArtifactStream.getJobname()).isEqualTo("releases");
    assertThat(savedNexusArtifactStream.getArtifactPaths()).contains("todolist");
    assertThat(savedNexusArtifactStream.getRepositoryFormat()).isEqualTo(RepositoryFormat.maven.name());

    savedNexusArtifactStream.setName("Nexus_Changed");
    savedNexusArtifactStream.setJobname("snapshots");
    savedNexusArtifactStream.setGroupId("io.harness.test.changed");
    savedNexusArtifactStream.setArtifactPaths(asList("todolist-changed"));
    if (isNotEmpty(extension)) {
      savedNexusArtifactStream.setExtension(extension);
    }
    if (isNotEmpty(classifier)) {
      savedNexusArtifactStream.setClassifier(classifier);
    }

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedNexusArtifactStream);
    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isNotEmpty();
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(NEXUS.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);
    assertThat(updatedArtifactStream.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("snapshots/io.harness.test.changed/todolist-changed__");
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo("snapshots/io.harness.test.changed/todolist-changed");
    assertThat(updatedArtifactStream).isInstanceOf(NexusArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(NEXUS.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("snapshots");
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getGroupId())
        .isEqualTo("io.harness.test.changed");
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactName())
        .isEqualTo("todolist-changed");
    NexusArtifactStream updatedNexusArtifactStream = savedArtifactSteam;
    assertThat(updatedNexusArtifactStream.getJobname()).isEqualTo("snapshots");
    assertThat(updatedNexusArtifactStream.getArtifactPaths()).contains("todolist-changed");

    if (appId.equals(APP_ID)) {
      verify(appService, times(2)).getAccountIdByAppId(appId);
      verify(yamlPushService, times(2))
          .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
    }
    if (isNotEmpty(extension)) {
      assertThat(updatedNexusArtifactStream.getExtension()).isEqualTo(extension);
    }

    if (isNotEmpty(classifier)) {
      assertThat(updatedNexusArtifactStream.getClassifier()).isEqualTo(classifier);
    }
    if (isEmpty(extension) && isEmpty(classifier)) {
      verify(buildSourceService, times(0))
          .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    } else {
      verify(buildSourceService, times(2))
          .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddNexusDockerArtifactStream() {
    NexusArtifactStream nexusDockerArtifactStream = NexusArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("docker-private")
                                                        .groupId("wingsplugings/todolist")
                                                        .imageName("wingsplugings/todolist")
                                                        .autoPopulate(true)
                                                        .serviceId(SERVICE_ID)
                                                        .repositoryFormat(RepositoryFormat.docker.name())
                                                        .build();
    validateNexusDockerArtifactStream(nexusDockerArtifactStream, APP_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddNexusDockerArtifactStreamAtConnectorLevel() {
    NexusArtifactStream nexusDockerArtifactStream = NexusArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(GLOBAL_APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("docker-private")
                                                        .groupId("wingsplugings/todolist")
                                                        .imageName("wingsplugings/todolist")
                                                        .autoPopulate(true)
                                                        .repositoryFormat(RepositoryFormat.docker.name())
                                                        .build();
    validateNexusDockerArtifactStream(nexusDockerArtifactStream, GLOBAL_APP_ID);
  }

  private void validateNexusDockerArtifactStream(NexusArtifactStream nexusDockerArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(nexusDockerArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(NEXUS.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("docker-private/wingsplugings/todolist__");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("docker-private/wingsplugings/todolist");
    assertThat(savedArtifactSteam).isInstanceOf(NexusArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(NEXUS.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("docker-private");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getGroupId())
        .isEqualTo("wingsplugings/todolist");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("wingsplugings/todolist");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactName()).isEmpty();
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());
    NexusArtifactStream savedNexusArtifactStream = (NexusArtifactStream) savedArtifactSteam;
    assertThat(savedNexusArtifactStream.getJobname()).isEqualTo("docker-private");
    assertThat(savedNexusArtifactStream.getGroupId()).isEqualTo("wingsplugings/todolist");
    assertThat(savedNexusArtifactStream.getImageName()).isEqualTo("wingsplugings/todolist");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateNexusDockerArtifactStream() {
    NexusArtifactStream nexusDockerArtifactStream = NexusArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("docker-private")
                                                        .groupId("wingsplugings/todolist")
                                                        .imageName("wingsplugings/todolist")
                                                        .autoPopulate(true)
                                                        .serviceId(SERVICE_ID)
                                                        .dockerRegistryUrl("https://nexus3.harness.io")
                                                        .build();
    updateNexusDockerArtifactStreamAndValidate(nexusDockerArtifactStream, APP_ID);
    verify(appService, times(2)).getAccountIdByAppId(APP_ID);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateNexusDockerArtifactStreamAtConnectorLevel() {
    NexusArtifactStream nexusDockerArtifactStream = NexusArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(GLOBAL_APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("docker-private")
                                                        .groupId("wingsplugings/todolist")
                                                        .imageName("wingsplugings/todolist")
                                                        .autoPopulate(true)
                                                        .dockerRegistryUrl("https://nexus3.harness.io")
                                                        .repositoryType("docker")
                                                        .repositoryFormat("docker")
                                                        .build();
    updateNexusDockerArtifactStreamAndValidate(nexusDockerArtifactStream, GLOBAL_APP_ID);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
  }

  private void updateNexusDockerArtifactStreamAndValidate(NexusArtifactStream nexusDockerArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(nexusDockerArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(NEXUS.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("docker-private/wingsplugings/todolist");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("docker-private/wingsplugings/todolist");
    assertThat(savedArtifactSteam).isInstanceOf(NexusArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(NEXUS.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("docker-private");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getGroupId())
        .isEqualTo("wingsplugings/todolist");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("wingsplugings/todolist");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getNexusDockerRegistryUrl())
        .isEqualTo("https://nexus3.harness.io");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactName()).isEmpty();

    NexusArtifactStream savedNexusArtifactStream = (NexusArtifactStream) savedArtifactSteam;
    assertThat(savedNexusArtifactStream.getJobname()).isEqualTo("docker-private");
    assertThat(savedNexusArtifactStream.getGroupId()).isEqualTo("wingsplugings/todolist");
    assertThat(savedNexusArtifactStream.getImageName()).isEqualTo("wingsplugings/todolist");
    assertThat(savedNexusArtifactStream.getRepositoryType()).isEqualTo("docker");

    savedNexusArtifactStream.setName("Nexus_Changed");
    savedNexusArtifactStream.setJobname("docker-hub");
    savedNexusArtifactStream.setGroupId("wingsplugings/todolist-changed");
    savedNexusArtifactStream.setImageName("wingsplugings/todolist-changed");

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedNexusArtifactStream);
    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isNotEmpty().isEqualTo("Nexus_Changed");
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(NEXUS.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);
    assertThat(updatedArtifactStream.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("docker-hub/wingsplugings/todolist-changed");
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo("docker-hub/wingsplugings/todolist-changed");
    assertThat(updatedArtifactStream).isInstanceOf(NexusArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(NEXUS.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("docker-hub");
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getGroupId())
        .isEqualTo("wingsplugings/todolist-changed");
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("wingsplugings/todolist-changed");

    NexusArtifactStream updatedNexusArtifactStream = (NexusArtifactStream) savedArtifactSteam;
    assertThat(updatedNexusArtifactStream.getJobname()).isEqualTo("docker-hub");
    assertThat(updatedNexusArtifactStream.getImageName()).isEqualTo("wingsplugings/todolist-changed");
    assertThat(updatedNexusArtifactStream.getRepositoryType()).isEqualTo("docker");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddArtifactoryArtifactStream() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .metadataOnly(true)
                                                              .build();
    ArtifactStream artifactStream =
        createAndValidateArtifactoryArtifactStream(artifactoryArtifactStream, APP_ID, "any");
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotAddArtifactoryNonMetadataArtifactStream() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .metadataOnly(false)
                                                              .build();
    createAndValidateArtifactoryArtifactStream(artifactoryArtifactStream, APP_ID, "any");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotUpdateArtifactoryMetadataOnlyArtifactStreamToFalse() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .metadataOnly(true)
                                                              .build();
    ArtifactoryArtifactStream savedArtifactoryArtifactStream =
        (ArtifactoryArtifactStream) createAndValidateArtifactoryArtifactStream(
            artifactoryArtifactStream, APP_ID, "any");
    savedArtifactoryArtifactStream.setMetadataOnly(false);
    artifactStreamService.update(savedArtifactoryArtifactStream);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddArtifactoryArtifactStreamAtConnectorLevel() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(GLOBAL_APP_ID)
                                                              .repositoryType(RepositoryType.any.name())
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .metadataOnly(true)
                                                              .build();
    ArtifactStream artifactStream =
        createAndValidateArtifactoryArtifactStream(artifactoryArtifactStream, GLOBAL_APP_ID, RepositoryType.any.name());
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  private ArtifactStream createAndValidateArtifactoryArtifactStream(
      ArtifactoryArtifactStream artifactoryArtifactStream, String appId, String repositoryType) {
    ArtifactStream savedArtifactSteam = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("generic-repo/io/harness/todolist/todolist*");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("generic-repo/io/harness/todolist/todolist*");
    assertThat(savedArtifactSteam).isInstanceOf(ArtifactoryArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType())
        .isEqualTo(repositoryType);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("generic-repo");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactPattern())
        .isEqualTo("io/harness/todolist/todolist*");

    ArtifactoryArtifactStream savedArtifactoryArtifactStream = (ArtifactoryArtifactStream) savedArtifactSteam;
    assertThat(savedArtifactoryArtifactStream.getJobname()).isEqualTo("generic-repo");
    assertThat(savedArtifactoryArtifactStream.getArtifactPattern()).isEqualTo("io/harness/todolist/todolist*");
    assertThat(savedArtifactoryArtifactStream.getRepositoryType()).isEqualTo(repositoryType);
    assertThat(savedArtifactoryArtifactStream.getCollectionStatus()).isEqualTo(UNSTABLE.name());

    verify(buildSourceService).validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return savedArtifactSteam;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateArtifactoryArtifactStream() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .appId(APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .metadataOnly(true)
                                                              .build();
    ArtifactStream artifactStream =
        updateArtifactoryArtifactStreamAndValidate(artifactoryArtifactStream, APP_ID, "any");
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateArtifactoryArtifactStreamAtConnectorLevel() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(GLOBAL_APP_ID)
                                                              .repositoryType(RepositoryType.any.name())
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .metadataOnly(true)
                                                              .build();
    ArtifactStream artifactStream =
        updateArtifactoryArtifactStreamAndValidate(artifactoryArtifactStream, GLOBAL_APP_ID, RepositoryType.any.name());
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotUpdateArtifactoryArtifactStreamAtConnectorLevelWithDifferentRepositoryType() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(GLOBAL_APP_ID)
                                                              .repositoryType(RepositoryType.any.name())
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .build();
    ArtifactoryArtifactStream savedArtifactSteam =
        (ArtifactoryArtifactStream) artifactStreamService.create(artifactoryArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getRepositoryType()).isEqualTo(RepositoryType.any.name());
    savedArtifactSteam.setRepositoryType(RepositoryType.docker.name());
    artifactStreamService.update(savedArtifactSteam);
  }

  private ArtifactStream updateArtifactoryArtifactStreamAndValidate(
      ArtifactoryArtifactStream artifactoryArtifactStream, String appId, String repositoryType) {
    ArtifactStream savedArtifactSteam = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("generic-repo/io/harness/todolist/todolist*");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("generic-repo/io/harness/todolist/todolist*");
    assertThat(savedArtifactSteam).isInstanceOf(ArtifactoryArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType())
        .isEqualTo(repositoryType);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("generic-repo");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactPattern())
        .isEqualTo("io/harness/todolist/todolist*");

    ArtifactoryArtifactStream savedArtifactoryArtifactStream = (ArtifactoryArtifactStream) savedArtifactSteam;
    assertThat(savedArtifactoryArtifactStream.getJobname()).isEqualTo("generic-repo");
    assertThat(savedArtifactoryArtifactStream.getArtifactPattern()).isEqualTo("io/harness/todolist/todolist*");
    assertThat(savedArtifactoryArtifactStream.getRepositoryType()).isEqualTo(repositoryType);

    savedArtifactoryArtifactStream.setName("Aritfactory_Changed");
    savedArtifactoryArtifactStream.setJobname("harness-rpm");
    savedArtifactoryArtifactStream.setArtifactPattern("todolist*");

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedArtifactoryArtifactStream);
    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isEqualTo("Aritfactory_Changed");
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);
    assertThat(updatedArtifactStream.fetchArtifactDisplayName("")).isNotEmpty().contains("harness-rpm/todolist*");
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo("harness-rpm/todolist*");
    assertThat(updatedArtifactStream).isInstanceOf(ArtifactoryArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ARTIFACTORY.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType())
        .isEqualTo(repositoryType);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("harness-rpm");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactPattern())
        .isEqualTo("todolist*");

    ArtifactoryArtifactStream updatedArtifactoryArtifactStream = (ArtifactoryArtifactStream) savedArtifactSteam;
    assertThat(updatedArtifactoryArtifactStream.getJobname()).isEqualTo("harness-rpm");
    assertThat(updatedArtifactoryArtifactStream.getArtifactPattern()).isEqualTo("todolist*");
    assertThat(updatedArtifactoryArtifactStream.getRepositoryType()).isEqualTo(repositoryType);

    verify(buildSourceService, times(2))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return updatedArtifactStream;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddArtifactoryMavenArtifactStream() {
    ArtifactoryArtifactStream artifactoryArtifactStream =
        ArtifactoryArtifactStream.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .repositoryType("maven")
            .settingId(SETTING_ID)
            .jobname("harness-maven")
            .artifactPattern("io/harness/todolist/todolist/*/todolist*")
            .autoPopulate(true)
            .serviceId(SERVICE_ID)
            .metadataOnly(true)
            .build();
    ArtifactStream artifactStream = addArtifactoryMavenArtifactStreamAndValidate(artifactoryArtifactStream, APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddArtifactoryMavenArtifactStreamAtConnectorLevel() {
    ArtifactoryArtifactStream artifactoryArtifactStream =
        ArtifactoryArtifactStream.builder()
            .accountId(ACCOUNT_ID)
            .appId(GLOBAL_APP_ID)
            .repositoryType("maven")
            .settingId(SETTING_ID)
            .jobname("harness-maven")
            .artifactPattern("io/harness/todolist/todolist/*/todolist*")
            .autoPopulate(true)
            .metadataOnly(true)
            .build();
    ArtifactStream artifactStream =
        addArtifactoryMavenArtifactStreamAndValidate(artifactoryArtifactStream, GLOBAL_APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  private ArtifactStream addArtifactoryMavenArtifactStreamAndValidate(
      ArtifactoryArtifactStream artifactoryArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("harness-maven/io/harness/todolist/todolist/*/todolist*");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("harness-maven/io/harness/todolist/todolist/*/todolist*");
    assertThat(savedArtifactSteam).isInstanceOf(ArtifactoryArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType())
        .isEqualTo("maven");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("harness-maven");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactPattern())
        .isEqualTo("io/harness/todolist/todolist/*/todolist*");

    ArtifactoryArtifactStream savedArtifactoryArtifactStream = (ArtifactoryArtifactStream) savedArtifactSteam;
    assertThat(savedArtifactoryArtifactStream.getJobname()).isEqualTo("harness-maven");
    assertThat(savedArtifactoryArtifactStream.getArtifactPattern())
        .isEqualTo("io/harness/todolist/todolist/*/todolist*");
    assertThat(savedArtifactoryArtifactStream.getRepositoryType()).isEqualTo("maven");
    return savedArtifactSteam;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateArtifactoryMavenArtifactStream() {
    ArtifactoryArtifactStream artifactoryArtifactStream =
        ArtifactoryArtifactStream.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .repositoryType("any")
            .settingId(SETTING_ID)
            .jobname("harness-maven")
            .artifactPattern("io/harness/todolist/todolist/*/todolist*")
            .autoPopulate(true)
            .metadataOnly(true)
            .serviceId(SERVICE_ID)
            .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(APP_ID);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("harness-maven/io/harness/todolist/todolist/*/todolist*");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("harness-maven/io/harness/todolist/todolist/*/todolist*");
    assertThat(savedArtifactSteam).isInstanceOf(ArtifactoryArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType())
        .isEqualTo("any");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("harness-maven");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactPattern())
        .isEqualTo("io/harness/todolist/todolist/*/todolist*");

    ArtifactoryArtifactStream savedArtifactoryArtifactStream = (ArtifactoryArtifactStream) savedArtifactSteam;
    assertThat(savedArtifactoryArtifactStream.getJobname()).isEqualTo("harness-maven");
    assertThat(savedArtifactoryArtifactStream.getArtifactPattern())
        .isEqualTo("io/harness/todolist/todolist/*/todolist*");
    assertThat(savedArtifactoryArtifactStream.getRepositoryType()).isEqualTo("any");

    savedArtifactoryArtifactStream.setName("Aritfactory_Changed");
    savedArtifactoryArtifactStream.setJobname("harness-maven2");
    savedArtifactoryArtifactStream.setArtifactPattern("io/harness/todolist/todolist/*/todolist2*");

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedArtifactoryArtifactStream);
    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isEqualTo("Aritfactory_Changed");
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(APP_ID);
    assertThat(updatedArtifactStream.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("io/harness/todolist/todolist/*/todolist2*");
    assertThat(updatedArtifactStream.getSourceName())
        .isEqualTo("harness-maven2/io/harness/todolist/todolist/*/todolist2*");
    assertThat(updatedArtifactStream).isInstanceOf(ArtifactoryArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ARTIFACTORY.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType())
        .isEqualTo("any");
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("harness-maven2");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactPattern())
        .isEqualTo("io/harness/todolist/todolist/*/todolist2*");

    ArtifactoryArtifactStream updatedArtifactoryArtifactStream = (ArtifactoryArtifactStream) savedArtifactSteam;
    assertThat(updatedArtifactoryArtifactStream.getJobname()).isEqualTo("harness-maven2");
    assertThat(updatedArtifactoryArtifactStream.getArtifactPattern())
        .isEqualTo("io/harness/todolist/todolist/*/todolist2*");
    assertThat(updatedArtifactoryArtifactStream.getRepositoryType()).isEqualTo("any");

    verify(appService, times(2)).getAccountIdByAppId(APP_ID);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
    verify(buildSourceService, times(2))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddArtifactoryDockerArtifactStream() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .settingId(SETTING_ID)
                                                              .jobname("docker")
                                                              .imageName("wingsplugins/todolist")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .metadataOnly(true)
                                                              .build();
    ArtifactStream artifactStream = addArtifactoryDockerArtifactStreamAndValidate(artifactoryArtifactStream, APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  private ArtifactStream addArtifactoryDockerArtifactStreamAndValidate(
      ArtifactoryArtifactStream artifactoryArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("docker/wingsplugins/todolist");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("docker/wingsplugins/todolist");
    assertThat(savedArtifactSteam).isInstanceOf(ArtifactoryArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType())
        .isEqualTo("docker");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName()).isEqualTo("docker");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("wingsplugins/todolist");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());

    ArtifactoryArtifactStream savedArtifactoryArtifactStream = (ArtifactoryArtifactStream) savedArtifactSteam;
    assertThat(savedArtifactoryArtifactStream.getJobname()).isEqualTo("docker");
    assertThat(savedArtifactoryArtifactStream.getImageName()).isEqualTo("wingsplugins/todolist");
    assertThat(savedArtifactoryArtifactStream.getRepositoryType()).isEqualTo("docker");

    verify(buildSourceService).validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return savedArtifactSteam;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateArtifactoryDockerArtifactStream() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .settingId(SETTING_ID)
                                                              .jobname("docker")
                                                              .imageName("wingsplugins/todolist")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .metadataOnly(true)
                                                              .build();
    ArtifactStream artifactStream = updateArtifactoryDockerArtifactStreamAndValidate(artifactoryArtifactStream, APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  private ArtifactStream updateArtifactoryDockerArtifactStreamAndValidate(
      ArtifactoryArtifactStream artifactoryArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("docker/wingsplugins/todolist");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("docker/wingsplugins/todolist");
    assertThat(savedArtifactSteam).isInstanceOf(ArtifactoryArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ARTIFACTORY.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType())
        .isEqualTo("docker");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName()).isEqualTo("docker");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("wingsplugins/todolist");

    ArtifactoryArtifactStream savedArtifactoryArtifactStream = (ArtifactoryArtifactStream) savedArtifactSteam;
    assertThat(savedArtifactoryArtifactStream.getJobname()).isEqualTo("docker");
    assertThat(savedArtifactoryArtifactStream.getImageName()).isEqualTo("wingsplugins/todolist");
    assertThat(savedArtifactoryArtifactStream.getRepositoryType()).isEqualTo("docker");

    savedArtifactoryArtifactStream.setName("Aritfactory_Changed");
    savedArtifactoryArtifactStream.setJobname("docker-local");
    //    savedArtifactoryArtifactStream.setArtifactPattern("todolist*");
    savedArtifactoryArtifactStream.setImageName("wingsplugins/todolist-changed");

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedArtifactoryArtifactStream);
    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isEqualTo("Aritfactory_Changed");
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);
    assertThat(updatedArtifactStream.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("docker-local/wingsplugins/todolist-changed");
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo("docker-local/wingsplugins/todolist-changed");
    assertThat(updatedArtifactStream).isInstanceOf(ArtifactoryArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ARTIFACTORY.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType())
        .isEqualTo("docker");
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("docker-local");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("wingsplugins/todolist-changed");

    ArtifactoryArtifactStream updatedArtifactoryArtifactStream = (ArtifactoryArtifactStream) savedArtifactSteam;
    assertThat(updatedArtifactoryArtifactStream.getJobname()).isEqualTo("docker-local");
    assertThat(updatedArtifactoryArtifactStream.getImageName()).isEqualTo("wingsplugins/todolist-changed");
    assertThat(updatedArtifactoryArtifactStream.getRepositoryType()).isEqualTo("docker");

    verify(appService, times(2)).getAccountIdByAppId(appId);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());

    verify(buildSourceService, times(2))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return updatedArtifactStream;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddAmiArtifactStream() {
    AmiArtifactStream.Tag tag = new AmiArtifactStream.Tag();
    tag.setKey("name");
    tag.setValue("jenkins");
    AmiArtifactStream amiArtifactStream = AmiArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .settingId(SETTING_ID)
                                              .region("us-east-1")
                                              .tags(asList(tag))
                                              .autoPopulate(true)
                                              .serviceId(SERVICE_ID)
                                              .build();
    validateAmiArtifactStream(amiArtifactStream, APP_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddAmiArtifactStreamAtCloudProviderLevel() {
    AmiArtifactStream.Tag tag = new AmiArtifactStream.Tag();
    tag.setKey("name");
    tag.setValue("jenkins");
    AmiArtifactStream amiArtifactStream = AmiArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(GLOBAL_APP_ID)
                                              .settingId(SETTING_ID)
                                              .region("us-east-1")
                                              .tags(asList(tag))
                                              .autoPopulate(true)
                                              .build();
    validateAmiArtifactStream(amiArtifactStream, GLOBAL_APP_ID);
  }

  private void validateAmiArtifactStream(AmiArtifactStream amiArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(amiArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(AMI.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("us-east-1:name:jenkins");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("us-east-1:name:jenkins");
    assertThat(savedArtifactSteam).isInstanceOf(AmiArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(AMI.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRegion()).isEqualTo("us-east-1");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());
    AmiArtifactStream savedAmiArtifactStream = (AmiArtifactStream) savedArtifactSteam;
    assertThat(savedAmiArtifactStream.getRegion()).isEqualTo("us-east-1");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateAmiArtifactStream() {
    AmiArtifactStream.Tag tag = new AmiArtifactStream.Tag();
    tag.setKey("name");
    tag.setValue("jenkins");

    AmiArtifactStream amiArtifactStream = AmiArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .settingId(SETTING_ID)
                                              .region("us-east-1")
                                              .tags(asList(tag))
                                              .autoPopulate(true)
                                              .serviceId(SERVICE_ID)
                                              .build();
    updateAmiArtifactStreamAndValidate(amiArtifactStream, APP_ID);

    verify(appService, times(2)).getAccountIdByAppId(APP_ID);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateAmiArtifactStreamAtCloudProviderLevel() {
    AmiArtifactStream.Tag tag = new AmiArtifactStream.Tag();
    tag.setKey("name");
    tag.setValue("jenkins");

    AmiArtifactStream amiArtifactStream = AmiArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(GLOBAL_APP_ID)
                                              .settingId(SETTING_ID)
                                              .region("us-east-1")
                                              .tags(asList(tag))
                                              .autoPopulate(true)
                                              .build();
    updateAmiArtifactStreamAndValidate(amiArtifactStream, GLOBAL_APP_ID);

    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
  }

  private void updateAmiArtifactStreamAndValidate(AmiArtifactStream amiArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(amiArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(AMI.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("us-east-1:name:jenkins");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("us-east-1:name:jenkins");
    assertThat(savedArtifactSteam).isInstanceOf(AmiArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(AMI.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRegion()).isEqualTo("us-east-1");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getTags()).containsKey("name");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getTags())
        .containsValue(asList("jenkins"));

    AmiArtifactStream savedAmiArtifactStream = (AmiArtifactStream) savedArtifactSteam;
    assertThat(savedAmiArtifactStream.getRegion()).isEqualTo("us-east-1");

    AmiArtifactStream.Tag updatedTag = new AmiArtifactStream.Tag();
    updatedTag.setKey("name2");
    updatedTag.setValue("jenkins2");
    savedAmiArtifactStream.getTags().add(updatedTag);
    savedAmiArtifactStream.setRegion("us-west");

    ArtifactStream updatedAmiArtifactStream = artifactStreamService.update(savedAmiArtifactStream);
    assertThat(updatedAmiArtifactStream.getAccountId()).isEqualTo(ACCOUNT_ID);

    assertThat(updatedAmiArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedAmiArtifactStream.getName()).isNotEmpty();
    assertThat(updatedAmiArtifactStream.getArtifactStreamType()).isEqualTo(AMI.name());
    assertThat(updatedAmiArtifactStream.getAppId()).isEqualTo(appId);
    assertThat(updatedAmiArtifactStream.fetchArtifactDisplayName("")).isNotEmpty().contains("us-west:name:jenkins");
    assertThat(updatedAmiArtifactStream.getSourceName()).isEqualTo("us-west:name:jenkins_name2:jenkins2");
    assertThat(updatedAmiArtifactStream).isInstanceOf(AmiArtifactStream.class);
    assertThat(updatedAmiArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(AMI.name());
    assertThat(updatedAmiArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getRegion())
        .isEqualTo("us-west");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getTags()).containsKey("name");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getTags())
        .containsValue(asList("jenkins"));
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getTags()).containsKey("name2");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getTags())
        .containsValue(asList("jenkins2"));

    AmiArtifactStream updatedArtifactStream = (AmiArtifactStream) savedArtifactSteam;
    assertThat(updatedArtifactStream.getRegion()).isEqualTo("us-west");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddS3ArtifactStream() {
    AmazonS3ArtifactStream amazonS3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("harnessapps")
                                                        .autoPopulate(true)
                                                        .serviceId(SERVICE_ID)
                                                        .artifactPaths(asList("dev/todolist.war"))
                                                        .build();
    validateS3ArtifactStream(amazonS3ArtifactStream, APP_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddS3ArtifactStreamAtConnectorLevel() {
    AmazonS3ArtifactStream amazonS3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(GLOBAL_APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("harnessapps")
                                                        .autoPopulate(true)
                                                        .artifactPaths(asList("dev/todolist.war"))
                                                        .build();
    validateS3ArtifactStream(amazonS3ArtifactStream, GLOBAL_APP_ID);
  }

  private void validateS3ArtifactStream(AmazonS3ArtifactStream amazonS3ArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(amazonS3ArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(AMAZON_S3.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("harnessapps");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("harnessapps/dev/todolist.war");
    assertThat(savedArtifactSteam).isInstanceOf(AmazonS3ArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(AMAZON_S3.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("harnessapps");
    AmazonS3ArtifactStream savedAmazonS3ArtifactStream = (AmazonS3ArtifactStream) savedArtifactSteam;
    assertThat(savedAmazonS3ArtifactStream.getJobname()).isEqualTo("harnessapps");
    assertThat(savedAmazonS3ArtifactStream.getArtifactPaths()).contains("dev/todolist.war");
    assertThat(savedAmazonS3ArtifactStream.getCollectionStatus()).isEqualTo(UNSTABLE.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateS3ArtifactStream() {
    AmazonS3ArtifactStream amazonS3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("harnessapps")
                                                        .autoPopulate(true)
                                                        .serviceId(SERVICE_ID)
                                                        .artifactPaths(asList("dev/todolist.war"))
                                                        .build();
    ArtifactStream artifactStream = updateS3ArtifactStreamAndValidate(amazonS3ArtifactStream, APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateS3ArtifactStreamAtConnectorLevel() {
    AmazonS3ArtifactStream amazonS3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(GLOBAL_APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("harnessapps")
                                                        .autoPopulate(true)
                                                        .artifactPaths(asList("dev/todolist.war"))
                                                        .build();
    ArtifactStream artifactStream = updateS3ArtifactStreamAndValidate(amazonS3ArtifactStream, GLOBAL_APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  private ArtifactStream updateS3ArtifactStreamAndValidate(
      AmazonS3ArtifactStream amazonS3ArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(amazonS3ArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(AMAZON_S3.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("harnessapps");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("harnessapps/dev/todolist.war");
    assertThat(savedArtifactSteam).isInstanceOf(AmazonS3ArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(AMAZON_S3.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("harnessapps");
    AmazonS3ArtifactStream savedAmazonS3ArtifactStream = (AmazonS3ArtifactStream) savedArtifactSteam;
    assertThat(savedAmazonS3ArtifactStream.getJobname()).isEqualTo("harnessapps");
    assertThat(savedAmazonS3ArtifactStream.getArtifactPaths()).contains("dev/todolist.war");

    savedAmazonS3ArtifactStream.setJobname("harnessapps-changed");
    savedAmazonS3ArtifactStream.setName("s3 stream");
    savedAmazonS3ArtifactStream.setArtifactPaths(asList("qa/todolist.war"));

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedAmazonS3ArtifactStream);
    assertThat(updatedArtifactStream.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isNotEmpty().isEqualTo("s3 stream");
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(AMAZON_S3.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);
    assertThat(updatedArtifactStream.fetchArtifactDisplayName("")).isNotEmpty().contains("harnessapps-changed");
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo("harnessapps-changed/qa/todolist.war");
    assertThat(updatedArtifactStream).isInstanceOf(AmazonS3ArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(AMAZON_S3.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getJobName())
        .isEqualTo("harnessapps-changed");
    AmazonS3ArtifactStream updatedAmazonS3ArtifactStream = (AmazonS3ArtifactStream) savedArtifactSteam;
    assertThat(updatedAmazonS3ArtifactStream.getJobname()).isEqualTo("harnessapps-changed");
    assertThat(updatedAmazonS3ArtifactStream.getArtifactPaths()).contains("qa/todolist.war");
    return updatedArtifactStream;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddDockerArtifactStream() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(dockerArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertDockerArtifactStream(savedArtifactSteam, APP_ID);
    verify(buildSourceService).validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddDockerArtifactStreamAtConnectorLevel() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(GLOBAL_APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(dockerArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertDockerArtifactStream(savedArtifactSteam, GLOBAL_APP_ID);
    verify(buildSourceService).validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldFailCreationWhenInvalidSettingId() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(GLOBAL_APP_ID)
                                                    .settingId("invalidSettingId")
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .build();
    assertThatThrownBy(() -> artifactStreamService.create(dockerArtifactStream)).isInstanceOf(GeneralException.class);
  }

  private void assertDockerArtifactStream(ArtifactStream savedArtifactSteam, String appId) {
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(DOCKER.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("wingsplugins/todolist");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("wingsplugins/todolist");
    assertThat(savedArtifactSteam).isInstanceOf(DockerArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(DOCKER.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("wingsplugins/todolist");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());
    DockerArtifactStream savedDockerArtifactStream = (DockerArtifactStream) savedArtifactSteam;
    assertThat(savedDockerArtifactStream.getImageName()).isEqualTo("wingsplugins/todolist");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateDockerArtifactStream() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();
    ArtifactStream artifactStream = updateAndValidateDockerArtifactStream(dockerArtifactStream, APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateDockerArtifactStreamAtConnectorLevel() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(GLOBAL_APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .build();
    ArtifactStream artifactStream = updateAndValidateDockerArtifactStream(dockerArtifactStream, GLOBAL_APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  private ArtifactStream updateAndValidateDockerArtifactStream(
      DockerArtifactStream dockerArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(dockerArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(DOCKER.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("wingsplugins/todolist");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("wingsplugins/todolist");
    assertThat(savedArtifactSteam).isInstanceOf(DockerArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(DOCKER.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("wingsplugins/todolist");
    DockerArtifactStream savedDockerArtifactStream = (DockerArtifactStream) savedArtifactSteam;
    assertThat(savedDockerArtifactStream.getImageName()).isEqualTo("wingsplugins/todolist");

    savedDockerArtifactStream.setImageName("harness/todolist");
    savedArtifactSteam.setName("Docker Stream");

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedDockerArtifactStream);
    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isNotEmpty().isEqualTo("Docker Stream");
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(DOCKER.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);
    assertThat(updatedArtifactStream.fetchArtifactDisplayName("")).isNotEmpty().contains("harness/todolist");
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo("harness/todolist");
    assertThat(updatedArtifactStream).isInstanceOf(DockerArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(DOCKER.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("harness/todolist");
    DockerArtifactStream updatedDockerArtifactStream = (DockerArtifactStream) savedArtifactSteam;
    assertThat(updatedDockerArtifactStream.getImageName()).isEqualTo("harness/todolist");

    verify(buildSourceService, times(2))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return updatedArtifactStream;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddEcrArtifactStream() {
    EcrArtifactStream dockerArtifactStream = EcrArtifactStream.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .appId(APP_ID)
                                                 .settingId(SETTING_ID)
                                                 .imageName("todolist")
                                                 .region("us-east-1")
                                                 .autoPopulate(true)
                                                 .serviceId(SERVICE_ID)
                                                 .metadataOnly(true)
                                                 .build();
    validateECRArtifactStream(dockerArtifactStream, APP_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddEcrArtifactStreamAtCloudProviderLevel() {
    EcrArtifactStream dockerArtifactStream = EcrArtifactStream.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .appId(GLOBAL_APP_ID)
                                                 .settingId(SETTING_ID)
                                                 .imageName("todolist")
                                                 .region("us-east-1")
                                                 .autoPopulate(true)
                                                 .metadataOnly(true)
                                                 .build();
    validateECRArtifactStream(dockerArtifactStream, GLOBAL_APP_ID);
  }

  private void validateECRArtifactStream(EcrArtifactStream dockerArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(dockerArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ECR.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("todolist");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("todolist");
    assertThat(savedArtifactSteam).isInstanceOf(EcrArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ECR.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("todolist");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRegion()).isEqualTo("us-east-1");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());
    EcrArtifactStream savedEcrArtifactStream = (EcrArtifactStream) savedArtifactSteam;
    assertThat(savedEcrArtifactStream.getImageName()).isEqualTo("todolist");

    verify(buildSourceService).validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateEcrArtifactStream() {
    EcrArtifactStream dockerArtifactStream = EcrArtifactStream.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .appId(APP_ID)
                                                 .settingId(SETTING_ID)
                                                 .imageName("todolist")
                                                 .region("us-east-1")
                                                 .autoPopulate(true)
                                                 .serviceId(SERVICE_ID)
                                                 .metadataOnly(true)
                                                 .build();
    ArtifactStream artifactStream = updateAndValidateECRArtifactStream(dockerArtifactStream, APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateEcrArtifactStreamAtCloudProviderLevel() {
    EcrArtifactStream dockerArtifactStream = EcrArtifactStream.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .appId(GLOBAL_APP_ID)
                                                 .settingId(SETTING_ID)
                                                 .imageName("todolist")
                                                 .region("us-east-1")
                                                 .autoPopulate(true)
                                                 .metadataOnly(true)
                                                 .build();
    ArtifactStream artifactStream = updateAndValidateECRArtifactStream(dockerArtifactStream, GLOBAL_APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  private ArtifactStream updateAndValidateECRArtifactStream(EcrArtifactStream dockerArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(dockerArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ECR.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("todolist");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("todolist");
    assertThat(savedArtifactSteam).isInstanceOf(EcrArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ECR.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("todolist");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRegion()).isEqualTo("us-east-1");
    EcrArtifactStream savedEcrArtifactStream = (EcrArtifactStream) savedArtifactSteam;

    savedEcrArtifactStream.setRegion("us-west");
    savedEcrArtifactStream.setName("Ecr Stream");
    savedEcrArtifactStream.setImageName("todolist-changed");

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedEcrArtifactStream);
    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isNotEmpty().isEqualTo("Ecr Stream");
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(ECR.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);
    assertThat(updatedArtifactStream.fetchArtifactDisplayName("")).isNotEmpty().contains("todolist-changed");
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo("todolist-changed");

    assertThat(updatedArtifactStream).isInstanceOf(EcrArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ECR.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("todolist-changed");
    EcrArtifactStream updatedEcrArtifactStream = (EcrArtifactStream) savedArtifactSteam;
    assertThat(updatedEcrArtifactStream.getImageName()).isEqualTo("todolist-changed");

    verify(buildSourceService, times(2))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return updatedArtifactStream;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddGcrArtifactStream() {
    GcrArtifactStream gcrArtifactStream = GcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .settingId(SETTING_ID)
                                              .dockerImageName("exploration-161417/todolist")
                                              .registryHostName("gcr.io")
                                              .autoPopulate(true)
                                              .serviceId(SERVICE_ID)
                                              .build();
    validateGCRArtifactStream(gcrArtifactStream, APP_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddGcrArtifactStreamAtCloudProviderLevel() {
    GcrArtifactStream gcrArtifactStream = GcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(GLOBAL_APP_ID)
                                              .settingId(SETTING_ID)
                                              .dockerImageName("exploration-161417/todolist")
                                              .registryHostName("gcr.io")
                                              .autoPopulate(true)
                                              .build();
    validateGCRArtifactStream(gcrArtifactStream, GLOBAL_APP_ID);
  }

  private void validateGCRArtifactStream(GcrArtifactStream gcrArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(gcrArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(GCR.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("exploration-161417/todolist");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("gcr.io/exploration-161417/todolist");
    assertThat(savedArtifactSteam).isInstanceOf(GcrArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(GCR.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("exploration-161417/todolist");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRegistryHostName())
        .isEqualTo("gcr.io");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());
    GcrArtifactStream savedGcrArtifactStream = (GcrArtifactStream) savedArtifactSteam;
    assertThat(savedGcrArtifactStream.getDockerImageName()).isEqualTo("exploration-161417/todolist");
    assertThat(savedGcrArtifactStream.getRegistryHostName()).isEqualTo("gcr.io");

    verify(buildSourceService).validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateGcrArtifactStream() {
    GcrArtifactStream gcrArtifactStream = GcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .settingId(SETTING_ID)
                                              .dockerImageName("exploration-161417/todolist")
                                              .registryHostName("gcr.io")
                                              .autoPopulate(true)
                                              .serviceId(SERVICE_ID)
                                              .build();
    ArtifactStream artifactStream = updateAndValidateGCRArtifactStream(gcrArtifactStream, APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateGcrArtifactStreamAtCloudProviderLevel() {
    GcrArtifactStream gcrArtifactStream = GcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(GLOBAL_APP_ID)
                                              .settingId(SETTING_ID)
                                              .dockerImageName("exploration-161417/todolist")
                                              .registryHostName("gcr.io")
                                              .autoPopulate(true)
                                              .build();
    ArtifactStream artifactStream = updateAndValidateGCRArtifactStream(gcrArtifactStream, GLOBAL_APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  private ArtifactStream updateAndValidateGCRArtifactStream(GcrArtifactStream gcrArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(gcrArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(GCR.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("exploration-161417/todolist");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("gcr.io/exploration-161417/todolist");
    assertThat(savedArtifactSteam).isInstanceOf(GcrArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(GCR.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("exploration-161417/todolist");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRegistryHostName())
        .isEqualTo("gcr.io");
    GcrArtifactStream savedGcrArtifactStream = (GcrArtifactStream) savedArtifactSteam;
    assertThat(savedGcrArtifactStream.getDockerImageName()).isEqualTo("exploration-161417/todolist");
    assertThat(savedGcrArtifactStream.getRegistryHostName()).isEqualTo("gcr.io");

    savedGcrArtifactStream.setDockerImageName("exploration-161417/todolist-changed");
    savedGcrArtifactStream.setRegistryHostName("gcr.io");
    savedGcrArtifactStream.setName("Gcr Stream");

    ArtifactStream updatedArtifactSteam = artifactStreamService.update(savedGcrArtifactStream);
    assertThat(updatedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(updatedArtifactSteam.getName()).isNotEmpty().isEqualTo("Gcr Stream");
    assertThat(updatedArtifactSteam.getArtifactStreamType()).isEqualTo(GCR.name());
    assertThat(updatedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(updatedArtifactSteam.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("exploration-161417/todolist-changed");
    assertThat(updatedArtifactSteam.getSourceName()).isEqualTo("gcr.io/exploration-161417/todolist-changed");
    assertThat(updatedArtifactSteam).isInstanceOf(GcrArtifactStream.class);
    assertThat(updatedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(GCR.name());
    assertThat(updatedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getImageName())
        .isEqualTo("exploration-161417/todolist-changed");
    assertThat(updatedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRegistryHostName())
        .isEqualTo("gcr.io");

    verify(buildSourceService, times(2))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return updatedArtifactSteam;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddAcrArtifactStream() {
    AcrArtifactStream acrArtifactStream = AcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .settingId(SETTING_ID)
                                              .subscriptionId("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0")
                                              .repositoryName("nginx")
                                              .registryName("harnessqa")
                                              .autoPopulate(true)
                                              .serviceId(SERVICE_ID)
                                              .build();
    validateAcrArtifactStream(acrArtifactStream, APP_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddAcrArtifactStreamAtCloudProviderLevel() {
    AcrArtifactStream acrArtifactStream = AcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(GLOBAL_APP_ID)
                                              .settingId(SETTING_ID)
                                              .subscriptionId("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0")
                                              .repositoryName("nginx")
                                              .registryName("harnessqa")
                                              .autoPopulate(true)
                                              .build();
    validateAcrArtifactStream(acrArtifactStream, GLOBAL_APP_ID);
  }

  private void validateAcrArtifactStream(AcrArtifactStream acrArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(acrArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ArtifactStreamType.ACR.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("harnessqa/nginx");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("harnessqa/nginx");
    assertThat(savedArtifactSteam).isInstanceOf(AcrArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ArtifactStreamType.ACR.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getSubscriptionId())
        .isEqualTo("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRepositoryName())
        .isEqualTo("nginx");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRegistryName())
        .isEqualTo("harnessqa");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(UNSTABLE.name());
    AcrArtifactStream savedAcrArtifactStream = (AcrArtifactStream) savedArtifactSteam;
    assertThat(savedAcrArtifactStream.getSubscriptionId()).isEqualTo("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0");
    assertThat(savedAcrArtifactStream.getRepositoryName()).isEqualTo("nginx");
    assertThat(savedAcrArtifactStream.getRegistryName()).isEqualTo("harnessqa");

    verify(buildSourceService).validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateAcrArtifactStream() {
    AcrArtifactStream acrArtifactStream = AcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .settingId(SETTING_ID)
                                              .subscriptionId("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0")
                                              .repositoryName("nginx")
                                              .registryName("harnessqa")
                                              .registryHostName("harnessqa.azurecr.io")
                                              .autoPopulate(true)
                                              .serviceId(SERVICE_ID)
                                              .build();
    ArtifactStream artifactStream = updateAndValidateACRArtifactStream(acrArtifactStream, APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateAcrArtifactStreamAtCloudProviderLevel() {
    AcrArtifactStream acrArtifactStream = AcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(GLOBAL_APP_ID)
                                              .settingId(SETTING_ID)
                                              .subscriptionId("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0")
                                              .repositoryName("nginx")
                                              .registryName("harnessqa")
                                              .registryHostName("harnessqa.azurecr.io")
                                              .autoPopulate(true)
                                              .build();
    ArtifactStream artifactStream = updateAndValidateACRArtifactStream(acrArtifactStream, GLOBAL_APP_ID);
    assertThat(artifactStream.getUuid()).isNotEmpty();
  }

  private ArtifactStream updateAndValidateACRArtifactStream(AcrArtifactStream acrArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(acrArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ArtifactStreamType.ACR.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName("")).isNotEmpty().contains("harnessqa/nginx");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("harnessqa/nginx");
    assertThat(savedArtifactSteam).isInstanceOf(AcrArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ArtifactStreamType.ACR.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getSubscriptionId())
        .isEqualTo("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRepositoryName())
        .isEqualTo("nginx");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getRegistryName())
        .isEqualTo("harnessqa");
    AcrArtifactStream savedAcrArtifactStream = (AcrArtifactStream) savedArtifactSteam;
    assertThat(savedAcrArtifactStream.getSubscriptionId()).isEqualTo("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0");
    assertThat(savedAcrArtifactStream.getRepositoryName()).isEqualTo("nginx");
    assertThat(savedAcrArtifactStream.getRegistryName()).isEqualTo("harnessqa");
    assertThat(savedAcrArtifactStream.getRegistryHostName()).isEqualTo("harnessqa.azurecr.io");

    savedAcrArtifactStream.setRegistryName("harnessprod");
    savedAcrArtifactStream.setRegistryHostName("harnessprod.azurecr.io");
    savedAcrArtifactStream.setRepositoryName("istio");
    savedAcrArtifactStream.setName("Acr Stream");

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedAcrArtifactStream);
    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isNotEmpty();
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(ArtifactStreamType.ACR.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);
    assertThat(updatedArtifactStream.fetchArtifactDisplayName("")).isNotEmpty().contains("harnessprod/istio");
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo("harnessprod/istio");
    assertThat(updatedArtifactStream).isInstanceOf(AcrArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(ArtifactStreamType.ACR.name());
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getSubscriptionId())
        .isEqualTo("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0");
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getRepositoryName())
        .isEqualTo("istio");
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getRegistryName())
        .isEqualTo("harnessprod");

    AcrArtifactStream updatedAcrArtifactStream = (AcrArtifactStream) savedArtifactSteam;
    assertThat(updatedAcrArtifactStream.getSubscriptionId()).isEqualTo("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0");
    assertThat(updatedAcrArtifactStream.getRepositoryName()).isEqualTo("istio");
    assertThat(updatedAcrArtifactStream.getRegistryName()).isEqualTo("harnessprod");
    assertThat(updatedAcrArtifactStream.getRegistryHostName()).isEqualTo("harnessprod.azurecr.io");
    verify(buildSourceService, times(2))
        .validateArtifactSource(anyString(), anyString(), any(ArtifactStreamAttributes.class));
    return updatedArtifactStream;
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldAddAzureArtifactsArtifactStream() {
    AzureArtifactsArtifactStream azureArtifactsArtifactStream =
        prepareAzureArtifactsArtifactStream(APP_ID, AzureArtifactsArtifactStreamProtocolType.maven);
    ArtifactStream savedArtifactSteam = createAzureArtifactsArtifactStream(azureArtifactsArtifactStream, APP_ID);
    AzureArtifactsArtifactStream savedAzureArtifactsArtifactStream = (AzureArtifactsArtifactStream) savedArtifactSteam;
    assertThat(savedAzureArtifactsArtifactStream.getPackageName()).isEqualTo(PACKAGE_NAME_MAVEN);
    verify(appService).getAccountIdByAppId(APP_ID);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldAddAzureArtifactsArtifactStreamAtConnectorLevel() {
    AzureArtifactsArtifactStream azureArtifactsArtifactStream =
        prepareAzureArtifactsArtifactStream(GLOBAL_APP_ID, AzureArtifactsArtifactStreamProtocolType.maven);
    ArtifactStream savedArtifactSteam = createAzureArtifactsArtifactStream(azureArtifactsArtifactStream, GLOBAL_APP_ID);
    AzureArtifactsArtifactStream savedAzureArtifactsArtifactStream = (AzureArtifactsArtifactStream) savedArtifactSteam;
    assertThat(savedAzureArtifactsArtifactStream.getPackageName()).isEqualTo(PACKAGE_NAME_MAVEN);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldUpdateAzureArtifactsArtifactStream() {
    AzureArtifactsArtifactStream azureArtifactsArtifactStream =
        prepareAzureArtifactsArtifactStream(APP_ID, AzureArtifactsArtifactStreamProtocolType.maven);
    ArtifactStream savedArtifactSteam = createAzureArtifactsArtifactStream(azureArtifactsArtifactStream, APP_ID);
    updateAndValidateAzureArtifactsArtifactStream((AzureArtifactsArtifactStream) savedArtifactSteam, APP_ID);
    verify(appService, times(2)).getAccountIdByAppId(APP_ID);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
    verify(artifactService).deleteByArtifactStreamId(APP_ID, savedArtifactSteam.getUuid());
    verify(triggerService).updateByArtifactStream(savedArtifactSteam.getUuid());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldUpdateAzureArtifactsArtifactStreamAtConnectorLevel() {
    AzureArtifactsArtifactStream azureArtifactsArtifactStream =
        prepareAzureArtifactsArtifactStream(GLOBAL_APP_ID, AzureArtifactsArtifactStreamProtocolType.maven);
    ArtifactStream savedArtifactSteam = createAzureArtifactsArtifactStream(azureArtifactsArtifactStream, GLOBAL_APP_ID);
    updateAndValidateAzureArtifactsArtifactStream((AzureArtifactsArtifactStream) savedArtifactSteam, GLOBAL_APP_ID);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
    verify(artifactService).deleteByArtifactStreamId(GLOBAL_APP_ID, savedArtifactSteam.getUuid());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotUpdateProtocolTypeAzureArtifactsArtifactStream() {
    AzureArtifactsArtifactStream azureArtifactsArtifactStream =
        prepareAzureArtifactsArtifactStream(APP_ID, AzureArtifactsArtifactStreamProtocolType.maven);
    ArtifactStream savedArtifactSteam = createAzureArtifactsArtifactStream(azureArtifactsArtifactStream, APP_ID);
    AzureArtifactsArtifactStream savedAzureArtifactsArtifactStream = (AzureArtifactsArtifactStream) savedArtifactSteam;
    savedAzureArtifactsArtifactStream.setProtocolType(AzureArtifactsArtifactStreamProtocolType.nuget.name());
    savedAzureArtifactsArtifactStream.setPackageName(PACKAGE_NAME_NUGET);
    artifactStreamService.update(savedAzureArtifactsArtifactStream);
  }

  private AzureArtifactsArtifactStream prepareAzureArtifactsArtifactStream(
      String appId, AzureArtifactsArtifactStreamProtocolType protocolType) {
    AzureArtifactsArtifactStream azureArtifactsArtifactStream = AzureArtifactsArtifactStream.builder()
                                                                    .accountId(ACCOUNT_ID)
                                                                    .appId(appId)
                                                                    .settingId(SETTING_ID)
                                                                    .autoPopulate(true)
                                                                    .serviceId(SERVICE_ID)
                                                                    .protocolType(protocolType.name())
                                                                    .project(null)
                                                                    .feed(FEED)
                                                                    .packageId(PACKAGE_ID)
                                                                    .build();
    if (AzureArtifactsArtifactStreamProtocolType.maven == protocolType) {
      azureArtifactsArtifactStream.setPackageName(PACKAGE_NAME_MAVEN);
    } else if (AzureArtifactsArtifactStreamProtocolType.nuget == protocolType) {
      azureArtifactsArtifactStream.setPackageName(PACKAGE_NAME_NUGET);
    }
    return azureArtifactsArtifactStream;
  }

  private ArtifactStream createAzureArtifactsArtifactStream(
      AzureArtifactsArtifactStream azureArtifactsArtifactStream, String appId) {
    ArtifactStream savedArtifactSteam = createArtifactStream(azureArtifactsArtifactStream);
    return validateAzureArtifactsArtifactStream(savedArtifactSteam, appId);
  }

  private void updateAndValidateAzureArtifactsArtifactStream(
      AzureArtifactsArtifactStream savedAzureArtifactsArtifactStream, String appId) {
    String protocolType = savedAzureArtifactsArtifactStream.getProtocolType();
    if (AzureArtifactsArtifactStreamProtocolType.maven.name().equals(protocolType)
        || AzureArtifactsArtifactStreamProtocolType.nuget.name().equals(protocolType)) {
      savedAzureArtifactsArtifactStream.setPackageName(savedAzureArtifactsArtifactStream.getPackageName() + "_tmp");
    }
    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedAzureArtifactsArtifactStream);
    validateUpdatedAzureArtifactsArtifactStream(updatedArtifactStream, appId);
  }

  private ArtifactStream validateAzureArtifactsArtifactStream(ArtifactStream savedArtifactStream, String appId) {
    assertThat(savedArtifactStream.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactStream.getUuid()).isNotEmpty();
    assertThat(savedArtifactStream.getName()).isNotEmpty();
    assertThat(savedArtifactStream.getArtifactStreamType()).isEqualTo(AZURE_ARTIFACTS.name());
    assertThat(savedArtifactStream.getAppId()).isEqualTo(appId);
    assertThat(savedArtifactStream).isInstanceOf(AzureArtifactsArtifactStream.class);
    ArtifactStreamAttributes artifactStreamAttributes =
        savedArtifactStream.fetchArtifactStreamAttributes(featureFlagService);
    assertThat(artifactStreamAttributes.getArtifactStreamType()).isEqualTo(AZURE_ARTIFACTS.name());
    assertThat(artifactStreamAttributes.getProtocolType())
        .isIn(AzureArtifactsArtifactStreamProtocolType.maven.name(),
            AzureArtifactsArtifactStreamProtocolType.nuget.name());
    assertThat(artifactStreamAttributes.getFeed()).isNotBlank();
    assertThat(artifactStreamAttributes.getPackageId()).isNotBlank();
    if (AzureArtifactsArtifactStreamProtocolType.maven.name().equals(artifactStreamAttributes.getProtocolType())
        || AzureArtifactsArtifactStreamProtocolType.nuget.name().equals(artifactStreamAttributes.getProtocolType())) {
      assertThat(artifactStreamAttributes.getPackageName()).isNotBlank();
    }
    assertThat(savedArtifactStream.getCollectionStatus()).isEqualTo(UNSTABLE.name());
    return savedArtifactStream;
  }

  private void validateUpdatedAzureArtifactsArtifactStream(ArtifactStream updatedArtifactStream, String appId) {
    validateAzureArtifactsArtifactStream(updatedArtifactStream, appId);
    ArtifactStreamAttributes artifactStreamAttributes =
        updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService);
    String protocolType = artifactStreamAttributes.getProtocolType();
    if (AzureArtifactsArtifactStreamProtocolType.maven.name().equals(protocolType)
        || AzureArtifactsArtifactStreamProtocolType.nuget.name().equals(protocolType)) {
      assertThat(artifactStreamAttributes.getPackageName()).endsWith("_tmp");
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListArtifactStreams() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStream();
    ArtifactStream savedJenkinsArtifactSteam = createArtifactStream(jenkinsArtifactStream);

    assertThat(savedJenkinsArtifactSteam).isNotNull();

    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .metadataOnly(true)
                                                              .build();

    ArtifactStream savedArtifactStream = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactStream).isNotNull();

    List<String> artifactStreamIds = asList(savedJenkinsArtifactSteam.getUuid(), savedArtifactStream.getUuid());
    Service service = Service.builder().artifactStreamIds(artifactStreamIds).build();
    when(serviceResourceService.findServicesByApp(APP_ID)).thenReturn(Collections.singletonList(service));
    when(artifactStreamServiceBindingService.listArtifactStreamIds(service)).thenReturn(artifactStreamIds);

    List<ArtifactStream> artifactStreams = artifactStreamService.listByAppId(APP_ID);
    assertThat(artifactStreams).isNotEmpty().size().isEqualTo(2);
    assertThat(artifactStreams)
        .extracting(ArtifactStream::getArtifactStreamType)
        .contains(ArtifactStreamType.JENKINS.name(), ArtifactStreamType.ARTIFACTORY.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldListArtifactStreamsAtConnectorLevel() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStreamAtConnectorLevel();
    ArtifactStream savedJenkinsArtifactStream = createArtifactStream(jenkinsArtifactStream);

    assertThat(savedJenkinsArtifactStream).isNotNull();

    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(GLOBAL_APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(ANOTHER_SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .metadataOnly(true)
                                                              .build();

    ArtifactStream savedArtifactStream = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactStream).isNotNull();

    List<ArtifactStream> artifactStreams = artifactStreamService.listBySettingId(SETTING_ID);
    assertThat(artifactStreams).isNotEmpty().size().isEqualTo(1);
    assertThat(artifactStreams)
        .extracting(ArtifactStream::getArtifactStreamType)
        .contains(ArtifactStreamType.JENKINS.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStream() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .metadataOnly(true)
                                                              .build();

    ArtifactStream savedArtifactStream = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactStream).isNotNull();

    ArtifactStream artifactStream = artifactStreamService.get(savedArtifactStream.getUuid());
    assertThat(artifactStream.getUuid()).isEqualTo(savedArtifactStream.getUuid());
    assertThat(artifactStream.getName()).isNotEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetArtifactStreamAtConnectorLevel() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStreamAtConnectorLevel();
    ArtifactStream savedArtifactStream = createArtifactStream(jenkinsArtifactStream);
    assertThat(savedArtifactStream).isNotNull();
    assertThat(savedArtifactStream.getAppId()).isEqualTo(GLOBAL_APP_ID);
    ArtifactStream artifactStream = artifactStreamService.get(savedArtifactStream.getUuid());
    assertThat(artifactStream.getUuid()).isEqualTo(savedArtifactStream.getUuid());
    assertThat(artifactStream.getName()).isNotEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDeleteArtifactStreamAtConnectorLevel() {
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStreamAtConnectorLevel();
    ArtifactStream savedArtifactStream = createArtifactStream(jenkinsArtifactStream);
    assertThat(savedArtifactStream).isNotNull();
    assertThat(artifactStreamService.delete(savedArtifactStream.getUuid(), false)).isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteArtifactStream() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .metadataOnly(true)
                                                              .build();

    ArtifactStream savedArtifactStream = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactStream).isNotNull();
    assertThat(artifactStreamService.delete(APP_ID, savedArtifactStream.getUuid())).isTrue();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotDeleteArtifactStream() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .build();

    ArtifactStream savedArtifactStream = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactStream).isNotNull();

    when(triggerService.getTriggersHasArtifactStreamAction(APP_ID, savedArtifactStream.getUuid()))
        .thenReturn(
            Collections.singletonList(software.wings.beans.trigger.Trigger.builder().name(TRIGGER_NAME).build()));
    assertThat(artifactStreamService.delete(APP_ID, savedArtifactStream.getUuid())).isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetDockerArtifactStreamSourceProperties() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(DockerConfig.builder()
                        .dockerRegistryUrl("http://hub.docker.com/")
                        .username("username")
                        .password("password".toCharArray())
                        .accountId(ACCOUNT_ID)
                        .build());
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(dockerArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(DOCKER.name());
    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, savedArtifactSteam.getUuid());
    assertThat(artifactSourceProperties).isNotEmpty();
    assertThat(artifactSourceProperties)
        .containsKeys(ARTIFACT_SOURCE_USER_NAME_KEY, ARTIFACT_SOURCE_REGISTRY_URL_KEY,
            ARTIFACT_SOURCE_REPOSITORY_NAME_KEY, ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY);
    assertThat(artifactSourceProperties).containsValues("username", "http://hub.docker.com/", "wingsplugins/todolist");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetDockerArtifactSourcePropertiesWhenArtifactStreamDeleted() {
    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, ARTIFACT_STREAM_ID);
    assertThat(artifactSourceProperties).isEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetGcrArtifactStreamSourceProperties() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(GcpConfig.builder().accountId(ACCOUNT_ID).build());
    GcrArtifactStream gcrArtifactStream = GcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .settingId(SETTING_ID)
                                              .dockerImageName("exploration-161417/todolist")
                                              .registryHostName("gcr.io")
                                              .autoPopulate(true)
                                              .serviceId(SERVICE_ID)
                                              .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(gcrArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, savedArtifactSteam.getUuid());
    assertThat(artifactSourceProperties).isNotEmpty();
    assertThat(artifactSourceProperties)
        .containsOnlyKeys(ARTIFACT_SOURCE_REGISTRY_URL_KEY, ARTIFACT_SOURCE_REPOSITORY_NAME_KEY,
            ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY);
    assertThat(artifactSourceProperties).containsValues("gcr.io", "exploration-161417/todolist");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetAcrArtifactStreamSourceProperties() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(AzureConfig.builder().accountId(ACCOUNT_ID).build());

    AcrArtifactStream acrArtifactStream = AcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .settingId(SETTING_ID)
                                              .subscriptionId("20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0")
                                              .repositoryName("nginx")
                                              .registryName("harnessqa")
                                              .autoPopulate(true)
                                              .serviceId(SERVICE_ID)
                                              .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(acrArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, savedArtifactSteam.getUuid());
    assertThat(artifactSourceProperties).isNotEmpty();
    assertThat(artifactSourceProperties)
        .containsOnlyKeys(ARTIFACT_SOURCE_REGISTRY_URL_KEY, ARTIFACT_SOURCE_REPOSITORY_NAME_KEY,
            ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY);
    assertThat(artifactSourceProperties).containsValues("harnessqa", "nginx");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetEcrArtifactStreamSourceProperties() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(AzureConfig.builder().accountId(ACCOUNT_ID).build());

    EcrArtifactStream ecrArtifactStream = EcrArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .settingId(SETTING_ID)
                                              .imageName("todolist")
                                              .region("us-east-1")
                                              .autoPopulate(true)
                                              .serviceId(SERVICE_ID)
                                              .metadataOnly(true)
                                              .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(ecrArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, savedArtifactSteam.getUuid());
    assertThat(artifactSourceProperties).isNotEmpty();
    assertThat(artifactSourceProperties)
        .containsOnlyKeys(ARTIFACT_SOURCE_REPOSITORY_NAME_KEY, ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY);
    assertThat(artifactSourceProperties).containsValues("todolist");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetJenkinsArtifactStreamSourceProperties() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(JenkinsConfig.builder()
                        .jenkinsUrl("http://jenkins.software")
                        .username("username")
                        .accountId(ACCOUNT_ID)
                        .build());

    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsStream();
    ArtifactStream savedArtifactSteam = createArtifactStream(jenkinsArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();

    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, savedArtifactSteam.getUuid());
    assertThat(artifactSourceProperties).isNotEmpty();
    assertThat(artifactSourceProperties)
        .containsOnlyKeys(
            ARTIFACT_SOURCE_USER_NAME_KEY, ARTIFACT_SOURCE_REGISTRY_URL_KEY, ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY);
    assertThat(artifactSourceProperties).containsValues("username", "http://jenkins.software");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetBabmooArtifactStreamSourceProperties() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(JenkinsConfig.builder()
                        .jenkinsUrl("http://bamboo.software")
                        .username("username")
                        .accountId(ACCOUNT_ID)
                        .build());

    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .jobname("TOD-TOD")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .metadataOnly(true)
                                                    .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(bambooArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();

    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, savedArtifactSteam.getUuid());
    assertThat(artifactSourceProperties).isNotEmpty();
    assertThat(artifactSourceProperties)
        .containsOnlyKeys(
            ARTIFACT_SOURCE_USER_NAME_KEY, ARTIFACT_SOURCE_REGISTRY_URL_KEY, ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY);
    assertThat(artifactSourceProperties).containsValues("username", "http://bamboo.software");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetNexusArtifactStreamSourceProperties() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(NexusConfig.builder()
                        .nexusUrl("http://bamboo.software")
                        .username("username")
                        .accountId(ACCOUNT_ID)
                        .build());

    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("todolist"))
                                                  .autoPopulate(true)
                                                  .serviceId(SERVICE_ID)
                                                  .metadataOnly(true)
                                                  .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(nexusArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();

    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, savedArtifactSteam.getUuid());
    assertThat(artifactSourceProperties).isNotEmpty();
    assertThat(artifactSourceProperties)
        .containsOnlyKeys(
            ARTIFACT_SOURCE_USER_NAME_KEY, ARTIFACT_SOURCE_REGISTRY_URL_KEY, ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY);
    assertThat(artifactSourceProperties).containsValues("username", "http://bamboo.software");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotAddNexusNonMetadataArtifactStream() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("todolist"))
                                                  .autoPopulate(true)
                                                  .serviceId(SERVICE_ID)
                                                  .metadataOnly(false)
                                                  .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(nexusArtifactStream);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotUpdateNexusMetadataOnlyArtifactStreamToFalse() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("todolist"))
                                                  .autoPopulate(true)
                                                  .serviceId(SERVICE_ID)
                                                  .metadataOnly(true)
                                                  .build();
    NexusArtifactStream savedNexusArtifactSteam = (NexusArtifactStream) createArtifactStream(nexusArtifactStream);
    savedNexusArtifactSteam.setMetadataOnly(false);
    artifactStreamService.update(savedNexusArtifactSteam);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetNexusDockerArtifactStreamSourceProperties() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(NexusConfig.builder()
                        .nexusUrl("http://bamboo.software")
                        .username("username")
                        .accountId(ACCOUNT_ID)
                        .version("3.x")
                        .build());

    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .imageName("wingsplugins/todolist")
                                                  .autoPopulate(true)
                                                  .serviceId(SERVICE_ID)
                                                  .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(nexusArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();

    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, savedArtifactSteam.getUuid());
    assertThat(artifactSourceProperties).isNotEmpty();
    assertThat(artifactSourceProperties)
        .containsOnlyKeys(ARTIFACT_SOURCE_USER_NAME_KEY, ARTIFACT_SOURCE_REGISTRY_URL_KEY,
            ARTIFACT_SOURCE_REPOSITORY_NAME_KEY, ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY);
    assertThat(artifactSourceProperties).containsValues("username", "http://bamboo.software", "wingsplugins/todolist");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactoryArtifactStreamSourceProperties() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(NexusConfig.builder()
                        .nexusUrl("http://artifactory.com")
                        .username("username")
                        .accountId(ACCOUNT_ID)
                        .build());

    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .metadataOnly(true)
                                                              .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();

    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, savedArtifactSteam.getUuid());
    assertThat(artifactSourceProperties).isNotEmpty();
    assertThat(artifactSourceProperties)
        .containsOnlyKeys(
            ARTIFACT_SOURCE_USER_NAME_KEY, ARTIFACT_SOURCE_REGISTRY_URL_KEY, ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY);
    assertThat(artifactSourceProperties).containsValues("username", "http://artifactory.com");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactoryDockerArtifactStreamSourceProperties() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(NexusConfig.builder()
                        .nexusUrl("http://artifactory.com")
                        .username("username")
                        .accountId(ACCOUNT_ID)
                        .build());

    ArtifactoryArtifactStream artifactoryArtifactStream = buildArtifactoryStream();
    ArtifactStream savedArtifactSteam = createArtifactStream(artifactoryArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();

    Map<String, String> artifactSourceProperties =
        artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, savedArtifactSteam.getUuid());
    assertThat(artifactSourceProperties).isNotEmpty();
    assertThat(artifactSourceProperties)
        .containsOnlyKeys(ARTIFACT_SOURCE_USER_NAME_KEY, ARTIFACT_SOURCE_REGISTRY_URL_KEY,
            ARTIFACT_SOURCE_REPOSITORY_NAME_KEY, ARTIFACT_SOURCE_DOCKER_CONFIG_NAME_KEY);
    assertThat(artifactSourceProperties).containsValues("username", "http://artifactory.com", "wingsplugins/todolist");
  }

  private ArtifactoryArtifactStream buildArtifactoryStream() {
    return ArtifactoryArtifactStream.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .repositoryType("any")
        .jobname("docker")
        .settingId(SETTING_ID)
        .imageName("wingsplugins/todolist")
        .autoPopulate(true)
        .serviceId(SERVICE_ID)
        .metadataOnly(true)
        .build();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListArtifactStreamIdsofService() {
    ArtifactStream savedArtifactSteam = createArtifactStream(buildArtifactoryStream());
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(artifactStreamService.fetchArtifactStreamIdsForService(APP_ID, SERVICE_ID))
        .isNotEmpty()
        .contains(savedArtifactSteam.getUuid());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListArtifactStreamsofService() {
    ArtifactStream savedArtifactSteam = createArtifactStream(buildArtifactoryStream());
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(artifactStreamService.fetchArtifactStreamsForService(APP_ID, SERVICE_ID))
        .isNotEmpty()
        .extracting(ArtifactStream::getUuid)
        .isNotNull()
        .contains(savedArtifactSteam.getUuid());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCRUDCustomArtifactStream() {
    ArtifactStream customArtifactStream = CustomArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .serviceId(SERVICE_ID)
                                              .name("Custom Artifact Stream" + System.currentTimeMillis())
                                              .scripts(Arrays.asList(CustomArtifactStream.Script.builder()
                                                                         .action(Action.FETCH_VERSIONS)
                                                                         .scriptString(SCRIPT_STRING)
                                                                         .build()))
                                              .build();

    ArtifactStream savedArtifactSteam = createArtifactStream(customArtifactStream);

    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo(savedArtifactSteam.getName());
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ArtifactStreamType.CUSTOM.name());
    CustomArtifactStream savedCustomArtifactStream = (CustomArtifactStream) savedArtifactSteam;
    assertThat(savedCustomArtifactStream.getScripts()).isNotEmpty();
    Script script = savedCustomArtifactStream.getScripts().stream().findFirst().orElse(null);
    assertThat(script.getAction()).isEqualTo(Action.FETCH_VERSIONS);
    assertThat(script.getScriptString()).isEqualTo(SCRIPT_STRING);

    assertThat(artifactStreamService.fetchArtifactStreamsForService(APP_ID, SERVICE_ID))
        .isNotEmpty()
        .extracting(ArtifactStream::getUuid)
        .isNotNull()
        .contains(savedArtifactSteam.getUuid());

    script.setScriptString("Welcome to harness");
    savedCustomArtifactStream.setScripts(Arrays.asList(script));
    savedCustomArtifactStream.setName("Name Changed");

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedArtifactSteam);

    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo(updatedArtifactStream.getName());
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(ArtifactStreamType.CUSTOM.name());
    CustomArtifactStream updatedCustomArtifactStream = (CustomArtifactStream) savedArtifactSteam;
    Script updatedScript = updatedCustomArtifactStream.getScripts().stream().findFirst().orElse(null);
    assertThat(updatedScript.getScriptString()).isEqualTo("Welcome to harness");

    artifactStreamService.delete(APP_ID, updatedArtifactStream.getUuid());
    verify(alertService).deleteByArtifactStream(any(), eq(updatedArtifactStream.getUuid()));
    verify(artifactStreamServiceBindingService).deleteByArtifactStream(eq(updatedArtifactStream.getUuid()), eq(false));

    assertThat(artifactStreamService.get(updatedArtifactStream.getUuid())).isNull();

    verify(artifactService, times(0)).deleteByArtifactStreamId(anyString(), anyString());
    verify(triggerService).updateByArtifactStream(updatedArtifactStream.getUuid());
    verify(buildSourceService, times(1)).validateArtifactSource(savedArtifactSteam);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldCRUDCustomArtifactStreamWithCustomMapping() {
    List<CustomRepositoryMapping.AttributeMapping> attributeMapping = new ArrayList<>();
    attributeMapping.add(builder().relativePath("assets.downloadUrl").mappedAttribute("metadata.downloadUrl").build());
    CustomRepositoryMapping mapping = CustomRepositoryMapping.builder()
                                          .artifactRoot("$.items")
                                          .buildNoPath("version")
                                          .artifactAttributes(attributeMapping)
                                          .build();
    ArtifactStream customArtifactStream = CustomArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .serviceId(SERVICE_ID)
                                              .name("Custom Artifact Stream" + System.currentTimeMillis())
                                              .scripts(Arrays.asList(CustomArtifactStream.Script.builder()
                                                                         .action(Action.FETCH_VERSIONS)
                                                                         .scriptString(SCRIPT_STRING)
                                                                         .customRepositoryMapping(mapping)
                                                                         .build()))
                                              .build();

    ArtifactStream savedArtifactSteam = createArtifactStream(customArtifactStream);

    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo(savedArtifactSteam.getName());
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ArtifactStreamType.CUSTOM.name());
    CustomArtifactStream savedCustomArtifactStream = (CustomArtifactStream) savedArtifactSteam;
    assertThat(savedCustomArtifactStream.getScripts()).isNotEmpty();
    Script script = savedCustomArtifactStream.getScripts().stream().findFirst().orElse(null);
    assertThat(script.getAction()).isEqualTo(Action.FETCH_VERSIONS);
    assertThat(script.getScriptString()).isEqualTo(SCRIPT_STRING);
    assertThat(script.getCustomRepositoryMapping()).isNotNull();
    assertThat(script.getCustomRepositoryMapping().getBuildNoPath()).isEqualTo("version");
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes().size()).isEqualTo(1);
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes())
        .extracting("mappedAttribute")
        .contains("metadata.downloadUrl");

    assertThat(artifactStreamService.fetchArtifactStreamsForService(APP_ID, SERVICE_ID))
        .isNotEmpty()
        .extracting(ArtifactStream::getUuid)
        .isNotNull()
        .contains(savedArtifactSteam.getUuid());

    attributeMapping = new ArrayList<>();
    attributeMapping.add(builder().relativePath("assets.path").mappedAttribute("metadata.path").build());
    mapping = CustomRepositoryMapping.builder()
                  .artifactRoot("$.items")
                  .buildNoPath("version")
                  .artifactAttributes(attributeMapping)
                  .build();
    script.setCustomRepositoryMapping(mapping);
    script.setScriptString("Welcome to harness");
    savedCustomArtifactStream.setScripts(Arrays.asList(script));
    savedCustomArtifactStream.setName("Name Changed");

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedArtifactSteam);

    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo(updatedArtifactStream.getName());
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(ArtifactStreamType.CUSTOM.name());
    CustomArtifactStream updatedCustomArtifactStream = (CustomArtifactStream) savedArtifactSteam;
    Script updatedScript = updatedCustomArtifactStream.getScripts().stream().findFirst().orElse(null);
    assertThat(updatedScript.getScriptString()).isEqualTo("Welcome to harness");
    assertThat(script.getCustomRepositoryMapping()).isNotNull();
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes().size()).isEqualTo(1);
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes())
        .extracting("mappedAttribute")
        .contains("metadata.path");
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes())
        .extracting("mappedAttribute")
        .doesNotContain("metadata.downloadUrl");

    artifactStreamService.delete(APP_ID, updatedArtifactStream.getUuid());

    assertThat(artifactStreamService.get(updatedArtifactStream.getUuid())).isNull();

    verify(artifactService, times(0)).deleteByArtifactStreamId(anyString(), anyString());
    verify(triggerService).updateByArtifactStream(updatedArtifactStream.getUuid());
    verify(buildSourceService, times(1)).validateArtifactSource(savedArtifactSteam);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldThrowExceptionIfCustomArtifactSourceScriptHasErrors() {
    ArtifactStream customArtifactStream = CustomArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .serviceId(SERVICE_ID)
                                              .name("Custom Artifact Stream" + System.currentTimeMillis())
                                              .scripts(Arrays.asList(CustomArtifactStream.Script.builder()
                                                                         .action(Action.FETCH_VERSIONS)
                                                                         .scriptString(SCRIPT_STRING_WITH_ERROR)
                                                                         .build()))
                                              .build();

    when(buildSourceService.validateArtifactSource(customArtifactStream))
        .thenThrow(new ShellExecutionException("script error"));
    assertThatThrownBy(() -> createArtifactStream(customArtifactStream))
        .isInstanceOf(ShellExecutionException.class)
        .hasMessage(
            "Custom Artifact script execution failed with following error: script error, Please verify the script.");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateLinkedCustomArtifactStreamByUpdatingOnlyVariables() {
    // create Custom artifact stream by linking from template library
    List<CustomRepositoryMapping.AttributeMapping> attributeMapping = new ArrayList<>();
    attributeMapping.add(builder().relativePath("assets.downloadUrl").mappedAttribute("metadata.downloadUrl").build());
    CustomRepositoryMapping mapping = CustomRepositoryMapping.builder()
                                          .artifactRoot("$.results")
                                          .buildNoPath("name")
                                          .artifactAttributes(attributeMapping)
                                          .build();
    ArtifactStream customArtifactStream = createCustomArtifactStreamFromTemplate(mapping);

    // mock get template call
    CustomArtifactStream customArtifactStreamFromTemplate = CustomArtifactStream.builder()
                                                                .scripts(asList(CustomArtifactStream.Script.builder()
                                                                                    .scriptString(SCRIPT_STRING)
                                                                                    .customRepositoryMapping(mapping)
                                                                                    .build()))
                                                                .build();
    customArtifactStreamFromTemplate.setTemplateUuid(TEMPLATE_ID);
    customArtifactStreamFromTemplate.setTemplateVersion(TEMPLATE_VERSION);
    customArtifactStreamFromTemplate.setTemplateVariables(
        asList(aVariable().name("var1").value("default value").type(TEXT).build()));

    when(templateService.constructEntityFromTemplate(eq(TEMPLATE_ID), anyString(), any()))
        .thenReturn(customArtifactStreamFromTemplate);

    ArtifactStream savedArtifactSteam = createArtifactStream(customArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo(savedArtifactSteam.getName());
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ArtifactStreamType.CUSTOM.name());
    assertThat(savedArtifactSteam.getTemplateUuid()).isEqualTo(TEMPLATE_ID);
    assertThat(savedArtifactSteam.getTemplateVariables().get(0).getValue()).isEqualTo("overridden value");
    CustomArtifactStream savedCustomArtifactStream = (CustomArtifactStream) savedArtifactSteam;
    assertThat(savedCustomArtifactStream.getScripts()).isNotEmpty();
    Script script = savedCustomArtifactStream.getScripts().stream().findFirst().orElse(null);
    assertThat(script.getAction()).isEqualTo(Action.FETCH_VERSIONS);
    assertThat(script.getScriptString()).isEqualTo(SCRIPT_STRING);
    assertThat(script.getCustomRepositoryMapping()).isNotNull();
    assertThat(script.getCustomRepositoryMapping().getBuildNoPath()).isEqualTo("name");
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes().size()).isEqualTo(1);
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes())
        .extracting("mappedAttribute")
        .contains("metadata.downloadUrl");

    assertThat(artifactStreamService.fetchArtifactStreamsForService(APP_ID, SERVICE_ID))
        .isNotEmpty()
        .extracting(ArtifactStream::getUuid)
        .isNotNull()
        .contains(savedArtifactSteam.getUuid());

    // update custom artifact stream by updating the template variable values in custom artifact stream.
    savedArtifactSteam.setTemplateVariables(
        asList(aVariable().name("var1").value("another overridden value").type(TEXT).build()));
    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedArtifactSteam);

    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo(updatedArtifactStream.getName());
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(ArtifactStreamType.CUSTOM.name());
    CustomArtifactStream updatedCustomArtifactStream = (CustomArtifactStream) savedArtifactSteam;
    Script updatedScript = updatedCustomArtifactStream.getScripts().stream().findFirst().orElse(null);
    assertThat(updatedScript.getScriptString()).isEqualTo(SCRIPT_STRING);
    assertThat(script.getCustomRepositoryMapping()).isNotNull();
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes().size()).isEqualTo(1);
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes())
        .extracting("mappedAttribute")
        .contains("metadata.downloadUrl");
    assertThat(savedArtifactSteam.getTemplateUuid()).isEqualTo(TEMPLATE_ID);
    assertThat(savedArtifactSteam.getTemplateVariables().get(0).getValue()).isEqualTo("another overridden value");
    verify(triggerService, times(0)).updateByArtifactStream(updatedArtifactStream.getUuid());

    artifactStreamService.delete(APP_ID, updatedArtifactStream.getUuid());
    assertThat(artifactStreamService.get(updatedArtifactStream.getUuid())).isNull();

    verify(artifactService, times(0)).deleteByArtifactStreamId(anyString(), anyString());
    verify(buildSourceService, times(1)).validateArtifactSource(savedArtifactSteam);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateLinkedCustomArtifactStreamByUpdatingScript() {
    // create Custom artifact stream by linking from template library
    List<CustomRepositoryMapping.AttributeMapping> attributeMapping = new ArrayList<>();
    attributeMapping.add(builder().relativePath("assets.downloadUrl").mappedAttribute("metadata.downloadUrl").build());
    CustomRepositoryMapping mapping = CustomRepositoryMapping.builder()
                                          .artifactRoot("$.results")
                                          .buildNoPath("name")
                                          .artifactAttributes(attributeMapping)
                                          .build();
    ArtifactStream customArtifactStream = createCustomArtifactStreamFromTemplate(mapping);

    // mock get template call - to simulate template update (script update)
    CustomArtifactStream customArtifactStreamFromTemplate = CustomArtifactStream.builder()
                                                                .scripts(asList(CustomArtifactStream.Script.builder()
                                                                                    .scriptString(SCRIPT_STRING)
                                                                                    .customRepositoryMapping(mapping)
                                                                                    .build()))
                                                                .build();
    customArtifactStreamFromTemplate.setTemplateUuid(TEMPLATE_ID);
    customArtifactStreamFromTemplate.setTemplateVersion(TEMPLATE_VERSION);
    customArtifactStreamFromTemplate.setTemplateVariables(
        asList(aVariable().name("var1").value("default value").type(TEXT).build()));

    when(templateService.constructEntityFromTemplate(eq(TEMPLATE_ID), anyString(), any()))
        .thenReturn(customArtifactStreamFromTemplate);

    ArtifactStream savedArtifactSteam = createArtifactStream(customArtifactStream);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo(savedArtifactSteam.getName());
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ArtifactStreamType.CUSTOM.name());
    assertThat(savedArtifactSteam.getTemplateUuid()).isEqualTo(TEMPLATE_ID);
    assertThat(savedArtifactSteam.getTemplateVariables().get(0).getValue()).isEqualTo("overridden value");
    CustomArtifactStream savedCustomArtifactStream = (CustomArtifactStream) savedArtifactSteam;
    assertThat(savedCustomArtifactStream.getScripts()).isNotEmpty();
    Script script = savedCustomArtifactStream.getScripts().stream().findFirst().orElse(null);
    assertThat(script.getAction()).isEqualTo(Action.FETCH_VERSIONS);
    assertThat(script.getScriptString()).isEqualTo(SCRIPT_STRING);
    assertThat(script.getCustomRepositoryMapping()).isNotNull();
    assertThat(script.getCustomRepositoryMapping().getBuildNoPath()).isEqualTo("name");
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes().size()).isEqualTo(1);
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes())
        .extracting("mappedAttribute")
        .contains("metadata.downloadUrl");

    assertThat(artifactStreamService.fetchArtifactStreamsForService(APP_ID, SERVICE_ID))
        .isNotEmpty()
        .extracting(ArtifactStream::getUuid)
        .isNotNull()
        .contains(savedArtifactSteam.getUuid());

    // update custom artifact stream by updating the template variable values and script string.
    savedCustomArtifactStream.getScripts().get(0).setScriptString(SCRIPT_STRING_UPDATED);
    savedArtifactSteam.setTemplateVariables(
        asList(aVariable().name("var1").value("another overridden value").type(TEXT).build()));
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE, ACCOUNT_ID)).thenReturn(true);
    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedArtifactSteam, false, true);

    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getSourceName()).isEqualTo(updatedArtifactStream.getName());
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(ArtifactStreamType.CUSTOM.name());
    assertThat(updatedArtifactStream.getCollectionStatus()).isEqualTo(UNSTABLE.name());
    CustomArtifactStream updatedCustomArtifactStream = (CustomArtifactStream) savedArtifactSteam;
    Script updatedScript = updatedCustomArtifactStream.getScripts().stream().findFirst().orElse(null);
    assertThat(updatedScript.getScriptString()).isEqualTo(SCRIPT_STRING_UPDATED);
    assertThat(script.getCustomRepositoryMapping()).isNotNull();
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes().size()).isEqualTo(1);
    assertThat(script.getCustomRepositoryMapping().getArtifactAttributes())
        .extracting("mappedAttribute")
        .contains("metadata.downloadUrl");
    assertThat(savedArtifactSteam.getTemplateUuid()).isEqualTo(TEMPLATE_ID);
    assertThat(savedArtifactSteam.getTemplateVariables().get(0).getValue()).isEqualTo("another overridden value");
    verify(triggerService, times(0)).updateByArtifactStream(updatedArtifactStream.getUuid());

    artifactStreamService.delete(APP_ID, updatedArtifactStream.getUuid());
    assertThat(artifactStreamService.get(updatedArtifactStream.getUuid())).isNull();

    verify(artifactService, times(0)).deleteByArtifactStreamId(anyString(), anyString());
    verify(buildSourceService, times(0)).validateArtifactSource(savedArtifactSteam);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testLinkedCustomArtifactStreamWhenVersionAndVariablesUpdated() {
    // existing artifact stream
    List<CustomRepositoryMapping.AttributeMapping> attributeMapping = new ArrayList<>();
    attributeMapping.add(builder().relativePath("path").mappedAttribute("${path}").build());
    attributeMapping.add(builder().relativePath("size").mappedAttribute("${size}").build());
    CustomRepositoryMapping mapping = CustomRepositoryMapping.builder()
                                          .artifactRoot("$.results")
                                          .buildNoPath("path")
                                          .artifactAttributes(attributeMapping)
                                          .build();
    CustomArtifactStream existingArtifactStream = CustomArtifactStream.builder()
                                                      .scripts(asList(CustomArtifactStream.Script.builder()
                                                                          .scriptString("some script")
                                                                          .customRepositoryMapping(mapping)
                                                                          .build()))
                                                      .name("test")
                                                      .serviceId(SERVICE_ID)
                                                      .uuid(ARTIFACT_STREAM_ID)
                                                      .appId(APP_ID)
                                                      .build();
    existingArtifactStream.setTemplateUuid(TEMPLATE_ID);
    existingArtifactStream.setTemplateVersion("latest");
    existingArtifactStream.setTemplateVariables(asList(aVariable().name("path").value("p11").type(TEXT).build(),
        aVariable().name("size").value("s11").type(TEXT).build()));

    when(templateService.constructEntityFromTemplate(anyString(), anyString(), any()))
        .thenReturn(existingArtifactStream);

    createArtifactStream(existingArtifactStream);

    // Incoming artifact stream
    attributeMapping = new ArrayList<>();
    attributeMapping.add(builder().relativePath("path").mappedAttribute("${path}").build());
    attributeMapping.add(builder().relativePath("size").mappedAttribute("size").build());
    mapping = CustomRepositoryMapping.builder()
                  .artifactRoot("$.results")
                  .buildNoPath("path")
                  .artifactAttributes(attributeMapping)
                  .build();
    CustomArtifactStream artifactStream = CustomArtifactStream.builder()
                                              .scripts(asList(CustomArtifactStream.Script.builder()
                                                                  .scriptString(SCRIPT_STRING)
                                                                  .customRepositoryMapping(mapping)
                                                                  .build()))
                                              .name("test")
                                              .serviceId(SERVICE_ID)
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .appId(APP_ID)
                                              .build();
    artifactStream.setTemplateUuid(TEMPLATE_ID);
    artifactStream.setTemplateVersion("2");
    artifactStream.setTemplateVariables(asList(aVariable().name("path").value("pp11").type(TEXT).build()));

    // from template
    attributeMapping = new ArrayList<>();
    attributeMapping.add(builder().relativePath("path").mappedAttribute("${path}").build());
    attributeMapping.add(builder().relativePath("size").mappedAttribute("size").build());
    mapping = CustomRepositoryMapping.builder()
                  .artifactRoot("$.results")
                  .buildNoPath("path")
                  .artifactAttributes(attributeMapping)
                  .build();
    CustomArtifactStream artifactStreamFromTemplate = CustomArtifactStream.builder()
                                                          .scripts(asList(CustomArtifactStream.Script.builder()
                                                                              .scriptString("some script")
                                                                              .customRepositoryMapping(mapping)
                                                                              .build()))
                                                          .name("test")
                                                          .serviceId(SERVICE_ID)
                                                          .uuid(ARTIFACT_STREAM_ID)
                                                          .appId(APP_ID)
                                                          .build();
    artifactStreamFromTemplate.setTemplateUuid(TEMPLATE_ID);
    artifactStreamFromTemplate.setTemplateVersion("2");
    artifactStreamFromTemplate.setTemplateVariables(asList(aVariable().name("path").value("").type(TEXT).build()));

    when(templateService.constructEntityFromTemplate(anyString(), anyString(), any()))
        .thenReturn(artifactStreamFromTemplate);

    CustomArtifactStream updatedStream =
        (CustomArtifactStream) artifactStreamService.update(artifactStream, false, false);
    assertThat(updatedStream).isNotNull();
    assertThat(updatedStream.getTemplateVersion()).isEqualTo("2");
    assertThat(updatedStream.getTemplateVariables().size()).isEqualTo(1);
    assertThat(updatedStream.getTemplateVariables())
        .extracting(Variable::getName, Variable::getValue)
        .contains(tuple("path", "pp11"));
    verify(buildSourceService, times(0)).validateArtifactSource(artifactStream);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testThrowExceptionWhenInvalidSettingForArtifactStream() {
    when(settingsService.get(eq("DOCKER_SETTING_ID")))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(new DockerConfig()).build());
    when(settingsService.get(eq("AZURE_SETTING_ID")))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(new AzureConfig()).build());

    // existing artifact stream
    ArtifactStream existingArtifactStream =
        AzureMachineImageArtifactStream.builder()
            .osType(AzureMachineImageArtifactStream.OSType.LINUX)
            .imageType(IMAGE_GALLERY)
            .subscriptionId("subId")
            .imageDefinition(AzureMachineImageArtifactStream.ImageDefinition.builder()
                                 .resourceGroup("resourceGroup")
                                 .imageGalleryName("galleryName")
                                 .imageDefinitionName("definitionName")
                                 .build())
            .name("test")
            .settingId("AZURE_SETTING_ID")
            .serviceId(SERVICE_ID)
            .uuid(ARTIFACT_STREAM_ID)
            .appId(APP_ID)
            .build();

    createArtifactStream(existingArtifactStream);

    // Incoming artifact stream
    ArtifactStream artifactStream = AzureMachineImageArtifactStream.builder()
                                        .osType(AzureMachineImageArtifactStream.OSType.LINUX)
                                        .imageType(IMAGE_GALLERY)
                                        .subscriptionId("subId")
                                        .imageDefinition(AzureMachineImageArtifactStream.ImageDefinition.builder()
                                                             .resourceGroup("resourceGroup")
                                                             .imageGalleryName("galleryName")
                                                             .imageDefinitionName("definitionName")
                                                             .build())
                                        .name("test")
                                        .settingId("DOCKER_SETTING_ID")
                                        .serviceId(SERVICE_ID)
                                        .uuid(ARTIFACT_STREAM_ID)
                                        .appId(APP_ID)
                                        .build();

    assertThatThrownBy(() -> artifactStreamService.update(artifactStream, true, false))
        .isInstanceOf(InvalidRequestException.class)
        .extracting("detailMessage")
        .isEqualTo("Invalid setting type DOCKER for artifact stream type AZURE_MACHINE_IMAGE");
  }

  @NotNull
  private ArtifactStream createCustomArtifactStreamFromTemplate(CustomRepositoryMapping mapping) {
    ArtifactStream customArtifactStream = CustomArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .serviceId(SERVICE_ID)
                                              .name("Custom Artifact Stream" + System.currentTimeMillis())
                                              .scripts(Arrays.asList(Script.builder()
                                                                         .action(Action.FETCH_VERSIONS)
                                                                         .scriptString(SCRIPT_STRING)
                                                                         .customRepositoryMapping(mapping)
                                                                         .build()))
                                              .build();
    customArtifactStream.setTemplateUuid(TEMPLATE_ID);
    customArtifactStream.setTemplateVersion(TEMPLATE_VERSION);
    customArtifactStream.setTemplateVariables(
        asList(aVariable().name("var1").value("overridden value").type(TEXT).build()));
    return customArtifactStream;
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testListArtifactStreamSummaryWithFeatureFlagDisabled() {
    createNexusArtifactStream("nexus1");
    createNexusArtifactStream("nexus2");
    List<ArtifactStreamSummary> artifactStreamSummary = artifactStreamService.listArtifactStreamSummary(APP_ID);
    assertThat(artifactStreamSummary).isEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testListArtifactStreamSummaryWithFeatureFlagEnabled() {
    when(featureFlagService.isEnabled(ArgumentMatchers.any(FeatureName.class), anyString())).thenReturn(true);
    createNexusArtifactStreamAtConnectorLevel("nexus1");
    createNexusArtifactStreamAtConnectorLevel("nexus2");
    List<ArtifactStreamSummary> artifactStreamSummary = artifactStreamService.listArtifactStreamSummary(APP_ID);
    assertThat(artifactStreamSummary).isEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetArtifactStreamByName() {
    ArtifactStream savedArtifactStream = createNexusArtifactStreamAtConnectorLevel("test");
    ArtifactStream artifactStream = artifactStreamService.getArtifactStreamByName(SETTING_ID, "test");
    assertThat(artifactStream).isNotNull();
    assertThat(artifactStream.getName()).isEqualTo("test");
    assertThat(artifactStream.getAppId()).isEqualTo(GLOBAL_APP_ID);
    assertThat(((NexusArtifactStream) savedArtifactStream).getGroupId())
        .isEqualTo(((NexusArtifactStream) artifactStream).getGroupId());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testListByAppIdConnectorLevel() {
    List<ArtifactStream> artifactStreams = artifactStreamService.listByAppId(GLOBAL_APP_ID);
    assertThat(artifactStreams).size().isEqualTo(0);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testListByAppIdSettingId() {
    ArtifactStream artifactStream1 = createNexusArtifactStreamAtConnectorLevel("nexus1");
    ArtifactStream artifactStream2 = createNexusArtifactStreamAtConnectorLevel("nexus2");
    ArtifactStream artifactStream3 = createNexusArtifactStream("nexus3");
    List<ArtifactStream> artifactStreams = artifactStreamService.listBySettingId(APP_ID, SETTING_ID);
    assertThat(artifactStreams).size().isEqualTo(1);
    assertThat(artifactStreams).extracting(ArtifactStream::getName).contains("nexus3");
    assertThat(artifactStreams).extracting(ArtifactStream::getUuid).contains(artifactStream3.getUuid());
    assertThat(artifactStreams)
        .extracting(ArtifactStream::getUuid)
        .doesNotContain(artifactStream1.getUuid(), artifactStream2.getUuid());
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testShouldNotInvalidArtifactStream() {
    ArtifactStream artifactStream = createNexusArtifactStreamAtConnectorLevel("test");
    artifactStreamService.delete(GLOBAL_APP_ID, artifactStream.getUuid());
    artifactStreamService.update(artifactStream, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCannotUpdateArtifactStreamType() {
    ArtifactStream artifactStream = createNexusArtifactStreamAtConnectorLevel("test");
    artifactStream.setArtifactStreamType(ARTIFACTORY.name());
    artifactStreamService.update(artifactStream, true);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCanUpdateMetadataDataOnlyField() {
    ArtifactStream artifactStream = createNexusArtifactStreamAtConnectorLevel("test");
    artifactStream.setMetadataOnly(true);
    ArtifactStream updatedArtifactStream = artifactStreamService.update(artifactStream, true);
    assertThat(updatedArtifactStream.isMetadataOnly()).isTrue();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCannotUpdateMetadataDataOnlyFieldToFalse() {
    ArtifactStream artifactStream = createNexusArtifactStreamAtConnectorLevelWithMetadataTrue("test");
    artifactStream.setMetadataOnly(false);
    artifactStreamService.update(artifactStream, true);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testShouldListArtifactStreams() {
    createNexusArtifactStreamAtConnectorLevel("test-1");
    createNexusArtifactStreamAtConnectorLevel("test-2");
    createNexusArtifactStreamAtConnectorLevel("test-3");
    createNexusArtifactStream("test-4");
    List<ArtifactStream> artifactStreams =
        artifactStreamService.list(aPageRequest()
                                       .addFilter(ArtifactStreamKeys.appId, EQ, GLOBAL_APP_ID)
                                       .addFilter(ArtifactStreamKeys.accountId, EQ, ACCOUNT_ID)
                                       .addFilter(ArtifactKeys.settingId, EQ, SETTING_ID)
                                       .build());
    assertThat(artifactStreams)
        .hasSize(3)
        .extracting(ArtifactStream::getName)
        .containsSequence("test-3", "test-2", "test-1")
        .doesNotContain("test-4");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testShouldListArtifactStreamsWithSearchString() {
    constructNexusArtifacts(GLOBAL_APP_ID, "test-1");
    constructNexusArtifacts(APP_ID, "test-4");
    constructNexusArtifacts(APP_ID, "test-5");
    constructNexusArtifacts(GLOBAL_APP_ID, "another-5");
    List<ArtifactStream> artifactStreams = artifactStreamService.list(
        aPageRequest().addFilter(ArtifactStreamKeys.appId, EQ, APP_ID).build(), ACCOUNT_ID, true, "test");
    assertThat(artifactStreams)
        .hasSize(2)
        .extracting(ArtifactStream::getName)
        .containsSequence("test-5", "test-4")
        .doesNotContain("another-5", "test-1");
    assertThat(artifactStreams).extracting(ArtifactStream::getArtifactCount).contains(1L, 1L);

    artifactStreams = artifactStreamService.list(
        aPageRequest().addFilter(ArtifactStreamKeys.appId, EQ, APP_ID).build(), ACCOUNT_ID, true, "15");
    assertThat(artifactStreams).isEmpty();
  }

  private void constructAmazonS3Artifacts(String appId, String name) {
    AmazonS3ArtifactStream amazonS3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(appId)
                                                        .settingId(SETTING_ID)
                                                        .jobname("harnessapps")
                                                        .name(name)
                                                        .serviceId(SERVICE_ID)
                                                        .artifactPaths(asList("dev/todolist.war"))
                                                        .build();
    ArtifactStream savedArtifactSteam = artifactStreamService.create(amazonS3ArtifactStream);
    createArtifact(appId, name, savedArtifactSteam.getUuid());
  }

  private void constructNexusArtifacts(String appId, String name) {
    NexusArtifactStream nexusDockerArtifactStream = NexusArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(appId)
                                                        .name(name)
                                                        .settingId(SETTING_ID)
                                                        .jobname("docker-private")
                                                        .groupId("wingsplugings/todolist")
                                                        .imageName("wingsplugings/todolist")
                                                        .serviceId(SERVICE_ID)
                                                        .repositoryFormat(RepositoryFormat.docker.name())
                                                        .build();
    ArtifactStream savedArtifactSteam = artifactStreamService.create(nexusDockerArtifactStream);
    createArtifact(appId, name, savedArtifactSteam.getUuid());
  }

  private void constructAmiArtifacts(String appId, String name) {
    AmiArtifactStream amiArtifactStream = AmiArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(appId)
                                              .settingId(SETTING_ID)
                                              .region("us-east-1")
                                              .name(name)
                                              .serviceId(SERVICE_ID)
                                              .build();
    ArtifactStream savedArtifactSteam = artifactStreamService.create(amiArtifactStream);
    createArtifact(appId, name, savedArtifactSteam.getUuid());
  }

  private void createArtifact(String appId, String name, String artifactStreamId) {
    String BUILD_NO = "buildNo";
    Artifact.Builder artifactBuilder = anArtifact()
                                           .withAppId(appId)
                                           .withArtifactStreamId(artifactStreamId)
                                           .withSettingId(SETTING_ID)
                                           .withAccountId(ACCOUNT_ID)
                                           .withRevision("1.0")
                                           .withDisplayName("DISPLAY_NAME")
                                           .withCreatedAt(System.currentTimeMillis())
                                           .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());

    persistence.save(artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, name))).but().build());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testShouldListArtifactStreamsWithSearchStringAndArtifactType() {
    constructNexusArtifacts(GLOBAL_APP_ID, "test-1");
    constructAmazonS3Artifacts(GLOBAL_APP_ID, "test-2");
    constructAmiArtifacts(GLOBAL_APP_ID, "test-3");
    List<ArtifactStream> artifactStreams =
        artifactStreamService.list(aPageRequest().addFilter(ArtifactStreamKeys.appId, EQ, GLOBAL_APP_ID).build(),
            ACCOUNT_ID, true, "test", ArtifactType.AWS_LAMBDA, 100);
    assertThat(artifactStreams)
        .hasSize(1)
        .extracting(ArtifactStream::getName)
        .containsSequence("test-2")
        .doesNotContain("test-1", "test-3");

    artifactStreams =
        artifactStreamService.list(aPageRequest().addFilter(ArtifactStreamKeys.appId, EQ, GLOBAL_APP_ID).build(),
            ACCOUNT_ID, true, "test", ArtifactType.AMI, 100);
    assertThat(artifactStreams)
        .hasSize(1)
        .extracting(ArtifactStream::getName)
        .containsSequence("test-3")
        .doesNotContain("test-1", "test-2");

    artifactStreams =
        artifactStreamService.list(aPageRequest().addFilter(ArtifactStreamKeys.appId, EQ, GLOBAL_APP_ID).build(),
            ACCOUNT_ID, true, "test", ArtifactType.OTHER, 100);
    assertThat(artifactStreams)
        .hasSize(3)
        .extracting(ArtifactStream::getName)
        .containsSequence("test-3", "test-2", "test-1");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAttachASWithPerpetualTaskId() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(dockerArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertDockerArtifactStream(dockerArtifactStream, APP_ID);

    ArtifactStream savedArtifactStream = artifactStreamService.get(dockerArtifactStream.getUuid());
    assertThat(artifactStreamService.attachPerpetualTaskId(savedArtifactStream, "PERPETUAL_TASK_ID")).isTrue();
    assertThat(artifactStreamService.get(dockerArtifactStream.getUuid()).getPerpetualTaskId())
        .isEqualTo("PERPETUAL_TASK_ID");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreatePerpetualTask() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, ACCOUNT_ID)).thenReturn(true);
    ArtifactStream savedArtifactStream = createArtifactStream(dockerArtifactStream);
    assertThat(savedArtifactStream).isNotNull();
    verify(subject).fireInform(any(), any());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddNexusArtifactStreamWithParameters() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(NexusConfig.builder()
                        .version("2.x")
                        .nexusUrl("http://nexus.software")
                        .username("username")
                        .accountId(ACCOUNT_ID)
                        .build());
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("${path}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .metadataOnly(true)
                                                  .build();
    ArtifactStream savedArtifactSteam = createNexusArtifactStream(nexusArtifactStream);
    NexusArtifactStream savedNexusArtifactStream = (NexusArtifactStream) savedArtifactSteam;
    assertThat(savedNexusArtifactStream.getArtifactPaths()).contains("${path}");
    assertThat(savedNexusArtifactStream.getJobname()).isEqualTo("releases");
    assertThat(savedNexusArtifactStream.getGroupId()).isEqualTo("io.harness.test");
    assertThat(savedNexusArtifactStream.getArtifactPaths()).contains("${path}");
    assertThat(savedNexusArtifactStream.getRepositoryFormat()).isEqualTo(RepositoryFormat.maven.name());
    assertThat(savedNexusArtifactStream.isArtifactStreamParameterized()).isEqualTo(true);
    verify(appService).getAccountIdByAppId(APP_ID);
  }

  private ArtifactStream createNexusArtifactStream(NexusArtifactStream nexusArtifactStream) {
    ArtifactStream savedArtifactSteam = createArtifactStream(nexusArtifactStream);
    assertThat(savedArtifactSteam.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getName()).isNotEmpty();
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(NEXUS.name());
    assertThat(savedArtifactSteam.getAppId()).isEqualTo(APP_ID);
    assertThat(savedArtifactSteam.fetchArtifactDisplayName(""))
        .isNotEmpty()
        .contains("releases/io.harness.test/${path}__");
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo("releases/io.harness.test/${path}");
    assertThat(savedArtifactSteam).isInstanceOf(NexusArtifactStream.class);
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(NEXUS.name());
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getJobName()).isEqualTo("releases");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getGroupId())
        .isEqualTo("io.harness.test");
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactName())
        .isEqualTo("${path}");
    assertThat(savedArtifactSteam.getCollectionStatus()).isEqualTo(STOPPED.name());
    return savedArtifactSteam;
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateNexusArtifactStreamParameterized() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(NexusConfig.builder()
                        .version("2.x")
                        .nexusUrl("http://nexus.software")
                        .username("username")
                        .accountId(ACCOUNT_ID)
                        .build());
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("${path}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .metadataOnly(true)
                                                  .build();
    ArtifactStream savedArtifactSteam = createNexusArtifactStream(nexusArtifactStream);
    assertThat(savedArtifactSteam.isArtifactStreamParameterized()).isEqualTo(true);

    updateAndValidateNexusArtifactStream((NexusArtifactStream) savedArtifactSteam, APP_ID);

    verify(appService, times(2)).getAccountIdByAppId(APP_ID);
    verify(yamlPushService, times(2))
        .pushYamlChangeSet(any(String.class), any(), any(ArtifactStream.class), any(), anyBoolean(), anyBoolean());
    verify(triggerService).updateByArtifactStream(savedArtifactSteam.getUuid());
  }

  private void updateAndValidateNexusArtifactStream(NexusArtifactStream savedArtifactSteam, String appId) {
    assertThat(savedArtifactSteam.getArtifactPaths()).contains("${path}");
    savedArtifactSteam.setName("nexus_Changed");
    savedArtifactSteam.setArtifactPaths(asList("${folder}/${path}"));

    ArtifactStream updatedArtifactStream = artifactStreamService.update(savedArtifactSteam);

    assertThat(updatedArtifactStream.getUuid()).isNotEmpty();
    assertThat(updatedArtifactStream.getName()).isNotEmpty().isEqualTo("nexus_Changed");
    assertThat(updatedArtifactStream.getArtifactStreamType()).isEqualTo(NEXUS.name());
    assertThat(updatedArtifactStream.getAppId()).isEqualTo(appId);

    assertThat(updatedArtifactStream.fetchArtifactDisplayName("")).isNotEmpty();
    assertThat(savedArtifactSteam.fetchArtifactStreamAttributes(featureFlagService).getArtifactName())
        .isEqualTo("${folder}/${path}");
    assertThat(updatedArtifactStream).isInstanceOf(NexusArtifactStream.class);
    assertThat(updatedArtifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamType())
        .isEqualTo(NEXUS.name());
    assertThat(updatedArtifactStream.getCollectionStatus()).isEqualTo(STOPPED.name());
    assertThat(updatedArtifactStream.isArtifactStreamParameterized()).isEqualTo(true);
    NexusArtifactStream updatedNexusArtifactStream = savedArtifactSteam;
    assertThat(updatedNexusArtifactStream.getArtifactPaths()).contains("${folder}/${path}");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotAddNexusArtifactStreamWithInvalidParameters() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("${artifact.path}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .build();
    createArtifactStream(nexusArtifactStream);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotAddNexus3xArtifactStreamWithParameters() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(NexusConfig.builder()
                        .version("3.x")
                        .nexusUrl("http://nexus.software")
                        .username("username")
                        .accountId(ACCOUNT_ID)
                        .build());
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("${path}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .build();
    createArtifactStream(nexusArtifactStream);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetParametersForArtifactStreamWithFFOn() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(NexusConfig.builder()
                        .version("2.x")
                        .nexusUrl("http://nexus.software")
                        .username("username")
                        .accountId(ACCOUNT_ID)
                        .build());
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("${repo}")
                                                  .groupId("${groupId}")
                                                  .artifactPaths(asList("${path}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .metadataOnly(true)
                                                  .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(nexusArtifactStream);
    List<String> parameters = artifactStreamService.getArtifactStreamParameters(savedArtifactSteam.getUuid());
    assertThat(parameters.size()).isEqualTo(3);
    assertThat(parameters).containsAll(asList("repo", "groupId", "path"));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotGetParametersForNonParameterizedArtifactStream() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("harness-maven")
                                                  .groupId("mygroup")
                                                  .artifactPaths(asList("myartifact"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(nexusArtifactStream);
    artifactStreamService.getArtifactStreamParameters(savedArtifactSteam.getUuid());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotGetParametersForNonNexus2xArtifactStream() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();
    ArtifactStream savedArtifactSteam = createArtifactStream(dockerArtifactStream);
    artifactStreamService.getArtifactStreamParameters(savedArtifactSteam.getUuid());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCreateNexusArtifactStreamsWithExtensionClassifier() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("${repo}")
                                                  .groupId("${groupId}")
                                                  .artifactPaths(asList("${path}"))
                                                  .extension("${extension}")
                                                  .classifier("${classifier}")
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .metadataOnly(true)
                                                  .build();
    NexusArtifactStream artifactStream = (NexusArtifactStream) createArtifactStream(nexusArtifactStream);
    assertThat(artifactStream.getJobname()).isEqualTo("${repo}");
    assertThat(artifactStream.getGroupId()).isEqualTo("${groupId}");
    assertThat(artifactStream.getArtifactPaths().get(0)).isEqualTo("${path}");
    assertThat(artifactStream.getExtension()).isEqualTo("${extension}");
    assertThat(artifactStream.getClassifier()).isEqualTo("${classifier}");
    assertThat(artifactStream.getName()).isEqualTo("testNexus");
    assertThat(artifactStream.isArtifactStreamParameterized()).isEqualTo(true);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenAddingNexusArtifactStreamForDockerType() {
    when(settingsService.getSettingValueById(ACCOUNT_ID, SETTING_ID))
        .thenReturn(NexusConfig.builder()
                        .username("username")
                        .password("password".toCharArray())
                        .accountId(ACCOUNT_ID)
                        .version("2.x")
                        .nexusUrl("http://bamboo.software")
                        .build());
    NexusArtifactStream nexusDockerArtifactStream = NexusArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("docker-private")
                                                        .groupId("wingsplugings/todolist")
                                                        .imageName("wingsplugings/todolist")
                                                        .autoPopulate(true)
                                                        .serviceId(SERVICE_ID)
                                                        .repositoryFormat(RepositoryFormat.docker.name())
                                                        .build();
    assertThatThrownBy(() -> createArtifactStream(nexusDockerArtifactStream))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Nexus 2.x does not support docker artifact type");
    verify(appService).getAccountIdByAppId(APP_ID);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldPruneByService() {
    WingsPersistence wingsPersistence = mock(WingsPersistence.class);
    Query<ArtifactStream> query = mock(Query.class);
    when(wingsPersistence.createQuery(ArtifactStream.class)).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    ArtifactStream stream = DockerArtifactStream.builder().appId(APP_ID).uuid(UUID).build();
    stream.setSyncFromGit(false);
    when(query.asList()).thenReturn(Collections.singletonList(stream));
    Reflect.on(artifactStreamService).set("wingsPersistence", wingsPersistence);
    artifactStreamService.pruneByService(APP_ID, SERVICE_ID);
    verify(alertService, times(1)).deleteByArtifactStream(APP_ID, UUID);
    verify(auditServiceHelper, times(1)).reportDeleteForAuditing(anyString(), any());
    verify(artifactStreamServiceBindingService, times(1)).deleteByArtifactStream(UUID, false);
    verify(wingsPersistence).delete(ArtifactStream.class, APP_ID, UUID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenPatternIsNotMatching() {
    assertThatThrownBy(
        () -> artifactStreamService.fetchByArtifactSourceVariableValue(APP_ID, "artifactName-serviceName"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The Artifact Source variable should be of the format 'artifactSourceName (serviceName)'");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenPatternIsNotMatching2() {
    assertThatThrownBy(() -> artifactStreamService.fetchByArtifactSourceVariableValue(APP_ID, "(serviceName)"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The Artifact Source variable should be of the format 'artifactSourceName (serviceName)'");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorForWrongServiceName() {
    when(serviceResourceService.getServiceByName(APP_ID, "serviceName")).thenReturn(null);
    assertThatThrownBy(
        () -> artifactStreamService.fetchByArtifactSourceVariableValue(APP_ID, "artifactSource (serviceName)"))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Service with name serviceName doesn't exist");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldFetchArtifactStreamByVariableValue() {
    when(serviceResourceService.getServiceByName(APP_ID, "serviceName"))
        .thenReturn(Service.builder().uuid(SERVICE_ID).build());
    ArtifactStream artifactStream =
        DockerArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).name("artifactSource").build();
    when(artifactStreamServiceBindingService.listArtifactStreams(APP_ID, SERVICE_ID))
        .thenReturn(Collections.singletonList(artifactStream));

    ArtifactStream artifactStream1 =
        artifactStreamService.fetchByArtifactSourceVariableValue(APP_ID, "artifactSource (serviceName)");
    assertThat(artifactStream1.getUuid()).isEqualTo(ARTIFACT_STREAM_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdateLastIterationFailed() {
    WingsPersistence wingsPersistence = mock(WingsPersistence.class);
    Query<ArtifactStream> query = mock(Query.class);
    when(wingsPersistence.createQuery(ArtifactStream.class)).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    ArtifactStream stream = DockerArtifactStream.builder().appId(APP_ID).uuid(UUID).build();
    stream.setSyncFromGit(false);
    when(query.asList()).thenReturn(Collections.singletonList(stream));

    UpdateOperations<ArtifactStream> updateOperations = mock(UpdateOperations.class);
    when(wingsPersistence.createUpdateOperations(ArtifactStream.class)).thenReturn(updateOperations);
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    when(wingsPersistence.update(query, updateOperations))
        .thenReturn(new UpdateResults(new WriteResult(1, true, null)));
    Reflect.on(artifactStreamService).set("wingsPersistence", wingsPersistence);
    assertThat(artifactStreamService.updateLastIterationFields(ACCOUNT_ID, ARTIFACT_STREAM_ID, false)).isTrue();
    verify(updateOperations, times(1)).set(any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdateLastIterationSuccess() {
    WingsPersistence wingsPersistence = mock(WingsPersistence.class);
    Query<ArtifactStream> query = mock(Query.class);
    when(wingsPersistence.createQuery(ArtifactStream.class)).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    ArtifactStream stream = DockerArtifactStream.builder().appId(APP_ID).uuid(UUID).build();
    stream.setSyncFromGit(false);
    when(query.asList()).thenReturn(Collections.singletonList(stream));

    UpdateOperations<ArtifactStream> updateOperations = mock(UpdateOperations.class);
    when(wingsPersistence.createUpdateOperations(ArtifactStream.class)).thenReturn(updateOperations);
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    when(wingsPersistence.update(query, updateOperations))
        .thenReturn(new UpdateResults(new WriteResult(1, true, null)));
    Reflect.on(artifactStreamService).set("wingsPersistence", wingsPersistence);
    assertThat(artifactStreamService.updateLastIterationFields(ACCOUNT_ID, ARTIFACT_STREAM_ID, true)).isTrue();
    verify(updateOperations, times(2)).set(any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResetArtifactCollection() {
    WingsPersistence wingsPersistence = mock(WingsPersistence.class);
    Query<ArtifactStream> query = mock(Query.class);
    when(wingsPersistence.createQuery(ArtifactStream.class)).thenReturn(query);
    ArgumentCaptor<String> fieldsCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valuesCaptor = ArgumentCaptor.forClass(Object.class);
    when(query.filter(fieldsCaptor.capture(), valuesCaptor.capture())).thenReturn(query);
    ArtifactStream stream = DockerArtifactStream.builder().appId(APP_ID).uuid(UUID).build();
    stream.setFailedCronAttempts(3501);
    stream.setCollectionStatus(STOPPED.name());
    stream.setSyncFromGit(false);
    when(query.asList()).thenReturn(Collections.singletonList(stream));

    UpdateOperations<ArtifactStream> updateOperations = mock(UpdateOperations.class);
    when(wingsPersistence.createUpdateOperations(ArtifactStream.class)).thenReturn(updateOperations);
    when(updateOperations.set(fieldsCaptor.capture(), valuesCaptor.capture())).thenReturn(updateOperations);
    when(wingsPersistence.update(query, updateOperations))
        .thenReturn(new UpdateResults(new WriteResult(1, true, null)));
    when(wingsPersistence.get(ArtifactStream.class, ARTIFACT_STREAM_ID)).thenReturn(stream);
    Reflect.on(artifactStreamService).set("wingsPersistence", wingsPersistence);
    assertThat(artifactStreamService.resetStoppedArtifactCollection(APP_ID, ARTIFACT_STREAM_ID)).isNotNull();
    assertThat(fieldsCaptor.getAllValues())
        .containsExactly(ArtifactStreamKeys.appId, ArtifactStreamKeys.uuid, ArtifactStreamKeys.collectionStatus,
            ArtifactStreamKeys.collectionStatus, ArtifactStreamKeys.failedCronAttempts);
    assertThat(valuesCaptor.getAllValues()).containsExactly(APP_ID, ARTIFACT_STREAM_ID, STOPPED, UNSTABLE, 0);
    verify(query, times(3)).filter(any(), any());
    verify(updateOperations, times(2)).set(any(), any());
    verify(alertService, times(1)).deleteByArtifactStream(APP_ID, ARTIFACT_STREAM_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotCreatePerpetualTaskWhenCollectionDisabled() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();

    dockerArtifactStream.setCollectionEnabled(false);
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, ACCOUNT_ID)).thenReturn(true);
    ArtifactStream savedArtifactStream = createArtifactStream(dockerArtifactStream);
    assertThat(savedArtifactStream).isNotNull();
    verify(subject, never()).fireInform(any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotDeletePerpetualTaskWhenCollectionDisabled() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();

    dockerArtifactStream.setCollectionEnabled(false);
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, ACCOUNT_ID)).thenReturn(true);
    ArtifactStream savedArtifactStream = createArtifactStream(dockerArtifactStream);
    assertThat(savedArtifactStream).isNotNull();
    verify(subject, never()).fireInform(any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldDeletePerpetualTaskWhenCollectionUpdatedToDisabled() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .build();

    dockerArtifactStream.setCollectionEnabled(false);
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, ACCOUNT_ID)).thenReturn(true);
    createArtifactStream(dockerArtifactStream);
    artifactStreamService.update(dockerArtifactStream, false);
    verify(subject).fireInform(any(), eq(dockerArtifactStream));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldGetArtifactStreamsForService() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .build();

    dockerArtifactStream.setCollectionEnabled(false);
    ArtifactStream savedArtifactSteam = artifactStreamService.create(dockerArtifactStream);

    List<String> projections = new ArrayList<>();
    projections.add(ArtifactStreamKeys.uuid);
    projections.add(ArtifactStreamKeys.collectionEnabled);

    List<ArtifactStream> artifactStreams =
        artifactStreamService.getArtifactStreamsForService(APP_ID, SERVICE_ID, projections);
    assertThat(artifactStreams).isNotEmpty().hasSize(1);
    ArtifactStream expectedArtifactStream = DockerArtifactStream.builder().uuid(savedArtifactSteam.getUuid()).build();
    expectedArtifactStream.setCollectionEnabled(false);
    assertThat(artifactStreams.get(0)).isEqualTo(expectedArtifactStream);
  }
}
