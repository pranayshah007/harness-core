package io.harness.idp.secret.repositories;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class EnvironmentVariableRepositoryCustomImpl implements EnvironmentVariableRepositoryCustom {
    private MongoTemplate mongoTemplate;
}
