package io.harness.rest;

import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Slf4j
public class CallbackWithRetry<T> implements Callback<T> {
  private static final int TOTAL_RETRIES = 3;
  private final Call<T> call;
  private int retryCount;
  private CompletableFuture<T> result;

  public CallbackWithRetry(Call<T> call, CompletableFuture<T> result) {
    this.call = call;
    this.result = result;
  }

  @Override
  public void onResponse(Call<T> call, Response<T> response) {
    if (response.isSuccessful()) {
      result.complete(response.body());
    }
  }

  @Override
  public void onFailure(Call<T> call, Throwable throwable) {
    if (retryCount++ < TOTAL_RETRIES) {
      retry();
    }
  }

  private void retry() {
    call.clone().enqueue(this);
  }
}
