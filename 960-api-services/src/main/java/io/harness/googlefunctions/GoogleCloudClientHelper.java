package io.harness.googlefunctions;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.functions.v2.FunctionServiceClient;
import com.google.cloud.functions.v2.FunctionServiceSettings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import static io.harness.exception.WingsException.USER;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class GoogleCloudClientHelper {
    @Inject private GcpCredentialsHelper gcpCredentialsHelper;

    public FunctionServiceClient getFunctionsClient(GcpInternalConfig gcpInternalConfig) throws IOException {
        CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(
                gcpCredentialsHelper.getGoogleCredentials(gcpInternalConfig.getServiceAccountKeyFileContent(),
                        gcpInternalConfig.isUseDelegate));
        FunctionServiceSettings functionServiceSettings = FunctionServiceSettings.newBuilder()
                .setCredentialsProvider(credentialsProvider)
                .build();
        return FunctionServiceClient.create(functionServiceSettings);
    }

    public void logCall(String client, String method) {
        log.info("Google Cloud Call: client: {}, method: {}", client, method);
    }

    public void logError(String client, String method, String errorMessage) {
        log.error("Google Cloud Call: client: {}, method: {}, error: {}", client, method, errorMessage);
    }

    public void handleException(Exception exception) {
        //todo: add more cases
        throw new InvalidRequestException(exception.getMessage(), exception, USER);
    }

}
