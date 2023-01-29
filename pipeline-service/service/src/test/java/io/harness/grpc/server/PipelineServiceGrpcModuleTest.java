/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.server;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.GAURAV_NANDA;
import static io.harness.rule.OwnerRule.NAMAN;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import io.harness.ModuleType;
import io.harness.PipelineServiceConfiguration;
import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.interrupts.InterruptGrpcService;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.pms.contracts.expression.RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub;
import io.harness.pms.contracts.governance.JsonExpansionServiceGrpc.JsonExpansionServiceBlockingStub;
import io.harness.pms.contracts.plan.PlanCreationServiceGrpc.PlanCreationServiceBlockingStub;
import io.harness.pms.plan.execution.data.service.expressions.EngineExpressionGrpcServiceImpl;
import io.harness.pms.plan.execution.data.service.outcome.OutcomeServiceGrpcServerImpl;
import io.harness.pms.plan.execution.data.service.outputs.SweepingOutputServiceImpl;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.service.execution.PmsExecutionGrpcService;
import io.harness.pms.template.EntityReferenceGrpcService;
import io.harness.pms.template.VariablesServiceImpl;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.grpc.BindableService;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.services.HealthStatusManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineServiceGrpcModuleTest extends PipelineServiceTestBase {
  PipelineServiceGrpcModule pipelineServiceGrpcModule;
  PmsSdkInstanceService pmsSdkInstanceService;
  PmsExecutionGrpcService pmsExecutionGrpcService;
  SweepingOutputServiceImpl sweepingOutputService;
  OutcomeServiceGrpcServerImpl outcomeServiceGrpcServer;
  EngineExpressionGrpcServiceImpl engineExpressionGrpcService;
  InterruptGrpcService interruptGrpcService;
  EntityReferenceGrpcService entityReferenceGrpcService;
  VariablesServiceImpl variablesService;
  HealthStatusManager healthStatusManager;

  @Before
  public void setUp() {
    pipelineServiceGrpcModule = PipelineServiceGrpcModule.getInstance();
    pmsSdkInstanceService = mock(PmsSdkInstanceService.class);
    pmsExecutionGrpcService = mock(PmsExecutionGrpcService.class);
    sweepingOutputService = mock(SweepingOutputServiceImpl.class);
    outcomeServiceGrpcServer = mock(OutcomeServiceGrpcServerImpl.class);
    engineExpressionGrpcService = mock(EngineExpressionGrpcServiceImpl.class);
    interruptGrpcService = mock(InterruptGrpcService.class);
    entityReferenceGrpcService = mock(EntityReferenceGrpcService.class);
    variablesService = mock(VariablesServiceImpl.class);
    healthStatusManager = new HealthStatusManager();
    doCallRealMethod().when(sweepingOutputService).bindService();
    doCallRealMethod().when(entityReferenceGrpcService).bindService();
    doCallRealMethod().when(pmsSdkInstanceService).bindService();
    doCallRealMethod().when(variablesService).bindService();
    doCallRealMethod().when(interruptGrpcService).bindService();
    doCallRealMethod().when(outcomeServiceGrpcServer).bindService();
    doCallRealMethod().when(pmsExecutionGrpcService).bindService();
    doCallRealMethod().when(engineExpressionGrpcService).bindService();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetJsonExpansionHandlerClients() throws SSLException {
    PipelineServiceConfiguration configuration = new PipelineServiceConfiguration();
    GrpcClientConfig client1 = GrpcClientConfig.builder().target("t1").authority("a1").build();
    GrpcClientConfig client2 = GrpcClientConfig.builder().target("t2").authority("a2").build();
    Map<String, GrpcClientConfig> grpcClientConfigMap = new HashMap<>();
    grpcClientConfigMap.put("CD", client1);
    grpcClientConfigMap.put("CI", client2);
    configuration.setGrpcClientConfigs(grpcClientConfigMap);

    Map<ModuleType, JsonExpansionServiceBlockingStub> jsonExpansionHandlerClients =
        pipelineServiceGrpcModule.getJsonExpansionHandlerClients(configuration);
    assertThat(jsonExpansionHandlerClients).hasSize(3);
    assertThat(jsonExpansionHandlerClients).containsOnlyKeys(ModuleType.CD, ModuleType.CI, ModuleType.PMS);

    grpcClientConfigMap.clear();
    jsonExpansionHandlerClients = pipelineServiceGrpcModule.getJsonExpansionHandlerClients(configuration);
    assertThat(jsonExpansionHandlerClients).hasSize(1);
    assertThat(jsonExpansionHandlerClients).containsOnlyKeys(ModuleType.PMS);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testBindableServices() {
    Set<BindableService> bindableServices = pipelineServiceGrpcModule.bindableServices(healthStatusManager,
        pmsSdkInstanceService, pmsExecutionGrpcService, sweepingOutputService, outcomeServiceGrpcServer,
        engineExpressionGrpcService, interruptGrpcService, entityReferenceGrpcService, variablesService);
    assertThat(bindableServices)
        .containsOnly(healthStatusManager.getHealthService(), pmsExecutionGrpcService, pmsSdkInstanceService,
            sweepingOutputService, outcomeServiceGrpcServer, engineExpressionGrpcService, interruptGrpcService,
            entityReferenceGrpcService, variablesService);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testPmsGrpcInternalService() {
    Set<BindableService> bindableServices = pipelineServiceGrpcModule.bindableServices(healthStatusManager,
        pmsSdkInstanceService, pmsExecutionGrpcService, sweepingOutputService, outcomeServiceGrpcServer,
        engineExpressionGrpcService, interruptGrpcService, entityReferenceGrpcService, variablesService);
    Service service = pipelineServiceGrpcModule.pmsGrpcInternalService(
        healthStatusManager, bindableServices, Sets.newHashSet(new PipelineServiceGrpcErrorHandler()));
    assertThat(service).isNotNull();
    assertThat(service).isInstanceOf(GrpcInProcessServer.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testPmsGrpcService() {
    PipelineServiceConfiguration configuration = new PipelineServiceConfiguration();
    GrpcServerConfig grpcServerConfig = new GrpcServerConfig();
    grpcServerConfig.setConnectors(Collections.singletonList(Connector.builder().port(12001).build()));
    configuration.setGrpcServerConfig(grpcServerConfig);
    Set<BindableService> bindableServices = pipelineServiceGrpcModule.bindableServices(healthStatusManager,
        pmsSdkInstanceService, pmsExecutionGrpcService, sweepingOutputService, outcomeServiceGrpcServer,
        engineExpressionGrpcService, interruptGrpcService, entityReferenceGrpcService, variablesService);

    Service service = pipelineServiceGrpcModule.pmsGrpcService(
        configuration, healthStatusManager, bindableServices, Sets.newHashSet(new PipelineServiceGrpcErrorHandler()));
    assertThat(service).isNotNull();
    assertThat(service).isInstanceOf(GrpcServer.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetExpressionExecutionClients() throws SSLException {
    PipelineServiceConfiguration configuration = new PipelineServiceConfiguration();
    GrpcClientConfig client1 = GrpcClientConfig.builder().target("t1").authority("a1").build();
    GrpcClientConfig client2 = GrpcClientConfig.builder().target("t2").authority("a2").build();
    Map<String, GrpcClientConfig> grpcClientConfigMap = new HashMap<>();
    grpcClientConfigMap.put("CD", client1);
    grpcClientConfigMap.put("CI", client2);
    configuration.setGrpcClientConfigs(grpcClientConfigMap);

    Map<ModuleType, RemoteFunctorServiceBlockingStub> expressionExecutionClients =
        pipelineServiceGrpcModule.getExpressionExecutionClients(configuration);

    assertThat(expressionExecutionClients).hasSize(2);
    assertThat(expressionExecutionClients).containsOnlyKeys(ModuleType.CD, ModuleType.CI);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetGrpcClients() throws SSLException {
    PipelineServiceConfiguration configuration = new PipelineServiceConfiguration();
    GrpcClientConfig client1 = GrpcClientConfig.builder().target("t1").authority("a1").build();
    GrpcClientConfig client2 = GrpcClientConfig.builder().target("t2").authority("a2").build();
    Map<String, GrpcClientConfig> grpcClientConfigMap = new HashMap<>();
    grpcClientConfigMap.put("CD", client1);
    grpcClientConfigMap.put("CI", client2);
    configuration.setGrpcClientConfigs(grpcClientConfigMap);

    Map<ModuleType, PlanCreationServiceBlockingStub> moduleTypePlanCreationServiceBlockingStubMap =
        pipelineServiceGrpcModule.grpcClients(configuration);
    assertThat(moduleTypePlanCreationServiceBlockingStubMap).hasSize(3);
    assertThat(moduleTypePlanCreationServiceBlockingStubMap)
        .containsOnlyKeys(ModuleType.CD, ModuleType.CI, ModuleType.PMS);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetServiceManager() {
    Set<BindableService> bindableServices = pipelineServiceGrpcModule.bindableServices(healthStatusManager,
        pmsSdkInstanceService, pmsExecutionGrpcService, sweepingOutputService, outcomeServiceGrpcServer,
        engineExpressionGrpcService, interruptGrpcService, entityReferenceGrpcService, variablesService);
    Service service = pipelineServiceGrpcModule.pmsGrpcInternalService(
        healthStatusManager, bindableServices, Sets.newHashSet(new PipelineServiceGrpcErrorHandler()));
    ServiceManager serviceManager = pipelineServiceGrpcModule.serviceManager(Sets.newHashSet(service));
    assertThat(serviceManager).isNotNull();
  }

  @Test
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void testShouldUsePlainTextNegotiationType_onPremDeployMode_returnsTrue() {
    assertTrue(pipelineServiceGrpcModule.shouldUsePlainTextNegotiationType(NegotiationType.TLS, "ONPREM"));
  }

  @Test
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void testShouldUsePlainTextNegotiationType_onPremKubDeployMode_returnsTrue() {
    assertTrue(pipelineServiceGrpcModule.shouldUsePlainTextNegotiationType(NegotiationType.TLS, "KUBERNETES_ONPREM"));
  }

  @Test
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void testShouldUsePlainTextNegotiationType_plainTextNegotiation_returnsTrue() {
    assertTrue(pipelineServiceGrpcModule.shouldUsePlainTextNegotiationType(NegotiationType.PLAINTEXT, "xyz"));
  }

  @Test
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void testShouldUsePlainTextNegotiationType_tlsNegotiationAndNonOnPrem_returnsFalse() {
    assertFalse(pipelineServiceGrpcModule.shouldUsePlainTextNegotiationType(NegotiationType.TLS, "xyz"));
  }
}
