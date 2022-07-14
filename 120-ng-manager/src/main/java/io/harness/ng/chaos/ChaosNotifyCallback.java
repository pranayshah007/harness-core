package io.harness.ng.chaos;

import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.exception.ExceptionUtils;
import io.harness.ng.chaos.client.ChaosHttpClient;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import com.google.inject.Inject;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class ChaosNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Inject ChaosHttpClient chaosHttpClient;

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    Iterator<Supplier<ResponseData>> iterator = response.values().iterator();
    if (response == null || !iterator.hasNext()) {
      chaosHttpClient.pushTaskResponse(
          ErrorNotifyResponseData.builder().errorMessage("null response from delegate").build());
    }
    try {
      Supplier<ResponseData> responseDataSupplier = iterator.next();
      ResponseData responseData = responseDataSupplier.get();
      chaosHttpClient.pushTaskResponse(responseData);
    } catch (Exception e) {
      log.error("Got error in response", e);
      chaosHttpClient.pushTaskResponse(
          ErrorNotifyResponseData.builder().errorMessage(ExceptionUtils.getMessage(e)).build());
    }
  }
}
