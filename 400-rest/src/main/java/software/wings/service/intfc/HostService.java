/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.stream.BoundedInputStream;
import io.harness.validation.Update;

import software.wings.beans.InfrastructureMapping;
import software.wings.beans.infrastructure.Host;
import software.wings.service.intfc.ownership.OwnedByInfrastructureMapping;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by anubhaw on 5/9/16.
 */
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface HostService extends OwnedByInfrastructureMapping {
  /**
   * List.
   *
   * @param req the req
   * @return the page response
   */
  PageResponse<Host> list(PageRequest<Host> req);

  /**
   * Gets the.
   *
   * @param appId  the app id
   * @param envId  the env id
   * @param hostId the host id
   * @return the host
   */
  Host get(@NotEmpty String appId, @NotEmpty String envId, @NotEmpty String hostId);

  /**
   * Update.
   *
   * @param envId the env id
   * @param host  the host
   * @return the host
   */
  @ValidationGroups(Update.class) Host update(String envId, @Valid Host host);

  /**
   * Delete.
   *
   * @param appId  the app id
   * @param envId  the env id
   * @param hostId the host id
   */
  void delete(@NotEmpty String appId, @NotEmpty String envId, @NotEmpty String hostId);

  /**
   * Prune owned from the app objects.
   *
   * @param appId the app id
   * @param hostId the host id
   */
  void pruneDescendingEntities(@NotEmpty String appId, @NotEmpty String hostId);

  /**
   * Import hosts.
   *
   * @param infraId            the infra id
   * @param appId              the app id
   * @param envId              the infra id
   * @param boundedInputStream the bounded input stream
   * @return the int
   */
  int importHosts(@NotEmpty String infraId, @NotEmpty String appId, @NotEmpty String envId,
      @NotNull BoundedInputStream boundedInputStream);

  /**
   * Gets the hosts by id.
   *
   * @param appId     the app id
   * @param envId     the env id
   * @param hostUuids the host uuids
   * @return the hosts by id
   */
  List<Host> getHostsByHostIds(@NotEmpty String appId, @NotEmpty String envId, @NotNull List<String> hostUuids);

  long getHostsCountByInfraMappingIds(String appId, List<String> infraMappingIds);

  /**
   * Gets host by env.
   *
   * @param appId  the app id
   * @param envId  the env id
   * @param hostId the host id
   * @return the host by env
   */
  Host getHostByEnv(@NotEmpty String appId, @NotEmpty String envId, @NotEmpty String hostId);

  /**
   * Save application host application host.
   *
   * @param appHost the app host
   * @return the application host
   */
  Host saveHost(Host appHost);

  /**
   * Exist boolean.
   *
   * @param appId  the app id
   * @param hostId the host id
   * @return the boolean
   */
  boolean exist(String appId, String hostId);

  /**
   * Delete by environment.
   *
   * @param appId the app id
   * @param envId the env id
   */
  void deleteByEnvironment(String appId, String envId);

  /**
   * Delete by host name.
   *
   * @param appId          the app id
   * @param infraMappingId the infra mapping id
   * @param dnsName        the dns name
   */
  void deleteByDnsName(String appId, String infraMappingId, String dnsName);

  void updateHostConnectionAttrByInfraMapping(InfrastructureMapping infrastructureMapping, String hostConnectionAttrs);

  /**
   * Delete by service.
   *
   * @param appId             the app id
   * @param envId             the env id
   * @param serviceTemplateId the service template id
   */
  void deleteByService(String appId, String envId, String serviceTemplateId);
}
