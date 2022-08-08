/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ldap;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.dto.LdapSettings;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@OwnedBy(PL)
@Value
@Getter
@Builder
public class NGLdapGroupSearchTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  LdapSettings ldapSettings;
  EncryptedDataDetail encryptedDataDetail;
  String name;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return ldapSettings.fetchRequiredExecutionCapabilities(maskingEvaluator);
  }
}