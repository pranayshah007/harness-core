/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessModule._970_RBAC_CORE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.security.AccessRequest;
import software.wings.beans.security.AccessRequestDTO;

import java.util.List;

@OwnedBy(HarnessTeam.PL)
@TargetModule(_970_RBAC_CORE)
public interface AccessRequestService {
  AccessRequest createAccessRequest(AccessRequestDTO accessRequestDTO);

  AccessRequest get(String accessRequestId);

  List<AccessRequest> getActiveAccessRequest(String harnessUserGroupId);

  List<AccessRequest> getActiveAccessRequestForAccount(String accountId);

  List<String> getAccountsHavingActiveAccessRequestForUser(String userId);

  List<AccessRequest> getAllAccessRequestForAccount(String accountId);

  List<AccessRequest> getActiveAccessRequestForAccountAndUser(String accountId, String userId);

  AccessRequestDTO toAccessRequestDTO(AccessRequest accessRequest);

  List<AccessRequestDTO> toAccessRequestDTO(List<AccessRequest> accessRequestList);

  boolean delete(String accessRequestId);

  void checkAndUpdateAccessRequests(AccessRequest accessRequest);
}
