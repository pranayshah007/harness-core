/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.userprofile.services.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.user.PasswordChangeDTO;
import io.harness.ng.core.user.PasswordChangeResponse;
import io.harness.ng.core.user.TwoFactorAuthMechanismInfo;
import io.harness.ng.core.user.TwoFactorAuthSettingsInfo;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserInfoUpdateDTO;

@OwnedBy(HarnessTeam.PL)
public interface UserInfoService {
  UserInfo getCurrentUser();
  UserInfo update(UserInfo userInfo, String accountIdentifier);
  UserInfo update(UserInfoUpdateDTO userInfoUpdateDTO, String userId, String accountIdentifier);
  TwoFactorAuthSettingsInfo getTwoFactorAuthSettingsInfo(TwoFactorAuthMechanismInfo twoFactorAuthMechanismInfo);
  UserInfo updateTwoFactorAuthInfo(TwoFactorAuthSettingsInfo authSettingsInfo);
  UserInfo disableTFA();
  Boolean sendTwoFactorAuthenticationResetEmail(String userId, String accountId);
  PasswordChangeResponse changeUserPassword(PasswordChangeDTO passwordChangeDTO);
  UserInfo unlockUser(String userId, String accountId);
}
