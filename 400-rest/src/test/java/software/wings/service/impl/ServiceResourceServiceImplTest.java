/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.beans.SearchFilter.Operator.NOT_EXISTS;
import static io.harness.beans.SearchFilter.Operator.OR;
import static io.harness.k8s.model.HelmVersion.V2;
import static io.harness.k8s.model.HelmVersion.V3;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.TARUN_UBA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.api.DeploymentType.AMI;
import static software.wings.api.DeploymentType.AWS_CODEDEPLOY;
import static software.wings.api.DeploymentType.AWS_LAMBDA;
import static software.wings.api.DeploymentType.AZURE_VMSS;
import static software.wings.api.DeploymentType.CUSTOM;
import static software.wings.api.DeploymentType.ECS;
import static software.wings.api.DeploymentType.HELM;
import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.PCF;
import static software.wings.api.DeploymentType.SSH;
import static software.wings.api.DeploymentType.WINRM;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.DELETE;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.FETCH;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.HISTORY;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.INSTALL;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.LIST;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.PULL;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.REPO_ADD;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.REPO_UPDATE;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.ROLLBACK;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.TEMPLATE;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.UNINSTALL;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.UPGRADE;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.VERSION;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.HelmVersion;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.pcf.model.CfCliVersion;
import io.harness.persistence.HPersistence;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.EntityType;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.container.KubernetesPayload;
import software.wings.helpers.ext.helm.HelmHelper;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.CommandService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.utils.ApplicationManifestUtils;
import software.wings.utils.ArtifactType;
import software.wings.utils.WingsTestConstants.MockChecker;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.AbstractMultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class ServiceResourceServiceImplTest extends WingsBaseTest {
  @Captor ArgumentCaptor<Command> commandCaptor;
  @Captor ArgumentCaptor<Boolean> booleanCaptor;
  @Inject private HPersistence persistence;
  @Inject private HarnessTagService harnessTagService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private AppService appService;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private HelmHelper helmHelper;
  @Mock private NotificationService notificationService;
  @Mock private CommandService commandService;
  @Inject @InjectMocks private ServiceResourceServiceImpl serviceResourceService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @InjectMocks private ServiceResourceServiceImpl mockedServiceResourceService;
  @InjectMocks @Inject private EntityVersionService entityVersionService;
  @Mock private ResourceLookupService resourceLookupService;
  @Mock private CustomDeploymentTypeService customDeploymentTypeService;
  @Mock private YamlPushService yamlPushService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private UpdateOperations<Service> updateOperations;
  private ServiceResourceServiceImpl spyServiceResourceService = spy(new ServiceResourceServiceImpl());
  private ApplicationManifest applicationManifest;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));

    applicationManifest = ApplicationManifest.builder().storeType(StoreType.Local).serviceId(SERVICE_ID).build();
    applicationManifest.setUuid("APPMANIFEST_ID");
    applicationManifest.setAppId(APP_ID);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldSaveWithDefaultHelm2Version() {
    Service helmService = Service.builder().appId(APP_ID).deploymentType(HELM).build();
    serviceResourceService.checkAndSetHelmVersion(helmService);
    assertThat(helmService.getHelmVersion()).isEqualTo(V2);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldSaveWithHelm3Version() {
    Service helmService = Service.builder().deploymentType(HELM).helmVersion(V3).build();
    serviceResourceService.checkAndSetHelmVersion(helmService);
    assertThat(helmService.getHelmVersion()).isEqualTo(V3);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCheckAndSetCfCliVersionWithDefaultV6Version() {
    Service pcfService = Service.builder().deploymentType(PCF).cfCliVersion(null).build();
    serviceResourceService.checkAndSetCfCliVersion(pcfService);
    assertThat(pcfService.getCfCliVersion()).isEqualTo(CfCliVersion.V6);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCheckAndSetCfCliVersionWithDefaultV7() {
    Service pcfService = Service.builder().deploymentType(PCF).cfCliVersion(CfCliVersion.V7).build();
    serviceResourceService.checkAndSetCfCliVersion(pcfService);
    assertThat(pcfService.getCfCliVersion()).isEqualTo(CfCliVersion.V7);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCheckAndSetCfCliVersionWithValidationError() {
    Service pcfService = Service.builder().deploymentType(HELM).cfCliVersion(CfCliVersion.V7).build();
    assertThatThrownBy(() -> serviceResourceService.checkAndSetCfCliVersion(pcfService))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("CfCliVersion is only supported with PCF type of services, found deployment type: [HELM]");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdateOperationsForCfCliVersionWithValidationError() {
    Service savedPcfService = Service.builder().deploymentType(HELM).cfCliVersion(CfCliVersion.V6).build();
    Service newPcfService = Service.builder().deploymentType(PCF).cfCliVersion(CfCliVersion.V6).build();

    assertThatThrownBy(
        () -> serviceResourceService.updateOperationsForCfCliVersion(savedPcfService, newPcfService, updateOperations))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("CfCliVersion is only supported with PCF type of services, found deployment type: [HELM]");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldValidateHelmVersionAndDeploymentType() {
    shouldFailIfVersionSetForNonHelmK8sService();
  }

  private void shouldFailIfVersionSetForNonHelmK8sService() {
    Service service =
        Service.builder().appId(APP_ID).uuid("some-random-id").deploymentType(SSH).helmVersion(V2).build();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> serviceResourceService.validateHelmVersion(service))
        .withMessageContaining("helmVersion is only supported with Helm and Kubernetes type of services");
  }

  private void shouldBeNoopIfHelmVersionNotSet() {
    Service service = Service.builder().appId(APP_ID).uuid("some-random-id").deploymentType(SSH).build();
    serviceResourceService.validateHelmVersion(service);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldNotUpdateHelmVersionIfNotPresent() {
    Service helmService = Service.builder().appId(APP_ID).uuid("some-random-helm-id").deploymentType(HELM).build();
    helmService = updateAndGetService(helmService, helmService);
    assertThat(helmService.getHelmVersion()).isNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldNotUpdateHelmVersionForNonHelmService() {
    Service helmService = Service.builder().appId(APP_ID).uuid("some-random-helm-id").deploymentType(SSH).build();
    helmService = updateAndGetService(helmService, helmService);
    assertThat(helmService.getHelmVersion()).isNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldBeNoopIfHelmVersionSet() {
    Service helm2Service = Service.builder().helmVersion(V2).deploymentType(HELM).build();
    Service helm3Service = Service.builder().helmVersion(V3).deploymentType(HELM).build();
    serviceResourceService.checkAndSetHelmVersion(helm2Service);
    serviceResourceService.checkAndSetHelmVersion(helm3Service);
    assertThat(helm2Service.getHelmVersion()).isEqualTo(V2);
    assertThat(helm2Service.getDeploymentType()).isEqualTo(HELM);
    assertThat(helm3Service.getHelmVersion()).isEqualTo(V3);
    assertThat(helm3Service.getDeploymentType()).isEqualTo(HELM);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldNotGenerateUpdateOperationIfHelmVersionSet() {
    Service oldHelm2Service = Service.builder().helmVersion(V2).deploymentType(HELM).build();
    Service newHelm2Service = Service.builder().helmVersion(V2).deploymentType(HELM).build();
    Service updatedService = updateAndGetService(oldHelm2Service, newHelm2Service);
    assertThat(updatedService).isEqualToIgnoringGivenFields(newHelm2Service, ServiceKeys.version, "lastUpdatedAt");

    Service oldHelm3Service = Service.builder().helmVersion(V3).deploymentType(HELM).build();
    Service newHelm3Service = Service.builder().helmVersion(V3).deploymentType(HELM).build();
    updatedService = updateAndGetService(oldHelm3Service, newHelm3Service);
    assertThat(updatedService).isEqualToIgnoringGivenFields(newHelm3Service, ServiceKeys.version, "lastUpdatedAt");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldSwitchHelmVersions() {
    Service oldHelm2Service = Service.builder().helmVersion(V2).deploymentType(HELM).build();
    Service newHelm3Service = Service.builder().helmVersion(V3).deploymentType(HELM).build();
    Service updatedService = updateAndGetService(oldHelm2Service, newHelm3Service);
    assertThat(updatedService)
        .isEqualToIgnoringGivenFields(newHelm3Service, ServiceKeys.version, "lastUpdatedAt", ServiceKeys.helmVersion);
    assertThat(updatedService.getHelmVersion()).isEqualTo(V3);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmVersionIfNotPresent() {
    Service service = Service.builder().appId(APP_ID).uuid("1972510").deploymentType(HELM).build();
    persistence.save(service);
    assertThat(serviceResourceService.getHelmVersionWithDefault(APP_ID, service.getUuid())).isEqualTo(V2);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmVersionIfPresent() {
    Service service = Service.builder().appId(APP_ID).uuid("017071").helmVersion(V2).build();
    persistence.save(service);
    assertThat(serviceResourceService.getHelmVersionWithDefault(APP_ID, service.getUuid())).isEqualTo(V2);

    service.setHelmVersion(V3);
    persistence.save(service);
    assertThat(serviceResourceService.getHelmVersionWithDefault(APP_ID, service.getUuid())).isEqualTo(V3);
  }

  private Service updateAndGetService(Service oldService, Service newService) {
    persistence.save(newService);
    UpdateOperations<Service> updateOperations = persistence.createUpdateOperations(Service.class);
    serviceResourceService.updateOperationsForHelmVersion(oldService, newService, updateOperations);
    persistence.update(newService, updateOperations);
    newService = persistence.get(Service.class, newService.getUuid());
    return newService;
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testUpdateOperationsForHelmVersion() {
    opsMapShouldBeUpdatedWhenVersionPresent();
    opsMapShouldNotBeUpdatedWhenVersionNotPresentInOldAndNewService();
    opsMapShouldNotBeUpdatedWhenVersionNotPresentInNewService();
    opsMapShouldNotBeUpdatedWhenNonHelmK8sService();
  }

  private void opsMapShouldNotBeUpdatedWhenNonHelmK8sService() {
    Service newService = Service.builder().appId(APP_ID).deploymentType(SSH).build();
    Service oldService = Service.builder().appId(APP_ID).deploymentType(SSH).build();
    UpdateOperations<Service> updateOperations = persistence.createUpdateOperations(Service.class);

    serviceResourceService.updateOperationsForHelmVersion(oldService, newService, updateOperations);

    Map<String, Object> ops =
        ReflectionUtils.getFieldValues(updateOperations, new HashSet<>(Collections.singletonList("ops")));
    Map opsMap = (Map) ops.get("ops");
    assertThat(opsMap.isEmpty()).isTrue();
  }

  private void opsMapShouldNotBeUpdatedWhenVersionNotPresentInNewService() {
    Service newService = Service.builder().appId(APP_ID).deploymentType(HELM).build();
    Service oldService = Service.builder().appId(APP_ID).deploymentType(HELM).helmVersion(V2).build();
    UpdateOperations<Service> updateOperations = persistence.createUpdateOperations(Service.class);

    serviceResourceService.updateOperationsForHelmVersion(oldService, newService, updateOperations);

    Map<String, Object> ops =
        ReflectionUtils.getFieldValues(updateOperations, new HashSet<>(Collections.singletonList("ops")));
    Map opsMap = (Map) ops.get("ops");
    assertThat(opsMap.isEmpty()).isTrue();
  }

  private void opsMapShouldNotBeUpdatedWhenVersionNotPresentInOldAndNewService() {
    Service newService = Service.builder().appId(APP_ID).deploymentType(HELM).build();
    Service oldService = Service.builder().appId(APP_ID).deploymentType(HELM).build();
    UpdateOperations<Service> updateOperations = persistence.createUpdateOperations(Service.class);

    serviceResourceService.updateOperationsForHelmVersion(oldService, newService, updateOperations);

    Map<String, Object> fieldValues =
        ReflectionUtils.getFieldValues(updateOperations, new HashSet<>(Collections.singletonList("ops")));
    Map opsMap = (Map) fieldValues.get("ops");
    assertThat(opsMap.isEmpty()).isTrue();
  }

  private void opsMapShouldBeUpdatedWhenVersionPresent() {
    Service newService = Service.builder().appId(APP_ID).deploymentType(HELM).helmVersion(V2).build();
    Service oldService = Service.builder().appId(APP_ID).deploymentType(HELM).helmVersion(V3).build();
    UpdateOperations<Service> updateOperations = persistence.createUpdateOperations(Service.class);

    serviceResourceService.updateOperationsForHelmVersion(oldService, newService, updateOperations);

    Map<String, Object> ops =
        ReflectionUtils.getFieldValues(updateOperations, new HashSet<>(Collections.singletonList("ops")));
    Object value = ((Map) ((Map) ops.get("ops")).get("$set")).get(ServiceKeys.helmVersion);
    assertThat(value).isEqualTo(V2.name());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetHelmVersionWithDefault() {
    shouldGetHelmVersionWhenPresent();
    shouldGetHelmVersionWhenDeploymentTypeAbsent();
    shouldReturnDefaultForHelmService();
    shouldReturnDefaultForK8sService();
  }

  private void shouldGetHelmVersionWhenDeploymentTypeAbsent() {
    Service service = Service.builder().deploymentType(null).build();
    doReturn(service).when(spyServiceResourceService).get(APP_ID, SERVICE_ID);

    HelmVersion helmVersion = spyServiceResourceService.getHelmVersionWithDefault(APP_ID, SERVICE_ID);

    assertThat(helmVersion).isEqualTo(V2);
  }

  private void shouldReturnDefaultForK8sService() {
    Service service = Service.builder().deploymentType(KUBERNETES).build();
    doReturn(service).when(spyServiceResourceService).get(APP_ID, SERVICE_ID);

    HelmVersion helmVersion = spyServiceResourceService.getHelmVersionWithDefault(APP_ID, SERVICE_ID);

    assertThat(helmVersion).isEqualTo(V2);
  }

  private void shouldReturnDefaultForHelmService() {
    Service service = Service.builder().deploymentType(HELM).build();
    doReturn(service).when(spyServiceResourceService).get(APP_ID, SERVICE_ID);

    HelmVersion helmVersion = spyServiceResourceService.getHelmVersionWithDefault(APP_ID, SERVICE_ID);

    assertThat(helmVersion).isEqualTo(V2);
  }

  private void shouldGetHelmVersionWhenPresent() {
    Service service = Service.builder().helmVersion(V2).build();
    doReturn(service).when(spyServiceResourceService).get(APP_ID, SERVICE_ID);

    HelmVersion helmVersion = spyServiceResourceService.getHelmVersionWithDefault(APP_ID, SERVICE_ID);

    assertThat(helmVersion).isEqualTo(V2);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testListServiceWithInfraDefFilterError() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<Service> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    queryParams.put("infraDefinitionId", Collections.singletonList("infra1"));
    assertThatThrownBy(() -> mockedServiceResourceService.list(pageRequest, false, false, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("AppId is mandatory for infra-based filtering");
    assertThat(pageRequest.getFilters()).isEmpty();

    pageRequest.addFilter("appId", EQ, asList("app1", "app2").toArray());
    assertThatThrownBy(() -> mockedServiceResourceService.list(pageRequest, false, false, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("More than 1 appId not supported for listing services");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testListServiceWithInfraDefFilterNoScoping() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<Service> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    queryParams.put("infraDefinitionId", Collections.singletonList("infra1"));
    pageRequest.addFilter("appId", EQ, Collections.singletonList("app1").toArray());
    when(appService.getAccountIdByAppId("app1")).thenReturn(ACCOUNT_ID);
    InfrastructureDefinition sshInfraDef = InfrastructureDefinition.builder()
                                               .uuid("infra1")
                                               .name("ssh")
                                               .deploymentType(DeploymentType.SSH)
                                               .appId(APP_ID)
                                               .build();
    when(infrastructureDefinitionService.get("app1", "infra1")).thenReturn(sshInfraDef);

    PageResponse<Service> pageResponse = new PageResponse<>();
    pageResponse.setResponse(Collections.EMPTY_LIST);
    when(resourceLookupService.listWithTagFilters(pageRequest, null, EntityType.SERVICE, false, false))
        .thenReturn(pageResponse);

    mockedServiceResourceService.list(pageRequest, false, false, false, null);
    assertThat(pageRequest.getFilters()).isNotEmpty();
    assertThat(pageRequest.getFilters().size()).isEqualTo(2);
    assertThat(pageRequest.getFilters().get(1).getFieldName()).isEqualTo("deploymentType");
    assertThat(pageRequest.getFilters().get(1).getOp()).isEqualTo(OR);
    SearchFilter searchFilter1 = (SearchFilter) pageRequest.getFilters().get(1).getFieldValues()[0];
    SearchFilter searchFilter2 = (SearchFilter) pageRequest.getFilters().get(1).getFieldValues()[1];
    assertThat(searchFilter1.getOp()).isEqualTo(EQ);
    assertThat(searchFilter2.getOp()).isEqualTo(NOT_EXISTS);
    assertThat(pageRequest.getFilters().stream().map(SearchFilter::getFieldName).collect(Collectors.toSet()))
        .doesNotContain(ServiceKeys.deploymentTypeTemplateId);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testListServiceWithInfraDefFilterNoScopingCustomDeployment() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<Service> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    queryParams.put("infraDefinitionId", Collections.singletonList("infra1"));
    pageRequest.addFilter("appId", EQ, Collections.singletonList("app1").toArray());
    when(appService.getAccountIdByAppId("app1")).thenReturn(ACCOUNT_ID);
    InfrastructureDefinition customInfraDef = InfrastructureDefinition.builder()
                                                  .uuid("infra1")
                                                  .name("ssh")
                                                  .deploymentType(CUSTOM)
                                                  .appId(APP_ID)
                                                  .deploymentTypeTemplateId(TEMPLATE_ID)
                                                  .customDeploymentName("my-deployment")
                                                  .build();
    when(infrastructureDefinitionService.get("app1", "infra1")).thenReturn(customInfraDef);

    PageResponse<Service> pageResponse = new PageResponse<>();
    pageResponse.setResponse(Collections.EMPTY_LIST);
    when(resourceLookupService.listWithTagFilters(pageRequest, null, EntityType.SERVICE, false, false))
        .thenReturn(pageResponse);

    mockedServiceResourceService.list(pageRequest, false, false, false, null);
    assertThat(pageRequest.getFilters()).isNotEmpty();
    assertThat(pageRequest.getFilters().size()).isEqualTo(3);
    assertThat(pageRequest.getFilters().get(1).getFieldName()).isEqualTo("deploymentType");
    assertThat(pageRequest.getFilters().get(1).getOp()).isEqualTo(OR);
    SearchFilter searchFilter1 = (SearchFilter) pageRequest.getFilters().get(1).getFieldValues()[0];
    SearchFilter searchFilter2 = (SearchFilter) pageRequest.getFilters().get(1).getFieldValues()[1];
    assertThat(searchFilter1.getOp()).isEqualTo(EQ);
    assertThat(searchFilter2.getOp()).isEqualTo(NOT_EXISTS);
    assertThat(pageRequest.getFilters()
                   .stream()
                   .filter(f -> ServiceKeys.deploymentTypeTemplateId.equals(f.getFieldName()))
                   .collect(Collectors.toList()))
        .containsExactly(SearchFilter.builder()
                             .fieldName(ServiceKeys.deploymentTypeTemplateId)
                             .op(EQ)
                             .fieldValues(new Object[] {TEMPLATE_ID})
                             .build());
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testListServiceWithInfraDefFilterScoped() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<Service> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    queryParams.put("infraDefinitionId", Collections.singletonList("infra1"));
    pageRequest.addFilter("appId", EQ, Collections.singletonList("app1").toArray());
    when(appService.getAccountIdByAppId("app1")).thenReturn(ACCOUNT_ID);
    InfrastructureDefinition sshInfraDef = InfrastructureDefinition.builder()
                                               .uuid("infra1")
                                               .name("ssh")
                                               .scopedToServices(Collections.singletonList("s1"))
                                               .deploymentType(DeploymentType.SSH)
                                               .appId(APP_ID)
                                               .build();
    when(infrastructureDefinitionService.get("app1", "infra1")).thenReturn(sshInfraDef);

    PageResponse<Service> pageResponse = new PageResponse<>();
    pageResponse.setResponse(Collections.emptyList());
    when(resourceLookupService.listWithTagFilters(pageRequest, null, EntityType.SERVICE, false, false))
        .thenReturn(pageResponse);

    mockedServiceResourceService.list(pageRequest, false, false, false, null);
    assertThat(pageRequest.getFilters()).isNotEmpty();
    assertThat(pageRequest.getFilters().size()).isEqualTo(2);
    assertThat(pageRequest.getFilters().get(1).getFieldName()).isEqualTo("_id");
    assertThat(pageRequest.getFilters().get(1).getOp()).isEqualTo(IN);
    assertThat(pageRequest.getFilters().get(1).getFieldValues()).containsExactly("s1");
    assertThat(pageResponse.getFilters().stream().map(SearchFilter::getFieldName).collect(Collectors.toSet()))
        .doesNotContain(ServiceKeys.deploymentTypeTemplateId);
    assertThat(pageRequest.getFilters().stream().map(SearchFilter::getFieldName).collect(Collectors.toSet()))
        .doesNotContain(ServiceKeys.deploymentType);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testListServiceWithInfraDefFilterNoCommonScoping() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<Service> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    queryParams.put("infraDefinitionId", asList("infra1", "infra2", "infra3"));
    pageRequest.addFilter("appId", EQ, Collections.singletonList("app1").toArray());
    when(appService.getAccountIdByAppId("app1")).thenReturn(ACCOUNT_ID);
    InfrastructureDefinition sshInfraDef = InfrastructureDefinition.builder()
                                               .uuid("infra1")
                                               .name("ssh")
                                               .scopedToServices(Collections.singletonList("s1"))
                                               .deploymentType(DeploymentType.SSH)
                                               .appId(APP_ID)
                                               .build();
    when(infrastructureDefinitionService.get("app1", "infra1")).thenReturn(sshInfraDef);

    InfrastructureDefinition k8sInfraDef = InfrastructureDefinition.builder()
                                               .uuid("infra2")
                                               .name("k8s")
                                               .scopedToServices(Collections.singletonList("s2"))
                                               .deploymentType(DeploymentType.SSH)
                                               .appId(APP_ID)
                                               .build();
    when(infrastructureDefinitionService.get("app1", "infra2")).thenReturn(k8sInfraDef);

    InfrastructureDefinition noScopingInfraDef = InfrastructureDefinition.builder()
                                                     .uuid("infra2")
                                                     .name("k8s")
                                                     .deploymentType(DeploymentType.SSH)
                                                     .appId(APP_ID)
                                                     .build();
    when(infrastructureDefinitionService.get("app1", "infra3")).thenReturn(noScopingInfraDef);

    PageResponse<Service> pageResponse = new PageResponse<>();
    pageResponse.setResponse(Collections.emptyList());
    when(resourceLookupService.listWithTagFilters(pageRequest, null, EntityType.SERVICE, false, false))
        .thenReturn(pageResponse);

    assertThatThrownBy(() -> mockedServiceResourceService.list(pageRequest, false, false, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No common scoped Services for selected Infra Definitions: [ssh, k8s]");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testListServiceWithDeploymentTypeFilter() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<Service> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    queryParams.put("deploymentTypeFromMetadata", asList("SSH", "KUBERNETES"));
    pageRequest.addFilter("appId", EQ, Collections.singletonList("app1").toArray());
    when(appService.getAccountIdByAppId("app1")).thenReturn(ACCOUNT_ID);
    PageResponse<Service> pageResponse = new PageResponse<>();
    pageResponse.setResponse(Collections.emptyList());
    when(resourceLookupService.listWithTagFilters(pageRequest, null, EntityType.SERVICE, false, false))
        .thenReturn(pageResponse);

    mockedServiceResourceService.list(pageRequest, false, false, false, null);
    assertThat(pageRequest.getFilters()).isNotEmpty();
    assertThat(pageRequest.getFilters().size()).isEqualTo(2);
    assertThat(pageRequest.getFilters().get(1).getFieldName()).isEqualTo("deploymentType");
    assertThat(pageRequest.getFilters().get(1).getOp()).isEqualTo(NOT_EXISTS);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldSetServiceDeploymentTypeAndArtifactTypeTag() {
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(featureFlagService.isEnabled(FeatureName.HARNESS_TAGS, ACCOUNT_ID)).thenReturn(true);
    when(limitCheckerFactory.getInstance(new Action(anyString(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    when(applicationManifestService.create(any()))
        .thenReturn(ApplicationManifest.builder().storeType(StoreType.Local).build());

    doNothing().when(auditServiceHelper).addEntityOperationIdentifierDataToAuditContext(any());
    doNothing().when(notificationService).sendNotificationAsync(any());
    Service helmService = Service.builder()
                              .name(SERVICE_NAME)
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .deploymentType(HELM)
                              .artifactType(ArtifactType.DOCKER)
                              .build();
    Service savedService = serviceResourceService.save(helmService);
    List<HarnessTagLink> tagLinksWithEntityId =
        harnessTagService.getTagLinksWithEntityId(ACCOUNT_ID, savedService.getUuid());
    assertThat(tagLinksWithEntityId).hasSize(2);
    assertTrue(tagLinksWithEntityId.stream().anyMatch(
        tagLink -> tagLink.getKey().equals("deploymentType") && tagLink.getValue().equals("HELM")));
    assertTrue(tagLinksWithEntityId.stream().anyMatch(
        tagLink -> tagLink.getKey().equals("artifactType") && tagLink.getValue().equals("DOCKER")));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdateServiceWithHelmVersion() {
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(featureFlagService.isEnabled(FeatureName.HARNESS_TAGS, ACCOUNT_ID)).thenReturn(true);
    when(limitCheckerFactory.getInstance(new Action(anyString(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));

    Service k8sService = Service.builder()
                             .name(SERVICE_NAME)
                             .accountId(ACCOUNT_ID)
                             .appId(APP_ID)
                             .deploymentType(KUBERNETES)
                             .artifactType(ArtifactType.DOCKER)
                             .description("Description")
                             .isK8sV2(true)
                             .build();
    Service service = serviceResourceService.save(k8sService);
    assertThat(service.getHelmVersion()).isEqualTo(HelmVersion.V2);
    assertThat(service.getDescription()).isEqualTo("Description");

    service.setHelmVersion(HelmVersion.V3);
    service.setDescription("UpdatedDescription");
    service = serviceResourceService.updateServiceWithHelmVersion(service);
    assertThat(service.getHelmVersion()).isEqualTo(HelmVersion.V3);
    assertThat(service.getDescription()).isEqualTo("Description");

    service.setHelmVersion(null);
    try {
      serviceResourceService.updateServiceWithHelmVersion(service);
      fail("Should not reach here");
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).isEqualTo("Helm Version is not set");
    }

    service.setK8sV2(false);
    service = serviceResourceService.save(service);
    try {
      serviceResourceService.updateServiceWithHelmVersion(service);
      service.setHelmVersion(null);
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).isEqualTo("Setting helm version is supported only for kubernetes deployment type");
    }

    service.setDeploymentType(AWS_CODEDEPLOY);
    service.setHelmVersion(null);
    service = serviceResourceService.update(service);
    try {
      serviceResourceService.updateServiceWithHelmVersion(service);
      service.setHelmVersion(null);
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).isEqualTo("Setting helm version is supported only for kubernetes deployment type");
    }
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSaveCustomDeploymentTypeService() {
    Service service = Service.builder()
                          .name("custom-service")
                          .appId(APP_ID)
                          .deploymentType(CUSTOM)
                          .deploymentTypeTemplateId(TEMPLATE_ID)
                          .build();
    serviceResourceService.save(service);

    verify(customDeploymentTypeService, times(1)).putCustomDeploymentTypeNameIfApplicable(service);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testListByDeploymentType() {
    persistence.save(Service.builder().uuid("1").appId(APP_ID).deploymentType(SSH).build());
    persistence.save(Service.builder().uuid("2").appId(APP_ID).deploymentType(KUBERNETES).build());
    persistence.save(Service.builder().uuid("3").appId(APP_ID).artifactType(ArtifactType.JAR).build());
    persistence.save(Service.builder().uuid("4").appId(APP_ID).artifactType(ArtifactType.AWS_CODEDEPLOY).build());
    persistence.save(
        Service.builder().uuid("5").appId(APP_ID).deploymentType(CUSTOM).deploymentTypeTemplateId(TEMPLATE_ID).build());
    assertThat(serviceResourceService.listByDeploymentType(APP_ID, SSH.name(), null)
                   .stream()
                   .map(Service::getUuid)
                   .collect(Collectors.toList()))
        .containsExactly("1", "3");

    assertThat(serviceResourceService.listByDeploymentType(APP_ID, CUSTOM.name(), TEMPLATE_ID)
                   .stream()
                   .map(Service::getUuid)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("3", "5");
    asList("1", "2", "3", "4", "5").forEach(id -> persistence.delete(Service.class, id));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpdateCustomDeploymentTypeService() {
    Service service = Service.builder()
                          .name("custom-service")
                          .appId(APP_ID)
                          .uuid(SERVICE_ID)
                          .deploymentType(CUSTOM)
                          .deploymentTypeTemplateId(TEMPLATE_ID)
                          .build();
    persistence.save(service);
    serviceResourceService.update(service);

    verify(customDeploymentTypeService, times(1)).putCustomDeploymentTypeNameIfApplicable(service);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testDeleteServiceWithServiceCommand() {
    final String UUID = RandomStringUtils.randomAlphanumeric(32);

    Command command = aCommand().withName("Install").withAccountId(ACCOUNT_ID).withOriginEntityId(UUID).build();
    command.setAppId(APP_ID);

    ServiceCommand serviceCommand = aServiceCommand()
                                        .withName("Install")
                                        .withServiceId(SERVICE_ID)
                                        .withAppId(APP_ID)
                                        .withAccountId(ACCOUNT_ID)
                                        .withUuid(UUID)
                                        .build();

    List<ServiceCommand> serviceCommands = new ArrayList<>();
    serviceCommands.add(serviceCommand);

    Service service = Service.builder()
                          .name("custom-service")
                          .appId(APP_ID)
                          .uuid(SERVICE_ID)
                          .deploymentType(CUSTOM)
                          .accountId(ACCOUNT_ID)
                          .deploymentTypeTemplateId(TEMPLATE_ID)
                          .serviceCommands(serviceCommands)
                          .build();

    persistence.save(command);
    persistence.save(serviceCommand);
    persistence.save(service);

    serviceResourceService.delete(APP_ID, SERVICE_ID);
    verify(appService, times(2)).getAccountIdByAppId(APP_ID);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSaveClonedCommandWithNewName() {
    ServiceCommand clonedServiceCommand = ServiceCommand.Builder.aServiceCommand()
                                              .withAccountId(ACCOUNT_ID)
                                              .withAppId(APP_ID)
                                              .withName("Name-clone")
                                              .withServiceId(SERVICE_ID)
                                              .withCommand(Command.Builder.aCommand()
                                                               .withCommandType(CommandType.OTHER)
                                                               .withName("Name")
                                                               .withAccountId(ACCOUNT_ID)
                                                               .build())
                                              .build();
    serviceResourceService.addServiceCommand(APP_ID, SERVICE_ID, clonedServiceCommand, false);
    verify(commandService).save(commandCaptor.capture(), booleanCaptor.capture());

    assertThat(commandCaptor.getValue().getName()).isEqualTo(clonedServiceCommand.getName());
    assertThat(booleanCaptor.getValue()).isFalse();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetHelmCommandFlags() {
    Service service = Service.builder().appId(APP_ID).uuid(SERVICE_ID).helmVersion(V2).deploymentType(HELM).build();
    persistence.save(service);
    assertThat(serviceResourceService.getHelmCommandFlags(V2, APP_ID, SERVICE_ID, StoreType.HelmChartRepo))
        .containsExactlyInAnyOrder(LIST, INSTALL, TEMPLATE, ROLLBACK, VERSION, DELETE, UPGRADE, HISTORY, FETCH);
    assertThat(serviceResourceService.getHelmCommandFlags(null, APP_ID, SERVICE_ID, StoreType.HelmChartRepo))
        .containsExactlyInAnyOrder(LIST, INSTALL, TEMPLATE, ROLLBACK, VERSION, DELETE, UPGRADE, HISTORY, FETCH);
    assertThat(serviceResourceService.getHelmCommandFlags(V3, APP_ID, SERVICE_ID, StoreType.HelmChartRepo))
        .containsExactlyInAnyOrder(
            LIST, INSTALL, TEMPLATE, ROLLBACK, VERSION, UNINSTALL, UPGRADE, HISTORY, PULL, REPO_ADD, REPO_UPDATE);

    assertThat(serviceResourceService.getHelmCommandFlags(V2, APP_ID, SERVICE_ID, StoreType.HelmSourceRepo))
        .containsExactlyInAnyOrder(LIST, INSTALL, TEMPLATE, ROLLBACK, VERSION, DELETE, UPGRADE, HISTORY);
    assertThat(serviceResourceService.getHelmCommandFlags(null, APP_ID, SERVICE_ID, StoreType.HelmSourceRepo))
        .containsExactlyInAnyOrder(LIST, INSTALL, TEMPLATE, ROLLBACK, VERSION, DELETE, UPGRADE, HISTORY);
    assertThat(serviceResourceService.getHelmCommandFlags(V3, APP_ID, SERVICE_ID, StoreType.HelmSourceRepo))
        .containsExactlyInAnyOrder(LIST, INSTALL, TEMPLATE, ROLLBACK, VERSION, UNINSTALL, UPGRADE, HISTORY);

    assertThat(serviceResourceService.getHelmCommandFlags(V2, APP_ID, SERVICE_ID, StoreType.CUSTOM))
        .containsExactlyInAnyOrder(LIST, INSTALL, TEMPLATE, ROLLBACK, VERSION, DELETE, UPGRADE, HISTORY, FETCH);
    assertThat(serviceResourceService.getHelmCommandFlags(null, APP_ID, SERVICE_ID, StoreType.CUSTOM))
        .containsExactlyInAnyOrder(LIST, INSTALL, TEMPLATE, ROLLBACK, VERSION, DELETE, UPGRADE, HISTORY, FETCH);
    assertThat(serviceResourceService.getHelmCommandFlags(V3, APP_ID, SERVICE_ID, StoreType.CUSTOM))
        .containsExactlyInAnyOrder(
            LIST, INSTALL, TEMPLATE, ROLLBACK, VERSION, UNINSTALL, UPGRADE, HISTORY, PULL, REPO_ADD, REPO_UPDATE);

    service.setDeploymentType(KUBERNETES);
    service.setHelmVersion(V3);
    persistence.save(service);
    assertThat(serviceResourceService.getHelmCommandFlags(V2, APP_ID, SERVICE_ID, StoreType.HelmChartRepo))
        .containsExactlyInAnyOrder(FETCH, VERSION, TEMPLATE);
    assertThat(serviceResourceService.getHelmCommandFlags(null, APP_ID, SERVICE_ID, StoreType.HelmChartRepo))
        .containsExactlyInAnyOrder(PULL, VERSION, TEMPLATE, REPO_UPDATE, REPO_ADD);
    assertThat(serviceResourceService.getHelmCommandFlags(V3, APP_ID, SERVICE_ID, StoreType.HelmChartRepo))
        .containsExactlyInAnyOrder(PULL, VERSION, TEMPLATE, REPO_UPDATE, REPO_ADD);

    assertThat(serviceResourceService.getHelmCommandFlags(V2, APP_ID, SERVICE_ID, StoreType.HelmSourceRepo))
        .containsExactlyInAnyOrder(VERSION, TEMPLATE);
    assertThat(serviceResourceService.getHelmCommandFlags(null, APP_ID, SERVICE_ID, StoreType.HelmSourceRepo))
        .containsExactlyInAnyOrder(VERSION, TEMPLATE);
    assertThat(serviceResourceService.getHelmCommandFlags(V3, APP_ID, SERVICE_ID, StoreType.HelmSourceRepo))
        .containsExactlyInAnyOrder(VERSION, TEMPLATE);

    assertThat(serviceResourceService.getHelmCommandFlags(V2, APP_ID, SERVICE_ID, StoreType.CUSTOM))
        .containsExactlyInAnyOrder(FETCH, VERSION, TEMPLATE);
    assertThat(serviceResourceService.getHelmCommandFlags(null, APP_ID, SERVICE_ID, StoreType.CUSTOM))
        .containsExactlyInAnyOrder(PULL, VERSION, TEMPLATE, REPO_UPDATE, REPO_ADD);
    assertThat(serviceResourceService.getHelmCommandFlags(V3, APP_ID, SERVICE_ID, StoreType.CUSTOM))
        .containsExactlyInAnyOrder(PULL, VERSION, TEMPLATE, REPO_UPDATE, REPO_ADD);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetHelmCommandFlagsForOldServices() {
    Service service = Service.builder().appId(APP_ID).uuid(SERVICE_ID).build();
    persistence.save(service);
    assertThat(serviceResourceService.getHelmCommandFlags(null, APP_ID, SERVICE_ID, null)).isEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testdeleteHelmValueYaml() {
    when(applicationManifestService.getByServiceId(any(), any(), any())).thenReturn(applicationManifest);
    serviceResourceService.deleteHelmValueYaml(APP_ID, SERVICE_ID);
    verify(applicationManifestService, times(1)).deleteAppManifest(APP_ID, applicationManifest.getUuid());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testcreateValuesAppManifest() {
    ApplicationManifest appManifest =
        ApplicationManifest.builder().storeType(StoreType.Local).serviceId(SERVICE_ID).build();
    appManifest.setAppId(APP_ID);
    when(applicationManifestService.create(appManifest)).thenReturn(appManifest);
    assertThat(serviceResourceService.createValuesAppManifest(APP_ID, SERVICE_ID, appManifest)).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testupdateValuesAppManifest() {
    ApplicationManifest savedApplicationManifest =
        ApplicationManifest.builder().storeType(StoreType.Local).serviceId("SERVICE_ID2").build();
    savedApplicationManifest.setAppId(APP_ID);

    when(applicationManifestService.getById(APP_ID, "APPMANIFEST_ID")).thenReturn(savedApplicationManifest);
    when(applicationManifestService.update(applicationManifest)).thenReturn(applicationManifest);
    ApplicationManifest updatedManifest =
        serviceResourceService.updateValuesAppManifest(APP_ID, SERVICE_ID, "APPMANIFEST_ID", applicationManifest);
    assertThat(updatedManifest).isNotNull();
    assertThat(updatedManifest.getServiceId()).isEqualTo("SERVICE_ID2");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testupdateManifestFile() {
    ManifestFile manifestFile = ManifestFile.builder().fileName("manifestFile1").build();
    when(applicationManifestService.getAppManifest(APP_ID, null, SERVICE_ID, AppManifestKind.VALUES))
        .thenReturn(applicationManifest);
    when(applicationManifestService.getManifestFileById(APP_ID, "APPMANIFEST_ID")).thenReturn(manifestFile);
    when(applicationManifestService.upsertApplicationManifestFile(manifestFile, applicationManifest, false))
        .thenReturn(manifestFile);
    ManifestFile updatedManifestFile = serviceResourceService.updateManifestFile(
        APP_ID, SERVICE_ID, "APPMANIFEST_ID", manifestFile, AppManifestKind.VALUES);
    assertThat(updatedManifestFile).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testaddArtifactStreamId() {
    Service service = Service.builder().uuid(SERVICE_ID).appId(APP_ID).build();
    persistence.save(service);
    doReturn(service).when(spyServiceResourceService).get(APP_ID, SERVICE_ID, false);
    Service updatedService = serviceResourceService.addArtifactStreamId(service, ARTIFACT_STREAM_ID);
    assertThat(updatedService.getArtifactStreamIds()).isNotNull();
    assertThat(updatedService.getArtifactStreamIds().get(0).equals(ARTIFACT_STREAM_ID)).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testsetHelmValueYaml() {
    Service service = Service.builder().uuid(SERVICE_ID).appId(APP_ID).build();
    persistence.save(service);

    ManifestFile manifestFile =
        ManifestFile.builder().applicationManifestId("APPMANIFEST_ID").fileName("manifestFile1").build();
    when(applicationManifestService.getManifestFileById(APP_ID, "APPMANIFEST_ID")).thenReturn(manifestFile);
    when(applicationManifestService.getManifestFileByFileName("APPMANIFEST_ID", "values.yaml"))
        .thenReturn(manifestFile);

    persistence.save(applicationManifest);
    when(applicationManifestService.getAppManifest(APP_ID, null, SERVICE_ID, AppManifestKind.VALUES))
        .thenReturn(applicationManifest);
    when(applicationManifestService.getByServiceId(APP_ID, SERVICE_ID, AppManifestKind.VALUES))
        .thenReturn(applicationManifest);

    when(applicationManifestService.getManifestFileById(APP_ID, null)).thenReturn(manifestFile);
    when(applicationManifestService.getManifestFileByFileName("APPMANIFEST_ID", "values.yaml"))
        .thenReturn(manifestFile);
    doReturn(service).when(spyServiceResourceService).get(APP_ID, SERVICE_ID, false);
    assertThat(serviceResourceService.setHelmValueYaml(APP_ID, SERVICE_ID, KubernetesPayload.builder().build()))
        .isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testgetWithHelmValues() {
    Service service = Service.builder().appId(APP_ID).uuid(SERVICE_ID).build();
    persistence.save(service);
    doReturn(service).when(spyServiceResourceService).get(APP_ID, SERVICE_ID);
    assertThat(serviceResourceService.getWithHelmValues(APP_ID, SERVICE_ID, null)).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testupdateWithHelmValues() {
    Service service = Service.builder().name("SERVICE_ID1").appId(APP_ID).uuid(SERVICE_ID).build();
    persistence.save(service);
    when(applicationManifestService.getAppManifest(APP_ID, null, SERVICE_ID, AppManifestKind.VALUES))
        .thenReturn(applicationManifest);
    ManifestFile manifestFile = ManifestFile.builder()
                                    .applicationManifestId("APPMANIFEST_ID")
                                    .fileContent("fileContent")
                                    .fileName("manifestFile1")
                                    .build();
    when(applicationManifestService.getManifestFileByFileName("APPMANIFEST_ID", "values.yaml"))
        .thenReturn(manifestFile);
    doReturn(service).when(spyServiceResourceService).update(service, false);
    Service updatedService = serviceResourceService.updateWithHelmValues(service);
    assertThat(updatedService.getHelmValueYaml()).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testcheckArtifactNeededForHelm() {
    when(applicationManifestUtils.getHelmValuesYamlFiles(APP_ID, SERVICE_TEMPLATE_ID))
        .thenReturn(Arrays.asList("values.yaml"));
    assertThat(serviceResourceService.checkArtifactNeededForHelm(APP_ID, SERVICE_TEMPLATE_ID)).isFalse();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testupsertHelmChartSpecification() {
    Service service = getService();
    persistence.save(service);
    HelmChartSpecification helmChartSpecification =
        HelmChartSpecification.builder().chartName("chartName").chartVersion("1.0").build();
    helmChartSpecification.setAppId(APP_ID);
    helmChartSpecification.setServiceId(SERVICE_ID);
    HelmChartSpecification createdHelmChartSpec =
        serviceResourceService.createHelmChartSpecification(helmChartSpecification);
    assertThat(createdHelmChartSpec.getChartName()).isEqualTo("chartName");
  }

  private Service getService() {
    return Service.builder().name("SERVICE_ID1").appId(APP_ID).uuid(SERVICE_ID).accountId(ACCOUNT_ID).build();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testlistByCustomDeploymentTypeId() {
    Service service = getService();
    service.setDeploymentTypeTemplateId("DeploymentTypeTemplate");
    persistence.save(service);
    assertThat(
        serviceResourceService.listByCustomDeploymentTypeId(ACCOUNT_ID, Arrays.asList("DeploymentTypeTemplate"), 10))
        .isNotEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testfetchServicesByUuids() {
    persistence.save(getService());
    List serviceUuids = Arrays.asList(SERVICE_ID, "SERVICE_ID1");
    List<Service> services = serviceResourceService.fetchServicesByUuids(APP_ID, serviceUuids);
    assertThat(services).isNotEmpty();
    assertThat(services).hasSize(1);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testfetchServicesByUuidsByAccountId() {
    persistence.save(getService());
    List serviceUuids = Arrays.asList(SERVICE_ID, "SERVICE_ID1");
    List<Service> services = serviceResourceService.fetchServicesByUuidsByAccountId(ACCOUNT_ID, serviceUuids);
    assertThat(services).isNotEmpty();
    assertThat(services).hasSize(1);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testIsK8sV2Service() {
    Service k8sService = Service.builder().isK8sV2(true).build();

    doReturn(k8sService).when(spyServiceResourceService).get(APP_ID, SERVICE_ID, false);
    assertThat(spyServiceResourceService.isK8sV2Service(APP_ID, SERVICE_ID)).isTrue();

    k8sService.setK8sV2(false);
    assertThat(spyServiceResourceService.isK8sV2Service(APP_ID, SERVICE_ID)).isFalse();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCheckAndSetServiceAsK8sV2() {
    Service k8sService = Service.builder().isK8sV2(true).build();
    spyServiceResourceService.checkAndSetServiceAsK8sV2(k8sService);
    assertThat(k8sService.isK8sV2()).isTrue();
    k8sService.setK8sV2(false);
    spyServiceResourceService.checkAndSetServiceAsK8sV2(k8sService);
    assertThat(k8sService.isK8sV2()).isFalse();
    k8sService.setDeploymentType(KUBERNETES);
    spyServiceResourceService.checkAndSetServiceAsK8sV2(k8sService);
    assertThat(k8sService.isK8sV2()).isTrue();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testSaveForArtifactTypeValidation() {
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(featureFlagService.isEnabled(FeatureName.HARNESS_TAGS, ACCOUNT_ID)).thenReturn(true);
    when(limitCheckerFactory.getInstance(new Action(anyString(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    when(applicationManifestService.create(any()))
        .thenReturn(ApplicationManifest.builder().storeType(StoreType.Local).build());

    doNothing().when(auditServiceHelper).addEntityOperationIdentifierDataToAuditContext(any());
    doNothing().when(notificationService).sendNotificationAsync(any());

    List<Service> services = generateTestServicesData();
    services.forEach(service -> assertThatCode(() -> serviceResourceService.save(service)).doesNotThrowAnyException());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testSaveForArtifactTypevalidationFail() {
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(featureFlagService.isEnabled(FeatureName.HARNESS_TAGS, ACCOUNT_ID)).thenReturn(true);
    when(limitCheckerFactory.getInstance(new Action(anyString(), ActionType.CREATE_SERVICE)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_SERVICE));
    when(applicationManifestService.create(any()))
        .thenReturn(ApplicationManifest.builder().storeType(StoreType.Local).build());

    doNothing().when(auditServiceHelper).addEntityOperationIdentifierDataToAuditContext(any());
    doNothing().when(notificationService).sendNotificationAsync(any());

    final Map<Service, String> servicesData = getTestInvalidServicesData();
    servicesData.forEach((service, message)
                             -> assertThatThrownBy(() -> serviceResourceService.save(service))
                                    .isInstanceOf(InvalidRequestException.class)
                                    .hasMessageContaining(message));
  }

  @NotNull
  private List<Service> generateTestServicesData() {
    List<Service> services = new ArrayList<>();
    services.add(buildService(KUBERNETES, ArtifactType.DOCKER));
    services.add(buildService(HELM, ArtifactType.DOCKER));
    services.add(buildService(ECS, ArtifactType.DOCKER));
    services.add(buildService(AWS_CODEDEPLOY, ArtifactType.AWS_CODEDEPLOY));
    services.add(buildService(AWS_LAMBDA, ArtifactType.AWS_LAMBDA));
    services.add(buildService(AMI, ArtifactType.AMI));
    services.add(buildService(PCF, ArtifactType.PCF));
    services.add(buildService(SSH, ArtifactType.NUGET));
    services.add(buildService(WINRM, ArtifactType.NUGET));
    services.add(buildService(AZURE_VMSS, ArtifactType.AZURE_MACHINE_IMAGE));
    services.add(buildService(CUSTOM, ArtifactType.OTHER));
    return services;
  }

  @NotNull
  private Map<Service, String> getTestInvalidServicesData() {
    Map<Service, String> services = new HashMap<>();
    services.put(buildService(KUBERNETES, ArtifactType.NUGET),
        "Only DOCKER artifactType allowed for KUBERNETES Deployment Type");
    services.put(buildService(HELM, ArtifactType.AMI), "Only DOCKER artifactType allowed for HELM Deployment Type");
    services.put(buildService(ECS, ArtifactType.AMI),
        "Only DOCKER artifactType allowed for Amazon EC2 Container Services (ECS) Deployment Type");
    services.put(buildService(AWS_CODEDEPLOY, ArtifactType.AWS_LAMBDA),
        "Only AWS_CODEDEPLOY artifactType allowed for AWS CODEDEPLOY Deployment Type");
    services.put(buildService(AWS_LAMBDA, ArtifactType.AMI),
        "Only AWS_LAMBDA artifactType allowed for AWS Lambda Deployment Type");
    services.put(buildService(AMI, ArtifactType.DOCKER), "Only AMI artifactType allowed for AMI Deployment Type");
    services.put(buildService(PCF, ArtifactType.OTHER),
        "Only PCF artifactType allowed for Tanzu Application Services Deployment Type");
    services.put(buildService(AZURE_VMSS, ArtifactType.OTHER),
        "Only AZURE_MACHINE_IMAGE artifactType allowed for Azure Virtual Machine Scale Set Deployment Type");
    return services;
  }

  private Service buildService(DeploymentType deploymentType, ArtifactType artifactType) {
    return Service.builder()
        .appId(APP_ID)
        .name(SERVICE_NAME)
        .accountId(ACCOUNT_ID)
        .deploymentType(deploymentType)
        .artifactType(artifactType)
        .build();
  }
}
