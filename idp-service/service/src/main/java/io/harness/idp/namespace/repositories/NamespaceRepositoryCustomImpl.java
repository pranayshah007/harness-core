package io.harness.idp.namespace.repositories;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject}))
public class NamespaceRepositoryCustomImpl implements NamespaceRepositoryCustom{
    private MongoTemplate mongoTemplate;
}
