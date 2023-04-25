/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.INDER;

import static software.wings.beans.PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping;
import static software.wings.beans.ServiceInstance.Builder.aServiceInstance;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.COMMAND_UNIT_TYPE;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.HOST_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_INSTANCE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HQuery;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Activity;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.infrastructure.Host;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.ServiceInstanceServiceImpl;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceInstanceService;

import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

/**
 * Created by anubhaw on 5/26/16.
 */
public class ServiceInstanceServiceTest extends WingsBaseTest {
  private final ServiceTemplate serviceTemplate = aServiceTemplate().withUuid(TEMPLATE_ID).withUuid(SERVICE_ID).build();
  private final Host host = aHost().withUuid(HOST_ID).build();
  @Mock private WingsPersistence wingsPersistence;
  @Mock private HQuery<ServiceInstance> query;
  @Mock private UpdateOperations<ServiceInstance> updateOperations;
  @Mock private FieldEnd end;
  @InjectMocks @Inject private ServiceInstanceService serviceInstanceService;
  @Spy @InjectMocks private ServiceInstanceService spyInstanceService = new ServiceInstanceServiceImpl();
  @Mock private AppService appService;
  private ServiceInstance.Builder builder =
      aServiceInstance()
          .withAppId(APP_ID)
          .withEnvId(ENV_ID)
          .withHost(aHost().withAppId(APP_ID).withEnvId(ENV_ID).withUuid(HOST_ID).build())
          .withServiceTemplate(serviceTemplate)
          .withArtifactStreamName(ARTIFACT_STREAM_NAME)
          .withArtifactStreamId(ARTIFACT_STREAM_ID)
          .withArtifactId(ARTIFACT_ID)
          .withArtifactName(ARTIFACT_NAME);

  /**
   * Sets up.
   *
   * @throws Exception the exception
   */
  @Before
  public void setUp() throws Exception {
    when(wingsPersistence.createQuery(ServiceInstance.class)).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    when(query.field(anyString())).thenReturn(end);
    when(end.hasAnyOf(anyCollection())).thenReturn(query);
    when(wingsPersistence.createUpdateOperations(ServiceInstance.class)).thenReturn(updateOperations);
    when(updateOperations.set(anyString(), any())).thenReturn(updateOperations);
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);
  }

  /**
   * Should list service instances.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldListServiceInstances() {
    PageResponse<ServiceInstance> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(builder.build()));
    pageResponse.setTotal(1l);
    PageRequest pageRequest = aPageRequest()
                                  .withLimit("50")
                                  .withOffset("0")
                                  .addFilter("appId", EQ, APP_ID)
                                  .addFilter("envId", EQ, ENV_ID)
                                  .build();
    when(wingsPersistence.query(ServiceInstance.class, pageRequest)).thenReturn(pageResponse);
    PageResponse<ServiceInstance> serviceInstances = serviceInstanceService.list(pageRequest);
    assertThat(serviceInstances).isNotNull();
    assertThat(serviceInstances.getResponse().get(0)).isInstanceOf(ServiceInstance.class);
  }

  /**
   * Should save service instance.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldSaveServiceInstance() {
    ServiceInstance serviceInstance = builder.build();
    when(wingsPersistence.saveAndGet(eq(ServiceInstance.class), eq(serviceInstance))).thenReturn(serviceInstance);
    ServiceInstance savedServiceInstance = serviceInstanceService.save(serviceInstance);
    assertThat(savedServiceInstance).isNotNull();
    assertThat(savedServiceInstance).isInstanceOf(ServiceInstance.class);
  }

  /**
   * Should update service instance.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldUpdateActivity() {
    long createdAt = System.currentTimeMillis();
    Activity activity = Activity.builder()
                            .serviceInstanceId(SERVICE_INSTANCE_ID)
                            .artifactStreamId(ARTIFACT_STREAM_ID)
                            .artifactStreamName(ARTIFACT_STREAM_NAME)
                            .artifactId(ARTIFACT_ID)
                            .artifactName(ARTIFACT_NAME)
                            .status(ExecutionStatus.SUCCESS)
                            .commandName(COMMAND_NAME)
                            .commandType(COMMAND_UNIT_TYPE)
                            .build();
    activity.setCreatedAt(createdAt);
    activity.setUuid(ACTIVITY_ID);
    activity.setAppId(APP_ID);
    serviceInstanceService.updateActivity(activity);

    verify(wingsPersistence).update(any(Query.class), any(UpdateOperations.class));

    verify(query).filter("appId", APP_ID);
    verify(query).filter(ID_KEY, SERVICE_INSTANCE_ID);

    verify(updateOperations).set("artifactId", ARTIFACT_ID);
    verify(updateOperations).set("artifactName", ARTIFACT_NAME);
    verify(updateOperations).set("artifactStreamId", ARTIFACT_STREAM_ID);
    verify(updateOperations).set("artifactStreamName", ARTIFACT_STREAM_NAME);
    verify(updateOperations).set("artifactDeployedOn", createdAt);
    verify(updateOperations).set("artifactDeploymentStatus", ExecutionStatus.SUCCESS);
    verify(updateOperations).set("artifactDeploymentActivityId", ACTIVITY_ID);
    verify(updateOperations).set("lastActivityId", ACTIVITY_ID);
    verify(updateOperations).set("lastActivityStatus", ExecutionStatus.SUCCESS);
    verify(updateOperations).set("commandName", COMMAND_NAME);
    verify(updateOperations).set("commandType", COMMAND_UNIT_TYPE);
    verify(updateOperations).set("lastActivityCreatedAt", createdAt);
  }

  /**
   * Should get service instance.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetServiceInstance() {
    when(query.get()).thenReturn(builder.withUuid(SERVICE_INSTANCE_ID).build());
    ServiceInstance savedServiceInstance = serviceInstanceService.get(APP_ID, ENV_ID, SERVICE_INSTANCE_ID);
    assertThat(savedServiceInstance).isNotNull();
    assertThat(savedServiceInstance).isInstanceOf(ServiceInstance.class);
    verify(query).get();
    verify(query).filter("appId", APP_ID);
    verify(query).filter("envId", ENV_ID);
    verify(query).filter(ID_KEY, SERVICE_INSTANCE_ID);
  }

  /**
   * Should delete service instance.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDeleteServiceInstance() {
    serviceInstanceService.delete(APP_ID, ENV_ID, SERVICE_INSTANCE_ID);
    verify(wingsPersistence).delete(query);
    verify(query).filter("appId", APP_ID);
    verify(query).filter("envId", ENV_ID);
    verify(query).filter(ID_KEY, SERVICE_INSTANCE_ID);
  }

  /**
   * Should delete by env.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDeleteByEnv() {
    when(query.asList()).thenReturn(asList(builder.withUuid(SERVICE_INSTANCE_ID).build()));
    doNothing().when(spyInstanceService).delete(APP_ID, ENV_ID, SERVICE_INSTANCE_ID);
    spyInstanceService.deleteByEnv(APP_ID, ENV_ID);
    verify(spyInstanceService).delete(APP_ID, ENV_ID, SERVICE_INSTANCE_ID);
    verify(query).filter("appId", APP_ID);
    verify(query).filter("envId", ENV_ID);
    verify(query).asList();
  }

  /**
   * Should delete by service template.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDeleteByServiceTemplate() {
    when(query.asList()).thenReturn(asList(builder.withUuid(SERVICE_INSTANCE_ID).build()));
    doNothing().when(spyInstanceService).delete(APP_ID, ENV_ID, SERVICE_INSTANCE_ID);
    spyInstanceService.deleteByServiceTemplate(APP_ID, ENV_ID, TEMPLATE_ID);
    verify(spyInstanceService).delete(APP_ID, ENV_ID, SERVICE_INSTANCE_ID);
    verify(query).filter("appId", APP_ID);
    verify(query).filter("envId", ENV_ID);
    verify(query).filter("serviceTemplate", TEMPLATE_ID);
    verify(query).asList();
  }

  /**
   * Should update host instance mapping.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldUpdateHostInstanceMapping() {
    List<Host> newHostList = singletonList(aHost()
                                               .withAppId(APP_ID)
                                               .withEnvId(ENV_ID)
                                               .withUuid("NEW_HOST_ID")
                                               .withHostName(HOST_NAME)
                                               .withPublicDns(HOST_NAME)
                                               .build());
    ServiceTemplate serviceTemplate =
        aServiceTemplate().withAppId(APP_ID).withEnvId(ENV_ID).withUuid(TEMPLATE_ID).withServiceId(SERVICE_ID).build();
    serviceInstanceService.updateInstanceMappings(
        serviceTemplate, aPhysicalInfrastructureMapping().withUuid(INFRA_MAPPING_ID).build(), newHostList);
    verify(query).filter("infraMappingId", INFRA_MAPPING_ID);
    verify(query).filter("hostId", "NEW_HOST_ID");
    verify(query).filter("hostName", HOST_NAME);
    verify(query).filter("publicDns", HOST_NAME);
    verify(wingsPersistence)
        .saveAndGet(ServiceInstance.class,
            aServiceInstance()
                .withAppId(APP_ID)
                .withEnvId(ENV_ID)
                .withServiceTemplate(serviceTemplate)
                .withHost(newHostList.get(0))
                .build());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldSaveServiceInstanceWithAccountId() {
    ServiceInstance serviceInstance = builder.build();
    when(wingsPersistence.saveAndGet(eq(ServiceInstance.class), any(ServiceInstance.class))).thenAnswer(invocation -> {
      ServiceInstance serviceInstance1 = invocation.getArgument(1, ServiceInstance.class);
      return serviceInstance1;
    });
    ServiceInstance savedServiceInstance = serviceInstanceService.save(serviceInstance);
    assertThat(savedServiceInstance.getAccountId()).isEqualTo(ACCOUNT_ID);
  }
}
