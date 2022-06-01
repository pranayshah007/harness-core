/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.ci.beans.entities.BuildNumberDetails;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class CIBuildNumberRepositoryCustomImpl implements CIBuildNumberRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public BuildNumberDetails increaseBuildNumber(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return null;
  }
}
