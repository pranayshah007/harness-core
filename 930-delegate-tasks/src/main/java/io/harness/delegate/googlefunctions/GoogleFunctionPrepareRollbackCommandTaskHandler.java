package io.harness.delegate.googlefunctions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionDeployRequest;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionPrepareRollbackRequest;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionCommandResponse;
import io.harness.exception.InvalidArgumentsException;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionPrepareRollbackCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
    @Override
    protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
                                                                ILogStreamingTaskClient iLogStreamingTaskClient,
                                                                CommandUnitsProgress commandUnitsProgress) throws Exception {
        if (!(googleFunctionCommandRequest instanceof GoogleFunctionPrepareRollbackRequest)) {
            throw new InvalidArgumentsException(Pair.of("googleFunctionCommandRequest", "Must be instance of " +
                    "GoogleFunctionPrepareRollbackRequest"));
        }
        GoogleFunctionPrepareRollbackRequest googleFunctionPrepareRollbackRequest = (GoogleFunctionPrepareRollbackRequest)
                googleFunctionCommandRequest;

        try{

        }
        catch (Exception e) {

        }
        return null;
    }
}
