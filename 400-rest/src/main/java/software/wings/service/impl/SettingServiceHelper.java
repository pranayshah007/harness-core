/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.encryption.EncryptionReflectUtils.getEncryptedRefField;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.SettingAttribute.SettingCategory.AZURE_ARTIFACTS;
import static software.wings.beans.SettingAttribute.SettingCategory.CONNECTOR;
import static software.wings.beans.SettingAttribute.SettingCategory.HELM_REPO;
import static software.wings.beans.SettingAttribute.SettingCategory.SETTING;
import static software.wings.security.PermissionAttribute.PermissionType;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_ACCOUNT_DEFAULTS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CLOUD_PROVIDERS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONNECTORS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SSH_AND_WINRM;
import static software.wings.settings.SettingVariableTypes.AMAZON_S3_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.APM_VERIFICATION;
import static software.wings.settings.SettingVariableTypes.APP_DYNAMICS;
import static software.wings.settings.SettingVariableTypes.ARTIFACTORY;
import static software.wings.settings.SettingVariableTypes.AWS;
import static software.wings.settings.SettingVariableTypes.AZURE;
import static software.wings.settings.SettingVariableTypes.AZURE_ARTIFACTS_PAT;
import static software.wings.settings.SettingVariableTypes.BAMBOO;
import static software.wings.settings.SettingVariableTypes.BUG_SNAG;
import static software.wings.settings.SettingVariableTypes.CE_AZURE;
import static software.wings.settings.SettingVariableTypes.CLOUD_WATCH;
import static software.wings.settings.SettingVariableTypes.DATA_DOG;
import static software.wings.settings.SettingVariableTypes.DATA_DOG_LOG;
import static software.wings.settings.SettingVariableTypes.DOCKER;
import static software.wings.settings.SettingVariableTypes.DYNA_TRACE;
import static software.wings.settings.SettingVariableTypes.ELK;
import static software.wings.settings.SettingVariableTypes.GCP;
import static software.wings.settings.SettingVariableTypes.GCS_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.GIT;
import static software.wings.settings.SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES;
import static software.wings.settings.SettingVariableTypes.HTTP_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.INSTANA;
import static software.wings.settings.SettingVariableTypes.JENKINS;
import static software.wings.settings.SettingVariableTypes.JIRA;
import static software.wings.settings.SettingVariableTypes.KUBERNETES_CLUSTER;
import static software.wings.settings.SettingVariableTypes.LOGZ;
import static software.wings.settings.SettingVariableTypes.NEW_RELIC;
import static software.wings.settings.SettingVariableTypes.NEXUS;
import static software.wings.settings.SettingVariableTypes.OCI_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.PCF;
import static software.wings.settings.SettingVariableTypes.PROMETHEUS;
import static software.wings.settings.SettingVariableTypes.RANCHER;
import static software.wings.settings.SettingVariableTypes.SERVICENOW;
import static software.wings.settings.SettingVariableTypes.SFTP;
import static software.wings.settings.SettingVariableTypes.SMB;
import static software.wings.settings.SettingVariableTypes.SMTP;
import static software.wings.settings.SettingVariableTypes.SPLUNK;
import static software.wings.settings.SettingVariableTypes.SPOT_INST;
import static software.wings.settings.SettingVariableTypes.SUMO;
import static software.wings.settings.SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedUsageRestrictionsException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.CGConstants;
import software.wings.beans.DockerConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.PcfConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._445_CG_CONNECTORS)
public class SettingServiceHelper {
  private static final String REFERENCED_SECRET_ERROR_MSG = "Unable to copy encryption details";
  private static final String USE_ENCRYPTED_VALUE_FLAG_FIELD_BASE = "useEncrypted";
  public static final Set<SettingVariableTypes> ATTRIBUTES_USING_REFERENCES = Sets.immutableEnumSet(AWS, AZURE, GCP,
      KUBERNETES_CLUSTER, PCF, SPOT_INST, APP_DYNAMICS, NEW_RELIC, INSTANA, PROMETHEUS, DATA_DOG, DYNA_TRACE,
      CLOUD_WATCH, DATA_DOG_LOG, BUG_SNAG, ELK, SPLUNK, SUMO, LOGZ, APM_VERIFICATION, JENKINS, BAMBOO, DOCKER, NEXUS,
      ARTIFACTORY, SMB, SFTP, AMAZON_S3_HELM_REPO, GCS_HELM_REPO, HTTP_HELM_REPO, AZURE_ARTIFACTS_PAT, GIT, SMTP, JIRA,
      SERVICENOW, WINRM_CONNECTION_ATTRIBUTES, HOST_CONNECTION_ATTRIBUTES, CE_AZURE, RANCHER, OCI_HELM_REPO);

  @Inject private SecretManager secretManager;
  @Inject private ManagerDecryptionService managerDecryptionService;
  @Inject private UsageRestrictionsService usageRestrictionsService;
  @Inject private AccountService accountService;
  @Inject private FeatureFlagService featureFlagService;

  public boolean hasReferencedSecrets(SettingAttribute settingAttribute) {
    if (settingAttribute == null || settingAttribute.getValue() == null || settingAttribute.getAccountId() == null
        || settingAttribute.getValue().getSettingType() == null
        || !ATTRIBUTES_USING_REFERENCES.contains(settingAttribute.getValue().getSettingType())) {
      return false;
    }

    if (settingAttribute.getUuid() == null) {
      settingAttribute.setSecretsMigrated(true);
    }
    return settingAttribute.isSecretsMigrated();
  }

  public void updateSettingAttributeBeforeResponse(SettingAttribute settingAttribute, boolean maskEncryptedFields) {
    if (settingAttribute == null) {
      return;
    }

    // Update usage restrictions if referenced secrets are used.
    updateUsageRestrictions(settingAttribute);
    SettingValue settingValue = settingAttribute.getValue();
    if (settingValue instanceof EncryptableSetting) {
      if (hasReferencedSecrets(settingAttribute)) {
        // Copy encrypted ref field values (which contain IDs) to the encrypted fields for UI consumption. Don't do
        // masking.
        copyFromEncryptedRefFields((EncryptableSetting) settingValue);
      } else if (maskEncryptedFields) {
        // Mask encrypted fields.
        secretManager.maskEncryptedFields((EncryptableSetting) settingValue);
      }
    }
  }

  public void updateReferencedSecrets(SettingAttribute settingAttribute) {
    if (!hasReferencedSecrets(settingAttribute)) {
      return;
    }

    SettingValue settingValue = settingAttribute.getValue();
    if (!(settingValue instanceof EncryptableSetting)) {
      return;
    }

    EncryptableSetting object = (EncryptableSetting) settingValue;
    boolean wasDecrypted = object.isDecrypted();
    object.setDecrypted(false);

    // The encrypted field contains the encrypted text id. Copy that value to the encrypted ref field and set encrypted
    // field to null.
    copyToEncryptedRefFields(object);

    if (!skipValidation(settingAttribute)) {
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails(object, settingAttribute.getAppId(), null);
      managerDecryptionService.decrypt(object, encryptionDetails);
    }
    if (wasDecrypted) {
      // Set decrypted to true even if the call to decrypt did not and the setting value initially had decrypted set to
      // true.
      object.setDecrypted(true);
    }
  }

  private boolean skipValidation(SettingAttribute settingAttribute) {
    if (Objects.nonNull(settingAttribute) && Objects.nonNull(settingAttribute.getValue())) {
      return (settingAttribute.getValue() instanceof KubernetesClusterConfig
                 && ((KubernetesClusterConfig) settingAttribute.getValue()).isSkipValidation())
          || (settingAttribute.getValue() instanceof GcpConfig
              && ((GcpConfig) settingAttribute.getValue()).isSkipValidation())
          || (settingAttribute.getValue() instanceof PcfConfig
              && ((PcfConfig) settingAttribute.getValue()).isSkipValidation())
          || (settingAttribute.getValue() instanceof ArtifactoryConfig
              && ((ArtifactoryConfig) settingAttribute.getValue()).isSkipValidation())
          || (settingAttribute.getValue() instanceof DockerConfig
              && ((DockerConfig) settingAttribute.getValue()).isSkipValidation());
    }

    return false;
  }

  /**
   * resetEncryptedFields sets encrypted fields to null. This method is required before saving/updating setting
   * attributes because wingsPersistence might try to encrypt the values stored in encrypted fields into the the ref
   * fields for legacy reasons.
   *
   * It does the following type of conversion:
   * { "password": "val1", "encryptedPassword": "..." }
   * ->
   * { "password": null, "encryptedPassword": "..." }
   *
   * @param object the encryptable setting to mutate
   */
  public void resetEncryptedFields(EncryptableSetting object) {
    List<Field> encryptedFields = object.getEncryptedFields();
    try {
      for (Field f : encryptedFields) {
        f.setAccessible(true);
        f.set(object, null);
      }
    } catch (Exception e) {
      throw new InvalidRequestException(REFERENCED_SECRET_ERROR_MSG, e);
    }
  }

  /**
   * copyToEncryptedRefFields copies the value of encrypted fields to encrypted ref fields. This method is needed
   * because UI passes the ID in the encrypted field and not the ref field.
   *
   * It does the following type of conversion:
   * { "password": "val1", "encryptedPassword": "..." }
   * ->
   * { "password": null, "encryptedPassword": "val1" }
   *
   * @param object the encryptable setting to mutate
   */
  public void copyToEncryptedRefFields(EncryptableSetting object) {
    List<Field> encryptedFields = object.getEncryptedFields();
    try {
      for (Field f : encryptedFields) {
        f.setAccessible(true);
        char[] fieldValue = (char[]) f.get(object);
        if (fieldValue == null) {
          // Ignore if encrypted field value is null. This is required for yaml.
          continue;
        }
        Field encryptedRefField = getEncryptedRefField(f, object);
        encryptedRefField.setAccessible(true);
        encryptedRefField.set(object, String.valueOf(fieldValue));
        f.set(object, null);
      }
    } catch (Exception e) {
      throw new InvalidRequestException(REFERENCED_SECRET_ERROR_MSG, e);
    }
  }

  /**
   * copyFromEncryptedRefFields copies the value of encrypted ref fields to encrypted fields. This method is needed
   * because UI passes the ID in the encrypted field and not the ref field.
   *
   * It does the following type of conversion:
   * { "password": "...", "encryptedPassword": "val1" }
   * ->
   * { "password": "val1", "encryptedPassword": null }
   *
   * @param object the encryptable setting to mutate
   */
  public void copyFromEncryptedRefFields(EncryptableSetting object) {
    List<Field> encryptedFields = object.getEncryptedFields();
    try {
      for (Field f : encryptedFields) {
        f.setAccessible(true);
        Field encryptedRefField = getEncryptedRefField(f, object);
        encryptedRefField.setAccessible(true);
        String encryptedFieldValue = (String) encryptedRefField.get(object);
        f.set(object, encryptedFieldValue == null ? null : encryptedFieldValue.toCharArray());
        encryptedRefField.set(object, null);
      }
    } catch (Exception e) {
      throw new InvalidRequestException(REFERENCED_SECRET_ERROR_MSG, e);
    }
  }

  boolean isConnectorCategory(SettingAttribute.SettingCategory settingCategory) {
    return settingCategory == CONNECTOR || settingCategory == HELM_REPO || settingCategory == AZURE_ARTIFACTS;
  }

  boolean isArtifactServer(SettingVariableTypes settingVariableTypes) {
    switch (settingVariableTypes) {
      case JENKINS:
      case BAMBOO:
      case DOCKER:
      case NEXUS:
      case ARTIFACTORY:
      case SMB:
      case SFTP:
      case AMAZON_S3_HELM_REPO:
      case GCS_HELM_REPO:
      case HTTP_HELM_REPO:
      case OCI_HELM_REPO:
      case AZURE_ARTIFACTS_PAT:
        return true;
      default:
        return false;
    }
  }

  public void updateUsageRestrictions(SettingAttribute settingAttribute) {
    if (isNotEmpty(getUsedSecretIds(settingAttribute))) {
      settingAttribute.setUsageRestrictions(null);
    }
  }

  public UsageRestrictions getUsageRestrictions(SettingAttribute settingAttribute) {
    if (isNotEmpty(getUsedSecretIds(settingAttribute))) {
      return null;
    }

    return settingAttribute.getUsageRestrictions();
  }

  public void validateUsageRestrictionsOnEntitySave(SettingAttribute settingAttribute, String accountId,
      UsageRestrictions newUsageRestrictions, boolean isAccountAdmin) {
    Set<String> usedSecretIds = getUsedSecretIds(settingAttribute);
    if (isNotEmpty(usedSecretIds)) {
      if (!secretManager.hasUpdateAccessToSecrets(usedSecretIds, accountId)) {
        throw new UnauthorizedUsageRestrictionsException(WingsException.USER);
      }
      return;
    }
    if (skipUsageRestrictionsValidation(isAccountAdmin, settingAttribute)) {
      return;
    }
    PermissionType permissionType = getPermissionType(settingAttribute);
    usageRestrictionsService.validateUsageRestrictionsOnEntitySave(
        accountId, permissionType, newUsageRestrictions, false);
  }

  @VisibleForTesting
  protected boolean skipUsageRestrictionsValidation(boolean isAccountAdmin, SettingAttribute settingAttribute) {
    if (!isAccountAdmin || settingAttribute == null || settingAttribute.getValue() == null
        || settingAttribute.getValue().getType() == null) {
      return false;
    }
    return SETTING.equals(SettingAttribute.SettingCategory.getCategory(
               SettingVariableTypes.valueOf(settingAttribute.getValue().getType())))
        && SettingVariableTypes.STRING.equals(SettingVariableTypes.valueOf(settingAttribute.getValue().getType()))
        && GLOBAL_APP_ID.equals(settingAttribute.getAppId());
  }

  public void validateUsageRestrictionsOnEntityUpdate(SettingAttribute settingAttribute, String accountId,
      UsageRestrictions oldUsageRestrictions, UsageRestrictions newUsageRestrictions, boolean isAccountAdmin) {
    Set<String> usedSecretIds = getUsedSecretIds(settingAttribute);
    if (isNotEmpty(usedSecretIds)) {
      if (!secretManager.hasUpdateAccessToSecrets(usedSecretIds, accountId)) {
        throw new UnauthorizedUsageRestrictionsException(WingsException.USER);
      }
      return;
    }
    if (skipUsageRestrictionsValidation(isAccountAdmin, settingAttribute)) {
      return;
    }
    PermissionType permissionType = getPermissionType(settingAttribute);
    usageRestrictionsService.validateUsageRestrictionsOnEntityUpdate(
        accountId, permissionType, oldUsageRestrictions, newUsageRestrictions, false);
  }

  public boolean userHasPermissionsToChangeEntity(SettingAttribute settingAttribute, String accountId,
      UsageRestrictions entityUsageRestrictions, boolean isAccountAdmin) {
    Set<String> usedSecretIds = getUsedSecretIds(settingAttribute);
    if (isNotEmpty(usedSecretIds)) {
      return secretManager.hasUpdateAccessToSecrets(usedSecretIds, accountId);
    }

    if (skipUsageRestrictionsValidation(isAccountAdmin, settingAttribute)) {
      return true;
    }
    PermissionType permissionType = getPermissionType(settingAttribute);
    return usageRestrictionsService.userHasPermissionsToChangeEntity(
        accountId, permissionType, entityUsageRestrictions, false);
  }

  public Set<String> getUsedSecretIds(SettingAttribute settingAttribute) {
    if (hasReferencedSecrets(settingAttribute)) {
      List<String> secretIds = emptyIfNull(settingAttribute.fetchRelevantSecretIds());
      return secretIds.stream()
          .filter(secretId -> isNotEmpty(secretId) && !CGConstants.ENCRYPTED_VALUE_STR.equals(secretId))
          .collect(Collectors.toSet());
    }

    return null;
  }

  public PermissionType getPermissionType(SettingAttribute settingAttribute) {
    if (settingAttribute == null || settingAttribute.getValue() == null
        || settingAttribute.getValue().getType() == null) {
      return ACCOUNT_MANAGEMENT;
    }
    switch (SettingAttribute.SettingCategory.getCategory(
        SettingVariableTypes.valueOf(settingAttribute.getValue().getType()))) {
      case AZURE_ARTIFACTS:
      case HELM_REPO:
      case CONNECTOR: {
        return MANAGE_CONNECTORS;
      }
      case CLOUD_PROVIDER: {
        return MANAGE_CLOUD_PROVIDERS;
      }
      case SETTING: {
        if (!SettingVariableTypes.STRING.equals(SettingVariableTypes.valueOf(settingAttribute.getValue().getType()))) {
          return MANAGE_SSH_AND_WINRM;
        } else {
          if (GLOBAL_APP_ID.equals(settingAttribute.getAppId())) {
            return MANAGE_ACCOUNT_DEFAULTS;
          } else {
            return MANAGE_APPLICATIONS;
          }
        }
      }
      default: {
        return ACCOUNT_MANAGEMENT;
      }
    }
  }

  public void setCertValidationRequired(String accountId, SettingAttribute settingAttribute) {
    if (settingAttribute != null && settingAttribute.getValue() != null) {
      settingAttribute.getValue().setCertValidationRequired(accountService.isCertValidationRequired(accountId));
    }
  }

  public void setCertValidationRequired(String accountId, SettingValue settingValue) {
    if (settingValue != null) {
      settingValue.setCertValidationRequired(accountService.isCertValidationRequired(accountId));
    }
  }

  public void resetTransientFields(SettingValue settingValue) {
    if (null != settingValue && SettingVariableTypes.GIT.name().equals(settingValue.getType())) {
      GitConfig gitConfig = (GitConfig) settingValue;
      gitConfig.setSshSettingAttribute(null);
    }
  }

  public void setFeatureFlagIfRequired(SettingValue value, String accountId) {
    if (null != value && isNotEmpty(accountId) && value instanceof HostConnectionAttributes) {
      HostConnectionAttributes connectionAttributes = (HostConnectionAttributes) value;
      connectionAttributes.setUseSshClient(featureFlagService.isEnabled(FeatureName.CDS_SSH_CLIENT, accountId));
      connectionAttributes.setUseSshj(featureFlagService.isEnabled(FeatureName.CDS_SSH_SSHJ, accountId));
    }
  }
}
