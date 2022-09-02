/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.commandlibrary;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.Variable;
import software.wings.beans.template.BaseTemplate;

import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "CommandVersionsKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "clCommandVersions", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class CommandVersionEntity implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware {
  @Id @NotNull(groups = {Update.class}) String uuid;
  String commandName;
  String commandStoreName;
  String commandId;
  String version;
  String description;
  String yamlContent;
  String repoUrl;
  Set<String> tags;
  BaseTemplate templateObject;
  List<Variable> variables;
  long createdAt;
  String lastUpdatedByAccountId;
  long lastUpdatedAt;
  String createdByAccountId;
}
