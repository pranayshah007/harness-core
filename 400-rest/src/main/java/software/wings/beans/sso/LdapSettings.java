/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.sso;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.service.impl.DelegateTaskServiceClassicImpl.TASK_SELECTORS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretText;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.SocketConnectivityCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.iterator.PersistentCronIterable;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.service.intfc.security.SecretManager;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotBlank;

/**
 * Bean to store all the ldap sso provider configuration details
 *
 * @author Swapnil
 */
@OwnedBy(PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
@Data
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "LdapSettingsKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("LDAP")
public class LdapSettings extends SSOSettings implements ExecutionCapabilityDemander, PersistentCronIterable {
  @NotBlank String accountId;
  @NotNull @Valid LdapConnectionSettings connectionSettings;

  /**
   * Keeping the below two attributes only for migration purpose.
   * Will be removed in subsequent release.
   */
  @Valid @Deprecated LdapUserSettings userSettings;

  @Valid @Deprecated LdapGroupSettings groupSettings;

  @Valid List<LdapUserSettings> userSettingsList;

  @Valid List<LdapGroupSettings> groupSettingsList;

  private String cronExpression;

  boolean disabled;

  @JsonIgnore @Transient private String defaultCronExpression;

  public String getCronExpression() {
    return isEmpty(cronExpression) ? defaultCronExpression : cronExpression;
  }

  public void setCronExpression(String cronExpression) {
    this.cronExpression = isEmpty(cronExpression) ? defaultCronExpression : cronExpression;
  }

  /**
   * Keeping this attribute only for migration purpose.
   * Will be removed in subsequent release.
   * Also, saving this info at present as UI still needs
   * some work to start using collections of groupSettings
   */

  @JsonCreator
  @Builder
  public LdapSettings(@JsonProperty("displayName") String displayName, @JsonProperty("accountId") String accountId,
      @JsonProperty("connectionSettings") LdapConnectionSettings connectionSettings,
      @JsonProperty("userSettingsList") List<LdapUserSettings> userSettingsList,
      @JsonProperty("groupSettingsList") List<LdapGroupSettings> groupSettingsList) {
    super(SSOType.LDAP, displayName, connectionSettings.generateUrl());
    this.accountId = accountId;
    this.connectionSettings = connectionSettings;
    this.userSettingsList = userSettingsList;
    this.groupSettingsList = groupSettingsList;
  }

  public EncryptedDataDetail getEncryptedDataDetails(SecretManager secretManager) {
    return secretManager
        .encryptedDataDetails(accountId, LdapConstants.BIND_PASSWORD_KEY, connectionSettings.fetchPassword(), null)
        .get();
  }

  public void encryptLdapInlineSecret(SecretManager secretManager, boolean localSM) {
    if (isNotEmpty(connectionSettings.getBindPassword())
        && !LdapConstants.MASKED_STRING.equals(connectionSettings.getBindPassword())) {
      connectionSettings.setPasswordType(LdapConnectionSettings.INLINE_SECRET);
      String oldEncryptedBindPassword = connectionSettings.getEncryptedBindPassword();
      if (isNotEmpty(oldEncryptedBindPassword)) {
        secretManager.deleteSecret(accountId, oldEncryptedBindPassword, new HashMap<>(), false);
      }
      SecretText secretText = SecretText.builder()
                                  .value(connectionSettings.getBindPassword())
                                  .hideFromListing(true)
                                  .name(UUID.randomUUID().toString())
                                  .scopedToAccount(true)
                                  .build();
      if (localSM) {
        secretText.setKmsId(accountId);
      }
      String encryptedBindPassword = secretManager.saveSecretText(accountId, secretText, false);
      connectionSettings.setEncryptedBindPassword(encryptedBindPassword);
      connectionSettings.setBindPassword(LdapConstants.MASKED_STRING);
      connectionSettings.setEncryptedBindSecret(null);
    } else {
      connectionSettings.setBindPassword(LdapConstants.MASKED_STRING);
    }
  }

  @Override
  public SSOSettings getPublicSSOSettings() {
    return this;
  }

  @Override
  public SSOType getType() {
    return SSOType.LDAP;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (connectionSettings == null) {
      return Collections.EMPTY_LIST;
    }
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    executionCapabilities.add(SocketConnectivityCapabilityGenerator.buildSocketConnectivityCapability(
        connectionSettings.getHost(), Integer.toString(connectionSettings.getPort())));
    if (isNotEmpty(connectionSettings.getDelegateSelectors())) {
      executionCapabilities.add(SelectorCapability.builder()
                                    .selectors(connectionSettings.getDelegateSelectors())
                                    .selectorOrigin(TASK_SELECTORS)
                                    .build());
    }
    return executionCapabilities;
  }

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    nextIterations = isEmpty(nextIterations) ? new ArrayList<>() : nextIterations;

    if (expandNextIterations(skipMissed, throttled, getCronExpression(), nextIterations)) {
      return isNotEmpty(nextIterations) ? nextIterations : Collections.singletonList(Long.MAX_VALUE);
    }

    return Collections.singletonList(Long.MAX_VALUE);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return EmptyPredicate.isEmpty(nextIterations) ? null : nextIterations.get(0);
  }
}