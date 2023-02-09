package io.harness.idp.namespace.service;

import io.harness.idp.namespace.beans.dto.NamespaceDTO;

import java.util.Optional;

public interface NamespaceService {
    Optional<NamespaceDTO> findByAccountId( String accountIdentifier);
}
