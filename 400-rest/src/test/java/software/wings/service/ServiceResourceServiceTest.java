/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.pcf.model.PcfConstants.MANIFEST_YML;
import static io.harness.pcf.model.PcfConstants.VARS_YML;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.PCF;
import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.CommandCategory.Type.COMMANDS;
import static software.wings.beans.CommandCategory.Type.COPY;
import static software.wings.beans.CommandCategory.Type.SCRIPTS;
import static software.wings.beans.CommandCategory.Type.VERIFICATIONS;
import static software.wings.beans.ConfigFile.DEFAULT_TEMPLATE_ID;
import static software.wings.beans.EntityVersion.Builder.anEntityVersion;
import static software.wings.beans.Graph.Builder.aGraph;
import static software.wings.beans.LambdaSpecification.DefaultSpecification;
import static software.wings.beans.LambdaSpecification.LambdaSpecificationKeys;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.Service.ServiceBuilder;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.CommandType.OTHER;
import static software.wings.beans.command.CommandType.START;
import static software.wings.beans.command.CommandType.STOP;
import static software.wings.beans.command.CommandUnitType.COMMAND;
import static software.wings.beans.command.CommandUnitType.COPY_CONFIGS;
import static software.wings.beans.command.CommandUnitType.DOCKER_START;
import static software.wings.beans.command.CommandUnitType.DOCKER_STOP;
import static software.wings.beans.command.CommandUnitType.EXEC;
import static software.wings.beans.command.CommandUnitType.PORT_CHECK_CLEARED;
import static software.wings.beans.command.CommandUnitType.PORT_CHECK_LISTENING;
import static software.wings.beans.command.CommandUnitType.PROCESS_CHECK_RUNNING;
import static software.wings.beans.command.CommandUnitType.SCP;
import static software.wings.beans.command.CommandUnitType.values;
import static software.wings.beans.command.ExecCommandUnit.Builder.anExecCommandUnit;
import static software.wings.beans.command.ScpCommandUnit.Builder.aScpCommandUnit;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.beans.container.ContainerTask.ContainerTaskKeys;
import static software.wings.beans.container.HelmChartSpecification.HelmChartSpecificationKeys;
import static software.wings.beans.container.HelmChartSpecification.builder;
import static software.wings.beans.container.PcfServiceSpecification.PcfServiceSpecificationKeys;
import static software.wings.beans.container.UserDataSpecification.UserDataSpecificationKeys;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;
import static software.wings.common.TemplateConstants.LATEST_TAG;
import static software.wings.persistence.AppContainer.Builder.anAppContainer;
import static software.wings.security.UserThreadLocal.userGuard;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.OBTAIN_VALUE;
import static software.wings.stencils.StencilCategory.CONTAINERS;
import static software.wings.utils.ArtifactType.AWS_LAMBDA;
import static software.wings.utils.ArtifactType.DOCKER;
import static software.wings.utils.ArtifactType.JAR;
import static software.wings.utils.ArtifactType.WAR;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_CUSTOM_KEYWORD;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_DESC;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.REFERENCED_WORKFLOW;
import static software.wings.utils.WingsTestConstants.SERVICE_COMMAND_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_VARIABLE_ID;
import static software.wings.utils.WingsTestConstants.TARGET_APP_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_VERSION;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.PageResponse.PageResponseBuilder;
import io.harness.beans.SearchFilter;
import io.harness.category.element.UnitTests;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;
import io.harness.stream.BoundedInputStream;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.AccountEvent;
import software.wings.beans.Application;
import software.wings.beans.CommandCategory;
import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.EntityVersion;
import software.wings.beans.EntityVersion.ChangeType;
import software.wings.beans.Graph;
import software.wings.beans.GraphNode;
import software.wings.beans.LambdaSpecification;
import software.wings.beans.LambdaSpecification.FunctionSpecification;
import software.wings.beans.Notification;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.ServiceVariable;
import software.wings.beans.Variable;
import software.wings.beans.Workflow.WorkflowBuilder;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.AppManifestSource;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.ArtifactStreamBinding;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.command.AmiCommandUnit;
import software.wings.beans.command.AwsLambdaCommandUnit;
import software.wings.beans.command.CleanupSshCommandUnit;
import software.wings.beans.command.CodeDeployCommandUnit;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CopyConfigCommandUnit;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.InitSshCommandUnit;
import software.wings.beans.command.InitSshCommandUnitV2;
import software.wings.beans.command.ScpCommandUnit;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.container.EcsServiceSpecification.EcsServiceSpecificationKeys;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.beans.container.KubernetesPayload;
import software.wings.beans.container.PcfServiceSpecification;
import software.wings.beans.container.UserDataSpecification;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateReference;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.BackgroundJobScheduler;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.CommandServiceImpl;
import software.wings.service.impl.ServiceResourceServiceImpl;
import software.wings.service.impl.command.CommandHelper;
import software.wings.service.impl.yaml.YamlChangeSetHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.CommandService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.stencils.Stencil;
import software.wings.utils.WingsTestConstants.MockChecker;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import de.danielbechler.diff.ObjectDifferBuilder;
import dev.morphia.Key;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@OwnedBy(CDC)
@Slf4j
public class ServiceResourceServiceTest extends WingsBaseTest {
  private static final Command.Builder commandBuilder = aCommand().withName("START").addCommandUnits(
      anExecCommandUnit().withCommandPath("/home/xxx/tomcat").withCommandString("bin/startup.sh").build());
  private static final ServiceCommand.Builder serviceCommandBuilder = aServiceCommand()
                                                                          .withServiceId(SERVICE_ID)
                                                                          .withUuid(SERVICE_COMMAND_ID)
                                                                          .withDefaultVersion(1)
                                                                          .withAppId(APP_ID)
                                                                          .withName("START")
                                                                          .withCommand(commandBuilder.but().build());
  private ServiceBuilder serviceBuilder = getServiceBuilder();

  PageRequest<ServiceCommand> serviceCommandPageRequest = getServiceCommandPageRequest(SERVICE_ID);

  @Inject private HPersistence persistence;
  @Mock private LimitCheckerFactory limitCheckerFactory;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static Answer executeRunnable(ArgumentCaptor<Runnable> runnableCaptor) {
    return invocation -> {
      runnableCaptor.getValue().run();
      return null;
    };
  }

  @Mock private ActivityService activityService;
  @Mock private NotificationService notificationService;
  @Mock private EventPublishHelper eventPublishHelper;
  @Mock private EntityVersionService entityVersionService;
  @Mock private CommandService commandService;
  @Mock private WorkflowService workflowService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private WingsPersistence mockWingsPersistence;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private ConfigService configService;
  @Mock private ServiceVariableService serviceVariableService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private AppService appService;
  @Mock private YamlChangeSetHelper yamlChangeSetHelper;
  @Mock private ExecutorService executorService;
  @Mock private BackgroundJobScheduler jobScheduler;
  @Mock private TemplateService templateService;
  @Mock private YamlPushService yamlPushService;
  @Mock private PipelineService pipelineService;
  @Mock private TriggerService triggerService;
  @Mock private HarnessTagService harnessTagService;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private CustomDeploymentTypeService customDeploymentTypeService;

  @Inject @InjectMocks private ServiceResourceServiceImpl srs;

  @Inject @InjectMocks private CommandHelper commandHelper;
  @Inject @InjectMocks private ResourceLookupService resourceLookupService;
  @Inject @InjectMocks private CommandServiceImpl commandServiceImpl;

  @Spy @InjectMocks private ServiceResourceServiceImpl spyServiceResourceService;

  @Captor
  private ArgumentCaptor<ServiceCommand> serviceCommandArgumentCaptor = ArgumentCaptor.forClass(ServiceCommand.class);

  @Mock private UpdateOperations<Service> updateOperations;

  @Mock private HQuery<ServiceCommand> serviceCommandQuery;

  private static ServiceBuilder getServiceBuilder() {
    return Service.builder()
        .uuid(SERVICE_ID)
        .appId(APP_ID)
        .name("SERVICE_NAME")
        .description("SERVICE_DESC")
        .artifactType(JAR)
        .appContainer(anAppContainer().withUuid("APP_CONTAINER_ID").build());
  }

  @Before
  public void setUp() throws Exception {
    when(mockWingsPersistence.saveAndGet(eq(Service.class), any(Service.class))).thenReturn(serviceBuilder.build());
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID)).thenReturn(serviceBuilder.build());
    when(mockWingsPersistence.getWithAppId(ServiceCommand.class, APP_ID, SERVICE_COMMAND_ID))
        .thenReturn(serviceCommandBuilder.but().build());
    when(appService.get(TARGET_APP_ID)).thenReturn(Application.Builder.anApplication().accountId(ACCOUNT_ID).build());
    when(appService.get(APP_ID)).thenReturn(Application.Builder.anApplication().accountId(ACCOUNT_ID).build());

    when(mockWingsPersistence.createUpdateOperations(Service.class)).thenReturn(updateOperations);
    when(updateOperations.set(anyString(), any())).thenReturn(updateOperations);

    when(mockWingsPersistence.createQuery(Service.class)).thenReturn(persistence.createQuery(Service.class));
    when(mockWingsPersistence.createQuery(ServiceCommand.class))
        .thenReturn(persistence.createQuery(ServiceCommand.class));
    when(mockWingsPersistence.createQuery(ServiceCommand.class, excludeAuthority))
        .thenReturn(persistence.createQuery(ServiceCommand.class));
    when(mockWingsPersistence.createQuery(Command.class)).thenReturn(persistence.createQuery(Command.class));
    when(mockWingsPersistence.createQuery(ContainerTask.class))
        .thenReturn(persistence.createQuery(ContainerTask.class));

    PageRequest<ServiceCommand> serviceCommandPageRequest = getServiceCommandPageRequest(SERVICE_ID);

    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(aPageResponse()
                        .withResponse(asList(aServiceCommand()
                                                 .withUuid(SERVICE_COMMAND_ID)
                                                 .withTargetToAllEnv(true)
                                                 .withName("START")
                                                 .withDefaultVersion(1)
                                                 .withCommand(commandBuilder.build())
                                                 .build()))
                        .build());
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldListServices() {
    PageRequest<Service> request = new PageRequest<>();
    request.addFilter("appId", EQ, APP_ID);
    Service mockService = Service.builder().uuid(SERVICE_ID).appId(APP_ID).build();
    when(mockWingsPersistence.query(Service.class, request))
        .thenReturn(aPageResponse().withResponse(asList(mockService)).build());
    PageRequest<ServiceCommand> serviceCommandPageRequest =
        aPageRequest().withLimit(PageRequest.UNLIMITED).addFilter("appId", EQ, APP_ID).build();
    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(aPageResponse()
                        .withResponse(asList(aServiceCommand()
                                                 .withUuid(SERVICE_COMMAND_ID)
                                                 .withServiceId(SERVICE_ID)
                                                 .withTargetToAllEnv(true)
                                                 .withName("START")
                                                 .withCommand(commandBuilder.build())
                                                 .build()))
                        .build());
    when(artifactStreamServiceBindingService.listArtifactStreamIds(mockService)).thenReturn(asList(ARTIFACT_STREAM_ID));
    when(artifactStreamService.listByIds(asList(ARTIFACT_STREAM_ID)))
        .thenReturn(asList(DockerArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).build()));
    when(artifactStreamServiceBindingService.list(APP_ID, SERVICE_ID))
        .thenReturn(asList(ArtifactStreamBinding.builder().name("test").build()));
    PageResponse<Service> response = srs.list(request, true, true, true, null);
    ArgumentCaptor<PageRequest> argument = ArgumentCaptor.forClass(PageRequest.class);
    verify(mockWingsPersistence).query(eq(Service.class), argument.capture());
    SearchFilter filter = (SearchFilter) argument.getValue().getFilters().get(0);
    assertThat(filter.getFieldName()).isEqualTo("appId");
    assertThat(filter.getFieldValues()).containsExactly(APP_ID);
    assertThat(filter.getOp()).isEqualTo(EQ);

    List<Service> services = response.getResponse();
    assertThat(services).isNotNull();
    Service service = services.get(0);
    assertThat(service.getServiceCommands()).isNotNull();
    assertThat(service.getArtifactStreams()).isNotNull();
    assertThat(service.getArtifactStreams().size()).isEqualTo(1);
    assertThat(service.getArtifactStreamBindings()).isNotNull();
    assertThat(service.getArtifactStreamBindings().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldSaveService() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    Service service = serviceBuilder.build();
    doReturn(service).when(spyServiceResourceService).addCommand(any(), any(), any(ServiceCommand.class), eq(true));
    doNothing().when(auditServiceHelper).addEntityOperationIdentifierDataToAuditContext(any());
    doNothing().when(harnessTagService).attachTag(any());
    Service savedService = spyServiceResourceService.save(service);

    assertThat(savedService.getUuid()).isEqualTo(SERVICE_ID);
    ArgumentCaptor<Service> calledService = ArgumentCaptor.forClass(Service.class);
    verify(mockWingsPersistence).saveAndGet(eq(Service.class), calledService.capture());
    verify(eventPublishHelper).publishAccountEvent(any(), any(AccountEvent.class), anyBoolean(), anyBoolean());
    Service calledServiceValue = calledService.getValue();
    assertThat(calledServiceValue)
        .isNotNull()
        .extracting("appId", "name", "description", "artifactType")
        .containsExactly(service.getAppId(), service.getName(), service.getDescription(), service.getArtifactType());
    assertThat(calledServiceValue.getKeywords())
        .isNotNull()
        .contains(service.getName().toLowerCase(), service.getDescription().toLowerCase(),
            service.getArtifactType().name().toLowerCase());

    verify(serviceTemplateService).createDefaultTemplatesByService(savedService);
    verify(spyServiceResourceService, times(3))
        .addCommand(eq(APP_ID), eq(SERVICE_ID), serviceCommandArgumentCaptor.capture(), eq(true));
    verify(notificationService).sendNotificationAsync(any(Notification.class));
    List<ServiceCommand> allValues = serviceCommandArgumentCaptor.getAllValues();
    assertThat(
        allValues.stream()
            .filter(
                command -> asList("Start", "Stop", "Install").contains(command.getCommand().getGraph().getGraphName()))
            .count())
        .isEqualTo(3);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetService() {
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID)).thenReturn(serviceBuilder.build());
    when(configService.getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID)).thenReturn(new ArrayList<>());
    srs.getWithDetails(APP_ID, SERVICE_ID);
    verify(mockWingsPersistence).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldUpdateService() throws IOException {
    try (UserThreadLocal.Guard guard = userGuard(null)) {
      Service service = serviceBuilder.name("UPDATED_SERVICE_NAME")
                            .description("UPDATED_SERVICE_DESC")
                            .artifactType(WAR)
                            .appContainer(anAppContainer().withUuid("UPDATED_APP_CONTAINER_ID").build())
                            .build();
      ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
      when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));

      srs.update(service);
      verify(mockWingsPersistence).update(any(Service.class), any(UpdateOperations.class));
      verify(mockWingsPersistence).createUpdateOperations(Service.class);
      verify(updateOperations).set("name", "UPDATED_SERVICE_NAME");
      verify(updateOperations).set("description", "UPDATED_SERVICE_DESC");
      verify(updateOperations)
          .set("keywords",
              new HashSet<>(asList(service.getName().toLowerCase(), service.getDescription().toLowerCase(),
                  service.getArtifactType().name().toLowerCase())));

      verify(serviceTemplateService)
          .updateDefaultServiceTemplateName(APP_ID, SERVICE_ID, SERVICE_NAME, "UPDATED_SERVICE_NAME");
      verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    }
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDeleteService() {
    Query mockQuery = mock(Query.class);
    AuditServiceHelper auditServiceHelper = mock(AuditServiceHelper.class);
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    when(mockWingsPersistence.delete(any(), any())).thenReturn(true);

    when(mockWingsPersistence.createQuery(any())).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), anyString())).thenReturn(mockQuery);
    when(mockQuery.get()).thenReturn(null);
    doNothing().when(auditServiceHelper).reportDeleteForAuditing(anyString(), any());
    when(workflowService.obtainWorkflowNamesReferencedByService(APP_ID, SERVICE_ID)).thenReturn(asList());
    when(workflowService.listWorkflows(any(PageRequest.class)))
        .thenReturn(aPageResponse().withResponse(asList()).build());

    Query pcfQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(PcfServiceSpecification.class)).thenReturn(pcfQuery);
    when(pcfQuery.filter(anyString(), anyString())).thenReturn(pcfQuery);
    when(mockQuery.filter(eq(PcfServiceSpecificationKeys.serviceId), anyString())).thenReturn(pcfQuery);
    PcfServiceSpecification pcfServiceSpecification = PcfServiceSpecification.builder().build();
    pcfServiceSpecification.setUuid(UUID);
    when(pcfQuery.get()).thenReturn(pcfServiceSpecification);

    Query containerQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(ContainerTask.class)).thenReturn(containerQuery);
    when(containerQuery.filter(anyString(), anyString())).thenReturn(containerQuery);
    when(mockQuery.filter(eq(ContainerTaskKeys.deploymentType), eq(KUBERNETES.name()))).thenReturn(containerQuery);
    ContainerTask k8sTask = new KubernetesContainerTask();
    k8sTask.setUuid(UUID);
    when(containerQuery.get()).thenReturn(null).thenReturn(k8sTask);

    Query helmQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(HelmChartSpecification.class)).thenReturn(helmQuery);
    when(helmQuery.filter(anyString(), anyString())).thenReturn(helmQuery);
    when(mockQuery.filter(eq(HelmChartSpecificationKeys.serviceId), anyString())).thenReturn(helmQuery);
    HelmChartSpecification chartSpecification = HelmChartSpecification.builder().build();
    chartSpecification.setUuid(UUID);
    when(helmQuery.get()).thenReturn(chartSpecification);

    Query userDataQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(UserDataSpecification.class)).thenReturn(userDataQuery);
    when(userDataQuery.filter(anyString(), anyString())).thenReturn(userDataQuery);
    when(mockQuery.filter(eq(UserDataSpecificationKeys.serviceId), anyString())).thenReturn(userDataQuery);
    UserDataSpecification userDataSpecification = UserDataSpecification.builder().build();
    userDataSpecification.setUuid(UUID);
    when(userDataQuery.get()).thenReturn(userDataSpecification);

    Query ecsQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(EcsServiceSpecification.class)).thenReturn(ecsQuery);
    when(ecsQuery.filter(anyString(), anyString())).thenReturn(ecsQuery);
    when(mockQuery.filter(eq(EcsServiceSpecificationKeys.serviceId), anyString())).thenReturn(ecsQuery);
    EcsServiceSpecification ecsSpec = EcsServiceSpecification.builder().build();
    ecsSpec.setUuid(UUID);
    when(ecsQuery.get()).thenReturn(ecsSpec);

    Query lambdaQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(LambdaSpecification.class)).thenReturn(lambdaQuery);
    when(lambdaQuery.filter(anyString(), anyString())).thenReturn(lambdaQuery);
    when(mockQuery.filter(eq(LambdaSpecificationKeys.serviceId), anyString())).thenReturn(lambdaQuery);
    LambdaSpecification lambdaSpec = LambdaSpecification.builder().build();
    lambdaSpec.setUuid(UUID);
    when(lambdaQuery.get()).thenReturn(lambdaSpec);

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    srs.delete(APP_ID, SERVICE_ID);
    InOrder inOrder = inOrder(mockWingsPersistence, workflowService, notificationService, serviceTemplateService,
        configService, serviceVariableService, artifactStreamService);
    inOrder.verify(mockWingsPersistence).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    inOrder.verify(workflowService).obtainWorkflowNamesReferencedByService(APP_ID, SERVICE_ID);
    inOrder.verify(mockWingsPersistence).delete(Service.class, SERVICE_ID);
    inOrder.verify(notificationService).sendNotificationAsync(any(Notification.class));
    verify(mockWingsPersistence).delete(ContainerTask.class, APP_ID, UUID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnDeleteReferencedByWorkflow() {
    List<String> referencedWorkflows = Collections.singletonList(REFERENCED_WORKFLOW);
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    when(mockWingsPersistence.delete(any(), any())).thenReturn(true);
    when(workflowService.obtainWorkflowNamesReferencedByService(APP_ID, SERVICE_ID)).thenReturn(referencedWorkflows);

    assertThatThrownBy(() -> srs.delete(APP_ID, SERVICE_ID))
        .isInstanceOf(WingsException.class)
        .hasMessage("Service " + SERVICE_NAME + " is referenced by 1 workflow " + referencedWorkflows + ".");

    verify(workflowService).obtainWorkflowNamesReferencedByService(APP_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnDeleteReferencedByPipeline() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    when(mockWingsPersistence.delete(any(), any())).thenReturn(true);
    when(workflowService.obtainWorkflowNamesReferencedByService(APP_ID, SERVICE_ID)).thenReturn(asList());
    when(infrastructureProvisionerService.listByBlueprintDetails(APP_ID, null, SERVICE_ID, null, null))
        .thenReturn(aPageResponse().build());
    when(pipelineService.obtainPipelineNamesReferencedByTemplatedEntity(APP_ID, SERVICE_ID))
        .thenReturn(asList("Referenced Pipeline"));

    assertThatThrownBy(() -> srs.delete(APP_ID, SERVICE_ID))
        .isInstanceOf(WingsException.class)
        .hasMessage("Service is referenced by 1 pipeline [Referenced Pipeline] as a workflow variable.");

    verify(workflowService).obtainWorkflowNamesReferencedByService(APP_ID, SERVICE_ID);
    verify(pipelineService).obtainPipelineNamesReferencedByTemplatedEntity(APP_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnDeleteReferencedByTrigger() {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    when(mockWingsPersistence.delete(any(), any())).thenReturn(true);
    when(workflowService.obtainWorkflowNamesReferencedByService(APP_ID, SERVICE_ID)).thenReturn(asList());
    when(infrastructureProvisionerService.listByBlueprintDetails(APP_ID, null, SERVICE_ID, null, null))
        .thenReturn(aPageResponse().build());
    when(pipelineService.obtainPipelineNamesReferencedByTemplatedEntity(APP_ID, SERVICE_ID)).thenReturn(asList());
    when(triggerService.obtainTriggerNamesReferencedByTemplatedEntityId(APP_ID, SERVICE_ID))
        .thenReturn(asList("Referenced Trigger"));

    assertThatThrownBy(() -> srs.delete(APP_ID, SERVICE_ID))
        .isInstanceOf(WingsException.class)
        .hasMessage("Service is referenced by 1 trigger [Referenced Trigger] as a workflow variable.");

    verify(workflowService).obtainWorkflowNamesReferencedByService(APP_ID, SERVICE_ID);
    verify(pipelineService).obtainPipelineNamesReferencedByTemplatedEntity(APP_ID, SERVICE_ID);
    verify(triggerService).obtainTriggerNamesReferencedByTemplatedEntityId(APP_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPruneDescendingObjects() {
    srs.pruneDescendingEntities(APP_ID, SERVICE_ID, false);
    InOrder inOrder = inOrder(mockWingsPersistence, workflowService, notificationService, serviceTemplateService,
        configService, serviceVariableService, artifactStreamService);
    inOrder.verify(artifactStreamService).pruneByService(APP_ID, SERVICE_ID);
    inOrder.verify(configService).pruneByService(APP_ID, SERVICE_ID);
    inOrder.verify(serviceTemplateService).pruneByService(APP_ID, SERVICE_ID);
    inOrder.verify(serviceVariableService).pruneByService(APP_ID, SERVICE_ID);
  }

  @Test(expected = ConstraintViolationException.class)
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldFailCloneWhenServiceNameIsNull() {
    Service originalService = Service.builder().build();
    srs.clone(APP_ID, SERVICE_ID, originalService);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testFailCloneWhenServiceNameIsNull() {
    Service originalService = Service.builder().build();
    assertThatThrownBy(() -> spyServiceResourceService.clone(APP_ID, SERVICE_ID, originalService))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Service Name can not be empty");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testFailCloneWhenServiceNameIsEmpty() {
    Service originalService = Service.builder().name(" ").build();
    assertThatThrownBy(() -> spyServiceResourceService.clone(APP_ID, SERVICE_ID, originalService))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Service Name can not be empty");
  }

  @Test(expected = ConstraintViolationException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldFailCloneWhenServiceNameHasLeadingAndTrailingSpace() {
    Service originalService = Service.builder().name(" s1 ").appId(APP_ID).build();
    srs.clone(APP_ID, SERVICE_ID, originalService);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldFetchServiceNamesByUuids() {
    List<String> serviceIds = asList(SERVICE_ID);
    List<Service> services = asList(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build());

    Query mockQuery = mock(Query.class);
    FieldEnd<Service> fieldEnd = mock(FieldEnd.class);
    doReturn(mockQuery).when(mockWingsPersistence).createQuery(Service.class);
    doReturn(mockQuery).when(mockQuery).project(ServiceKeys.name, true);
    doReturn(mockQuery).when(mockQuery).project(ServiceKeys.accountId, true);
    doReturn(mockQuery).when(mockQuery).project(ServiceKeys.appId, true);
    doReturn(mockQuery).when(mockQuery).filter(anyString(), anyString());

    doReturn(fieldEnd).when(mockQuery).field(anyString());
    doReturn(mockQuery).when(fieldEnd).in(serviceIds);

    doReturn(services).when(mockQuery).asList();

    List<String> returnedServices = srs.fetchServiceNamesByUuids(APP_ID, serviceIds);
    assertThat(returnedServices).isNotNull();
    assertThat(returnedServices.size()).isEqualTo(1);
    assertThat(returnedServices.get(0)).isEqualTo(SERVICE_NAME);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCloneService() throws IOException {
    PageRequest<ServiceCommand> serviceCommandPageRequest = getServiceCommandPageRequest(SERVICE_ID);

    when(mockWingsPersistence.saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class)))
        .thenAnswer(invocation -> {
          ServiceCommand command = invocation.getArgument(1, ServiceCommand.class);
          command.setUuid(ID_KEY);
          return command;
        });

    Command command = getCommand();

    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(getPageResponse(command));
    when(commandService.getCommand(APP_ID, "SERVICE_COMMAND_ID", 1)).thenReturn(command);

    Service originalService =
        serviceBuilder.serviceCommands(asList(aServiceCommand().withUuid("SERVICE_COMMAND_ID").build())).build();
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID)).thenReturn(originalService);

    Query<PcfServiceSpecification> pcfSpecificationQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(PcfServiceSpecification.class)).thenReturn(pcfSpecificationQuery);
    when(pcfSpecificationQuery.filter(anyString(), any())).thenReturn(pcfSpecificationQuery);
    when(pcfSpecificationQuery.get()).thenReturn(null);

    Query<HelmChartSpecification> helmQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(HelmChartSpecification.class)).thenReturn(helmQuery);
    when(helmQuery.filter(anyString(), any())).thenReturn(helmQuery);
    when(helmQuery.get()).thenReturn(null);

    Service savedClonedService = getClonedService(originalService);
    when(mockWingsPersistence.saveAndGet(eq(Service.class), any(Service.class))).thenReturn(savedClonedService);
    when(applicationManifestService.getManifestByServiceId(anyString(), anyString())).thenReturn(null);

    doReturn(savedClonedService)
        .when(spyServiceResourceService)
        .addCommand(eq(APP_ID), eq("CLONED_SERVICE_ID"), any(ServiceCommand.class), eq(true));

    ConfigFile configFile = getConfigFile();
    when(configService.getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID)).thenReturn(asList(configFile));

    ServiceVariable serviceVariable = getServiceVariable();
    when(serviceVariableService.getServiceVariablesForEntity(APP_ID, SERVICE_ID, OBTAIN_VALUE))
        .thenReturn(asList(serviceVariable));

    when(serviceTemplateService.list(any(PageRequest.class), any(Boolean.class), any()))
        .thenReturn(aPageResponse().withResponse(asList(aServiceTemplate().build())).build());

    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));

    when(configService.download(APP_ID, "CONFIG_FILE_ID")).thenReturn(folder.newFile("abc.txt"));

    Service clonedService = spyServiceResourceService.clone(
        APP_ID, SERVICE_ID, Service.builder().name("Clone Service").description("clone description").build());

    assertThat(clonedService)
        .isNotNull()
        .isInstanceOf(Service.class)
        .hasFieldOrPropertyWithValue("name", "Clone Service")
        .hasFieldOrPropertyWithValue("description", "clone description")
        .hasFieldOrPropertyWithValue("artifactType", originalService.getArtifactType())
        .hasFieldOrPropertyWithValue("appContainer", originalService.getAppContainer());

    verify(mockWingsPersistence).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);

    ArgumentCaptor<Service> serviceArgumentCaptor = ArgumentCaptor.forClass(Service.class);
    verify(mockWingsPersistence).saveAndGet(eq(Service.class), serviceArgumentCaptor.capture());
    Service savedService = serviceArgumentCaptor.getAllValues().get(0);
    assertThat(savedService)
        .isNotNull()
        .isInstanceOf(Service.class)
        .hasFieldOrPropertyWithValue("name", "Clone Service")
        .hasFieldOrPropertyWithValue("description", "clone description")
        .hasFieldOrPropertyWithValue("artifactType", originalService.getArtifactType())
        .hasFieldOrPropertyWithValue("appContainer", originalService.getAppContainer());

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
    verify(configService).download(APP_ID, "CONFIG_FILE_ID");
    verify(configService).save(any(ConfigFile.class), new BoundedInputStream(any(InputStream.class)));
    verify(serviceVariableService).getServiceVariablesForEntity(APP_ID, SERVICE_ID, OBTAIN_VALUE);
    verify(serviceVariableService).save(any(ServiceVariable.class));

    originalService =
        serviceBuilder.serviceCommands(asList(aServiceCommand().withUuid("SERVICE_COMMAND_ID").build())).build();
    originalService.setArtifactType(AWS_LAMBDA);
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID)).thenReturn(originalService);
    doReturn(savedClonedService)
        .when(spyServiceResourceService)
        .addCommand(eq(APP_ID), eq("CLONED_SERVICE_ID"), any(ServiceCommand.class), eq(false));
    LambdaSpecification lambdaSpec = createLambdaSpecification();
    Query<LambdaSpecification> lambdaQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(LambdaSpecification.class)).thenReturn(lambdaQuery);
    when(lambdaQuery.filter(anyString(), any())).thenReturn(lambdaQuery);
    when(lambdaQuery.get()).thenReturn(lambdaSpec);
    when(mockWingsPersistence.saveAndGet(eq(LambdaSpecification.class), any(LambdaSpecification.class)))
        .thenReturn(lambdaSpec);

    spyServiceResourceService.clone(
        APP_ID, SERVICE_ID, Service.builder().name("Clone Service").description("clone description").build());

    ArgumentCaptor<LambdaSpecification> lambdaArgumentCaptor = ArgumentCaptor.forClass(LambdaSpecification.class);
    verify(mockWingsPersistence, times(1)).saveAndGet(eq(LambdaSpecification.class), lambdaArgumentCaptor.capture());
    LambdaSpecification lambdaSpecification = lambdaArgumentCaptor.getValue();
    assertThat(lambdaSpecification.getDefaults().getRuntime()).isEqualTo("default-runtTime");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldCloneServiceWithoutConfigFile() throws IOException {
    PageRequest<ServiceCommand> serviceCommandPageRequest = getServiceCommandPageRequest(SERVICE_ID);

    when(mockWingsPersistence.saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class)))
        .thenAnswer(invocation -> {
          ServiceCommand command = invocation.getArgument(1, ServiceCommand.class);
          command.setUuid(ID_KEY);
          return command;
        });

    Command command = getCommand();

    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(getPageResponse(command));
    when(commandService.getCommand(APP_ID, "SERVICE_COMMAND_ID", 1)).thenReturn(command);

    Service originalService =
        serviceBuilder.serviceCommands(asList(aServiceCommand().withUuid("SERVICE_COMMAND_ID").build())).build();
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID)).thenReturn(originalService);

    Query<PcfServiceSpecification> pcfSpecificationQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(PcfServiceSpecification.class)).thenReturn(pcfSpecificationQuery);
    when(pcfSpecificationQuery.filter(anyString(), any())).thenReturn(pcfSpecificationQuery);
    when(pcfSpecificationQuery.get()).thenReturn(null);

    Query<HelmChartSpecification> helmQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(HelmChartSpecification.class)).thenReturn(helmQuery);
    when(helmQuery.filter(anyString(), any())).thenReturn(helmQuery);
    when(helmQuery.get()).thenReturn(null);

    Service savedClonedService = getClonedService(originalService);
    when(mockWingsPersistence.saveAndGet(eq(Service.class), any(Service.class))).thenReturn(savedClonedService);
    when(applicationManifestService.getManifestByServiceId(anyString(), anyString())).thenReturn(null);

    doReturn(savedClonedService)
        .when(spyServiceResourceService)
        .addCommand(eq(APP_ID), eq("CLONED_SERVICE_ID"), any(ServiceCommand.class), eq(true));

    ConfigFile configFile = getConfigFile();
    when(configService.getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID)).thenReturn(asList(configFile));

    ServiceVariable serviceVariable = getServiceVariable();
    when(serviceVariableService.getServiceVariablesForEntity(APP_ID, SERVICE_ID, OBTAIN_VALUE))
        .thenReturn(asList(serviceVariable));

    when(serviceTemplateService.list(any(PageRequest.class), any(Boolean.class), any()))
        .thenReturn(aPageResponse().withResponse(asList(aServiceTemplate().build())).build());

    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));

    File invalidFile = getFile();
    when(configService.download(APP_ID, "CONFIG_FILE_ID")).thenReturn(invalidFile);

    Service clonedService = spyServiceResourceService.clone(
        APP_ID, SERVICE_ID, Service.builder().name("Clone Service").description("clone description").build());

    assertThat(clonedService)
        .isNotNull()
        .isInstanceOf(Service.class)
        .hasFieldOrPropertyWithValue("name", "Clone Service")
        .hasFieldOrPropertyWithValue("description", "clone description")
        .hasFieldOrPropertyWithValue("artifactType", originalService.getArtifactType())
        .hasFieldOrPropertyWithValue("appContainer", originalService.getAppContainer());

    verify(mockWingsPersistence).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
    verify(serviceVariableService).getServiceVariablesForEntity(APP_ID, SERVICE_ID, OBTAIN_VALUE);
    verify(serviceVariableService).save(any(ServiceVariable.class));
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldCloneServiceWithDockerArtifactType() throws IOException {
    PageRequest<ServiceCommand> serviceCommandPageRequest = getServiceCommandPageRequest(SERVICE_ID);
    PageRequest<ServiceCommand> clonedServiceCommandPageRequest = getServiceCommandPageRequest("CLONED_SERVICE_ID");

    when(mockWingsPersistence.saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class)))
        .thenAnswer(invocation -> {
          ServiceCommand command = invocation.getArgument(1, ServiceCommand.class);
          command.setUuid(ID_KEY);
          return command;
        });

    Command command = getCommand();

    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(getPageResponse(command));
    when(mockWingsPersistence.query(ServiceCommand.class, clonedServiceCommandPageRequest))
        .thenReturn(getPageResponse(command));
    when(commandService.getCommand(APP_ID, "SERVICE_COMMAND_ID", 1)).thenReturn(command);

    Service originalService =
        serviceBuilder.serviceCommands(asList(aServiceCommand().withUuid("SERVICE_COMMAND_ID").build()))
            .artifactType(DOCKER)
            .build();
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID)).thenReturn(originalService);

    Query<PcfServiceSpecification> pcfSpecificationQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(PcfServiceSpecification.class)).thenReturn(pcfSpecificationQuery);
    when(pcfSpecificationQuery.filter(anyString(), any())).thenReturn(pcfSpecificationQuery);
    when(pcfSpecificationQuery.get()).thenReturn(null);

    Query<HelmChartSpecification> helmQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(HelmChartSpecification.class)).thenReturn(helmQuery);
    when(helmQuery.filter(anyString(), any())).thenReturn(helmQuery);
    when(helmQuery.get()).thenReturn(null);

    Service savedClonedService = getClonedService(originalService);
    when(mockWingsPersistence.saveAndGet(eq(Service.class), any(Service.class))).thenReturn(savedClonedService);
    when(applicationManifestService.getManifestByServiceId(anyString(), anyString())).thenReturn(null);
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, "CLONED_SERVICE_ID")).thenReturn(savedClonedService);

    doReturn(savedClonedService)
        .when(spyServiceResourceService)
        .addCommand(eq(APP_ID), eq("CLONED_SERVICE_ID"), any(ServiceCommand.class), eq(true));

    ConfigFile configFile = getConfigFile();
    when(configService.getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID)).thenReturn(asList(configFile));
    when(configService.download(APP_ID, "CONFIG_FILE_ID")).thenReturn(folder.newFile("abc.txt"));

    ServiceVariable serviceVariable = ServiceVariable.builder().build();
    serviceVariable.setAppId(APP_ID);
    serviceVariable.setUuid(SERVICE_VARIABLE_ID);
    when(serviceVariableService.getServiceVariablesForEntity(APP_ID, SERVICE_ID, OBTAIN_VALUE))
        .thenReturn(asList(serviceVariable));

    when(serviceTemplateService.list(any(PageRequest.class), any(Boolean.class), any()))
        .thenReturn(aPageResponse().withResponse(asList(aServiceTemplate().build())).build());

    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));

    ContainerTask containerTask = null;
    List<ContainerTask> containerTaskList = Arrays.asList(containerTask);
    PageRequest<ContainerTask> pageRequest = getContainerTaskPageRequest(SERVICE_ID);
    PageResponse<ContainerTask> pageResponse = mock(PageResponse.class);
    when(mockWingsPersistence.query(ContainerTask.class, pageRequest))
        .thenReturn(aPageResponse().withResponse(Collections.emptyList()).build());
    when(pageResponse.getResponse()).thenReturn(containerTaskList);

    Service clonedService = spyServiceResourceService.clone(
        APP_ID, SERVICE_ID, Service.builder().name("Clone Service").description("clone description").build());

    assertThat(clonedService)
        .isNotNull()
        .isInstanceOf(Service.class)
        .hasFieldOrPropertyWithValue("name", "Clone Service")
        .hasFieldOrPropertyWithValue("description", "clone description")
        .hasFieldOrPropertyWithValue("artifactType", originalService.getArtifactType())
        .hasFieldOrPropertyWithValue("appContainer", originalService.getAppContainer());

    verify(mockWingsPersistence).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldCloneServiceWithContainerTask() throws IOException {
    PageRequest<ServiceCommand> serviceCommandPageRequest = getServiceCommandPageRequest(SERVICE_ID);
    PageRequest<ServiceCommand> clonedServiceCommandPageRequest = getServiceCommandPageRequest("CLONED_SERVICE_ID");

    when(mockWingsPersistence.saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class)))
        .thenAnswer(invocation -> {
          ServiceCommand command = invocation.getArgument(1, ServiceCommand.class);
          command.setUuid(ID_KEY);
          return command;
        });

    Command command = getCommand();

    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(getPageResponse(command));
    when(mockWingsPersistence.query(ServiceCommand.class, clonedServiceCommandPageRequest))
        .thenReturn(getPageResponse(command));
    when(commandService.getCommand(APP_ID, "SERVICE_COMMAND_ID", 1)).thenReturn(command);

    Service originalService =
        serviceBuilder.serviceCommands(asList(aServiceCommand().withUuid("SERVICE_COMMAND_ID").build()))
            .artifactType(DOCKER)
            .build();
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID)).thenReturn(originalService);

    Query<PcfServiceSpecification> pcfSpecificationQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(PcfServiceSpecification.class)).thenReturn(pcfSpecificationQuery);
    when(pcfSpecificationQuery.filter(anyString(), any())).thenReturn(pcfSpecificationQuery);
    when(pcfSpecificationQuery.get()).thenReturn(null);

    Query<HelmChartSpecification> helmQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(HelmChartSpecification.class)).thenReturn(helmQuery);
    when(helmQuery.filter(anyString(), any())).thenReturn(helmQuery);
    when(helmQuery.get()).thenReturn(getHelmChartSpecification());
    when(mockWingsPersistence.saveAndGet(eq(HelmChartSpecification.class), any(HelmChartSpecification.class)))
        .thenReturn(getHelmChartSpecification());

    Service savedClonedService = getClonedService(originalService);
    when(mockWingsPersistence.saveAndGet(eq(Service.class), any(Service.class))).thenReturn(savedClonedService);
    when(applicationManifestService.getManifestByServiceId(anyString(), anyString())).thenReturn(null);
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, "CLONED_SERVICE_ID")).thenReturn(savedClonedService);

    doReturn(savedClonedService)
        .when(spyServiceResourceService)
        .addCommand(eq(APP_ID), eq("CLONED_SERVICE_ID"), any(ServiceCommand.class), eq(true));

    ConfigFile configFile = getConfigFile();
    when(configService.getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID)).thenReturn(asList(configFile));
    when(configService.download(APP_ID, "CONFIG_FILE_ID")).thenReturn(folder.newFile("abc.txt"));

    ServiceVariable serviceVariable = ServiceVariable.builder().build();
    serviceVariable.setAppId(APP_ID);
    serviceVariable.setUuid(SERVICE_VARIABLE_ID);
    when(serviceVariableService.getServiceVariablesForEntity(APP_ID, SERVICE_ID, OBTAIN_VALUE))
        .thenReturn(asList(serviceVariable));

    when(serviceTemplateService.list(any(PageRequest.class), any(Boolean.class), any()))
        .thenReturn(aPageResponse().withResponse(asList(aServiceTemplate().build())).build());
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));

    ContainerTask containerTask = getContainerTask(SERVICE_ID);
    List<ContainerTask> containerTaskList = Arrays.asList(containerTask);
    PageRequest<ContainerTask> pageRequest = getContainerTaskPageRequest(SERVICE_ID);
    PageResponse<ContainerTask> pageResponse = mock(PageResponse.class);
    when(mockWingsPersistence.query(ContainerTask.class, pageRequest)).thenReturn(pageResponse);
    when(pageResponse.getResponse()).thenReturn(containerTaskList);

    Query<Service> serviceQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(Service.class)).thenReturn(serviceQuery);
    when(serviceQuery.filter(anyString(), any())).thenReturn(serviceQuery);
    Key<Service> key = new Key(Service.class, "services", "CLONED_SERVICE_ID");
    when(serviceQuery.getKey()).thenReturn(key);

    ContainerTask clonedContainerTask = getContainerTask("CLONED_SERVICE_ID");
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, "CLONED_SERVICE_ID")).thenReturn(originalService);
    when(mockWingsPersistence.saveAndGet(ContainerTask.class, clonedContainerTask)).thenReturn(clonedContainerTask);

    Service clonedService = spyServiceResourceService.clone(
        APP_ID, SERVICE_ID, Service.builder().name("Clone Service").description("clone description").build());

    assertThat(clonedService)
        .isNotNull()
        .isInstanceOf(Service.class)
        .hasFieldOrPropertyWithValue("name", "Clone Service")
        .hasFieldOrPropertyWithValue("description", "clone description")
        .hasFieldOrPropertyWithValue("artifactType", originalService.getArtifactType())
        .hasFieldOrPropertyWithValue("appContainer", originalService.getAppContainer());

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    verify(configService, times(3)).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
  }

  public HelmChartSpecification getHelmChartSpecification() {
    HelmChartSpecification specification =
        builder().chartName("chartName").chartUrl("chartUrl").chartVersion("v1").serviceId(SERVICE_ID).build();
    specification.setAccountId(ACCOUNT_ID);
    specification.setAppId(APP_ID);
    return specification;
  }

  @NotNull
  private ContainerTask getContainerTask(String serviceId) {
    ContainerTask containerTask = new KubernetesContainerTask();
    containerTask.setServiceId(serviceId);
    return containerTask;
  }

  @NotNull
  private File getFile() throws IOException {
    File invalidFile = folder.newFile("invalid_file");
    invalidFile.setReadable(false);
    invalidFile.setWritable(false);
    return invalidFile;
  }

  @NotNull
  private Command getCommand() {
    Graph commandGraph = getGraph();

    Command command = aCommand().withGraph(commandGraph).build();
    command.transformGraph();
    command.setVersion(1L);
    return command;
  }

  @NotNull
  private ConfigFile getConfigFile() {
    ConfigFile configFile = ConfigFile.builder().build();
    configFile.setAppId(APP_ID);
    configFile.setUuid("CONFIG_FILE_ID");
    return configFile;
  }

  @NotNull
  private Service getClonedService(Service originalService) {
    Service savedClonedService = originalService.cloneInternal();
    savedClonedService.setName("Clone Service");
    savedClonedService.setDescription("clone description");
    savedClonedService.setUuid("CLONED_SERVICE_ID");
    return savedClonedService;
  }

  @NotNull
  private ServiceVariable getServiceVariable() {
    ServiceVariable serviceVariable = ServiceVariable.builder().build();
    serviceVariable.setAppId(APP_ID);
    serviceVariable.setUuid(SERVICE_VARIABLE_ID);
    return serviceVariable;
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCloneCommandWithDockerArtifactType() {
    Service service = serviceBuilder.serviceCommands(asList(aServiceCommand().withUuid("SERVICE_COMMAND_ID").build()))
                          .artifactType(DOCKER)
                          .build();
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID)).thenReturn(service);

    ServiceCommand serviceCommand = ServiceCommand.Builder.aServiceCommand()
                                        .withAccountId(ACCOUNT_ID)
                                        .withAppId(APP_ID)
                                        .withName("service-command-name")
                                        .withServiceId(SERVICE_ID)
                                        .withCommand(Command.Builder.aCommand()
                                                         .withCommandType(CommandType.OTHER)
                                                         .withName("command-name")
                                                         .withAccountId(ACCOUNT_ID)
                                                         .build())
                                        .build();
    assertThatThrownBy(() -> spyServiceResourceService.cloneCommand(APP_ID, SERVICE_ID, "command-name", serviceCommand))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Docker commands can not be cloned");
  }

  @NotNull
  private PageResponse getPageResponse(Command command) {
    return aPageResponse()
        .withResponse(asList(aServiceCommand()
                                 .withUuid("SERVICE_COMMAND_ID")
                                 .withDefaultVersion(1)
                                 .withTargetToAllEnv(true)
                                 .withName("START")
                                 .withCommand(command)
                                 .build()))
        .build();
  }

  private Graph getGraph() {
    return aGraph()
        .withGraphName("START")
        .addNodes(GraphNode.builder()
                      .id("1")
                      .origin(true)
                      .type("EXEC")
                      .properties(ImmutableMap.<String, Object>builder()
                                      .put("commandPath", "/home/xxx/tomcat")
                                      .put("commandString", "bin/startup.sh")
                                      .build())
                      .build())
        .build();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldAddCommand() {
    when(mockWingsPersistence.saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class)))
        .thenAnswer(invocation -> {
          ServiceCommand command = invocation.getArgument(1, ServiceCommand.class);
          command.setServiceId(SERVICE_ID);
          command.setUuid(ID_KEY);
          return command;
        });

    Graph commandGraph = aGraph()
                             .withGraphName("START")
                             .addNodes(GraphNode.builder()
                                           .id("1")
                                           .origin(true)
                                           .type("EXEC")
                                           .properties(ImmutableMap.<String, Object>builder()
                                                           .put("commandPath", "/home/xxx/tomcat")
                                                           .put("command", "bin/startup.sh")
                                                           .build())
                                           .build())
                             .build();

    Command expectedCommand = aCommand().withGraph(commandGraph).build();
    expectedCommand.transformGraph();

    Service service = serviceBuilder.build();
    service.getServiceCommands().add(aServiceCommand()
                                         .withTargetToAllEnv(true)
                                         .withAppId(APP_ID)
                                         .withServiceId(SERVICE_ID)
                                         .withUuid(ID_KEY)
                                         .withDefaultVersion(1)
                                         .withName("START")
                                         .withCommand(expectedCommand)
                                         .build());

    srs.addCommand(APP_ID, SERVICE_ID,
        aServiceCommand().withServiceId(SERVICE_ID).withTargetToAllEnv(true).withCommand(expectedCommand).build(),
        true);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
    verify(mockWingsPersistence).saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldAddCommandWithTemplateVariables() {
    when(mockWingsPersistence.saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class)))
        .thenAnswer(invocation -> {
          ServiceCommand serviceCommand = invocation.getArgument(1, ServiceCommand.class);
          serviceCommand.setServiceId(SERVICE_ID);
          serviceCommand.setUuid(ID_KEY);
          Command command = serviceCommand.getCommand();
          assertThat(command.getTemplateVariables()).isNotNull();
          assertThat(command.getTemplateVariables().stream().map(Variable::getName).collect(toList()))
              .containsExactly("var2", "var3");
          return serviceCommand;
        });

    when(templateService.constructEntityFromTemplate(eq(TEMPLATE_ID), eq(TEMPLATE_VERSION), eq(EntityType.COMMAND)))
        .thenReturn(aCommand()
                        .withTemplateVariables(asList(prepareVariable(1), prepareVariable(2)))
                        .withName(COMMAND_NAME)
                        .withTemplateId(TEMPLATE_ID)
                        .withTemplateVersion(TEMPLATE_VERSION)
                        .build());

    Command expectedCommand = aCommand()
                                  .withTemplateId(TEMPLATE_ID)
                                  .withTemplateVersion(TEMPLATE_VERSION)
                                  .withTemplateVariables(asList(prepareVariable(2), prepareVariable(3)))
                                  .build();

    Service service = serviceBuilder.build();
    service.getServiceCommands().add(aServiceCommand()
                                         .withTargetToAllEnv(true)
                                         .withAppId(APP_ID)
                                         .withServiceId(SERVICE_ID)
                                         .withUuid(ID_KEY)
                                         .withDefaultVersion(1)
                                         .withName("START")
                                         .withCommand(expectedCommand)
                                         .withTemplateUuid(TEMPLATE_ID)
                                         .withTemplateVersion(TEMPLATE_VERSION)
                                         .build());

    srs.addCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withServiceId(SERVICE_ID)
            .withTargetToAllEnv(true)
            .withCommand(expectedCommand)
            .withTemplateUuid(TEMPLATE_ID)
            .withTemplateVersion(TEMPLATE_VERSION)
            .build(),
        true);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
    verify(mockWingsPersistence).saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class));
  }

  private Variable prepareVariable(int idx) {
    return aVariable().name(format("var%d", idx)).value(format("val%d", idx)).build();
  }

  /**
   * Should add command state.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldAddCommandWithCommandUnits() {
    when(mockWingsPersistence.saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class)))
        .thenAnswer(invocation -> {
          ServiceCommand command = invocation.getArgument(1, ServiceCommand.class);
          command.setUuid(ID_KEY);
          return command;
        });

    Graph commandGraph = aGraph()
                             .withGraphName("START")
                             .addNodes(GraphNode.builder()
                                           .id("1")
                                           .origin(true)
                                           .type("EXEC")
                                           .properties(ImmutableMap.<String, Object>builder()
                                                           .put("commandPath", "/home/xxx/tomcat")
                                                           .put("command", "bin/startup.sh")
                                                           .build())
                                           .build())
                             .build();

    Command expectedCommand = aCommand().withGraph(commandGraph).build();
    expectedCommand.transformGraph();

    srs.addCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withCommand(aCommand().withCommandUnits(expectedCommand.getCommandUnits()).build())
            .build(),
        true);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
    verify(mockWingsPersistence).saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class));
  }

  private void prepeareEntityVersionServiceMocks() {
    when(entityVersionService.newEntityVersion(
             APP_ID, EntityType.COMMAND, ID_KEY, SERVICE_ID, "START", ChangeType.UPDATED, null))
        .thenReturn(anEntityVersion().withVersion(2).build());

    when(entityVersionService.lastEntityVersion(APP_ID, EntityType.COMMAND, ID_KEY, SERVICE_ID))
        .thenReturn(anEntityVersion().withVersion(1).build());
  }

  private void prepareServiceCommandMocks() {
    when(mockWingsPersistence.createUpdateOperations(ServiceCommand.class))
        .thenReturn(persistence.createUpdateOperations(ServiceCommand.class));
    when(mockWingsPersistence.createQuery(ServiceCommand.class))
        .thenReturn(persistence.createQuery(ServiceCommand.class));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnAddingCommandWithInvalidName() {
    assertThatThrownBy(()
                           -> srs.addCommand(APP_ID, SERVICE_ID,
                               aServiceCommand()
                                   .but()
                                   .withName("test & 1")
                                   .withTargetToAllEnv(true)
                                   .withCommand(aCommand().withName("test & 1").build())
                                   .build(),
                               true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Command Name can only have characters -, _, a-z, A-Z, 0-9 and space");
    verify(mockWingsPersistence).getWithAppId(Service.class, APP_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnUpdatingCommandWithInvalidName() {
    when(commandService.getServiceCommand(APP_ID, ID_KEY))
        .thenReturn(aServiceCommand()
                        .withName("ServiceCommand")
                        .withUuid(ID_KEY)
                        .withCommand(aCommand().withName("test").build())
                        .build());
    assertThatThrownBy(()
                           -> srs.updateCommand(APP_ID, SERVICE_ID,
                               aServiceCommand()
                                   .withTargetToAllEnv(true)
                                   .withUuid(ID_KEY)
                                   .withName("test & 1")
                                   .withCommand(aCommand().withName("test").build())
                                   .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Command Name can only have characters -, _, a-z, A-Z, 0-9 and space");
    verify(mockWingsPersistence).getWithAppId(Service.class, APP_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCommandWhenCommandChanged() {
    Command oldCommand =
        aCommand()
            .withName("START")
            .addCommandUnits(
                anExecCommandUnit().withCommandPath("/home/xxx/tomcat").withCommandString("bin/startup.sh").build())
            .build();

    oldCommand.setVersion(1L);

    PageRequest<ServiceCommand> serviceCommandPageRequest = getServiceCommandPageRequest(SERVICE_ID);

    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(aPageResponse()
                        .withResponse(asList(aServiceCommand()
                                                 .withUuid(ID_KEY)
                                                 .withTargetToAllEnv(true)
                                                 .withName("START")
                                                 .withDefaultVersion(1)
                                                 .withCommand(oldCommand)
                                                 .build()))
                        .build());

    prepareServiceCommandMocks();

    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(serviceBuilder.serviceCommands(asList(getStartCommand(oldCommand, aServiceCommand()))).build());

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    prepeareEntityVersionServiceMocks();

    Command expectedCommand =
        aCommand()
            .withName("START")
            .addCommandUnits(
                anExecCommandUnit().withCommandPath("/home/xxx/tomcat1").withCommandString("bin/startup.sh").build())
            .build();
    expectedCommand.setVersion(2L);

    when(commandService.getServiceCommand(APP_ID, ID_KEY)).thenReturn(aServiceCommand().withName("START").build());
    Service updatedService = srs.updateCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("START")
            .withCommand(expectedCommand)
            .build());

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence).update(any(Query.class), any(UpdateOperations.class));

    verify(mockWingsPersistence).createQuery(ServiceCommand.class);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);

    assertThat(updatedService).isNotNull();
  }

  /**
   * Should update command when command graph changed.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldUpdateCommandWhenNameChanged() {
    Graph oldCommandGraph = aGraph()
                                .withGraphName("START")
                                .addNodes(GraphNode.builder()
                                              .id("1")
                                              .origin(true)
                                              .type("EXEC")
                                              .rollback(true)
                                              .properties(ImmutableMap.<String, Object>builder()
                                                              .put("commandPath", "/home/xxx/tomcat")
                                                              .put("commandString", "bin/startup.sh")
                                                              .build())
                                              .build())
                                .build();

    Command oldCommand = aCommand().withGraph(oldCommandGraph).build();
    oldCommand.transformGraph();
    oldCommand.setVersion(1L);

    prepareServiceCommandMocks();

    when(mockWingsPersistence.createUpdateOperations(Command.class))
        .thenReturn(persistence.createUpdateOperations(Command.class));

    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(
            serviceBuilder.serviceCommands(ImmutableList.of(getStartCommand(oldCommand, aServiceCommand()))).build());

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    prepeareEntityVersionServiceMocks();

    Graph commandGraph = aGraph()
                             .withGraphName("START2")
                             .addNodes(GraphNode.builder()
                                           .id("1")
                                           .origin(true)
                                           .type("EXEC")
                                           .rollback(false)
                                           .properties(ImmutableMap.<String, Object>builder()
                                                           .put("commandPath", "/home/xxx/tomcat")
                                                           .put("commandString", "bin/startup.sh")
                                                           .build())
                                           .build())
                             .build();

    Command expectedCommand = aCommand().withGraph(commandGraph).build();
    expectedCommand.transformGraph();
    expectedCommand.setVersion(2L);

    when(commandService.getServiceCommand(APP_ID, ID_KEY)).thenReturn(aServiceCommand().withName("START2").build());
    srs.updateCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("START2")
            .withCommand(expectedCommand)
            .build());

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence, times(2)).update(any(Query.class), any(UpdateOperations.class));

    verify(mockWingsPersistence).createQuery(ServiceCommand.class);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCommandWhenCommandUnitsChanged() {
    Command oldCommand = commandBuilder.build();
    oldCommand.setVersion(1L);

    prepareServiceCommandMocks();

    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(
            serviceBuilder.serviceCommands(ImmutableList.of(getStartCommand(oldCommand, aServiceCommand()))).build());

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    prepeareEntityVersionServiceMocks();

    Command expectedCommand =
        aCommand()
            .withName("START")
            .addCommandUnits(
                anExecCommandUnit().withCommandPath("/home/xxx/tomcat1").withCommandString("bin/startup.sh").build())
            .build();
    expectedCommand.setVersion(2L);

    when(commandService.getServiceCommand(APP_ID, ID_KEY)).thenReturn(aServiceCommand().withName("START").build());
    srs.updateCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("START")
            .withDefaultVersion(1)
            .withCommand(expectedCommand)
            .build());

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence).update(any(Query.class), any(UpdateOperations.class));

    verify(mockWingsPersistence).createQuery(ServiceCommand.class);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);

    verify(commandService).save(expectedCommand, false);
  }

  /**
   * Should not update command nothing changed.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldUpdateCommandWhenCommandUnitsOrderChanged() {
    Graph oldCommandGraph = aGraph()
                                .withGraphName("START")
                                .addNodes(GraphNode.builder()
                                              .id("1")
                                              .origin(true)
                                              .name("Exec")
                                              .type("EXEC")
                                              .properties(ImmutableMap.<String, Object>builder()
                                                              .put("commandPath", "/home/xxx/tomcat")
                                                              .put("commandString", "bin/startup.sh")
                                                              .build())
                                              .build(),
                                    GraphNode.builder()
                                        .id("2")
                                        .origin(true)
                                        .name("Exec2")
                                        .type("EXEC")
                                        .properties(ImmutableMap.<String, Object>builder()
                                                        .put("commandPath", "/home/xxx/tomcat")
                                                        .put("commandString", "bin/startup.sh")
                                                        .build())
                                        .build())
                                .build();

    Command oldCommand = aCommand().withGraph(oldCommandGraph).build();
    oldCommand.transformGraph();
    oldCommand.setGraph(null);
    oldCommand.setVersion(1L);

    prepareServiceCommandMocks();

    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(
            serviceBuilder.serviceCommands(ImmutableList.of(getStartCommand(oldCommand, aServiceCommand()))).build());

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    prepeareEntityVersionServiceMocks();

    Graph commandGraph = aGraph()
                             .withGraphName("START")

                             .addNodes(GraphNode.builder()
                                           .id("2")
                                           .origin(true)
                                           .name("Exec2")
                                           .type("EXEC")
                                           .properties(ImmutableMap.<String, Object>builder()
                                                           .put("commandPath", "/home/xxx/tomcat")
                                                           .put("commandString", "bin/startup.sh")
                                                           .build())
                                           .build(),
                                 GraphNode.builder()
                                     .id("1")
                                     .origin(true)
                                     .type("EXEC")
                                     .name("Exec")
                                     .properties(ImmutableMap.<String, Object>builder()
                                                     .put("commandPath", "/home/xxx/tomcat")
                                                     .put("commandString", "bin/startup.sh")
                                                     .build())
                                     .build())
                             .build();

    Command expectedCommand = aCommand().withGraph(commandGraph).build();
    expectedCommand.transformGraph();
    expectedCommand.setGraph(null);
    expectedCommand.setVersion(2L);

    when(commandService.getServiceCommand(APP_ID, ID_KEY)).thenReturn(aServiceCommand().withName("START").build());
    srs.updateCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("START")
            .withDefaultVersion(1)
            .withCommand(expectedCommand)
            .build());

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence).update(any(Query.class), any(UpdateOperations.class));

    verify(mockWingsPersistence).createQuery(ServiceCommand.class);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);

    verify(commandService).save(expectedCommand, false);
  }

  /**
   * Should not update command nothing changed.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotUpdateCommandNothingChanged() {
    Command oldCommand = getCommand();

    prepareServiceCommandMocks();

    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(
            serviceBuilder.serviceCommands(ImmutableList.of(getStartCommand(oldCommand, aServiceCommand()))).build());

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    prepeareEntityVersionServiceMocks();

    Graph commandGraph = getGraph();

    Command expectedCommand = aCommand().withGraph(commandGraph).build();
    expectedCommand.transformGraph();
    expectedCommand.setVersion(2L);

    when(commandService.getServiceCommand(APP_ID, ID_KEY)).thenReturn(aServiceCommand().withName("START").build());
    srs.updateCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("START")
            .withDefaultVersion(1)
            .withCommand(expectedCommand)
            .build());

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence).update(any(Query.class), any(UpdateOperations.class));

    verify(mockWingsPersistence).createQuery(ServiceCommand.class);

    verify(commandService, never()).save(any(Command.class), eq(true));

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
  }

  /**
   * Should not update command nothing changed.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotUpdateCommandWhenCommandUnitsOrderNotChanged() {
    Graph oldCommandGraph = aGraph()
                                .withGraphName("START")
                                .addNodes(GraphNode.builder()
                                              .id("1")
                                              .origin(true)
                                              .name("EXEC")
                                              .type("EXEC")
                                              .properties(ImmutableMap.<String, Object>builder()
                                                              .put("commandPath", "/home/xxx/tomcat")
                                                              .put("commandString", "bin/startup.sh")
                                                              .build())
                                              .build())
                                .build();

    Command oldCommand = aCommand().withGraph(oldCommandGraph).build();
    oldCommand.transformGraph();
    oldCommand.setVersion(1L);

    prepareServiceCommandMocks();

    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(
            serviceBuilder.serviceCommands(ImmutableList.of(getStartCommand(oldCommand, aServiceCommand()))).build());

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    prepeareEntityVersionServiceMocks();

    Graph commandGraph = aGraph()
                             .withGraphName("START")
                             .addNodes(GraphNode.builder()
                                           .id("1")
                                           .name("EXEC")
                                           .origin(true)
                                           .type("EXEC")
                                           .properties(ImmutableMap.<String, Object>builder()
                                                           .put("commandPath", "/home/xxx/tomcat")
                                                           .put("commandString", "bin/startup.sh")
                                                           .build())
                                           .build())
                             .build();

    Command expectedCommand = aCommand().withGraph(commandGraph).build();
    expectedCommand.transformGraph();
    expectedCommand.setVersion(2L);

    when(commandService.getServiceCommand(APP_ID, ID_KEY)).thenReturn(aServiceCommand().withName("START").build());
    srs.updateCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("START")
            .withDefaultVersion(1)
            .withCommand(expectedCommand)
            .build());

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence).update(any(Query.class), any(UpdateOperations.class));

    verify(mockWingsPersistence).createQuery(ServiceCommand.class);

    verify(commandService, never()).save(any(Command.class), eq(true));

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
  }

  /**
   * Should not update command nothing changed.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotUpdateVersionWhenNothingChanged() {
    Command oldCommand = getCommand();

    prepareServiceCommandMocks();

    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(serviceBuilder
                        .serviceCommands(ImmutableList.of(aServiceCommand()
                                                              .withName("START")
                                                              .withUuid(ID_KEY)
                                                              .withAppId(APP_ID)
                                                              .withServiceId(SERVICE_ID)
                                                              .withDefaultVersion(1)
                                                              .withCommand(oldCommand)
                                                              .build()))
                        .build());

    when(entityVersionService.newEntityVersion(
             APP_ID, EntityType.COMMAND, ID_KEY, SERVICE_ID, "START", ChangeType.UPDATED, null))
        .thenReturn(anEntityVersion().withVersion(2).build());

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    when(entityVersionService.lastEntityVersion(APP_ID, EntityType.COMMAND, ID_KEY, SERVICE_ID))
        .thenReturn(anEntityVersion().withVersion(1).build());

    Graph commandGraph = getGraph();

    Command expectedCommand = aCommand().withGraph(commandGraph).build();
    expectedCommand.transformGraph();
    expectedCommand.setVersion(2L);

    when(commandService.getServiceCommand(APP_ID, ID_KEY)).thenReturn(aServiceCommand().withName("START").build());
    Service updatedService = srs.updateCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withDefaultVersion(1)
            .withUuid(ID_KEY)
            .withName("START")
            .withCommand(expectedCommand)
            .build());

    assertThat(updatedService).isNotNull();
    assertThat(updatedService.getServiceCommands()).isNotEmpty();
    assertThat(updatedService.getServiceCommands()).extracting("defaultVersion").contains(1);
    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence).update(any(Query.class), any(UpdateOperations.class));

    verify(mockWingsPersistence).createQuery(ServiceCommand.class);

    verify(commandService, never()).save(any(Command.class), eq(true));

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
  }

  /**
   * Should not update command nothing changed.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCommandNameAndTypeChanged() {
    Command oldCommand = getCommand();
    oldCommand.setGraph(null);
    oldCommand.setName("START");
    oldCommand.setCommandType(CommandType.START);

    prepareServiceCommandMocks();

    when(mockWingsPersistence.createUpdateOperations(Command.class))
        .thenReturn(persistence.createUpdateOperations(Command.class));
    when(mockWingsPersistence.createQuery(Command.class)).thenReturn(persistence.createQuery(Command.class));

    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(
            serviceBuilder
                .serviceCommands(ImmutableList.of(getStartCommand(oldCommand, aServiceCommand().withName("START"))))
                .build());

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    prepeareEntityVersionServiceMocks();

    Graph commandGraph = getGraph();

    Command expectedCommand = aCommand().withGraph(commandGraph).build();
    expectedCommand.transformGraph();
    expectedCommand.setVersion(2L);
    expectedCommand.setGraph(null);
    expectedCommand.setName("STOP");
    expectedCommand.setCommandType(STOP);

    when(commandService.getServiceCommand(APP_ID, ID_KEY)).thenReturn(aServiceCommand().withName("STOP").build());
    srs.updateCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("STOP")
            .withDefaultVersion(1)
            .withCommand(expectedCommand)
            .build());

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence).createUpdateOperations(Command.class);

    verify(mockWingsPersistence, times(2)).update(any(Query.class), any(UpdateOperations.class));

    verify(mockWingsPersistence).createQuery(ServiceCommand.class);

    verify(commandService, never()).save(any(Command.class), eq(true));

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
  }

  private ServiceCommand getStartCommand(Command oldCommand, ServiceCommand.Builder start) {
    return start.withTargetToAllEnv(true)
        .withUuid(ID_KEY)
        .withAppId(APP_ID)
        .withServiceId(SERVICE_ID)
        .withDefaultVersion(1)
        .withCommand(oldCommand)
        .build();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCommandsOrder() {
    Graph oldCommandGraph = aGraph()
                                .withGraphName("START")
                                .addNodes(GraphNode.builder()
                                              .id("1")
                                              .origin(true)
                                              .type("EXEC")
                                              .rollback(true)
                                              .properties(ImmutableMap.<String, Object>builder()
                                                              .put("commandPath", "/home/xxx/tomcat")
                                                              .put("commandString", "bin/startup.sh")
                                                              .build())
                                              .build())
                                .build();

    Command oldCommand = aCommand().withGraph(oldCommandGraph).build();
    oldCommand.transformGraph();
    oldCommand.setVersion(1L);
    oldCommand.setGraph(null);

    Graph commandGraph = aGraph()
                             .withGraphName("START")
                             .addNodes(GraphNode.builder()
                                           .id("1")
                                           .origin(true)
                                           .type("EXEC")
                                           .rollback(false)
                                           .properties(ImmutableMap.<String, Object>builder()
                                                           .put("commandPath", "/home/xxx/tomcat")
                                                           .put("commandString", "bin/startup2.sh")
                                                           .build())
                                           .build())
                             .build();

    Command expectedCommand = aCommand().withGraph(commandGraph).build();
    expectedCommand.transformGraph();
    expectedCommand.setVersion(2L);
    expectedCommand.setGraph(null);

    prepareServiceCommandMocks();

    List<ServiceCommand> serviceCommands = asList(aServiceCommand()
                                                      .withTargetToAllEnv(true)
                                                      .withUuid(ID_KEY)
                                                      .withName("EXEC")
                                                      .withAppId(APP_ID)
                                                      .withServiceId(SERVICE_ID)
                                                      .withDefaultVersion(1)
                                                      .withCommand(oldCommand)
                                                      .build(),
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("START")
            .withAppId(APP_ID)
            .withServiceId(SERVICE_ID)
            .withDefaultVersion(1)
            .withCommand(aCommand().withCommandUnits(expectedCommand.getCommandUnits()).build())
            .build());

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    PageRequest<ServiceCommand> serviceCommandPageRequest = getServiceCommandPageRequest(SERVICE_ID);

    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(aPageResponse().withResponse(serviceCommands).build());

    Service service = Service.builder().uuid(SERVICE_ID).appId(APP_ID).serviceCommands(serviceCommands).build();
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID)).thenReturn(service);

    prepeareEntityVersionServiceMocks();

    service = srs.updateCommandsOrder(APP_ID, SERVICE_ID, serviceCommands);

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence, times(2)).createQuery(ServiceCommand.class);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);

    assertThat(service.getServiceCommands()).extracting(ServiceCommand::getName).containsSequence("EXEC", "START");
    assertThat(service.getServiceCommands()).extracting(ServiceCommand::getOrder).containsSequence(0.0, 0.0);

    serviceCommands = asList(aServiceCommand()
                                 .withTargetToAllEnv(true)
                                 .withUuid(ID_KEY)
                                 .withName("START")
                                 .withAppId(APP_ID)
                                 .withServiceId(SERVICE_ID)
                                 .withDefaultVersion(1)
                                 .withCommand(aCommand().withCommandUnits(expectedCommand.getCommandUnits()).build())
                                 .build(),
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("EXEC")
            .withAppId(APP_ID)
            .withServiceId(SERVICE_ID)
            .withDefaultVersion(1)
            .withCommand(oldCommand)
            .build());

    service = srs.updateCommandsOrder(APP_ID, SERVICE_ID, serviceCommands);

    assertThat(service.getServiceCommands()).extracting(ServiceCommand::getName).containsSequence("EXEC", "START");

    verify(mockWingsPersistence, times(4)).update(any(Query.class), any(UpdateOperations.class));
  }

  /**
   * Should delete command state.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldDeleteCommand() {
    when(workflowService.listWorkflows(any(PageRequest.class)))
        .thenReturn(aPageResponse().withResponse(asList()).build());
    when(mockWingsPersistence.delete(any(Query.class))).thenReturn(true);
    when(mockWingsPersistence.delete(any(ServiceCommand.class))).thenReturn(true);
    srs.deleteCommand(APP_ID, SERVICE_ID, SERVICE_COMMAND_ID);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    verify(mockWingsPersistence, times(1)).getWithAppId(ServiceCommand.class, APP_ID, SERVICE_COMMAND_ID);
    verify(workflowService, times(1)).listWorkflows(any(PageRequest.class));
    verify(mockWingsPersistence, times(1)).createQuery(Command.class);
    verify(mockWingsPersistence, times(1)).delete(any(ServiceCommand.class));
    verify(mockWingsPersistence, times(1)).delete(any(Query.class));
    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnReferencedServiceCommandDelete() {
    ServiceCommand serviceCommand = serviceCommandBuilder.but().build();
    when(workflowService.listWorkflows(any(PageRequest.class))).thenReturn(listWorkflows(serviceCommand, "START"));
    assertThatThrownBy(() -> srs.deleteCommand(APP_ID, SERVICE_ID, SERVICE_COMMAND_ID))
        .isInstanceOf(WingsException.class)
        .hasMessage("Command [START] couldn't be deleted. "
            + "Remove reference from the following workflows [ (WORKFLOW_NAME:null:Phase 1) ]");
    verify(mockWingsPersistence).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    verify(mockWingsPersistence).getWithAppId(ServiceCommand.class, APP_ID, SERVICE_COMMAND_ID);
    verify(workflowService).listWorkflows(any(PageRequest.class));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotThrowExceptionOnReferencedServiceCommandDelete() {
    ServiceCommand serviceCommand = serviceCommandBuilder.but().build();
    when(workflowService.listWorkflows(any(PageRequest.class))).thenReturn(listWorkflows(serviceCommand, "Stop"));
    when(mockWingsPersistence.delete(any(Query.class))).thenReturn(true);
    when(mockWingsPersistence.delete(any(ServiceCommand.class))).thenReturn(true);

    srs.deleteCommand(APP_ID, SERVICE_ID, SERVICE_COMMAND_ID);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    verify(mockWingsPersistence, times(1)).getWithAppId(ServiceCommand.class, APP_ID, SERVICE_COMMAND_ID);
    verify(workflowService, times(1)).listWorkflows(any(PageRequest.class));
    verify(mockWingsPersistence, times(1)).createQuery(Command.class);
    verify(mockWingsPersistence, times(1)).delete(any(ServiceCommand.class));
    verify(mockWingsPersistence, times(1)).delete(any(Query.class));
    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
  }

  private PageResponse listWorkflows(ServiceCommand serviceCommand, String stop) {
    return aPageResponse()
        .withResponse(asList(
            WorkflowBuilder.aWorkflow()
                .name(WORKFLOW_NAME)
                .services(asList(
                    Service.builder().uuid(SERVICE_ID).appId(APP_ID).serviceCommands(asList(serviceCommand)).build()))
                .orchestrationWorkflow(
                    aCanaryOrchestrationWorkflow()
                        .withWorkflowPhases(asList(
                            aWorkflowPhase()
                                .serviceId(SERVICE_ID)
                                .phaseSteps(asList(aPhaseStep(PhaseStepType.STOP_SERVICE, "Phase 1")
                                                       .addStep(GraphNode.builder()
                                                                    .type("COMMAND")
                                                                    .properties(ImmutableMap.<String, Object>builder()
                                                                                    .put("commandName", stop)
                                                                                    .build())
                                                                    .build())
                                                       .build()))
                                .build()))
                        .build())
                .build()))
        .build();
  }

  /**
   * Should get command stencils.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldGetCommandStencils() {
    when(mockWingsPersistence.getWithAppId(eq(Service.class), anyString(), anyString()))
        .thenReturn(serviceBuilder
                        .serviceCommands(ImmutableList.of(aServiceCommand()
                                                              .withTargetToAllEnv(true)
                                                              .withName("START")
                                                              .withDefaultVersion(1)
                                                              .withCommand(commandBuilder.build())
                                                              .build(),
                            aServiceCommand()
                                .withDefaultVersion(1)
                                .withTargetToAllEnv(true)
                                .withName("START2")
                                .withCommand(commandBuilder.but().withName("START2").build())
                                .build()))
                        .build());

    PageRequest<ServiceCommand> serviceCommandPageRequest = getServiceCommandPageRequest(SERVICE_ID);

    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(getServiceCommandPageResponse());

    List<Stencil> commandStencils = srs.getCommandStencils(APP_ID, SERVICE_ID, null);

    assertThat(commandStencils)
        .isNotNull()
        .hasSize(values().length + 1)
        .extracting(Stencil::getName)
        .contains("START", "START2");

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
  }

  private PageResponse getServiceCommandPageResponse() {
    return aPageResponse()
        .withResponse(asList(aServiceCommand()
                                 .withTargetToAllEnv(false)
                                 .withName("START")
                                 .withDefaultVersion(1)
                                 .withCommand(commandBuilder.build())
                                 .build(),
            aServiceCommand()
                .withTargetToAllEnv(true)
                .withName("START2")
                .withDefaultVersion(1)
                .withCommand(commandBuilder.but().withName("START2").build())
                .build()))
        .build();
  }

  /**
   * Should get command stencils.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldGetScriptCommandStencilsOnly() {
    when(mockWingsPersistence.getWithAppId(eq(Service.class), anyString(), anyString()))
        .thenReturn(serviceBuilder
                        .serviceCommands(ImmutableList.of(aServiceCommand()
                                                              .withTargetToAllEnv(true)
                                                              .withName("START")
                                                              .withDefaultVersion(1)
                                                              .withCommand(commandBuilder.build())
                                                              .build(),
                            aServiceCommand()
                                .withDefaultVersion(1)
                                .withTargetToAllEnv(true)
                                .withName("START2")
                                .withCommand(commandBuilder.but().withName("START2").build())
                                .build()))
                        .build());

    PageRequest<ServiceCommand> serviceCommandPageRequest = getServiceCommandPageRequest(SERVICE_ID);

    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(getServiceCommandPageResponse());

    List<Stencil> commandStencils = srs.getCommandStencils(APP_ID, SERVICE_ID, null, true);

    assertThat(commandStencils).isNotNull().extracting(Stencil::getName).contains("START", "START2");

    assertThat(commandStencils)
        .isNotNull()
        .extracting(Stencil::getTypeClass)
        .isNotOfAnyClassIn(CodeDeployCommandUnit.class, AwsLambdaCommandUnit.class, AmiCommandUnit.class);

    assertThat(commandStencils).isNotNull().extracting(Stencil::getStencilCategory).doesNotContain(CONTAINERS);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
  }

  /**
   * Should get command categories
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetCommandCategories() {
    when(mockWingsPersistence.createQuery(ServiceCommand.class)).thenReturn(serviceCommandQuery);
    when(serviceCommandQuery.project("name", true)).thenReturn(serviceCommandQuery);
    when(serviceCommandQuery.filter("appId", APP_ID)).thenReturn(serviceCommandQuery);
    when(serviceCommandQuery.filter("serviceId", SERVICE_ID)).thenReturn(serviceCommandQuery);

    when(serviceCommandQuery.asList())
        .thenReturn(asList(aServiceCommand()
                               .withTargetToAllEnv(false)
                               .withName("START")
                               .withDefaultVersion(1)
                               .withCommand(commandBuilder.build())
                               .build(),
            aServiceCommand()
                .withTargetToAllEnv(true)
                .withName("START2")
                .withDefaultVersion(1)
                .withCommand(commandBuilder.but().withName("START2").build())
                .build()));

    List<CommandCategory> commandCategories = srs.getCommandCategories(APP_ID, SERVICE_ID, "MyCommand");
    assertCommandCategories(commandCategories);
  }

  /**
   * Should get command by name.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetCommandByName() {
    when(mockWingsPersistence.getWithAppId(eq(Service.class), anyString(), anyString()))
        .thenReturn(serviceBuilder
                        .serviceCommands(ImmutableList.of(aServiceCommand()
                                                              .withTargetToAllEnv(true)
                                                              .withName("START")
                                                              .withDefaultVersion(1)
                                                              .withCommand(commandBuilder.build())
                                                              .build()))
                        .build());

    assertThat(srs.getCommandByName(APP_ID, SERVICE_ID, "START")).isNotNull();

    verify(mockWingsPersistence, times(1)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetCommandByNameAndEnv() {
    when(mockWingsPersistence.getWithAppId(eq(Service.class), anyString(), anyString()))
        .thenReturn(serviceBuilder
                        .serviceCommands(ImmutableList.of(aServiceCommand()
                                                              .withTargetToAllEnv(true)
                                                              .withName("START")
                                                              .withDefaultVersion(1)
                                                              .withCommand(commandBuilder.build())
                                                              .build()))
                        .build());

    assertThat(srs.getCommandByName(APP_ID, SERVICE_ID, ENV_ID, "START")).isNotNull();

    verify(mockWingsPersistence, times(1)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetCommandByNameAndEnvForSpecificEnv() {
    when(mockWingsPersistence.getWithAppId(eq(Service.class), anyString(), anyString()))
        .thenReturn(serviceBuilder
                        .serviceCommands(ImmutableList.of(
                            aServiceCommand()
                                .withEnvIdVersionMap(ImmutableMap.of(ENV_ID, anEntityVersion().withVersion(2).build()))
                                .withName("START")
                                .withDefaultVersion(1)
                                .withCommand(commandBuilder.build())
                                .build()))
                        .build());

    assertThat(srs.getCommandByName(APP_ID, SERVICE_ID, ENV_ID, "START")).isNotNull();

    verify(mockWingsPersistence, times(1)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetCommandByNameAndEnvForSpecificEnvNotTargetted() {
    when(mockWingsPersistence.query(ServiceCommand.class, serviceCommandPageRequest))
        .thenReturn(aPageResponse()
                        .withResponse(asList(aServiceCommand()
                                                 .withTargetToAllEnv(false)
                                                 .withName("START")
                                                 .withDefaultVersion(1)
                                                 .withCommand(commandBuilder.build())
                                                 .build()))
                        .build());

    assertThat(srs.getCommandByName(APP_ID, SERVICE_ID, ENV_ID, "START")).isNull();

    verify(mockWingsPersistence, times(1)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
  }

  private FunctionSpecification createFunctionSpecification(String functionName, Integer timeout, Integer memorySize) {
    return FunctionSpecification.builder()
        .runtime("TestRunTime")
        .functionName(functionName)
        .handler("TestHandler")
        .timeout(timeout)
        .memorySize(memorySize)
        .build();
  }

  private void verifyLambdaSpec(LambdaSpecification lambdaSpecification, String expectedMessage) {
    try {
      srs.updateLambdaSpecification(lambdaSpecification);
      fail("Should not reach here");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).isEqualTo(expectedMessage);
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testLambdaTimeoutValidation() {
    LambdaSpecification lambdaSpecification = LambdaSpecification.builder().serviceId("TestServiceID").build();
    lambdaSpecification.setAppId(APP_ID);

    lambdaSpecification.setDefaults(null);
    verifyLambdaSpec(lambdaSpecification, "Defaults must exist in Lambda Specification");

    DefaultSpecification defaultSpecification = DefaultSpecification.builder().build();
    lambdaSpecification.setDefaults(defaultSpecification);

    defaultSpecification.setRuntime(null);
    verifyLambdaSpec(lambdaSpecification, "Runtime in Defaults for Lambda Specification must not be empty");
    defaultSpecification.setRuntime("  ");
    verifyLambdaSpec(lambdaSpecification, "Runtime in Defaults for Lambda Specification must not be empty");
    defaultSpecification.setRuntime("runTime");

    defaultSpecification.setMemorySize(-128);
    verifyLambdaSpec(lambdaSpecification, "Memory Size in Defaults for Lambda Specification must be greater than 0");
    defaultSpecification.setMemorySize(0);
    verifyLambdaSpec(lambdaSpecification, "Memory Size in Defaults for Lambda Specification must be greater than 0");
    defaultSpecification.setMemorySize(12);

    defaultSpecification.setTimeout(null);
    verifyLambdaSpec(
        lambdaSpecification, "Execution Timeout in Defaults for Lambda Specification must be greater than 0");
    defaultSpecification.setTimeout(-1);
    verifyLambdaSpec(
        lambdaSpecification, "Execution Timeout in Defaults for Lambda Specification must be greater than 0");
    defaultSpecification.setTimeout(0);
    verifyLambdaSpec(
        lambdaSpecification, "Execution Timeout in Defaults for Lambda Specification must be greater than 0");
    defaultSpecification.setTimeout(1);

    FunctionSpecification function = FunctionSpecification.builder().build();
    verifyLambdaSpec(lambdaSpecification, "Lambda Specification must contain atleast 1 function");
    lambdaSpecification.setFunctions(asList(function));

    function.setFunctionName(" ");
    verifyLambdaSpec(lambdaSpecification, "Function name must not be empty");
    function.setFunctionName(null);
    verifyLambdaSpec(lambdaSpecification, "Function name must not be empty");
    function.setFunctionName("func1");

    function.setHandler(null);
    verifyLambdaSpec(lambdaSpecification, "Handler must not be empty for function func1");
    function.setHandler("  ");
    verifyLambdaSpec(lambdaSpecification, "Handler must not be empty for function func1");
    function.setHandler("handler");

    function.setRuntime(null);
    verifyLambdaSpec(lambdaSpecification, "Runtime must not be empty for function func1");
    function.setRuntime("  ");
    verifyLambdaSpec(lambdaSpecification, "Runtime must not be empty for function func1");
    function.setRuntime("runTime");

    function.setMemorySize(null);
    verifyLambdaSpec(lambdaSpecification, "Memory Size must be greater than 0 for function func1");
    function.setMemorySize(0);
    verifyLambdaSpec(lambdaSpecification, "Memory Size must be greater than 0 for function func1");
    function.setMemorySize(-1);
    verifyLambdaSpec(lambdaSpecification, "Memory Size must be greater than 0 for function func1");
    function.setMemorySize(2);

    function.setTimeout(null);
    verifyLambdaSpec(lambdaSpecification, "Execution Timeout must be greater than 0 for function func1");
    function.setTimeout(0);
    verifyLambdaSpec(lambdaSpecification, "Execution Timeout must be greater than 0 for function func1");
    function.setTimeout(-1);
    verifyLambdaSpec(lambdaSpecification, "Execution Timeout must be greater than 0 for function func1");
    function.setTimeout(2);

    when(mockWingsPersistence.saveAndGet(LambdaSpecification.class, lambdaSpecification))
        .thenReturn(lambdaSpecification);
    srs.createLambdaSpecification(lambdaSpecification);
    verify(mockWingsPersistence, times(1)).saveAndGet(LambdaSpecification.class, lambdaSpecification);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testLambdaValidation() {
    FunctionSpecification functionSpecification = FunctionSpecification.builder()
                                                      .runtime("TestRunTime")
                                                      .functionName("TestFunctionName")
                                                      .handler("TestHandler")
                                                      .timeout(2)
                                                      .memorySize(23)
                                                      .build();
    LambdaSpecification lambdaSpecification =
        LambdaSpecification.builder()
            .defaults(DefaultSpecification.builder().timeout(1).memorySize(2).runtime("runTime").build())
            .serviceId("TestServiceID")
            .functions(asList(functionSpecification, functionSpecification))
            .build();
    lambdaSpecification.setAppId("TestAppID");
    try {
      srs.updateLambdaSpecification(lambdaSpecification);
      fail("Should have thrown a wingsException");
    } catch (WingsException e) {
      log.info("Expected exception");
    }

    FunctionSpecification functionSpecification2 = FunctionSpecification.builder()
                                                       .runtime("TestRunTime")
                                                       .functionName("TestFunctionName2")
                                                       .handler("TestHandler")
                                                       .timeout(2)
                                                       .memorySize(23)
                                                       .build();
    lambdaSpecification =
        LambdaSpecification.builder()
            .serviceId("TestServiceID")
            .defaults(DefaultSpecification.builder().timeout(1).memorySize(2).runtime("runTime").build())
            .functions(asList(functionSpecification, functionSpecification2))
            .build();
    lambdaSpecification.setAppId("TestAppID");
    when(mockWingsPersistence.saveAndGet(Mockito.any(Class.class), Mockito.any(LambdaSpecification.class)))
        .thenReturn(lambdaSpecification);

    try {
      srs.updateLambdaSpecification(lambdaSpecification);
    } catch (WingsException e) {
      fail("Should not have thrown a wingsException", e);
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldUpdateContainerTaskAdvanced() {
    persistence.save(Service.builder().uuid(SERVICE_ID).appId(APP_ID).build());
    KubernetesContainerTask containerTask = new KubernetesContainerTask();
    containerTask.setAppId(APP_ID);
    containerTask.setServiceId(SERVICE_ID);
    containerTask.setUuid("TASK_ID");
    persistence.save(containerTask);
    KubernetesPayload payload = new KubernetesPayload();

    when(mockWingsPersistence.saveAndGet(ContainerTask.class, containerTask)).thenAnswer(t -> t.getArguments()[1]);

    payload.setAdvancedConfig("${DOCKER_IMAGE_NAME}");
    KubernetesContainerTask result =
        (KubernetesContainerTask) srs.updateContainerTaskAdvanced(APP_ID, SERVICE_ID, "TASK_ID", payload, false);
    assertThat(result.getAdvancedConfig()).isEqualTo("${DOCKER_IMAGE_NAME}");

    payload.setAdvancedConfig("a\n${DOCKER_IMAGE_NAME}");
    result = (KubernetesContainerTask) srs.updateContainerTaskAdvanced(APP_ID, SERVICE_ID, "TASK_ID", payload, false);
    assertThat(result.getAdvancedConfig()).isEqualTo("a\n${DOCKER_IMAGE_NAME}");

    payload.setAdvancedConfig("a \n${DOCKER_IMAGE_NAME}");
    result = (KubernetesContainerTask) srs.updateContainerTaskAdvanced(APP_ID, SERVICE_ID, "TASK_ID", payload, false);
    assertThat(result.getAdvancedConfig()).isEqualTo("a\n${DOCKER_IMAGE_NAME}");

    payload.setAdvancedConfig("a    \n b   \n  ${DOCKER_IMAGE_NAME}");
    result = (KubernetesContainerTask) srs.updateContainerTaskAdvanced(APP_ID, SERVICE_ID, "TASK_ID", payload, false);
    assertThat(result.getAdvancedConfig()).isEqualTo("a\n b\n  ${DOCKER_IMAGE_NAME}");

    result = (KubernetesContainerTask) srs.updateContainerTaskAdvanced(APP_ID, SERVICE_ID, "TASK_ID", null, true);
    assertThat(result.getAdvancedConfig()).isNull();
  }

  private PageRequest getServiceCommandPageRequest(String serviceId) {
    return aPageRequest()
        .withLimit(PageRequest.UNLIMITED)
        .addFilter("appId", EQ, APP_ID)
        .addFilter("serviceId", EQ, serviceId)
        .build();
  }

  private PageRequest<ContainerTask> getContainerTaskPageRequest(String serviceId) {
    return aPageRequest().addFilter("appId", EQ, APP_ID).addFilter("serviceId", EQ, SERVICE_ID).build();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCompareCommandUnits() {
    assertThatCode(() -> {
      ObjectDifferBuilder.buildDefault().compare(anExecCommandUnit().build(), anExecCommandUnit().build());
      ObjectDifferBuilder.buildDefault().compare(aScpCommandUnit().build(), aScpCommandUnit().build());
      ObjectDifferBuilder.buildDefault().compare(new CleanupSshCommandUnit(), new CleanupSshCommandUnit());
      ObjectDifferBuilder.buildDefault().compare(new CopyConfigCommandUnit(), new CopyConfigCommandUnit());
      ObjectDifferBuilder.buildDefault().compare(new InitSshCommandUnit(), new InitSshCommandUnit());
      ObjectDifferBuilder.buildDefault().compare(new InitSshCommandUnitV2(), new InitSshCommandUnitV2());
    }).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddCommandFromTemplate() {
    when(mockWingsPersistence.saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class)))
        .thenAnswer(invocation -> {
          ServiceCommand command = invocation.getArgument(1, ServiceCommand.class);
          command.setUuid(ID_KEY);
          return command;
        });

    Command expectedCommand = commandBuilder.build();

    when(templateService.constructEntityFromTemplate(TEMPLATE_ID, LATEST_TAG, EntityType.COMMAND))
        .thenReturn(expectedCommand);

    srs.addCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTemplateUuid(TEMPLATE_ID)
            .withTemplateVersion(LATEST_TAG)
            .withTargetToAllEnv(true)
            .withCommand(expectedCommand)
            .build(),
        true);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);
    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);
    verify(mockWingsPersistence).saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class));
    verify(templateService).constructEntityFromTemplate(TEMPLATE_ID, LATEST_TAG, EntityType.COMMAND);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateCommandLinkedToTemplate() {
    Command oldCommand = commandBuilder.build();
    oldCommand.setVersion(1L);

    prepareServiceCommandMocks();

    ServiceCommand serviceCommand = getTemplateServiceCommand(oldCommand);
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(serviceBuilder.serviceCommands(ImmutableList.of(serviceCommand)).build());

    serviceCommand.setName("START");

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    prepeareEntityVersionServiceMocks();

    Command expectedCommand =
        aCommand()
            .withName("START")
            .addCommandUnits(
                anExecCommandUnit().withCommandPath("/home/xxx/tomcat1").withCommandString("bin/startup.sh").build())
            .build();
    expectedCommand.setVersion(2L);
    expectedCommand.setAppId(APP_ID);

    when(commandService.getServiceCommand(APP_ID, ID_KEY)).thenReturn(serviceCommand);

    Template template = getTemplate();

    when(templateService.get(TEMPLATE_ID, "1")).thenReturn(template);

    srs.updateCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("START")
            .withDefaultVersion(1)
            .withTemplateUuid(TEMPLATE_ID)
            .withTemplateVersion("1")
            .withCommand(expectedCommand)
            .build());

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence).update(any(Query.class), any(UpdateOperations.class));

    verify(mockWingsPersistence).createQuery(ServiceCommand.class);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);

    verify(commandService).save(Mockito.any(Command.class), Mockito.anyBoolean());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateLinkedCommandVariables() {
    Command oldCommand = commandBuilder.build();
    oldCommand.setVersion(1L);

    prepareServiceCommandMocks();

    ServiceCommand serviceCommand = getTemplateServiceCommand(oldCommand);
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(serviceBuilder.serviceCommands(ImmutableList.of(serviceCommand)).build());

    serviceCommand.setName("START");

    when(commandService.getCommand(APP_ID, ID_KEY, 1)).thenReturn(oldCommand);

    prepeareEntityVersionServiceMocks();

    Command expectedCommand =
        aCommand()
            .withName("START")
            .withTemplateVariables(asList(aVariable().name("MyVar").value("MyValue").build()))
            .addCommandUnits(
                anExecCommandUnit().withCommandPath("/home/xxx/tomcat1").withCommandString("bin/startup.sh").build())
            .build();
    expectedCommand.setVersion(2L);
    expectedCommand.setAppId(APP_ID);

    when(commandService.getServiceCommand(APP_ID, ID_KEY)).thenReturn(serviceCommand);

    Template template = getTemplate();

    when(templateService.get(TEMPLATE_ID, "1")).thenReturn(template);

    srs.updateCommand(APP_ID, SERVICE_ID,
        aServiceCommand()
            .withTargetToAllEnv(true)
            .withUuid(ID_KEY)
            .withName("START")
            .withDefaultVersion(1)
            .withTemplateUuid(TEMPLATE_ID)
            .withTemplateVersion(LATEST_TAG)
            .withCommand(expectedCommand)
            .build());

    verify(mockWingsPersistence).createUpdateOperations(ServiceCommand.class);

    verify(mockWingsPersistence).update(any(Query.class), any(UpdateOperations.class));

    verify(mockWingsPersistence).createQuery(ServiceCommand.class);

    verify(mockWingsPersistence, times(2)).getWithAppId(Service.class, APP_ID, SERVICE_ID);

    verify(configService).getConfigFilesForEntity(APP_ID, DEFAULT_TEMPLATE_ID, SERVICE_ID);

    verify(commandService).save(Mockito.any(Command.class), Mockito.anyBoolean());
  }

  private ServiceCommand getTemplateServiceCommand(Command oldCommand) {
    return aServiceCommand()
        .withTargetToAllEnv(true)
        .withUuid(ID_KEY)
        .withAppId(APP_ID)
        .withServiceId(SERVICE_ID)
        .withTemplateUuid(TEMPLATE_ID)
        .withTemplateVersion(LATEST_TAG)
        .withDefaultVersion(1)
        .withCommand(oldCommand)
        .withName("ServiceCommand")
        .build();
  }

  private Template getTemplate() {
    SshCommandTemplate sshCommandTemplate = SshCommandTemplate.builder()
                                                .commandType(START)
                                                .commandUnits(asList(anExecCommandUnit()
                                                                         .withName("Start")
                                                                         .withCommandPath("/home/xxx/tomcat")
                                                                         .withCommandString("bin/startup.sh")
                                                                         .build()))
                                                .build();

    return Template.builder()
        .templateObject(sshCommandTemplate)
        .name("My Start Command")
        .description(TEMPLATE_DESC)
        .folderPath("Harness/Tomcat Commands/Standard")
        .keywords(ImmutableSet.of(TEMPLATE_CUSTOM_KEYWORD))
        .gallery(HARNESS_GALLERY)
        .appId(GLOBAL_APP_ID)
        .accountId(GLOBAL_ACCOUNT_ID)
        .build();
  }

  public void assertCommandCategories(List<CommandCategory> commandCategories) {
    assertThat(commandCategories).isNotEmpty();
    assertThat(commandCategories)
        .isNotEmpty()
        .extracting(CommandCategory::getType)
        .contains(CommandCategory.Type.values());
    assertThat(commandCategories)
        .isNotEmpty()
        .extracting(CommandCategory::getDisplayName)
        .contains(
            COMMANDS.getDisplayName(), COPY.getDisplayName(), SCRIPTS.getDisplayName(), VERIFICATIONS.getDisplayName());

    List<CommandCategory> copyCategories =
        commandCategories.stream().filter(commandCategory -> commandCategory.getType() == COPY).collect(toList());
    assertThat(copyCategories).extracting(CommandCategory::getCommandUnits).isNotEmpty();
    copyCategories.forEach(commandCategory -> {
      assertThat(commandCategory.getType()).isEqualTo(COPY);
      assertThat(commandCategory.getDisplayName()).isEqualTo(COPY.getDisplayName());
      assertThat(commandCategory.getCommandUnits())
          .isNotEmpty()
          .extracting(CommandCategory.CommandUnit::getType)
          .contains(COPY_CONFIGS, SCP);
    });
    List<CommandCategory> scriptCategories =
        commandCategories.stream().filter(commandCategory -> commandCategory.getType() == SCRIPTS).collect(toList());
    assertThat(scriptCategories).extracting(CommandCategory::getCommandUnits).isNotEmpty();
    scriptCategories.forEach(commandCategory -> {
      assertThat(commandCategory.getType()).isEqualTo(SCRIPTS);
      assertThat(commandCategory.getDisplayName()).isEqualTo(SCRIPTS.getDisplayName());
      assertThat(commandCategory.getCommandUnits())
          .isNotEmpty()
          .extracting(CommandCategory.CommandUnit::getType)
          .contains(EXEC, DOCKER_START, DOCKER_STOP);
    });

    List<CommandCategory> commandCommandCategories =
        commandCategories.stream().filter(commandCategory -> commandCategory.getType() == COMMANDS).collect(toList());
    assertThat(commandCommandCategories).extracting(CommandCategory::getCommandUnits).isNotEmpty();
    commandCommandCategories.forEach(commandCategory -> {
      assertThat(commandCategory.getType()).isEqualTo(COMMANDS);
      assertThat(commandCategory.getDisplayName()).isEqualTo(COMMANDS.getDisplayName());
      assertThat(commandCategory.getCommandUnits())
          .isNotEmpty()
          .extracting(CommandCategory.CommandUnit::getType)
          .contains(COMMAND, COMMAND);
      assertThat(commandCategory.getCommandUnits())
          .isNotEmpty()
          .extracting(CommandCategory.CommandUnit::getName)
          .contains("START", "START2");
    });

    List<CommandCategory> verifyCategories = commandCategories.stream()
                                                 .filter(commandCategory -> commandCategory.getType() == VERIFICATIONS)
                                                 .collect(toList());
    assertThat(verifyCategories).extracting(CommandCategory::getCommandUnits).isNotEmpty();
    verifyCategories.forEach(commandCategory -> {
      assertThat(commandCategory.getType()).isEqualTo(VERIFICATIONS);
      assertThat(commandCategory.getDisplayName()).isEqualTo(VERIFICATIONS.getDisplayName());
      assertThat(commandCategory.getCommandUnits())
          .isNotEmpty()
          .extracting(CommandCategory.CommandUnit::getType)
          .contains(PROCESS_CHECK_RUNNING, PORT_CHECK_CLEARED, PORT_CHECK_LISTENING);
    });
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreatePCFSpecCreatesManifestFile() {
    String fileContent = "pcf spec";

    Query mockQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(any())).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), anyString())).thenReturn(mockQuery);
    when(mockQuery.getKey()).thenReturn(new Key<>(Service.class, "services", SERVICE_ID));

    PcfServiceSpecification pcfServiceSpecification =
        PcfServiceSpecification.builder().serviceId(SERVICE_ID).manifestYaml(fileContent).build();
    pcfServiceSpecification.setAppId(APP_ID);

    when(mockWingsPersistence.saveAndGet(any(), any())).thenReturn(pcfServiceSpecification);
    when(applicationManifestService.create(any()))
        .thenReturn(ApplicationManifest.builder().storeType(StoreType.Local).build());
    when(applicationManifestService.getManifestFileByFileName(any(), any())).thenReturn(ManifestFile.builder().build());

    srs.createPcfServiceSpecification(pcfServiceSpecification);
    // applicationManifestService.create(applicationManifest);
    verify(applicationManifestService, times(1)).create(any(ApplicationManifest.class));
    ArgumentCaptor<ManifestFile> manifestFileArgumentCaptor = ArgumentCaptor.forClass(ManifestFile.class);
    verify(applicationManifestService, times(1))
        .upsertApplicationManifestFile(
            manifestFileArgumentCaptor.capture(), any(ApplicationManifest.class), any(Boolean.class));

    ManifestFile manifestFile = manifestFileArgumentCaptor.getValue();
    assertThat(manifestFile.getFileContent()).isEqualTo(fileContent + "\n");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdatePCFSpecUpdatesManifestFile() {
    String fileContent = "pcf spec";

    Query mockQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(any())).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), anyString())).thenReturn(mockQuery);
    when(mockQuery.getKey()).thenReturn(new Key<>(Service.class, "services", SERVICE_ID));
    PcfServiceSpecification pcfServiceSpecification =
        PcfServiceSpecification.builder().serviceId(SERVICE_ID).manifestYaml(fileContent).build();
    pcfServiceSpecification.setAppId(APP_ID);

    when(mockWingsPersistence.saveAndGet(any(), any())).thenReturn(pcfServiceSpecification);
    when(applicationManifestService.getManifestByServiceId(APP_ID, SERVICE_ID))
        .thenReturn(ApplicationManifest.builder().storeType(StoreType.Local).build());
    when(applicationManifestService.getManifestFileByFileName(any(), any())).thenReturn(ManifestFile.builder().build());

    srs.updatePcfServiceSpecification(pcfServiceSpecification);
    verify(applicationManifestService, times(0)).create(any(ApplicationManifest.class));
    ArgumentCaptor<ManifestFile> manifestFileArgumentCaptor = ArgumentCaptor.forClass(ManifestFile.class);
    verify(applicationManifestService, times(1))
        .upsertApplicationManifestFile(
            manifestFileArgumentCaptor.capture(), any(ApplicationManifest.class), any(Boolean.class));

    ManifestFile manifestFile = manifestFileArgumentCaptor.getValue();
    assertThat(manifestFile.getFileContent()).isEqualTo(fileContent + "\n");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreatePCFV2Service() throws IOException {
    doNothing().when(harnessTagService).attachTag(any());
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    Service service = Service.builder()
                          .appId(APP_ID)
                          .accountId(ACCOUNT_ID)
                          .uuid(SERVICE_ID)
                          .deploymentType(DeploymentType.PCF)
                          .name("PCFV2")
                          .build();

    doReturn(service).when(spyServiceResourceService).addCommand(any(), any(), any(ServiceCommand.class), eq(true));
    doNothing().when(auditServiceHelper).addEntityOperationIdentifierDataToAuditContext(any());
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    Service savedService = spyServiceResourceService.save(service);

    assertThat(savedService.getUuid()).isEqualTo(SERVICE_ID);
    assertThat(savedService.getName()).isEqualTo("PCFV2");

    ArgumentCaptor<ApplicationManifest> appManifestArgumentCaptor = ArgumentCaptor.forClass(ApplicationManifest.class);
    verify(applicationManifestService, times(1)).create(appManifestArgumentCaptor.capture());
    ApplicationManifest appManifest = appManifestArgumentCaptor.getValue();
    assertThat(appManifest.getKind()).isEqualTo(AppManifestKind.K8S_MANIFEST);
    assertThat(appManifest.getStoreType()).isEqualTo(StoreType.Local);
    assertThat(appManifest.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(appManifest.getAppId()).isEqualTo(APP_ID);

    ArgumentCaptor<ManifestFile> manifestFileArgumentCaptor = ArgumentCaptor.forClass(ManifestFile.class);
    verify(applicationManifestService, times(2))
        .createManifestFileByServiceId(manifestFileArgumentCaptor.capture(), any());

    URL url = ServiceResourceServiceImpl.class.getClassLoader().getResource("default-pcf-manifests/vars.yml");
    String default_pcf_vars_yml = Resources.toString(url, Charsets.UTF_8);

    url = ServiceResourceServiceImpl.class.getClassLoader().getResource("default-pcf-manifests/manifest.yml");
    String default_pcf_manifest_yml = Resources.toString(url, Charsets.UTF_8);

    List<ManifestFile> manifestFiles = manifestFileArgumentCaptor.getAllValues();
    assertThat(manifestFiles)
        .extracting(ManifestFile::getFileName, ManifestFile::getFileContent)
        .contains(tuple(MANIFEST_YML, default_pcf_manifest_yml), tuple(VARS_YML, default_pcf_vars_yml));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSetPcfV2ServiceFromAppManifestIfRequired() {
    Query mockQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(Service.class)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), anyString())).thenReturn(mockQuery);
    when(mockQuery.getKey()).thenReturn(new Key<>(Service.class, "services", SERVICE_ID));

    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(StoreType.Local).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);

    srs.setPcfV2ServiceFromAppManifestIfRequired(applicationManifest, AppManifestSource.SERVICE);

    verify(mockWingsPersistence, times(1)).update(any(Query.class), any());
    verify(updateOperations).set(ServiceKeys.isPcfV2, true);
    verify(mockQuery).filter(ServiceKeys.deploymentType, PCF.name());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreatePCFV2ServiceWithExistingAppManifest() {
    doNothing().when(harnessTagService).attachTag(any());
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    Service service = Service.builder()
                          .appId(APP_ID)
                          .accountId(ACCOUNT_ID)
                          .uuid(SERVICE_ID)
                          .deploymentType(DeploymentType.PCF)
                          .name("PCFV2")
                          .build();

    doReturn(service).when(spyServiceResourceService).addCommand(any(), any(), any(ServiceCommand.class), eq(true));
    doNothing().when(auditServiceHelper).addEntityOperationIdentifierDataToAuditContext(any());
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(applicationManifestService.getManifestByServiceId(service.getAppId(), service.getUuid()))
        .thenReturn(ApplicationManifest.builder().storeType(StoreType.Local).build());
    Service savedService = spyServiceResourceService.save(service);

    assertThat(savedService.getUuid()).isEqualTo(SERVICE_ID);
    assertThat(savedService.getName()).isEqualTo("PCFV2");

    verify(applicationManifestService, times(0)).create(any());
    verify(applicationManifestService, times(0)).createManifestFileByServiceId(any(), any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSetPcfV2ServiceFromAppManifestIfRequiredInvalidSource() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(StoreType.Local).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);

    srs.setPcfV2ServiceFromAppManifestIfRequired(applicationManifest, AppManifestSource.ENV_SERVICE);

    verify(mockWingsPersistence, times(0)).update(any(Query.class), any());
    verify(mockWingsPersistence, times(0)).createUpdateOperations(any());
    verify(mockWingsPersistence, times(0)).createQuery(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSetPcfV2ServiceFromAppManifestIfRequiredNonExistentService() {
    Query mockQuery = mock(Query.class);
    when(mockWingsPersistence.createQuery(Service.class)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), anyString())).thenReturn(mockQuery);
    when(mockQuery.getKey()).thenReturn(null);

    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(StoreType.Local).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    srs.setPcfV2ServiceFromAppManifestIfRequired(applicationManifest, AppManifestSource.ENV_SERVICE);

    verify(mockWingsPersistence, times(0)).update(any(Query.class), any());
    verify(mockWingsPersistence, times(0)).createUpdateOperations(any());
    verify(mockWingsPersistence, times(0)).createQuery(any());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetFlattenCommandUnits() {
    final Command command_1 =
        Command.Builder.aCommand()
            .withCommandType(START)
            .withName("start")
            .withCommandUnits(Arrays.asList(ExecCommandUnit.Builder.anExecCommandUnit().withName("exec").build(),
                ScpCommandUnit.Builder.aScpCommandUnit().withName("scp").build()))
            .build();
    final Command command_2 =
        Command.Builder.aCommand()
            .withCommandType(STOP)
            .withName("stop")
            .withCommandUnits(Arrays.asList(ExecCommandUnit.Builder.anExecCommandUnit().withName("exec").build(),
                ScpCommandUnit.Builder.aScpCommandUnit().withName("scp").build()))
            .build();
    final Map<String, EntityVersion> envIdVersionMap =
        ImmutableMap.<String, EntityVersion>of(ENV_ID, EntityVersion.Builder.anEntityVersion().withVersion(1).build());
    ServiceCommand sc_1 = ServiceCommand.Builder.aServiceCommand()
                              .withUuid("uuid_1")
                              .withAppId(APP_ID)
                              .withServiceId(SERVICE_ID)
                              .withName(command_1.getName())
                              .withTargetToAllEnv(true)
                              .withCommand(command_1)
                              .withEnvIdVersionMap(envIdVersionMap)
                              .build();
    ServiceCommand sc_2 = ServiceCommand.Builder.aServiceCommand()
                              .withUuid("uuid_2")
                              .withAppId(APP_ID)
                              .withServiceId(SERVICE_ID)
                              .withName(command_2.getName())
                              .withTargetToAllEnv(true)
                              .withCommand(command_2)
                              .withEnvIdVersionMap(envIdVersionMap)
                              .build();

    List<ServiceCommand> serviceCommands = Arrays.asList(sc_1, sc_2);
    doReturn(serviceCommands).when(spyServiceResourceService).getServiceCommands(anyString(), anyString());
    doReturn(PageResponseBuilder.aPageResponse().withResponse(serviceCommands).build())
        .when(mockWingsPersistence)
        .query(any(), any());
    doReturn(command_1).when(commandService).getCommand(APP_ID, sc_1.getUuid(), sc_1.getVersionForEnv(ENV_ID));
    doReturn(command_2).when(commandService).getCommand(APP_ID, sc_2.getUuid(), sc_2.getVersionForEnv(ENV_ID));
    List<CommandUnit> commandUnits =
        spyServiceResourceService.getFlattenCommandUnitList(APP_ID, SERVICE_ID, ENV_ID, sc_1.getName());
    assertThat(commandUnits).hasSize(2);

    sc_2.setName(sc_1.getName());
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () -> spyServiceResourceService.getFlattenCommandUnitList(APP_ID, SERVICE_ID, ENV_ID, sc_1.getName()))
        .withMessageContaining(sc_1.getName());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetFlattenNestedCommandUnits() {
    final Command nestedCommand =
        Command.Builder.aCommand()
            .withCommandType(CommandType.OTHER)
            .withName("start")
            .withTemplateReference(TemplateReference.builder().templateUuid(UUID).templateVersion(4L).build())
            .build();
    final Command parentCommand = Command.Builder.aCommand()
                                      .withCommandType(OTHER)
                                      .withName("stop")
                                      .withCommandUnits(Collections.singletonList(nestedCommand))
                                      .build();
    final Map<String, EntityVersion> envIdVersionMap =
        ImmutableMap.<String, EntityVersion>of(ENV_ID, EntityVersion.Builder.anEntityVersion().withVersion(1).build());
    ServiceCommand sc_1 = ServiceCommand.Builder.aServiceCommand()
                              .withUuid("uuid_1")
                              .withAppId(APP_ID)
                              .withServiceId(SERVICE_ID)
                              .withName(parentCommand.getName())
                              .withTargetToAllEnv(true)
                              .withCommand(parentCommand)
                              .withEnvIdVersionMap(envIdVersionMap)
                              .build();

    when(templateService.get(any(), any()))
        .thenReturn(Template.builder()
                        .templateObject(SshCommandTemplate.builder()
                                            .commandUnits(Collections.singletonList(
                                                ExecCommandUnit.Builder.anExecCommandUnit().withName("exec").build()))
                                            .build())
                        .build());
    List<ServiceCommand> serviceCommands = Arrays.asList(sc_1);
    doReturn(serviceCommands).when(spyServiceResourceService).getServiceCommands(anyString(), anyString());
    doReturn(PageResponseBuilder.aPageResponse().withResponse(serviceCommands).build())
        .when(mockWingsPersistence)
        .query(any(), any());
    doReturn(parentCommand).when(commandService).getCommand(APP_ID, sc_1.getUuid(), sc_1.getVersionForEnv(ENV_ID));
    List<CommandUnit> commandUnits =
        spyServiceResourceService.getFlattenCommandUnitList(APP_ID, SERVICE_ID, ENV_ID, sc_1.getName());
    assertThat(commandUnits).hasSize(1);
    assertThat(commandUnits.get(0).getName()).isEqualTo("exec");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldGetExistingEcsServiceSpecification() {
    EcsServiceSpecification org = EcsServiceSpecification.builder().serviceId(SERVICE_ID).build();
    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(mockWingsPersistence).createQuery(EcsServiceSpecification.class);
    doReturn(mockQuery).when(mockQuery).filter(anyString(), anyString());
    doReturn(mockQuery).when(mockQuery).filter(EcsServiceSpecificationKeys.serviceId, SERVICE_ID);
    doReturn(org).when(mockQuery).get();

    EcsServiceSpecification result = srs.getExistingOrDefaultEcsServiceSpecification(APP_ID, SERVICE_ID);
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(org);

    doReturn(null).when(mockQuery).get();
    EcsServiceSpecification resultDefault = srs.getExistingOrDefaultEcsServiceSpecification(APP_ID, SERVICE_ID);

    assertThat(resultDefault).isNotNull();
    assertThat(resultDefault).isNotEqualTo(org);
    assertThat(resultDefault.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(resultDefault.getAppId()).isEqualTo(APP_ID);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCloneLambdaSpecification() {
    LambdaSpecification lambdaSpecification = createLambdaSpecification();
    LambdaSpecification clonedSpec = lambdaSpecification.cloneInternal();
    assertThat(clonedSpec.getDefaults().getRuntime()).isEqualTo("default-runtTime");
    assertThat(clonedSpec.getFunctions().get(0).getHandler()).isEqualTo("handler");

    lambdaSpecification.setDefaults(null);
    clonedSpec = lambdaSpecification.cloneInternal();
    assertThat(clonedSpec.getDefaults()).isNull();

    lambdaSpecification.setFunctions(null);
    clonedSpec = lambdaSpecification.cloneInternal();
    assertThat(clonedSpec.getFunctions()).isEmpty();
  }

  private LambdaSpecification createLambdaSpecification() {
    return LambdaSpecification.builder()
        .serviceId(SERVICE_ID)
        .defaults(DefaultSpecification.builder().timeout(1).memorySize(123).runtime("default-runtTime").build())
        .functions(asList(FunctionSpecification.builder()
                              .handler("handler")
                              .functionName("functionName")
                              .memorySize(122)
                              .timeout(12)
                              .runtime("runTime")
                              .build()))
        .build();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldSaveServiceCommandWithAccountId() {
    ServiceCommand serviceCommand =
        aServiceCommand().withName("START").withAppId(APP_ID).withServiceId(SERVICE_ID).build();
    serviceCommand.setCommand(aCommand().withGraph(getGraph()).build());
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(entityVersionService.newEntityVersion(
             APP_ID, EntityType.COMMAND, ID_KEY, SERVICE_ID, "START", ChangeType.CREATED, null))
        .thenReturn(anEntityVersion().withVersion(2).build());
    when(mockWingsPersistence.saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class)))
        .thenAnswer(invocation -> {
          ServiceCommand command = invocation.getArgument(1, ServiceCommand.class);
          command.setUuid(ID_KEY);
          assertThat(command.getAccountId()).isEqualTo(ACCOUNT_ID);
          return command;
        });
    srs.addCommand(APP_ID, SERVICE_ID, serviceCommand, false);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCheckForRunningExecutionsBeforeDelete() {
    when(mockWingsPersistence.getWithAppId(Service.class, APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().name(SERVICE_NAME).appId(APP_ID).uuid(SERVICE_ID).build());
    when(workflowExecutionService.runningExecutionsForService(APP_ID, SERVICE_ID))
        .thenReturn(asList(PIPELINE_EXECUTION_ID, WORKFLOW_EXECUTION_ID));
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    assertThatThrownBy(() -> srs.delete(APP_ID, SERVICE_ID)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldCloneCommand() {
    Command command = aCommand().withName("START").build();
    ServiceCommand serviceCommand =
        aServiceCommand().withName("START").withAppId(APP_ID).withServiceId(SERVICE_ID).withCommand(command).build();
    Service service = serviceBuilder.build();
    service.setServiceCommands(Arrays.asList(serviceCommand));
    persistence.save(serviceCommand);
    when(entityVersionService.newEntityVersion(
             APP_ID, EntityType.COMMAND, ID_KEY, SERVICE_ID, "START", ChangeType.CREATED, null))
        .thenReturn(anEntityVersion().withVersion(2).build());
    when(commandService.getCommand(APP_ID, "SERVICE_COMMAND_ID", 1)).thenReturn(command);
    doReturn(service).when(spyServiceResourceService).getServiceWithServiceCommands(APP_ID, SERVICE_ID);
    when(mockWingsPersistence.saveAndGet(eq(ServiceCommand.class), any(ServiceCommand.class)))
        .thenAnswer(invocation -> {
          ServiceCommand svccommand = invocation.getArgument(1, ServiceCommand.class);
          svccommand.setServiceId(SERVICE_ID);
          svccommand.setUuid(ID_KEY);
          return svccommand;
        });
    Service updatedService = srs.cloneCommand(APP_ID, SERVICE_ID, "START", serviceCommand);
    verify(commandService).save(Mockito.any(Command.class), Mockito.anyBoolean());
    assertThat(updatedService).isNotNull();
    assertThat(updatedService.getServiceCommands()).isNotEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testsetConfigMapYaml() {
    persistence.save(Service.builder().uuid(SERVICE_ID).appId(APP_ID).build());
    KubernetesPayload payload = new KubernetesPayload();
    payload.setAdvancedConfig("${DOCKER_IMAGE_NAME}");
    srs.setConfigMapYaml(APP_ID, SERVICE_ID, payload);
    verify(mockWingsPersistence).update(any(Service.class), any(UpdateOperations.class));
    verify(mockWingsPersistence).createUpdateOperations(Service.class);
    verify(updateOperations).set("configMapYaml", "${DOCKER_IMAGE_NAME}");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testsetK8v2ServiceFromAppManifest() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(StoreType.Local).serviceId(SERVICE_ID).build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest.setUuid("APPMANIFEST_ID");
    Service service = Service.builder().name("SERVICE_ID1").appId(APP_ID).uuid(SERVICE_ID).build();
    persistence.save(service);
    srs.setK8v2ServiceFromAppManifest(applicationManifest, ApplicationManifest.AppManifestSource.SERVICE);
    verify(mockWingsPersistence).createUpdateOperations(Service.class);
    verify(updateOperations).set("isK8sV2", true);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenArtifactFromManifestEnabledForWrongDeploymentType() {
    Service service =
        Service.builder().name(SERVICE_NAME).deploymentType(DeploymentType.SSH).artifactFromManifest(true).build();
    assertThatThrownBy(() -> spyServiceResourceService.save(service, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact from Manifest flag can be set to true only for kubernetes and helm deployment types");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnOnlyArtifactFromManifestServices() {
    Service service = Service.builder()
                          .name(SERVICE_NAME)
                          .uuid(SERVICE_ID)
                          .appId(APP_ID)
                          .deploymentType(DeploymentType.HELM)
                          .artifactFromManifest(true)
                          .build();
    Service service2 = Service.builder()
                           .name(SERVICE_NAME + 2)
                           .uuid(SERVICE_ID + 2)
                           .appId(APP_ID)
                           .deploymentType(DeploymentType.HELM)
                           .artifactFromManifest(false)
                           .build();
    persistence.save(service);
    persistence.save(service2);
    assertThat(spyServiceResourceService.getIdsWithArtifactFromManifest(APP_ID)).hasSize(1).containsExactly(SERVICE_ID);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testShouldDeleteByAccountId() {
    when(mockWingsPersistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    Query<PersistentEntity> mockedQueryEntity = mock(Query.class);
    when(mockWingsPersistence.createQuery(any())).thenReturn(mockedQueryEntity);
    when(mockedQueryEntity.filter(anyString(), any())).thenReturn(mockedQueryEntity);
    commandServiceImpl.deleteByAccountId(ACCOUNT_ID);
    verify(mockWingsPersistence, times(1)).delete((Query<PersistentEntity>) any());
  }
}
