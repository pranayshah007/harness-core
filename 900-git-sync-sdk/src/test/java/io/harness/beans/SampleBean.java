/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.TEST)
@Entity(value = "sampleBean", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("sampleBean")
@FieldNameConstants(innerTypeName = "SampleBeanKeys")
@OwnedBy(HarnessTeam.DX)
public class SampleBean implements PersistentEntity, YamlDTO, GitSyncableEntity {
  @Id String uuid;
  String test1;
  String name;
  String accountIdentifier;
  String projectIdentifier;
  String orgIdentifier;
  String identifier;
  String objectIdOfYaml;
  Boolean isFromDefaultBranch;
  String branch;
  String yamlGitConfigRef;
  String filePath;
  String rootFolder;
  Boolean isEntityInvalid;
  String yaml; // use to store yaml of invalid entity

  @Override
  public boolean isEntityInvalid() {
    return Boolean.TRUE.equals(isEntityInvalid);
  }

  @Override
  public void setEntityInvalid(boolean isEntityInvalid) {
    this.isEntityInvalid = isEntityInvalid;
  }

  @Override
  public String getInvalidYamlString() {
    return yaml;
  }
}
