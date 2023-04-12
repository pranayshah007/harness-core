/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.gitness;

import io.harness.connector.entities.embedded.gitness.Gitness;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.scm.gitness.GitnessDTO;

import com.google.inject.Singleton;

@Singleton
public class GitnessDTOToEntity implements ConnectorDTOToEntityMapper<GitnessDTO, Gitness> {
  @Override
  public Gitness toConnectorEntity(GitnessDTO connectorDTO) {
    return Gitness.builder().url(connectorDTO.getUrl()).build();
  }
}
