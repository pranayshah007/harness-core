package io.harness.ldap.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;

import static software.wings.beans.TaskType.NG_LDAP_TEST_CONN_SETTINGS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ldap.service.NGLdapService;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.service.impl.ldap.LdapDelegateException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGLdapServiceImpl implements NGLdapService {
  public static final String UNKNOWN_RESPONSE_FROM_DELEGATE = "Unknown Response from delegate";
  private final DelegateGrpcClientWrapper delegateService;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  @Override
  public LdapTestResponse validateLdapConnectionSettings(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, software.wings.beans.dto.LdapSettings settings) {
    NGLdapDelegateTaskParameters parameters = NGLdapDelegateTaskParameters.builder().ldapSettings(settings).build();

    DelegateResponseData delegateResponseData = getDelegateResponseData(
        accountIdentifier, orgIdentifier, projectIdentifier, parameters, NG_LDAP_TEST_CONN_SETTINGS);

    NGLdapDelegateTaskResponse delegateTaskResponse = (NGLdapDelegateTaskResponse) delegateResponseData;
    log.info("Delegate response for validateLdapConnectionSettings: "
        + delegateTaskResponse.getLdapTestResponse().getStatus());
    return delegateTaskResponse.getLdapTestResponse();
  }

  private DelegateResponseData getDelegateResponseData(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, NGLdapDelegateTaskParameters parameters, TaskType taskType) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(taskType.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountIdentifier)
            .taskSetupAbstractions(buildAbstractions(accountIdentifier, orgIdentifier, projectIdentifier))
            .build();

    DelegateResponseData delegateResponseData = delegateService.executeSyncTask(delegateTaskRequest);
    validateDelegateTaskResponse(delegateResponseData);
    return delegateResponseData;
  }

  private void validateDelegateTaskResponse(DelegateResponseData delegateResponseData) {
    if (delegateResponseData instanceof ErrorNotifyResponseData) {
      throw new LdapDelegateException(
          UNKNOWN_RESPONSE_FROM_DELEGATE, ((ErrorNotifyResponseData) delegateResponseData).getException());
    } else if (delegateResponseData instanceof RemoteMethodReturnValueData
        && (((RemoteMethodReturnValueData) delegateResponseData).getException() instanceof InvalidRequestException)) {
      throw(InvalidRequestException)((RemoteMethodReturnValueData) delegateResponseData).getException();
    }
  }

  private Map<String, String> buildAbstractions(
      String accountIdIdentifier, String orgIdentifier, String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(2);
    String owner = taskSetupAbstractionHelper.getOwner(accountIdIdentifier, orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, "true");
    return abstractions;
  }
}