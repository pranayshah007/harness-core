/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.shell.AccessType.USER_PASSWORD;

import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.HostConnectionAttributes.Builder.aHostConnectionAttributes;
import static software.wings.beans.HostConnectionCredential.HostConnectionCredentialBuilder.aHostConnectionCredential;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.beans.infrastructure.Host.HostKeys;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.HOST_CONN_ATTR_ID;
import static software.wings.utils.WingsTestConstants.HOST_CONN_ATTR_KEY_ID;
import static software.wings.utils.WingsTestConstants.HOST_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.PUBLIC_DNS;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HQuery;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.HostConnectionCredential;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.Host;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.utils.WingsTestConstants;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by anubhaw on 6/7/16.
 */
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class HostServiceTest extends WingsBaseTest {
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private WingsPersistence wingsPersistence;

  @Mock private EnvironmentService environmentService;
  @Mock private ConfigService configService;
  @Mock private ServiceInstanceService serviceInstanceService;

  @Inject @InjectMocks private HostService hostService;

  @Mock private HQuery<Host> hostQuery;
  @Mock private FieldEnd hostQueryEnd;
  @Mock private UpdateOperations<Host> updateOperations;

  private SettingAttribute HOST_CONN_ATTR_PWD =
      aSettingAttribute()
          .withUuid(HOST_CONN_ATTR_ID)
          .withValue(aHostConnectionAttributes().withAccessType(USER_PASSWORD).build())
          .build();
  private HostConnectionCredential CREDENTIAL =
      aHostConnectionCredential().withSshUser(USER_NAME).withSshPassword(WingsTestConstants.USER_PASSWORD).build();
  private Host.Builder hostBuilder =
      aHost().withAppId(APP_ID).withEnvId(ENV_ID).withHostName(HOST_NAME).withHostConnAttr(
          HOST_CONN_ATTR_PWD.getUuid());

  /**
   * Sets up.
   *
   * @throws Exception the exception
   */
  @Before
  public void setUp() throws Exception {
    when(wingsPersistence.createUpdateOperations(Host.class)).thenReturn(updateOperations);

    when(wingsPersistence.createQuery(Host.class)).thenReturn(hostQuery);
    when(hostQuery.filter(any(), any())).thenReturn(hostQuery);

    when(hostQuery.field(anyString())).thenReturn(hostQueryEnd);
    when(hostQueryEnd.hasAnyOf(anyCollection())).thenReturn(hostQuery);

    when(wingsPersistence.createQuery(Host.class)).thenReturn(hostQuery);
  }

  /**
   * Should list hosts.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldListHosts() {
    PageResponse<Host> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(hostBuilder.but().build()));
    pageResponse.setTotal(1l);
    PageRequest pageRequest = aPageRequest()
                                  .withLimit("50")
                                  .withOffset("0")
                                  .addFilter("appId", EQ, APP_ID)
                                  .addFilter("envId", EQ, ENV_ID)
                                  .build();
    when(wingsPersistence.query(Host.class, pageRequest)).thenReturn(pageResponse);
    PageResponse<Host> hosts = hostService.list(pageRequest);
    assertThat(hosts).isNotNull();
    assertThat(hosts.getResponse().get(0)).isInstanceOf(Host.class);
  }

  /**
   * Should get host.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetHost() {
    Host host = hostBuilder.build();
    when(hostQuery.get()).thenReturn(host);
    Host savedHost = hostService.get(APP_ID, ENV_ID, HOST_ID);
    verify(hostQuery).filter("appId", APP_ID);
    verify(hostQuery).filter("envId", ENV_ID);
    verify(hostQuery).filter(ID_KEY, HOST_ID);
    assertThat(savedHost).isNotNull();
    assertThat(savedHost).isInstanceOf(Host.class);
  }

  /**
   * Should update host.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldUpdateHost() {
    Host host = hostBuilder.withUuid(HOST_ID).build();
    when(hostQuery.get()).thenReturn(hostBuilder.build());
    Host savedHost = hostService.update(ENV_ID, host);
    verify(wingsPersistence).updateFields(Host.class, HOST_ID, ImmutableMap.of("hostConnAttr", HOST_CONN_ATTR_ID));
    assertThat(savedHost).isNotNull();
    assertThat(savedHost).isInstanceOf(Host.class);
  }

  /**
   * Should delete host.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDeleteHost() {
    Host host = hostBuilder.withAppId(APP_ID).withUuid(HOST_ID).build();
    when(hostQuery.get()).thenReturn(host);
    when(wingsPersistence.delete(any(Host.class))).thenReturn(true);
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(anEnvironment().name("PROD").build());
    hostService.delete(APP_ID, ENV_ID, HOST_ID);
    verify(hostQuery).filter("appId", APP_ID);
    verify(hostQuery).filter("envId", ENV_ID);
    verify(hostQuery).filter(ID_KEY, HOST_ID);
    verify(wingsPersistence).delete(host);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPruneDescendingObjects() {
    hostService.pruneDescendingEntities(APP_ID, HOST_ID);
    InOrder inOrder = inOrder(wingsPersistence, configService, serviceInstanceService);
    inOrder.verify(configService).pruneByHost(APP_ID, HOST_ID);
    inOrder.verify(serviceInstanceService).pruneByHost(APP_ID, HOST_ID);
  }

  /**
   * Should get hosts by host ids.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetHostsByHostIds() {
    when(hostQuery.asList()).thenReturn(asList(hostBuilder.withUuid(HOST_ID).build()));
    List<Host> hosts = hostService.getHostsByHostIds(APP_ID, ENV_ID, asList(HOST_ID));

    verify(hostQuery).asList();
    verify(hostQuery).filter("appId", APP_ID);
    verify(hostQuery).filter("envId", ENV_ID);
    verify(hostQuery).field(ID_KEY);
    verify(hostQueryEnd).hasAnyOf(asList(HOST_ID));
    assertThat(hosts.get(0)).isInstanceOf(Host.class);
    assertThat(hosts.get(0).getUuid()).isEqualTo(HOST_ID);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testSaveHost() {
    Host host = getCleanHost();
    Host savedHost = hostService.saveHost(host);
    verify(wingsPersistence).save(host);
    assertThat(savedHost).isEqualTo(host);

    Host host1 = getCleanHost();
    host1.setUuid(UUID);
    Instance aInstance = new Instance().withInstanceId("A");
    host1.setEc2Instance(aInstance);
    doReturn(host1).when(hostQuery).get();

    Host host2 = getCleanHost();
    host2.setUuid(UUID);
    Instance bInstance = new Instance().withInstanceId("B");
    host2.setEc2Instance(bInstance);
    savedHost = hostService.saveHost(host2);
    assertThat(savedHost.getEc2Instance().getInstanceId()).isEqualTo("B");
    verify(wingsPersistence).updateField(Host.class, UUID, HostKeys.ec2Instance, bInstance);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveHostWithExistingHostConnAttr() {
    Host host1 = getCleanHost();
    host1.setUuid(UUID);
    host1.setHostConnAttr(HOST_CONN_ATTR_ID);
    doReturn(host1).when(hostQuery).get();

    Host host2 = getCleanHost();
    host2.setUuid(UUID);
    host2.setHostConnAttr(HOST_CONN_ATTR_KEY_ID);
    Host savedHost = hostService.saveHost(host2);
    assertThat(savedHost.getHostConnAttr()).isEqualTo(HOST_CONN_ATTR_KEY_ID);
    verify(wingsPersistence).updateField(Host.class, UUID, HostKeys.hostConnAttr, HOST_CONN_ATTR_KEY_ID);
  }

  @NotNull
  private Host getCleanHost() {
    return aHost()
        .withServiceTemplateId(SERVICE_TEMPLATE_ID)
        .withHostName(HOST_NAME)
        .withPublicDns(PUBLIC_DNS)
        .withAppId(APP_ID)
        .withEnvId(ENV_ID)
        .withInfraMappingId(INFRA_MAPPING_ID)
        .build();
  }

  /**
   * Should bulk save.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldBulkSave() {
    /*
        ServiceTemplate serviceTemplate = aServiceTemplate().uuid(TEMPLATE_ID).build();
        SettingAttribute hostConnAttr =
            aSettingAttribute().uuid(HOST_CONN_ATTR_ID).value(aHostConnectionAttributes().withAccessType(USER_PASSWORD).build()).build();

        Host requestHost =
       aHost().appId(APP_ID).withHostName(HOST_NAME).withHostConnAttr(hostConnAttr.getUuid())
            .withServiceTemplates(asList(serviceTemplate)).withServiceTemplates(asList(serviceTemplate)).build();

        Host hostPreSave =
       aHost().appId(GLOBAL_APP_ID).withInfraId(INFRA_ID).withHostName(HOST_NAME).withHostConnAttr(hostConnAttr).build();
        Host hostPostSave =
       aHost().uuid(HOST_ID).appId(APP_ID).withInfraId(INFRA_ID).withHostName(HOST_NAME).withHostConnAttr(hostConnAttr).build();
        Host applicationHostPreSave =
            Host.PageResponseBuilder.aHost().appId(APP_ID).envId(ENV_ID).withInfraId(INFRA_ID).withHostName(HOST_NAME)
                .withHost(hostPostSave).build();
        Host applicationHostPostSave =
            Host.PageResponseBuilder.aHost().uuid(HOST_ID).appId(APP_ID).envId(ENV_ID).withInfraId(INFRA_ID).withHostName(HOST_NAME)
                .withHost(hostPostSave).build();

        when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(anEnvironment().name("PROD").build());
        when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(serviceTemplate);
        when(wingsPersistence.saveAndGet(Host.class, hostPreSave)).thenReturn(hostPostSave);
        when(wingsPersistence.saveAndGet(Host.class, applicationHostPreSave)).thenReturn(applicationHostPostSave);
        when(infrastructureService.get(INFRA_ID))
            .thenReturn(Infrastructure.PageResponseBuilder.anInfrastructure().type(STATIC).appId(GLOBAL_APP_ID).uuid(INFRA_ID).build());
        when(settingsService.get(GLOBAL_APP_ID, HOST_CONN_ATTR_ID)).thenReturn(hostConnAttr);

        hostService.bulkSave(INFRA_ID, ENV_ID, requestHost);

        verify(wingsPersistence).saveAndGet(Host.class, hostPreSave);
        verify(wingsPersistence).saveAndGet(Host.class, applicationHostPreSave);
        verify(notificationService).sendNotificationAsync(any(Notification.class));
        */
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldGetInfrastructureHostUsageByApplication() {
    /*å
        List<Application> applications =
            asList(anApplication().uuid("ID1").name("NAME1").build(),
       anApplication().uuid("ID2").name("NAME2").build()); PageResponse pageResponse = new PageResponse();
        pageResponse.setResponse(applications);
        when(appService.list(any(), eq(false), eq(0), eq(0))).thenReturn(pageResponse);
        when(wingsPersistence.getDatastore().createAggregation(Host.class)).thenReturn(aggregationPipeline);
        when(aggregationPipeline.match(hostQuery)).thenReturn(aggregationPipeline);
        when(aggregationPipeline.group(anyString(), any())).thenReturn(aggregationPipeline);
        when(aggregationPipeline.aggregate(HostUsage.class)).thenReturn(
            asList(HostUsage.PageResponseBuilder.aHostUsage().appId("ID1").withCount(1).build(),
       aHostUsage().appId("ID2").withCount(2).build()).listIterator());

        List<HostUsage> usageByApplication = hostService.getInfrastructureHostUsageByApplication(INFRA_ID);

        assertThat(usageByApplication).hasSize(2)
            .containsExactlyInAnyOrder(aHostUsage().appId("ID1").appName("NAME1").withCount(1).build(),
                aHostUsage().appId("ID2").appName("NAME2").withCount(2).build());
                */
  }
}
