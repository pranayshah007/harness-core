/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow;

import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.event.handler.impl.Utils;
import io.harness.event.handler.impl.segment.SegmentHandler;
import io.harness.event.handler.impl.segment.SegmentHelper;
import io.harness.event.handler.segment.SegmentConfig;
import io.harness.event.listener.EventListener;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.event.usagemetrics.UsageMetricsHelper;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.GeneralNotifyEventListener;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.NameValuePair;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionBuilder;
import software.wings.dl.WingsPersistence;
import software.wings.events.TestUtils;
import software.wings.rules.Listeners;
import software.wings.service.impl.WorkflowExecutionUpdate;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionContextImpl;

import com.google.inject.Inject;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * @author rktummala
 */
@Slf4j
@OwnedBy(HarnessTeam.CDC)
@Listeners(GeneralNotifyEventListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkflowExecutionUpdateTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private PersistentLocker persistentLocker;
  @Inject private TestUtils testUtils;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private ExecutionContextImpl context;
  @Mock AppService appService;
  @Mock HarnessTagService harnessTagService;
  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock UsageMetricsHelper usageMetricsHelper;
  @Mock UsageMetricsEventPublisher usageMetricsEventPublisher;
  @Inject WingsPersistence wingsPersistence;

  @InjectMocks @Inject private WorkflowExecutionUpdate workflowExecutionUpdate;

  private SegmentHandler segmentHandler;
  private MainConfiguration mainConfiguration;
  private SegmentHelper segmentHelper;
  private UserService userService = mock(UserService.class);
  private Utils utils = spy(new Utils());

  @Before
  public void setup() throws IllegalAccessException {
    SegmentConfig segmentConfig =
        SegmentConfig.builder().enabled(true).url("https://api.segment.io").apiKey("dummy_api_key").build();
    EventListener eventListener = mock(EventListener.class);
    segmentHandler = Mockito.spy(new SegmentHandler(segmentConfig, eventListener));
    mainConfiguration = mock(MainConfiguration.class);

    segmentHelper = new SegmentHelper(mainConfiguration);

    FieldUtils.writeField(utils, "userService", userService, true);
    FieldUtils.writeField(segmentHandler, "segmentHelper", segmentHelper, true);
    FieldUtils.writeField(segmentHandler, "utils", utils, true);
    FieldUtils.writeField(segmentHandler, "userService", userService, true);
    FieldUtils.writeField(segmentHandler, "persistentLocker", persistentLocker, true);
    FieldUtils.writeField(workflowExecutionUpdate, "segmentHandler", segmentHandler, true);
    FieldUtils.writeField(segmentHandler, "segmentHelper", segmentHelper, true);
    FieldUtils.writeField(workflowExecutionUpdate, "segmentHandler", segmentHandler, true);
  }

  private WorkflowExecution createWorkflowExecution(User triggeredBy) {
    WorkflowExecutionBuilder workflowExecutionBuilder = WorkflowExecution.builder()
                                                            .accountId(ACCOUNT_ID)
                                                            .appId(APP_ID)
                                                            .appName(APP_NAME)
                                                            .envType(NON_PROD)
                                                            .status(ExecutionStatus.EXPIRED)
                                                            .workflowType(WorkflowType.ORCHESTRATION)
                                                            .uuid(generateUuid());
    if (triggeredBy != null) {
      workflowExecutionBuilder.triggeredBy(EmbeddedUser.builder()
                                               .uuid(triggeredBy.getUuid())
                                               .email(triggeredBy.getEmail())
                                               .name(triggeredBy.getName())
                                               .build());
    }
    return workflowExecutionBuilder.build();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldReportDeploymentEventToSegmentWithNullWorkflowTypeAndNullServiceIds() throws URISyntaxException {
    Account account = testUtils.createAccount();
    when(accountService.getFromCacheWithFallback(anyString())).thenReturn(account);
    WorkflowExecution workflowExecution = createWorkflowExecution(null);
    workflowExecution.setServiceIds(Arrays.asList(generateUuid()));
    workflowExecutionUpdate.reportDeploymentEventToSegment(workflowExecution);
    verify(segmentHandler).reportTrackEvent(eq(account), anyString(), nullable(String.class), anyMap(), anyMap());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldReportDeploymentEventToSegmentWithNullServiceIds() throws URISyntaxException {
    Account account = testUtils.createAccount();
    when(accountService.getFromCacheWithFallback(anyString())).thenReturn(account);
    WorkflowExecution workflowExecution = createWorkflowExecution(null);
    workflowExecution.setWorkflowType(null);
    workflowExecution.setServiceIds(null);
    workflowExecutionUpdate.reportDeploymentEventToSegment(workflowExecution);
    verify(segmentHandler).reportTrackEvent(eq(account), anyString(), nullable(String.class), anyMap(), anyMap());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldReportDeploymentEventToSegmentWithNullWorkflowType() throws URISyntaxException {
    Account account = testUtils.createAccount();
    when(accountService.getFromCacheWithFallback(anyString())).thenReturn(account);
    WorkflowExecution workflowExecution = createWorkflowExecution(null);
    workflowExecution.setWorkflowType(null);
    workflowExecutionUpdate.reportDeploymentEventToSegment(workflowExecution);
    verify(segmentHandler).reportTrackEvent(eq(account), anyString(), nullable(String.class), anyMap(), anyMap());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldReportDeploymentEventToSegmentByTrigger() throws URISyntaxException {
    Account account = testUtils.createAccount();
    when(accountService.getFromCacheWithFallback(anyString())).thenReturn(account);
    WorkflowExecution workflowExecution = createWorkflowExecution(null);
    workflowExecutionUpdate.reportDeploymentEventToSegment(workflowExecution);
    verify(segmentHandler).reportTrackEvent(eq(account), anyString(), nullable(String.class), anyMap(), anyMap());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldReportDeploymentEventToSegmentByUser() throws IllegalAccessException, URISyntaxException {
    Account account = testUtils.createAccount();
    User user = testUtils.createUser(account);
    user.setSegmentIdentity(UUIDGenerator.generateUuid());

    when(userService.get(anyString())).thenReturn(user);
    when(userService.getUserFromCacheOrDB(anyString())).thenReturn(user);
    when(userService.update(any(User.class))).thenReturn(user);

    when(accountService.getFromCacheWithFallback(anyString())).thenReturn(account);
    AcquiredLock acquiredLock = mock(AcquiredLock.class);
    when(persistentLocker.waitToAcquireLock(anyString(), any(Duration.class), any(Duration.class)))
        .thenReturn(acquiredLock);
    WorkflowExecution workflowExecution = createWorkflowExecution(user);
    workflowExecutionUpdate.reportDeploymentEventToSegment(workflowExecution);
    verify(segmentHandler).reportTrackEvent(eq(account), anyString(), anyString(), anyMap(), anyMap());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldEvaluateAllTags() {
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);
    List<HarnessTagLink> harnessTagLinkList = new ArrayList<>();
    harnessTagLinkList.add(constructHarnessTagLink("foo", ""));
    harnessTagLinkList.add(constructHarnessTagLink("env", "${workflow.variables.env}"));
    harnessTagLinkList.add(constructHarnessTagLink("company", "foobar"));
    harnessTagLinkList.add(constructHarnessTagLink("${account.defaults.owner}", ""));
    when(harnessTagService.getTagLinksWithEntityId(anyString(), anyString())).thenReturn(harnessTagLinkList);
    when(context.renderExpression(eq("foo"))).thenReturn("foo");
    when(context.renderExpression(eq(""))).thenReturn("");
    when(context.renderExpression(eq("env"))).thenReturn("env");
    when(context.renderExpression(eq("${workflow.variables.env}"))).thenReturn("dev");
    when(context.renderExpression(eq("company"))).thenReturn("company");
    when(context.renderExpression(eq("foobar"))).thenReturn("foobar");
    when(context.renderExpression(eq("${account.defaults.owner}"))).thenReturn("user1");
    List<NameValuePair> tags = workflowExecutionUpdate.resolveDeploymentTags(context, WORKFLOW_ID);
    assertThat(tags).isNotEmpty();
    assertThat(tags.size()).isEqualTo(4);
    assertThat(tags)
        .extracting(NameValuePair::getName, NameValuePair::getValue)
        .containsExactly(tuple("foo", ""), tuple("env", "dev"), tuple("company", "foobar"), tuple("user1", ""));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotEvaluateAllTags() {
    when(appService.getAccountIdByAppId(anyString())).thenReturn(ACCOUNT_ID);
    List<HarnessTagLink> harnessTagLinkList = new ArrayList<>();
    harnessTagLinkList.add(constructHarnessTagLink("foo", ""));
    harnessTagLinkList.add(constructHarnessTagLink("env", "${workflow.variables.env}"));
    harnessTagLinkList.add(constructHarnessTagLink("company", "foobar"));
    harnessTagLinkList.add(constructHarnessTagLink("${account.defaults.owner}", ""));
    harnessTagLinkList.add(constructHarnessTagLink("${workflow.variables.mytag}", ""));
    harnessTagLinkList.add(constructHarnessTagLink("${workflow.variables.mytag2}", ""));
    harnessTagLinkList.add(constructHarnessTagLink("${app.defaults.RUNTIME_PATH}", ""));
    harnessTagLinkList.add(constructHarnessTagLink("${mytag}", ""));
    harnessTagLinkList.add(constructHarnessTagLink("${mytag1}", ""));
    when(harnessTagService.getTagLinksWithEntityId(any(), any())).thenReturn(harnessTagLinkList);
    when(context.renderExpression(eq("foo"))).thenReturn("foo");
    when(context.renderExpression(eq(""))).thenReturn("");
    when(context.renderExpression(eq("env"))).thenReturn("env");
    when(context.renderExpression(eq("${workflow.variables.env}"))).thenReturn("${workflow.variables.env}");
    when(context.renderExpression(eq("company"))).thenReturn("company");
    when(context.renderExpression(eq("foobar"))).thenReturn("foobar");
    when(context.renderExpression(eq("${account.defaults.owner}"))).thenReturn("${account.defaults.owner}");
    when(context.renderExpression(eq("${workflow.variables.mytag}"))).thenReturn("");
    when(context.renderExpression(eq("${workflow.variables.mytag2}"))).thenReturn(null);
    when(context.renderExpression(eq("${app.defaults.RUNTIME_PATH}")))
        .thenReturn("$HOME/my app/${service.name}/${env.name}/runtime");
    when(context.renderExpression(eq("${mytag}"))).thenReturn("$HOME/my app/");
    when(context.renderExpression(eq("${mytag1}"))).thenReturn("${HOME}");
    List<NameValuePair> tags = workflowExecutionUpdate.resolveDeploymentTags(context, WORKFLOW_ID);
    assertThat(tags).isNotEmpty();
    assertThat(tags.size()).isEqualTo(4);
    assertThat(tags)
        .extracting(NameValuePair::getName, NameValuePair::getValue)
        .containsExactly(
            tuple("foo", ""), tuple("env", "unresolved"), tuple("company", "foobar"), tuple("$HOME/my app/", ""));
  }

  private HarnessTagLink constructHarnessTagLink(String key, String value) {
    return HarnessTagLink.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .key(key)
        .value(value)
        .entityType(EntityType.WORKFLOW)
        .entityId(WORKFLOW_ID)
        .build();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldAddTagsToWorkflowExecution() {
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .appName(APP_NAME)
                                              .envType(NON_PROD)
                                              .status(ExecutionStatus.SUCCESS)
                                              .workflowType(WorkflowType.ORCHESTRATION)
                                              .uuid(WORKFLOW_EXECUTION_ID)
                                              .build();
    wingsPersistence.save(workflowExecution);
    List<NameValuePair> resolvedTags = new ArrayList<>();
    resolvedTags.add(NameValuePair.builder().name("foo").value("").build());
    resolvedTags.add(NameValuePair.builder().name("env").value("QA").build());
    on(workflowExecutionUpdate).set("appId", APP_ID);
    on(workflowExecutionUpdate).set("workflowExecutionId", WORKFLOW_EXECUTION_ID);
    workflowExecutionUpdate.addTagsToWorkflowExecution(resolvedTags);
    WorkflowExecution updated = wingsPersistence.get(WorkflowExecution.class, WORKFLOW_EXECUTION_ID);
    assertThat(updated.getTags().size()).isEqualTo(2);
    assertThat(updated.getTags())
        .extracting(NameValuePair::getName, NameValuePair::getValue)
        .containsExactly(tuple("foo", ""), tuple("env", "QA"));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_publishEvent() {
    Application app = Application.Builder.anApplication().build();
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .accountId("accountId")
                                              .appId("appId")
                                              .uuid("uuid")
                                              .serviceIds(Collections.singletonList("serviceId"))
                                              .build();
    WorkflowExecution updatedWorkflowExecution = WorkflowExecution.builder()
                                                     .accountId("accountId")
                                                     .appId("appId")
                                                     .uuid("uuid")
                                                     .serviceIds(Collections.singletonList("serviceId"))
                                                     .deployedServices(Collections.singletonList("serviceId"))
                                                     .build();
    when(usageMetricsHelper.getApplication("appId")).thenReturn(app);
    when(workflowExecutionService.getUpdatedWorkflowExecution("appId", "uuid")).thenReturn(updatedWorkflowExecution);
    doNothing()
        .when(usageMetricsEventPublisher)
        .publishDeploymentTimeSeriesEvent("accountId", updatedWorkflowExecution, Collections.emptyMap());
    workflowExecutionUpdate.publish(workflowExecution);
    ArgumentCaptor<WorkflowExecution> captor = ArgumentCaptor.forClass(WorkflowExecution.class);
    verify(usageMetricsEventPublisher, times(1)).publishDeploymentTimeSeriesEvent(any(), captor.capture(), anyMap());
    assertThat(captor.getValue()).isEqualTo(updatedWorkflowExecution);
  }
}
