/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.instance;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.cluster.entities.CEUserInfo;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.config.GcpBillingAccount;
import io.harness.ccm.config.GcpServiceAccount;
import io.harness.delegate.beans.Delegate;

import software.wings.api.DeploymentSummary;
import software.wings.beans.Account;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.ResourceLookup;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata;
import software.wings.settings.SettingVariableTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public interface CloudToHarnessMappingService {
  Optional<HarnessServiceInfo> getHarnessServiceInfo(DeploymentSummary deploymentSummary);

  Optional<HarnessServiceInfo> getHarnessServiceInfo(
      String accountId, String computeProviderId, String namespace, String podName);

  Optional<SettingAttribute> getSettingAttribute(String id);

  List<HarnessTagLink> getTagLinksWithEntityId(String accountId, String entityId);

  List<HarnessServiceInfo> getHarnessServiceInfoList(List<DeploymentSummary> deploymentSummaryList);

  List<Account> getCeEnabledAccounts();

  Account getAccountInfoFromId(String accountId);

  List<ResourceLookup> getResourceList(String accountId, List<String> resourceIds);

  Map<String, String> getServiceName(String accountId, List<String> serviceIds);

  Map<String, String> getEnvName(String accountId, List<String> envIds);

  List<DeploymentSummary> getDeploymentSummary(String accountId, String offset, Instant startTime, Instant endTime);

  SettingAttribute getFirstSettingAttributeByCategory(String accountId, SettingCategory category);

  List<SettingAttribute> listSettingAttributesCreatedInDuration(
      String accountId, SettingCategory category, SettingVariableTypes valueType);

  List<SettingAttribute> listSettingAttributesCreatedInDuration(
      String accountId, SettingCategory category, SettingVariableTypes valueType, long startTime, long endTime);

  List<GcpBillingAccount> listGcpBillingAccountUpdatedInDuration(String accountId);

  GcpServiceAccount getGcpServiceAccount(String accountId);

  String getEntityName(BillingDataQueryMetadata.BillingDataMetaDataFields field, String entityId);

  UserGroup getUserGroup(String accountId, String userGroupId, boolean loadUsers);

  User getUser(String userId);

  List<Account> getCeAccountsWithLicense();

  List<SettingAttribute> getCEConnectors(String accountId);

  CEUserInfo getUserForCluster(String clusterId);

  List<UserGroup> listUserGroupsForAccount(String accountId);

  CEMetadataRecord upsertCEMetaDataRecord(CEMetadataRecord ceMetadataRecord);

  String getApplicationName(String entityId);

  String getServiceName(String entityId);

  String getEnvironmentName(String entityId);

  ClusterRecord getClusterRecord(String clusterId);

  List<Delegate> obtainDelegateDetails(String accountId, List<String> delegateIds);
}
