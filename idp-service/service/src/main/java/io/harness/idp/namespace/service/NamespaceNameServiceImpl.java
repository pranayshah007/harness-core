package io.harness.idp.namespace.service;

import io.harness.idp.namespace.beans.dto.NamespaceDTO;
import io.harness.idp.namespace.beans.entity.NamespaceName;
import io.harness.idp.namespace.mappers.NamespaceNameMapper;
import io.harness.idp.namespace.repositories.NamespaceNameRepository;

import javax.inject.Inject;
import java.util.Optional;

public class NamespaceNameServiceImpl implements NamespaceNameService {

    @Inject private NamespaceNameRepository namespaceRepository;

    @Override
    public Optional<NamespaceDTO> findByAccountIdentifier(String accountId) {
        Optional<NamespaceName> namespaceName =
                namespaceRepository.findByAccountIdentifier(accountId);
        return namespaceName.map(NamespaceNameMapper::toDTO);
    }

    @Override
    public NamespaceName pushAccountIDNamespace(String accountId, String namespaceName){
         NamespaceName pushData = NamespaceName.builder()
                .accountIdentifier(accountId)
                .namespaceName(namespaceName)
                .build();
        NamespaceName namespaceName1 = namespaceRepository.save(pushData);
        return namespaceName1;
    }
}
