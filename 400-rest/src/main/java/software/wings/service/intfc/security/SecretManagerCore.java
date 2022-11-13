package software.wings.service.intfc.security;

import software.wings.beans.SecretChangeLog;
import software.wings.beans.SecretManagerRuntimeParameters;
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

}
