package io.harness.delegate.googlefunctions;

import com.google.cloud.functions.v2.CreateFunctionRequest;
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
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionTrafficShiftRequest;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionDeployResponse;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionTrafficShiftResponse;
import io.harness.logging.CommandExecutionStatus;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionTrafficShiftCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
    @Inject
    private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

    @Override
    protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
                                                                ILogStreamingTaskClient iLogStreamingTaskClient,
                                                                CommandUnitsProgress commandUnitsProgress) throws Exception {
        GoogleFunctionTrafficShiftRequest googleFunctionTrafficShiftRequest =
                (GoogleFunctionTrafficShiftRequest) googleFunctionCommandRequest;
        GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
                (GcpGoogleFunctionInfraConfig) googleFunctionTrafficShiftRequest.getGoogleFunctionInfraConfig();
        try {
            if (!googleFunctionTrafficShiftRequest.isFirstDeployment()) {
                Function.Builder functionBuilder = Function.newBuilder();
                googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
                        googleFunctionTrafficShiftRequest.getGoogleFunctionAsString(),
                        functionBuilder, "cloudFunction");
                Service.Builder serviceBuilder = Service.newBuilder();
                googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
                        googleFunctionTrafficShiftRequest.getGoogleCloudRunServiceAsString(),
                        serviceBuilder, "cloudRunService");
                String existingRevision = googleFunctionCommandTaskHelper.getCurrentRevision(serviceBuilder.build());

                googleFunctionCommandTaskHelper.updateTraffic(serviceBuilder.getName(), googleFunctionTrafficShiftRequest.getTargetTrafficPercent(),
                        googleFunctionTrafficShiftRequest.getTargetRevision(), existingRevision,
                        googleFunctionInfraConfig.getGcpConnectorDTO(),
                        googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());
                Function function = googleFunctionCommandTaskHelper.getFunction(functionBuilder.getName(),
                        googleFunctionInfraConfig.getGcpConnectorDTO(),
                        googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()).get();
                GoogleFunction googleFunction = googleFunctionCommandTaskHelper.getGoogleFunction(function,
                        googleFunctionInfraConfig);

                return GoogleFunctionTrafficShiftResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .function(googleFunction)
                        .build();
            }
            return GoogleFunctionTrafficShiftResponse.builder()
                    .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                    .errorMessage("traffic shift not allowed with first deployment")
                    .build();
        }
         catch (Exception exception) {
            throw new GoogleFunctionException(exception);
        }
    }
}
