package io.harness.delegate.googlefunctions;

import com.google.cloud.functions.v2.Function;
import com.google.cloud.run.v2.Service;
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
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionRollbackRequest;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionRollbackResponse;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionTrafficShiftResponse;
import io.harness.logging.CommandExecutionStatus;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionRollbackCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
    @Inject
    private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

    @Override
    protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
                                                                ILogStreamingTaskClient iLogStreamingTaskClient,
                                                                CommandUnitsProgress commandUnitsProgress) throws Exception {
        GoogleFunctionRollbackRequest googleFunctionRollbackRequest =
                (GoogleFunctionRollbackRequest) googleFunctionCommandRequest;
        GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
                (GcpGoogleFunctionInfraConfig) googleFunctionRollbackRequest.getGoogleFunctionInfraConfig();
        try{
            Function.Builder functionBuilder = Function.newBuilder();
            googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
                    googleFunctionRollbackRequest.getGoogleFunctionAsString(),
                    functionBuilder, "cloudFunction");
            if(googleFunctionRollbackRequest.isFirstDeployment()) {
                googleFunctionCommandTaskHelper.deleteFunction(functionBuilder.getName(),
                        googleFunctionInfraConfig.getGcpConnectorDTO(),
                        googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());
                return GoogleFunctionRollbackResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .build();
            }
            else {
                Service.Builder serviceBuilder = Service.newBuilder();
                googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
                        googleFunctionRollbackRequest.getGoogleCloudRunServiceAsString(),
                        serviceBuilder, "cloudRunService");
                String targetRevision = googleFunctionCommandTaskHelper.getCurrentRevision(serviceBuilder.build());
                googleFunctionCommandTaskHelper.updateFullTrafficToSingleRevision(serviceBuilder.getName(), targetRevision,
                        googleFunctionInfraConfig.getGcpConnectorDTO(),
                        googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());
                Function function = googleFunctionCommandTaskHelper.getFunction(functionBuilder.getName(),
                        googleFunctionInfraConfig.getGcpConnectorDTO(),
                        googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()).get();
                GoogleFunction googleFunction = googleFunctionCommandTaskHelper.getGoogleFunction(function,
                        googleFunctionInfraConfig);
                return GoogleFunctionRollbackResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .function(googleFunction)
                        .build();
            }

        }
        catch (Exception exception) {
            throw new GoogleFunctionException(exception);
        }
    }
}
