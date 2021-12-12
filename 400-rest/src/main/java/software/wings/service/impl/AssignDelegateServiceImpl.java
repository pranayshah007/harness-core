package software.wings.service.impl;

import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.task.TaskFailureReason.EXPIRED;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;

import static com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateActivity;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfileScopingRule;
import io.harness.delegate.beans.DelegateScope;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.NgSetupFields;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.TaskFailureReason;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.selection.log.BatchDelegateSelectionLog;
import io.harness.service.dto.RetryDelegate;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateTaskRetryObserver;

import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.validation.DelegateConnectionResult;
import software.wings.delegatetasks.validation.DelegateConnectionResult.DelegateConnectionResultKeys;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("io.harness.beans.EnvironmentType")
@BreakDependencyOn("io.harness.beans.Cd1SetupFields")
@BreakDependencyOn("software.wings.beans.Environment")
@BreakDependencyOn("software.wings.beans.InfrastructureMapping")
@BreakDependencyOn("software.wings.service.intfc.EnvironmentService")
@BreakDependencyOn("software.wings.service.intfc.InfrastructureMappingService")
@OwnedBy(HarnessTeam.DEL)
public class AssignDelegateServiceImpl implements AssignDelegateService, DelegateTaskRetryObserver {
  public static final String SCOPE_WILDCARD = "*";
  private static final SecureRandom random = new SecureRandom();
  public static final long MAX_DELEGATE_LAST_HEARTBEAT = (5 * 60 * 1000L) + (15 * 1000L); // 5 minutes 15 seconds

  public static final String ERROR_MESSAGE =
      "Delegate selection log: Delegate id: %s, Name: %s, Host name: %s, Profile name: %s, %s with note: %s at: %s";

  public static final long WHITELIST_TTL = TimeUnit.HOURS.toMillis(6);
  public static final long BLACKLIST_TTL = TimeUnit.MINUTES.toMillis(5);
  private static final long WHITELIST_REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(10);

  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;
  @Inject private DelegateService delegateService;
  @Inject private EnvironmentService environmentService;
  @Inject private HPersistence persistence;
  @Inject private Injector injector;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private DelegateCache delegateCache;

  private LoadingCache<ImmutablePair<String, String>, Optional<DelegateConnectionResult>>
      delegateConnectionResultCache =
          CacheBuilder.newBuilder()
              .maximumSize(10000)
              .expireAfterWrite(2, TimeUnit.MINUTES)
              .build(new CacheLoader<ImmutablePair<String, String>, Optional<DelegateConnectionResult>>() {
                @Override
                public Optional<DelegateConnectionResult> load(ImmutablePair<String, String> key) {
                  return Optional.ofNullable(persistence.createQuery(DelegateConnectionResult.class)
                                                 .filter(DelegateConnectionResultKeys.delegateId, key.getLeft())
                                                 .filter(DelegateConnectionResultKeys.criteria, key.getRight())
                                                 .get());
                }
              });

  private LoadingCache<String, List<Delegate>> accountDelegatesCache =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(MAX_DELEGATE_LAST_HEARTBEAT / 3, TimeUnit.MILLISECONDS)
          .build(new CacheLoader<String, List<Delegate>>() {
            @Override
            public List<Delegate> load(String accountId) {
              return persistence.createQuery(Delegate.class)
                  .filter(DelegateKeys.accountId, accountId)
                  .field(DelegateKeys.status)
                  .notEqual(DelegateInstanceStatus.DELETED)
                  .project(DelegateKeys.uuid, true)
                  .project(DelegateKeys.lastHeartBeat, true)
                  .project(DelegateKeys.status, true)
                  .project(DelegateKeys.delegateGroupName, true)
                  .project(DelegateKeys.delegateGroupId, true)
                  .project(DelegateKeys.ng, true)
                  .asList();
            }
          });

  @Override
  public boolean canAssign(BatchDelegateSelectionLog batch, String delegateId, DelegateTask task) {
    Delegate delegate = delegateCache.get(task.getAccountId(), delegateId, false);
    if (delegate == null) {
      return false;
    }

    boolean canAssignTaskToDelegate =
        canAssignTaskToDelegate(delegate.getSupportedTaskTypes(), task.getData().getTaskType());
    if (!canAssignTaskToDelegate) {
      log.debug("Delegate {} does not support task {} which is of type {}", delegateId, task.getUuid(),
          task.getData().getTaskType());
      return canAssignTaskToDelegate;
    }

    boolean canAssignCgNg = canAssignCgNg(delegate, task.getSetupAbstractions());
    if (!canAssignCgNg) {
      log.debug("can not assign canAssignCgNg {}", canAssignCgNg);
      return canAssignCgNg;
    }
    boolean canAssignOwner = canAssignOwner(batch, delegate, task.getSetupAbstractions());
    if (!canAssignOwner) {
      log.debug("can not assign canAssignOwner {}", canAssignOwner);
      return canAssignOwner;
    }

    boolean canAssignDelegateScopes = canAssignDelegateScopes(batch, delegate, task);
    if (!canAssignDelegateScopes) {
      log.debug("can not assign canAssignDelegateScopes {}", canAssignDelegateScopes);
      return canAssignDelegateScopes;
    }

    boolean canAssignDelegateProfileScopes =
        canAssignDelegateProfileScopes(batch, delegate, task.getSetupAbstractions());

    if (!canAssignDelegateProfileScopes) {
      log.debug("can not assign canAssignDelegateProfileScopes {}", canAssignDelegateProfileScopes);
      return canAssignDelegateProfileScopes;
    }

    boolean canAssignSelectors = canAssignSelectors(batch, delegate, task.getExecutionCapabilities());
    if (!canAssignSelectors) {
      log.debug("can not assign canAssignSelectors {}", canAssignSelectors);
      return canAssignSelectors;
    }

    delegateSelectionLogsService.logCanAssign(batch, task.getAccountId(), delegateId);
    return true;
  }

  public List<String> extractSelectors(DelegateTask task) {
    Set<String> selectors = new HashSet<>();

    if (isNotEmpty(task.getExecutionCapabilities())) {
      Set<String> selectorsCapability = task.getExecutionCapabilities()
                                            .stream()
                                            .filter(c -> c instanceof SelectorCapability)
                                            .flatMap(c -> ((SelectorCapability) c).getSelectors().stream())
                                            .collect(Collectors.toSet());
      selectors.addAll(selectorsCapability);
    }

    if (isNotEmpty(task.getTags())) {
      selectors.addAll(task.getTags());
    }

    return new ArrayList<>(selectors);
  }

  @Override
  public boolean canAssign(BatchDelegateSelectionLog batch, String delegateId, String accountId, String appId,
      String envId, String infraMappingId, TaskGroup taskGroup, List<ExecutionCapability> executionCapabilities,
      Map<String, String> taskSetupAbstractions) {
    Delegate delegate = delegateCache.get(accountId, delegateId, false);
    if (delegate == null) {
      return false;
    }
    return canAssignCgNg(delegate, taskSetupAbstractions) && canAssignOwner(batch, delegate, taskSetupAbstractions)
        && canAssignDelegateScopes(batch, delegate, appId, envId, infraMappingId, taskGroup)
        && canAssignDelegateProfileScopes(batch, delegate, taskSetupAbstractions)
        && canAssignSelectors(batch, delegate, executionCapabilities);
  }

  /**
   * Method will make sure that CG delegate is being assigned to CG task and NG delegate to NG task
   */
  private boolean canAssignCgNg(Delegate delegate, Map<String, String> taskSetupAbstractions) {
    boolean isDelegateNg = delegate.isNg();
    boolean isTaskNg = !isEmpty(taskSetupAbstractions) && taskSetupAbstractions.get(NgSetupFields.NG) != null
        && Boolean.TRUE.equals(Boolean.valueOf(taskSetupAbstractions.get(NgSetupFields.NG)));

    if (isDelegateNg && isTaskNg || !isDelegateNg && !isTaskNg) {
      return true;
    }

    if (log.isDebugEnabled()) {
      log.debug("CG/NG delegate/task assignment isolation check was negative. Is Delegate NG: {}, Is Task NG: {}",
          isDelegateNg, isTaskNg);
    }
    return false;
  }

  private boolean canAssignOwner(
      BatchDelegateSelectionLog batch, Delegate delegate, Map<String, String> taskSetupAbstractions) {
    DelegateEntityOwner delegateOwner = delegate.getOwner();

    // Account level delegate can handle anything. This is equivalent to CG behavior.
    if (delegateOwner == null) {
      return true;
    }

    // Account level task and delegate with an owner defined
    if (isEmpty(taskSetupAbstractions) || taskSetupAbstractions.get(NgSetupFields.OWNER) == null) {
      delegateSelectionLogsService.logOwnerRuleNotMatched(
          batch, delegate.getAccountId(), delegate.getUuid(), delegateOwner);
      return false;
    }

    String taskOrgIdentifier =
        DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(taskSetupAbstractions.get(NgSetupFields.OWNER));
    String taskProjectIdentifier =
        DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(taskSetupAbstractions.get(NgSetupFields.OWNER));

    String delegateOrgIdentifier =
        DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(delegateOwner.getIdentifier());
    String delegateProjectIdentifier =
        DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(delegateOwner.getIdentifier());

    // Match org. When owner is specified, at org must be there.
    if (!StringUtils.equals(taskOrgIdentifier, delegateOrgIdentifier)) {
      delegateSelectionLogsService.logOwnerRuleNotMatched(
          batch, delegate.getAccountId(), delegate.getUuid(), delegateOwner);

      return false;
    }

    // Match projects: task and delegate are org level ones, project level task and org level delegate, matching task
    // and project level ones
    if (isBlank(taskProjectIdentifier) && isBlank(delegateProjectIdentifier)
        || !isBlank(taskProjectIdentifier) && isBlank(delegateProjectIdentifier)
        || StringUtils.equals(taskProjectIdentifier, delegateProjectIdentifier)) {
      return true;
    }

    delegateSelectionLogsService.logOwnerRuleNotMatched(
        batch, delegate.getAccountId(), delegate.getUuid(), delegateOwner);
    return false;
  }

  private boolean canAssignDelegateScopes(BatchDelegateSelectionLog batch, Delegate delegate, DelegateTask task) {
    TaskGroup taskGroup =
        isNotBlank(task.getData().getTaskType()) ? TaskType.valueOf(task.getData().getTaskType()).getTaskGroup() : null;

    String appId =
        task.getSetupAbstractions() == null ? null : task.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD);
    String envId =
        task.getSetupAbstractions() == null ? null : task.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD);
    String infrastructureMappingId = task.getSetupAbstractions() == null
        ? null
        : task.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD);

    return canAssignDelegateScopes(batch, delegate, appId, envId, infrastructureMappingId, taskGroup);
  }

  @VisibleForTesting
  protected boolean canAssignDelegateProfileScopes(
      BatchDelegateSelectionLog batch, Delegate delegate, Map<String, String> taskSetupAbstractions) {
    DelegateProfile delegateProfile = persistence.get(DelegateProfile.class, delegate.getDelegateProfileId());
    if (delegateProfile == null) {
      log.warn(
          "Delegate profile {} not found. Considering this delegate profile matched", delegate.getDelegateProfileId());
      return true;
    }

    List<DelegateProfileScopingRule> delegateProfileScopingRules = delegateProfile.getScopingRules();

    if (isEmpty(delegateProfileScopingRules)) {
      return true;
    } else if (isEmpty(taskSetupAbstractions)) {
      log.warn(
          "No setup abstractions have been passed in from delegate task, while there are some profile scoping rules. Considering this delegate profile NOT matched");
      return false;
    }

    // All logs with logSequence,so as the map string builder should probably be removed before GA or changed to debug
    // level
    StringBuilder taskSetupAbstractionsPrintable = new StringBuilder();
    for (Map.Entry<String, String> entity : taskSetupAbstractions.entrySet()) {
      taskSetupAbstractionsPrintable.append(entity.getKey() + ":" + entity.getValue() + "; ");
    }
    String logSequence = batch != null && isNotBlank(batch.getTaskId()) ? batch.getTaskId() : generateUuid();
    log.debug("{} - Starting profile scoping rules match with task abstractions {}.", logSequence,
        taskSetupAbstractionsPrintable.toString());

    Set<String> failedRulesDescriptions = new HashSet<>();
    for (DelegateProfileScopingRule scopingRule : delegateProfileScopingRules) {
      boolean scopingRuleMatched = true;

      for (Map.Entry<String, Set<String>> entity : scopingRule.getScopingEntities().entrySet()) {
        String taskSetupAbstractionValue = taskSetupAbstractions.get(entity.getKey());

        if (isBlank(taskSetupAbstractionValue)
            || (entity.getValue() != null && !entity.getValue().contains(taskSetupAbstractionValue))) {
          // Temporary workaround, until all tasks start sending envType and serviceId
          boolean workaroundPassed =
              trySetupAbstractionsWorkaround(logSequence, taskSetupAbstractions, entity.getKey(), entity.getValue());

          if (!workaroundPassed) {
            log.info(
                logSequence + " - Scoping rule with description: {}, did not match with task abstractions for key {}.",
                scopingRule.getDescription(), entity.getKey());
            failedRulesDescriptions.add(scopingRule.getDescription());
            scopingRuleMatched = false;
            break;
          }
        }
      }

      if (scopingRuleMatched) {
        return true;
      }
    }

    delegateSelectionLogsService.logProfileScopeRuleNotMatched(
        batch, delegate.getAccountId(), delegate.getUuid(), delegateProfile.getUuid(), failedRulesDescriptions);

    return false;
  }

  private boolean canAssignTaskToDelegate(List<String> supportedTaskTypesByDelegate, String taskType) {
    return supportedTaskTypesByDelegate != null && taskType != null && supportedTaskTypesByDelegate.contains(taskType);
  }

  private boolean trySetupAbstractionsWorkaround(String logSequence, Map<String, String> taskSetupAbstractions,
      String scopingEntityKey, Set<String> scopingEntityValues) {
    boolean workaroundPassed = false;

    if (Cd1SetupFields.ENV_TYPE_FIELD.equals(scopingEntityKey)) {
      if (taskSetupAbstractions.containsKey(Cd1SetupFields.ENV_ID_FIELD)) {
        Environment environment =
            persistence.get(Environment.class, taskSetupAbstractions.get(Cd1SetupFields.ENV_ID_FIELD));
        if (environment != null && environment.getEnvironmentType() != null) {
          workaroundPassed = scopingEntityValues.contains(environment.getEnvironmentType().name());
        }
      }
    }

    if (Cd1SetupFields.SERVICE_ID_FIELD.equals(scopingEntityKey)) {
      if (taskSetupAbstractions.containsKey(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD)) {
        InfrastructureMapping infrastructureMapping = persistence.get(
            InfrastructureMapping.class, taskSetupAbstractions.get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD));
        if (infrastructureMapping != null && isNotBlank(infrastructureMapping.getServiceId())) {
          workaroundPassed = scopingEntityValues.contains(infrastructureMapping.getServiceId());
        }
      }
    }

    log.warn(logSequence + " - Workaround for entity key {} passed: {}", scopingEntityKey, workaroundPassed);
    return workaroundPassed;
  }

  private boolean canAssignDelegateScopes(BatchDelegateSelectionLog batch, Delegate delegate, String appId,
      String envId, String infraMappingId, TaskGroup taskGroup) {
    List<DelegateScope> includeScopes = new ArrayList<>();

    if (isNotEmpty(delegate.getIncludeScopes())) {
      includeScopes = delegate.getIncludeScopes().stream().filter(Objects::nonNull).collect(toList());
    }

    boolean includeMatched = includeScopes.isEmpty();
    for (DelegateScope scope : includeScopes) {
      if (isDelegateAllowedForScope(
              scopeMatch(scope, appId, envId, infraMappingId, taskGroup, delegate.getAccountId()))) {
        includeMatched = true;
        break;
      }
    }

    if (!includeMatched) {
      delegateSelectionLogsService.logNoIncludeScopeMatched(batch, delegate.getAccountId(), delegate.getUuid());
      return false;
    }

    List<DelegateScope> excludeScopes = new ArrayList<>();
    if (isNotEmpty(delegate.getExcludeScopes())) {
      excludeScopes = delegate.getExcludeScopes().stream().filter(Objects::nonNull).collect(toList());
    }

    for (DelegateScope scope : excludeScopes) {
      if (ScopeMatchResult.SCOPE_MATCHED
          == scopeMatch(scope, appId, envId, infraMappingId, taskGroup, delegate.getAccountId())) {
        delegateSelectionLogsService.logExcludeScopeMatched(
            batch, delegate.getAccountId(), delegate.getUuid(), scope.getName());
        return false;
      }
    }

    return true;
  }

  private boolean canAssignSelectors(
      BatchDelegateSelectionLog batch, Delegate delegate, List<ExecutionCapability> executionCapabilities) {
    if (isEmpty(executionCapabilities)) {
      return true;
    }

    List<SelectorCapability> selectorsCapabilityList = executionCapabilities.stream()
                                                           .filter(c -> c instanceof SelectorCapability)
                                                           .map(c -> (SelectorCapability) c)
                                                           .collect(Collectors.toList());

    if (isEmpty(selectorsCapabilityList)) {
      return true;
    }

    Set<String> delegateSelectors = trimmedLowercaseSet(delegateService.retrieveDelegateSelectors(delegate));

    if (isEmpty(delegateSelectors)) {
      delegateSelectionLogsService.logMissingAllSelectors(batch, delegate.getAccountId(), delegate.getUuid());
      return false;
    }

    boolean canAssignSelector = true;

    for (SelectorCapability selectorCapability : selectorsCapabilityList) {
      Set<String> selectors = selectorCapability.getSelectors();
      String selectorOrigin = selectorCapability.getSelectorOrigin();
      for (String selector : trimmedLowercaseSet(selectors)) {
        if (!delegateSelectors.contains(selector)) {
          delegateSelectionLogsService.logMissingSelector(
              batch, delegate.getAccountId(), delegate.getUuid(), selector, selectorOrigin);
          canAssignSelector = false;
        }
      }
    }

    return canAssignSelector;
  }

  private ScopeMatchResult scopeMatch(
      DelegateScope scope, String appId, String envId, String infraMappingId, TaskGroup taskGroup, String accountId) {
    if (!scope.isValid()) {
      log.error("Delegate scope cannot be empty.");
      throw new WingsException(ErrorCode.INVALID_ARGUMENT).addParam("args", "Delegate scope cannot be empty.");
    }
    ScopeMatchResult scopeMatchResult = ScopeMatchResult.SCOPE_MATCHED;

    if (isNotEmpty(scope.getEnvironmentTypes())) {
      if (shouldFollowWildcardScope(appId) || shouldFollowWildcardScope(envId)) {
        scopeMatchResult = ScopeMatchResult.ALLOWED_WILDCARD;
      } else {
        if (isNotBlank(appId) && isNotBlank(envId)) {
          try {
            Environment environment = environmentService.get(appId, envId, false);
            scopeMatchResult =
                environment != null && scope.getEnvironmentTypes().contains(environment.getEnvironmentType())
                ? ScopeMatchResult.SCOPE_MATCHED
                : ScopeMatchResult.SCOPE_NOT_MATCHED;
          } catch (InvalidRequestException ex) {
            log.error("Environment {} referenced by scope {} does not exist.", envId, scope.getName());
            throw ex;
          }
        } else {
          scopeMatchResult = ScopeMatchResult.SCOPE_NOT_MATCHED;
        }
      }
    }

    if (isDelegateAllowedForScope(scopeMatchResult) && isNotEmpty(scope.getTaskTypes())) {
      scopeMatchResult = scope.getTaskTypes().contains(taskGroup) ? ScopeMatchResult.SCOPE_MATCHED
                                                                  : ScopeMatchResult.SCOPE_NOT_MATCHED;
    }

    if (isDelegateAllowedForScope(scopeMatchResult) && isNotEmpty(scope.getApplications())) {
      if (shouldFollowWildcardScope(appId)) {
        scopeMatchResult = ScopeMatchResult.ALLOWED_WILDCARD;
      } else {
        scopeMatchResult = (isNotBlank(appId) && scope.getApplications().contains(appId))
            ? ScopeMatchResult.SCOPE_MATCHED
            : ScopeMatchResult.SCOPE_NOT_MATCHED;
      }
    }

    if (isDelegateAllowedForScope(scopeMatchResult) && isNotEmpty(scope.getEnvironments())) {
      if (shouldFollowWildcardScope(envId)) {
        scopeMatchResult = ScopeMatchResult.ALLOWED_WILDCARD;
      } else {
        scopeMatchResult = (isNotBlank(envId) && scope.getEnvironments().contains(envId))
            ? ScopeMatchResult.SCOPE_MATCHED
            : ScopeMatchResult.SCOPE_NOT_MATCHED;
      }
    }

    if (isNotEmpty(scope.getInfrastructureDefinitions()) || isNotEmpty(scope.getServices())) {
      if (shouldFollowWildcardScope(appId) || shouldFollowWildcardScope(infraMappingId)) {
        scopeMatchResult = ScopeMatchResult.ALLOWED_WILDCARD;
      } else {
        InfrastructureMapping infrastructureMapping =
            isNotBlank(infraMappingId) ? infrastructureMappingService.get(appId, infraMappingId) : null;
        if (infrastructureMapping != null) {
          if (isDelegateAllowedForScope(scopeMatchResult) && isNotEmpty(scope.getInfrastructureDefinitions())) {
            scopeMatchResult =
                scope.getInfrastructureDefinitions().contains(infrastructureMapping.getInfrastructureDefinitionId())
                ? ScopeMatchResult.SCOPE_MATCHED
                : ScopeMatchResult.SCOPE_NOT_MATCHED;
          }
          if (isDelegateAllowedForScope(scopeMatchResult) && isNotEmpty(scope.getServices())) {
            scopeMatchResult = scope.getServices().contains(infrastructureMapping.getServiceId())
                ? ScopeMatchResult.SCOPE_MATCHED
                : ScopeMatchResult.SCOPE_NOT_MATCHED;
          }
        } else {
          scopeMatchResult = ScopeMatchResult.SCOPE_NOT_MATCHED;
        }
      }
    } else {
      if (isDelegateAllowedForScope(scopeMatchResult) && isNotEmpty(scope.getServiceInfrastructures())) {
        scopeMatchResult = (isNotBlank(infraMappingId) && scope.getServiceInfrastructures().contains(infraMappingId))
            ? ScopeMatchResult.SCOPE_MATCHED
            : ScopeMatchResult.SCOPE_NOT_MATCHED;
      }
    }

    return scopeMatchResult;
  }

  private boolean shouldFollowWildcardScope(String entityId) {
    return StringUtils.equals(entityId, SCOPE_WILDCARD);
  }

  private boolean isDelegateAllowedForScope(ScopeMatchResult scopeMatchResult) {
    return scopeMatchResult == ScopeMatchResult.SCOPE_MATCHED || scopeMatchResult == ScopeMatchResult.ALLOWED_WILDCARD;
  }

  @Override
  public boolean isWhitelisted(DelegateTask task, String delegateId) {
    try {
      boolean matching = true;
      for (String criteria : fetchCriteria(task)) {
        if (isNotBlank(criteria)) {
          Optional<DelegateConnectionResult> result =
              delegateConnectionResultCache.get(ImmutablePair.of(delegateId, criteria));
          if (!result.isPresent() || result.get().getLastUpdatedAt() < currentTimeMillis() - WHITELIST_TTL
              || !result.get().isValidated()) {
            matching = false;
            break;
          }
        }
      }
      return matching;
    } catch (Exception e) {
      log.error("Error checking whether delegate is whitelisted for task {}", task.getUuid(), e);
    }
    return false;
  }

  public static boolean shouldValidateCriteria(Optional<DelegateConnectionResult> result, long now) {
    if (!result.isPresent()) {
      return true;
    }

    long delay = now - result.get().getLastUpdatedAt();
    if (result.get().isValidated() && delay > WHITELIST_TTL) {
      return true;
    }

    if (!result.get().isValidated() && delay > BLACKLIST_TTL) {
      return true;
    }

    return false;
  }

  @Override
  public boolean shouldValidate(DelegateTask task, String delegateId) {
    try {
      for (String criteria : fetchCriteria(task)) {
        if (isNotBlank(criteria)) {
          Optional<DelegateConnectionResult> result =
              delegateConnectionResultCache.get(ImmutablePair.of(delegateId, criteria));
          if (shouldValidateCriteria(result, currentTimeMillis())
              || (!retrieveActiveDelegates(task.getAccountId(), null).contains(delegateId)
                  && isEmpty(connectedWhitelistedDelegates(task)))) {
            return true;
          }
        } else {
          log.error("We should not have bank criteria");
          return true;
        }
      }
    } catch (Exception e) {
      log.error("Error checking whether delegate should validate task", e);
    }
    return false;
  }

  @Override
  public List<String> connectedWhitelistedDelegates(DelegateTask task) {
    List<String> delegateIds = new ArrayList<>();
    try {
      BatchDelegateSelectionLog batch = delegateSelectionLogsService.createBatch(task);

      List<String> connectedEligibleDelegates = retrieveActiveDelegates(task.getAccountId(), batch)
                                                    .stream()
                                                    .filter(delegateId -> canAssign(batch, delegateId, task))
                                                    .collect(toList());

      delegateSelectionLogsService.save(batch);

      List<String> criteria = fetchCriteria(task);
      if (isEmpty(criteria)) {
        return connectedEligibleDelegates;
      }

      for (String delegateId : connectedEligibleDelegates) {
        boolean matching = true;
        for (String criterion : criteria) {
          Optional<DelegateConnectionResult> result =
              delegateConnectionResultCache.get(ImmutablePair.of(delegateId, criterion));
          if (!result.isPresent() || !result.get().isValidated()) {
            matching = false;
            break;
          }
        }
        if (matching) {
          delegateIds.add(delegateId);
        }
      }
    } catch (Exception e) {
      log.error("Error checking for whitelisted delegates", e);
    }
    return delegateIds;
  }

  protected List<String> fetchCriteria(DelegateTask task) {
    if (isEmpty(task.getExecutionCapabilities())) {
      return emptyList();
    }

    return task.getExecutionCapabilities()
        .stream()
        .filter(e -> e.evaluationMode() == ExecutionCapability.EvaluationMode.AGENT)
        .map(ExecutionCapability::fetchCapabilityBasis)
        .collect(toList());
  }

  @Override
  public String pickFirstAttemptDelegate(DelegateTask task) {
    List<String> delegates = connectedWhitelistedDelegates(task);
    if (delegates.isEmpty()) {
      log.info("No first attempt delegate was picked");
      return null;
    }
    return delegates.get(random.nextInt(delegates.size()));
  }

  private static final FindAndModifyOptions findAndModifyOptions = new FindAndModifyOptions();

  @Override
  public void refreshWhitelist(DelegateTask task, String delegateId) {
    try {
      for (String criteria : fetchCriteria(task)) {
        if (isNotBlank(criteria)) {
          Optional<DelegateConnectionResult> cachedResult =
              delegateConnectionResultCache.get(ImmutablePair.of(delegateId, criteria));
          if (cachedResult.isPresent()
              && cachedResult.get().getLastUpdatedAt() < currentTimeMillis() - WHITELIST_REFRESH_INTERVAL) {
            Query<DelegateConnectionResult> query =
                persistence.createQuery(DelegateConnectionResult.class)
                    .filter(DelegateConnectionResultKeys.accountId, task.getAccountId())
                    .filter(DelegateConnectionResultKeys.delegateId, delegateId)
                    .filter(DelegateConnectionResultKeys.criteria, criteria);
            UpdateOperations<DelegateConnectionResult> updateOperations =
                persistence.createUpdateOperations(DelegateConnectionResult.class)
                    .set(DelegateConnectionResultKeys.lastUpdatedAt, currentTimeMillis())
                    .set(DelegateConnectionResultKeys.validUntil, DelegateConnectionResult.getValidUntilTime());
            DelegateConnectionResult result = persistence.findAndModify(query, updateOperations, findAndModifyOptions);
            if (result != null) {
              log.debug("Whitelist entry refreshed");
            } else {
              log.debug("Whitelist entry was not updated");
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("Error refreshing whitelist entry for task {}", task.getUuid(), e);
    }
  }

  @Override
  public void saveConnectionResults(List<DelegateConnectionResult> results) {
    List<DelegateConnectionResult> resultsToSave =
        results.stream().filter(result -> isNotBlank(result.getCriteria())).collect(toList());

    for (DelegateConnectionResult result : resultsToSave) {
      Query<DelegateConnectionResult> query =
          persistence.createQuery(DelegateConnectionResult.class)
              .filter(DelegateConnectionResultKeys.accountId, result.getAccountId())
              .filter(DelegateConnectionResultKeys.delegateId, result.getDelegateId())
              .filter(DelegateConnectionResultKeys.criteria, result.getCriteria());
      UpdateOperations<DelegateConnectionResult> updateOperations =
          persistence.createUpdateOperations(DelegateConnectionResult.class)
              .setOnInsert(DelegateConnectionResultKeys.accountId, result.getAccountId())
              .setOnInsert(DelegateConnectionResultKeys.delegateId, result.getDelegateId())
              .setOnInsert(DelegateConnectionResultKeys.criteria, result.getCriteria())
              .setOnInsert(DelegateConnectionResultKeys.duration, result.getDuration())
              .setOnInsert(DelegateConnectionResultKeys.validUntil, result.getValidUntil())
              .set(DelegateConnectionResultKeys.validated, result.isValidated());
      persistence.upsert(query, updateOperations, upsertReturnNewOptions);
    }
  }

  @Override
  public String getActiveDelegateAssignmentErrorMessage(TaskFailureReason reason, DelegateTask delegateTask) {
    log.info("Delegate task is terminated");

    String errorMessage = "Unknown";

    List<DelegateSelectionLogParams> delegateSelectionLogs =
        delegateSelectionLogsService.fetchTaskSelectionLogs(delegateTask.getAccountId(), delegateTask.getUuid());

    if (reason != EXPIRED && isNotEmpty(delegateSelectionLogs)) {
      return delegateSelectionLogs.stream()
          .map(selectionLog
              -> String.format(String.format(ERROR_MESSAGE, selectionLog.getDelegateId(),
                  selectionLog.getDelegateName(), selectionLog.getDelegateHostName(),
                  selectionLog.getDelegateProfileName(), selectionLog.getConclusion(), selectionLog.getMessage(),
                  LocalDateTime.ofInstant(
                      Instant.ofEpochMilli(selectionLog.getEventTimestamp()), ZoneId.systemDefault()))))
          .distinct()
          .collect(Collectors.joining(", "));
    }

    try {
      // We are skipping invocation of the delegateSelectionLogsService.save intentionally, becuase we do not need to
      // track selection logs here, we just want retrieveActiveDelegates method to respect cg/ng isolation, if necessary
      BatchDelegateSelectionLog batch = delegateSelectionLogsService.createBatch(delegateTask);
      List<String> activeDelegates = retrieveActiveDelegates(delegateTask.getAccountId(), batch);

      List<String> whitelistedDelegates = connectedWhitelistedDelegates(delegateTask);
      if (activeDelegates.isEmpty()) {
        errorMessage = "There were no active delegates to complete the task.";
      } else if (whitelistedDelegates.isEmpty()) {
        StringBuilder msg = new StringBuilder();
        for (String delegateId : activeDelegates) {
          Delegate delegate = delegateCache.get(delegateTask.getAccountId(), delegateId, false);
          if (delegate != null) {
            msg.append(" ===> ").append(delegate.getHostName()).append(": ");
            boolean canAssignScope = canAssignDelegateScopes(null, delegate, delegateTask);
            boolean canAssignTags = canAssignSelectors(null, delegate, delegateTask.getExecutionCapabilities());
            if (!canAssignScope) {
              msg.append("Not in scope");
            }
            if (!canAssignScope && !canAssignTags) {
              msg.append(" - ");
            }
            if (!canAssignTags) {
              msg.append("Tag mismatch: ").append(Optional.ofNullable(delegate.getTags()).orElse(emptyList()));
            }
            if (canAssignScope && canAssignTags) {
              msg.append("In scope and no tag mismatch");
            }
            msg.append('\n');
          }
        }
        String taskTagsMsg = isNotEmpty(delegateTask.getTags()) ? " Task tags: " + delegateTask.getTags() : "";
        errorMessage =
            "None of the active delegates were eligible to complete the task." + taskTagsMsg + "\n\n" + msg.toString();
      } else if (delegateTask.getDelegateId() != null) {
        Delegate delegate = delegateCache.get(delegateTask.getAccountId(), delegateTask.getDelegateId(), false);
        errorMessage = "Delegate task timed out. Delegate: "
            + (delegate != null ? delegate.getHostName() : "not found: " + delegateTask.getDelegateId());
      } else {
        errorMessage = "Delegate task was never assigned and timed out.";
      }
    } catch (Exception e) {
      log.error("Execution exception", e);
    }
    return errorMessage;
  }

  @Override
  public boolean noInstalledDelegates(String accountId) {
    try {
      return accountDelegatesCache.get(accountId).isEmpty();
    } catch (ExecutionException ex) {
      log.error("Unexpected error occurred while fetching delegates from cache.", ex);
      return true;
    }
  }

  @Override
  public List<Delegate> getAccountDelegates(String accountId) {
    try {
      return accountDelegatesCache.get(accountId);
    } catch (ExecutionException | InvalidCacheLoadException ex) {
      log.error("Unexpected error occurred while fetching delegates from cache.", ex);
      return emptyList();
    }
  }

  @Override
  public List<String> retrieveActiveDelegates(String accountId, BatchDelegateSelectionLog batch) {
    try {
      List<Delegate> accountDelegates = accountDelegatesCache.get(accountId);
      if (accountDelegates.isEmpty()) {
        /* Cache invalidation was added here in order to cover the edge case, when there are no delegates in db for
         * the given account, so that the cache has an opportunity to refresh on a next invocation, instead of waiting
         * for the whole cache validity period to pass and returning empty list.
         * */
        accountDelegatesCache.invalidate(accountId);
      }

      if (batch != null) {
        accountDelegates =
            accountDelegates.stream().filter(delegate -> delegate.isNg() == batch.isTaskNg()).collect(toList());
      }

      return identifyActiveDelegateIds(accountDelegates, accountId, batch);
    } catch (ExecutionException ex) {
      log.error("Unexpected error occurred while fetching delegates from cache.", ex);
      return emptyList();
    }
  }

  private List<String> identifyActiveDelegateIds(
      List<Delegate> accountDelegates, String accountId, BatchDelegateSelectionLog batch) {
    long oldestAcceptableHeartBeat = currentTimeMillis() - MAX_DELEGATE_LAST_HEARTBEAT;

    Map<DelegateActivity, List<Delegate>> delegatesMap =
        accountDelegates.stream().collect(Collectors.groupingBy(delegate -> {
          if (DelegateInstanceStatus.ENABLED == delegate.getStatus()) {
            if (delegate.getLastHeartBeat() > oldestAcceptableHeartBeat) {
              return DelegateActivity.ACTIVE;
            } else {
              return DelegateActivity.DISCONNECTED;
            }
          } else if (DelegateInstanceStatus.WAITING_FOR_APPROVAL == delegate.getStatus()) {
            return DelegateActivity.WAITING_FOR_APPROVAL;
          }
          return DelegateActivity.OTHER;
        }, Collectors.toList()));

    logInactiveDelegates(batch, accountId, delegatesMap);

    return delegatesMap.get(DelegateActivity.ACTIVE) == null
        ? emptyList()
        : delegatesMap.get(DelegateActivity.ACTIVE).stream().map(Delegate::getUuid).collect(Collectors.toList());
  }

  @VisibleForTesting
  protected void logInactiveDelegates(
      BatchDelegateSelectionLog batch, String accountId, Map<DelegateActivity, List<Delegate>> delegatesMap) {
    if (batch == null) {
      return;
    }

    if (delegatesMap.get(DelegateActivity.DISCONNECTED) != null) {
      Set<String> disconnectedDelegateIds = delegatesMap.get(DelegateActivity.DISCONNECTED)
                                                .stream()
                                                .filter(a -> isEmpty(a.getDelegateGroupName()))
                                                .map(Delegate::getUuid)
                                                .collect(Collectors.toSet());
      if (isNotEmpty(disconnectedDelegateIds)) {
        delegateSelectionLogsService.logDisconnectedDelegate(batch, accountId, disconnectedDelegateIds);
      }
    }

    Set<String> disconnectedScalingGroup = new HashSet<>();
    if (delegatesMap.get(DelegateActivity.DISCONNECTED) != null) {
      disconnectedScalingGroup = delegatesMap.get(DelegateActivity.DISCONNECTED)
                                     .stream()
                                     .map(Delegate::getDelegateGroupName)
                                     .filter(EmptyPredicate::isNotEmpty)
                                     .collect(Collectors.toSet());
    }

    Set<String> connectedScalingGroup = new HashSet<>();
    if (delegatesMap.get(DelegateActivity.ACTIVE) != null) {
      connectedScalingGroup = delegatesMap.get(DelegateActivity.ACTIVE)
                                  .stream()
                                  .map(Delegate::getDelegateGroupName)
                                  .filter(EmptyPredicate::isNotEmpty)
                                  .collect(Collectors.toSet());
    }

    disconnectedScalingGroup.removeAll(connectedScalingGroup);

    for (String groupName : disconnectedScalingGroup) {
      delegateSelectionLogsService.logDisconnectedScalingGroup(batch, accountId, disconnectedScalingGroup, groupName);
    }

    if (delegatesMap.get(DelegateActivity.WAITING_FOR_APPROVAL) != null) {
      Set<String> wapprDelegateIds = delegatesMap.get(DelegateActivity.WAITING_FOR_APPROVAL)
                                         .stream()
                                         .map(Delegate::getUuid)
                                         .collect(Collectors.toSet());
      delegateSelectionLogsService.logWaitingForApprovalDelegate(batch, accountId, wapprDelegateIds);
    }
  }

  @Override
  public RetryDelegate onPossibleRetry(RetryDelegate retryDelegate) {
    log.info("Delegate returned retryable error for task");

    Set<String> alreadyTriedDelegates = retryDelegate.getDelegateTask().getAlreadyTriedDelegates();
    List<String> remainingConnectedDelegates =
        this.connectedWhitelistedDelegates(retryDelegate.getDelegateTask())
            .stream()
            .filter(item -> !retryDelegate.getDelegateId().equals(item))
            .filter(item -> isEmpty(alreadyTriedDelegates) || !alreadyTriedDelegates.contains(item))
            .collect(toList());

    if (!remainingConnectedDelegates.isEmpty()) {
      log.info("Requeueing task");

      persistence.update(retryDelegate.getTaskQuery(),
          persistence.createUpdateOperations(DelegateTask.class)
              .unset(DelegateTaskKeys.delegateId)
              .unset(DelegateTaskKeys.validationStartedAt)
              .unset(DelegateTaskKeys.lastBroadcastAt)
              .unset(DelegateTaskKeys.validatingDelegateIds)
              .unset(DelegateTaskKeys.validationCompleteDelegateIds)
              .set(DelegateTaskKeys.broadcastCount, 1)
              .set(DelegateTaskKeys.status, QUEUED)
              .addToSet(DelegateTaskKeys.alreadyTriedDelegates, retryDelegate.getDelegateId()));

      return RetryDelegate.builder().retryPossible(true).build();
    } else {
      log.info("Task has been tried on all the connected delegates. Proceeding with error.");
    }

    return RetryDelegate.builder().retryPossible(false).build();
  }

  @Override
  public void onTaskResponseProcessed(DelegateTask delegateTask, String delegateId) {
    this.refreshWhitelist(delegateTask, delegateId);
  }

  private enum ScopeMatchResult { SCOPE_MATCHED, ALLOWED_WILDCARD, SCOPE_NOT_MATCHED }
}
