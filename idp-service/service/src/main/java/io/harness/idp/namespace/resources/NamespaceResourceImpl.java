package io.harness.idp.namespace.resources;


import io.harness.idp.namespace.beans.dto.NamespaceDTO;
import io.harness.idp.namespace.beans.entity.NamespaceName;
import io.harness.idp.namespace.resource.NamespaceResource;
import io.harness.idp.namespace.service.NamespaceNameService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import java.util.Optional;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject}))
public class NamespaceResourceImpl implements NamespaceResource {

    private NamespaceNameService namespaceService;

    public String getNamespace(String accountIdentifier){
        Optional<NamespaceDTO> namespaceDTOOpt =
                namespaceService.findByAccountIdentifier(accountIdentifier);
        if (namespaceDTOOpt.isEmpty()) {
            throw new NotFoundException("Namespace for account id -  " + accountIdentifier + " not found");
        }
        return namespaceDTOOpt.get().getNamespaceName();
    }

    public String pushNamespace(String accountIdentifier, String namespaceName){
        NamespaceName namespaceName1 = namespaceService.pushAccountIDNamespace(accountIdentifier, namespaceName);
        return namespaceName1.getNamespaceName()+namespaceName1.getAccountIdentifier();
    }
}
