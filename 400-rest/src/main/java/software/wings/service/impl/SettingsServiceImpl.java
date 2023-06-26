/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.DEFAULT_UNLIMITED;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.ccm.license.CeLicenseType.LIMITED_TRIAL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.USAGE_LIMITS_EXCEEDED;
import static io.harness.exception.WingsException.USER;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.persistence.HQuery.excludeValidate;
import static io.harness.validation.PersistenceValidator.duplicateCheck;
import static io.harness.validation.Validator.equalCheck;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.app.ManagerCacheRegistrar.NEW_RELIC_APPLICATION_CACHE;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.GitConfig.GIT_USER;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.SettingAttribute.SettingCategory.CE_CONNECTOR;
import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;
import static software.wings.beans.StringValue.Builder.aStringValue;
import static software.wings.common.PathConstants.BACKUP_PATH;
import static software.wings.common.PathConstants.DEFAULT_BACKUP_PATH;
import static software.wings.common.PathConstants.DEFAULT_RUNTIME_PATH;
import static software.wings.common.PathConstants.DEFAULT_STAGING_PATH;
import static software.wings.common.PathConstants.DEFAULT_WINDOWS_RUNTIME_PATH;
import static software.wings.common.PathConstants.RUNTIME_PATH;
import static software.wings.common.PathConstants.STAGING_PATH;
import static software.wings.common.PathConstants.WINDOWS_RUNTIME_PATH;
import static software.wings.service.impl.ArtifactStreamServiceImpl.addFilterToArtifactStreamQuery;
import static software.wings.service.intfc.security.SecretManager.ENCRYPTED_FIELD_MASK;
import static software.wings.settings.SettingVariableTypes.AMAZON_S3_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.GCS_HELM_REPO;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.atteo.evo.inflector.English.plural;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SecretMetadata;
import io.harness.beans.SecretState;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.config.CCMSettingService;
import io.harness.ccm.config.CloudCostAware;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.ccm.setup.service.CEInfraSetupHandler;
import io.harness.ccm.setup.service.CEInfraSetupHandlerFactory;
import io.harness.ccm.setup.service.support.intfc.AWSCEConfigValidationService;
import io.harness.ccm.setup.service.support.intfc.AzureCEConfigValidationService;
import io.harness.data.parser.CsvParser;
import io.harness.data.structure.EmptyPredicate;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidCredentialsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedUsageRestrictionsException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.observer.Rejection;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.HIterator;
import io.harness.queue.QueuePublisher;
import io.harness.reflection.ReflectionUtils;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.validation.Create;
import io.harness.validation.SuppressValidation;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.Account;
import software.wings.beans.AccountEvent;
import software.wings.beans.AccountEventType;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsS3BucketDetails;
import software.wings.beans.Base;
import software.wings.beans.CustomArtifactServerConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.Event.Type;
import software.wings.beans.GcpConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.ServiceVariable;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.StringValue;
import software.wings.beans.ValidationResult;
import software.wings.beans.Variable;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.Workflow;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.SettingAttributeValidationFailedAlert;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.beans.ce.CEAzureConfig;
import software.wings.beans.ce.CEGcpConfig;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.features.CeCloudAccountFeature;
import software.wings.features.GitOpsFeature;
import software.wings.features.api.UsageLimitedFeature;
import software.wings.persistence.artifact.Artifact;
import software.wings.prune.PruneEvent;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UsageRestrictions;
import software.wings.service.impl.newrelic.NewRelicApplication.NewRelicApplications;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.BuildService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.apm.ApmVerificationService;
import software.wings.service.intfc.manipulation.SettingsServiceManipulationObserver;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.RestrictionsAndAppEnvMap;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.ArtifactType;
import software.wings.utils.CryptoUtils;
import software.wings.utils.EmailHelperUtils;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfiguration.CVConfigurationKeys;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.amazonaws.arn.Arn;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.annotations.Transient;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.validation.executable.ValidateOnExecution;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by anubhaw on 5/17/16.
 */
@Slf4j
@ValidateOnExecution
@Singleton
@OwnedBy(CDC)
@TargetModule(HarnessModule._445_CG_CONNECTORS)
public class SettingsServiceImpl implements SettingsService {
  public static final String TRIAL_LIMIT_EXCEEDED =
      "Please contact sales to enable CE on more than 2 Kubernetes clusters.";
  @Inject private Map<Class<? extends SettingValue>, Class<? extends BuildService>> buildServiceMap;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private ServiceClassLocator serviceLocator;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private SettingValidationService settingValidationService;
  @Inject private AppService appService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Transient @Inject private SecretManager secretManager;
  @Inject private ManagerDecryptionService managerDecryptionService;
  @Inject private UsageRestrictionsService usageRestrictionsService;
  @Inject private UserService userService;
  @Inject private YamlPushService yamlPushService;
  @Inject private GitConfigHelperService gitConfigHelperService;
  @Inject private AccountService accountService;
  @Inject private ArtifactService artifactService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private CCMSettingService ccmSettingService;
  @Inject private CEInfraSetupHandlerFactory ceInfraSetupHandlerFactory;

  @Getter private Subject<SettingsServiceManipulationObserver> manipulationSubject = new Subject<>();
  @Inject @Named(NEW_RELIC_APPLICATION_CACHE) private Cache<String, NewRelicApplications> newRelicApplicationCache;
  @Inject private EnvironmentService envService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject @Named(GitOpsFeature.FEATURE_NAME) private UsageLimitedFeature gitOpsFeature;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private ServiceVariableService serviceVariableService;
  @Inject private WorkflowService workflowService;
  @Inject private QueuePublisher<PruneEvent> pruneQueue;
  @Inject private AlertService alertService;
  @Inject private SettingServiceHelper settingServiceHelper;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private ApmVerificationService apmVerificationService;
  @Inject private AWSCEConfigValidationService awsCeConfigService;
  @Inject private AzureCEConfigValidationService azureCEConfigValidationService;
  @Inject @Named(CeCloudAccountFeature.FEATURE_NAME) private UsageLimitedFeature ceCloudAccountFeature;
  @Inject EmailHelperUtils emailHelperUtils;

  @Inject @Getter(onMethod = @__(@SuppressValidation)) private Subject<CloudProviderObserver> subject = new Subject<>();
  @Inject
  @Getter(onMethod = @__(@SuppressValidation))
  private Subject<SettingAttributeObserver> artifactStreamSubject = new Subject<>();
  @Inject
  @Getter(onMethod = @__(@SuppressValidation))
  private Subject<SettingAttributeObserver> appManifestSubject = new Subject<>();
  @Inject private SettingAttributeDao settingAttributeDao;
  @Inject private CEMetadataRecordDao ceMetadataRecordDao;
  @Inject private RemoteObserverInformer remoteObserverInformer;
  @Inject private YamlGitService yamlGitService;

  private static final String OPEN_SSH = "OPENSSH";

  @Override
  public List<String> getSettingIdsForAccount(String accountId) {
    return wingsPersistence.createQuery(SettingAttribute.class)
        .filter(SettingAttributeKeys.accountId, accountId)
        .project(SettingAttributeKeys.uuid, true)
        .asList()
        .stream()
        .map(SettingAttribute::getUuid)
        .collect(toList());
  }

  public List<SettingAttribute> list(String accountId, SettingCategory category) {
    return settingAttributeDao.list(accountId, category);
  }

  @Override
  public PageResponse<SettingAttribute> list(
      PageRequest<SettingAttribute> req, String appIdFromRequest, String accountIdFromRequest) {
    try {
      PageResponse<SettingAttribute> pageResponse = wingsPersistence.query(SettingAttribute.class, req);
      List<SettingAttribute> listSettings = pageResponse.getResponse();
      RestrictionsAndAppEnvMap restrictionsAndAppEnvMap =
          usageRestrictionsService.getRestrictionsAndAppEnvMapFromCache(accountIdFromRequest, Action.READ);
      Map<String, Set<String>> appEnvMapFromUserPermissions = restrictionsAndAppEnvMap.getAppEnvMap();
      if (isEmpty(appEnvMapFromUserPermissions) || !appEnvMapFromUserPermissions.containsKey(appIdFromRequest)) {
        listSettings = Collections.emptyList();
      }
      return aPageResponse().withResponse(listSettings).withTotal(pageResponse.getTotal()).build();
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public PageResponse<SettingAttribute> list(
      PageRequest<SettingAttribute> req, String appIdFromRequest, String envIdFromRequest, boolean forUsageInNewApp) {
    try {
      long timestamp = System.currentTimeMillis();
      PageResponse<SettingAttribute> pageResponse = wingsPersistence.query(SettingAttribute.class, req);
      log.info("Time taken in DB Query for while fetching settings:  {}", System.currentTimeMillis() - timestamp);

      timestamp = System.currentTimeMillis();
      List<SettingAttribute> filteredSettingAttributes =
          getFilteredSettingAttributes(pageResponse.getResponse(), appIdFromRequest, envIdFromRequest, forUsageInNewApp)
              .stream()
              .filter(settingAttribute
                  -> !(SettingCategory.CONNECTOR.equals(settingAttribute.getCategory())
                      && "SMTP".equals(settingAttribute.getValue().getType())
                      && emailHelperUtils.isNgSmtp(settingAttribute.getName())))
              .collect(Collectors.toList());
      log.info("Total time taken in filtering setting records:  {}.", System.currentTimeMillis() - timestamp);
      return aPageResponse().withResponse(filteredSettingAttributes).withTotal(pageResponse.getTotal()).build();
    } catch (InvalidRequestException | InvalidCredentialsException | InvalidArgumentsException e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public List<SettingAttribute> listAllSettingAttributesByType(String accountId, String type) {
    List<SettingAttribute> settingAttributeList = new ArrayList<>();
    Integer limit = DEFAULT_UNLIMITED;
    Integer previousOffset = 0;
    PageRequest<SettingAttribute> pageRequest = aPageRequest()
                                                    .addFilter(SettingAttributeKeys.accountId, EQ, accountId)
                                                    .addFilter(SettingAttributeKeys.value_type, EQ, type)
                                                    .withLimit(String.valueOf(limit))
                                                    .withOffset(String.valueOf(previousOffset))
                                                    .build();
    PageResponse<SettingAttribute> pageResponse;
    do {
      pageResponse = list(pageRequest, null, null, false);
      settingAttributeList.addAll(pageResponse.getResponse());
      pageRequest.setOffset(String.valueOf(previousOffset + limit));
      previousOffset += limit;
    } while (!pageResponse.isEmpty());
    return settingAttributeList;
  }

  @Override
  public PageResponse<SettingAttribute> list(PageRequest<SettingAttribute> req, String appIdFromRequest,
      String envIdFromRequest, String accountId, boolean gitSshConfigOnly, boolean withArtifactStreamCount,
      String artifactStreamSearchString, int maxArtifactStreams, ArtifactType artifactType, boolean forUsageInNewApp) {
    try {
      PageRequest<SettingAttribute> pageRequest = req.copy();
      int offset = pageRequest.getStart();
      int limit = pageRequest.getPageSize();

      pageRequest.setOffset("0");
      pageRequest.setLimit(String.valueOf(Integer.MAX_VALUE));
      PageResponse<SettingAttribute> pageResponse = wingsPersistence.query(SettingAttribute.class, pageRequest);

      List<SettingAttribute> filteredSettingAttributes = getFilteredSettingAttributes(
          pageResponse.getResponse(), appIdFromRequest, envIdFromRequest, forUsageInNewApp);

      if (gitSshConfigOnly) {
        filteredSettingAttributes =
            filteredSettingAttributes.stream()
                .filter(settingAttribute
                    -> GIT_USER.equals(((HostConnectionAttributes) settingAttribute.getValue()).getUserName()))
                .collect(Collectors.toList());
      }

      if (withArtifactStreamCount && isNotEmpty(filteredSettingAttributes)) {
        filteredSettingAttributes = filterSettingsAndArtifactStreamsWithCount(
            accountId, artifactStreamSearchString, maxArtifactStreams, artifactType, filteredSettingAttributes);
      }

      List<SettingAttribute> resp;
      if (isEmpty(filteredSettingAttributes)) {
        resp = Collections.emptyList();
      } else {
        int total = filteredSettingAttributes.size();
        if (total <= offset) {
          resp = Collections.emptyList();
        } else {
          int endIdx = Math.min(offset + limit, total);
          resp = filteredSettingAttributes.subList(offset, endIdx);
        }
      }

      return aPageResponse()
          .withResponse(resp)
          .withTotal(pageResponse.getTotal())
          .withOffset(req.getOffset())
          .withLimit(req.getLimit())
          .build();
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private List<SettingAttribute> filterSettingsAndArtifactStreamsWithCount(String accountId,
      String artifactStreamSearchString, int maxArtifactStreams, ArtifactType artifactType,
      List<SettingAttribute> filteredSettingAttributes) {
    List<SettingAttribute> newSettingAttributes = new ArrayList<>();
    for (SettingAttribute settingAttribute : filteredSettingAttributes) {
      Query<ArtifactStream> artifactStreamQuery = wingsPersistence.createQuery(ArtifactStream.class)
                                                      .disableValidation()
                                                      .filter(ArtifactStreamKeys.settingId, settingAttribute.getUuid())
                                                      .filter(ArtifactStreamKeys.accountId, accountId)
                                                      .project(ArtifactStreamKeys.settingId, true)
                                                      .project(ArtifactStreamKeys.name, true)
                                                      .project(ArtifactStreamKeys.sourceName, true)
                                                      .order(Sort.descending(CreatedAtAware.CREATED_AT_KEY));
      if (isNotEmpty(artifactStreamSearchString)) {
        artifactStreamQuery.field(ArtifactStreamKeys.name).containsIgnoreCase(artifactStreamSearchString);
      }

      addFilterToArtifactStreamQuery(artifactType, artifactStreamQuery);
      long totalArtifactStreamCount = artifactStreamQuery.count();
      if (totalArtifactStreamCount == 0L) {
        continue;
      }
      List<ArtifactStream> artifactStreams = artifactStreamQuery.asList(new FindOptions().limit(maxArtifactStreams));
      if (isEmpty(artifactStreams)) {
        continue;
      }

      List<ArtifactStreamSummary> artifactStreamSummaries = new ArrayList<>();
      artifactStreams.forEach(artifactStream -> {
        Artifact lastCollectedArtifact = artifactService.fetchLastCollectedArtifact(artifactStream);
        ArtifactStreamSummary artifactStreamSummary =
            ArtifactStreamServiceBindingServiceImpl.prepareSummaryFromArtifactStream(
                artifactStream, lastCollectedArtifact);
        artifactStreamSummaries.add(artifactStreamSummary);
      });
      settingAttribute.setArtifactStreamCount(totalArtifactStreamCount);
      settingAttribute.setArtifactStreams(artifactStreamSummaries);
      newSettingAttributes.add(settingAttribute);
    }
    return newSettingAttributes;
  }

  /**
   * Extracts all secret Ids and there state referenced by collection of SettingAttribute. This function helps to
   * optimize RBAC rule evaluations by batching DB accesses.
   * @param settingAttributes
   * @param forUsageInNewApp
   * @return
   */
  Map<String, SecretState> extractAllSecretIdsWithState(Collection<SettingAttribute> settingAttributes,
      String accountId, String appIdFromRequest, String envIdFromRequest, boolean forUsageInNewApp) {
    // useInApp
    if (isEmpty(settingAttributes)) {
      return Collections.emptyMap();
    }
    Set<String> allSecrets = new HashSet<>(settingAttributes.size() + 1);
    // Collect all secrets in a single collection so that they can be filtered in batch fashion.
    for (SettingAttribute settingAttribute : settingAttributes) {
      Set<String> usedSecretIds = settingServiceHelper.getUsedSecretIds(settingAttribute);
      if (!isEmpty(usedSecretIds)) {
        allSecrets.addAll(usedSecretIds);
      }
    }

    List<SecretMetadata> secretMetadataList = secretManager.filterSecretIdsByReadPermission(
        allSecrets, accountId, appIdFromRequest, envIdFromRequest, forUsageInNewApp);
    if (!isEmpty(secretMetadataList)) {
      return secretMetadataList.stream().collect(
          Collectors.toMap(SecretMetadata::getSecretId, SecretMetadata::getSecretState));
    }

    return Collections.emptyMap();
  }

  @Override
  public List<SettingAttribute> getFilteredSettingAttributes(List<SettingAttribute> inputSettingAttributes,
      String appIdFromRequest, String envIdFromRequest, boolean forUsageInNewApp) {
    if (inputSettingAttributes == null) {
      return Collections.emptyList();
    }
    if (isEmpty(inputSettingAttributes)) {
      return inputSettingAttributes;
    }

    String accountId = inputSettingAttributes.get(0).getAccountId();
    List<SettingAttribute> filteredSettingAttributes = Lists.newArrayList();

    RestrictionsAndAppEnvMap restrictionsAndAppEnvMap =
        usageRestrictionsService.getRestrictionsAndAppEnvMapFromCache(accountId, Action.READ);
    Map<String, Set<String>> appEnvMapFromUserPermissions = restrictionsAndAppEnvMap.getAppEnvMap();
    UsageRestrictions restrictionsFromUserPermissions = restrictionsAndAppEnvMap.getUsageRestrictions();

    Set<String> appsByAccountId = appService.getAppIdsAsSetByAccountId(accountId);
    Map<String, List<Base>> appIdEnvMap = envService.getAppIdEnvMap(appsByAccountId, accountId);
    if (featureFlagService.isGlobalEnabled(FeatureName.SPG_ENVIRONMENT_QUERY_LOGS)) {
      log.info("[GetAppIdEnvMap] SettingsServiceImpl:getFilteredSettingAttributes - debug log");
    }

    Set<SettingAttribute> helmRepoSettingAttributes = new HashSet<>();
    boolean isAccountAdmin;
    Map<String, SecretState> secretIdsStateMap = extractAllSecretIdsWithState(
        inputSettingAttributes, accountId, appIdFromRequest, envIdFromRequest, forUsageInNewApp);
    for (SettingAttribute settingAttribute : inputSettingAttributes) {
      PermissionAttribute.PermissionType permissionType = settingServiceHelper.getPermissionType(settingAttribute);
      isAccountAdmin = userService.hasPermission(accountId, permissionType);
      boolean isReferring = isSettingAttributeReferencingCloudProvider(settingAttribute);
      if (isReferring) {
        helmRepoSettingAttributes.add(settingAttribute);
      } else {
        if (isFilteredSettingAttribute(appIdFromRequest, envIdFromRequest, accountId, forUsageInNewApp,
                appEnvMapFromUserPermissions, restrictionsFromUserPermissions, isAccountAdmin, appIdEnvMap,
                settingAttribute, settingAttribute, secretIdsStateMap)) {
          filteredSettingAttributes.add(settingAttribute);
        }
      }
    }
    getFilteredHelmRepoSettingAttributes(appIdFromRequest, envIdFromRequest, accountId, forUsageInNewApp,
        filteredSettingAttributes, appEnvMapFromUserPermissions, restrictionsFromUserPermissions, appIdEnvMap,
        helmRepoSettingAttributes);

    return filteredSettingAttributes;
  }

  private void checkGitConnectorsUsageWithinLimit(SettingAttribute settingAttribute) {
    int maxGitConnectorsAllowed = gitOpsFeature.getMaxUsageAllowedForAccount(settingAttribute.getAccountId());
    PageRequest<SettingAttribute> request =
        aPageRequest()
            .addFilter(SettingAttributeKeys.accountId, Operator.EQ, settingAttribute.getAccountId())
            .addFilter(SettingAttributeKeys.value_type, Operator.EQ, SettingVariableTypes.GIT)
            .build();
    int currentGitConnectorCount = list(request, null, null, false).getResponse().size();
    if (currentGitConnectorCount >= maxGitConnectorsAllowed) {
      log.info("Did not save Setting Attribute of type {} for account ID {} because usage limit exceeded",
          settingAttribute.getValue().getType(), settingAttribute.getAccountId());
      throw new WingsException(USAGE_LIMITS_EXCEEDED,
          String.format("Cannot create more than %d Git Connector", maxGitConnectorsAllowed), WingsException.USER);
    }
  }

  private void getFilteredHelmRepoSettingAttributes(String appIdFromRequest, String envIdFromRequest, String accountId,
      boolean forUsageInNewApp, List<SettingAttribute> filteredSettingAttributes,
      Map<String, Set<String>> appEnvMapFromUserPermissions, UsageRestrictions restrictionsFromUserPermissions,
      Map<String, List<Base>> appIdEnvMap, Set<SettingAttribute> helmRepoSettingAttributes) {
    if (isNotEmpty(helmRepoSettingAttributes)) {
      Set<String> cloudProviderIds = new HashSet<>();

      helmRepoSettingAttributes.forEach(settingAttribute -> {
        HelmRepoConfig helmRepoConfig = (HelmRepoConfig) settingAttribute.getValue();
        if (isNotBlank(helmRepoConfig.getConnectorId())) {
          cloudProviderIds.add(helmRepoConfig.getConnectorId());
        }
      });

      Map<String, SettingAttribute> cloudProvidersMap = new HashMap<>();

      try (HIterator<SettingAttribute> settingAttributes =
               new HIterator<>(wingsPersistence.createQuery(SettingAttribute.class)
                                   .filter(SettingAttributeKeys.accountId, accountId)
                                   .filter(SettingAttributeKeys.appId, GLOBAL_APP_ID)
                                   .field(ID_KEY)
                                   .in(cloudProviderIds)
                                   .fetch())) {
        settingAttributes.forEach(
            settingAttribute -> { cloudProvidersMap.put(settingAttribute.getUuid(), settingAttribute); });
      }
      Map<String, SecretState> secretIdsStateMap = extractAllSecretIdsWithState(
          cloudProvidersMap.values(), accountId, appIdFromRequest, envIdFromRequest, forUsageInNewApp);
      helmRepoSettingAttributes.forEach(settingAttribute -> {
        PermissionAttribute.PermissionType permissionType = settingServiceHelper.getPermissionType(settingAttribute);
        boolean isAccountAdmin = userService.hasPermission(accountId, permissionType);
        String cloudProviderId = ((HelmRepoConfig) settingAttribute.getValue()).getConnectorId();
        if (isNotBlank(cloudProviderId) && cloudProvidersMap.containsKey(cloudProviderId)
            && isFilteredSettingAttribute(appIdFromRequest, envIdFromRequest, accountId, forUsageInNewApp,
                appEnvMapFromUserPermissions, restrictionsFromUserPermissions, isAccountAdmin, appIdEnvMap,
                settingAttribute, cloudProvidersMap.get(cloudProviderId), secretIdsStateMap)) {
          filteredSettingAttributes.add(settingAttribute);
        }
      });
    }
  }

  @VisibleForTesting
  public boolean isFilteredSettingAttribute(String appIdFromRequest, String envIdFromRequest, String accountId,
      boolean forUsageInNewApp, Map<String, Set<String>> appEnvMapFromUserPermissions,
      UsageRestrictions restrictionsFromUserPermissions, boolean isAccountAdmin, Map<String, List<Base>> appIdEnvMap,
      SettingAttribute settingAttribute, SettingAttribute settingAttributeWithUsageRestrictions,
      Map<String, SecretState> secretIdsStateMap) {
    if (settingServiceHelper.hasReferencedSecrets(settingAttributeWithUsageRestrictions)) {
      // Try to get any secret references if possible.
      Set<String> usedSecretIds = settingServiceHelper.getUsedSecretIds(settingAttributeWithUsageRestrictions);
      if (isNotEmpty(usedSecretIds)) {
        // Returning false only on SecretState.CANNOT_READ. Which means SecretState.CAN_READ, SecretState.NOT_FOUND
        // are both allowed. This allows settings with mis matching/deleted secret Ids, this is done to
        return usedSecretIds.stream()
            .filter(secretIdsStateMap::containsKey)
            .noneMatch(secretId -> secretIdsStateMap.get(secretId) == SecretState.CANNOT_READ);
      }
    }

    UsageRestrictions usageRestrictionsFromEntity = settingAttributeWithUsageRestrictions.getUsageRestrictions();

    if (usageRestrictionsService.hasAccess(accountId, isAccountAdmin, appIdFromRequest, envIdFromRequest,
            forUsageInNewApp, usageRestrictionsFromEntity, restrictionsFromUserPermissions,
            appEnvMapFromUserPermissions, appIdEnvMap, false)) {
      // HAR-7726: Mask the encrypted field values when listing all settings.
      SettingValue settingValue = settingAttribute.getValue();
      if (settingValue instanceof EncryptableSetting) {
        secretManager.maskEncryptedFields((EncryptableSetting) settingValue);
      }
      return true;
    }
    return false;
  }

  private boolean isSettingAttributeReferencingCloudProvider(SettingAttribute settingAttribute) {
    return SettingCategory.HELM_REPO == settingAttribute.getCategory()
        && (AMAZON_S3_HELM_REPO.name().equals(settingAttribute.getValue().getType())
            || GCS_HELM_REPO.name().equals(settingAttribute.getValue().getType()));
  }

  private UsageRestrictions getUsageRestrictions(SettingAttribute settingAttribute) {
    SettingValue settingValue = settingAttribute.getValue();
    if (isSettingAttributeReferencingCloudProvider(settingAttribute)) {
      String cloudProviderId = ((HelmRepoConfig) settingValue).getConnectorId();
      SettingAttribute cloudProvider = get(settingAttribute.getAppId(), cloudProviderId);
      if (cloudProvider == null) {
        throw new InvalidRequestException("Cloud provider doesn't exist", USER);
      }
      return cloudProvider.getUsageRestrictions();
    } else {
      return settingAttribute.getUsageRestrictions();
    }
  }

  @Override
  @ValidationGroups(Create.class)
  public SettingAttribute save(SettingAttribute settingAttribute) {
    if (settingAttribute.getValue().getType().equals(SettingVariableTypes.GIT.name())) {
      checkGitConnectorsUsageWithinLimit(settingAttribute);
    } else if (isOpenSSHKeyUsed(settingAttribute)) {
      restrictOpenSSHKey(settingAttribute);
    }
    return save(settingAttribute, true);
  }

  @Override
  public SettingAttribute saveWithPruning(SettingAttribute settingAttribute, String appId, String accountId) {
    prePruneSettingAttribute(appId, accountId, settingAttribute);
    return save(settingAttribute);
  }

  private void prePruneSettingAttribute(final String appId, final String accountId, final SettingAttribute variable) {
    variable.setName(StringUtils.trim(variable.getName()));
    variable.setAppId(appId);
    if (accountId != null) {
      variable.setAccountId(accountId);
    }
    if (variable.getValue() != null) {
      if (variable.getValue() instanceof EncryptableSetting) {
        ((EncryptableSetting) variable.getValue()).setAccountId(variable.getAccountId());
        ((EncryptableSetting) variable.getValue()).setDecrypted(true);
      }
      if (variable.getValue() instanceof CustomArtifactServerConfig) {
        ((CustomArtifactServerConfig) variable.getValue()).setAccountId(variable.getAccountId());
      }
      if (variable.getValue() instanceof HostConnectionAttributes
          && null == ((HostConnectionAttributes) variable.getValue()).getSshPort()) {
        ((HostConnectionAttributes) variable.getValue()).setSshPort(22);
      }
      variable.setCategory(SettingCategory.getCategory(SettingVariableTypes.valueOf(variable.getValue().getType())));
    }
  }

  @Override
  @ValidationGroups(Create.class)
  public SettingAttribute forceSave(SettingAttribute settingAttribute) {
    return forceSave(settingAttribute, false);
  }

  private SettingAttribute forceSave(SettingAttribute settingAttribute, boolean alreadyUpdatedReferencedSecrets) {
    String accountId = settingAttribute.getAccountId();

    if (!alreadyUpdatedReferencedSecrets) {
      settingServiceHelper.updateReferencedSecrets(settingAttribute);
    }
    if (settingServiceHelper.hasReferencedSecrets(settingAttribute)
        && settingAttribute.getValue() instanceof EncryptableSetting) {
      settingServiceHelper.resetEncryptedFields((EncryptableSetting) settingAttribute.getValue());
    }

    PermissionAttribute.PermissionType permissionType = settingServiceHelper.getPermissionType(settingAttribute);
    boolean isAccountAdmin = userService.hasPermission(accountId, permissionType);

    settingServiceHelper.validateUsageRestrictionsOnEntitySave(
        settingAttribute, accountId, getUsageRestrictions(settingAttribute), isAccountAdmin);

    if (settingAttribute.getValue() != null) {
      if (settingAttribute.getValue() instanceof EncryptableSetting) {
        ((EncryptableSetting) settingAttribute.getValue()).setAccountId(accountId);
      }
    }

    String createdSettingAttributeId =
        duplicateCheck(() -> wingsPersistence.save(settingAttribute), "name", settingAttribute.getName());
    if (isNotBlank(createdSettingAttributeId) && !settingAttribute.isSample()) {
      if (SettingCategory.CLOUD_PROVIDER == settingAttribute.getCategory()) {
        eventPublishHelper.publishAccountEvent(accountId,
            AccountEvent.builder().accountEventType(AccountEventType.CLOUD_PROVIDER_CREATED).build(), true, true);
      } else if (settingServiceHelper.isConnectorCategory(settingAttribute.getCategory())
          && settingServiceHelper.isArtifactServer(settingAttribute.getValue().getSettingType())) {
        eventPublishHelper.publishAccountEvent(accountId,
            AccountEvent.builder().accountEventType(AccountEventType.ARTIFACT_REPO_CREATED).build(), true, true);
      }
    }

    if (settingServiceHelper.hasReferencedSecrets(settingAttribute)
        && settingAttribute.getValue().getSettingType() == SettingVariableTypes.APM_VERIFICATION) {
      apmVerificationService.addParents(settingAttribute);
    }
    settingServiceHelper.resetTransientFields(settingAttribute.getValue());
    return settingAttribute;
  }

  @Override
  public CEK8sDelegatePrerequisite validateCEDelegateSetting(String accountId, String delegateName) {
    List<SettingAttribute> settingAttributeList =
        this.getGlobalSettingAttributesByType(accountId, SettingVariableTypes.KUBERNETES_CLUSTER.name());
    if (settingAttributeList.isEmpty()) {
      settingAttributeList = this.getGlobalSettingAttributesByType(accountId, SettingVariableTypes.KUBERNETES.name());
    }
    SettingAttribute settingAttribute =
        settingAttributeList.stream()
            .filter(sa
                -> sa.getValue() instanceof KubernetesClusterConfig
                    && ((KubernetesClusterConfig) sa.getValue()).getDelegateSelectors() != null
                    && (((KubernetesClusterConfig) sa.getValue()).getDelegateSelectors()).contains(delegateName))
            .findFirst()
            .orElse(null);

    if (settingAttribute == null) {
      log.info("No settings associated with [accountId:{}, delegateName:{}]", accountId, delegateName);
      return CEK8sDelegatePrerequisite.builder().build();
    }
    return settingValidationService.validateCEK8sDelegateSetting(settingAttribute);
  }

  @Override
  public boolean isSettingValueGcp(SettingAttribute settingAttribute) {
    return settingAttribute.getValue() instanceof GcpConfig;
  }

  @Override
  public boolean hasDelegateSelectorProperty(SettingAttribute settingAttribute) {
    return settingAttribute.getValue() instanceof GcpConfig || settingAttribute.getValue() instanceof DockerConfig
        || settingAttribute.getValue() instanceof AwsConfig || settingAttribute.getValue() instanceof NexusConfig
        || settingAttribute.getValue() instanceof ArtifactoryConfig;
  }

  @Override
  public List<String> getDelegateSelectors(SettingAttribute settingAttribute) {
    List<String> selectors = new ArrayList<>();
    if (!hasDelegateSelectorProperty(settingAttribute)) {
      return selectors;
    }
    if (settingAttribute.getValue() instanceof GcpConfig
        && ((GcpConfig) settingAttribute.getValue()).isUseDelegateSelectors()) {
      selectors = ((GcpConfig) settingAttribute.getValue()).getDelegateSelectors();
    }
    if (settingAttribute.getValue() instanceof DockerConfig) {
      selectors = ((DockerConfig) settingAttribute.getValue()).getDelegateSelectors();
    }
    if (settingAttribute.getValue() instanceof NexusConfig) {
      selectors = ((NexusConfig) settingAttribute.getValue()).getDelegateSelectors();
    }
    if (settingAttribute.getValue() instanceof ArtifactoryConfig) {
      selectors = ((ArtifactoryConfig) settingAttribute.getValue()).getDelegateSelectors();
    }
    if (settingAttribute.getValue() instanceof AwsConfig) {
      selectors = Collections.singletonList(((AwsConfig) settingAttribute.getValue()).getTag());
    }
    return isEmpty(selectors) ? new ArrayList<>()
                              : selectors.stream().filter(StringUtils::isNotBlank).distinct().collect(toList());
  }

  @Override
  public List<SettingAttribute> getSettingAttributeByReferencedConnector(
      String accountId, String settingAttributeUuid) {
    return wingsPersistence.createQuery(SettingAttribute.class)
        .disableValidation()
        .filter(SettingAttributeKeys.accountId, accountId)
        .filter(SettingAttributeKeys.referencedConnector, settingAttributeUuid)
        .asList();
  }

  @Override
  public ValidationResult validateConnectivity(SettingAttribute settingAttribute) {
    try {
      SettingAttribute existingSetting = get(settingAttribute.getAppId(), settingAttribute.getUuid());
      if (existingSetting != null) {
        settingAttribute.setSecretsMigrated(existingSetting.isSecretsMigrated());
      }
      if (settingServiceHelper.hasReferencedSecrets(settingAttribute)) {
        settingServiceHelper.updateReferencedSecrets(settingAttribute);
      } else {
        if (existingSetting != null) {
          resetUnchangedEncryptedFields(existingSetting, settingAttribute);
        }
      }

      if (settingAttribute.getValue() instanceof HostConnectionAttributes
          || settingAttribute.getValue() instanceof WinRmConnectionAttributes) {
        auditServiceHelper.reportForAuditingUsingAccountId(
            settingAttribute.getAccountId(), null, settingAttribute, Type.TEST);
        log.info("Auditing testing of connectivity for settingAttribute={} in accountId={}", settingAttribute.getUuid(),
            settingAttribute.getAccountId());
      }
      return settingValidationService.validateConnectivity(settingAttribute);
    } catch (Exception ex) {
      return new ValidationResult(false, ExceptionUtils.getMessage(ex));
    }
  }

  @Override
  public ValidationResult validateConnectivityWithPruning(
      SettingAttribute settingAttribute, String appId, String accountId) {
    prePruneSettingAttribute(appId, accountId, settingAttribute);
    return validateConnectivity(settingAttribute);
  }

  private ValidationResult validateInternal(final SettingAttribute settingAttribute) {
    try {
      settingServiceHelper.updateReferencedSecrets(settingAttribute);
      return new ValidationResult(settingValidationService.validate(settingAttribute), "");
    } catch (Exception ex) {
      return new ValidationResult(false, ExceptionUtils.getMessage(ex));
    }
  }

  @Override
  public ValidationResult validate(final SettingAttribute settingAttribute) {
    return validateInternal(settingAttribute);
  }

  @Override
  public ValidationResult validateWithPruning(final SettingAttribute settingAttribute, String appId, String accountId) {
    prePruneSettingAttribute(appId, accountId, settingAttribute);
    return validateInternal(settingAttribute);
  }

  @Override
  public ValidationResult validate(final String varId) {
    final SettingAttribute settingAttribute = get(varId);
    if (settingAttribute != null) {
      return validateInternal(settingAttribute);
    } else {
      return new ValidationResult(false, format("Setting Attribute with id: %s does not exist.", varId));
    }
  }

  @Override
  public Map<String, String> listAccountDefaults(String accountId) {
    return listAccountOrAppDefaults(accountId, GLOBAL_APP_ID);
  }

  @Override
  public Map<String, String> listAppDefaults(String accountId, String appId) {
    return listAccountOrAppDefaults(accountId, appId);
  }

  private Map<String, String> listAccountOrAppDefaults(String accountId, String appId) {
    List<SettingAttribute> settingAttributes =
        wingsPersistence.createQuery(SettingAttribute.class)
            .filter(SettingAttributeKeys.accountId, accountId)
            .filter(SettingAttributeKeys.appId, appId)
            .filter(SettingAttributeKeys.value_type, SettingVariableTypes.STRING.name())
            .asList();

    return settingAttributes.stream().collect(Collectors.toMap(SettingAttribute::getName,
        settingAttribute
        -> Optional.ofNullable(((StringValue) settingAttribute.getValue()).getValue()).orElse(""),
        (a, b) -> b));
  }

  @Override
  @ValidationGroups(Create.class)
  public SettingAttribute save(SettingAttribute settingAttribute, boolean pushToGit) {
    settingServiceHelper.updateReferencedSecrets(settingAttribute);
    if (settingAttribute.getValue() instanceof KubernetesClusterConfig
        && ((KubernetesClusterConfig) settingAttribute.getValue()).cloudCostEnabled()) {
      checkCeTrialLimit(settingAttribute);
    }
    settingValidationService.validate(settingAttribute);
    validateAndUpdateCEDetails(settingAttribute, true);
    // e.g. User is saving GitConnector and setWebhookToken is needed.
    // This fields is populated by us and not by user
    autoGenerateFieldsIfRequired(settingAttribute);
    SettingAttribute newSettingAttribute = forceSave(settingAttribute, true);

    if (shouldBeSynced(newSettingAttribute, pushToGit)) {
      yamlPushService.pushYamlChangeSet(settingAttribute.getAccountId(), null, newSettingAttribute, Type.CREATE,
          settingAttribute.isSyncFromGit(), false);
    } else {
      auditServiceHelper.reportForAuditingUsingAccountId(
          settingAttribute.getAccountId(), null, newSettingAttribute, Type.CREATE);
    }

    try {
      if (CLOUD_PROVIDER == settingAttribute.getCategory()) {
        subject.fireInform(CloudProviderObserver::onSaved, newSettingAttribute);
        remoteObserverInformer.sendEvent(
            ReflectionUtils.getMethod(CloudProviderObserver.class, "onSaved", SettingAttribute.class),
            SettingsServiceImpl.class, newSettingAttribute);
      }
    } catch (Exception e) {
      log.error("Encountered exception while informing the observers of Cloud Providers.", e);
    }

    syncCEInfra(settingAttribute);
    artifactStreamSubject.fireInform(SettingAttributeObserver::onSaved, newSettingAttribute);
    appManifestSubject.fireInform(SettingAttributeObserver::onSaved, newSettingAttribute);
    remoteObserverInformer.sendEvent(
        ReflectionUtils.getMethod(SettingAttributeObserver.class, "onSaved", SettingAttribute.class),
        SettingsServiceImpl.class, newSettingAttribute);
    return newSettingAttribute;
  }

  @VisibleForTesting
  void checkCeTrialLimit(SettingAttribute settingAttribute) {
    String accountId = settingAttribute.getAccountId();
    Account account = accountService.get(accountId);
    CeLicenseInfo ceLicenseInfo =
        Optional.ofNullable(account.getCeLicenseInfo()).orElse(CeLicenseInfo.builder().build());
    if (ceLicenseInfo.getLicenseType() == LIMITED_TRIAL) {
      List<SettingAttribute> settingAttributes = list(accountId, CLOUD_PROVIDER);
      long count =
          settingAttributes.stream()
              .filter(s -> s.getValue() instanceof KubernetesClusterConfig && ccmSettingService.isCloudCostEnabled(s))
              .count();
      if (count >= 2) {
        throw new InvalidRequestException(TRIAL_LIMIT_EXCEEDED);
      }
    }
  }

  @VisibleForTesting
  void validateAndUpdateCEDetails(SettingAttribute settingAttribute, boolean isSave) {
    if (CE_CONNECTOR == settingAttribute.getCategory()) {
      List<SettingAttribute> settingAttributesList =
          ccmSettingService.listCeCloudAccounts(settingAttribute.getAccountId());
      boolean isAwsConnectorPresent = false;
      boolean isGCPConnectorPresent = false;
      boolean isAzureConnectorPresent = false;

      for (SettingAttribute attribute : settingAttributesList) {
        if (attribute.getValue() instanceof CEAwsConfig) {
          isAwsConnectorPresent = true;
        }
        if (attribute.getValue() instanceof CEGcpConfig) {
          isGCPConnectorPresent = true;
        }
        if (attribute.getValue() instanceof CEAzureConfig) {
          isAzureConnectorPresent = true;
        }
      }

      ceMetadataRecordDao.upsert(
          CEMetadataRecord.builder()
              .accountId(settingAttribute.getAccountId())
              .awsConnectorConfigured(isAwsConnectorPresent || (settingAttribute.getValue() instanceof CEAwsConfig))
              .gcpConnectorConfigured(isGCPConnectorPresent || (settingAttribute.getValue() instanceof CEGcpConfig))
              .azureConnectorConfigured(
                  isAzureConnectorPresent || (settingAttribute.getValue() instanceof CEAzureConfig))
              .build());

      int maxCloudAccountsAllowed = ceCloudAccountFeature.getMaxUsageAllowedForAccount(settingAttribute.getAccountId());
      int currentCloudAccountsCount = settingAttributesList.size();

      if (currentCloudAccountsCount >= maxCloudAccountsAllowed && isSave) {
        log.info("Did not save Setting Attribute of type {} for account ID {} because usage limit exceeded",
            settingAttribute.getValue().getType(), settingAttribute.getAccountId());
        throw new InvalidRequestException(String.format(
            "Cannot enable Cloud Cost Management for more than %d cloud accounts", maxCloudAccountsAllowed));
      }

      if (settingAttribute.getValue() instanceof CEAwsConfig) {
        // Throw Exception if AWS connector Exists already
        if (isAwsConnectorPresent && isSave) {
          log.info("Did not save Setting Attribute of type {} for account ID {} because AWS connector exists already",
              settingAttribute.getValue().getType(), settingAttribute.getAccountId());
          throw new InvalidRequestException("Cannot enable Cloud Cost Management for more than 1 AWS cloud account");
        }

        // Extract AWS Master AccountId
        CEAwsConfig awsConfig = (CEAwsConfig) settingAttribute.getValue();
        Arn roleArn = Arn.fromString(awsConfig.getAwsCrossAccountAttributes().getCrossAccountRoleArn());
        String masterAwsAccountId = roleArn.getAccountId();
        awsConfig.setAwsAccountId(masterAwsAccountId);
        awsConfig.setAwsMasterAccountId(masterAwsAccountId);

        // Bucket Details
        AwsS3BucketDetails s3BucketDetails = awsConfig.getS3BucketDetails();
        AwsS3BucketDetails updatedBucketDetails =
            awsCeConfigService.validateCURReportAccessAndReturnS3Config(awsConfig);
        s3BucketDetails.setRegion(updatedBucketDetails.getRegion());
        String s3Prefix = updatedBucketDetails.getS3Prefix().equals("/") ? "" : updatedBucketDetails.getS3Prefix();
        s3BucketDetails.setS3Prefix(s3Prefix);

        // Update Bucket Policy
        awsCeConfigService.updateBucketPolicy(awsConfig);
      }

      if (settingAttribute.getValue() instanceof CEGcpConfig) {
        // Throw Exception if GCP connector Exists already
        if (isGCPConnectorPresent && isSave) {
          log.info("Did not save Setting Attribute of type {} for account ID {} because GCP connector exists already",
              settingAttribute.getValue().getType(), settingAttribute.getAccountId());
          throw new InvalidRequestException("Cannot enable Cloud Cost Management for more than 1 GCP cloud account");
        }
      }

      // Azure
      if (settingAttribute.getValue() instanceof CEAzureConfig) {
        // Throw Exception if Azure connector Exists already
        if (isAzureConnectorPresent && isSave) {
          log.info("Did not save Setting Attribute of type {} for account ID {} because Azure connector exists already",
              settingAttribute.getValue().getType(), settingAttribute.getAccountId());
          throw new InvalidRequestException("Cannot enable Cloud Cost Management for more than 1 Azure cloud account");
        }
        CEAzureConfig azureConfig = (CEAzureConfig) settingAttribute.getValue();
        azureCEConfigValidationService.verifyCrossAccountAttributes(azureConfig);
      }
    }
  }

  private void syncCEInfra(SettingAttribute settingAttribute) {
    try {
      if (CE_CONNECTOR == settingAttribute.getCategory()) {
        CEInfraSetupHandler ceInfraSetupHandler =
            ceInfraSetupHandlerFactory.getCEInfraSetupHandler(settingAttribute.getValue());
        ceInfraSetupHandler.syncCEInfra(settingAttribute);
      }
    } catch (Exception e) {
      log.error("Encountered exception while syncing CE Infra.", e);
    }
  }

  private void autoGenerateFieldsIfRequired(SettingAttribute settingAttribute) {
    if (settingAttribute.getValue() instanceof GitConfig) {
      GitConfig gitConfig = (GitConfig) settingAttribute.getValue();

      if (gitConfig.isGenerateWebhookUrl() && isEmpty(gitConfig.getWebhookToken())) {
        gitConfig.setWebhookToken(CryptoUtils.secureRandAlphaNumString(40));
      }

      if (GitConfig.UrlType.ACCOUNT == gitConfig.getUrlType()) {
        gitConfig.setBranch(null);
      }
    }
  }

  private boolean shouldBeSynced(SettingAttribute settingAttribute, boolean pushToGit) {
    String type = settingAttribute.getValue().getType();

    boolean skip = SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES.name().equals(type)
        || SettingVariableTypes.BASTION_HOST_CONNECTION_ATTRIBUTES.name().equals(type)
        || SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES.name().equals(type);

    return pushToGit && !skip;
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#get(java.lang.String, java.lang.String)
   */
  @Override
  public SettingAttribute get(String appId, String varId) {
    return get(appId, GLOBAL_ENV_ID, varId);
  }

  @Override
  public SettingAttribute get(String appId, String envId, String varId) {
    SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                            .filter("appId", appId)
                                            .filter(SettingAttributeKeys.envId, envId)
                                            .filter(ID_KEY, varId)
                                            .get();
    setCertValidationRequired(settingAttribute);
    setFeatureFlagIfRequired(settingAttribute);
    return settingAttribute;
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#get(java.lang.String)
   */
  @Override
  public SettingAttribute get(String varId) {
    SettingAttribute settingAttribute = getById(varId);
    setInternal(settingAttribute);
    setCertValidationRequired(settingAttribute);
    return settingAttribute;
  }

  private SettingAttribute getById(String varId) {
    SettingAttribute settingAttribute = wingsPersistence.get(SettingAttribute.class, varId);
    setCertValidationRequired(settingAttribute);
    setFeatureFlagIfRequired(settingAttribute);
    return settingAttribute;
  }

  private void setFeatureFlagIfRequired(SettingAttribute settingAttribute) {
    try {
      if (settingAttribute != null && settingAttribute.getValue() != null) {
        settingServiceHelper.setFeatureFlagIfRequired(settingAttribute.getValue(), settingAttribute.getAccountId());
      }
    } catch (Exception ex) {
      log.error("Failed to set Feature Flags for HostConnectionAttributes", ex);
    }
  }

  @Override
  public SettingAttribute getWithRbac(String id) {
    final SettingAttribute settingAttribute = getById(id);
    if (settingAttribute == null) {
      throw new InvalidRequestException(String.format("Entity with id %s does not exist.", id));
    }
    final List<SettingAttribute> filteredSettingAttributes =
        getFilteredSettingAttributes(asList(settingAttribute), null, null, false);
    if (isEmpty(filteredSettingAttributes)) {
      throw new InvalidRequestException("Not authorized");
    } else {
      return filteredSettingAttributes.get(0);
    }
  }

  @Override
  @Nullable
  public SettingAttribute getByAccount(String accountId, String varId) {
    SettingAttribute settingAttribute = get(varId);
    if (settingAttribute == null) {
      return null;
    }
    return settingAttribute.getAccountId().equals(accountId) ? settingAttribute : null;
  }

  @Override
  public SettingAttribute getByAccountAndId(String accountId, String settingId) {
    SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                            .filter(SettingAttributeKeys.uuid, settingId)
                                            .filter(SettingAttributeKeys.accountId, accountId)
                                            .get();
    setCertValidationRequired(settingAttribute);
    return settingAttribute;
  }

  private void setInternal(SettingAttribute settingAttribute) {
    if (settingAttribute != null && settingAttribute.getValue() instanceof GitConfig) {
      GitConfig gitConfig = (GitConfig) settingAttribute.getValue();
      gitConfigHelperService.setSshKeySettingAttributeIfNeeded(gitConfig);
    }
  }

  private void setCertValidationRequired(SettingAttribute settingAttribute) {
    if (settingAttribute != null && settingAttribute.getAccountId() != null) {
      settingServiceHelper.setCertValidationRequired(settingAttribute.getAccountId(), settingAttribute);
    }
  }

  @Override
  public SettingAttribute getOnlyConnectivityError(String settingId) {
    return wingsPersistence.createQuery(SettingAttribute.class, excludeAuthority)
        .project(SettingAttributeKeys.connectivityError, true)
        .filter(SettingAttributeKeys.uuid, settingId)
        .get();
  }

  @Override
  public SettingAttribute getSettingAttributeByName(String accountId, String settingAttributeName) {
    SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                            .filter(SettingAttributeKeys.name, settingAttributeName)
                                            .filter(SettingAttributeKeys.accountId, accountId)
                                            .get();
    setCertValidationRequired(settingAttribute);
    setFeatureFlagIfRequired(settingAttribute);
    return settingAttribute;
  }

  @Override
  public void checkRbacOnSettingAttribute(String appId, SettingAttribute settingAttribute) {
    if (featureFlagService.isEnabled(FeatureName.USAGE_SCOPE_RBAC, settingAttribute.getAccountId())) {
      final List<SettingAttribute> filteredSettingAttributes =
          getFilteredSettingAttributes(asList(settingAttribute), appId, null, false);
      if (isEmpty(filteredSettingAttributes)) {
        throw new InvalidRequestException("Not authorized");
      }
    }
  }

  private void resetUnchangedEncryptedFields(
      SettingAttribute existingSettingAttribute, SettingAttribute newSettingAttribute) {
    if (EncryptableSetting.class.isInstance(existingSettingAttribute.getValue())) {
      EncryptableSetting object = (EncryptableSetting) existingSettingAttribute.getValue();
      object.setDecrypted(false);

      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails(object, newSettingAttribute.getAppId(), null);
      managerDecryptionService.decrypt(object, encryptionDetails);

      secretManager.resetUnchangedEncryptedFields((EncryptableSetting) existingSettingAttribute.getValue(),
          (EncryptableSetting) newSettingAttribute.getValue());
    }
  }

  @Override
  public SettingAttribute update(SettingAttribute settingAttribute, boolean updateConnectivity, boolean pushToGit) {
    if (isOpenSSHKeyUsed(settingAttribute)) {
      restrictOpenSSHKey(settingAttribute);
    }
    if (settingAttribute.getValue() instanceof HostConnectionAttributes
        && null == ((HostConnectionAttributes) settingAttribute.getValue()).getSshPort()) {
      ((HostConnectionAttributes) settingAttribute.getValue()).setSshPort(22);
    }
    SettingAttribute existingSetting = get(settingAttribute.getAppId(), settingAttribute.getUuid());
    SettingAttribute prevSettingAttribute = existingSetting;
    if (settingAttribute.getValue() instanceof CEAwsConfig) {
      validateAndUpdateCEDetails(settingAttribute, false);
    }
    if (settingAttribute.getValue() instanceof KubernetesClusterConfig
        && ((KubernetesClusterConfig) settingAttribute.getValue()).cloudCostEnabled()) {
      checkCeTrialLimit(settingAttribute);
    }
    notNullCheck("Setting Attribute was deleted", existingSetting, USER);
    notNullCheck("SettingValue not associated", settingAttribute.getValue(), USER);
    equalCheck(existingSetting.getValue().getType(), settingAttribute.getValue().getType());

    settingAttribute.setAccountId(existingSetting.getAccountId());
    settingAttribute.setAppId(existingSetting.getAppId());
    settingAttribute.setSecretsMigrated(existingSetting.isSecretsMigrated());
    // e.g. User is saving GitConnector and setWebhookToken is needed.
    // This fields is populated by us and not by user

    boolean referencesSecrets = settingServiceHelper.hasReferencedSecrets(settingAttribute);
    if (referencesSecrets) {
      settingServiceHelper.updateReferencedSecrets(settingAttribute);
    } else {
      resetUnchangedEncryptedFields(existingSetting, settingAttribute);
    }
    PermissionAttribute.PermissionType permissionType = settingServiceHelper.getPermissionType(settingAttribute);
    boolean isAccountAdmin = userService.hasPermission(settingAttribute.getAccountId(), permissionType);

    settingServiceHelper.validateUsageRestrictionsOnEntityUpdate(settingAttribute, settingAttribute.getAccountId(),
        existingSetting.getUsageRestrictions(), getUsageRestrictions(settingAttribute), isAccountAdmin);
    validateSettingAttribute(settingAttribute, existingSetting);
    autoGenerateFieldsIfRequired(settingAttribute);

    if (updateConnectivity || isBlank(settingAttribute.getConnectivityError())) {
      settingValidationService.validate(settingAttribute);
    }

    if (referencesSecrets && settingAttribute.getValue() instanceof EncryptableSetting) {
      settingServiceHelper.resetEncryptedFields((EncryptableSetting) settingAttribute.getValue());
    }

    SettingAttribute savedSettingAttributes = get(settingAttribute.getUuid());
    Map<String, String> existingSecretRefsForApm = new HashMap<>();
    if (referencesSecrets && settingAttribute.getValue().getSettingType() == SettingVariableTypes.APM_VERIFICATION) {
      existingSecretRefsForApm =
          new HashMap<>(((APMVerificationConfig) savedSettingAttributes.getValue()).getSecretIdsToFieldNameMap());
    }

    ImmutableMap.Builder<String, Object> fields =
        ImmutableMap.<String, Object>builder().put("name", settingAttribute.getName());

    // To revisit
    if (settingAttribute.getUsageRestrictions() != null) {
      fields.put(SettingAttributeKeys.usageRestrictions, settingAttribute.getUsageRestrictions());
    }

    fields.put(SettingAttributeKeys.secretsMigrated, settingAttribute.isSecretsMigrated());

    if (settingAttribute.getValue() != null) {
      if (settingAttribute.getValue() instanceof EncryptableSetting) {
        ((EncryptableSetting) settingAttribute.getValue()).setAccountId(settingAttribute.getAccountId());
      }
      fields.put("value", settingAttribute.getValue());
    }

    Set<String> fieldsToRemove;
    if (updateConnectivity) {
      closeConnectivityErrorAlert(settingAttribute.getAccountId(), settingAttribute.getUuid());
      fieldsToRemove = Collections.singleton(SettingAttributeKeys.connectivityError);
    } else {
      String connErr = settingAttribute.getConnectivityError();
      if (isBlank(connErr)) {
        closeConnectivityErrorAlert(settingAttribute.getAccountId(), settingAttribute.getUuid());
        fieldsToRemove = Collections.singleton(SettingAttributeKeys.connectivityError);
      } else {
        openConnectivityErrorAlert(settingAttribute.getAccountId(), settingAttribute.getUuid(),
            settingAttribute.getCategory().name(), connErr);
        fieldsToRemove = Collections.emptySet();
        fields.put(SettingAttributeKeys.connectivityError, connErr);
      }
    }

    wingsPersistence.updateFields(SettingAttribute.class, settingAttribute.getUuid(), fields.build(), fieldsToRemove);

    SettingAttribute updatedSettingAttribute = getById(settingAttribute.getUuid());
    if (referencesSecrets && settingAttribute.getValue().getSettingType() == SettingVariableTypes.APM_VERIFICATION) {
      apmVerificationService.updateParents(updatedSettingAttribute, existingSecretRefsForApm);
    }

    if (!shouldBeSynced(settingAttribute, true)) {
      auditServiceHelper.reportForAuditingUsingAccountId(
          settingAttribute.getAccountId(), existingSetting, updatedSettingAttribute, Type.UPDATE);
    }

    // Need to mask the private key field value before the value is returned.
    // This will avoid confusing the user that the key field is empty when it's not.
    SettingValue updatedSettingValue = updatedSettingAttribute.getValue();
    if (updatedSettingValue instanceof HostConnectionAttributes) {
      HostConnectionAttributes hostConnectionAttributes = (HostConnectionAttributes) updatedSettingValue;
      if (!hostConnectionAttributes.isKeyless()) {
        hostConnectionAttributes.setKey(ENCRYPTED_FIELD_MASK.toCharArray());
      }
    }

    if (shouldBeSynced(updatedSettingAttribute, pushToGit)) {
      boolean isRename = !savedSettingAttributes.getName().equals(updatedSettingAttribute.getName());
      yamlPushService.pushYamlChangeSet(settingAttribute.getAccountId(), savedSettingAttributes,
          updatedSettingAttribute, Type.UPDATE, settingAttribute.isSyncFromGit(), isRename);
    }
    newRelicApplicationCache.remove(updatedSettingAttribute.getUuid());

    if (updatedSettingAttribute.getValue() instanceof CloudCostAware) {
      ccmSettingService.maskCCMConfig(updatedSettingAttribute);
    }

    try {
      if (CLOUD_PROVIDER == settingAttribute.getCategory()) {
        subject.fireInform(CloudProviderObserver::onUpdated, prevSettingAttribute, settingAttribute);
        remoteObserverInformer.sendEvent(ReflectionUtils.getMethod(CloudProviderObserver.class, "onUpdated",
                                             SettingAttribute.class, SettingAttribute.class),
            SettingsServiceImpl.class, prevSettingAttribute, settingAttribute);
      }
    } catch (Exception e) {
      log.error("Encountered exception while informing the observers of Cloud Providers.", e);
    }

    artifactStreamSubject.fireInform(SettingAttributeObserver::onUpdated, prevSettingAttribute, settingAttribute);
    appManifestSubject.fireInform(SettingAttributeObserver::onUpdated, prevSettingAttribute, settingAttribute);
    remoteObserverInformer.sendEvent(ReflectionUtils.getMethod(SettingAttributeObserver.class, "onUpdated",
                                         SettingAttribute.class, SettingAttribute.class),
        SettingsServiceImpl.class, prevSettingAttribute, settingAttribute);
    return updatedSettingAttribute;
  }

  @VisibleForTesting
  void validateSettingAttribute(SettingAttribute settingAttribute, SettingAttribute existingSettingAttribute) {
    if (settingAttribute != null && existingSettingAttribute != null) {
      if (settingAttribute.getValue() != null && existingSettingAttribute.getValue() != null) {
        if (existingSettingAttribute.getValue() instanceof NexusConfig) {
          if (!((NexusConfig) settingAttribute.getValue())
                   .getVersion()
                   .equals(((NexusConfig) existingSettingAttribute.getValue()).getVersion())) {
            throw new InvalidRequestException("Version cannot be updated", USER);
          }
        }

        if (existingSettingAttribute.getValue() instanceof GitConfig) {
          GitConfig.UrlType oldUrlType = ((GitConfig) existingSettingAttribute.getValue()).getUrlType();
          GitConfig.UrlType newUrlType = ((GitConfig) settingAttribute.getValue()).getUrlType();
          if (null != oldUrlType && null != newUrlType && oldUrlType != newUrlType) {
            throw new InvalidRequestException("UrlType cannot be updated", USER);
          }
        }
      }
    }
  }

  @Override
  public void updateUsageRestrictionsInternal(String uuid, UsageRestrictions usageRestrictions) {
    ImmutableMap.Builder<String, Object> fields =
        ImmutableMap.<String, Object>builder().put("usageRestrictions", usageRestrictions);
    wingsPersistence.updateFields(SettingAttribute.class, uuid, fields.build());
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#update(software.wings.beans.SettingAttribute)
   */
  @Override
  public SettingAttribute update(SettingAttribute settingAttribute) {
    return update(settingAttribute, true, true);
  }

  @Override
  public SettingAttribute updateWithSettingFields(SettingAttribute settingAttribute, String attrId, String appId) {
    settingAttribute.setUuid(attrId);
    settingAttribute.setAppId(appId);
    if (settingAttribute.getValue() != null) {
      if (settingAttribute.getValue() instanceof EncryptableSetting) {
        ((EncryptableSetting) settingAttribute.getValue()).setAccountId(settingAttribute.getAccountId());
        ((EncryptableSetting) settingAttribute.getValue()).setDecrypted(true);
      }
    }
    return update(settingAttribute);
  }

  @Override
  public SettingAttribute update(SettingAttribute settingAttribute, boolean updateConnectivity) {
    return update(settingAttribute, updateConnectivity, true);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#delete(java.lang.String, java.lang.String)
   */
  @Override
  public void delete(String appId, String varId) {
    delete(appId, varId, true, false);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#delete(java.lang.String, java.lang.String)
   */
  @Override
  public void delete(String appId, String varId, boolean pushToGit, boolean syncFromGit) {
    SettingAttribute settingAttribute = getById(varId);
    notNullCheck("Setting Value", settingAttribute, USER);
    String accountId = settingAttribute.getAccountId();
    PermissionAttribute.PermissionType permissionType = settingServiceHelper.getPermissionType(settingAttribute);
    boolean isAccountAdmin = userService.hasPermission(settingAttribute.getAccountId(), permissionType);

    if (!settingServiceHelper.userHasPermissionsToChangeEntity(
            settingAttribute, accountId, settingAttribute.getUsageRestrictions(), isAccountAdmin)) {
      throw new UnauthorizedUsageRestrictionsException(USER);
    }

    ensureSettingAttributeSafeToDelete(settingAttribute);

    closeConnectivityErrorAlert(accountId, settingAttribute.getUuid());
    boolean deleted = wingsPersistence.delete(settingAttribute);

    try {
      if (CLOUD_PROVIDER == settingAttribute.getCategory()) {
        log.info("Deleted the cloud provider with id={}", settingAttribute.getUuid());
        subject.fireInform(CloudProviderObserver::onDeleted, settingAttribute);
        remoteObserverInformer.sendEvent(
            ReflectionUtils.getMethod(CloudProviderObserver.class, "onDeleted", SettingAttribute.class),
            SettingsServiceImpl.class, settingAttribute);
      }
    } catch (Exception e) {
      log.error("Encountered exception while informing the observers of Cloud Providers.", e);
    }

    if (deleted && shouldBeSynced(settingAttribute, pushToGit)) {
      yamlPushService.pushYamlChangeSet(accountId, settingAttribute, null, Type.DELETE, syncFromGit, false);
      newRelicApplicationCache.remove(settingAttribute.getUuid());
    } else {
      auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, settingAttribute);
    }

    artifactStreamSubject.fireInform(SettingAttributeObserver::onDeleted, settingAttribute);
    appManifestSubject.fireInform(SettingAttributeObserver::onDeleted, settingAttribute);
    remoteObserverInformer.sendEvent(
        ReflectionUtils.getMethod(SettingAttributeObserver.class, "onDeleted", SettingAttribute.class),
        SettingsServiceImpl.class, settingAttribute);
  }

  /**
   * Retain only the selected
   * @param selectedGitConnectors List of setting attribute Names of Git connectors to be retained
   */
  @Override
  public boolean retainSelectedGitConnectorsAndDeleteRest(String accountId, List<String> selectedGitConnectors) {
    if (EmptyPredicate.isNotEmpty(selectedGitConnectors)) {
      // Delete git connectors
      wingsPersistence.delete(wingsPersistence.createQuery(SettingAttribute.class)
                                  .filter(SettingAttributeKeys.accountId, accountId)
                                  .filter(SettingAttributeKeys.value_type, SettingVariableTypes.GIT)
                                  .field(SettingAttributeKeys.name)
                                  .notIn(selectedGitConnectors));
      return true;
    }
    return false;
  }

  @Override
  public void deleteByYamlGit(String appId, String settingAttributeId, boolean syncFromGit) {
    delete(appId, settingAttributeId, true, syncFromGit);
  }

  @VisibleForTesting
  void ensureSettingAttributeSafeToDelete(SettingAttribute settingAttribute) {
    if (settingAttribute.getCategory() == SettingCategory.CLOUD_PROVIDER) {
      ensureCloudProviderSafeToDelete(settingAttribute);
    } else if (settingAttribute.getCategory() == SettingCategory.CONNECTOR) {
      ensureConnectorSafeToDelete(settingAttribute);
    } else if (settingAttribute.getCategory() == SettingCategory.SETTING) {
      ensureSettingSafeToDelete(settingAttribute);
    } else if (settingAttribute.getCategory() == SettingCategory.AZURE_ARTIFACTS) {
      ensureAzureArtifactsConnectorSafeToDelete(settingAttribute);
    } else if (settingAttribute.getCategory() == SettingCategory.HELM_REPO) {
      ensureHelmConnectorSafeToDelete(settingAttribute);
    }

    ensureNotReferencedInServiceGuard(settingAttribute);
  }

  private void ensureHelmConnectorSafeToDelete(SettingAttribute settingAttribute) {
    final int entityNamesLimit = 5;
    final String accountId = settingAttribute.getAccountId();
    final List<ApplicationManifest> manifestsWithConnector = applicationManifestService.getAllByConnectorId(
        settingAttribute.getAccountId(), settingAttribute.getUuid(), EnumSet.of(StoreType.HelmChartRepo));

    if (isNotEmpty(manifestsWithConnector)) {
      final List<String> serviceIds =
          manifestsWithConnector.stream()
              .filter(manifest -> isNotBlank(manifest.getServiceId()) && isBlank(manifest.getEnvId()))
              .map(ApplicationManifest::getServiceId)
              .collect(Collectors.toList());
      final List<String> envIds = manifestsWithConnector.stream()
                                      .filter(manifest -> isNotBlank(manifest.getEnvId()))
                                      .map(ApplicationManifest::getEnvId)
                                      .collect(Collectors.toList());
      final List<String> serviceNames = serviceResourceService.getNames(accountId, serviceIds);
      final List<String> envNames = envService.getNames(accountId, envIds);
      final StringBuilder errorMsgBuilder = new StringBuilder(64);
      errorMsgBuilder.append(format("Helm Connector [%s] is referenced ", settingAttribute.getName()));
      if (isNotEmpty(serviceNames)) {
        errorMsgBuilder.append(
            format("by [%d] service(s) %s ", serviceNames.size(), trimList(serviceNames, entityNamesLimit)));
        errorMsgBuilder.append(serviceNames.size() > entityNamesLimit
                ? format(" and [%d] more..", serviceNames.size() - entityNamesLimit)
                : "");
      }
      if (isNotEmpty(envNames)) {
        errorMsgBuilder.append(format(
            "and by [%d] override in environment(s) %s ", envNames.size(), trimList(envNames, entityNamesLimit)));
        errorMsgBuilder.append(
            envNames.size() > entityNamesLimit ? format("and [%d] more..", envNames.size() - entityNamesLimit) : "");
      }
      throw new InvalidRequestException(errorMsgBuilder.toString(), USER);
    }
  }

  private List<String> trimList(List<String> strings, int n) {
    return strings.stream().limit(n).collect(toList());
  }

  private void ensureSettingSafeToDelete(SettingAttribute settingAttribute) {
    List<String> infraDefinitionNames = infrastructureDefinitionService.listNamesByConnectionAttr(
        settingAttribute.getAccountId(), settingAttribute.getUuid());
    if (isNotEmpty(infraDefinitionNames)) {
      throw new InvalidRequestException(format("Attribute [%s] is referenced by %d "
              + " %s "
              + "[%s].",
          settingAttribute.getName(), infraDefinitionNames.size(),
          plural("Infrastructure "
                  + "Definition",
              infraDefinitionNames.size()),
          join(", ", infraDefinitionNames)));
    }
    // TODO:: workflow scan for finding out usage in Steps/expression ???
  }

  private void ensureConnectorSafeToDelete(SettingAttribute connectorSetting) {
    if (SettingVariableTypes.ELB.name().equals(connectorSetting.getValue().getType())) {
      List<InfrastructureMapping> infrastructureMappings =
          infrastructureMappingService
              .list(aPageRequest()
                        .addFilter("loadBalancerId", EQ, connectorSetting.getUuid())
                        .withLimit(PageRequest.UNLIMITED)
                        .build(),
                  excludeValidate)
              .getResponse();

      List<String> infraMappingNames =
          infrastructureMappings.stream().map(InfrastructureMapping::getName).collect(toList());
      if (!infraMappingNames.isEmpty()) {
        throw new InvalidRequestException(format("Connector [%s] is referenced by %d Service %s [%s].",
            connectorSetting.getName(), infraMappingNames.size(), plural("Infrastructure", infraMappingNames.size()),
            join(", ", infraMappingNames)));
      }
    } else {
      if (SettingVariableTypes.GIT.name().equals(connectorSetting.getValue().getType())) {
        List<YamlGitConfig> yamlGitConfigByConnector =
            yamlGitService.getYamlGitConfigByConnector(connectorSetting.getAccountId(), connectorSetting.getUuid());
        if (isNotEmpty(yamlGitConfigByConnector)) {
          throw new InvalidRequestException(String.format("Connector is referenced for git sync in apps %s",
              yamlGitConfigByConnector.stream().map(YamlGitConfig::getAppId).collect(Collectors.toList())));
        }
      }
      List<String> allAccountApps = appService.getAppIdsByAccountId(connectorSetting.getAccountId());
      List<ArtifactStream> artifactStreams =
          artifactStreamService.listBySettingId(connectorSetting.getUuid())
              .stream()
              .filter(artifactStream -> allAccountApps.contains(artifactStream.getAppId()))
              .filter(artifactStream -> serviceResourceService.get(artifactStream.getServiceId()) != null)
              .collect(toList());
      if (!artifactStreams.isEmpty()) {
        List<String> artifactStreamNames = artifactStreams.stream()
                                               .map(ArtifactStream::getSourceName)
                                               .filter(java.util.Objects::nonNull)
                                               .collect(toList());
        throw new InvalidRequestException(
            format("Connector [%s] is referenced by %d Artifact %s [%s].", connectorSetting.getName(),
                artifactStreamNames.size(), plural("Source", artifactStreamNames.size()),
                join(", ", artifactStreamNames)),
            USER);
      }

      List<Rejection> rejections = manipulationSubject.fireApproveFromAll(
          SettingsServiceManipulationObserver::settingsServiceDeleting, connectorSetting);
      if (isNotEmpty(rejections)) {
        throw new InvalidRequestException(
            format("[%s]", join("\n", rejections.stream().map(Rejection::message).collect(toList()))), USER);
      }
    }

    // TODO:: workflow scan for finding out usage in Steps ???
  }

  private void ensureCloudProviderSafeToDelete(SettingAttribute cloudProviderSetting) {
    String accountId = cloudProviderSetting.getAccountId();
    List<String> infraDefinitionNames =
        infrastructureDefinitionService.listNamesByComputeProviderId(accountId, cloudProviderSetting.getUuid());
    if (isNotEmpty(infraDefinitionNames)) {
      throw new InvalidRequestException(
          format("Cloud provider [%s] is referenced by %d Infrastructure  %s [%s].", cloudProviderSetting.getName(),
              infraDefinitionNames.size(), plural("Definition", infraDefinitionNames.size()),
              join(", ", infraDefinitionNames)),
          USER);
    }

    List<ArtifactStream> artifactStreams = artifactStreamService.listBySettingId(cloudProviderSetting.getUuid());
    if (!artifactStreams.isEmpty()) {
      List<String> artifactStreamNames = artifactStreams.stream().map(ArtifactStream::getName).collect(toList());
      throw new InvalidRequestException(
          format("Cloud provider [%s] is referenced by %d Artifact %s [%s].", cloudProviderSetting.getName(),
              artifactStreamNames.size(), plural("Source", artifactStreamNames.size()),
              join(", ", artifactStreamNames)),
          USER);
    }
    // TODO:: workflow scan for finding out usage in Steps ???
  }

  private void ensureAzureArtifactsConnectorSafeToDelete(SettingAttribute connectorSetting) {
    List<ArtifactStream> artifactStreams = artifactStreamService.listBySettingId(connectorSetting.getUuid());
    if (isEmpty(artifactStreams)) {
      return;
    }

    List<String> artifactStreamNames = artifactStreams.stream()
                                           .map(ArtifactStream::getSourceName)
                                           .filter(java.util.Objects::nonNull)
                                           .collect(toList());
    throw new InvalidRequestException(
        format("Connector [%s] is referenced by %d Artifact %s [%s].", connectorSetting.getName(),
            artifactStreamNames.size(), plural("Source", artifactStreamNames.size()), join(", ", artifactStreamNames)),
        USER);
  }

  private void ensureNotReferencedInServiceGuard(SettingAttribute settingAttribute) {
    final List<CVConfiguration> cvConfigurations =
        wingsPersistence.createQuery(CVConfiguration.class)
            .filter(CVConfigurationKeys.accountId, settingAttribute.getAccountId())
            .filter(CVConfigurationKeys.connectorId, settingAttribute.getUuid())
            .asList();

    if (isNotEmpty(cvConfigurations)) {
      List<String> cvConfigNames = cvConfigurations.stream().map(CVConfiguration::getName).collect(toList());
      throw new InvalidRequestException(
          format("Connector %s is referenced by %d Service Guard(s). %s [%s].", settingAttribute.getName(),
              cvConfigurations.size(), plural("Source", cvConfigurations.size()), join(", ", cvConfigNames)),
          USER);
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#getByName(java.lang.String, java.lang.String)
   */
  @Override
  public SettingAttribute getByName(String accountId, String appId, String attributeName) {
    return getByName(accountId, appId, GLOBAL_ENV_ID, attributeName);
  }

  @Override
  public SettingAttribute getByName(String accountId, String appId, String envId, String attributeName) {
    return getByNameAndCategory(accountId, appId, envId, attributeName, null);
  }

  @Override
  public SettingAttribute getConnectorByName(String accountId, String appId, String attributeName) {
    return getByNameAndCategory(accountId, appId, GLOBAL_ENV_ID, attributeName, SettingCategory.CONNECTOR);
  }

  private SettingAttribute getByNameAndCategory(
      String accountId, String appId, String envId, String attributeName, SettingCategory category) {
    final Query<SettingAttribute> query = wingsPersistence.createQuery(SettingAttribute.class)
                                              .filter(SettingAttributeKeys.accountId, accountId)
                                              .field(SettingAttributeKeys.appId)
                                              .in(asList(appId, GLOBAL_APP_ID))
                                              .field(SettingAttributeKeys.envId)
                                              .in(asList(envId, GLOBAL_ENV_ID))
                                              .filter(SettingAttributeKeys.name, attributeName);
    if (category != null) {
      query.filter(SettingAttributeKeys.category, category);
    }
    SettingAttribute settingAttribute = query.get();
    if (null != settingAttribute) {
      setFeatureFlagIfRequired(settingAttribute);
    }
    return settingAttribute;
  }

  @Override
  public SettingAttribute fetchSettingAttributeByName(
      String accountId, String attributeName, SettingVariableTypes settingVariableTypes) {
    SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                            .filter(SettingAttributeKeys.accountId, accountId)
                                            .filter(SettingAttributeKeys.appId, GLOBAL_APP_ID)
                                            .filter(SettingAttributeKeys.envId, GLOBAL_ENV_ID)
                                            .filter(SettingAttributeKeys.name, attributeName)
                                            .filter(SettingAttributeKeys.value_type, settingVariableTypes.name())
                                            .get();
    setFeatureFlagIfRequired(settingAttribute);
    return settingAttribute;
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#createDefaultApplicationSettings(java.lang.String)
   */
  @Override
  public void createDefaultApplicationSettings(String appId, String accountId, boolean syncFromGit) {
    SettingAttribute settingAttribute1 = aSettingAttribute()
                                             .withAppId(appId)
                                             .withAccountId(accountId)
                                             .withEnvId(GLOBAL_ENV_ID)
                                             .withName(WINDOWS_RUNTIME_PATH)
                                             .withValue(aStringValue().withValue(DEFAULT_WINDOWS_RUNTIME_PATH).build())
                                             .build();
    wingsPersistence.save(settingAttribute1);

    SettingAttribute settingAttribute2 = aSettingAttribute()
                                             .withAppId(appId)
                                             .withAccountId(accountId)
                                             .withEnvId(GLOBAL_ENV_ID)
                                             .withName(RUNTIME_PATH)
                                             .withValue(aStringValue().withValue(DEFAULT_RUNTIME_PATH).build())
                                             .build();
    wingsPersistence.save(settingAttribute2);

    SettingAttribute settingAttribute3 = aSettingAttribute()
                                             .withAppId(appId)
                                             .withAccountId(accountId)
                                             .withEnvId(GLOBAL_ENV_ID)
                                             .withName(STAGING_PATH)
                                             .withValue(aStringValue().withValue(DEFAULT_STAGING_PATH).build())
                                             .build();
    wingsPersistence.save(settingAttribute3);

    SettingAttribute settingAttribute4 = aSettingAttribute()
                                             .withAppId(appId)
                                             .withAccountId(accountId)
                                             .withEnvId(GLOBAL_ENV_ID)
                                             .withName(BACKUP_PATH)
                                             .withValue(aStringValue().withValue(DEFAULT_BACKUP_PATH).build())
                                             .build();
    wingsPersistence.save(settingAttribute4);

    yamlPushService.pushYamlChangeSet(
        settingAttribute1.getAccountId(), null, settingAttribute1, Type.CREATE, syncFromGit, false);
    yamlPushService.pushYamlChangeSet(
        settingAttribute2.getAccountId(), null, settingAttribute2, Type.CREATE, syncFromGit, false);
    yamlPushService.pushYamlChangeSet(
        settingAttribute3.getAccountId(), null, settingAttribute3, Type.CREATE, syncFromGit, false);
    yamlPushService.pushYamlChangeSet(
        settingAttribute4.getAccountId(), null, settingAttribute4, Type.CREATE, syncFromGit, false);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#getSettingAttributesByType(java.lang.String,
   * software.wings.settings.SettingValue.SettingVariableTypes)
   */
  @Override
  public List<SettingAttribute> getSettingAttributesByType(String appId, String type) {
    return getSettingAttributesByType(appId, GLOBAL_ENV_ID, type);
  }

  @Override
  public List<SettingAttribute> getFilteredSettingAttributesByType(
      String appId, String type, String currentAppId, String currentEnvId) {
    return getFilteredSettingAttributesByType(appId, GLOBAL_ENV_ID, type, currentAppId, currentEnvId);
  }

  @Override
  public List<SettingAttribute> getSettingAttributesByType(String appId, String envId, String type) {
    PageRequest<SettingAttribute> pageRequest;
    if (appId == null || appId.equals(GLOBAL_APP_ID)) {
      pageRequest = aPageRequest()
                        .addFilter("appId", EQ, GLOBAL_APP_ID)
                        .addFilter("envId", EQ, GLOBAL_ENV_ID)
                        .addFilter("value.type", EQ, type)
                        .build();
    } else {
      Application application = appService.get(appId);
      pageRequest = aPageRequest()
                        .addFilter("accountId", EQ, application.getAccountId())
                        .addFilter("envId", EQ, GLOBAL_ENV_ID)
                        .addFilter("value.type", EQ, type)
                        .build();
    }

    return wingsPersistence.query(SettingAttribute.class, pageRequest).getResponse();
  }

  @Override
  public List<SettingAttribute> getFilteredSettingAttributesByType(
      String appId, String envId, String type, String currentAppId, String currentEnvId) {
    List<SettingAttribute> settingAttributeList = getSettingAttributesByType(appId, envId, type);
    return getFilteredSettingAttributes(settingAttributeList, currentAppId, currentEnvId, false);
  }

  @Override
  public List<SettingAttribute> getSettingAttributesByType(String accountId, String appId, String envId, String type) {
    List<SettingAttribute> settingAttributes = new ArrayList<>();

    try (HIterator<SettingAttribute> iterator = new HIterator(wingsPersistence.createQuery(SettingAttribute.class)
                                                                  .filter(SettingAttributeKeys.accountId, accountId)
                                                                  .filter(SettingAttributeKeys.appId, appId)
                                                                  .filter(SettingAttributeKeys.envId, envId)
                                                                  .filter(SettingAttributeKeys.value_type, type)
                                                                  .order(SettingAttributeKeys.name)
                                                                  .fetch())) {
      while (iterator.hasNext()) {
        settingAttributes.add(iterator.next());
      }
    }

    return settingAttributes;
  }

  @Override
  public List<SettingAttribute> getGlobalSettingAttributesByType(String accountId, String type) {
    PageRequest<SettingAttribute> pageRequest =
        aPageRequest().addFilter("accountId", EQ, accountId).addFilter("value.type", EQ, type).build();
    List<SettingAttribute> response = wingsPersistence.query(SettingAttribute.class, pageRequest).getResponse();
    if (isNotEmpty(response)) {
      response.forEach(r -> setFeatureFlagIfRequired(r));
    }
    return response;
  }

  @Override
  public List<SettingAttribute> getFilteredGlobalSettingAttributesByType(
      String accountId, String type, String currentAppId, String currentEnvId) {
    List<SettingAttribute> settingAttributeList = getGlobalSettingAttributesByType(accountId, type);
    return getFilteredSettingAttributes(settingAttributeList, currentAppId, currentEnvId, false);
  }

  @Override
  public SettingValue getSettingValueById(String accountId, String id) {
    SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                            .filter(SettingAttributeKeys.uuid, id)
                                            .filter(SettingAttributeKeys.accountId, accountId)
                                            .get();
    if (settingAttribute != null) {
      setFeatureFlagIfRequired(settingAttribute);
      return settingAttribute.getValue();
    }
    return null;
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(SettingAttribute.class).filter(SettingAttributeKeys.accountId, accountId));
  }

  @Override
  public void deleteSettingAttributesByType(String accountId, String appId, String envId, String type) {
    wingsPersistence.delete(wingsPersistence.createQuery(SettingAttribute.class)
                                .filter(SettingAttributeKeys.accountId, accountId)
                                .filter("appId", appId)
                                .filter(SettingAttributeKeys.envId, envId)
                                .filter("value.type", type));
  }

  @Override
  public void pruneByApplication(String appId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(SettingAttribute.class).filter(SettingAttributeKeys.appId, appId));
  }

  @Override
  public GitConfig fetchGitConfigFromConnectorId(String gitConnectorId) {
    if (isBlank(gitConnectorId)) {
      return null;
    }

    SettingAttribute gitSettingAttribute = get(gitConnectorId);

    if (gitSettingAttribute == null || !(gitSettingAttribute.getValue() instanceof GitConfig)) {
      throw new InvalidRequestException("Git connector not found", USER);
    }

    GitConfig gitConfig = (GitConfig) gitSettingAttribute.getValue();
    gitConfigHelperService.setSshKeySettingAttributeIfNeeded(gitConfig);
    return gitConfig;
  }

  @Override
  public String fetchAccountIdBySettingId(String settingId) {
    SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                            .filter(SettingAttributeKeys.uuid, settingId)
                                            .project(SettingAttributeKeys.accountId, true)
                                            .get();
    if (settingAttribute == null) {
      throw new InvalidRequestException(format("Setting attribute %s not found", settingId), USER);
    }
    return settingAttribute.getAccountId();
  }

  @Override
  public UsageRestrictions getUsageRestrictionsForSettingId(String settingId) {
    SettingAttribute settingAttribute = get(settingId);
    if (settingAttribute == null) {
      throw new InvalidRequestException(format("Setting attribute %s not found", settingId), USER);
    }

    // TODO: ASR: This is only used for multi-artifact right now. When enabling that, we might need to change this to
    // use runtime usage restrictions.
    return getUsageRestrictions(settingAttribute);
  }

  @Override
  public void pruneBySettingAttribute(String appId, String settingId) {
    // Delete all artifact stream bindings and artifact streams
    wingsPersistence.createQuery(ArtifactStream.class)
        .filter(ArtifactStreamKeys.appId, appId)
        .filter(ArtifactStreamKeys.settingId, settingId)
        .asList()
        .forEach(artifactStream -> {
          // remove artifact stream bindings
          removeArtifactStreamBindings(artifactStream);
          artifactStreamService.pruneArtifactStream(artifactStream);
          auditServiceHelper.reportDeleteForAuditing(appId, artifactStream);
        });
  }

  private void removeArtifactStreamBindings(ArtifactStream artifactStream) {
    // TODO: Might require extra yaml push.
    List<ServiceVariable> serviceVariables =
        artifactStreamServiceBindingService.fetchArtifactServiceVariableByArtifactStreamId(
            artifactStream.getAccountId(), artifactStream.getUuid());
    if (isNotEmpty(serviceVariables)) {
      for (ServiceVariable serviceVariable : serviceVariables) {
        List<String> allowedList = serviceVariable.getAllowedList();
        allowedList.remove(artifactStream.getUuid());
        // TODO: remove artifact variable if allowedList is empty
        serviceVariable.setAllowedList(allowedList);
        serviceVariableService.updateWithChecks(serviceVariable.getAppId(), serviceVariable.getUuid(), serviceVariable);
      }
    }

    List<Workflow> workflows = artifactStreamServiceBindingService.listWorkflows(artifactStream.getUuid());
    if (isNotEmpty(workflows)) {
      for (Workflow workflow : workflows) {
        if (workflow.getOrchestrationWorkflow() != null
            && isNotEmpty(workflow.getOrchestrationWorkflow().getUserVariables())) {
          List<Variable> userVariables = workflow.getOrchestrationWorkflow().getUserVariables();
          List<Variable> updatedUserVariables = new ArrayList<>();
          for (Variable userVariable : userVariables) {
            String allowedValues = userVariable.getAllowedValues();
            List<String> allowedValuesList = CsvParser.parse(allowedValues);
            allowedValuesList.remove(artifactStream.getUuid());
            // TODO: remove artifact variable if allowedList is empty
            userVariable.setAllowedValues(join(",", allowedValuesList));
            updatedUserVariables.add(userVariable);
          }

          workflowService.updateUserVariables(workflow.getAppId(), workflow.getUuid(), updatedUserVariables);
          List<String> linkedArtifactStreamIds = workflow.getLinkedArtifactStreamIds();
          linkedArtifactStreamIds.remove(artifactStream.getUuid());
          workflow.setLinkedArtifactStreamIds(linkedArtifactStreamIds);
          workflowService.updateWorkflow(workflow, false);
        }
      }
    }
  }

  @Override
  public void openConnectivityErrorAlert(
      String accountId, String settingId, String settingCategory, String connectivityError) {
    AlertData alertData = SettingAttributeValidationFailedAlert.builder()
                              .settingId(settingId)
                              .settingCategory(settingCategory)
                              .connectivityError(connectivityError)
                              .build();
    alertService.closeAllAlerts(accountId, null, AlertType.SETTING_ATTRIBUTE_VALIDATION_FAILED, alertData);
    alertService.openAlert(accountId, null, AlertType.SETTING_ATTRIBUTE_VALIDATION_FAILED, alertData);
  }

  @Override
  public void closeConnectivityErrorAlert(String accountId, String settingId) {
    AlertData alertData = SettingAttributeValidationFailedAlert.builder().settingId(settingId).build();
    alertService.closeAllAlerts(accountId, null, AlertType.SETTING_ATTRIBUTE_VALIDATION_FAILED, alertData);
  }

  @Override
  @VisibleForTesting
  public boolean isOpenSSHKeyUsed(SettingAttribute settingAttribute) {
    return SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES.name().equals(settingAttribute.getValue().getType())
        && HostConnectionAttributes.ConnectionType.SSH.name().equals(
            ((HostConnectionAttributes) settingAttribute.getValue()).getConnectionType().name())
        && null != ((HostConnectionAttributes) settingAttribute.getValue()).getKey();
  }

  @VisibleForTesting
  @Override
  public void restrictOpenSSHKey(SettingAttribute settingAttribute) {
    String openSSHkey = new String(((HostConnectionAttributes) settingAttribute.getValue()).getKey());
    if (openSSHkey.contains(OPEN_SSH)) {
      throw new InvalidRequestException(
          "An OpenSSH key might not work with Harness. Please use an RSA key. See Harness documentation for help.");
    }
  }

  @Override
  public String getSSHKeyName(String sshSettingId) {
    SettingAttribute settingAttribute = getById(sshSettingId);
    return settingAttribute.getName();
  }

  @Override
  public String getSSHSettingId(String accountId, String sshKeyName) {
    SettingAttribute settingAttributeByName = getSettingAttributeByName(accountId, sshKeyName);
    if (settingAttributeByName == null) {
      throw new InvalidRequestException("No setting attribute with given keyName", USER);
    }
    return settingAttributeByName.getUuid();
  }
}
