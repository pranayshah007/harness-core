/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.core.ci.services;

import io.harness.ci.beans.entities.BuildNumberDetails;
import io.harness.repositories.CIBuildNumberRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class BuildNumberServiceImpl implements BuildNumberService {
  private final CIBuildNumberRepository ciBuildNumberRepository;
  public BuildNumberDetails increaseBuildNumber(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return ciBuildNumberRepository.increaseBuildNumber(accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
