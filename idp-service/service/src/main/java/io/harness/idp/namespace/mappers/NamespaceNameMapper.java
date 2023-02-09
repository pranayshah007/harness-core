package io.harness.idp.namespace.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.dto.NamespaceDTO;
import io.harness.idp.namespace.beans.entity.NamespaceName;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class NamespaceNameMapper {

    public NamespaceDTO toDTO(NamespaceName namespaceName) {
        return NamespaceDTO.builder()
                .accountIdentifier(namespaceName.getAccountIdentifier())
                .namespace(namespaceName.getNamespaceName())
                .build();
    }

    public NamespaceName fromDTO(NamespaceDTO namespaceDTO) {
        return NamespaceName.builder()
                .accountIdentifier(namespaceDTO.getAccountIdentifier())
                .namespaceName(namespaceDTO.getNamespace())
                .build();
    }
}
