package io.harness.delegate.task.ldap;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ldap.LdapGroupSearchTaskParameters;
import io.harness.delegate.beans.ldap.LdapGroupSearchTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.service.intfc.ldap.LdapDelegateService;

import com.google.inject.Inject;
import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class NGLdapGroupSearchTask extends AbstractDelegateRunnableTask {
  @Inject private LdapDelegateService ldapDelegateService;

  public NGLdapGroupSearchTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
                               Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    LdapGroupSearchTaskParameters ngLdapGroupSearchParameters = (LdapGroupSearchTaskParameters) parameters;
    LdapSettings ldapSettings = ngLdapGroupSearchParameters.getLdapSettings();
    Collection<LdapGroupResponse> ldapGroupResponses =
        ldapDelegateService.searchGroupsByName(ldapSettings,
            ngLdapGroupSearchParameters.getEncryptedDataDetail(), ngLdapGroupSearchParameters.getName());
    return LdapGroupSearchTaskResponse.builder().ldapListGroupsResponses(ldapGroupResponses).build();
  }
}
