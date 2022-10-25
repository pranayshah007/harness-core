/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.tracing.shapedetector;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Value;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class QueryShapeDetectorTest extends CategoryTest {
  private static final List<List<CalculateHashParams>> sameShapeQueriesList = Arrays.asList(
      // Basic types
      Arrays.asList(createCalculateHashParams(query(where("_str").is("abc"))),
          createCalculateHashParams(query(where("_str").is("def")))),
      Arrays.asList(createCalculateHashParams(query(where("_int").is(1))),
          createCalculateHashParams(query(where("_int").is(10)))),
      Arrays.asList(createCalculateHashParams(query(where("_long").is(1L))),
          createCalculateHashParams(query(where("_long").is(10L)))),
      Arrays.asList(createCalculateHashParams(query(where("_double").is(56.98))),
          createCalculateHashParams(query(where("_double").is(25.0)))),
      Arrays.asList(createCalculateHashParams(query(where("_decimal").is(new BigDecimal("123.456")))),
          createCalculateHashParams(query(where("_decimal").is(new BigDecimal("456.789"))))),
      Arrays.asList(createCalculateHashParams(query(where("_bool").is(true))),
          createCalculateHashParams(query(where("_bool").is(false)))),
      Arrays.asList(createCalculateHashParams(query(where("_oid").is(new ObjectId(new Date())))),
          createCalculateHashParams(
              query(where("_oid").is(new ObjectId(new Date(System.currentTimeMillis() + 5000)))))),
      Arrays.asList(createCalculateHashParams(query(where("_array").is(Arrays.asList(1, 2)))),
          createCalculateHashParams(query(where("_array").is(Arrays.asList(3, 4, 5))))),
      Arrays.asList(createCalculateHashParams(query(where("_binary").is("abc".getBytes()))),
          createCalculateHashParams(query(where("_binary").is("def".getBytes())))),
      Arrays.asList(createCalculateHashParams(query(where("_date").is(new Date()))),
          createCalculateHashParams(query(where("_date").is(new Date(System.currentTimeMillis() + 5000))))),
      Arrays.asList(createCalculateHashParams(query(where("_ts").is(new BsonTimestamp(50)))),
          createCalculateHashParams(query(where("_ts").is(new BsonTimestamp(1000))))),
      Arrays.asList(createCalculateHashParams(query(where("_regex").is(Pattern.compile("abc")))),
          createCalculateHashParams(query(where("_regex").is(Pattern.compile("def"))))),
      Arrays.asList(createCalculateHashParams(query(where("_obj").is(ImmutablePair.of(1, "abc")))),
          createCalculateHashParams(query(where("_obj").is(ImmutablePair.of("def", 5))))),

      // Order of params
      Arrays.asList(createCalculateHashParams(query(where("_a").is("a").and("_b").is("b"))),
          createCalculateHashParams(query(where("_b").is("a").and("_a").is("b")))),
      Arrays.asList(createCalculateHashParams(query(where("_a").is("a").and("_b").in(Arrays.asList(1, 2, 3)))),
          createCalculateHashParams(query(where("_b").in(Arrays.asList(4, 5)).and("_a").is("b")))),

      // in operator
      Arrays.asList(createCalculateHashParams(
                        query(where("planExecutionId").is("p1").and("status").in(Arrays.asList("RUNNING", "WAITING")))),
          createCalculateHashParams(
              query(where("status").in(Arrays.asList("WAITING", "RUNNING")).and("planExecutionId").is("p3")))),

      // Operators
      Arrays.asList(createCalculateHashParams(query(where("_a").is("a").and("_b._c").maxDistance(10))),
          createCalculateHashParams(query(where("_b._c").maxDistance(15).and("_a").is("b")))),
      Arrays.asList(createCalculateHashParams(query(where("_a").is("a").and("_b").lte(10))),
          createCalculateHashParams(query(where("_b").lte(15).and("_a").is("b")))),
      Arrays.asList(createCalculateHashParams(query(where("_a").is("a").and("_b").exists(true))),
          createCalculateHashParams(query(where("_b").exists(false).and("_a").is("b")))),
      Arrays.asList(createCalculateHashParams(
                        query(where("_a").is("a").and("_b").elemMatch(where("_c").is("c").and("_d").is("d")))),
          createCalculateHashParams(
              query(where("_b").elemMatch(where("_d").is("c").and("_c").is("d")).and("_a").is("b")))),
      Arrays.asList(createCalculateHashParams(query(where("_a").is("a").and("_b").size(5))),
          createCalculateHashParams(query(where("_b").size(8).and("_a").is("b")))),
      Arrays.asList(createCalculateHashParams(query(where("_a").is("a").and("_b").near(new Point(1.0, 2.0)))),
          createCalculateHashParams(query(where("_b").near(new Point(2.0, 3.0)).and("_a").is("b")))),

      // Logical operators
      Arrays.asList(createCalculateHashParams(query(
                        where("_a").is("a").and("_b").exists(true).orOperator(where("_c").is("c").and("_d").is("d")))),
          createCalculateHashParams(
              query(where("_b").exists(false).orOperator(where("_d").is("c").and("_c").is("d")).and("_a").is("b")))),
      Arrays.asList(createCalculateHashParams(query(
                        where("_a").is("a").and("_b").exists(true).norOperator(where("_c").is("c").and("_d").is("d")))),
          createCalculateHashParams(
              query(where("_b").exists(false).norOperator(where("_d").is("c").and("_c").is("d")).and("_a").is("b")))));

  private static final List<Pair<CalculateHashParams, CalculateHashParams>> diffShapeQueriesList = Arrays.asList(
      // Basic types
      ImmutablePair.of(createCalculateHashParams(query(where("_str").is("abc"))),
          createCalculateHashParams(query(where("_def").is("def")))),
      ImmutablePair.of(createCalculateHashParams(query(where("planExecutionId").is("p1"))),
          createCalculateHashParams(query(where("status").is("s1")))),
      ImmutablePair.of(createCalculateHashParams(query(where("_str").is("abc"))),
          createCalculateHashParams(query(where("_str").is(null)))),
      ImmutablePair.of(createCalculateHashParams(query(where("_a").is(1))),
          createCalculateHashParams(query(where("_a").is(10).and("_b").is(5)))));

  private static final List<Pair<CalculateHashParams, CalculateHashParams>> diffShapeSortQueriesList = Arrays.asList(
      // Basic types
      ImmutablePair.of(createCalculateHashParams(query(where("a").is(1)).with(Sort.by(Sort.Direction.ASC, "b", "c"))),
          createCalculateHashParams(query(where("a").is(1)).with(Sort.by(Sort.Direction.ASC, "c", "b")))));

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNormalizeMapSameShape() {
    for (List<CalculateHashParams> sameShapeQueries : sameShapeQueriesList) {
      String normalized =
          JsonUtils.asJson(QueryShapeDetector.normalizeObject(sameShapeQueries.get(0).getQueryDoc(), false));
      for (int i = 1; i < sameShapeQueries.size(); i++) {
        CalculateHashParams params = sameShapeQueries.get(i);
        assertThat(JsonUtils.asJson(QueryShapeDetector.normalizeObject(params.getQueryDoc(), false)))
            .isEqualTo(normalized);
      }
    }
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(io.harness.category.element.UnitTests.class)
  public void testQueryHashForSameQueryShape() {
    for (List<CalculateHashParams> sameShapeQueries : sameShapeQueriesList) {
      String firstElementHash = QueryShapeDetector.getQueryHash(sameShapeQueries.get(0).collectionName,
          sameShapeQueries.get(0).getQueryDoc(), sameShapeQueries.get(0).getSortDoc());
      for (CalculateHashParams sameShapeQuery : sameShapeQueries) {
        String queryHash = QueryShapeDetector.getQueryHash(
            sameShapeQuery.collectionName, sameShapeQuery.getQueryDoc(), sameShapeQuery.getSortDoc());
        assertThat(queryHash).isEqualTo(firstElementHash);
      }
    }
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(io.harness.category.element.UnitTests.class)
  public void testQueryHashForDifferentQueryShape() {
    for (Pair<CalculateHashParams, CalculateHashParams> diffShapeQueries : diffShapeQueriesList) {
      QueryHashKey leftQueryHash = QueryShapeDetector.calculateQueryHashKey(diffShapeQueries.getLeft().collectionName,
          diffShapeQueries.getLeft().getQueryDoc(), diffShapeQueries.getLeft().getSortDoc());
      QueryHashKey rightQueryHash = QueryShapeDetector.calculateQueryHashKey(diffShapeQueries.getRight().collectionName,
          diffShapeQueries.getRight().getQueryDoc(), diffShapeQueries.getRight().getSortDoc());
      assertThat(leftQueryHash.getQueryHash()).isNotEqualTo(rightQueryHash.getQueryHash());
      assertThat(leftQueryHash.getSortHash()).isEqualTo(rightQueryHash.getSortHash());
      assertThat(leftQueryHash.hashCode()).isNotEqualTo(rightQueryHash.hashCode());
    }
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(io.harness.category.element.UnitTests.class)
  public void testQueryHashForDifferentSortQueryShape() {
    for (Pair<CalculateHashParams, CalculateHashParams> diffShapeQueries : diffShapeSortQueriesList) {
      QueryHashKey leftQueryHash = QueryShapeDetector.calculateQueryHashKey(diffShapeQueries.getLeft().collectionName,
          diffShapeQueries.getLeft().getQueryDoc(), diffShapeQueries.getLeft().getSortDoc());
      QueryHashKey rightQueryHash = QueryShapeDetector.calculateQueryHashKey(diffShapeQueries.getRight().collectionName,
          diffShapeQueries.getRight().getQueryDoc(), diffShapeQueries.getRight().getSortDoc());
      assertThat(leftQueryHash.getSortHash()).isNotEqualTo(rightQueryHash.getSortHash());
      assertThat(leftQueryHash.getQueryHash()).isEqualTo(rightQueryHash.getQueryHash());
      assertThat(leftQueryHash.hashCode()).isNotEqualTo(rightQueryHash.hashCode());
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(io.harness.category.element.UnitTests.class)
  public void testNormalizeMapDiffShape() {
    for (Pair<CalculateHashParams, CalculateHashParams> diffShapeQueries : diffShapeQueriesList) {
      String normalized =
          JsonUtils.asJson(QueryShapeDetector.normalizeObject(diffShapeQueries.getLeft().getQueryDoc(), false));
      assertThat(JsonUtils.asJson(QueryShapeDetector.normalizeObject(diffShapeQueries.getRight().getQueryDoc(), false)))
          .isNotEqualTo(normalized);
    }
  }

  @Value
  private static class CalculateHashParams {
    String collectionName;
    Document queryDoc;
    Document sortDoc;
  }

  private static CalculateHashParams createCalculateHashParams(Query query) {
    new CalculateHashParams("randomColl", query.getQueryObject(), query.getSortObject());
    return new CalculateHashParams("randomColl", query.getQueryObject(), query.getSortObject());
  }
}
