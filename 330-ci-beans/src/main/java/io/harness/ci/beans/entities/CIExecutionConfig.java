/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.beans.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.RecasterAlias;
import io.harness.annotation.StoreIn;
import io.harness.ci.config.VmImageConfig;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotBlank;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "CIExecutionConfigKeys")
@Entity(value = "ciexecutionconfig", noClassnameStored = true)
@StoreIn("harnessci")
@Document("ciexecutionconfig")
@HarnessEntity(exportable = true)
@TypeAlias("ciExecutionConfig")
@RecasterAlias("io.harness.ci.beans.entities.CIExecutionConfig")
public class CIExecutionConfig implements PersistentEntity, UuidAware, CreatedAtAware {
  @Id @org.mongodb.morphia.annotations.Id String uuid;
  @NotBlank String accountIdentifier;
  @NotBlank String addOnImage;
  @NotBlank String liteEngineImage;
  @NotBlank String gitCloneImage;
  @NotBlank String securityImage;
  @NotBlank String buildAndPushDockerRegistryImage;
  @NotBlank String buildAndPushECRImage;
  @NotBlank String buildAndPushGCRImage;
  @NotBlank String gcsUploadImage;
  @NotBlank String s3UploadImage;
  @NotBlank String artifactoryUploadTag;
  @NotBlank String cacheGCSTag;
  @NotBlank String cacheS3Tag;
  VmImageConfig vmImageConfig;
  @SchemaIgnore private long createdAt;
}
