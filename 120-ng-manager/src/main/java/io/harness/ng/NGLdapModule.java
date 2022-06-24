package io.harness.ng;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ldap.service.NGLdapService;
import io.harness.ldap.service.impl.NGLdapServiceImpl;

import com.google.inject.AbstractModule;

@OwnedBy(PL)
public class NGLdapModule extends AbstractModule {
  NextGenConfiguration appConfig;

  public NGLdapModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(NextGenConfiguration.class).toInstance(appConfig);
    // Add service class and their implementations here as and when we add them
    bind(NGLdapService.class).to(NGLdapServiceImpl.class);
  }
}