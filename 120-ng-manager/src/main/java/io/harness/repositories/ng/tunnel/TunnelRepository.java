/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.ng.tunnel;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.tunnel.entities.Tunnel;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@HarnessRepo
@Transactional
@OwnedBy(CI)
public interface TunnelRepository extends CrudRepository<Tunnel, String> {
  Optional<Tunnel> findByAccountIdentifier(String accountIdentifier);
}
