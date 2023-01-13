package io.harness.delegate.googlefunctions;

import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.googlefunction.GoogleFunctionCommandTaskHelper;
import io.harness.delegate.task.googlefunction.GoogleFunctionUtils;
import io.harness.delegate.task.googlefunctions.GoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionDeployRequest;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionDeployResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionDeployCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
    private GoogleFunctionInfraConfig googleFunctionInfraConfig;
    private long timeoutInMillis;
    @Inject private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;
    @Override
    protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
                                                                ILogStreamingTaskClient iLogStreamingTaskClient,
                                                                CommandUnitsProgress commandUnitsProgress) throws Exception {
        if (!(googleFunctionCommandRequest instanceof GoogleFunctionDeployRequest)) {
            throw new InvalidArgumentsException(Pair.of("googleFunctionCommandRequest", "Must be instance of " +
                    "GoogleFunctionCommandRequest"));
        }
        GoogleFunctionDeployRequest googleFunctionDeployRequest = (GoogleFunctionDeployRequest)
                googleFunctionCommandRequest;

        try{
            Function function = googleFunctionCommandTaskHelper.deployFunction(googleFunctionDeployRequest);
            return GoogleFunctionDeployResponse.builder()
                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                    .build();
        }
        catch(Exception e) {
            log.error(e.getMessage());
            throw e;
        }

    }
}
