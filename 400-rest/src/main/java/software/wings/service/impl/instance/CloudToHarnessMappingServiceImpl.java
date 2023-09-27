/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.cluster.entities.ClusterType.AWS_ECS;
import static io.harness.ccm.cluster.entities.ClusterType.DIRECT_KUBERNETES;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.ccm.cluster.dao.ClusterRecordDao;
import io.harness.ccm.cluster.entities.CEUserInfo;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.cluster.entities.ClusterRecord.ClusterRecordKeys;
import io.harness.ccm.cluster.entities.DirectKubernetesCluster;
import io.harness.ccm.cluster.entities.EcsCluster;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.config.GcpBillingAccount;
import io.harness.ccm.config.GcpBillingAccount.GcpBillingAccountKeys;
import io.harness.ccm.config.GcpServiceAccount;
import io.harness.ccm.config.GcpServiceAccount.GcpServiceAccountKeys;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery;
import io.harness.security.EncryptionUtils;

import software.wings.api.DeploymentSummary;
import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.HarnessTagLink.HarnessTagLinkKeys;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import software.wings.beans.LicenseInfo;
import software.wings.beans.ResourceLookup;
import software.wings.beans.ResourceLookup.ResourceLookupKeys;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.User;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata;
import software.wings.service.impl.LicenseUtils;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;

@Singleton
@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class CloudToHarnessMappingServiceImpl implements CloudToHarnessMappingService {
  private final HPersistence persistence;
  private final WingsPersistence wingsPersistence;
  private final DeploymentService deploymentService;
  private final CEMetadataRecordDao ceMetadataRecordDao;
  private final FeatureFlagService featureFlagService;
  private ClusterRecordDao clusterRecordDao;

  private static final String EXC_MSG_USER_DOESNT_EXIST = "User does not exist";

  @Inject
  public CloudToHarnessMappingServiceImpl(HPersistence persistence, WingsPersistence wingsPersistence,
      DeploymentService deploymentService, CEMetadataRecordDao ceMetadataRecordDao, ClusterRecordDao clusterRecordDao,
      FeatureFlagService featureFlagService) {
    this.persistence = persistence;
    this.wingsPersistence = wingsPersistence;
    this.deploymentService = deploymentService;
    this.ceMetadataRecordDao = ceMetadataRecordDao;
    this.clusterRecordDao = clusterRecordDao;
    this.featureFlagService = featureFlagService;
  }

  @Override
  public Optional<HarnessServiceInfo> getHarnessServiceInfo(DeploymentSummary deploymentSummary) {
    return getHarnessServiceInfo(deploymentService.getWithAccountId(deploymentSummary));
  }

  private Optional<HarnessServiceInfo> getHarnessServiceInfo(Optional<DeploymentSummary> summary) {
    if (summary.isPresent()) {
      DeploymentSummary deploymentSummaryResponse = summary.get();
      InfrastructureMapping infrastructureMapping =
          persistence.createQuery(InfrastructureMapping.class)
              .filter(InfrastructureMappingKeys.uuid, deploymentSummaryResponse.getInfraMappingId())
              .get();

      if (infrastructureMapping != null) {
        return Optional.of(
            new HarnessServiceInfo(infrastructureMapping.getServiceId(), infrastructureMapping.getAppId(),
                infrastructureMapping.getComputeProviderSettingId(), infrastructureMapping.getEnvId(),
                deploymentSummaryResponse.getInfraMappingId(), deploymentSummaryResponse.getUuid()));
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<HarnessServiceInfo> getHarnessServiceInfo(
      String accountId, String computeProviderId, String namespace, String podName) {
    Instance instance = persistence.createQuery(Instance.class, excludeValidate)
                            .filter(InstanceKeys.accountId, accountId)
                            .filter(InstanceKeys.computeProviderId, computeProviderId)
                            .filter(InstanceKeys.instanceInfoNamespace, namespace)
                            .filter(InstanceKeys.instanceInfoPodName, podName)
                            .order(Sort.descending(InstanceKeys.createdAt))
                            .get();

    if (null != instance) {
      return getHarnessServiceInfo(deploymentService.getWithInfraMappingId(accountId, instance.getInfraMappingId()));
    }
    return Optional.empty();
  }

  @Override
  public Optional<SettingAttribute> getSettingAttribute(String id) {
    return Optional.ofNullable(
        persistence.createQuery(SettingAttribute.class).filter(SettingAttributeKeys.uuid, id).get());
  }

  @Override
  public List<SettingAttribute> getCEConnectors(String accountId) {
    return persistence.createQuery(SettingAttribute.class)
        .filter(SettingAttributeKeys.accountId, accountId)
        .filter(SettingAttributeKeys.category, SettingCategory.CE_CONNECTOR)
        .asList();
  }

  @Override
  public List<HarnessTagLink> getTagLinksWithEntityId(String accountId, String entityId) {
    return wingsPersistence.createQuery(HarnessTagLink.class)
        .filter(HarnessTagLinkKeys.accountId, accountId)
        .filter(HarnessTagLinkKeys.entityId, entityId)
        .asList();
  }

  @Override
  public List<HarnessServiceInfo> getHarnessServiceInfoList(List<DeploymentSummary> deploymentSummaryList) {
    List<String> infraMappingIds =
        deploymentSummaryList.stream().map(DeploymentSummary::getInfraMappingId).collect(Collectors.toList());
    List<InfrastructureMapping> infrastructureMappings =
        persistence.createQuery(InfrastructureMapping.class, excludeAuthority)
            .field(InfrastructureMappingKeys.uuid)
            .in(infraMappingIds)
            .asList();
    return infrastructureMappings.stream()
        .map(infrastructureMapping
            -> new HarnessServiceInfo(infrastructureMapping.getServiceId(), infrastructureMapping.getAppId(),
                infrastructureMapping.getComputeProviderSettingId(), infrastructureMapping.getEnvId(),
                infrastructureMapping.getUuid(), null))
        .collect(Collectors.toList());
  }

  @Override
  public List<Account> getCeEnabledAccounts() {
    List<Account> accounts = new ArrayList<>();
    Query<Account> query =
        persistence.createQuery(Account.class, excludeAuthority).filter(AccountKeys.cloudCostEnabled, Boolean.TRUE);
    try (HIterator<Account> accountItr = new HIterator<>(query.fetch())) {
      for (Account account : accountItr) {
        accounts.add(account);
      }
    }
    return accounts;
  }

  @Override
  public Account getAccountInfoFromId(String accountId) {
    Account defaultAccount = Account.Builder.anAccount().withAccountName(accountId).build();
    try (HIterator<Account> query = new HIterator<>(persistence.createQuery(Account.class, excludeAuthority)
                                                        .filter(AccountKeys.cloudCostEnabled, Boolean.TRUE)
                                                        .field(AccountKeys.uuid)
                                                        .equal(accountId)
                                                        .fetch())) {
      for (Account account : query) {
        if (account.getUuid().equals(accountId)) {
          return account;
        }
      }
    }
    return defaultAccount;
  }

  @Override
  public List<ResourceLookup> getResourceList(String accountId, List<String> resourceIds) {
    return persistence.createQuery(ResourceLookup.class)
        .filter(ResourceLookupKeys.accountId, accountId)
        .field(ResourceLookupKeys.resourceId)
        .in(resourceIds)
        .asList();
  }

  @Override
  public Map<String, String> getServiceName(String accountId, List<String> serviceIds) {
    List<Service> services = persistence.createQuery(Service.class)
                                 .filter(ServiceKeys.accountId, accountId)
                                 .field(ServiceKeys.uuid)
                                 .in(serviceIds)
                                 .asList();
    return services.stream().collect(Collectors.toMap(Service::getUuid, Service::getName));
  }

  @Override
  public Map<String, String> getEnvName(String accountId, List<String> envIds) {
    List<Environment> environments = persistence.createQuery(Environment.class)
                                         .filter(EnvironmentKeys.accountId, accountId)
                                         .field(EnvironmentKeys.uuid)
                                         .in(envIds)
                                         .asList();
    return environments.stream().collect(Collectors.toMap(Environment::getUuid, Environment::getName));
  }

  @Override
  public List<DeploymentSummary> getDeploymentSummary(
      String accountId, String offset, Instant startTime, Instant endTime) {
    return deploymentService.getDeploymentSummary(accountId, offset, startTime, endTime);
  }

  @Override
  public SettingAttribute getFirstSettingAttributeByCategory(String accountId, SettingCategory category) {
    return persistence.createQuery(SettingAttribute.class, excludeAuthority)
        .filter(SettingAttributeKeys.accountId, accountId)
        .filter(SettingAttributeKeys.category, category)
        .order(Sort.ascending(SettingAttributeKeys.createdAt))
        .get();
  }

  @Override
  public List<SettingAttribute> listSettingAttributesCreatedInDuration(
      String accountId, SettingCategory category, SettingVariableTypes valueType) {
    List<SettingAttribute> settingAttributes = new ArrayList<>();
    try (HIterator<SettingAttribute> query =
             new HIterator<>(persistence.createQuery(SettingAttribute.class, EnumSet.of(HQuery.QueryChecks.COUNT))
                                 .filter(SettingAttributeKeys.accountId, accountId)
                                 .filter(SettingAttributeKeys.category, category)
                                 .filter(SettingAttributeKeys.value_type, valueType)
                                 .fetch())) {
      log.info("Fetching settingAttributes query: {}", query.toString());
      for (SettingAttribute settingAttribute : query) {
        settingAttributes.add(settingAttribute);
      }
    }
    return settingAttributes;
  }

  @Override
  public List<SettingAttribute> listSettingAttributesCreatedInDuration(
      String accountId, SettingCategory category, SettingVariableTypes valueType, long startTime, long endTime) {
    List<SettingAttribute> settingAttributes = new ArrayList<>();
    try (HIterator<SettingAttribute> query =
             new HIterator<>(persistence.createQuery(SettingAttribute.class, excludeAuthority)
                                 .filter(SettingAttributeKeys.accountId, accountId)
                                 .filter(SettingAttributeKeys.category, category)
                                 .filter(SettingAttributeKeys.value_type, valueType)
                                 .field(SettingAttributeKeys.createdAt)
                                 .greaterThan(startTime)
                                 .field(SettingAttributeKeys.createdAt)
                                 .lessThan(endTime)
                                 .fetch())) {
      log.info("Fetching settingAttributes query: {}", query.toString());
      for (SettingAttribute settingAttribute : query) {
        settingAttributes.add(settingAttribute);
      }
    }
    return settingAttributes;
  }

  public List<GcpBillingAccount> listGcpBillingAccountUpdatedInDuration(String accountId) {
    Query<GcpBillingAccount> query = persistence.createQuery(GcpBillingAccount.class, excludeAuthority)
                                         .filter(GcpBillingAccountKeys.accountId, accountId);
    log.info("Query listGcpBillingAccountUpdatedInDuration {}", query.toString());
    return query.asList();
  }

  public GcpServiceAccount getGcpServiceAccount(String accountId) {
    Query<GcpServiceAccount> query =
        persistence.createQuery(GcpServiceAccount.class).field(GcpServiceAccountKeys.accountId).equal(accountId);
    return query.get();
  }

  @Override
  public String getEntityName(BillingDataQueryMetadata.BillingDataMetaDataFields field, String entityId) {
    switch (field) {
      case APPID:
      case ENVID:
      case SERVICEID:
      case CLUSTERID:
        return fetchEntityName(field, entityId);
      case CLOUDSERVICENAME:
      case TASKID:
      case WORKLOADNAME:
      case NAMESPACE:
      case CLUSTERNAME:
        return entityId;
      default:
        throw new InvalidRequestException("Invalid EntityType " + field);
    }
  }

  private String fetchEntityName(BillingDataQueryMetadata.BillingDataMetaDataFields field, String entityId) {
    switch (field) {
      case APPID:
        return getApplicationName(entityId);
      case ENVID:
        return getEnvironmentName(entityId);
      case SERVICEID:
        return getServiceName(entityId);
      case CLUSTERID:
        return getClusterName(entityId);
      default:
        throw new InvalidRequestException("Invalid EntityType " + field);
    }
  }

  public String getApplicationName(String entityId) {
    try {
      Application app = persistence.get(Application.class, entityId);
      if (app != null) {
        return app.getName();
      } else {
        return entityId;
      }
    } catch (Exception e) {
      log.info("Entity Id could not be converted : ", e);
      return entityId;
    }
  }

  public String getServiceName(String entityId) {
    try {
      Service service = persistence.get(Service.class, entityId);
      if (service != null) {
        return service.getName();
      } else {
        return entityId;
      }
    } catch (Exception e) {
      log.info("Entity Id could not be converted : ", e);
      return entityId;
    }
  }

  public String getEnvironmentName(String entityId) {
    try {
      Environment env = persistence.get(Environment.class, entityId);
      if (env != null) {
        return env.getName();
      } else {
        return entityId;
      }
    } catch (Exception e) {
      log.info("Entity Id could not be converted : ", e);
      return entityId;
    }
  }

  @Override
  public ClusterRecord getClusterRecord(String clusterId) {
    return clusterRecordDao.get(clusterId);
  }

  private String getClusterName(String entityId) {
    try {
      Cluster cluster = getCluster(entityId).getCluster();
      if (cluster != null) {
        if (cluster.getClusterType().equals(AWS_ECS)) {
          EcsCluster ecsCluster = (EcsCluster) cluster;
          if (null != ecsCluster.getClusterName()) {
            return ecsCluster.getClusterName();
          } else {
            return entityId;
          }
        } else if (cluster.getClusterType().equals(DIRECT_KUBERNETES)) {
          DirectKubernetesCluster kubernetesCluster = (DirectKubernetesCluster) cluster;
          String clusterName = kubernetesCluster.getClusterName();
          if (null == clusterName || clusterName.equals("")) {
            SettingAttribute settingAttribute = getSettingAttributeForCluster(kubernetesCluster.getCloudProviderId());
            clusterName = settingAttribute.getName();
          }
          return clusterName;
        } else {
          return entityId;
        }
      } else {
        return entityId;
      }
    } catch (Exception e) {
      log.info("Entity Id could not be converted : ", e);
      return entityId;
    }
  }

  private ClusterRecord getCluster(String clusterId) {
    Query<ClusterRecord> query = persistence.createQuery(ClusterRecord.class, excludeValidate)
                                     .filter(ClusterRecordKeys.uuid, new ObjectId(clusterId));
    return query.get();
  }

  private SettingAttribute getSettingAttributeForCluster(String varId) {
    return persistence.get(SettingAttribute.class, varId);
  }

  @Override
  public UserGroup getUserGroup(String accountId, String userGroupId, boolean loadUsers) {
    return wingsPersistence.createQuery(UserGroup.class)
        .filter(UserGroupKeys.accountId, accountId)
        .filter(UserGroup.ID_KEY2, userGroupId)
        .get();
  }

  @Override
  public User getUser(String userId) {
    User user = wingsPersistence.createQuery(User.class).filter(UserGroup.ID_KEY2, userId).get();
    if (user == null) {
      throw new UnauthorizedException(EXC_MSG_USER_DOESNT_EXIST, USER);
    }

    List<Account> accounts = user.getAccounts();
    if (isNotEmpty(accounts)) {
      accounts.forEach(account -> decryptLicenseInfo(account, false));
    }

    return user;
  }

  @Override
  public CEUserInfo getUserForCluster(String clusterId) {
    Query<ClusterRecord> clusterQuery = persistence.createQuery(ClusterRecord.class, excludeValidate)
                                            .filter(ClusterRecordKeys.uuid, new ObjectId(clusterId));
    ClusterRecord cluster = clusterQuery.get();
    if (cluster == null) {
      return null;
    }
    String cloudProviderId = cluster.getCluster().getCloudProviderId();

    Query<SettingAttribute> cloudProviderQuery =
        persistence.createQuery(SettingAttribute.class).filter(SettingAttributeKeys.uuid, cloudProviderId);
    SettingAttribute cloudProvider = cloudProviderQuery.get();

    if (cloudProvider != null && cloudProvider.getCreatedBy() != null) {
      return CEUserInfo.builder()
          .name(cloudProvider.getCreatedBy().getName())
          .email(cloudProvider.getCreatedBy().getEmail())
          .build();
    }
    return null;
  }

  public Account decryptLicenseInfo(Account account, boolean setExpiry) {
    if (account == null) {
      return null;
    }

    byte[] encryptedLicenseInfo = account.getEncryptedLicenseInfo();
    if (isNotEmpty(encryptedLicenseInfo)) {
      byte[] decryptedBytes = EncryptionUtils.decrypt(encryptedLicenseInfo, null);
      if (isNotEmpty(decryptedBytes)) {
        LicenseInfo licenseInfo = LicenseUtils.convertToObject(decryptedBytes, setExpiry);
        account.setLicenseInfo(licenseInfo);
      } else {
        log.error("Error while decrypting license info. Deserialized object is not instance of LicenseInfo");
      }
    }

    return account;
  }

  public List<Account> getCeAccountsWithLicense() {
    return persistence.createQuery(Account.class, excludeAuthority).field(AccountKeys.ceLicenseInfo).exists().asList();
  }

  @Override
  public List<UserGroup> listUserGroupsForAccount(String accountId) {
    List<UserGroup> userGroups = new ArrayList<>();
    for (UserGroup userGroup : wingsPersistence.createQuery(UserGroup.class)
                                   .filter(UserGroupKeys.accountId, accountId)
                                   .fetch(createFindOptionsToHitSecondaryNode(accountId))) {
      if (userGroup.getAccountPermissions() != null && userGroup.getMemberIds() != null) {
        userGroups.add(userGroup);
      }
    }
    return userGroups;
  }

  @Override
  public CEMetadataRecord upsertCEMetaDataRecord(CEMetadataRecord ceMetadataRecord) {
    return ceMetadataRecordDao.upsert(ceMetadataRecord);
  }

  @Override
  public List<Delegate> obtainDelegateDetails(String accountId, List<String> delegateIds) {
    return persistence.createQuery(Delegate.class)
        .filter(DelegateKeys.accountId, accountId)
        .field(DelegateKeys.uuid)
        .in(delegateIds)
        .asList();
  }

  @Override
  public List<ClusterRecord> listCeEnabledClusters(String accountId) {
    return clusterRecordDao.listCeEnabledClusters(accountId);
  }

  private FindOptions createFindOptionsToHitSecondaryNode(String accountId) {
    if (accountId != null && featureFlagService.isEnabled(FeatureName.CDS_QUERY_OPTIMIZATION, accountId)) {
      return new FindOptions().readPreference(ReadPreference.secondaryPreferred());
    }
    return new FindOptions();
  }
}
