package io.harness.delegate.beans.ldap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import software.wings.beans.dto.LdapSettings;

import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.PL;

@OwnedBy(PL)
@Getter
@Value
@Builder
public class LdapGroupSearchTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  LdapSettings ldapSettings;
  EncryptedDataDetail encryptedDataDetail;
  String name;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return ldapSettings.fetchRequiredExecutionCapabilities(maskingEvaluator);
  }
}