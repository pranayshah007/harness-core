package io.harness.delegate.googlefunctions;

import com.google.cloud.functions.v2.Function;
import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.GoogleFunctionException;
import io.harness.delegate.task.googlefunction.GoogleFunctionCommandTaskHelper;
import io.harness.delegate.task.googlefunctions.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctions.GoogleFunction;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionDeployWithoutTrafficRequest;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionPrepareRollbackRequest;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionDeployResponse;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionDeployWithoutTrafficResponse;
import io.harness.logging.CommandExecutionStatus;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionDeployWithoutTrafficCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
    @Inject private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

    @Override
    protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest
                                                                            googleFunctionCommandRequest,
                                                                ILogStreamingTaskClient iLogStreamingTaskClient,
                                                                CommandUnitsProgress commandUnitsProgress) throws Exception {
        GoogleFunctionDeployWithoutTrafficRequest googleFunctionDeployWithoutTrafficRequest =
                (GoogleFunctionDeployWithoutTrafficRequest) googleFunctionCommandRequest;
        GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
                (GcpGoogleFunctionInfraConfig) googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionInfraConfig();
        try{
            Function function = googleFunctionCommandTaskHelper.deployFunction(googleFunctionInfraConfig,
                    googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionDeployManifestContent(),
                    googleFunctionDeployWithoutTrafficRequest.getUpdateFieldMaskContent(),
                    googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionArtifactConfig(), false);

            GoogleFunction googleFunction = googleFunctionCommandTaskHelper.getGoogleFunction(function,
                    googleFunctionInfraConfig);

            return GoogleFunctionDeployWithoutTrafficResponse.builder()
                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                    .function(googleFunction)
                    .build();
        }
        catch (Exception exception) {
            throw new GoogleFunctionException(exception);
        }
    }
}
