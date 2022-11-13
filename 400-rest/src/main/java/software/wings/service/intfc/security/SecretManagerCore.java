package software.wings.service.intfc.security;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import io.harness.beans.SecretChangeLog;
import software.wings.beans.SecretManagerRuntimeParameters;
import io.harness.beans.SecretUsageLog;
import software.wings.settings.SettingVariableTypes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SecretManagerCore {
  SecretManagerRuntimeParameters configureSecretManagerRuntimeCredentialsForExecution(
      String accountId, String kmsId, String executionId, Map<String, String> runtimeParameters);

  Optional<SecretManagerRuntimeParameters> getSecretManagerRuntimeCredentialsForExecution(
      String executionId, String secretManagerId);

  List<SecretChangeLog> getChangeLogs(String accountId, String entityId, SettingVariableTypes variableType)
      throws IllegalAccessException;

  PageResponse<SecretUsageLog> getUsageLogs(PageRequest<SecretUsageLog> pageRequest, String accountId, String entityId,
      SettingVariableTypes variableType) throws IllegalAccessException;
}
