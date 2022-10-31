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
import io.harness.delegate.beans.ldap.NGLdapTestAuthenticationTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapTestAuthenticationTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;

import software.wings.beans.dto.LdapSettings;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.service.intfc.ldap.LdapDelegateService;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class NGLdapTestAuthenticationTask extends AbstractDelegateRunnableTask {
  @Inject private LdapDelegateService ldapDelegateService;

  public NGLdapTestAuthenticationTask(DelegateTaskPackage delegateTaskPackage,
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
    NGLdapTestAuthenticationTaskParameters ngLdapTestAuthenticationTaskParams =
        (NGLdapTestAuthenticationTaskParameters) parameters;
    LdapSettings ldapSettings = ngLdapTestAuthenticationTaskParams.getLdapSettings();
    LdapResponse ldapAuthenticationResponse = ldapDelegateService.authenticate(ldapSettings,
        ngLdapTestAuthenticationTaskParams.getSettingsEncryptedDataDetail(),
        ngLdapTestAuthenticationTaskParams.getUsername(),
        ngLdapTestAuthenticationTaskParams.getPasswordEncryptedDataDetail());
    return NGLdapTestAuthenticationTaskResponse.builder()
        .ldapAuthenticationResponse(ldapAuthenticationResponse)
        .build();
  }
}
