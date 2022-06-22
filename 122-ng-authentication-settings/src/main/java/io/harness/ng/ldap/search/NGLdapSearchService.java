/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.ldap.search;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;

import static software.wings.beans.TaskType.NG_LDAP_SEARCH_GROUPS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ldap.LdapGroupSearchTaskParameters;
import io.harness.delegate.beans.ldap.LdapGroupSearchTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryptors.DelegateTaskUtils;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.service.impl.ldap.LdapDelegateException;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.ldaptive.LdapException;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGLdapSearchService {
  private final DelegateGrpcClientWrapper delegateService;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;

  @Inject
  public NGLdapSearchService(
      DelegateGrpcClientWrapper delegateService, TaskSetupAbstractionHelper taskSetupAbstractionHelper) {
    this.delegateService = delegateService;
    this.taskSetupAbstractionHelper = taskSetupAbstractionHelper;
  }

  public Collection<LdapGroupResponse> searchGroupsByName(
      LdapSettings settings, EncryptedDataDetail encryptedDataDetail, String nameQuery) {
    LdapGroupSearchTaskParameters parameters = LdapGroupSearchTaskParameters.builder()
                                                   .ldapSettings(settings)
                                                   .encryptedDataDetail(encryptedDataDetail)
                                                   .name(nameQuery)
                                                   .build();

    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(NG_LDAP_SEARCH_GROUPS.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(settings.getAccountId())
            .taskSetupAbstractions(buildAbstractions(settings.getAccountId(), null, null))
            .build();

    DelegateResponseData delegateResponseData = delegateService.executeSyncTask(delegateTaskRequest);
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);

    if (!(delegateResponseData instanceof LdapGroupSearchTaskResponse)) {
      log.error("Group name search query {}, failed on ldap server in account {}", nameQuery, settings.getAccountId());
      throw new LdapDelegateException(
          NG_LDAP_SEARCH_GROUPS.name(), new LdapException("Groups name search failed on ldap server"));
    }

    LdapGroupSearchTaskResponse groupSearchResponse = (LdapGroupSearchTaskResponse) delegateResponseData;

    return groupSearchResponse.getLdapListGroupsResponses();
  }

  private Map<String, String> buildAbstractions(
      String accountIdIdentifier, String orgIdentifier, String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(2);
    // Verify if its a Task from NG
    String owner = taskSetupAbstractionHelper.getOwner(accountIdIdentifier, orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, "true");
    return abstractions;
  }
}
