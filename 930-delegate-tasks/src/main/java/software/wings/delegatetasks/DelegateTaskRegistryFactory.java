package software.wings.delegatetasks;

import io.harness.delegate.TaskDetailsV2;
import io.harness.delegate.beans.ci.docker.CIDockerCleanupStepRequest;
import io.harness.delegate.beans.ci.docker.CIDockerExecuteStepRequest;
import io.harness.delegate.beans.ci.docker.CIDockerInitializeTaskRequest;
import io.harness.delegate.beans.ci.docker.DockerTaskExecutionResponse;

import software.wings.beans.TaskType;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DelegateTaskRegistryFactory {
  public void bindDelegateTasksWithTaskData(Binder binder) {
    MapBinder<TaskType, TaskDetailsV2> mapBinder =
        MapBinder.newMapBinder(binder, new TypeLiteral<TaskType>() {}, new TypeLiteral<TaskDetailsV2>() {});

    mapBinder.addBinding(TaskType.CI_DOCKER_INITIALIZE_TASK)
        .toInstance(TaskDetailsV2.builder()
                        .taskRequest(CIDockerInitializeTaskRequest.class)
                        .taskResponse(DockerTaskExecutionResponse.class)
                        .unsupported(true)
                        .build());
    mapBinder.addBinding(TaskType.CI_DOCKER_EXECUTE_TASK)
        .toInstance(TaskDetailsV2.builder()
                        .taskRequest(CIDockerExecuteStepRequest.class)
                        .taskResponse(DockerTaskExecutionResponse.class)
                        .unsupported(true)
                        .build());
    mapBinder.addBinding(TaskType.CI_DOCKER_CLEANUP_TASK)
        .toInstance(TaskDetailsV2.builder()
                        .taskRequest(CIDockerCleanupStepRequest.class)
                        .taskResponse(DockerTaskExecutionResponse.class)
                        .unsupported(true)
                        .build());
    mapBinder.addBinding(TaskType.K8S_COMMAND_TASK)
        .toInstance(TaskDetailsV2.builder()
                        .taskRequest(K8sTaskParameters.class)
                        .taskResponse(K8sTaskExecutionResponse.class)
                        .unsupported(false)
                        .build());
  }
}
