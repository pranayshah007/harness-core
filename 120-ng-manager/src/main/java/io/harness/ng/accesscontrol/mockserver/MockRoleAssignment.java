/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.accesscontrol.mockserver;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.principals.PrincipalDTO.PrincipalDTOKeys;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO.RoleAssignmentDTOKey;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder(toBuilder = true)
@FieldNameConstants(innerTypeName = "MockRoleAssignmentKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "mockRoleAssignments", noClassnameStored = true)
@Document("mockRoleAssignments")
@TypeAlias("mockRoleAssignments")
@OwnedBy(PL)
public class MockRoleAssignment implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueMockRoleAssignment")
                 .field(MockRoleAssignmentKeys.accountIdentifier)
                 .field(MockRoleAssignmentKeys.orgIdentifier)
                 .field(MockRoleAssignmentKeys.projectIdentifier)
                 .field(MockRoleAssignmentKeys.roleAssignment + "." + RoleAssignmentDTOKey.roleIdentifier)
                 .field(MockRoleAssignmentKeys.roleAssignment + "." + RoleAssignmentDTOKey.resourceGroupIdentifier)
                 .field(MockRoleAssignmentKeys.roleAssignment + "." + RoleAssignmentDTOKey.resourceGroupIdentifier + "."
                     + PrincipalDTOKeys.identifier)
                 .field(MockRoleAssignmentKeys.roleAssignment + "." + RoleAssignmentDTOKey.resourceGroupIdentifier + "."
                     + PrincipalDTOKeys.type)
                 .build())
        .build();
  }
  @Id @dev.morphia.annotations.Id @EntityIdentifier String id;
  @NotEmpty String accountIdentifier;
  @NotEmpty String orgIdentifier;
  @NotEmpty String projectIdentifier;
  @NotNull RoleAssignmentDTO roleAssignment;
}
