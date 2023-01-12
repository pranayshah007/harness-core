package io.harness.delegate.task.googlefunction;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.ecs.EcsDelegateTaskHelper;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionCommandResponse;
import io.harness.secret.SecretSanitizerThreadLocal;
import org.apache.commons.lang3.NotImplementedException;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionCommandTask extends AbstractDelegateRunnableTask {
    @Inject
    private GoogleFunctionDelegateTaskHelper googleFunctionDelegateTaskHelper;

    public GoogleFunctionCommandTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
                            Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
        super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

        SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
    }

    @Override
    public GoogleFunctionCommandResponse run(Object[] parameters) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public GoogleFunctionCommandResponse run(TaskParameters parameters) {
        GoogleFunctionCommandRequest googleFunctionCommandRequest = (GoogleFunctionCommandRequest) parameters;
        return googleFunctionDelegateTaskHelper.getCommandResponse(googleFunctionCommandRequest, getLogStreamingTaskClient());
    }

    @Override
    public boolean isSupportingErrorFramework() {
        return true;
    }
}
