package io.harness.delegate.task.citasks.vm.helper;

import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.RUNNER_CONNECT_TIMEOUT_SECS;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.RUNNER_URL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.vm.runner.DestroyVmRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepResponse;
import io.harness.delegate.beans.ci.vm.runner.SetupVmRequest;
import io.harness.delegate.beans.ci.vm.runner.SetupVmResponse;
import io.harness.delegate.beans.ci.vm.runner.PoolOwnerStepResponse;
import io.harness.network.Http;

import com.google.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import okhttp3.MediaType;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class HttpHelper {
  public static final MediaType APPLICATION_JSON = MediaType.parse("application/json; charset=utf-8");
  private final int MAX_ATTEMPTS = 3;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int DELETION_MAX_ATTEMPTS = 15;

  public RunnerRestClient getRunnerClient(int timeoutInSecs) {
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(RUNNER_URL)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .client(Http.getUnsafeOkHttpClient(RUNNER_URL, RUNNER_CONNECT_TIMEOUT_SECS, timeoutInSecs))
                            .build();
    return retrofit.create(RunnerRestClient.class);
  }

  public Response<SetupVmResponse> setupStageWithRetries(SetupVmRequest setupVmRequest) {
    // TODO(shubham): Retry on stage setup can create 2 VMs.
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying failed to setup stage; attempt: {}", "Failing to setup stage after retrying {} times");
    return Failsafe.with(retryPolicy).get(() -> getRunnerClient(600).setup(setupVmRequest).execute());
  }

  public Response<ExecuteStepResponse> executeStepWithRetries(ExecuteStepRequest executeStepRequest) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying failed to execute step; attempt: {}", "Failing to execute step after retrying {} times");
    return Failsafe.with(retryPolicy).get(() -> getRunnerClient(14400).step(executeStepRequest).execute());
  }

  public Response<Void> cleanupStageWithRetries(DestroyVmRequest destroyVmRequest) {
    RetryPolicy<Object> retryPolicy = getRetryPolicyForDeletion(
        "[Retrying failed to cleanup stage; attempt: {}", "Failing to cleanup stage after retrying {} times");
    return Failsafe.with(retryPolicy).get(() -> getRunnerClient(600).destroy(destroyVmRequest).execute());
  }

  public Response<PoolOwnerStepResponse> isPoolOwner(Map<String, String> params) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying failed to check for pool_owner; attempt: {}",
        "Failing to check for pool_owner after retrying {} times");
    return Failsafe.with(retryPolicy).get(() -> getRunnerClient(600).poolOwner(params.get("pool_id")).execute());
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  private RetryPolicy<Object> getRetryPolicyForDeletion(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withMaxAttempts(DELETION_MAX_ATTEMPTS)
        .withBackoff(5, 60, ChronoUnit.SECONDS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
