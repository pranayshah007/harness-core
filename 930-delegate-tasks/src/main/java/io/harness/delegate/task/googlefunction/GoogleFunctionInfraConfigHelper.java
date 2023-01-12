package io.harness.delegate.task.googlefunction;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.googlefunctions.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctions.GoogleFunctionInfraConfig;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionInfraConfigHelper {
    @Inject private SecretDecryptionService secretDecryptionService;

    public void decryptInfraConfig(GoogleFunctionInfraConfig googleFunctionInfraConfig) {
        GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig =
                (GcpGoogleFunctionInfraConfig) googleFunctionInfraConfig;
        decryptInfraConfig(gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
                gcpGoogleFunctionInfraConfig.getEncryptionDataDetails());
    }

    private void decryptInfraConfig(GcpConnectorDTO gcpConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
        if (gcpConnectorDTO.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
            GcpManualDetailsDTO gcpManualDetailsDTO =
                    (GcpManualDetailsDTO) gcpConnectorDTO.getCredential().getConfig();
            secretDecryptionService.decrypt(gcpManualDetailsDTO, encryptedDataDetails);
            ExceptionMessageSanitizer.storeAllSecretsForSanitizing(gcpManualDetailsDTO, encryptedDataDetails);
        }
    }
}

