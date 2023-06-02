/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.ValidationResult;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.ownership.OwnedByAccount;
import software.wings.service.intfc.ownership.OwnedByApplication;
import software.wings.service.intfc.ownership.OwnedBySettingAttribute;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.ArtifactType;

import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface SettingsService extends OwnedByAccount, OwnedBySettingAttribute, OwnedByApplication {
  /**
   * List.
   *
   * @param req the req
   * @param appIdFromRequest
   * @param envIdFromRequest
   * @param forUsageInNewApp
   * @return the page response
   */
  PageResponse<SettingAttribute> list(
      PageRequest<SettingAttribute> req, String appIdFromRequest, String envIdFromRequest, boolean forUsageInNewApp);

  PageResponse<SettingAttribute> list(
      PageRequest<SettingAttribute> req, String appIdFromRequest, String accountIdFromRequest);

  PageResponse<SettingAttribute> list(PageRequest<SettingAttribute> req, String appIdFromRequest,
      String envIdFromRequest, String accountId, boolean gitSshConfigOnly, boolean withArtifactStreamCount,
      String artifactStreamSearchString, int maxArtifactStreams, ArtifactType artifactType, boolean forUsageInNewApp);

  List<SettingAttribute> listAllSettingAttributesByType(String accountId, String type);

  List<String> getSettingIdsForAccount(String accountId);

  List<SettingAttribute> list(String accountId, SettingCategory category);

  List<SettingAttribute> getFilteredSettingAttributes(List<SettingAttribute> inputSettingAttributes,
      String appIdFromRequest, String envIdFromRequest, boolean forUsageInNewApp);

  @ValidationGroups(Create.class) SettingAttribute save(@Valid SettingAttribute settingAttribute);

  @ValidationGroups(Create.class)
  SettingAttribute saveWithPruning(SettingAttribute settingAttribute, String appId, String accountId);

  @ValidationGroups(Create.class) SettingAttribute forceSave(@Valid SettingAttribute settingAttribute);

  @ValidationGroups(Create.class) SettingAttribute save(@Valid SettingAttribute settingAttribute, boolean pushToGit);

  SettingAttribute get(String appId, String varId);

  SettingAttribute get(String appId, String envId, String varId);

  SettingAttribute get(String varId);

  SettingAttribute getWithRbac(String id);

  SettingAttribute getByAccount(String accountId, String varId);

  SettingAttribute getByAccountAndId(String accountId, String settingId);

  SettingAttribute getOnlyConnectivityError(String settingId);

  SettingAttribute getSettingAttributeByName(String accountId, String settingAttributeName);

  void checkRbacOnSettingAttribute(String appId, SettingAttribute settingAttribute);

  @ValidationGroups(Update.class) SettingAttribute update(@Valid SettingAttribute settingAttribute);

  @ValidationGroups(Update.class)
  SettingAttribute updateWithSettingFields(SettingAttribute settingAttribute, String attrId, String appId);

  @ValidationGroups(Update.class)
  SettingAttribute update(@Valid SettingAttribute settingAttribute, boolean updateConnectivity);

  @ValidationGroups(Update.class)
  SettingAttribute update(@Valid SettingAttribute settingAttribute, boolean updateConnectivity, boolean pushToGit);

  /**
   * INTERNAL API only no usage restriction is checked. Only update the usage restrictions of the specified setting
   * attribute. This API is primary called during migration of removing dangling app/env references,
   */
  void updateUsageRestrictionsInternal(String uuid, UsageRestrictions usageRestrictions);

  void delete(String appId, String varId);

  void delete(String appId, String varId, boolean pushToGit, boolean syncFromGit);

  boolean retainSelectedGitConnectorsAndDeleteRest(String accountId, List<String> gitConnectorToRetain);

  SettingAttribute getByName(String accountId, String appId, String attributeName);

  SettingAttribute getByName(String accountId, String appId, String envId, String attributeName);

  SettingAttribute getConnectorByName(String accountId, String appId, String attributeName);

  SettingAttribute fetchSettingAttributeByName(
      @NotEmpty String accountId, @NotEmpty String attributeName, @NotNull SettingVariableTypes settingVariableTypes);

  void createDefaultApplicationSettings(String appId, String accountId, boolean syncFromGit);

  List<SettingAttribute> getSettingAttributesByType(String appId, String type);

  List<SettingAttribute> getFilteredSettingAttributesByType(
      String appId, String type, String currentAppId, String currentEnvId);

  List<SettingAttribute> getSettingAttributesByType(String appId, String envId, String type);

  List<SettingAttribute> getFilteredSettingAttributesByType(
      String appId, String envId, String type, String currentAppId, String currentEnvId);

  List<SettingAttribute> getSettingAttributesByType(String accountId, String appId, String envId, String type);

  List<SettingAttribute> getGlobalSettingAttributesByType(String accountId, String type);

  List<SettingAttribute> getFilteredGlobalSettingAttributesByType(
      String accountId, String type, String currentAppId, String currentEnvId);

  void deleteSettingAttributesByType(String accountId, String appId, String envId, String type);

  SettingValue getSettingValueById(String accountId, String id);

  ValidationResult validate(SettingAttribute settingAttribute);

  ValidationResult validateWithPruning(SettingAttribute settingAttribute, String appId, String accountId);

  ValidationResult validate(String varId);

  ValidationResult validateConnectivity(SettingAttribute settingAttribute);

  ValidationResult validateConnectivityWithPruning(SettingAttribute settingAttribute, String appId, String accountId);

  void deleteByYamlGit(String appId, String settingAttributeId, boolean syncFromGit);
  Map<String, String> listAccountDefaults(String accountId);

  Map<String, String> listAppDefaults(String accountId, String appId);

  GitConfig fetchGitConfigFromConnectorId(String gitConnectorId);

  String fetchAccountIdBySettingId(String settingId);

  UsageRestrictions getUsageRestrictionsForSettingId(String settingId);

  void openConnectivityErrorAlert(String accountId, String settingId, String settingCategory, String connectivityError);

  void closeConnectivityErrorAlert(String accountId, String settingId);

  boolean isOpenSSHKeyUsed(SettingAttribute settingAttribute);

  void restrictOpenSSHKey(SettingAttribute settingAttribute);

  String getSSHKeyName(String sshSettingId);

  String getSSHSettingId(String accountId, String sshKeyName);

  CEK8sDelegatePrerequisite validateCEDelegateSetting(String accountId, String delegateName);

  boolean isSettingValueGcp(SettingAttribute settingAttribute);

  boolean hasDelegateSelectorProperty(SettingAttribute settingAttribute);

  List<String> getDelegateSelectors(SettingAttribute settingAttribute);

  List<SettingAttribute> getSettingAttributeByReferencedConnector(String accountId, String settingAttributeUuid);
}
