package io.harness.delegate.googlefunctions;

import com.google.cloud.functions.v2.CreateFunctionRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.googlefunction.GoogleFunctionUtils;
import io.harness.delegate.task.googlefunctions.GoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionDeployRequest;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionCommandResponse;
import io.harness.exception.InvalidArgumentsException;
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
    @Override
    protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
                                                                ILogStreamingTaskClient iLogStreamingTaskClient,
                                                                CommandUnitsProgress commandUnitsProgress) throws Exception {
        if (!(googleFunctionCommandRequest instanceof GoogleFunctionCommandRequest)) {
            throw new InvalidArgumentsException(Pair.of("googleFunctionCommandRequest", "Must be instance of " +
                    "GoogleFunctionCommandRequest"));
        }
        GoogleFunctionDeployRequest googleFunctionDeployRequest = (GoogleFunctionDeployRequest)
                googleFunctionCommandRequest;

        timeoutInMillis = googleFunctionDeployRequest.getTimeoutIntervalInMin() * 60000;
        googleFunctionInfraConfig = googleFunctionDeployRequest.getGoogleFunctionInfraConfig();

        try{
            CreateFunctionRequest createFunctionRequest = CreateFunctionRequest.parseFrom(
                    new ByteArrayInputStream(googleFunctionDeployRequest.getGoogleFunctionDeployManifestContent()
                            .getBytes(StandardCharsets.UTF_8)));
            createFunctionRequest.


        }
        catch(Exception e) {

        }

        return null;
    }
}
