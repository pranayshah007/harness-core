/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.entities;

import static io.harness.annotations.dev.HarnessTeam.SSCA;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@OwnedBy(SSCA)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "EnforcementResultEntityKeys")
@StoreIn(DbAliases.SSCA)
@Entity(value = "enforcementResult", noClassnameStored = true)
@Document("enforcementResult")
@TypeAlias("enforcementResult")
@HarnessEntity(exportable = true)
public class EnforcementResultEntity implements PersistentEntity {
  @Field("enforcement_id") String enforcementID;
  @Field("artifactid") String artifactId;
  String tag;
  @Field("imagename") String imageName;
  @Field("accountid") String accountId;
  @Field("orgidentifier") String orgIdentifier;
  @Field("projectidentifier") String projectIdentifier;
  @Field("orchestrationid") String orchestrationID;
  @Field("violationtype") String violationType;
  @Field("violationdetails") String violationDetails;
  String name;
  String version;
  String supplier;
  @Field("suppliertype") String supplierType;
  @Field("packagemanager") String packageManager;
  List<String> license;
  String purl;
}
