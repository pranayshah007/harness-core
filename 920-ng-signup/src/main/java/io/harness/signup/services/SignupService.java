/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.signup.services;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;
import io.harness.ng.core.user.UserInfo;
import io.harness.signup.dto.OAuthSignupDTO;
import io.harness.signup.dto.SignupDTO;
import io.harness.signup.dto.VerifyTokenResponseDTO;

import javax.annotation.Nullable;

@OwnedBy(GTM)
public interface SignupService {
  UserInfo signup(SignupDTO dto, String captchaToken, @Nullable String referer) throws WingsException;

  UserInfo communitySignup(SignupDTO dto) throws WingsException;

  UserInfo marketplaceSignup(SignupDTO dto, String inviteId, String marketPlaceToken) throws WingsException;

  boolean createSignupInvite(SignupDTO dto, String captchaToken);

  UserInfo completeSignupInvite(
      String token, @Nullable String referer, @Nullable String gaClientId, @Nullable String visitorToken);

  UserInfo oAuthSignup(OAuthSignupDTO dto) throws WingsException;

  VerifyTokenResponseDTO verifyToken(String token);

  void resendVerificationEmail(String email);

  void deleteByAccount(String accountId);
}
