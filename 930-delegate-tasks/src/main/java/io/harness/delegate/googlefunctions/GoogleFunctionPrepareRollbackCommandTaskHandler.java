package io.harness.delegate.googlefunctions;

import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.run.v2.Revision;
import com.google.cloud.run.v2.Service;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.GoogleFunctionException;
import io.harness.delegate.task.googlefunction.GoogleFunctionCommandTaskHelper;
import io.harness.delegate.task.googlefunctions.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionDeployRequest;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionPrepareRollbackRequest;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionPrepareRollbackResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;

import static java.lang.String.format;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionPrepareRollbackCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
    @Inject private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

    @Override
    protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
                                                                ILogStreamingTaskClient iLogStreamingTaskClient,
                                                                CommandUnitsProgress commandUnitsProgress) throws Exception {

        if (!(googleFunctionCommandRequest instanceof GoogleFunctionPrepareRollbackRequest)) {
            throw new InvalidArgumentsException(Pair.of("googleFunctionCommandRequest", "Must be instance of " +
                    "GoogleFunctionPrepareRollbackRequest"));
        }
        GoogleFunctionPrepareRollbackRequest googleFunctionPrepareRollbackRequest =
                (GoogleFunctionPrepareRollbackRequest) googleFunctionCommandRequest;
        GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
                (GcpGoogleFunctionInfraConfig) googleFunctionPrepareRollbackRequest.getGoogleFunctionInfraConfig();
        try {

            CreateFunctionRequest.Builder createFunctionRequestBuilder = CreateFunctionRequest.newBuilder();
            googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
                    googleFunctionPrepareRollbackRequest.getGoogleFunctionDeployManifestContent(),
                    createFunctionRequestBuilder, "createFunctionRequest");

            // get function name
            String functionName = googleFunctionCommandTaskHelper.getFunctionName(googleFunctionInfraConfig.getProject(),
                    googleFunctionInfraConfig.getRegion(), createFunctionRequestBuilder.getFunction().getName());

            Optional<Function> existingFunctionOptional = googleFunctionCommandTaskHelper.getFunction(functionName,
                    googleFunctionInfraConfig.getGcpConnectorDTO(), googleFunctionInfraConfig.getProject(),
                    googleFunctionInfraConfig.getRegion());
            if(existingFunctionOptional.isPresent()) {
                // if function exist

                Function existingFunction = existingFunctionOptional.get();
                Optional<String> cloudRunServiceNameOptional = googleFunctionCommandTaskHelper.getCloudRunServiceName(existingFunction);
                if(cloudRunServiceNameOptional.isEmpty()){
                    throw NestedExceptionUtils.hintWithExplanationException(
                            "Please make sure Google Function should be 2nd Gen.",
                            "Cloud Run Service doesn't exist with Cloud Function. Harness supports 2nd Gen Google Functions which " +
                                    "are integrated with cloud run",
                            new InvalidRequestException("Cloud Run Service doesn't exist with Cloud Function."));
                }
                Service existingService = googleFunctionCommandTaskHelper.getCloudRunService(cloudRunServiceNameOptional.get(),
                        googleFunctionInfraConfig.getGcpConnectorDTO(), googleFunctionInfraConfig.getProject(),
                        googleFunctionInfraConfig.getRegion());

                if(!googleFunctionCommandTaskHelper.validateTrafficInExistingRevisions(existingService.getTrafficStatusesList())) {
                    //todo: show traffic percents in log
                    throw NestedExceptionUtils.hintWithExplanationException(
                            format("Please make sure that one revision cloud run service is serving full traffic. Please check execution logs" +
                                    "to see present traffic split among revisions."),
                            format("Only one revision of cloud run service is expected to serve full traffic before new deployment."),
                            new InvalidRequestException("More than one Revision of cloud run service is serving traffic."));
                }
                return GoogleFunctionPrepareRollbackResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .isFirstDeployment(false)
                        .cloudFunctionAsString(JsonFormat.printer().print(existingFunction))
                        .cloudRunServiceAsString(JsonFormat.printer().print(existingService))
                        .build();
            }
            else {
                // if function doesn't exist
                return GoogleFunctionPrepareRollbackResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .isFirstDeployment(true)
                        .build();
            }
        }
        catch (Exception exception) {
            throw new GoogleFunctionException(exception);
        }


    }
}
