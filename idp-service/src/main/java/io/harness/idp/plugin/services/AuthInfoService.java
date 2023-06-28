/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.services;

import io.harness.spec.server.idp.v1.model.AuthInfo;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import java.util.List;

public interface AuthInfoService {
  AuthInfo getAuthInfo(String authId, String harnessAccount);
  List<BackstageEnvVariable> saveAuthEnvVariables(
      String authId, List<BackstageEnvVariable> envVariables, String harnessAccount) throws Exception;
}
