package software.wings.service.impl;

import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateInstanceStatus.ENABLED;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.task.TaskFailureReason.EXPIRED;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARPIT;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.SANJA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.service.impl.AssignDelegateServiceImpl.BLACKLIST_TTL;
import static software.wings.service.impl.AssignDelegateServiceImpl.ERROR_MESSAGE;
import static software.wings.service.impl.AssignDelegateServiceImpl.MAX_DELEGATE_LAST_HEARTBEAT;
import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;
import static software.wings.service.impl.AssignDelegateServiceImpl.WHITELIST_TTL;
import static software.wings.service.impl.AssignDelegateServiceImplTest.CriteriaType.MATCHING_CRITERIA;
import static software.wings.service.impl.AssignDelegateServiceImplTest.CriteriaType.NOT_MATCHING_CRITERIA;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskBuilder;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateActivity;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfileScopingRule;
import io.harness.delegate.beans.DelegateScope;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.selection.log.BatchDelegateSelectionLog;
import io.harness.service.intfc.DelegateCache;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.validation.DelegateConnectionResult;
import software.wings.delegatetasks.validation.DelegateConnectionResult.DelegateConnectionResultBuilder;
import software.wings.delegatetasks.validation.DelegateConnectionResult.DelegateConnectionResultKeys;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;

import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(HarnessTeam.DEL)
@BreakDependencyOn("software.wings.WingsBaseTest")
@BreakDependencyOn("software.wings.beans.AwsAmiInfrastructureMapping")
@BreakDependencyOn("software.wings.beans.Environment")
@BreakDependencyOn("software.wings.beans.InfrastructureMapping")
@BreakDependencyOn("software.wings.beans.InfrastructureMappingType")
@BreakDependencyOn("software.wings.service.intfc.InfrastructureMappingService")
@BreakDependencyOn("software.wings.beans.GcpKubernetesInfrastructureMapping")
@BreakDependencyOn("software.wings.service.intfc.EnvironmentService")
public class AssignDelegateServiceImplTest extends WingsBaseTest {
  @Mock private EnvironmentService environmentService;
  @Mock private DelegateService delegateService;
  @Mock private DelegateCache delegateCache;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private DelegateSelectionLogsService delegateSelectionLogsService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock
  private LoadingCache<ImmutablePair<String, String>, Optional<DelegateConnectionResult>> delegateConnectionResultCache;
  @Mock private LoadingCache<String, List<Delegate>> accountDelegatesCache;

  @Inject @InjectMocks private AssignDelegateServiceImpl assignDelegateService;

  @Inject private HPersistence persistence;
  @Inject private Clock clock;

  private static final String WRONG_INFRA_MAPPING_ID = "WRONG_INFRA_MAPPING_ID";

  private static final String VERSION = "1.0.0";

  @Before
  public void setUp() throws IllegalAccessException, ExecutionException {
    Environment environment = anEnvironment().uuid("ENV_ID").appId("APP_ID").environmentType(PROD).build();
    when(environmentService.get("APP_ID", "ENV_ID", false)).thenReturn(environment);
    when(delegateConnectionResultCache.get(any(ImmutablePair.class))).thenReturn(Optional.empty());
    when(accountDelegatesCache.get(anyString())).thenReturn(Collections.emptyList());
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder().status(DelegateInstanceStatus.ENABLED).lastHeartBeat(System.currentTimeMillis());
  }

  @Value
  @Builder
  public static class DelegateScopeTestData {
    List<DelegateScope> excludeScopes;
    List<DelegateScope> includeScopes;
    boolean assignable;
    int numOfNoIncludeScopeMatchedInvocations;
    int numOfExcludeScopeMatchedInvocations;
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testAssignByDelegateScopes() {
    List<DelegateScopeTestData> tests =
        ImmutableList.<DelegateScopeTestData>builder()
            .add(DelegateScopeTestData.builder()
                     .excludeScopes(emptyList())
                     .includeScopes(emptyList())
                     .assignable(true)
                     .numOfNoIncludeScopeMatchedInvocations(0)
                     .numOfExcludeScopeMatchedInvocations(0)
                     .build())
            .add(DelegateScopeTestData.builder()
                     .excludeScopes(emptyList())
                     .includeScopes(
                         ImmutableList.of(DelegateScope.builder().environmentTypes(ImmutableList.of(PROD)).build()))
                     .assignable(true)
                     .numOfNoIncludeScopeMatchedInvocations(0)
                     .numOfExcludeScopeMatchedInvocations(0)
                     .build())
            .add(DelegateScopeTestData.builder()
                     .excludeScopes(
                         ImmutableList.of(DelegateScope.builder().environmentTypes(ImmutableList.of(NON_PROD)).build()))
                     .includeScopes(
                         ImmutableList.of(DelegateScope.builder().environmentTypes(ImmutableList.of(PROD)).build()))
                     .assignable(true)
                     .numOfNoIncludeScopeMatchedInvocations(0)
                     .numOfExcludeScopeMatchedInvocations(0)
                     .build())
            .add(DelegateScopeTestData.builder()
                     .excludeScopes(
                         ImmutableList.of(DelegateScope.builder().environmentTypes(ImmutableList.of(PROD)).build()))
                     .includeScopes(
                         ImmutableList.of(DelegateScope.builder().environmentTypes(ImmutableList.of(PROD)).build()))
                     .assignable(false)
                     .numOfNoIncludeScopeMatchedInvocations(0)
                     .numOfExcludeScopeMatchedInvocations(1)
                     .build())
            .add(DelegateScopeTestData.builder()
                     .excludeScopes(
                         ImmutableList.of(DelegateScope.builder().environmentTypes(ImmutableList.of(NON_PROD)).build()))
                     .includeScopes(emptyList())
                     .assignable(true)
                     .numOfNoIncludeScopeMatchedInvocations(0)
                     .numOfExcludeScopeMatchedInvocations(1)
                     .build())
            .add(DelegateScopeTestData.builder()
                     .excludeScopes(
                         ImmutableList.of(DelegateScope.builder().environmentTypes(ImmutableList.of(PROD)).build()))
                     .includeScopes(emptyList())
                     .assignable(false)
                     .numOfNoIncludeScopeMatchedInvocations(0)
                     .numOfExcludeScopeMatchedInvocations(2)
                     .build())
            .add(DelegateScopeTestData.builder()
                     .excludeScopes(emptyList())
                     .includeScopes(
                         ImmutableList.of(DelegateScope.builder().environmentTypes(ImmutableList.of(NON_PROD)).build()))
                     .assignable(false)
                     .numOfNoIncludeScopeMatchedInvocations(1)
                     .numOfExcludeScopeMatchedInvocations(2)
                     .build())
            .build();

    DelegateTaskBuilder delegateTaskBuilder = DelegateTask.builder()
                                                  .accountId("ACCOUNT_ID")
                                                  .setupAbstraction("appId", "APP_ID")
                                                  .setupAbstraction("envId", "ENV_ID")
                                                  .data(TaskData.builder()
                                                            .async(true)
                                                            .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                                            .taskType(TaskType.HTTP.name())
                                                            .build());

    DelegateBuilder delegateBuilder = Delegate.builder()
                                          .accountId("ACCOUNT_ID")
                                          .uuid("DELEGATE_ID")
                                          .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()));

    for (DelegateScopeTestData test : tests) {
      Delegate delegate =
          delegateBuilder.includeScopes(test.getIncludeScopes()).excludeScopes(test.getExcludeScopes()).build();
      when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);

      BatchDelegateSelectionLog batch =
          BatchDelegateSelectionLog.builder().taskId(delegateTaskBuilder.build().getUuid()).build();
      assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTaskBuilder.build()))
          .isEqualTo(test.isAssignable());

      verify(delegateSelectionLogsService, Mockito.times(test.getNumOfNoIncludeScopeMatchedInvocations()))
          .logNoIncludeScopeMatched(eq(batch), anyString(), anyString());
      verify(delegateSelectionLogsService, Mockito.times(test.getNumOfExcludeScopeMatchedInvocations()))
          .logExcludeScopeMatched(eq(batch), anyString(), anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testAssignByDelegateIncludeScopesWithWildcard() {
    DelegateTaskBuilder delegateTaskBuilder = DelegateTask.builder()
                                                  .accountId("ACCOUNT_ID")
                                                  .setupAbstraction("appId", SCOPE_WILDCARD)
                                                  .setupAbstraction("envId", "ENV_ID")
                                                  .data(TaskData.builder()
                                                            .async(true)
                                                            .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                                            .taskType(TaskType.HTTP.name())
                                                            .build());

    DelegateBuilder delegateBuilder = Delegate.builder()
                                          .accountId("ACCOUNT_ID")
                                          .uuid("DELEGATE_ID")
                                          .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()));

    Delegate delegate = delegateBuilder
                            .includeScopes(ImmutableList.of(
                                DelegateScope.builder().applications(ImmutableList.of("APPLICATION_ID")).build()))
                            .build();
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);

    BatchDelegateSelectionLog batch =
        BatchDelegateSelectionLog.builder().taskId(delegateTaskBuilder.build().getUuid()).build();
    assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTaskBuilder.build())).isEqualTo(true);

    delegate = delegateBuilder
                   .includeScopes(ImmutableList.of(
                       DelegateScope.builder().environments(ImmutableList.of("ENVIRONMENT_ID")).build()))
                   .build();
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);

    delegateTaskBuilder.setupAbstraction("envId", SCOPE_WILDCARD);

    batch = BatchDelegateSelectionLog.builder().taskId(delegateTaskBuilder.build().getUuid()).build();
    assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTaskBuilder.build())).isEqualTo(true);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testAssignByDelegateExcludeScopesWithWildcard() {
    DelegateTaskBuilder delegateTaskBuilder = DelegateTask.builder()
                                                  .accountId("ACCOUNT_ID")
                                                  .setupAbstraction("appId", SCOPE_WILDCARD)
                                                  .setupAbstraction("envId", "ENV_ID")
                                                  .data(TaskData.builder()
                                                            .async(true)
                                                            .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                                            .taskType(TaskType.HTTP.name())
                                                            .build());

    DelegateBuilder delegateBuilder = Delegate.builder()
                                          .accountId("ACCOUNT_ID")
                                          .uuid("DELEGATE_ID")
                                          .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()));

    Delegate delegate = delegateBuilder
                            .excludeScopes(ImmutableList.of(
                                DelegateScope.builder().applications(ImmutableList.of("APPLICATION_ID")).build()))
                            .build();
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);

    BatchDelegateSelectionLog batch =
        BatchDelegateSelectionLog.builder().taskId(delegateTaskBuilder.build().getUuid()).build();
    assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTaskBuilder.build())).isEqualTo(true);

    delegate = delegateBuilder
                   .excludeScopes(ImmutableList.of(
                       DelegateScope.builder().environments(ImmutableList.of("ENVIRONMENT_ID")).build()))
                   .build();
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);

    delegateTaskBuilder.setupAbstraction("enviId", SCOPE_WILDCARD);

    batch = BatchDelegateSelectionLog.builder().taskId(delegateTaskBuilder.build().getUuid()).build();
    assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTaskBuilder.build())).isEqualTo(true);
  }

  @Value
  @Builder
  public static class DelegateProfileScopeTestData {
    Delegate delegate;
    DelegateTask task;
    List<DelegateProfileScopingRule> scopingRules;
    boolean assignable;
    int numOfProfileScopeNotMatchedInvocations;
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testAssignByDelegateProfileScopes() {
    String accountId = generateUuid();

    Map<String, Set<String>> scopingRulesMap1 = new HashMap<>();
    scopingRulesMap1.put("k1", new HashSet<>(singletonList("yy")));

    Map<String, Set<String>> scopingRulesMap2 = new HashMap<>();
    scopingRulesMap2.put("k2", new HashSet<>(singletonList("yy")));

    Map<String, Set<String>> scopingRulesMap3 = new HashMap<>();
    scopingRulesMap3.put("k1", new HashSet<>(asList("v11", "v12", "v13")));
    scopingRulesMap3.put("k2", new HashSet<>(asList("v21", "v22")));
    scopingRulesMap3.put("k3", new HashSet<>(singletonList("v31")));

    Map<String, Set<String>> scopingRulesMap4 = new HashMap<>();
    scopingRulesMap4.put("k1", emptySet());

    // Test for temporary workaround, until all tasks start sending envType and serviceId
    Map<String, Set<String>> scopingRulesWorkaroundMap5 = new HashMap<>();
    scopingRulesWorkaroundMap5.put("envType", new HashSet<>(singletonList(PROD.name())));
    scopingRulesWorkaroundMap5.put("serviceId", new HashSet<>(singletonList("s1")));

    Environment env = new Environment();
    env.setName("test environment");
    env.setEnvironmentType(PROD);
    String envId = persistence.save(env);

    AwsAmiInfrastructureMapping infrastructureMapping = new AwsAmiInfrastructureMapping();
    infrastructureMapping.setAccountId(generateUuid());
    infrastructureMapping.setInfraMappingType(generateUuid());
    infrastructureMapping.setComputeProviderType(generateUuid());
    infrastructureMapping.setComputeProviderSettingId(generateUuid());
    infrastructureMapping.setEnvId(generateUuid());
    infrastructureMapping.setDeploymentType(generateUuid());
    infrastructureMapping.setServiceId("s1");
    String infraMappingId = persistence.save(infrastructureMapping);
    // End of temporary workaround

    List<DelegateProfileScopeTestData> tests =
        ImmutableList.<DelegateProfileScopeTestData>builder()
            .add(DelegateProfileScopeTestData.builder()
                     .delegate(Delegate.builder()
                                   .accountId(accountId)
                                   .uuid(generateUuid())
                                   .delegateProfileId(generateUuid())
                                   .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                   .build())
                     .task(DelegateTask.builder()
                               .uuid(generateUuid())
                               .accountId(accountId)
                               .setupAbstraction("envId", envId)
                               .setupAbstraction("infrastructureMappingId", infraMappingId)
                               .data(TaskData.builder()
                                         .taskType(TaskType.HTTP.name())
                                         .async(true)
                                         .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                         .build())
                               .build())
                     .scopingRules(singletonList(DelegateProfileScopingRule.builder()
                                                     .description("rule1")
                                                     .scopingEntities(scopingRulesWorkaroundMap5)
                                                     .build()))
                     .assignable(true)
                     .numOfProfileScopeNotMatchedInvocations(0)
                     .build())
            .add(DelegateProfileScopeTestData.builder()
                     .delegate(Delegate.builder()
                                   .accountId(accountId)
                                   .uuid(generateUuid())
                                   .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                   .build())
                     .task(DelegateTask.builder()
                               .uuid(generateUuid())
                               .accountId(accountId)
                               .data(TaskData.builder()
                                         .taskType(TaskType.HTTP.name())
                                         .async(true)
                                         .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                         .build())
                               .build())
                     .scopingRules(null)
                     .assignable(true)
                     .numOfProfileScopeNotMatchedInvocations(0)
                     .build())
            .add(DelegateProfileScopeTestData.builder()
                     .delegate(Delegate.builder()
                                   .accountId(accountId)
                                   .uuid(generateUuid())
                                   .delegateProfileId(generateUuid())
                                   .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                   .build())
                     .task(DelegateTask.builder()
                               .uuid(generateUuid())
                               .accountId(accountId)
                               .setupAbstraction("k1", "v1")
                               .data(TaskData.builder()
                                         .taskType(TaskType.HTTP.name())
                                         .async(true)
                                         .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                         .build())
                               .build())
                     .scopingRules(null)
                     .assignable(true)
                     .numOfProfileScopeNotMatchedInvocations(0)
                     .build())
            .add(DelegateProfileScopeTestData.builder()
                     .delegate(Delegate.builder()
                                   .accountId(accountId)
                                   .uuid(generateUuid())
                                   .delegateProfileId(generateUuid())
                                   .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                   .build())
                     .task(DelegateTask.builder()
                               .uuid(generateUuid())
                               .accountId(accountId)
                               .data(TaskData.builder()
                                         .taskType(TaskType.HTTP.name())
                                         .async(true)
                                         .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                         .build())
                               .build())
                     .scopingRules(singletonList(DelegateProfileScopingRule.builder()
                                                     .description("rule1")
                                                     .scopingEntities(scopingRulesMap1)
                                                     .build()))
                     .assignable(false)
                     .numOfProfileScopeNotMatchedInvocations(0)
                     .build())
            .add(DelegateProfileScopeTestData.builder()
                     .delegate(Delegate.builder()
                                   .accountId(accountId)
                                   .uuid(generateUuid())
                                   .delegateProfileId(generateUuid())
                                   .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                   .build())
                     .task(DelegateTask.builder()
                               .uuid(generateUuid())
                               .accountId(accountId)
                               .setupAbstraction("k1", "v1")
                               .data(TaskData.builder()
                                         .taskType(TaskType.HTTP.name())
                                         .async(true)
                                         .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                         .build())
                               .build())
                     .scopingRules(singletonList(DelegateProfileScopingRule.builder()
                                                     .description("rule1")
                                                     .scopingEntities(scopingRulesMap1)
                                                     .build()))
                     .assignable(false)
                     .numOfProfileScopeNotMatchedInvocations(1)
                     .build())
            .add(DelegateProfileScopeTestData.builder()
                     .delegate(Delegate.builder()
                                   .accountId(accountId)
                                   .uuid(generateUuid())
                                   .delegateProfileId(generateUuid())
                                   .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                   .build())
                     .task(DelegateTask.builder()
                               .uuid(generateUuid())
                               .accountId(accountId)
                               .setupAbstraction("k0", "v0")
                               .setupAbstraction("k1", "v1")
                               .data(TaskData.builder()
                                         .taskType(TaskType.HTTP.name())
                                         .async(true)
                                         .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                         .build())
                               .build())
                     .scopingRules(singletonList(DelegateProfileScopingRule.builder()
                                                     .description("rule1")
                                                     .scopingEntities(scopingRulesMap1)
                                                     .build()))
                     .assignable(false)
                     .numOfProfileScopeNotMatchedInvocations(1)
                     .build())
            .add(DelegateProfileScopeTestData.builder()
                     .delegate(Delegate.builder()
                                   .accountId(accountId)
                                   .uuid(generateUuid())
                                   .delegateProfileId(generateUuid())
                                   .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                   .build())
                     .task(DelegateTask.builder()
                               .uuid(generateUuid())
                               .accountId(accountId)
                               .setupAbstraction("k1", "v1")
                               .data(TaskData.builder()
                                         .taskType(TaskType.HTTP.name())
                                         .async(true)
                                         .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                         .build())
                               .build())
                     .scopingRules(singletonList(DelegateProfileScopingRule.builder()
                                                     .description("rule2")
                                                     .scopingEntities(scopingRulesMap2)
                                                     .build()))
                     .assignable(false)
                     .numOfProfileScopeNotMatchedInvocations(1)
                     .build())
            .add(DelegateProfileScopeTestData.builder()
                     .delegate(Delegate.builder()
                                   .accountId(accountId)
                                   .uuid(generateUuid())
                                   .delegateProfileId(generateUuid())
                                   .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                   .build())
                     .task(DelegateTask.builder()
                               .uuid(generateUuid())
                               .accountId(accountId)
                               .setupAbstraction("k1", "v13")
                               .setupAbstraction("k2", "v22")
                               .data(TaskData.builder()
                                         .taskType(TaskType.HTTP.name())
                                         .async(true)
                                         .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                         .build())
                               .build())
                     .scopingRules(asList(DelegateProfileScopingRule.builder()
                                              .description("rule3")
                                              .scopingEntities(scopingRulesMap2)
                                              .build(),
                         DelegateProfileScopingRule.builder()
                             .description("rule4")
                             .scopingEntities(scopingRulesMap3)
                             .build()))
                     .assignable(false)
                     .numOfProfileScopeNotMatchedInvocations(1)
                     .build())
            .add(DelegateProfileScopeTestData.builder()
                     .delegate(Delegate.builder()
                                   .accountId(accountId)
                                   .uuid(generateUuid())
                                   .delegateProfileId(generateUuid())
                                   .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                   .ng(true)
                                   .build())
                     .task(DelegateTask.builder()
                               .uuid(generateUuid())
                               .accountId(accountId)
                               .setupAbstraction("k1", "v13")
                               .setupAbstraction("ng", "TRUE")
                               .data(TaskData.builder()
                                         .taskType(TaskType.HTTP.name())
                                         .async(true)
                                         .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                         .build())
                               .build())
                     .scopingRules(singletonList(DelegateProfileScopingRule.builder()
                                                     .description("rule5")
                                                     .scopingEntities(scopingRulesMap4)
                                                     .build()))
                     .assignable(true)
                     .numOfProfileScopeNotMatchedInvocations(0)
                     .build())
            .build();

    for (DelegateProfileScopeTestData test : tests) {
      when(delegateCache.get(accountId, test.getDelegate().getUuid(), false)).thenReturn(test.getDelegate());

      DelegateProfile delegateProfile = DelegateProfile.builder()
                                            .uuid(test.getDelegate().getDelegateProfileId())
                                            .accountId(accountId)
                                            .name(generateUuid())
                                            .scopingRules(test.getScopingRules())
                                            .build();

      persistence.save(delegateProfile);

      BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(test.getTask().getUuid()).build();
      assertThat(assignDelegateService.canAssign(batch, test.getDelegate().getUuid(), test.getTask()))
          .isEqualTo(test.isAssignable());
      verify(delegateSelectionLogsService, Mockito.times(test.getNumOfProfileScopeNotMatchedInvocations()))
          .logProfileScopeRuleNotMatched(
              eq(batch), eq(accountId), eq(test.getDelegate().getUuid()), eq(delegateProfile.getUuid()), anySet());
    }

    // Case to cover non-existing delegate profile
    Delegate delegateWithNonExistingProfile = Delegate.builder()
                                                  .accountId(accountId)
                                                  .uuid(generateUuid())
                                                  .delegateProfileId(generateUuid())
                                                  .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                                  .build();
    when(delegateCache.get(accountId, delegateWithNonExistingProfile.getUuid(), false))
        .thenReturn(delegateWithNonExistingProfile);
    assertThat(assignDelegateService.canAssign(null, delegateWithNonExistingProfile.getUuid(),
                   DelegateTask.builder()
                       .uuid(generateUuid())
                       .accountId(accountId)
                       .setupAbstraction("k1", "v13")
                       .data(TaskData.builder()
                                 .taskType(TaskType.HTTP.name())
                                 .async(true)
                                 .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                 .build())
                       .build()))
        .isEqualTo(true);
  }

  @Value
  @Builder
  public static class TagTestData {
    List<ExecutionCapability> executionCapabilities;
    List<String> delegateTags;
    boolean assignable;
    int numOfMissingAllSelectorsInvocations;
    int numOfMissingSelectorInvocations;
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void assignByTags() {
    List<TagTestData> tests =
        ImmutableList.<TagTestData>builder()
            .add(TagTestData.builder()
                     .executionCapabilities(null)
                     .delegateTags(null)
                     .assignable(true)
                     .numOfMissingAllSelectorsInvocations(0)
                     .numOfMissingSelectorInvocations(0)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(null)
                     .delegateTags(emptyList())
                     .assignable(true)
                     .numOfMissingAllSelectorsInvocations(0)
                     .numOfMissingSelectorInvocations(0)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(emptyList())
                     .delegateTags(null)
                     .assignable(true)
                     .numOfMissingAllSelectorsInvocations(0)
                     .numOfMissingSelectorInvocations(0)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(emptyList())
                     .delegateTags(emptyList())
                     .assignable(true)
                     .numOfMissingAllSelectorsInvocations(0)
                     .numOfMissingSelectorInvocations(0)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(ImmutableList.of(
                         SelectorCapability.builder().selectors(Stream.of("a").collect(Collectors.toSet())).build()))
                     .delegateTags(null)
                     .assignable(false)
                     .numOfMissingAllSelectorsInvocations(1)
                     .numOfMissingSelectorInvocations(0)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(ImmutableList.of(
                         SelectorCapability.builder().selectors(Stream.of("a").collect(Collectors.toSet())).build()))
                     .delegateTags(emptyList())
                     .assignable(false)
                     .numOfMissingAllSelectorsInvocations(2)
                     .numOfMissingSelectorInvocations(0)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(null)
                     .delegateTags(ImmutableList.of("a"))
                     .assignable(true)
                     .numOfMissingAllSelectorsInvocations(2)
                     .numOfMissingSelectorInvocations(0)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(emptyList())
                     .delegateTags(ImmutableList.of("a"))
                     .assignable(true)
                     .numOfMissingAllSelectorsInvocations(2)
                     .numOfMissingSelectorInvocations(0)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(
                         ImmutableList.of(SelectorCapability.builder()
                                              .selectors(Stream.of("a", "b").collect(Collectors.toSet()))
                                              .build()))
                     .delegateTags(ImmutableList.of("a", "c", "b"))
                     .assignable(true)
                     .numOfMissingAllSelectorsInvocations(2)
                     .numOfMissingSelectorInvocations(0)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(
                         ImmutableList.of(SelectorCapability.builder()
                                              .selectors(Stream.of("a", "b", "c").collect(Collectors.toSet()))
                                              .build()))
                     .delegateTags(ImmutableList.of("a", "b"))
                     .assignable(false)
                     .numOfMissingAllSelectorsInvocations(2)
                     .numOfMissingSelectorInvocations(1)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(
                         ImmutableList.of(SelectorCapability.builder()
                                              .selectors(Stream.of("a", "b").collect(Collectors.toSet()))
                                              .build()))
                     .delegateTags(ImmutableList.of("c", "a"))
                     .assignable(false)
                     .numOfMissingAllSelectorsInvocations(2)
                     .numOfMissingSelectorInvocations(2)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(
                         ImmutableList.of(SelectorCapability.builder()
                                              .selectors(Stream.of("a", "b").collect(Collectors.toSet()))
                                              .build()))
                     .delegateTags(ImmutableList.of("c", "d"))
                     .assignable(false)
                     .numOfMissingAllSelectorsInvocations(2)
                     .numOfMissingSelectorInvocations(4)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(
                         ImmutableList.of(SelectorCapability.builder()
                                              .selectors(Stream.of("a ", " B ").collect(Collectors.toSet()))
                                              .build()))
                     .delegateTags(ImmutableList.of("A", " b"))
                     .assignable(true)
                     .numOfMissingAllSelectorsInvocations(2)
                     .numOfMissingSelectorInvocations(4)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(ImmutableList.of(
                         SelectorCapability.builder().selectors(Stream.of("a-b").collect(Collectors.toSet())).build()))
                     .delegateTags(ImmutableList.of("a", " b"))
                     .assignable(false)
                     .numOfMissingAllSelectorsInvocations(2)
                     .numOfMissingSelectorInvocations(5)
                     .build())
            .add(TagTestData.builder()
                     .executionCapabilities(ImmutableList.of(
                         SelectorCapability.builder().selectors(Stream.of("a").collect(Collectors.toSet())).build()))
                     .delegateTags(ImmutableList.of("a-b"))
                     .assignable(false)
                     .numOfMissingAllSelectorsInvocations(2)
                     .numOfMissingSelectorInvocations(6)
                     .build())
            .add(
                TagTestData.builder()
                    .executionCapabilities(ImmutableList.of(
                        SelectorCapability.builder().selectors(Stream.of("", " ").collect(Collectors.toSet())).build()))
                    .delegateTags(ImmutableList.of("a"))
                    .assignable(true)
                    .numOfMissingAllSelectorsInvocations(2)
                    .numOfMissingSelectorInvocations(6)
                    .build())
            .build();

    DelegateTaskBuilder delegateTaskBuilder = DelegateTask.builder()
                                                  .accountId("ACCOUNT_ID")
                                                  .setupAbstraction("appId", "APP_ID")
                                                  .data(TaskData.builder()
                                                            .async(true)
                                                            .taskType(TaskType.SCRIPT.name())
                                                            .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                                            .build());

    DelegateProfile delegateProfile =
        DelegateProfile.builder().uuid(generateUuid()).accountId("ACCOUNT_ID").name("testProfileName").build();

    DelegateBuilder delegateBuilder = Delegate.builder()
                                          .accountId("ACCOUNT_ID")
                                          .uuid("DELEGATE_ID")
                                          .hostName("a.b.c.")
                                          .delegateName("testDelegateName")
                                          .delegateProfileId(delegateProfile.getUuid())
                                          .supportedTaskTypes(Arrays.asList(TaskType.SCRIPT.name()))
                                          .includeScopes(emptyList())
                                          .excludeScopes(emptyList());

    for (TagTestData test : tests) {
      Delegate delegate = delegateBuilder.tags(test.getDelegateTags()).build();
      when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
      when(delegateService.retrieveDelegateSelectors(delegate))
          .thenReturn(delegate.getTags() == null ? new HashSet<>() : new HashSet<>(test.getDelegateTags()));

      DelegateTask delegateTask = delegateTaskBuilder.executionCapabilities(test.getExecutionCapabilities()).build();
      BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(delegateTask.getUuid()).build();
      assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTask)).isEqualTo(test.isAssignable());

      verify(delegateSelectionLogsService, Mockito.times(test.getNumOfMissingAllSelectorsInvocations()))
          .logMissingAllSelectors(batch, "ACCOUNT_ID", "DELEGATE_ID");
      verify(delegateSelectionLogsService, Mockito.times(test.getNumOfMissingSelectorInvocations()))
          .logMissingSelector(eq(batch), eq("ACCOUNT_ID"), eq("DELEGATE_ID"), anyString(), anyString());
    }

    delegateTaskBuilder.setupAbstraction("envId", "ENV_ID");
    delegateBuilder.excludeScopes(
        ImmutableList.of(DelegateScope.builder().environmentTypes(ImmutableList.of(PROD)).build()));

    for (TagTestData test : tests) {
      Delegate delegate = delegateBuilder.tags(test.getDelegateTags()).build();
      when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
      when(delegateService.retrieveDelegateSelectors(delegate))
          .thenReturn(delegate.getTags() == null ? new HashSet<>() : new HashSet<>(test.getDelegateTags()));

      DelegateTask delegateTask = delegateTaskBuilder.executionCapabilities(test.getExecutionCapabilities()).build();
      BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(delegateTask.getUuid()).build();
      assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTask)).isFalse();
    }
  }

  @Value
  @Builder
  public static class NameTestData {
    List<ExecutionCapability> executionCapabilities;
    String delegateName;
    String hostName;
    boolean assignable;
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void assignByNames() {
    when(delegateService.retrieveDelegateSelectors(any(Delegate.class)))
        .thenReturn(emptySet())
        .thenReturn(new HashSet<>(asList("A")))
        .thenReturn(new HashSet<>(asList("a", "b")));

    SelectorCapability selectorCapability =
        SelectorCapability.builder().selectors(Stream.of("a").collect(Collectors.toSet())).build();

    List<NameTestData> tests = ImmutableList.<NameTestData>builder()
                                   .add(NameTestData.builder()
                                            .executionCapabilities(ImmutableList.of(selectorCapability))
                                            .assignable(false)
                                            .build())
                                   .add(NameTestData.builder()
                                            .executionCapabilities(ImmutableList.of(selectorCapability))
                                            .delegateName("A")
                                            .assignable(true)
                                            .build())
                                   .add(NameTestData.builder()
                                            .executionCapabilities(ImmutableList.of(selectorCapability))
                                            .hostName("A")
                                            .assignable(true)
                                            .build())
                                   .add(NameTestData.builder()
                                            .executionCapabilities(ImmutableList.of(
                                                SelectorCapability.builder()
                                                    .selectors(Stream.of("a", "b").collect(Collectors.toSet()))
                                                    .build()))
                                            .delegateName("A")
                                            .hostName("b")
                                            .assignable(true)
                                            .build())
                                   .build();

    DelegateTaskBuilder delegateTaskBuilder = DelegateTask.builder()
                                                  .accountId("ACCOUNT_ID")
                                                  .setupAbstraction("appId", "APP_ID")
                                                  .data(TaskData.builder()
                                                            .async(true)
                                                            .taskType(TaskType.SCRIPT.name())
                                                            .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                                            .build());

    DelegateBuilder delegateBuilder = Delegate.builder()
                                          .accountId("ACCOUNT_ID")
                                          .uuid("DELEGATE_ID")
                                          .includeScopes(emptyList())
                                          .supportedTaskTypes(Arrays.asList(TaskType.SCRIPT.name()))
                                          .excludeScopes(emptyList());

    for (NameTestData test : tests) {
      Delegate delegate = delegateBuilder.delegateName(test.getDelegateName()).hostName(test.getHostName()).build();
      when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);

      DelegateTask delegateTask = delegateTaskBuilder.executionCapabilities(test.getExecutionCapabilities()).build();
      BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(delegateTask.getUuid()).build();
      assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTask)).isEqualTo(test.isAssignable());
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldSaveConnectionResults() {
    List<DelegateConnectionResult> results = singletonList(DelegateConnectionResult.builder()
                                                               .accountId("ACCOUNT_ID")
                                                               .delegateId("DELEGATE_ID")
                                                               .criteria("criteria")
                                                               .validated(true)
                                                               .build());

    assignDelegateService.saveConnectionResults(results);

    DelegateConnectionResult saved = persistence.createQuery(DelegateConnectionResult.class).get();
    assertThat(saved).isNotNull();
    assertThat(saved.getCriteria()).isEqualTo("criteria");
    assertThat(saved.isValidated()).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldUpdateConnectionResults() {
    persistence.save(DelegateConnectionResult.builder()
                         .accountId("ACCOUNT_ID")
                         .delegateId("DELEGATE_ID")
                         .criteria("criteria")
                         .validated(false)
                         .build());

    List<DelegateConnectionResult> results = singletonList(DelegateConnectionResult.builder()
                                                               .accountId("ACCOUNT_ID")
                                                               .delegateId("DELEGATE_ID")
                                                               .criteria("criteria")
                                                               .validated(true)
                                                               .build());

    assignDelegateService.saveConnectionResults(results);

    List<DelegateConnectionResult> saved = persistence.createQuery(DelegateConnectionResult.class)
                                               .filter(DelegateConnectionResultKeys.accountId, "ACCOUNT_ID")
                                               .asList();
    assertThat(saved).isNotNull();
    assertThat(saved.size()).isEqualTo(1);
    assertThat(saved.get(0).getCriteria()).isEqualTo("criteria");
    assertThat(saved.get(0).isValidated()).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBeWhitelisted() {
    persistence.save(DelegateConnectionResult.builder()
                         .accountId("ACCOUNT_ID")
                         .delegateId("DELEGATE_ID")
                         .criteria("criteria")
                         .validated(true)
                         .build());

    Object[] params = {HttpTaskParameters.builder().url("criteria").build()};

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId("ACCOUNT_ID")
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.HTTP.name())
                                              .parameters(params)
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();

    assertThat(assignDelegateService.isWhitelisted(delegateTask, "DELEGATE_ID")).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotBeWhitelistedDiffCriteria() throws ExecutionException {
    DelegateTask delegateTask = createDelegateTask(true, NOT_MATCHING_CRITERIA);
    assertThat(assignDelegateService.isWhitelisted(delegateTask, "DELEGATE_ID")).isFalse();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotBeWhitelistedWhenNotValidated() throws ExecutionException {
    DelegateTask delegateTask = createDelegateTask(false, MATCHING_CRITERIA);
    assertThat(assignDelegateService.isWhitelisted(delegateTask, "DELEGATE_ID")).isFalse();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetConnectedWhitelistedDelegates() throws ExecutionException {
    DelegateTask delegateTask = createDelegateTask(true, MATCHING_CRITERIA);
    List<String> delegateIds = assignDelegateService.connectedWhitelistedDelegates(delegateTask);

    assertThat(delegateIds.size()).isEqualTo(1);
    assertThat(delegateIds.get(0)).isEqualTo("DELEGATE_ID");
  }

  enum CriteriaType { MATCHING_CRITERIA, NOT_MATCHING_CRITERIA }

  private DelegateTask createDelegateTask(boolean validated, CriteriaType criteria) throws ExecutionException {
    Delegate delegate = Delegate.builder()
                            .accountId("ACCOUNT_ID")
                            .uuid("DELEGATE_ID")
                            .status(ENABLED)
                            .lastHeartBeat(clock.millis())
                            .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                            .build();

    when(accountDelegatesCache.get("ACCOUNT_ID")).thenReturn(asList(delegate));

    HttpConnectionExecutionCapability matchingExecutionCapability =
        buildHttpConnectionExecutionCapability("http//www.matching.com", null);

    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId("ACCOUNT_ID")
                                                    .delegateId("DELEGATE_ID")
                                                    .criteria(matchingExecutionCapability.fetchCapabilityBasis())
                                                    .validated(validated)
                                                    .build();

    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));

    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);

    HttpConnectionExecutionCapability executionCapability = criteria == MATCHING_CRITERIA
        ? matchingExecutionCapability
        : buildHttpConnectionExecutionCapability("http//www.not-matching.com", null);

    Object[] params = {HttpTaskParameters.builder().url(executionCapability.getUrl()).build()};
    return DelegateTask.builder()
        .accountId("ACCOUNT_ID")
        .data(TaskData.builder()
                  .async(true)
                  .taskType(TaskType.HTTP.name())
                  .parameters(params)
                  .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                  .build())
        .executionCapabilities(asList(executionCapability))
        .build();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotGetConnectedWhitelistedDelegatesNotValidated() throws ExecutionException {
    DelegateTask delegateTask = createDelegateTask(false, MATCHING_CRITERIA);

    List<String> delegateIds = assignDelegateService.connectedWhitelistedDelegates(delegateTask);

    assertThat(delegateIds.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotGetConnectedWhitelistedDelegatesOldHeartbeat() {
    Delegate delegate = Delegate.builder()
                            .accountId("ACCOUNT_ID")
                            .uuid("DELEGATE_ID")
                            .status(ENABLED)
                            .lastHeartBeat(clock.millis() - MAX_DELEGATE_LAST_HEARTBEAT - 1000)
                            .build();
    persistence.save(delegate);
    persistence.save(DelegateConnectionResult.builder()
                         .accountId("ACCOUNT_ID")
                         .delegateId("DELEGATE_ID")
                         .criteria("criteria")
                         .validated(true)
                         .build());
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);

    Object[] params = {HttpTaskParameters.builder().url("criteria").build()};
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId("ACCOUNT_ID")
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.HTTP.name())
                                              .parameters(params)
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();

    List<String> delegateIds = assignDelegateService.connectedWhitelistedDelegates(delegateTask);

    assertThat(delegateIds.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotGetConnectedWhitelistedDelegatesOtherCriteria() throws ExecutionException {
    DelegateTask delegateTask = createDelegateTask(true, NOT_MATCHING_CRITERIA);

    List<String> delegateIds = assignDelegateService.connectedWhitelistedDelegates(delegateTask);

    assertThat(delegateIds.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldGetWhitelistedDelegatesWithoutCriteriaCapabilityFramework() throws ExecutionException {
    TaskData taskData = TaskData.builder().taskType(TaskType.SPOTINST_COMMAND_TASK.name()).build();
    DelegateTask delegateTask =
        DelegateTask.builder().accountId("ACCOUNT_ID").data(taskData).executionCapabilities(emptyList()).build();
    Delegate delegate = Delegate.builder()
                            .accountId("ACCOUNT_ID")
                            .uuid("DELEGATE_ID")
                            .status(ENABLED)
                            .lastHeartBeat(clock.millis())
                            .supportedTaskTypes(Arrays.asList(TaskType.SPOTINST_COMMAND_TASK.name()))
                            .build();
    when(accountDelegatesCache.get("ACCOUNT_ID")).thenReturn(asList(delegate));
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    List<String> delegateIds = assignDelegateService.connectedWhitelistedDelegates(delegateTask);
    assertThat(delegateIds).containsExactly(delegate.getUuid());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetNullFirstAttemptDelegate() {
    Object[] params = {HttpTaskParameters.builder().url("criteria-other").build()};
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId("ACCOUNT_ID")
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.HTTP.name())
                                              .parameters(params)
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();

    String delegateId = assignDelegateService.pickFirstAttemptDelegate(delegateTask);

    assertThat(delegateId).isEqualTo(null);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void shouldGetFirstAttemptDelegate() throws ExecutionException {
    DelegateTask delegateTask = createDelegateTask(true, MATCHING_CRITERIA);

    String delegateId = assignDelegateService.pickFirstAttemptDelegate(delegateTask);

    assertThat(delegateId).isEqualTo("DELEGATE_ID");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAssignDelegateWithNullIncludeScope() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId("ACCOUNT_ID")
                                    .setupAbstraction("appId", "APP_ID")
                                    .setupAbstraction("envId", "ENV_ID")
                                    .data(TaskData.builder()
                                              .async(true)
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .taskType(TaskType.HTTP.name())
                                              .build())
                                    .build();
    Delegate delegate = Delegate.builder()
                            .accountId("ACCOUNT_ID")
                            .uuid("DELEGATE_ID")
                            .includeScopes(singletonList(null))
                            .excludeScopes(emptyList())
                            .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                            .build();
    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(delegateTask.getUuid()).build();
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTask)).isTrue();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAssignDelegateWithNullExcludeScope() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId("ACCOUNT_ID")
                                    .setupAbstraction("appId", "APP_ID")
                                    .setupAbstraction("envId", "ENV_ID")
                                    .data(TaskData.builder()
                                              .async(true)
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .taskType(TaskType.HTTP.name())
                                              .build())
                                    .build();

    Delegate delegate = Delegate.builder()
                            .accountId("ACCOUNT_ID")
                            .uuid("DELEGATE_ID")
                            .includeScopes(emptyList())
                            .excludeScopes(singletonList(null))
                            .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                            .build();
    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(delegateTask.getUuid()).build();
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTask)).isTrue();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAssignDelegateWithMultipleIncludeScopes() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId("ACCOUNT_ID")
                                    .setupAbstraction("appId", "APP_ID")
                                    .setupAbstraction("envId", "ENV_ID")
                                    .data(TaskData.builder()
                                              .async(true)
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .taskType(TaskType.HTTP.name())
                                              .build())
                                    .build();

    List<DelegateScope> includeScopes = new ArrayList<>();
    includeScopes.add(null);
    includeScopes.add(DelegateScope.builder().environmentTypes(ImmutableList.of(PROD)).build());

    Delegate delegate = Delegate.builder()
                            .accountId("ACCOUNT_ID")
                            .uuid("DELEGATE_ID")
                            .includeScopes(includeScopes)
                            .excludeScopes(emptyList())
                            .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                            .build();
    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(delegateTask.getUuid()).build();
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTask)).isTrue();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldAssignDelegateWithInfrastructureMappingScope() {
    InfrastructureMapping infrastructureMapping =
        aGcpKubernetesInfrastructureMapping()
            .withAppId("APP_ID")
            .withComputeProviderSettingId("computeProviderSetting_Id")
            .withUuid("infraMapping_Id")
            .withClusterName("k")
            .withNamespace("default")
            .withEnvId("env_Id")
            .withInfraMappingType(InfrastructureMappingType.GCP_KUBERNETES.getName())
            .withServiceId("serviceId")
            .withUuid("infraMapping_Id")
            .withAccountId("account_id")
            .build();
    infrastructureMapping.setInfrastructureDefinitionId("INFRA_DEFINITION_ID");

    List<DelegateScope> scopes = new ArrayList<>();
    scopes.add(DelegateScope.builder()
                   .infrastructureDefinitions(ImmutableList.of("INFRA_DEFINITION_ID"))
                   .services(ImmutableList.of("serviceId"))
                   .build());

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId("ACCOUNT_ID")
                                    .setupAbstraction("appId", "APP_ID")
                                    .setupAbstraction("envId", "ENV_ID")
                                    .setupAbstraction("infrastructureMappingId", infrastructureMapping.getUuid())
                                    .data(TaskData.builder()
                                              .async(true)
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .taskType(TaskType.SCRIPT.name())
                                              .build())
                                    .build();

    DelegateTask delegateTask2 = DelegateTask.builder()
                                     .accountId("ACCOUNT_ID")
                                     .setupAbstraction("appId", "APP_ID")
                                     .setupAbstraction("envId", "ENV_ID")
                                     .setupAbstraction("infrastructureMappingId", WRONG_INFRA_MAPPING_ID)
                                     .data(TaskData.builder()
                                               .async(true)
                                               .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                               .taskType(TaskType.SCRIPT.name())
                                               .build())
                                     .build();
    Delegate delegate = Delegate.builder()
                            .accountId("ACCOUNT_ID")
                            .uuid("DELEGATE_ID")
                            .includeScopes(scopes)
                            .excludeScopes(emptyList())
                            .supportedTaskTypes(Arrays.asList(TaskType.SCRIPT.name()))
                            .build();
    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(delegateTask.getUuid()).build();
    when(infrastructureMappingService.get("APP_ID", "infraMapping_Id")).thenReturn(infrastructureMapping);
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTask)).isTrue();

    assertThat(assignDelegateService.canAssign(batch, "DELEGATE_ID", delegateTask2)).isFalse();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldExtractSelectorsFromSelectorsCapability() {
    Set<String> selectors1 = Stream.of("a", "b").collect(Collectors.toSet());
    Set<String> selectors2 = Stream.of("a", "c").collect(Collectors.toSet());

    SelectorCapability selectorCapability1 = SelectorCapability.builder().selectors(selectors1).build();
    SelectorCapability selectorCapability2 = SelectorCapability.builder().selectors(selectors2).build();

    List<ExecutionCapability> executionCapabilityList = asList(selectorCapability1, selectorCapability2);

    DelegateTask delegateTask =
        DelegateTask.builder().accountId("ACCOUNT_ID").executionCapabilities(executionCapabilityList).build();

    List<String> extractSelectorsList = assignDelegateService.extractSelectors(delegateTask);

    assertThat(extractSelectorsList).isNotNull();
    assertThat(extractSelectorsList).isNotEmpty();
    assertThat(extractSelectorsList).containsExactlyInAnyOrder("a", "b", "c");
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldNotExtractSelectorsFromSelectorsCapability() {
    HttpConnectionExecutionCapability httpConnectionExecutionCapability =
        HttpConnectionExecutionCapability.builder().port(80).build();

    List<ExecutionCapability> executionCapabilityList = asList(httpConnectionExecutionCapability);

    DelegateTask delegateTask =
        DelegateTask.builder().accountId("ACCOUNT_ID").executionCapabilities(executionCapabilityList).build();

    List<String> extractSelectorsList = assignDelegateService.extractSelectors(delegateTask);

    assertThat(extractSelectorsList).isNullOrEmpty();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldExtractSelectorFromTaskSelectors() {
    List<String> tagsList = asList("a", "b", "c");

    DelegateTask delegateTask = DelegateTask.builder().accountId("ACCOUNT_ID").tags(tagsList).build();

    List<String> extractSelectorsList = assignDelegateService.extractSelectors(delegateTask);

    assertThat(extractSelectorsList).isNotNull();
    assertThat(extractSelectorsList).isNotEmpty();
    assertThat(extractSelectorsList).containsExactlyInAnyOrder("a", "b", "c");
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldExtractSelectorMerged() {
    Set<String> selectors = Stream.of("a", "d").collect(Collectors.toSet());
    List<String> tagsList = asList("a", "b", "c");

    SelectorCapability selectorCapability = SelectorCapability.builder().selectors(selectors).build();

    List<ExecutionCapability> executionCapabilityList = asList(selectorCapability);

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId("ACCOUNT_ID")
                                    .tags(tagsList)
                                    .executionCapabilities(executionCapabilityList)
                                    .build();

    List<String> extractSelectorsList = assignDelegateService.extractSelectors(delegateTask);

    assertThat(extractSelectorsList).isNotNull();
    assertThat(extractSelectorsList).isNotEmpty();
    assertThat(extractSelectorsList).containsExactlyInAnyOrder("a", "b", "c", "d");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldRetrieveNoActiveDelegates() {
    String accountId = generateUuid();

    List<String> activeDelegates = assignDelegateService.retrieveActiveDelegates(accountId, null);
    assertThat(activeDelegates).isNotNull();
    assertThat(activeDelegates).isEmpty();
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldReturnNoInstalledDelegates() {
    String accountId = generateUuid();
    boolean noInstalledDelegates = assignDelegateService.noInstalledDelegates(accountId);
    assertThat(noInstalledDelegates).isTrue();
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldReturnInstalledDelegates() throws ExecutionException {
    String accountId = generateUuid();
    String activeDelegateId = generateUuid();
    Delegate activeDelegate = createDelegateBuilder().accountId(accountId).uuid(activeDelegateId).build();
    when(accountDelegatesCache.get(accountId)).thenReturn(asList(activeDelegate));
    boolean noInstalledDelegates = assignDelegateService.noInstalledDelegates(accountId);
    assertThat(noInstalledDelegates).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldRetrieveActiveDelegates() throws ExecutionException {
    String accountId = generateUuid();
    String activeDelegate1Id = generateUuid();
    String activeDelegate2Id = generateUuid();
    Delegate activeDelegate1 = createDelegateBuilder().accountId(accountId).uuid(activeDelegate1Id).build();

    Delegate activeDelegate2 = createDelegateBuilder().accountId(accountId).uuid(activeDelegate2Id).build();

    Delegate disconnectedDelegate = createDelegateBuilder()
                                        .accountId(accountId)
                                        .uuid(generateUuid())
                                        .lastHeartBeat(System.currentTimeMillis() - 500000L)
                                        .build();

    Delegate wapprDelegate = createDelegateBuilder()
                                 .accountId(accountId)
                                 .uuid(generateUuid())
                                 .status(DelegateInstanceStatus.WAITING_FOR_APPROVAL)
                                 .build();

    String groupName = generateUuid();
    Delegate delegateInScalingGroup = Delegate.builder()
                                          .accountId(accountId)
                                          .uuid(generateUuid())
                                          .lastHeartBeat(System.currentTimeMillis() - 500000L)
                                          .delegateGroupName(groupName)
                                          .build();

    Delegate deletedDelegate = createDelegateBuilder()
                                   .accountId(accountId)
                                   .uuid(generateUuid())
                                   .status(DelegateInstanceStatus.DELETED)
                                   .build();

    when(accountDelegatesCache.get(accountId))
        .thenReturn(asList(activeDelegate1, activeDelegate2, disconnectedDelegate, wapprDelegate, deletedDelegate,
            delegateInScalingGroup));

    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(generateUuid()).build();

    List<String> activeDelegates = assignDelegateService.retrieveActiveDelegates(accountId, batch);
    assertThat(activeDelegates).isNotNull();
    assertThat(activeDelegates.size()).isEqualTo(2);
    assertThat(activeDelegates.containsAll(asList(activeDelegate1Id, activeDelegate2Id))).isTrue();

    Set<String> disconnectedDelegates = new HashSet<>();
    disconnectedDelegates.add(disconnectedDelegate.getUuid());
    verify(delegateSelectionLogsService).logDisconnectedDelegate(eq(batch), eq(accountId), eq(disconnectedDelegates));

    Set<String> disconnectedScalingGroup = new HashSet<>();
    disconnectedScalingGroup.add(delegateInScalingGroup.getDelegateGroupName());
    verify(delegateSelectionLogsService)
        .logDisconnectedScalingGroup(eq(batch), eq(accountId), eq(disconnectedScalingGroup), eq(groupName));

    activeDelegate1.setNg(true);
    batch = BatchDelegateSelectionLog.builder().taskId(generateUuid()).build();

    activeDelegates = assignDelegateService.retrieveActiveDelegates(accountId, batch);
    assertThat(activeDelegates).isNotNull();
    assertThat(activeDelegates.size()).isEqualTo(1);
    assertThat(activeDelegates).containsExactly(activeDelegate2Id);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldLogDisconnectedDelegate() throws ExecutionException {
    String accountId = generateUuid();
    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(generateUuid()).build();

    Delegate disconnectedDelegate = createDelegateBuilder()
                                        .accountId(accountId)
                                        .uuid(generateUuid())
                                        .lastHeartBeat(System.currentTimeMillis() - 500000L)
                                        .build();

    when(accountDelegatesCache.get(accountId)).thenReturn(asList(disconnectedDelegate));

    List<Delegate> delegates = new ArrayList<>();
    delegates.add(disconnectedDelegate);

    Map<DelegateActivity, List<Delegate>> delegatesMap = new HashMap<>();
    delegatesMap.put(DelegateActivity.DISCONNECTED, delegates);

    assignDelegateService.logInactiveDelegates(batch, accountId, delegatesMap);

    Set<String> disconnectedDelegates = new HashSet<>();
    disconnectedDelegates.add(disconnectedDelegate.getUuid());
    verify(delegateSelectionLogsService, times(1))
        .logDisconnectedDelegate(eq(batch), eq(accountId), eq(disconnectedDelegates));
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldLogDisconnectedScalingGroup() throws ExecutionException {
    String accountId = generateUuid();
    String groupName = generateUuid();
    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(generateUuid()).build();

    Delegate delegateInScalingGroup = Delegate.builder()
                                          .accountId(accountId)
                                          .uuid(generateUuid())
                                          .lastHeartBeat(System.currentTimeMillis() - 500000L)
                                          .delegateGroupName(groupName)
                                          .build();

    when(accountDelegatesCache.get(accountId)).thenReturn(asList(delegateInScalingGroup));

    List<Delegate> delegates = new ArrayList<>();
    delegates.add(delegateInScalingGroup);

    Map<DelegateActivity, List<Delegate>> delegatesMap = new HashMap<>();
    delegatesMap.put(DelegateActivity.DISCONNECTED, delegates);

    assignDelegateService.logInactiveDelegates(batch, accountId, delegatesMap);

    Set<String> disconnectedScalingGroup = new HashSet<>();
    disconnectedScalingGroup.add(delegateInScalingGroup.getDelegateGroupName());
    verify(delegateSelectionLogsService, times(1))
        .logDisconnectedScalingGroup(eq(batch), eq(accountId), eq(disconnectedScalingGroup), eq(groupName));
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldLogWaitingForApprovalDelegate() throws ExecutionException {
    String accountId = generateUuid();
    String delegateId = generateUuid();

    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(generateUuid()).build();

    Delegate waitForApprovalDelegate = createDelegateBuilder()
                                           .uuid(delegateId)
                                           .accountId(accountId)
                                           .uuid(generateUuid())
                                           .status(DelegateInstanceStatus.WAITING_FOR_APPROVAL)
                                           .build();

    when(accountDelegatesCache.get(accountId)).thenReturn(asList(waitForApprovalDelegate));

    List<Delegate> delegates = new ArrayList<>();
    delegates.add(waitForApprovalDelegate);

    Map<DelegateActivity, List<Delegate>> delegatesMap = new HashMap<>();
    delegatesMap.put(DelegateActivity.WAITING_FOR_APPROVAL, delegates);

    assignDelegateService.logInactiveDelegates(batch, accountId, delegatesMap);

    Set<String> delegateIds = new HashSet<>();
    delegateIds.add(waitForApprovalDelegate.getUuid());
    verify(delegateSelectionLogsService, times(1))
        .logWaitingForApprovalDelegate(eq(batch), eq(accountId), eq(delegateIds));
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldNotLogDisconnectedDelegate() throws ExecutionException {
    String accountId = generateUuid();
    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(generateUuid()).build();

    Delegate disconnectedDelegate = createDelegateBuilder()
                                        .accountId(accountId)
                                        .uuid(generateUuid())
                                        .lastHeartBeat(System.currentTimeMillis() - 500000L)
                                        .build();

    when(accountDelegatesCache.get(accountId)).thenReturn(asList(disconnectedDelegate));

    Map<DelegateActivity, List<Delegate>> delegatesMap = new HashMap<>();

    assignDelegateService.logInactiveDelegates(batch, accountId, delegatesMap);

    Set<String> disconnectedDelegates = new HashSet<>();
    disconnectedDelegates.add(disconnectedDelegate.getUuid());
    verify(delegateSelectionLogsService, never())
        .logDisconnectedDelegate(eq(batch), eq(accountId), eq(disconnectedDelegates));
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldNotLogDisconnectedScalingGroup() throws ExecutionException {
    String accountId = generateUuid();
    String groupName = generateUuid();
    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(generateUuid()).build();

    Delegate delegateInScalingGroup = Delegate.builder()
                                          .accountId(accountId)
                                          .uuid(generateUuid())
                                          .lastHeartBeat(System.currentTimeMillis() - 500000L)
                                          .delegateGroupName(groupName)
                                          .build();

    when(accountDelegatesCache.get(accountId)).thenReturn(asList(delegateInScalingGroup));

    Map<DelegateActivity, List<Delegate>> delegatesMap = new HashMap<>();

    assignDelegateService.logInactiveDelegates(batch, accountId, delegatesMap);

    Set<String> disconnectedScalingGroup = new HashSet<>();
    disconnectedScalingGroup.add(delegateInScalingGroup.getDelegateGroupName());
    verify(delegateSelectionLogsService, never())
        .logDisconnectedScalingGroup(eq(batch), eq(accountId), eq(disconnectedScalingGroup), eq(groupName));
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldNotLogWaitingForApprovalDelegate() throws ExecutionException {
    String accountId = generateUuid();
    String delegateId = generateUuid();

    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(generateUuid()).build();

    Delegate waitForApprovalDelegate = createDelegateBuilder()
                                           .uuid(delegateId)
                                           .accountId(accountId)
                                           .uuid(generateUuid())
                                           .status(DelegateInstanceStatus.WAITING_FOR_APPROVAL)
                                           .build();

    when(accountDelegatesCache.get(accountId)).thenReturn(asList(waitForApprovalDelegate));

    Map<DelegateActivity, List<Delegate>> delegatesMap = new HashMap<>();

    assignDelegateService.logInactiveDelegates(batch, accountId, delegatesMap);

    Set<String> delegateIds = new HashSet<>();
    delegateIds.add(waitForApprovalDelegate.getUuid());
    verify(delegateSelectionLogsService, never())
        .logWaitingForApprovalDelegate(eq(batch), eq(accountId), eq(delegateIds));
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldOnlyLogWaitingForApprovalDelegate() throws ExecutionException {
    String groupName = generateUuid();
    String accountId = generateUuid();
    String delegateId = generateUuid();

    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(generateUuid()).build();

    Delegate waitForApprovalDelegate = createDelegateBuilder()
                                           .uuid(delegateId)
                                           .accountId(accountId)
                                           .uuid(generateUuid())
                                           .status(DelegateInstanceStatus.WAITING_FOR_APPROVAL)
                                           .build();

    Delegate delegateInScalingGroup = Delegate.builder()
                                          .accountId(accountId)
                                          .uuid(generateUuid())
                                          .lastHeartBeat(System.currentTimeMillis() - 500000L)
                                          .delegateGroupName(groupName)
                                          .build();

    Delegate disconnectedDelegate = createDelegateBuilder()
                                        .accountId(accountId)
                                        .uuid(generateUuid())
                                        .lastHeartBeat(System.currentTimeMillis() - 500000L)
                                        .build();

    when(accountDelegatesCache.get(accountId))
        .thenReturn(asList(waitForApprovalDelegate, delegateInScalingGroup, disconnectedDelegate));

    List<Delegate> delegates = new ArrayList<>();
    delegates.add(waitForApprovalDelegate);

    Map<DelegateActivity, List<Delegate>> delegatesMap = new HashMap<>();
    delegatesMap.put(DelegateActivity.WAITING_FOR_APPROVAL, delegates);

    assignDelegateService.logInactiveDelegates(batch, accountId, delegatesMap);

    Set<String> delegateIds = new HashSet<>();
    delegateIds.add(waitForApprovalDelegate.getUuid());
    verify(delegateSelectionLogsService, times(1))
        .logWaitingForApprovalDelegate(eq(batch), eq(accountId), eq(delegateIds));

    Set<String> disconnectedScalingGroup = new HashSet<>();
    disconnectedScalingGroup.add(delegateInScalingGroup.getDelegateGroupName());
    verify(delegateSelectionLogsService, never())
        .logDisconnectedScalingGroup(eq(batch), eq(accountId), eq(disconnectedScalingGroup), eq(groupName));

    Set<String> disconnectedDelegates = new HashSet<>();
    disconnectedDelegates.add(disconnectedDelegate.getUuid());
    verify(delegateSelectionLogsService, never())
        .logDisconnectedDelegate(eq(batch), eq(accountId), eq(disconnectedDelegates));
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldOnlyLogDisconnectedDelegate() throws ExecutionException {
    String groupName = generateUuid();
    String accountId = generateUuid();
    String delegateId = generateUuid();

    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().taskId(generateUuid()).build();

    Delegate waitForApprovalDelegate = createDelegateBuilder()
                                           .uuid(delegateId)
                                           .accountId(accountId)
                                           .uuid(generateUuid())
                                           .status(DelegateInstanceStatus.WAITING_FOR_APPROVAL)
                                           .build();

    Delegate delegateInScalingGroup = Delegate.builder()
                                          .accountId(accountId)
                                          .uuid(generateUuid())
                                          .lastHeartBeat(System.currentTimeMillis() - 500000L)
                                          .delegateGroupName(groupName)
                                          .build();

    Delegate disconnectedDelegate = createDelegateBuilder()
                                        .accountId(accountId)
                                        .uuid(generateUuid())
                                        .lastHeartBeat(System.currentTimeMillis() - 500000L)
                                        .build();

    when(accountDelegatesCache.get(accountId))
        .thenReturn(asList(waitForApprovalDelegate, delegateInScalingGroup, disconnectedDelegate));

    List<Delegate> delegates = new ArrayList<>();
    delegates.add(disconnectedDelegate);

    Map<DelegateActivity, List<Delegate>> delegatesMap = new HashMap<>();
    delegatesMap.put(DelegateActivity.DISCONNECTED, delegates);

    assignDelegateService.logInactiveDelegates(batch, accountId, delegatesMap);

    Set<String> disconnectedDelegates = new HashSet<>();
    disconnectedDelegates.add(disconnectedDelegate.getUuid());
    verify(delegateSelectionLogsService, times(1))
        .logDisconnectedDelegate(eq(batch), eq(accountId), eq(disconnectedDelegates));

    Set<String> delegateIds = new HashSet<>();
    delegateIds.add(waitForApprovalDelegate.getUuid());
    verify(delegateSelectionLogsService, never())
        .logWaitingForApprovalDelegate(eq(batch), eq(accountId), eq(delegateIds));

    Set<String> disconnectedScalingGroup = new HashSet<>();
    disconnectedScalingGroup.add(delegateInScalingGroup.getDelegateGroupName());
    verify(delegateSelectionLogsService, never())
        .logDisconnectedScalingGroup(eq(batch), eq(accountId), eq(disconnectedScalingGroup), eq(groupName));
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldFetchCriteriaWithCapabilityFramework() {
    Set<String> selectors = Stream.of("a", "b").collect(Collectors.toSet());

    HttpConnectionExecutionCapability connectionExecutionCapability =
        HttpConnectionExecutionCapability.builder().url("localhost").build();
    SelectorCapability selectorCapability = SelectorCapability.builder().selectors(selectors).build();

    List<ExecutionCapability> executionCapabilityList = asList(selectorCapability, connectionExecutionCapability);

    DelegateTask delegateTask =
        DelegateTask.builder().accountId("ACCOUNT_ID").executionCapabilities(executionCapabilityList).build();

    List<String> criteria = assignDelegateService.fetchCriteria(delegateTask);

    assertThat(criteria).containsExactly("localhost");
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testGetActiveDelegateAssignmentErrorMessageSelectionLogsAvailable() {
    String accountId = generateUuid();
    String uuid = generateUuid();
    String delegateId = generateUuid();

    DelegateTask delegateTask = DelegateTask.builder().uuid(uuid).accountId(accountId).delegateId(delegateId).build();

    DelegateSelectionLogParams delegateSelectionLog = DelegateSelectionLogParams.builder()
                                                          .delegateId(delegateTask.getDelegateId())
                                                          .delegateName("testDelegateName")
                                                          .delegateHostName("testDelegateHostName")
                                                          .delegateProfileName("testDelegateProfileName")
                                                          .conclusion("Disconnected")
                                                          .message("testMessage")
                                                          .build();

    List<DelegateSelectionLogParams> delegateSelectionLogs = asList(delegateSelectionLog);

    when(delegateSelectionLogsService.fetchTaskSelectionLogs(accountId, uuid)).thenReturn(delegateSelectionLogs);

    String errorMessage = assignDelegateService.getActiveDelegateAssignmentErrorMessage(null, delegateTask);

    String expectedErrorMessage =
        String.format(ERROR_MESSAGE, delegateSelectionLog.getDelegateId(), delegateSelectionLog.getDelegateName(),
            delegateSelectionLog.getDelegateHostName(), delegateSelectionLog.getDelegateProfileName(),
            delegateSelectionLog.getConclusion(), delegateSelectionLog.getMessage(),
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(delegateSelectionLog.getEventTimestamp()), ZoneId.systemDefault()));

    assertThat(errorMessage).isNotNull();
    assertThat(errorMessage).isEqualTo(expectedErrorMessage);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testGetActiveDelegateAssignmentErrorMessage_emptyActiveDelegates() {
    String accountId = generateUuid();
    String uuid = generateUuid();
    String delegateId = generateUuid();

    DelegateTask delegateTask = DelegateTask.builder().uuid(uuid).accountId(accountId).delegateId(delegateId).build();

    String errorMessage = assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, delegateTask);

    String expectedErrorMessage = "There were no active delegates to complete the task.";

    assertThat(errorMessage).isNotNull();
    assertThat(errorMessage).isEqualTo(expectedErrorMessage);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testShouldValidate() throws ExecutionException {
    String accountId = generateUuid();

    DelegateTaskBuilder taskBuilder = DelegateTask.builder()
                                          .accountId(accountId)
                                          .description("HTTP task")
                                          .data(TaskData.builder().taskType(TaskType.HTTP.name()).build());

    DelegateConnectionResultBuilder connectionResultBuilder =
        DelegateConnectionResult.builder().accountId(accountId).criteria("https://google.com");

    // test case: no criteria
    assertThat(assignDelegateService.shouldValidate(taskBuilder.build(), null)).isFalse();

    // test case: empty criteria
    HttpConnectionExecutionCapability httpCapability = HttpConnectionExecutionCapability.builder().build();
    DelegateTask task = taskBuilder.build();
    task.setExecutionCapabilities(Arrays.asList(httpCapability));
    assertThat(assignDelegateService.shouldValidate(task, null)).isTrue();

    // test case: no connection result
    httpCapability = HttpConnectionExecutionCapability.builder().url("https://google.com").build();
    task.setExecutionCapabilities(Arrays.asList(httpCapability));
    assertThat(assignDelegateService.shouldValidate(task, null)).isTrue();

    // test case: connection result present, but not validated
    String delegateId = generateUuid();
    DelegateConnectionResult connectionResult = connectionResultBuilder.build();
    connectionResult.setDelegateId(delegateId);
    connectionResult.setValidated(false);
    persistence.save(connectionResult);
    assertThat(assignDelegateService.shouldValidate(task, delegateId)).isTrue();

    // test case: connection result present, and validated, but expired
    delegateId = generateUuid();
    connectionResult = connectionResultBuilder.build();
    connectionResult.setDelegateId(delegateId);
    connectionResult.setValidated(true);
    connectionResult.setLastUpdatedAt(clock.millis() - TimeUnit.MINUTES.toMillis(6));
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegateId, connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    assertThat(assignDelegateService.shouldValidate(task, delegateId)).isTrue();

    // test case: connection result present, validated, not expired, delegate disconnected and no other connected
    // whitelisted ones
    delegateId = generateUuid();
    connectionResult = connectionResultBuilder.build();
    connectionResult.setDelegateId(delegateId);
    connectionResult.setValidated(true);
    connectionResult.setLastUpdatedAt(clock.millis());
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegateId, connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    assertThat(assignDelegateService.shouldValidate(task, delegateId)).isTrue();

    // test case: connection result present, validated, not expired, delegate disconnected, but there are other
    // connected whitelisted ones
    delegateId = generateUuid();
    connectionResult = connectionResultBuilder.build();
    connectionResult.setDelegateId(delegateId);
    connectionResult.setValidated(true);
    connectionResult.setLastUpdatedAt(clock.millis());

    Delegate delegate2 = Delegate.builder()
                             .uuid(generateUuid())
                             .status(ENABLED)
                             .lastHeartBeat(clock.millis())
                             .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                             .build();

    DelegateConnectionResult connectionResult2 = connectionResultBuilder.build();
    connectionResult2.setDelegateId(delegate2.getUuid());
    connectionResult2.setValidated(true);
    connectionResult2.setLastUpdatedAt(clock.millis());

    when(delegateConnectionResultCache.get(ImmutablePair.of(delegateId, connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));

    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate2.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult2));

    when(accountDelegatesCache.get(accountId)).thenReturn(Collections.emptyList()).thenReturn(Arrays.asList(delegate2));
    when(delegateCache.get(task.getAccountId(), delegate2.getUuid(), false))
        .thenReturn(
            Delegate.builder().uuid(delegateId).supportedTaskTypes(Arrays.asList(TaskType.HTTP.name())).build());
    assertThat(assignDelegateService.shouldValidate(task, delegateId)).isFalse();

    // test case: connection result present, validated, not expired, delegate connected
    delegateId = generateUuid();
    connectionResult = connectionResultBuilder.build();
    connectionResult.setDelegateId(delegateId);
    connectionResult.setValidated(true);
    connectionResult.setLastUpdatedAt(clock.millis());
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegateId, connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    when(accountDelegatesCache.get(accountId))
        .thenReturn(
            Arrays.asList(Delegate.builder().uuid(delegateId).status(ENABLED).lastHeartBeat(clock.millis()).build()));
    assertThat(assignDelegateService.shouldValidate(task, delegateId)).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCanAssignCgNg() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).build())
                                    .executionCapabilities(emptyList())
                                    .build();
    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .uuid(delegateId)
                            .status(ENABLED)
                            .lastHeartBeat(clock.millis())
                            .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                            .build();

    BatchDelegateSelectionLog batch = Mockito.mock(BatchDelegateSelectionLog.class);
    when(delegateCache.get(accountId, delegateId, false)).thenReturn(delegate);

    // Test delegate cg and task cg
    canAssignCgNgAssert(delegateTask, batch, delegate, false, null, true);
    canAssignCgNgAssert(delegateTask, batch, delegate, false, ImmutableMap.of(), true);
    canAssignCgNgAssert(delegateTask, batch, delegate, false, ImmutableMap.of("k1", "v1"), true);
    canAssignCgNgAssert(delegateTask, batch, delegate, false, ImmutableMap.of("ng", "FALSE"), true);
    canAssignCgNgAssert(delegateTask, batch, delegate, false, ImmutableMap.of("ng", "false"), true);
    canAssignCgNgAssert(delegateTask, batch, delegate, false, ImmutableMap.of("ng", "invalidValue"), true);

    // Test delegate ng and task ng
    canAssignCgNgAssert(delegateTask, batch, delegate, true, ImmutableMap.of("ng", "TRUE"), true);
    canAssignCgNgAssert(delegateTask, batch, delegate, true, ImmutableMap.of("ng", "true"), true);

    // Test other non-matching cases
    canAssignCgNgAssert(delegateTask, batch, delegate, true, null, false);
    canAssignCgNgAssert(delegateTask, batch, delegate, true, ImmutableMap.of(), false);
    canAssignCgNgAssert(delegateTask, batch, delegate, true, ImmutableMap.of("k1", "v1"), false);
    canAssignCgNgAssert(delegateTask, batch, delegate, false, ImmutableMap.of("ng", "TRUE"), false);
    canAssignCgNgAssert(delegateTask, batch, delegate, false, ImmutableMap.of("ng", "true"), false);
    canAssignCgNgAssert(delegateTask, batch, delegate, true, ImmutableMap.of("ng", "invalidValue"), false);
  }

  private void canAssignCgNgAssert(DelegateTask delegateTask, BatchDelegateSelectionLog batch, Delegate delegate,
      boolean isDelegateNg, Map<String, String> setupAbstractions, boolean canAssign) {
    delegate.setNg(isDelegateNg);
    delegateTask.setSetupAbstractions(setupAbstractions);
    assertThat(assignDelegateService.canAssign(batch, delegate.getUuid(), delegateTask)).isEqualTo(canAssign);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCanAssignOwner() {
    TaskData taskData = TaskData.builder().taskType(TaskType.SCRIPT.name()).build();
    String accountId = generateUuid();
    String delegateId = generateUuid();
    DelegateTask delegateTask =
        DelegateTask.builder().accountId(accountId).data(taskData).executionCapabilities(emptyList()).build();
    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .uuid(delegateId)
                            .status(ENABLED)
                            .lastHeartBeat(clock.millis())
                            .supportedTaskTypes(Arrays.asList(TaskType.SCRIPT.name()))
                            .build();

    BatchDelegateSelectionLog batch = Mockito.mock(BatchDelegateSelectionLog.class);
    // Test matching mustExecuteOnDelegateId
    when(delegateCache.get(accountId, delegateId, false)).thenReturn(delegate);

    DelegateEntityOwner orgOwner = DelegateEntityOwner.builder().identifier("o1").build();
    DelegateEntityOwner projectOwner = DelegateEntityOwner.builder().identifier("o1/p1").build();

    Map<String, String> noSetupAbstractions = ImmutableMap.of();
    Map<String, String> orgSetupAbstractions = ImmutableMap.of("owner", "o1");
    Map<String, String> orgLikeSetupAbstractions = ImmutableMap.of("owner", "o1like");

    Map<String, String> projectSetupAbstractions = ImmutableMap.of("owner", "o1/p1");
    Map<String, String> projectLikeSetupAbstractions = ImmutableMap.of("owner", "o1/p1like");

    canAssignOwnerAssert(delegateTask, batch, delegate, null, null, true);
    canAssignOwnerAssert(delegateTask, batch, delegate, null, noSetupAbstractions, true);
    canAssignOwnerAssert(delegateTask, batch, delegate, null, orgSetupAbstractions, true);
    canAssignOwnerAssert(delegateTask, batch, delegate, null, projectSetupAbstractions, true);

    canAssignOwnerAssert(delegateTask, batch, delegate, orgOwner, null, false);
    canAssignOwnerAssert(delegateTask, batch, delegate, orgOwner, noSetupAbstractions, false);
    canAssignOwnerAssert(delegateTask, batch, delegate, orgOwner, orgSetupAbstractions, true);
    canAssignOwnerAssert(delegateTask, batch, delegate, orgOwner, projectSetupAbstractions, true);
    canAssignOwnerAssert(delegateTask, batch, delegate, orgOwner, orgLikeSetupAbstractions, false);

    canAssignOwnerAssert(delegateTask, batch, delegate, projectOwner, null, false);
    canAssignOwnerAssert(delegateTask, batch, delegate, projectOwner, noSetupAbstractions, false);
    canAssignOwnerAssert(delegateTask, batch, delegate, projectOwner, orgSetupAbstractions, false);
    canAssignOwnerAssert(delegateTask, batch, delegate, projectOwner, projectSetupAbstractions, true);
    canAssignOwnerAssert(delegateTask, batch, delegate, projectOwner, projectLikeSetupAbstractions, false);

    // testing above valid scenarios with wrong values of project / org
    Map<String, String> invalidOrgSetupAbstractions = ImmutableMap.of("owner", "o2");
    Map<String, String> invalidProjectSetupAbstractions = ImmutableMap.of("owner", "o2/p2");
    canAssignOwnerAssert(delegateTask, batch, delegate, orgOwner, invalidOrgSetupAbstractions, false);
    canAssignOwnerAssert(delegateTask, batch, delegate, projectOwner, invalidProjectSetupAbstractions, false);
  }

  private void canAssignOwnerAssert(DelegateTask delegateTask, BatchDelegateSelectionLog batch, Delegate delegate,
      DelegateEntityOwner delegateEntityOwner, Map<String, String> setupAbstractions, boolean canAssign) {
    delegate.setOwner(delegateEntityOwner);
    delegateTask.setSetupAbstractions(setupAbstractions);
    assertThat(assignDelegateService.canAssign(batch, delegate.getUuid(), delegateTask)).isEqualTo(canAssign);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void ValidateCriteria() {
    Optional<DelegateConnectionResult> nullResult = Optional.empty();
    assertThat(AssignDelegateServiceImpl.shouldValidateCriteria(nullResult, 0)).isTrue();

    Optional<DelegateConnectionResult> trueResult =
        Optional.of(DelegateConnectionResult.builder().validated(true).lastUpdatedAt(10).build());
    assertThat(AssignDelegateServiceImpl.shouldValidateCriteria(trueResult, WHITELIST_TTL)).isFalse();
    assertThat(AssignDelegateServiceImpl.shouldValidateCriteria(trueResult, WHITELIST_TTL + 20)).isTrue();

    Optional<DelegateConnectionResult> falseResult =
        Optional.of(DelegateConnectionResult.builder().validated(false).lastUpdatedAt(10).build());
    assertThat(AssignDelegateServiceImpl.shouldValidateCriteria(falseResult, BLACKLIST_TTL)).isFalse();
    assertThat(AssignDelegateServiceImpl.shouldValidateCriteria(falseResult, BLACKLIST_TTL + 20)).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testGetAccountDelegates() throws ExecutionException {
    String accountId = generateUuid();

    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .uuid(generateUuid())
                            .status(ENABLED)
                            .lastHeartBeat(clock.millis())
                            .build();
    when(accountDelegatesCache.get(accountId)).thenReturn(Collections.singletonList(delegate));

    List<Delegate> accountDelegates = assignDelegateService.getAccountDelegates(accountId);

    assertThat(accountDelegates).hasSize(1);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testGetAccountDelegatesWithExecutionException() throws ExecutionException {
    String accountId = generateUuid();

    when(accountDelegatesCache.get(accountId)).thenThrow(ExecutionException.class);

    assertThat(assignDelegateService.getAccountDelegates(accountId)).isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testGetAccountDelegatesWithInvalidCacheLoadException() throws ExecutionException {
    String accountId = generateUuid();

    when(accountDelegatesCache.get(accountId)).thenThrow(InvalidCacheLoadException.class);

    assertThat(assignDelegateService.getAccountDelegates(accountId)).isEmpty();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testDelegateSupportsGivenTaskType() {
    String delegateId1 = "delegateId1";
    String delegateId2 = "delegateId2";

    Delegate delegate1 = Delegate.builder()
                             .uuid(delegateId1)
                             .delegateName("delegateName")
                             .accountId("accountId")
                             .lastHeartBeat(System.currentTimeMillis())
                             .supportedTaskTypes(Arrays.asList(TaskType.SCRIPT.name(), TaskType.HTTP.name()))
                             .build();

    Delegate delegate2 = Delegate.builder()
                             .uuid(delegateId2)
                             .delegateName("delegateName")
                             .accountId("accountId")
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();

    persistence.save(delegate1);
    persistence.save(delegate2);
    when(delegateCache.get("accountId", delegateId1, false)).thenReturn(delegate1);
    when(delegateCache.get("accountId", delegateId2, false)).thenReturn(delegate2);
    DelegateTask asyncTask = DelegateTask.builder()
                                 .uuid(generateUuid())
                                 .accountId("accountId")
                                 .data(TaskData.builder().async(true).taskType(TaskType.SCRIPT.name()).build())
                                 .build();
    DelegateTask syncTask = DelegateTask.builder()
                                .uuid(generateUuid())
                                .accountId("accountId")
                                .data(TaskData.builder().async(false).taskType(TaskType.HTTP.name()).build())
                                .build();
    DelegateTask wrongAsyncTask =
        DelegateTask.builder()
            .uuid(generateUuid())
            .accountId("accountId")
            .data(TaskData.builder().async(true).taskType(TaskType.SPOTINST_COMMAND_TASK.name()).build())
            .build();

    DelegateTask wrongSyncTask =
        DelegateTask.builder()
            .uuid(generateUuid())
            .accountId("accountId")
            .data(TaskData.builder().async(false).taskType(TaskType.SPOTINST_COMMAND_TASK.name()).build())
            .build();

    assertThat(assignDelegateService.canAssign(null, delegateId1, asyncTask)).isTrue();
    assertThat(assignDelegateService.canAssign(null, delegateId1, syncTask)).isTrue();
    assertThat(assignDelegateService.canAssign(null, delegateId2, asyncTask)).isFalse();
    assertThat(assignDelegateService.canAssign(null, delegateId2, syncTask)).isFalse();
    assertThat(assignDelegateService.canAssign(null, delegateId1, wrongAsyncTask)).isFalse();
    assertThat(assignDelegateService.canAssign(null, delegateId1, wrongSyncTask)).isFalse();
  }
}