/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ldap;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSearchTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapGroupSyncTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.service.intfc.ldap.LdapDelegateService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.lang.JoseException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class NGLdapGroupSyncTask extends AbstractDelegateRunnableTask {
  @Inject private LdapDelegateService ldapDelegateService;

  public NGLdapGroupSyncTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return null;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    NGLdapGroupSearchTaskParameters ngLdapGroupSearchParameters = (NGLdapGroupSearchTaskParameters) parameters;
    LdapSettings ldapSettings = ngLdapGroupSearchParameters.getLdapSettings();
    LdapGroupResponse ldapGroupResponse = ldapDelegateService.fetchGroupByDn(
        ldapSettings, ngLdapGroupSearchParameters.getEncryptedDataDetail(), ngLdapGroupSearchParameters.getName());
    return NGLdapGroupSyncTaskResponse.builder().ldapGroupsResponse(ldapGroupResponse).build();
  }
}
