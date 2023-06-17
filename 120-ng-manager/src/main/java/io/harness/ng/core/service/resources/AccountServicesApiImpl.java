/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityYamlSchemaHelper;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.AccountServicesApi;
import io.harness.spec.server.ng.v1.model.ServiceRequest;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.Response;

@OwnedBy(CDC)
@NextGenManagerAuth
public class AccountServicesApiImpl extends AbstractServicesApiImpl implements AccountServicesApi {
  @Inject
  AccountServicesApiImpl(ServiceEntityService serviceEntityService, AccessControlClient accessControlClient,
      ServiceEntityManagementService serviceEntityManagementService,
      OrgAndProjectValidationHelper orgAndProjectValidationHelper, ServiceResourceApiUtils serviceResourceApiUtils,
      ServiceEntityYamlSchemaHelper serviceSchemaHelper) {
    super(serviceEntityService, accessControlClient, serviceEntityManagementService, orgAndProjectValidationHelper,
        serviceResourceApiUtils, serviceSchemaHelper);
  }

  @Override
  public Response createAccountScopedService(@Valid ServiceRequest serviceRequest, String account) {
    return super.createServiceEntity(serviceRequest, null, null, account);
  }

  @NGAccessControlCheck(resourceType = NGResourceType.SERVICE, permission = "core_service_delete")
  @Override
  public Response deleteAccountScopedService(
      @ResourceIdentifier String service, @AccountIdentifier String account, Boolean forceDelete) {
    return super.deleteServiceEntity(null, null, service, account, Boolean.TRUE == forceDelete);
  }

  @NGAccessControlCheck(resourceType = NGResourceType.SERVICE, permission = "core_service_view")
  @Override
  public Response getAccountScopedService(@ResourceIdentifier String service, @AccountIdentifier String account) {
    return super.getServiceEntity(null, null, service, account);
  }

  @Override
  public Response getAccountScopedServices(Integer page, Integer limit, String searchTerm, List<String> services,
      String sort, Boolean isAccessList, String type, Boolean gitOpsEnabled, String account, String order) {
    return super.getServicesList(
        null, null, page, limit, searchTerm, services, sort, isAccessList, type, gitOpsEnabled, account, order);
  }

  @Override
  public Response updateAccountScopedService(@Valid ServiceRequest serviceRequest, String service, String account) {
    return super.updateServiceEntity(serviceRequest, null, null, service, account);
  }
}