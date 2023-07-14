/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.dto;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.SocketConnectivityCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.service.intfc.security.EncryptionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotBlank;

@Data
@SuperBuilder
public class LdapSettings extends SSOSettings implements ExecutionCapabilityDemander {
  @NotBlank String accountId;
  @NotNull @Valid LdapConnectionSettings connectionSettings;
  @Valid List<LdapUserSettings> userSettingsList;

  @Valid List<LdapGroupSettings> groupSettingsList;

  @Valid @Deprecated LdapUserSettings userSettings;

  @Valid @Deprecated LdapGroupSettings groupSettings;
  public static final String TASK_SELECTORS = "Task Selectors";

  boolean disabled;

  public void decryptFields(
      @NotNull EncryptedDataDetail encryptedDataDetail, @NotNull EncryptionService encryptionService) {
    if (connectionSettings.getBindPassword().equals(LdapConstants.MASKED_STRING)) {
      String bindPassword = new String(encryptionService.getDecryptedValue(encryptedDataDetail, false));
      connectionSettings.setBindPassword(bindPassword);
    }
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
}
