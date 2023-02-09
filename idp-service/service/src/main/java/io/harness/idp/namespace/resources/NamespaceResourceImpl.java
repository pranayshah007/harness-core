package io.harness.idp.namespace.resources;


import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.dto.NamespaceDTO;
import io.harness.idp.namespace.resource.NamespaceResource;
import io.harness.idp.namespace.service.NamespaceService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import java.util.Optional;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject}))
public class NamespaceResourceImpl implements NamespaceResource {

    @Inject private NamespaceService namespaceService;

    public String getNamespace(String accountIdentifier){
        Optional<NamespaceDTO> namespaceDTOOpt =
                namespaceService.findByAccountId(accountIdentifier);
        if (namespaceDTOOpt.isEmpty()) {
            throw new NotFoundException("Namespace for account id -  " + accountIdentifier + " not found");
        }
        return namespaceDTOOpt.get().getNamespace();
    }
}
