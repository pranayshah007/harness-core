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
public class NGLdapDelegateTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  LdapSettings ldapSettings;
  EncryptedDataDetail encryptedDataDetail;
  String name;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return ldapSettings.fetchRequiredExecutionCapabilities(maskingEvaluator);
  }
}