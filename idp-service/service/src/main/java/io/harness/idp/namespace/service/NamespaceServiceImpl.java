package io.harness.idp.namespace.service;

import io.harness.idp.namespace.beans.dto.NamespaceDTO;
import io.harness.idp.namespace.beans.entity.NamespaceName;
import io.harness.idp.namespace.mappers.NamespaceNameMapper;
import io.harness.idp.namespace.repositories.NamespaceRepository;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.Optional;

public class NamespaceServiceImpl implements NamespaceService{

    @Inject private NamespaceRepository namespaceRepository;

    @Override
    public Optional<NamespaceDTO> findByAccountId(String accountIdentifier) {
        Optional<NamespaceName> namespaceName =
                namespaceRepository.findByAccountIdentifier(accountIdentifier);
        return namespaceName.map(NamespaceNameMapper::toDTO);
    }
}
