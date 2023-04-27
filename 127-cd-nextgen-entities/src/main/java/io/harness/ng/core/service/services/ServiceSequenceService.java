/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services;

import io.harness.ng.core.service.entity.ServiceSequence;

import java.util.Optional;

public interface ServiceSequenceService {
  Optional<ServiceSequence> get(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier);

  ServiceSequence upsertDefaultSequence(ServiceSequence requestServiceSequence);

  ServiceSequence upsertCustomSequence(ServiceSequence requestServiceSequence);

  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier);
}
