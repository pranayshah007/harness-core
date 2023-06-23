/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateInstanceStatus.ENABLED;
import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.delegate.task.TaskFailureReason.EXPIRED;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARPIT;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.GAURAV_NANDA;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SANJA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VUK;
import static io.harness.utils.DelegateOwner.NG_DELEGATE_ENABLED_CONSTANT;
import static io.harness.utils.DelegateOwner.NG_DELEGATE_OWNER_CONSTANT;

import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.service.impl.AssignDelegateServiceImpl.BLACKLIST_TTL;
import static software.wings.service.impl.AssignDelegateServiceImpl.ERROR_MESSAGE;
import static software.wings.service.impl.AssignDelegateServiceImpl.MAX_DELEGATE_LAST_HEARTBEAT;
import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;
import static software.wings.service.impl.AssignDelegateServiceImpl.WHITELIST_TTL;
import static software.wings.service.impl.AssignDelegateServiceImplTest.CriteriaType.MATCHING_CRITERIA;
import static software.wings.service.impl.AssignDelegateServiceImplTest.CriteriaType.NOT_MATCHING_CRITERIA;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.DELEGATE_ID;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskBuilder;
import io.harness.category.element.UnitTests;
import io.harness.common.NGTaskType;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfileScopingRule;
import io.harness.delegate.beans.DelegateScope;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateCache;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.validation.core.DelegateConnectionResult;
import software.wings.delegatetasks.validation.core.DelegateConnectionResult.DelegateConnectionResultBuilder;
import software.wings.delegatetasks.validation.core.DelegateConnectionResult.DelegateConnectionResultKeys;
import software.wings.service.impl.aws.model.AwsIamListInstanceRolesRequest;
import software.wings.service.impl.aws.model.AwsIamRequest;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;

import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import java.util.LinkedList;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Sets;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

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
  private static final String UNREACHABLE_URL = "http://unreachableUrl.com";

  private static final List<String> supportedTasks = Arrays.stream(TaskType.values()).map(Enum::name).collect(toList());
  private static final String expectedErrorMessage =
      "None of the active delegates were eligible to complete the task.\n\n ===> hostname: Unknown error\n";

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

      assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTaskBuilder.build()))
          .isEqualTo(test.isAssignable());
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

    assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTaskBuilder.build())).isEqualTo(true);

    delegate = delegateBuilder
                   .includeScopes(ImmutableList.of(
                       DelegateScope.builder().environments(ImmutableList.of("ENVIRONMENT_ID")).build()))
                   .build();
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);

    delegateTaskBuilder.setupAbstraction("envId", SCOPE_WILDCARD);

    assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTaskBuilder.build())).isEqualTo(true);
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

    assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTaskBuilder.build())).isEqualTo(true);

    delegate = delegateBuilder
                   .excludeScopes(ImmutableList.of(
                       DelegateScope.builder().environments(ImmutableList.of("ENVIRONMENT_ID")).build()))
                   .build();
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);

    delegateTaskBuilder.setupAbstraction("enviId", SCOPE_WILDCARD);

    assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTaskBuilder.build())).isEqualTo(true);
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

      DelegateProfile returnDelegateProfile =
          StringUtils.isEmpty(test.getDelegate().getDelegateProfileId()) ? null : delegateProfile;
      when(delegateCache.getDelegateProfile(
               test.getDelegate().getAccountId(), test.getDelegate().getDelegateProfileId()))
          .thenReturn(returnDelegateProfile);

      assertThat(assignDelegateService.canAssign(test.getDelegate().getUuid(), test.getTask()))
          .isEqualTo(test.isAssignable());
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
    assertThat(assignDelegateService.canAssign(delegateWithNonExistingProfile.getUuid(),
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
      when(delegateService.retrieveDelegateSelectors(delegate, true))
          .thenReturn(delegate.getTags() == null ? new HashSet<>() : new HashSet<>(test.getDelegateTags()));

      DelegateTask delegateTask = delegateTaskBuilder.executionCapabilities(test.getExecutionCapabilities()).build();
      assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTask)).isEqualTo(test.isAssignable());
    }

    delegateTaskBuilder.setupAbstraction("envId", "ENV_ID");
    delegateBuilder.excludeScopes(
        ImmutableList.of(DelegateScope.builder().environmentTypes(ImmutableList.of(PROD)).build()));

    for (TagTestData test : tests) {
      Delegate delegate = delegateBuilder.tags(test.getDelegateTags()).build();
      when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
      when(delegateService.retrieveDelegateSelectors(delegate, true))
          .thenReturn(delegate.getTags() == null ? new HashSet<>() : new HashSet<>(test.getDelegateTags()));

      DelegateTask delegateTask = delegateTaskBuilder.executionCapabilities(test.getExecutionCapabilities()).build();
      assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTask)).isFalse();
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
    when(delegateService.retrieveDelegateSelectors(any(Delegate.class), eq(true)))
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
      assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTask)).isEqualTo(test.isAssignable());
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
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("DELEGATE_ID")));
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
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(delegate.getUuid())));
    when(accountDelegatesCache.get("ACCOUNT_ID")).thenReturn(asList(delegate));
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    List<String> delegateIds = assignDelegateService.connectedWhitelistedDelegates(delegateTask);
    assertThat(delegateIds).containsExactly(delegate.getUuid());
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
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTask)).isTrue();
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
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTask)).isTrue();
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
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTask)).isTrue();
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
    when(infrastructureMappingService.get("APP_ID", "infraMapping_Id")).thenReturn(infrastructureMapping);
    when(delegateCache.get("ACCOUNT_ID", "DELEGATE_ID", false)).thenReturn(delegate);
    assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTask)).isTrue();

    assertThat(assignDelegateService.canAssign("DELEGATE_ID", delegateTask2)).isFalse();
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

    List<String> activeDelegates = assignDelegateService.retrieveActiveDelegates(accountId, null);
    assertThat(activeDelegates).isNotNull();
    assertThat(activeDelegates.size()).isEqualTo(2);
    assertThat(activeDelegates.containsAll(asList(activeDelegate1Id, activeDelegate2Id))).isTrue();

    activeDelegate1.setNg(true);
    activeDelegates = assignDelegateService.retrieveActiveDelegates(accountId, DelegateTask.builder().build());
    assertThat(activeDelegates).isNotNull();
    assertThat(activeDelegates.size()).isEqualTo(1);
    assertThat(activeDelegates).containsExactly(activeDelegate2Id);
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
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetActiveDelegateAssignmentErrorMessageSelectionLogsAvailableForExpiredTask()
      throws ExecutionException {
    String accountId = generateUuid();
    String uuid = generateUuid();
    String delegateId = generateUuid();
    Delegate activeDelegate1 =
        createDelegateBuilder().accountId(accountId).uuid(delegateId).hostName("hostname").build();
    when(accountDelegatesCache.get(accountId)).thenReturn(asList(activeDelegate1));
    when(delegateCache.get(accountId, delegateId, false)).thenReturn(activeDelegate1);

    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(uuid)
                                    .accountId(accountId)
                                    .delegateId(delegateId)
                                    .eligibleToExecuteDelegateIds(new LinkedList<>())
                                    .data(TaskData.builder().taskType(TaskType.NOTIFY_SLACK.name()).build())
                                    .build();

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

    String errorMessage = assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, delegateTask);

    assertThat(errorMessage).isNotNull();
    assertThat(errorMessage).isEqualTo(expectedErrorMessage);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetActiveDelegateAssignmentErrorMessageSelectionLogsAvailableForExpiredV2Task()
      throws ExecutionException {
    String accountId = generateUuid();
    String uuid = generateUuid();
    String delegateId = generateUuid();
    Delegate activeDelegate1 =
        createDelegateBuilder().accountId(accountId).uuid(delegateId).hostName("hostname").build();
    when(accountDelegatesCache.get(accountId)).thenReturn(asList(activeDelegate1));
    when(delegateCache.get(accountId, delegateId, false)).thenReturn(activeDelegate1);

    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(uuid)
                                    .accountId(accountId)
                                    .delegateId(delegateId)
                                    .eligibleToExecuteDelegateIds(new LinkedList<>())
                                    .taskDataV2(TaskDataV2.builder().taskType(TaskType.NOTIFY_SLACK.name()).build())
                                    .build();

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

    String errorMessage = assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, delegateTask);

    assertThat(errorMessage).isNotNull();
    assertThat(errorMessage).isEqualTo(expectedErrorMessage);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetActiveDelegateAssignmentErrorMessageSelectionLogsAvailableForOtherThanExpiredV2Task() {
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
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void testGetActiveDelegateAssignmentErrorMessageWithOneDelegateFailingCapabilityCheck()
      throws ExecutionException {
    // Arrange
    final String accountId = generateUuid();
    final String eligibleDelegateId = generateUuid();
    final String eligibleDelegateHostName = "eligibleDelegateHostName";

    Delegate eligibleDelegate = Delegate.builder()
                                    .uuid(eligibleDelegateId)
                                    .hostName(eligibleDelegateHostName)
                                    .status(ENABLED)
                                    .lastHeartBeat(clock.millis())
                                    .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                                    .build();
    when(accountDelegatesCache.get(accountId)).thenReturn(Arrays.asList(eligibleDelegate));
    when(delegateCache.get(accountId, eligibleDelegateId, false)).thenReturn(eligibleDelegate);

    DelegateTask taskWithCapability = DelegateTask.builder()
                                          .accountId(accountId)
                                          .description("HTTP task")
                                          .data(TaskData.builder().taskType(TaskType.HTTP.name()).build())
                                          .build();
    taskWithCapability.setExecutionCapabilities(
        Arrays.asList(HttpConnectionExecutionCapability.builder().url(UNREACHABLE_URL).build()));
    taskWithCapability.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(eligibleDelegateId)));

    DelegateConnectionResult connectionResult =
        DelegateConnectionResult.builder().accountId(accountId).criteria(UNREACHABLE_URL).build();
    connectionResult.setDelegateId(eligibleDelegateId);
    connectionResult.setValidated(false);
    persistence.save(connectionResult);

    // Act
    String errorMessage = assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, taskWithCapability);

    // Assert
    assertThat(errorMessage).isNotNull();
    assertThat(errorMessage)
        .isEqualTo("None of the active delegates were eligible to complete the task.\n\n ===> "
            + eligibleDelegateHostName + ": \"Missing Capabilities: [" + UNREACHABLE_URL + "]\"\n");
  }

  @Test
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void testGetActiveDelegateAssignmentErrorMessageWithTwoDelegatesFailingCapabilityCheck()
      throws ExecutionException {
    // Arrange
    final String accountId = generateUuid();

    final String delegate1Id = generateUuid();
    final String delegate1HostName = "DelegateOne";
    final String delegate2Id = generateUuid();
    final String delegate2HostName = "DelegateTwo";

    Delegate delegate1 = Delegate.builder()
                             .uuid(delegate1Id)
                             .hostName(delegate1HostName)
                             .status(ENABLED)
                             .lastHeartBeat(clock.millis())
                             .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                             .build();
    Delegate delegate2 = Delegate.builder()
                             .uuid(delegate2Id)
                             .hostName(delegate2HostName)
                             .status(ENABLED)
                             .lastHeartBeat(clock.millis())
                             .supportedTaskTypes(Arrays.asList(TaskType.HTTP.name()))
                             .build();
    when(accountDelegatesCache.get(accountId)).thenReturn(Arrays.asList(delegate1, delegate2));
    when(delegateCache.get(accountId, delegate1Id, false)).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2Id, false)).thenReturn(delegate2);

    DelegateTask taskWithCapability = DelegateTask.builder()
                                          .accountId(accountId)
                                          .description("HTTP task")
                                          .data(TaskData.builder().taskType(TaskType.HTTP.name()).build())
                                          .build();
    taskWithCapability.setExecutionCapabilities(
        Arrays.asList(HttpConnectionExecutionCapability.builder().url(UNREACHABLE_URL).build()));
    taskWithCapability.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(delegate1Id, delegate2Id)));

    DelegateConnectionResult delegate1ConnectionResult =
        DelegateConnectionResult.builder().accountId(accountId).criteria(UNREACHABLE_URL).build();
    delegate1ConnectionResult.setDelegateId(delegate1Id);
    delegate1ConnectionResult.setValidated(false);
    persistence.save(delegate1ConnectionResult);

    DelegateConnectionResult delegate2ConnectionResult =
        DelegateConnectionResult.builder().accountId(accountId).criteria(UNREACHABLE_URL).build();
    delegate2ConnectionResult.setDelegateId(delegate2Id);
    delegate2ConnectionResult.setValidated(false);
    persistence.save(delegate2ConnectionResult);

    // Act
    String errorMessage = assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, taskWithCapability);

    // Assert
    assertThat(errorMessage).isNotNull();
    assertThat(errorMessage)
        .isEqualTo("None of the active delegates were eligible to complete the task.\n\n"
            + " ===> " + delegate1HostName + ": \"Missing Capabilities: [" + UNREACHABLE_URL + "]\"\n"
            + " ===> " + delegate2HostName + ": \"Missing Capabilities: [" + UNREACHABLE_URL + "]\"\n");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  @Ignore("Platform Team will fix later")
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
    task.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(delegate2.getUuid())));
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
                                    .status(DelegateTask.Status.QUEUED)
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

    when(delegateCache.get(accountId, delegateId, false)).thenReturn(delegate);

    // Test delegate cg and task cg
    canAssignCgNgAssert(delegateTask, delegate, false, null, true);
    canAssignCgNgAssert(delegateTask, delegate, false, ImmutableMap.of(), true);
    canAssignCgNgAssert(delegateTask, delegate, false, ImmutableMap.of("k1", "v1"), true);
    canAssignCgNgAssert(delegateTask, delegate, false, ImmutableMap.of("ng", "FALSE"), true);
    canAssignCgNgAssert(delegateTask, delegate, false, ImmutableMap.of("ng", "false"), true);
    canAssignCgNgAssert(delegateTask, delegate, false, ImmutableMap.of("ng", "invalidValue"), true);

    // Test delegate ng and task ng
    canAssignCgNgAssert(delegateTask, delegate, true, ImmutableMap.of("ng", "TRUE"), true);
    canAssignCgNgAssert(delegateTask, delegate, true, ImmutableMap.of("ng", "true"), true);

    // Test other non-matching cases
    canAssignCgNgAssert(delegateTask, delegate, true, null, false);
    canAssignCgNgAssert(delegateTask, delegate, true, ImmutableMap.of(), false);
    canAssignCgNgAssert(delegateTask, delegate, true, ImmutableMap.of("k1", "v1"), false);
    canAssignCgNgAssert(delegateTask, delegate, false, ImmutableMap.of("ng", "TRUE"), false);
    canAssignCgNgAssert(delegateTask, delegate, false, ImmutableMap.of("ng", "true"), false);
    canAssignCgNgAssert(delegateTask, delegate, true, ImmutableMap.of("ng", "invalidValue"), false);
  }

  private void canAssignCgNgAssert(DelegateTask delegateTask, Delegate delegate, boolean isDelegateNg,
      Map<String, String> setupAbstractions, boolean canAssign) {
    delegate.setNg(isDelegateNg);
    delegateTask.setSetupAbstractions(setupAbstractions);
    assertThat(assignDelegateService.canAssign(delegate.getUuid(), delegateTask)).isEqualTo(canAssign);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCanAssignOwner() {
    TaskData taskData = TaskData.builder().taskType(TaskType.SCRIPT.name()).build();
    String accountId = generateUuid();
    String delegateId = generateUuid();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .status(DelegateTask.Status.QUEUED)
                                    .data(taskData)
                                    .executionCapabilities(emptyList())
                                    .build();
    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .uuid(delegateId)
                            .status(ENABLED)
                            .lastHeartBeat(clock.millis())
                            .supportedTaskTypes(Arrays.asList(TaskType.SCRIPT.name()))
                            .build();

    // Test matching mustExecuteOnDelegateId
    when(delegateCache.get(accountId, delegateId, false)).thenReturn(delegate);

    DelegateEntityOwner orgOwner = DelegateEntityOwner.builder().identifier("o1").build();
    DelegateEntityOwner projectOwner = DelegateEntityOwner.builder().identifier("o1/p1").build();

    Map<String, String> noSetupAbstractions = ImmutableMap.of();
    Map<String, String> orgSetupAbstractions = ImmutableMap.of("owner", "o1");
    Map<String, String> orgLikeSetupAbstractions = ImmutableMap.of("owner", "o1like");

    Map<String, String> projectSetupAbstractions = ImmutableMap.of("owner", "o1/p1");
    Map<String, String> projectLikeSetupAbstractions = ImmutableMap.of("owner", "o1/p1like");

    canAssignOwnerAssert(delegateTask, delegate, null, null, true);
    canAssignOwnerAssert(delegateTask, delegate, null, noSetupAbstractions, true);
    canAssignOwnerAssert(delegateTask, delegate, null, orgSetupAbstractions, true);
    canAssignOwnerAssert(delegateTask, delegate, null, projectSetupAbstractions, true);

    canAssignOwnerAssert(delegateTask, delegate, orgOwner, null, false);
    canAssignOwnerAssert(delegateTask, delegate, orgOwner, noSetupAbstractions, false);
    canAssignOwnerAssert(delegateTask, delegate, orgOwner, orgSetupAbstractions, true);
    canAssignOwnerAssert(delegateTask, delegate, orgOwner, projectSetupAbstractions, true);
    canAssignOwnerAssert(delegateTask, delegate, orgOwner, orgLikeSetupAbstractions, false);

    canAssignOwnerAssert(delegateTask, delegate, projectOwner, null, false);
    canAssignOwnerAssert(delegateTask, delegate, projectOwner, noSetupAbstractions, false);
    canAssignOwnerAssert(delegateTask, delegate, projectOwner, orgSetupAbstractions, false);
    canAssignOwnerAssert(delegateTask, delegate, projectOwner, projectSetupAbstractions, true);
    canAssignOwnerAssert(delegateTask, delegate, projectOwner, projectLikeSetupAbstractions, false);

    // testing above valid scenarios with wrong values of project / org
    Map<String, String> invalidOrgSetupAbstractions = ImmutableMap.of("owner", "o2");
    Map<String, String> invalidProjectSetupAbstractions = ImmutableMap.of("owner", "o2/p2");
    canAssignOwnerAssert(delegateTask, delegate, orgOwner, invalidOrgSetupAbstractions, false);
    canAssignOwnerAssert(delegateTask, delegate, projectOwner, invalidProjectSetupAbstractions, false);
  }

  private void canAssignOwnerAssert(DelegateTask delegateTask, Delegate delegate,
      DelegateEntityOwner delegateEntityOwner, Map<String, String> setupAbstractions, boolean canAssign) {
    delegate.setOwner(delegateEntityOwner);
    delegateTask.setSetupAbstractions(setupAbstractions);
    assertThat(assignDelegateService.canAssign(delegate.getUuid(), delegateTask)).isEqualTo(canAssign);
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
                                 .status(DelegateTask.Status.QUEUED)
                                 .accountId("accountId")
                                 .data(TaskData.builder().async(true).taskType(TaskType.SCRIPT.name()).build())
                                 .build();
    DelegateTask syncTask = DelegateTask.builder()
                                .uuid(generateUuid())
                                .accountId("accountId")
                                .status(DelegateTask.Status.QUEUED)
                                .data(TaskData.builder().async(false).taskType(TaskType.HTTP.name()).build())
                                .build();
    DelegateTask wrongAsyncTask =
        DelegateTask.builder()
            .uuid(generateUuid())
            .status(DelegateTask.Status.QUEUED)
            .accountId("accountId")
            .data(TaskData.builder().async(true).taskType(TaskType.SPOTINST_COMMAND_TASK.name()).build())
            .build();

    DelegateTask wrongSyncTask =
        DelegateTask.builder()
            .uuid(generateUuid())
            .status(DelegateTask.Status.QUEUED)
            .accountId("accountId")
            .data(TaskData.builder().async(false).taskType(TaskType.SPOTINST_COMMAND_TASK.name()).build())
            .build();

    assertThat(assignDelegateService.canAssign(delegateId1, asyncTask)).isTrue();
    assertThat(assignDelegateService.canAssign(delegateId1, syncTask)).isTrue();
    assertThat(assignDelegateService.canAssign(delegateId2, asyncTask)).isFalse();
    assertThat(assignDelegateService.canAssign(delegateId2, syncTask)).isFalse();
    assertThat(assignDelegateService.canAssign(delegateId1, wrongAsyncTask)).isFalse();
    assertThat(assignDelegateService.canAssign(delegateId1, wrongSyncTask)).isFalse();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetAccountEligibleDelegatesToExecuteTask() throws ExecutionException {
    Delegate delegate = createAccountDelegate();
    DelegateTask task = constructDelegateTask(false, Collections.emptySet(), DelegateTask.Status.QUEUED);
    when(accountDelegatesCache.get(ACCOUNT_ID)).thenReturn(asList(delegate));
    when(delegateCache.get(ACCOUNT_ID, delegate.getUuid(), false)).thenReturn(delegate);
    assertThat(assignDelegateService.getEligibleDelegatesToExecuteTask(task)).isNotEmpty();
    assertThat(assignDelegateService.getEligibleDelegatesToExecuteTask(task)).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetAccountEligibleDelegatesToExecuteTaskWithCriteraMatch() throws ExecutionException {
    Delegate delegate = createAccountDelegate();
    DelegateTask task = constructDelegateTask(false, Collections.emptySet(), DelegateTask.Status.QUEUED);

    when(accountDelegatesCache.get(ACCOUNT_ID)).thenReturn(asList(delegate));
    when(delegateCache.get(ACCOUNT_ID, delegate.getUuid(), false)).thenReturn(delegate);
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("https://www.google.com")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    assertThat(assignDelegateService.getEligibleDelegatesToExecuteTask(task)).isNotEmpty();
    assertThat(assignDelegateService.getEligibleDelegatesToExecuteTask(task)).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetEligibleDelegatesToExecuteTaskWithNoActiveDelegates() throws ExecutionException {
    DelegateTask task = constructDelegateTask(false, Collections.emptySet(), DelegateTask.Status.QUEUED);
    assertThat(assignDelegateService.getEligibleDelegatesToExecuteTask(task)).isEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetEligibleDelegatesWhenOwnerMismatch() throws ExecutionException {
    Delegate delegate = createAccountDelegate();
    delegate.setOwner(DelegateEntityOwner.builder().build());
    DelegateTask task = constructDelegateTask(false, Collections.emptySet(), DelegateTask.Status.QUEUED);
    when(accountDelegatesCache.get("ACCOUNT_ID")).thenReturn(asList(delegate));
    when(delegateCache.get("ACCOUNT_ID", delegate.getUuid(), false)).thenReturn(delegate);
    assertThat(assignDelegateService.getEligibleDelegatesToExecuteTask(task)).isEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetConnectedDelegatesFromlist() throws ExecutionException {
    Delegate delegate = createAccountDelegate();
    when(accountDelegatesCache.get("ACCOUNT_ID")).thenReturn(asList(delegate));
    when(delegateCache.get("ACCOUNT_ID", delegate.getUuid(), false)).thenReturn(delegate);
    DelegateTask delegateTask = DelegateTask.builder().accountId(ACCOUNT_ID).build();
    assertThat(assignDelegateService.getConnectedDelegateList(Arrays.asList(delegate.getUuid()), delegateTask))
        .isNotEmpty();
    assertThat(assignDelegateService.getConnectedDelegateList(Arrays.asList(delegate.getUuid()), delegateTask))
        .contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetActiveEligibleDelegatesForTask() throws ExecutionException {
    List<Delegate> delegates = createAccountDelegates();
    when(accountDelegatesCache.get("ACCOUNT_ID")).thenReturn(delegates);
    List<String> delegateIds = delegates.stream().map(delegate -> delegate.getUuid()).collect(toList());
    assertThat(assignDelegateService
                   .fetchActiveDelegates(
                       DelegateTask.builder().nonAssignableDelegates(new HashMap<>()).accountId("ACCOUNT_ID").build())
                   .size()
        == 2);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetActiveNGEligibleDelegatesForTask() throws ExecutionException {
    Delegate delegate = createNGDelegate();
    delegate.setNg(true);
    persistence.save(delegate);
    delegate.setSupportedTaskTypes(Collections.singletonList(NGTaskType.JIRA_TASK_NG.name()));
    // DelegateTask task = constructDelegateTask(false, Collections.emptySet(), DelegateTask.Status.QUEUED);
    DelegateTask task = DelegateTask.builder()
                            .accountId(ACCOUNT_ID)
                            .setupAbstraction(NG_DELEGATE_ENABLED_CONSTANT, "true")
                            .setupAbstraction(NG_DELEGATE_OWNER_CONSTANT, "orgId/projectId")
                            .data(TaskData.builder()
                                      .async(true)
                                      .taskType(NGTaskType.JIRA_TASK_NG.name())
                                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                      .build())
                            .build();
    when(accountDelegatesCache.get(ACCOUNT_ID)).thenReturn(asList(delegate));
    when(delegateCache.get(ACCOUNT_ID, delegate.getUuid(), false)).thenReturn(delegate);

    assertThat(assignDelegateService.getEligibleDelegatesToExecuteTask(task)).isNotEmpty();
    assertThat(assignDelegateService.getEligibleDelegatesToExecuteTask(task)).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetActiveCGEligibleDelegatesForTask() throws ExecutionException {
    Delegate delegate = createNGDelegate();
    delegate.setNg(false);
    persistence.save(delegate);
    delegate.setSupportedTaskTypes(Collections.singletonList(NGTaskType.JIRA_TASK_NG.name()));
    // DelegateTask task = constructDelegateTask(false, Collections.emptySet(), DelegateTask.Status.QUEUED);
    DelegateTask task = DelegateTask.builder()
                            .accountId(ACCOUNT_ID)
                            .setupAbstraction(NG_DELEGATE_ENABLED_CONSTANT, "false")
                            .setupAbstraction(NG_DELEGATE_OWNER_CONSTANT, "orgId/projectId")
                            .data(TaskData.builder()
                                      .async(true)
                                      .taskType(NGTaskType.JIRA_TASK_NG.name())
                                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                      .build())
                            .build();
    when(accountDelegatesCache.get(ACCOUNT_ID)).thenReturn(asList(delegate));
    when(delegateCache.get(ACCOUNT_ID, delegate.getUuid(), false)).thenReturn(delegate);

    assertThat(assignDelegateService.getEligibleDelegatesToExecuteTask(task)).isNotEmpty();
    assertThat(assignDelegateService.getEligibleDelegatesToExecuteTask(task)).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAssignSelectorsNonMatching() throws ExecutionException {
    Delegate delegate = createNGDelegate();
    delegate.setTags(Arrays.asList("sel1"));
    persistence.save(delegate);

    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);

    Set<String> selectors1 = Stream.of("sel1").collect(Collectors.toSet());
    Set<String> selectors2 = Stream.of("sel2").collect(Collectors.toSet());

    SelectorCapability selectorCapability1 =
        SelectorCapability.builder().selectors(selectors1).selectorOrigin("stage").build();
    SelectorCapability selectorCapability2 =
        SelectorCapability.builder().selectors(selectors2).selectorOrigin("pipeline").build();

    List<ExecutionCapability> executionCapabilityList = asList(selectorCapability1, selectorCapability2);

    assertThat(assignDelegateService.canAssignSelectors(delegate, executionCapabilityList)).isFalse();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAssignSelectorsMatching() throws ExecutionException {
    Delegate delegate = createNGDelegate();
    delegate.setTags(Arrays.asList("sel1", "sel2"));
    persistence.save(delegate);

    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);

    Set<String> selectors1 = Stream.of("sel1").collect(Collectors.toSet());
    Set<String> selectors2 = Stream.of("sel2").collect(Collectors.toSet());

    SelectorCapability selectorCapability1 =
        SelectorCapability.builder().selectors(selectors1).selectorOrigin("stage").build();
    SelectorCapability selectorCapability2 =
        SelectorCapability.builder().selectors(selectors2).selectorOrigin("pipeline").build();

    List<ExecutionCapability> executionCapabilityList = asList(selectorCapability1, selectorCapability2);

    when(delegateService.retrieveDelegateSelectors(delegate, true)).thenReturn(Sets.newHashSet(delegate.getTags()));

    assertThat(assignDelegateService.canAssignSelectors(delegate, executionCapabilityList)).isTrue();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAssignSelectorsWithOnlyConnectorSelector() throws ExecutionException {
    Delegate delegate = createNGDelegate();
    delegate.setTags(Arrays.asList("sel1"));
    persistence.save(delegate);

    Set<String> selectors1 = Stream.of("sel1").collect(Collectors.toSet());

    SelectorCapability selectorCapability1 =
        SelectorCapability.builder().selectors(selectors1).selectorOrigin("connector").build();
    List<ExecutionCapability> executionCapabilityList = asList(selectorCapability1);

    when(delegateService.retrieveDelegateSelectors(delegate, true)).thenReturn(Sets.newHashSet(delegate.getTags()));

    assertThat(assignDelegateService.canAssignSelectors(delegate, executionCapabilityList)).isTrue();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testConnectorSelectorIgnoredWithSelectorsAtAllLevel() throws ExecutionException {
    Delegate delegate = createNGDelegate();
    delegate.setTags(Arrays.asList("sel1", "sel2", "sel3", "sel4", "sel41", "sel5"));
    persistence.save(delegate);

    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);

    Set<String> selectors_step = Stream.of("sel1").collect(Collectors.toSet());
    Set<String> selectors_step_group = Stream.of("sel2").collect(Collectors.toSet());
    Set<String> selectors_stage = Stream.of("sel3").collect(Collectors.toSet());
    Set<String> selectors_pipeline = Stream.of("sel4", "sel41").collect(Collectors.toSet());
    Set<String> selectors_connector = Stream.of("sel5").collect(Collectors.toSet());

    SelectorCapability selectorCapability_step =
        SelectorCapability.builder().selectors(selectors_step).selectorOrigin("step").build();
    SelectorCapability selectorCapability_step_group =
        SelectorCapability.builder().selectors(selectors_step_group).selectorOrigin("stepGroup").build();
    SelectorCapability selectorCapability_stage =
        SelectorCapability.builder().selectors(selectors_stage).selectorOrigin("stage").build();
    SelectorCapability selectorCapability_pipeline =
        SelectorCapability.builder().selectors(selectors_pipeline).selectorOrigin("pipeline").build();
    SelectorCapability selectorCapability_connector =
        SelectorCapability.builder().selectors(selectors_connector).selectorOrigin("connector").build();
    List<ExecutionCapability> executionCapabilityList = asList(selectorCapability_step, selectorCapability_step_group,
        selectorCapability_stage, selectorCapability_pipeline, selectorCapability_connector);

    when(delegateService.retrieveDelegateSelectors(delegate, true)).thenReturn(Sets.newHashSet(delegate.getTags()));

    assertThat(assignDelegateService.canAssignSelectors(delegate, executionCapabilityList)).isTrue();
  }
  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateGroupWhitelisting() throws ExecutionException {
    String accountId = generateUuid();
    // create 2 delegate replicas with same group
    List<Delegate> delegates = createDelegateReplicas(accountId);
    Delegate delegate1 = delegates.get(0);
    Delegate delegate2 = delegates.get(1);

    DelegateTask delegateTask = getDelegateTaskWithCapabilities(accountId);

    // set delegate connection result for delegate1
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate1.getUuid())
                                                    .lastUpdatedAt(currentTimeMillis() - TimeUnit.HOURS.toMillis(1))
                                                    .criteria("https//aws.amazon.com")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate1.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    when(delegateCache.get(accountId, delegate2.getUuid(), false)).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate1.getUuid(), false)).thenReturn(delegate1);
    when(delegateCache.getDelegatesForGroup(accountId, delegate1.getDelegateGroupId()))
        .thenReturn(Lists.newArrayList(delegate1, delegate2));
    // verify delegate2 is not whitelisted by itself
    assertThat(assignDelegateService.isWhitelisted(delegateTask, delegate2.getUuid())).isFalse();
    // verify delegate group whitelisting for delegate2 return true as delegate1 and delegate2 belongs to same group.
    assertThat(assignDelegateService.isDelegateGroupWhitelisted(delegateTask, delegate2.getUuid())).isTrue();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateGroupWhitelisting_noDelegatesInGroupWhitelisted() throws ExecutionException {
    String accountId = generateUuid();
    // create 2 delegate replicas with same group
    List<Delegate> delegates = createDelegateReplicas(accountId);
    Delegate delegate1 = delegates.get(0);
    Delegate delegate2 = delegates.get(1);

    DelegateTask delegateTask = getDelegateTaskWithCapabilities(accountId);

    when(delegateCache.get(accountId, delegate2.getUuid(), false)).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate1.getUuid(), false)).thenReturn(delegate1);
    when(delegateCache.getDelegatesForGroup(accountId, delegate1.getDelegateGroupId()))
        .thenReturn(Lists.newArrayList(delegate1, delegate2));

    // verify delegate group is not whitelisted, as both delegate1 or delegate2 are not whitelisted
    assertThat(assignDelegateService.isDelegateGroupWhitelisted(delegateTask, delegate2.getUuid())).isFalse();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateGroupWhitelisting_withExpiredConnectionResult() throws ExecutionException {
    String accountId = generateUuid();
    // create 2 delegate replicas with same group
    List<Delegate> delegates = createDelegateReplicas(accountId);
    Delegate delegate1 = delegates.get(0);
    Delegate delegate2 = delegates.get(1);

    DelegateTask delegateTask = getDelegateTaskWithCapabilities(accountId);

    // set delegate connection result for delegate1, more than 6 hours
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate1.getUuid())
                                                    .lastUpdatedAt(currentTimeMillis() - TimeUnit.HOURS.toMillis(7))
                                                    .criteria("https//aws.amazon.com")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate1.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    when(delegateCache.get(accountId, delegate2.getUuid(), false)).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate1.getUuid(), false)).thenReturn(delegate1);
    when(delegateCache.getDelegatesForGroup(accountId, delegate1.getDelegateGroupId()))
        .thenReturn(Lists.newArrayList(delegate1, delegate2));
    // verify delegate group whitelisting return false as delegate1 connectionResult expired, last updated more than 6
    // hours
    assertThat(assignDelegateService.isDelegateGroupWhitelisted(delegateTask, delegate2.getUuid())).isFalse();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateGroupWhitelisting_withMoreThanOneCapabilities() throws ExecutionException {
    String accountId = generateUuid();
    // create 2 delegate replicas with same group
    List<Delegate> delegates = createDelegateReplicas(accountId);
    Delegate delegate1 = delegates.get(0);
    Delegate delegate2 = delegates.get(1);

    DelegateTask delegateTask = getDelegateTaskWithMoreThanOneCapabilities(accountId);

    // set delegate connection result for delegate1 for both criteria
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate1.getUuid())
                                                    .lastUpdatedAt(currentTimeMillis() - TimeUnit.HOURS.toMillis(1))
                                                    .criteria("https//aws.amazon.com")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate1.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    DelegateConnectionResult connectionResult2 = DelegateConnectionResult.builder()
                                                     .accountId(accountId)
                                                     .delegateId(delegate1.getUuid())
                                                     .lastUpdatedAt(currentTimeMillis() - TimeUnit.HOURS.toMillis(1))
                                                     .criteria("https//google.com")
                                                     .validated(true)
                                                     .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate1.getUuid(), connectionResult2.getCriteria())))
        .thenReturn(of(connectionResult2));

    when(delegateCache.get(accountId, delegate2.getUuid(), false)).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate1.getUuid(), false)).thenReturn(delegate1);
    when(delegateCache.getDelegatesForGroup(accountId, delegate1.getDelegateGroupId()))
        .thenReturn(Lists.newArrayList(delegate1, delegate2));
    // verify delegate2 is not whitelisted by itself
    assertThat(assignDelegateService.isWhitelisted(delegateTask, delegate2.getUuid())).isFalse();
    // verify delegate group whitelisting for delegate2 return true as delegate1 and delegate2 belongs to same group.
    assertThat(assignDelegateService.isDelegateGroupWhitelisted(delegateTask, delegate2.getUuid())).isTrue();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateGroupWhitelisting_withMoreThanOneCapabilitiesWithDifferentDelegate()
      throws ExecutionException {
    String accountId = generateUuid();
    // create 2 delegate replicas with same group
    List<Delegate> delegates = createDelegateReplicas(accountId);
    Delegate delegate1 = delegates.get(0);
    Delegate delegate2 = delegates.get(1);

    DelegateTask delegateTask = getDelegateTaskWithMoreThanOneCapabilities(accountId);

    // set delegateConnectionResultCriteria 1 to delegate1 and  delegateConnectionResultCriteria 2 to delegate2
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate1.getUuid())
                                                    .lastUpdatedAt(currentTimeMillis() - TimeUnit.HOURS.toMillis(1))
                                                    .criteria("https//aws.amazon.com")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate1.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    DelegateConnectionResult connectionResult2 = DelegateConnectionResult.builder()
                                                     .accountId(accountId)
                                                     .delegateId(delegate2.getUuid())
                                                     .lastUpdatedAt(currentTimeMillis() - TimeUnit.HOURS.toMillis(1))
                                                     .criteria("https//google.com")
                                                     .validated(true)
                                                     .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate2.getUuid(), connectionResult2.getCriteria())))
        .thenReturn(of(connectionResult2));

    when(delegateCache.get(accountId, delegate2.getUuid(), false)).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate1.getUuid(), false)).thenReturn(delegate1);
    when(delegateCache.getDelegatesForGroup(accountId, delegate1.getDelegateGroupId()))
        .thenReturn(Lists.newArrayList(delegate1, delegate2));
    // verify delegate2 is not whitelisted by itself
    assertThat(assignDelegateService.isWhitelisted(delegateTask, delegate2.getUuid())).isFalse();
    // verify delegate group whitelisting for delegate2 return false, as both criteras not marching with either of one
    // delegate
    assertThat(assignDelegateService.isDelegateGroupWhitelisted(delegateTask, delegate2.getUuid())).isFalse();
  }

  private DelegateTask constructDelegateTask(boolean async, Set<String> validatingTaskIds, DelegateTask.Status status) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(ACCOUNT_ID)
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, "APP_ID")
            .version(VERSION)
            .data(TaskData.builder()
                      .async(async)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validatingDelegateIds(validatingTaskIds)
            .validationCompleteDelegateIds(ImmutableSet.of(DELEGATE_ID))
            .build();
    return delegateTask;
  }

  private Delegate createAccountDelegate() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .ip("127.0.0.1")
                            .hostName("localhost")
                            .delegateName("testDelegateName")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .supportedTaskTypes(supportedTasks)
                            .lastHeartBeat(System.currentTimeMillis())
                            .build();
    persistence.save(delegate);
    return delegate;
  }

  private List<Delegate> createAccountDelegates() {
    Delegate delegate1 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .delegateName("testDelegateName")
                             .version(VERSION)
                             .status(DelegateInstanceStatus.ENABLED)
                             .supportedTaskTypes(supportedTasks)
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();
    persistence.save(delegate1);
    Delegate delegate2 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .delegateName("testDelegateName")
                             .version(VERSION)
                             .status(DelegateInstanceStatus.ENABLED)
                             .supportedTaskTypes(supportedTasks)
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();
    persistence.save(delegate2);
    Delegate delegate3 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .delegateName("testDelegateName")
                             .version(VERSION)
                             .status(DelegateInstanceStatus.WAITING_FOR_APPROVAL)
                             .supportedTaskTypes(supportedTasks)
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();
    persistence.save(delegate3);
    // non connected delegate
    Delegate delegate4 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .delegateName("testDelegateName")
                             .version(VERSION)
                             .status(DelegateInstanceStatus.ENABLED)
                             .supportedTaskTypes(supportedTasks)
                             .lastHeartBeat(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(26))
                             .build();
    persistence.save(delegate4);
    return Lists.newArrayList(delegate1, delegate2, delegate3, delegate4);
  }

  private Delegate createNGDelegate() {
    Delegate delegate = createDelegateBuilder().build();
    delegate.setOwner(DelegateEntityOwner.builder().identifier("orgId/projectId").build());
    delegate.setNg(true);
    persistence.save(delegate);
    return delegate;
  }

  private List<Delegate> createDelegateReplicas(String accountId) {
    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .name("grp1")
                                      .accountId(accountId)
                                      .ng(true)
                                      .delegateType(KUBERNETES)
                                      .description("description")
                                      .build();
    persistence.save(delegateGroup);

    // 2 delegates with same delegate group
    Delegate delegate1 = createNGDelegate();
    delegate1.setDelegateGroupId(delegateGroup.getUuid());
    delegate1.setAccountId(accountId);
    persistence.save(delegate1);

    Delegate delegate2 = createNGDelegate();
    delegate2.setDelegateGroupId(delegateGroup.getUuid());
    delegate2.setAccountId(accountId);
    persistence.save(delegate2);
    return Lists.newArrayList(delegate1, delegate2);
  }

  private DelegateTask getDelegateTaskWithCapabilities(String accountId) {
    AwsIamRequest request = AwsIamListInstanceRolesRequest.builder().awsConfig(AwsConfig.builder().build()).build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .setupAbstraction(NG_DELEGATE_ENABLED_CONSTANT, "true")
            .setupAbstraction(NG_DELEGATE_OWNER_CONSTANT, "orgId/projectId")
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AWS_IAM_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                      .build())
            .build();

    HttpConnectionExecutionCapability matchingExecutionCapability =
        buildHttpConnectionExecutionCapability("https//aws.amazon.com", null);
    delegateTask.setExecutionCapabilities(Arrays.asList(matchingExecutionCapability));
    return delegateTask;
  }

  private DelegateTask getDelegateTaskWithMoreThanOneCapabilities(String accountId) {
    AwsIamRequest request = AwsIamListInstanceRolesRequest.builder().awsConfig(AwsConfig.builder().build()).build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .setupAbstraction(NG_DELEGATE_ENABLED_CONSTANT, "true")
            .setupAbstraction(NG_DELEGATE_OWNER_CONSTANT, "orgId/projectId")
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AWS_IAM_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                      .build())
            .build();

    HttpConnectionExecutionCapability matchingExecutionCapability =
        buildHttpConnectionExecutionCapability("https//aws.amazon.com", null);
    HttpConnectionExecutionCapability matchingExecutionCapability2 =
        buildHttpConnectionExecutionCapability("https//google.com", null);
    delegateTask.setExecutionCapabilities(Arrays.asList(matchingExecutionCapability, matchingExecutionCapability2));
    return delegateTask;
  }
}
