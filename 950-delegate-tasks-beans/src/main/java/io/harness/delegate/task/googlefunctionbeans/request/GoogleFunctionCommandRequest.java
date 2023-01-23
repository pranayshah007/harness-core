package io.harness.delegate.task.googlefunctionbeans.request;

import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionCommandTypeNG;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionInfraConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

public interface GoogleFunctionCommandRequest extends TaskParameters, ExecutionCapabilityDemander {
    @NotEmpty GoogleFunctionCommandTypeNG getGoogleFunctionCommandType();
    String getCommandName();
    CommandUnitsProgress getCommandUnitsProgress();
    GoogleFunctionInfraConfig getGoogleFunctionInfraConfig();
    Integer getTimeoutIntervalInMin();

    @Override
    default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
        GoogleFunctionInfraConfig googleFunctionInfraConfig = getGoogleFunctionInfraConfig();
        List<EncryptedDataDetail> infraConfigEncryptionDataDetails = googleFunctionInfraConfig.getEncryptionDataDetails();
        List<ExecutionCapability> capabilities =
                new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                        infraConfigEncryptionDataDetails, maskingEvaluator));
        if(googleFunctionInfraConfig instanceof GcpGoogleFunctionInfraConfig) {
            GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig =
                    (GcpGoogleFunctionInfraConfig) googleFunctionInfraConfig;
            capabilities.addAll(GcpCapabilityHelper.fetchRequiredExecutionCapabilities(
                    gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(), maskingEvaluator));
        }
        return capabilities;
    }
}
