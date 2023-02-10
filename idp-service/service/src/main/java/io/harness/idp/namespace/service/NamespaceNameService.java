package io.harness.idp.namespace.service;

import io.harness.idp.namespace.beans.dto.NamespaceDTO;
import io.harness.idp.namespace.beans.entity.NamespaceName;

import java.util.Optional;

public interface NamespaceNameService {
    Optional<NamespaceDTO> findByAccountIdentifier(String accountIdentifier);
    NamespaceName pushAccountIDNamespace(String accountIdentifier, String namespaceName);
}
