package io.harness.delegate.beans.connector.helpers;

import io.harness.azure.AzureEnvironmentType;
import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConstants.AZURE_DEFAULT_ENCRYPTION_URL;
import static io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConstants.AZURE_US_GOVERNMENT_ENCRYPTION_URL;

public class AzureCapabilityBaseHelper extends ConnectorCapabilityBaseHelper {
    public static List<ExecutionCapability> getExecutionCapabilities(String vaultName, AzureEnvironmentType azureEnvironmentType,
                                                                     Set<String> delegateSelectors, ExpressionEvaluator maskingEvaluator) {
        List<ExecutionCapability> executionCapabilities = new ArrayList<>();
        String encryptionServiceUrl;
        if (azureEnvironmentType == null) {
            encryptionServiceUrl = String.format(AZURE_DEFAULT_ENCRYPTION_URL, vaultName);
        } else {
            switch (azureEnvironmentType) {
                case AZURE_US_GOVERNMENT:
                    encryptionServiceUrl =
                            String.format(AZURE_US_GOVERNMENT_ENCRYPTION_URL, vaultName);
                    break;
                case AZURE:
                default:
                    encryptionServiceUrl = String.format(AZURE_DEFAULT_ENCRYPTION_URL, vaultName);
            }
        }

        executionCapabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
                encryptionServiceUrl, maskingEvaluator));
        populateDelegateSelectorCapability(executionCapabilities, delegateSelectors);
        return executionCapabilities;
    }
}
