/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.exception;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.AzureAppServiceTaskException;
import io.harness.exception.runtime.azure.AzureAppServicesDeployArtifactFileException;
import io.harness.exception.runtime.azure.AzureAppServicesDeploymentSlotNotFoundException;
import io.harness.exception.runtime.azure.AzureAppServicesWebAppNotFoundException;
import io.harness.rule.Owner;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Response;
import retrofit2.adapter.rxjava.HttpException;

@OwnedBy(HarnessTeam.CDP)
public class AzureAppServicesRuntimeExceptionHandlerTest extends CategoryTest {
  AzureAppServicesRuntimeExceptionHandler exceptionHandler = new AzureAppServicesRuntimeExceptionHandler();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleWebAppNotFound() {
    final AzureAppServicesWebAppNotFoundException notFoundException =
        new AzureAppServicesWebAppNotFoundException("test", "rg");
    WingsException result = exceptionHandler.handleException(notFoundException);
    assertExceptionMessage(result, "Check to make sure web app 'test' exists under resource group 'rg'",
        "Unable to find web app with name 'test' in resource group 'rg'",
        "Not found web app with name: test, resource group name: rg");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleSlotNotFound() {
    final AzureAppServicesDeploymentSlotNotFoundException slotNotFoundException =
        new AzureAppServicesDeploymentSlotNotFoundException(
            "stage", "webapp", "rg", "00000000-0000-0000-0000-000000000000");
    WingsException result = exceptionHandler.handleException(slotNotFoundException);
    assertExceptionMessage(result,
        "Check to make sure you have provided correct deployment slot 'stage' for web app 'webapp'",
        "Unable to find deployment slot 'stage' for web app 'webapp', resource group 'rg' and subscription '00000000-0000-0000-0000-000000000000'",
        "Unable to get deployment slot by slot name: stage, app name: webapp, resource group name: rg, subscription id: 00000000-0000-0000-0000-000000000000");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleDeployArtifactFileHttpException() {
    final okhttp3.Request rawRequest = new Request.Builder()
                                           .method("POST", RequestBody.create(MediaType.get("text/html"), "empty"))
                                           .url("https://microsoft-test-app.appservices.msc.com")
                                           .build();
    final okhttp3.Response rawResponse = new okhttp3.Response.Builder()
                                             .protocol(Protocol.HTTP_1_1)
                                             .message("error")
                                             .code(400)
                                             .request(rawRequest)
                                             .build();
    final HttpException retrofitHttpException =
        new HttpException(Response.error(ResponseBody.create(MediaType.get("text/html"), "empty"), rawResponse));
    final AzureAppServicesDeployArtifactFileException artifactFileException =
        new AzureAppServicesDeployArtifactFileException("/artifact.zip", "ZIP", retrofitHttpException);
    WingsException result = exceptionHandler.handleException(artifactFileException);
    assertExceptionMessage(result, "Check if deployed artifact '/artifact.zip' is packaged 'ZIP' file",
        "HTTP request POST https://microsoft-test-app.appservices.msc.com/ failed with error code '400' while uploading artifact file '/artifact.zip'",
        "Failed to deploy artifact file: /artifact.zip");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleDeployArtifactFileRuntimeException() {
    final AzureAppServicesDeployArtifactFileException artifactFileException =
        new AzureAppServicesDeployArtifactFileException(
            "artifact.zip", "WAR", new RuntimeException("Something failed"));
    WingsException result = exceptionHandler.handleException(artifactFileException);
    assertExceptionMessage(result, "Check if deployed artifact 'artifact.zip' is packaged 'WAR' file",
        "Failed to deploy artifact file: artifact.zip", "Failed to deploy artifact file: artifact.zip");
  }

  private void assertExceptionMessage(
      Exception exception, String expectedHint, String expectedExplanation, String expectedMessage) {
    HintException hint = ExceptionUtils.cause(HintException.class, exception);
    ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, exception);
    AzureAppServiceTaskException taskException = ExceptionUtils.cause(AzureAppServiceTaskException.class, exception);

    assertThat(hint.getMessage()).isEqualTo(expectedHint);
    assertThat(explanation.getMessage()).isEqualTo(expectedExplanation);
    assertThat(taskException.getMessage()).isEqualTo(expectedMessage);
  }
}