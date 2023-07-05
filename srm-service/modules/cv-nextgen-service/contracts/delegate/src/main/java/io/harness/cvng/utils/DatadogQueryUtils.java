/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
@Slf4j
public class DatadogQueryUtils {
  private static final String DELIMITER_SEMICOLON = ";";
  private static final String DATADOG_CLAUSE_BY = "by";
  private static final String DATADOG_ROLLUP_CLAUSE = "rollup\\(";
  private static final String REGEX_NOT_CHARACTER = "[^A-Za-z0-9]";
  private static final String WORD_BOUNDARY = "\\b";
  private static final String DATADOG_ROLLUP_AVG_60_SEC = ".rollup(avg, 60)";

  public static Pair<String, List<String>> processCompositeQuery(
      String rawQuery, String SII, boolean isCollectHostData) {
    if (!isValidQuery(rawQuery)) {
      throw new RuntimeException("Invalid Query : " + rawQuery);
    }
    boolean isCompositeQuery = StringUtils.countMatches(rawQuery, DELIMITER_SEMICOLON) > 0;
    String formula = getFormula(rawQuery, isCompositeQuery);
    List<String> queryParts = getQueryParts(rawQuery, isCompositeQuery);
    formula = formulaReplaceQueryAliases(formula);
    if (isCollectHostData) {
      appendMissingGroupingClause(SII, queryParts);
    }
    appendMissingRollUpClause(queryParts);
    return Pair.of(formula, queryParts);
  }

  private static String getFormula(String rawQuery, boolean isCompositeQuery) {
    String formula = "query1";
    List<String> queryParts = Arrays.asList(rawQuery.split(DELIMITER_SEMICOLON));
    int lastElementIdx = queryParts.size() - 1;
    if (isCompositeQuery) {
      formula = queryParts.get(lastElementIdx).trim();
    }
    return formula;
  }

  private static List<String> getQueryParts(String rawQuery, boolean isCompositeQuery) {
    List<String> queryParts = new ArrayList<>(List.of(rawQuery));
    if (isCompositeQuery) {
      queryParts = Arrays.asList(rawQuery.split(DELIMITER_SEMICOLON));
      int lastElementIdx = queryParts.size() - 1;
      queryParts = queryParts.subList(0, lastElementIdx);
    }
    queryParts = queryParts.stream().map(String::trim).collect(Collectors.toList());
    return queryParts;
  }

  private static String formulaReplaceQueryAliases(String formula) {
    int queryNum = 1;
    for (char chr = 'a'; chr <= 'z'; chr++) {
      formula = formula.replaceAll(WORD_BOUNDARY + chr + WORD_BOUNDARY, "query" + queryNum);
      queryNum++;
    }
    return formula;
  }

  private static void appendMissingGroupingClause(String SII, List<String> queryParts) {
    for (int i = 0; i < queryParts.size(); i++) {
      String pattern = REGEX_NOT_CHARACTER + DATADOG_CLAUSE_BY + REGEX_NOT_CHARACTER;
      Pattern metricPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
      String query = queryParts.get(i);
      Matcher metricPatternMatch = metricPattern.matcher(query);
      if (!metricPatternMatch.find()) { // By clause is not there
        StringBuilder queryBld = new StringBuilder(query);
        int lastIndexOf = query.lastIndexOf('}');
        queryBld.insert(lastIndexOf + 1, DATADOG_CLAUSE_BY + "{" + SII + "}");
        queryParts.set(i, queryBld.toString());
      }
    }
  }

  private static void appendMissingRollUpClause(List<String> queryParts) {
    for (int i = 0; i < queryParts.size(); i++) {
      String pattern = DATADOG_ROLLUP_CLAUSE;
      Pattern metricPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
      String query = queryParts.get(i);
      Matcher metricPatternMatch = metricPattern.matcher(query);
      if (!metricPatternMatch.find()) { // roll up clause is not there
        StringBuilder queryBld = new StringBuilder(query);
        int lastIndexOf = query.lastIndexOf('}');
        queryBld.insert(lastIndexOf + 1, DATADOG_ROLLUP_AVG_60_SEC);
        queryParts.set(i, queryBld.toString());
      }
    }
  }

  public static boolean isValidQuery(String rawQuery) {
    boolean isCompositeQuery = StringUtils.countMatches(rawQuery, DELIMITER_SEMICOLON) > 0;
    String formula = getFormula(rawQuery, isCompositeQuery);
    if (formula.contains("{") || formula.contains("}")) {
      return false;
    }
    List<String> queryParts = getQueryParts(rawQuery, isCompositeQuery);
    List<Boolean> querysValid = queryParts.stream()
                                    .map(singleQuery -> singleQuery.contains("}") && singleQuery.contains("{"))
                                    .collect(Collectors.toList());
    return !querysValid.contains(false);
  }
}
