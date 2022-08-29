package io.harness.ng.core.api;

import io.harness.beans.Scope;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.user.entities.UserGroup;

import java.util.List;
import java.util.Optional;

public interface DefaultUserGroupService {
    UserGroup create(Scope scope, List<String> userIds);
    void addUserToDefaultUserGroups(Scope scope, String userId);
    UserGroup update(UserGroup userGroup);
    Optional<UserGroup> get(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);
    String getUserGroupIdentifier(Scope scope);
    boolean isDefaultUserGroupService(Scope scope, String identifier);
}
