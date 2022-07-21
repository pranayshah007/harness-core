/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.agent;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.service.impl.AgentMtlsEndpointServiceImpl;
import io.harness.service.impl.AgentMtlsEndpointServiceReadOnlyImpl;
import io.harness.service.intfc.AgentMtlsEndpointService;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class AgentMtlsModule extends AbstractModule {
  private final String mtlsSubdomain;

  public AgentMtlsModule() {
    this(null);
  }

  public AgentMtlsModule(@Nullable String mtlsSubdomain) {
    this.mtlsSubdomain = mtlsSubdomain;
  }

  @Provides
  @Named("agentMtlsSubdomain")
  public String agentMtlsSubdomain() {
    return this.mtlsSubdomain;
  }

  @Override
  protected void configure() {
    log.info("Configure agent mTLS with subdomain '{}'.", this.mtlsSubdomain);

    // Depending on configuration, only use read-only impl. so any non-get operations throw runtime exceptions.
    if (StringUtils.isBlank(this.mtlsSubdomain)) {
      bind(AgentMtlsEndpointService.class).to(AgentMtlsEndpointServiceReadOnlyImpl.class);
    } else {
      bind(AgentMtlsEndpointService.class).to(AgentMtlsEndpointServiceImpl.class);
    }
  }
}
