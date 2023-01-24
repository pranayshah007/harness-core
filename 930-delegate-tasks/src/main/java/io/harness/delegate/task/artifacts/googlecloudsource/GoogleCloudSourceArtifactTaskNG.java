package io.harness.delegate.task.artifacts.googlecloudsource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.lang.JoseException;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class GoogleCloudSourceArtifactTaskNG extends AbstractDelegateRunnableTask {
  @Inject private GoogleCloudSourceArtifactTaskHelper googleCloudSourceArtifactTaskHelper;

  public GoogleCloudSourceArtifactTaskNG(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }
  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) parameters;
    return googleCloudSourceArtifactTaskHelper.getArtifactCollectResponse(taskParameters, null);
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
