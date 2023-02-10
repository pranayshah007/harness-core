package io.harness.idp.namespace.repositories;


import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.entity.NamespaceName;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface NamespaceNameRepository extends CrudRepository<NamespaceName, String>, NamespaceNameRepositoryCustom {

    Optional<NamespaceName> findByAccountIdentifier(String accountIdentifier);
}
