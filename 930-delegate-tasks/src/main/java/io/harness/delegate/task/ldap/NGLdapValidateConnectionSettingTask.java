/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ldap;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.service.intfc.ldap.LdapDelegateService;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class NGLdapValidateConnectionSettingTask extends AbstractDelegateRunnableTask {
  @Inject private LdapDelegateService ldapDelegateService;

  public NGLdapValidateConnectionSettingTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    NGLdapDelegateTaskParameters ngLdapGroupSearchParameters = (NGLdapDelegateTaskParameters) parameters;
    LdapSettings ldapSettings = ngLdapGroupSearchParameters.getLdapSettings();
    LdapTestResponse ldapTestResponse = ldapDelegateService.validateLdapConnectionSettings(
        ldapSettings, ngLdapGroupSearchParameters.getEncryptedDataDetail());
    return NGLdapDelegateTaskResponse.builder().ldapTestResponse(ldapTestResponse).build();
  }
}
