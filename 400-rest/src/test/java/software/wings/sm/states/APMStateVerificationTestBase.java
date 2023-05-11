/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.cv.api.WorkflowVerificationResultService;
import io.harness.ff.FeatureFlagService;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.WingsBaseTest;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.app.MainConfiguration;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.security.AppPermissionSummary;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserPermissionInfo;
import software.wings.service.impl.analysis.ContinuousVerificationService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionBaselineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.analysis.AnalysisService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;

/**
 * Created by Praveen on 5/31/2018
 */
public class APMStateVerificationTestBase extends WingsBaseTest {
  protected String accountId;
  protected String appId;
  protected String stateExecutionId;
  protected String workflowId;
  protected String workflowExecutionId;
  protected String serviceId;
  protected User user;

  @Mock protected ExecutionContextImpl executionContext;
  @Mock protected AuthService mockAuthService;
  @Mock protected BroadcasterFactory broadcasterFactory;
  @Mock protected WorkflowStandardParams workflowStandardParams;

  @Inject protected WingsPersistence wingsPersistence;
  @Inject protected AppService appService;
  @Inject protected SettingsService settingsService;
  @Inject protected WaitNotifyEngine waitNotifyEngine;
  @Inject protected DelegateService delegateService;
  @Inject protected MainConfiguration configuration;
  @Inject protected SecretManager secretManager;
  @Inject protected ContinuousVerificationService continuousVerificationService;
  @Inject protected WorkflowExecutionBaselineService workflowExecutionBaselineService;
  @Inject protected FeatureFlagService featureFlagService;
  @Inject protected TemplateExpressionProcessor templateExpressionProcessor;

  @Inject protected WorkflowExecutionService workflowExecutionService;
  @Inject protected WorkflowVerificationResultService workflowVerificationResultService;

  @Mock protected PhaseElement phaseElement;
  @Mock protected Environment environment;
  @Mock protected Application application;
  @Mock protected Artifact artifact;
  @Mock protected StateExecutionInstance stateExecutionInstance;
  @Mock private UserPermissionInfo mockUserPermissionInfo;
  @Inject protected VersionInfoManager versionInfoManager;
  @Mock protected CVActivityLogService cvActivityLogService;
  @Inject protected AnalysisService analysisService;

  protected void setupCommon() {
    accountId = UUID.randomUUID().toString();
    appId = UUID.randomUUID().toString();
    stateExecutionId = UUID.randomUUID().toString();
    workflowId = UUID.randomUUID().toString();
    workflowExecutionId = UUID.randomUUID().toString();
    serviceId = UUID.randomUUID().toString();
    user = new User();

    wingsPersistence.save(Application.Builder.anApplication().uuid(appId).accountId(accountId).build());
    wingsPersistence.save(WorkflowExecution.builder()
                              .appId(appId)
                              .workflowId(workflowId)
                              .uuid(workflowExecutionId)
                              .startTs(1519200000000L)
                              .name("dummy workflow")
                              .status(ExecutionStatus.NEW)
                              .build());
    configuration.getPortal().setJwtExternalServiceSecret(accountId);
  }

  protected void setupCommonMocks() throws IllegalAccessException {
    when(executionContext.getAppId()).thenReturn(appId);
    when(executionContext.getWorkflowExecutionId()).thenReturn(workflowExecutionId);
    when(executionContext.getStateExecutionInstanceId()).thenReturn(stateExecutionId);
    when(executionContext.getWorkflowExecutionName()).thenReturn("dummy workflow");
    when(executionContext.renderExpression(anyString())).then(returnsFirstArg());
    when(executionContext.getWorkflowId()).thenReturn(workflowId);
    when(phaseElement.getServiceElement()).thenReturn(ServiceElement.builder().name("dummy").uuid("1").build());
    when(executionContext.getContextElement(ContextElementType.PARAM, ExecutionContextImpl.PHASE_PARAM))
        .thenReturn(phaseElement);
    when(environment.getName()).thenReturn("dummy env");
    when(executionContext.getEnv()).thenReturn(environment);
    when(application.getName()).thenReturn("dummuy app");
    when(executionContext.getApp()).thenReturn(application);
    when(artifact.getDisplayName()).thenReturn("dummy artifact");
    when(executionContext.getArtifactForService(anyString())).thenReturn(artifact);
    when(stateExecutionInstance.getStartTs()).thenReturn(1519200000000L);
    when(executionContext.getStateExecutionInstance()).thenReturn(stateExecutionInstance);

    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcaster.broadcast(any())).thenReturn(null);
    when(broadcasterFactory.lookup(any(), anyBoolean())).thenReturn(broadcaster);
    FieldUtils.writeField(delegateService, "broadcasterFactory", broadcasterFactory, true);
    FieldUtils.writeField(continuousVerificationService, "authService", mockAuthService, true);
    // Setup authService for continuousVerificationService
    when(mockUserPermissionInfo.getAppPermissionMapInternal()).thenReturn(new HashMap<String, AppPermissionSummary>() {
      { put(appId, buildAppPermissionSummary()); }
    });

    when(mockAuthService.getUserPermissionInfo(accountId, user, false)).thenReturn(mockUserPermissionInfo);
  }

  private AppPermissionSummary buildAppPermissionSummary() {
    Map<Action, Set<String>> servicePermissions = new HashMap<Action, Set<String>>() {
      { put(Action.READ, Sets.newHashSet(serviceId)); }
    };
    Map<Action, Set<String>> envPermissions = new HashMap<Action, Set<String>>() {
      { put(Action.READ, Sets.newHashSet()); }
    };
    Map<Action, Set<String>> pipelinePermissions = new HashMap<Action, Set<String>>() {
      { put(Action.READ, Sets.newHashSet()); }
    };
    Map<Action, Set<String>> workflowPermissions = new HashMap<Action, Set<String>>() {
      { put(Action.READ, Sets.newHashSet(workflowId)); }
    };

    return AppPermissionSummary.builder()
        .envPermissions(null)
        .servicePermissions(servicePermissions)
        //        .envPermissions(envPermissions)
        .workflowPermissions(workflowPermissions)
        .pipelinePermissions(pipelinePermissions)
        .build();
  }

  protected void setupCommonFields(AbstractAnalysisState state) throws IllegalAccessException {
    FieldUtils.writeField(state, "appService", this.appService, true);
    FieldUtils.writeField(state, "configuration", configuration, true);
    FieldUtils.writeField(state, "settingsService", settingsService, true);
    FieldUtils.writeField(state, "waitNotifyEngine", waitNotifyEngine, true);
    FieldUtils.writeField(state, "delegateService", delegateService, true);
    FieldUtils.writeField(state, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(state, "secretManager", secretManager, true);
    FieldUtils.writeField(state, "workflowExecutionService", workflowExecutionService, true);
    FieldUtils.writeField(state, "continuousVerificationService", continuousVerificationService, true);
    FieldUtils.writeField(state, "workflowExecutionBaselineService", workflowExecutionBaselineService, true);
    FieldUtils.writeField(state, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(state, "versionInfoManager", versionInfoManager, true);
    FieldUtils.writeField(state, "cvActivityLogService", cvActivityLogService, true);
  }

  protected void setupCvActivityLogService(AbstractAnalysisState state) throws IllegalAccessException {
    FieldUtils.writeField(state, "cvActivityLogService", cvActivityLogService, true);
    PowerMockito.when(cvActivityLogService.getLoggerByStateExecutionId(any(), any()))
        .thenReturn(mock(CVActivityLogger.class));
  }
}
