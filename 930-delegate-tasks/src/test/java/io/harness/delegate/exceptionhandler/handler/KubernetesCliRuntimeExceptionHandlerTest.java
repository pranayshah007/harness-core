/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.configuration.KubernetesCliCommandType.APPLY;
import static io.harness.configuration.KubernetesCliCommandType.DELETE;
import static io.harness.configuration.KubernetesCliCommandType.DRY_RUN;
import static io.harness.configuration.KubernetesCliCommandType.GENERATE_HASH;
import static io.harness.configuration.KubernetesCliCommandType.SCALE;
import static io.harness.configuration.KubernetesCliCommandType.STEADY_STATE_CHECK;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.configuration.KubernetesCliCommandType;
import io.harness.delegate.task.k8s.exception.KubernetesCliRuntimeExceptionHandler;
import io.harness.exception.ExplanationException;
import io.harness.exception.FailureType;
import io.harness.exception.HintException;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.WingsException;
import io.harness.k8s.ProcessResponse;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.rule.Owner;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(CDP)
public class KubernetesCliRuntimeExceptionHandlerTest extends CategoryTest {
  private KubernetesCliRuntimeExceptionHandler exceptionHandler = new KubernetesCliRuntimeExceptionHandler();
  private static final String kubectlTimeoutMessagePart = "i/o timeout";
  private static final String kubectlConnectionRefusedMessagePart1 = "The connection to the server";
  private static final String kubectlConnectionRefusedMessagePart2 = "was refused";
  private static final String kubectlConnectionRefusedMessagePart3 = "did you specify the right host or port";
  private static final String kubectlConnectionRefusedMessagePart4 = "connection refused";
  private static final String kubectlUnableToConnectMessagePart = "transport connection broken";

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void verifyHandledExceptions() {
    assertThat(KubernetesCliRuntimeExceptionHandler.exceptions())
        .containsExactly(KubernetesCliTaskRuntimeException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleUnknownFieldInDryRunException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.MULTIPLE_UNKNOWN_FIELDS), DRY_RUN);
    WingsException handledException = exceptionHandler.handleException(exception);

    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.VALIDATION_FAILED_UNKNOWN_FIELD);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage())
        .contains("Ingress.spec.rules[0].http.paths[0].backend:serviceName");
    assertThat(handledException.getCause().getMessage())
        .contains("Ingress.spec.rules[0].http.paths[0].backend:servicePort");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleInvalidTypeInDryRunException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.INVALID_TYPE_VALUE), DRY_RUN);
    exception.setKubectlVersion("");
    exception.setResourcesNotApplied("");
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.VALIDATION_FAILED_INVALID_TYPE);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Invalid type value for [Deployment.spec.replicas]");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause().getCause().getCause())
        .isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void handleInvalidTypeInDryRunExceptionKubectlVersionAndResourcesNull() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.INVALID_TYPE_VALUE), DRY_RUN);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.VALIDATION_FAILED_INVALID_TYPE);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Invalid type value for [Deployment.spec.replicas]");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause().getCause().getCause())
        .isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void handleInvalidTypeInDryRunExceptionKubectlVersionAndResourcesNonNull() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.INVALID_TYPE_VALUE), DRY_RUN);
    exception.setKubectlVersion(
        "{\"clientVersion\":{\"gitVersion\":\"v1.19.2\"},\"serverVersion\":{\"gitVersion\":\"v1.23.14-gke.1800\"}}");
    exception.setResourcesNotApplied("deployment/test-svc");
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.VALIDATION_FAILED_INVALID_TYPE);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Invalid type value for [Deployment.spec.replicas]");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getMessage()).contains("kubectl binary path");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause().getMessage()).contains("deployment/test-svc");
    assertThat(handledException.getCause().getCause().getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause().getCause().getMessage())
        .contains("clientVersion: [v1.19.2]");
    assertThat(handledException.getCause().getCause().getCause().getCause().getCause())
        .isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void handleInvalidTypeInDryRunExceptionKubectlVersionEmptyVersion() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.INVALID_TYPE_VALUE), DRY_RUN);
    exception.setKubectlVersion("Wrong output");
    exception.setResourcesNotApplied("deployment/test-svc");
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException.getCause().getCause().getCause().getCause().getMessage()).contains("");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleMissingRequiredFieldInDryRunException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.MISSING_REQUIRED_FIELD), DRY_RUN);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.MISSING_REQUIRED_FIELD);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlTimeoutInDryRunException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_COMMAND_TIMEOUT_MESSAGE, DRY_RUN,
        Collections.singletonList(kubectlTimeoutMessagePart), KubernetesExceptionHints.KUBECTL_COMMAND_TIMEOUT);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlConnectionRefusedInDryRunException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_CONNECTION_REFUSED_MESSAGE, DRY_RUN,
        Arrays.asList(kubectlConnectionRefusedMessagePart1, kubectlConnectionRefusedMessagePart2,
            kubectlConnectionRefusedMessagePart3),
        KubernetesExceptionHints.KUBECTL_CONNECTION_REFUSED);

    testConnectivityTypeException(CliErrorMessages.KUBECTL_CONNECTION_REFUSED_MESSAGE_2, DRY_RUN,
        Arrays.asList(kubectlConnectionRefusedMessagePart4), KubernetesExceptionHints.KUBECTL_CONNECTION_REFUSED);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlUnableToConnectInDryRunException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_UNABLE_TO_CONNECT_MESSAGE, DRY_RUN,
        Collections.singletonList(kubectlUnableToConnectMessagePart),
        KubernetesExceptionHints.KUBECTL_UNABLE_TO_CONNECT);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void handleNoObjectToApplyInDryRunException() {
    KubernetesCliTaskRuntimeException exception = new KubernetesCliTaskRuntimeException(
        createProcessResponse(CliErrorMessages.KUBECTL_COMMAND_NO_OBJECT_TO_APPLY_MESSAGE), DRY_RUN);
    exception.setKubectlVersion("");
    exception.setResourcesNotApplied("");
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.NO_OBJECT_PASSED);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getMessage())
        .contains(CliErrorMessages.KUBECTL_COMMAND_NO_OBJECT_TO_APPLY_MESSAGE);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleDryRunException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), DRY_RUN);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.DRY_RUN_MANIFEST_FAILED);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleSteadyStateProgressDeadlineFailure() {
    KubernetesCliTaskRuntimeException exception = new KubernetesCliTaskRuntimeException(
        createProcessResponse(CliErrorMessages.STEADY_STATE_PROGRESS_DEADLINE_ERROR), STEADY_STATE_CHECK);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage())
        .isEqualTo(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_FAILED
            + KubernetesExceptionHints.DEPLOYMENT_PROGRESS_DEADLINE_DOC_REFERENCE);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Resources failed to reach steady state");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleSteadyStateProgressDeletedObject() {
    KubernetesCliTaskRuntimeException exception = new KubernetesCliTaskRuntimeException(
        createProcessResponse(CliErrorMessages.OBJECT_DELETED), STEADY_STATE_CHECK);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.MISSING_OBJECT_ERROR);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlTimeoutInSteadyStateException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_COMMAND_TIMEOUT_MESSAGE, STEADY_STATE_CHECK,
        Collections.singletonList(kubectlTimeoutMessagePart), KubernetesExceptionHints.KUBECTL_COMMAND_TIMEOUT);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlConnectionRefusedInSteadyStateException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_CONNECTION_REFUSED_MESSAGE, STEADY_STATE_CHECK,
        Arrays.asList(kubectlConnectionRefusedMessagePart1, kubectlConnectionRefusedMessagePart2,
            kubectlConnectionRefusedMessagePart3),
        KubernetesExceptionHints.KUBECTL_CONNECTION_REFUSED);

    testConnectivityTypeException(CliErrorMessages.KUBECTL_CONNECTION_REFUSED_MESSAGE_2, STEADY_STATE_CHECK,
        Arrays.asList(kubectlConnectionRefusedMessagePart4), KubernetesExceptionHints.KUBECTL_CONNECTION_REFUSED);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlUnableToConnectInSteadyStateException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_UNABLE_TO_CONNECT_MESSAGE, STEADY_STATE_CHECK,
        Collections.singletonList(kubectlUnableToConnectMessagePart),
        KubernetesExceptionHints.KUBECTL_UNABLE_TO_CONNECT);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleSteadyStateException() {
    KubernetesCliTaskRuntimeException exception = new KubernetesCliTaskRuntimeException(
        createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), STEADY_STATE_CHECK);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_FAILED);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleScaleMissingResourceException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.SCALE_MISSING_RESOURCE), SCALE);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.SCALE_CLI_FAILED);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Failed to scale resource [abc]");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlTimeoutInScaleException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_COMMAND_TIMEOUT_MESSAGE, SCALE,
        Collections.singletonList(kubectlTimeoutMessagePart), KubernetesExceptionHints.KUBECTL_COMMAND_TIMEOUT);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlConnectionRefusedInScaleException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_CONNECTION_REFUSED_MESSAGE, SCALE,
        Arrays.asList(kubectlConnectionRefusedMessagePart1, kubectlConnectionRefusedMessagePart2,
            kubectlConnectionRefusedMessagePart3),
        KubernetesExceptionHints.KUBECTL_CONNECTION_REFUSED);

    testConnectivityTypeException(CliErrorMessages.KUBECTL_CONNECTION_REFUSED_MESSAGE_2, SCALE,
        Arrays.asList(kubectlConnectionRefusedMessagePart4), KubernetesExceptionHints.KUBECTL_CONNECTION_REFUSED);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlUnableToConnectInScaleException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_UNABLE_TO_CONNECT_MESSAGE, SCALE,
        Collections.singletonList(kubectlUnableToConnectMessagePart),
        KubernetesExceptionHints.KUBECTL_UNABLE_TO_CONNECT);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleScaleException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), SCALE);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.SCALE_CLI_FAILED_GENERIC);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleInvalidCharactersError() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.INVALID_CHARACTERS_ERROR), APPLY);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.K8S_CHARACTER_ERROR);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Deployment/Pqr");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleApplyManifestErrors() {
    Map<String, String> expectedHintMap = createExceptionHintMap();

    for (Map.Entry<String, String> errorMessageAndHint : expectedHintMap.entrySet()) {
      KubernetesCliTaskRuntimeException exception =
          new KubernetesCliTaskRuntimeException(createProcessResponse(errorMessageAndHint.getKey()), APPLY);
      WingsException handledException = exceptionHandler.handleException(exception);
      assertThat(handledException).isInstanceOf(HintException.class);
      assertThat(handledException.getMessage()).isEqualTo(errorMessageAndHint.getValue());
      assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
      assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
      assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
    }
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleApplyException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), APPLY);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.APPLY_MANIFEST_FAILED);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlTimeoutInApplyException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_COMMAND_TIMEOUT_MESSAGE, APPLY,
        Collections.singletonList(kubectlTimeoutMessagePart), KubernetesExceptionHints.KUBECTL_COMMAND_TIMEOUT);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlConnectionRefusedInApplyException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_CONNECTION_REFUSED_MESSAGE, APPLY,
        Arrays.asList(kubectlConnectionRefusedMessagePart1, kubectlConnectionRefusedMessagePart2,
            kubectlConnectionRefusedMessagePart3),
        KubernetesExceptionHints.KUBECTL_CONNECTION_REFUSED);

    testConnectivityTypeException(CliErrorMessages.KUBECTL_CONNECTION_REFUSED_MESSAGE_2, APPLY,
        Arrays.asList(kubectlConnectionRefusedMessagePart4), KubernetesExceptionHints.KUBECTL_CONNECTION_REFUSED);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlUnableToConnectInApplyException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_UNABLE_TO_CONNECT_MESSAGE, APPLY,
        Collections.singletonList(kubectlUnableToConnectMessagePart),
        KubernetesExceptionHints.KUBECTL_UNABLE_TO_CONNECT);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleGenericException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), DELETE);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.GENERIC_CLI_FAILURE);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleMultipleNamingExceptions() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.MULTIPLE_NAMING_ERRORS), APPLY);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.K8S_CHARACTER_ERROR);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Secret/InvalidSecret");
    assertThat(handledException.getCause().getMessage()).contains("ConfigMap/InvalidConfigmap");
    assertThat(handledException.getCause().getMessage()).contains("Service/InvalidService");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleUnresolvedFieldError() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.UNRESOLVED_VALUE_ERROR), APPLY);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.UNRESOLVED_MANIFEST_FIELD);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void handleHashGenerationFailure() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), GENERATE_HASH);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).contains(KubernetesExceptionHints.HASH_CALCULATION_FAILED_ERROR);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
    assertThat(handledException.getCause().getCause().getMessage()).contains(CliErrorMessages.DUMMY_MESSAGE);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlTimeoutInHashGenerationException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_COMMAND_TIMEOUT_MESSAGE, GENERATE_HASH,
        Collections.singletonList(kubectlTimeoutMessagePart), KubernetesExceptionHints.KUBECTL_COMMAND_TIMEOUT);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlConnectionRefusedInHashGenerationException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_CONNECTION_REFUSED_MESSAGE, GENERATE_HASH,
        Arrays.asList(kubectlConnectionRefusedMessagePart1, kubectlConnectionRefusedMessagePart2,
            kubectlConnectionRefusedMessagePart3),
        KubernetesExceptionHints.KUBECTL_CONNECTION_REFUSED);

    testConnectivityTypeException(CliErrorMessages.KUBECTL_CONNECTION_REFUSED_MESSAGE_2, GENERATE_HASH,
        Arrays.asList(kubectlConnectionRefusedMessagePart4), KubernetesExceptionHints.KUBECTL_CONNECTION_REFUSED);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void handleKubectlUnableToConnectInHashGenerationException() {
    testConnectivityTypeException(CliErrorMessages.KUBECTL_UNABLE_TO_CONNECT_MESSAGE, GENERATE_HASH,
        Collections.singletonList(kubectlUnableToConnectMessagePart),
        KubernetesExceptionHints.KUBECTL_UNABLE_TO_CONNECT);
  }

  private ProcessResponse createProcessResponse(String cliErrorMessage) {
    return ProcessResponse.builder()
        .processResult(new ProcessResult(1, new ProcessOutput("process failed.".getBytes(StandardCharsets.UTF_8))))
        .errorMessage(cliErrorMessage)
        .build();
  }

  private Map<String, String> createExceptionHintMap() {
    Map<String, String> expectedHintMap = new HashMap<>();
    expectedHintMap.put(CliErrorMessages.IMMUTABLE_OBJECT_ERROR, KubernetesExceptionHints.IMMUTABLE_FIELD);
    expectedHintMap.put(CliErrorMessages.RESOURCE_NOT_FOUND, KubernetesExceptionHints.MISSING_RESOURCE);
    expectedHintMap.put(CliErrorMessages.UNSUPPORTED_VALUE, KubernetesExceptionHints.UNSUPPORTED_VALUE);
    expectedHintMap.put(CliErrorMessages.FORBIDDEN_MESSAGE, KubernetesExceptionHints.K8S_API_FORBIDDEN_EXCEPTION);
    return expectedHintMap;
  }

  private void testConnectivityTypeException(String cliErrorMessage, KubernetesCliCommandType kubernetesCliCommandType,
      List<String> identifyingPartsOfCauseException, String handledExceptionExpectedMessage) {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(cliErrorMessage), kubernetesCliCommandType);
    exception.setKubectlVersion("");
    exception.setResourcesNotApplied("");
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(handledExceptionExpectedMessage);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getFailureTypes().contains(FailureType.CONNECTIVITY));
    for (String partOfCauseException : identifyingPartsOfCauseException) {
      assertThat(handledException.getCause().getCause().getMessage()).contains(partOfCauseException);
    }
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  static class CliErrorMessages {
    private CliErrorMessages() {}
    static final String DUMMY_MESSAGE = "Some random error message";
    static final String MULTIPLE_UNKNOWN_FIELDS =
        "error: error validating \"manifests-dry-run.yaml\": error validating data: [ValidationError(Ingress.spec.rules[0].http.paths[0].backend): unknown field \"serviceName\" in io.k8s.api.networking.v1.IngressBackend, ValidationError(Ingress.spec.rules[0].http.paths[0].backend): unknown field \"servicePort\" in io.k8s.api.networking.v1.IngressBackend]; if you choose to ignore these errors, turn validation off with --validate=false]";
    static final String INVALID_TYPE_VALUE =
        "error validating data: ValidationError(Deployment.spec.replicas): invalid type for io.k8s.api.apps.v1.DeploymentSpec.replicas: got \"string\", expected \"integer\"; if you choose to ignore these errors, turn validation off with --validate=false";
    static final String STEADY_STATE_PROGRESS_DEADLINE_ERROR =
        "error: deployment \"abc\" exceeded its progress deadline";
    static final String SCALE_MISSING_RESOURCE = "deployments.apps \"abc\" not found";
    static final String INVALID_CHARACTERS_ERROR =
        "The Deployment \"Pqr\" is invalid: metadata.name: Invalid value: \"Pqr\": a DNS-1123 subdomain must consist of lower case alphanumeric characters, '-' or '.', and must start and end with an alphanumeric character (e.g. 'example.com', regex used for validation is '[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*')";
    static final String MULTIPLE_NAMING_ERRORS =
        "Error from server (Invalid): error when creating \"manifests.yaml\": Secret \"InvalidSecret\" is invalid: metadata.name: Invalid value: \"InvalidSecret\": a DNS-1123 subdomain must consist of lower case alphanumeric characters, '-' or '.', and must start and end with an alphanumeric character (e.g. 'example.com', regex used for validation is '[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*')Error from server (Invalid): error when creating \"manifests.yaml\": ConfigMap \"InvalidConfigmap\" is invalid: metadata.name: Invalid value: \"InvalidConfigmap\": a DNS-1123 subdomain must consist of lower case alphanumeric characters, '-' or '.', and must start and end with an alphanumeric character (e.g. 'example.com', regex used for validation is '[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*')Error from server (Invalid): error when creating \"manifests.yaml\": Service \"InvalidService\" is invalid: [metadata.name: Invalid value: \"InvalidService\": a DNS-1035 label must consist of lower case alphanumeric characters or '-', start with an alphabetic character, and end with an alphanumeric character (e.g. 'my-name',  or 'abc-123', regex used for validation is '[a-z]([-a-z0-9]*[a-z0-9])?')";
    static final String UNRESOLVED_VALUE_ERROR =
        "Error from server (Invalid): error when creating \"manifests.yaml\": Secret \"<no value>-1\" is invalid: metadata.name: Invalid value: \"<no value>-1\": a DNS-1123 subdomain must consist of lower case alphanumeric characters, '-' or '.', and must start and end with an alphanumeric character (e.g. 'example.com', regex used for validation is '[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*')";
    static final String MISSING_REQUIRED_FIELD =
        "error validating data: ValidationError(StatefulSet.spec): missing required field \"serviceName\" in io.k8s.api.apps.v1.StatefulSetSpec; if you choose to ignore these errors, turn validation off with --validate=false";
    static final String OBJECT_DELETED = "error: object has been deleted";
    static final String IMMUTABLE_OBJECT_ERROR =
        "The Deployment \"test\" is invalid: spec.selector: Invalid value: v1.LabelSelector{MatchLabels:map[string]string{\"app\":\"test\"}, MatchExpressions:[]v1.LabelSelectorRequirement(nil)}: field is immutable]";
    static final String RESOURCE_NOT_FOUND =
        "Error from server (NotFound): error when creating \"manifests.yaml\": namespaces \"-test\" not found";
    static final String UNSUPPORTED_VALUE =
        "The Deployment \"nginx-ingress-controller\" is invalid: spec.template.spec.containers[0].livenessProbe.httpGet.scheme: Unsupported value: \"http\": supported values: \"HTTP\", \"HTTPS\"";
    static final String FORBIDDEN_MESSAGE =
        "Error from server (Forbidden): error when retrieving current configuration of:Resource: \"/v1, Resource=services\", GroupVersionKind: \"/v1, Kind=Service\"Name: \"latest\", Namespace: \"dev\"Object: &{map[\"metadata\":map[\"labels\":map[\"app\":\"latest\"] \"name\":\"latest\" \"namespace\":\"dev\" \"annotations\":map[\"kubernetes.io/change-cause\":\"kubectl apply --kubeconfig=config --filename=manifests.yaml --record=true\" \"kubectl.kubernetes.io/last-applied-configuration\":\"\"]] \"spec\":map[\"ports\":[map[\"port\":'\\u1f90']] \"selector\":map[\"harness.io/name\":\"latest\"] \"type\":\"ClusterIP\"] \"apiVersion\":\"v1\" \"kind\":\"Service\"]}from server for: \"manifests.yaml\": services \"np-latest\" is forbidden: User \"system:serviceaccount:hrns\" cannot get resource \"services\" in API group \"\" in the namespace \"standard\"Error from server (Forbidden): error when retrieving current configuration of:Resource: \"apps/v1, Resource=statefulsets\", GroupVersionKind: \"apps/v1, Kind=StatefulSet\"Name: \"np-latest\", Namespace: \"standard\"Object: &{map[\"apiVersion\":\"apps/v1\" \"kind\":\"StatefulSet\" \"metadata\":map[\"labels\":map[\"harness.io/name\":\"np-latest\"] \"name\":\"np-latest\" \"namespace\":\"standard\" \"annotations\":map[\"kubernetes.io/change-cause\":\"kubectl apply --kubeconfig=config --filename=manifests.yaml --record=true\" \"kubectl.kubernetes.io/last-applied-configuration\":\"\"]] \"spec\":map[\"replicas\":'\\x01' \"selector\":map[\"matchLabels\":map[\"harness.io/name\":\"tkgi-tepmr22-k8s-np-latest\"]] \"serviceName\":\"\" \"template\":map[\"metadata\":map[\"labels\":map[\"harness.io/name\":\"np-latest\" \"harness.io/release-name\":\"81eeaa34dc\"]] \"spec\":map[\"containers\":[map[\"resources\":map[\"limits\":map[\"cpu\":\"500m\" \"memory\":\"2Gi\"] \"requests\":map[\"cpu\":\"500m\" \"memory\":\"2Gi\"]] \"volumeMounts\":[map[\"mountPath\":\"/var/certs\" \"name\":\"cert\" \"readOnly\":%!q(bool=false)]] \"image\":\"w/hrns:0.0.1\" \"lifecycle\":map[\"postStart\":map[\"exec\":map[\"command\":[\"/bin/bash\" \"-c\" \"cp /var/certs/cacerts jdk8u242-b08-jre/lib/security/cacerts\\n\"]]]] \"livenessProbe\":map[\"exec\":map[\"command\":[\"bash\" \"-c\" \"[[ -e /watcher-data && $(($(date +%s000) - $(grep heartbeat /opt/harness-delegate/msg/data/watcher-data | cut -d \\\":\\\" -f 2 | cut -d \\\",\\\" -f 1))) -lt 300000 ]]\"]] \"failureThreshold\":'\\x02' \"initialDelaySeconds\":'\\u00f0' \"periodSeconds\":'\\n' \"successThreshold\":'\\x01' \"timeoutSeconds\":'\\n'] \"ports\":[map[\"containerPort\":'\\u1f90']] \"imagePullPolicy\":\"Always\" \"name\":\"delegate\" \"readinessProbe\":map[\"exec\":map[\"command\":[\"test\" \"-s\" \"delegate.log\"]] \"initialDelaySeconds\":'\\x14' \"periodSeconds\":'\\n']]] \"imagePullSecrets\":[map[\"name\":\"hrns-cred\"]] \"serviceAccountName\":\"hrns-sa\" \"volumes\":[map[\"configMap\":map[\"name\":\"ca-bundle-1\"] \"name\":\"cert\"]]]] \"podManagementPolicy\":\"Parallel\"]]}from server for: \"manifests.yaml\": statefulsets.apps \"latest\" is forbidden: User \"system:serviceaccount:test\" cannot get resource \"statefulsets\" in API group \"apps\" in the namespace \"abc\"";
    static final String KUBECTL_COMMAND_TIMEOUT_MESSAGE =
        "Unable to connect to the server: dial tcp X.X.X.X:443: i/o timeout";
    static final String KUBECTL_CONNECTION_REFUSED_MESSAGE =
        "The connection to the server someHostName:443 was refused - did you specify the right host or port?";
    static final String KUBECTL_CONNECTION_REFUSED_MESSAGE_2 =
        "unable to recognize \"manifests.yaml\": Get https://localhost/api?timeout=32s: dial tcp [::1]:443: connect: connection refused";
    static final String KUBECTL_UNABLE_TO_CONNECT_MESSAGE =
        "Unable to connect to the server: net/http: HTTP/1.x transport connection broken: malformed HTTP status code \"\\x00\\x00\\x00\\x04\\b\\x00\\x00\\x00\\x00\\x00\\x00\\x0f\\x00\\x01\\x00\\x00:\\a\\x00\\x00\\x00\\x00\\x00\\u007f\\xff\\xff\\xff\\x00\\x00\\x00\\x01Unexpected\"";

    static final String KUBECTL_COMMAND_NO_OBJECT_TO_APPLY_MESSAGE = "error: no objects passed to apply";
  }
}
