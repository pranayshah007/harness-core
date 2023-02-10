package io.harness.idp.namespace.repositories;

import com.google.inject.Inject;
import io.harness.idp.namespace.beans.entity.NamespaceName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject}))
public class NamespaceNameRepositoryCustomImpl implements NamespaceNameRepositoryCustom {
    private MongoTemplate mongoTemplate;

    NamespaceName save(NamespaceName namespaceName){
        NamespaceName namespaceName1 = mongoTemplate.insert(namespaceName);
        return namespaceName1;
    }
}
