/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.beans.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import io.leangen.graphql.annotations.GraphQLNonNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

/**
 * javax.persistence.Column annotation is jooq aware and can be used while parsing the DB response.
 * https://www.jooq.org/doc/latest/manual/sql-execution/fetching/pojos/#N8DEF1
 */
@Value
@Builder
@EqualsAndHashCode
@OwnedBy(CE)
@FieldNameConstants(innerTypeName = "NodePoolIdKeys")
public class NodePoolId {
  // nodepoolname can be null for some nodes
  @GraphQLNonNull String nodepoolname;
  @GraphQLNonNull @NonNull String clusterid;
}
