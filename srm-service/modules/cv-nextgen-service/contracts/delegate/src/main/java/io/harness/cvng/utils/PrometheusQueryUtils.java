/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@UtilityClass
@Slf4j
public class PrometheusQueryUtils {
  private static final String REGEX_PARENTHESIS_OPEN = "\\(";
  private static final String REGEX_CLOSING_PARENTHESIS = "\\)";

  private static final List<String> aggregationOperatorList = List.of(
      "sum", "min", "max", "avg", "group", "stddev", "stdvar", "count", "count_values", "bottomk", "topk", "quantile");
  private static final String REGEX_OR = "|";
  public static final String PROMQL_CLAUSE_BY = "by";
  public static String formGroupByQuery(String rawQuery, String SII) {
    if (StringUtils.isEmpty(rawQuery)) {
      return rawQuery;
    }
    if (!areBracketsBalanced(rawQuery)) {
      throw new RuntimeException("Bad Query");
    }
    rawQuery = cleanUpWhitespacesOnAggregationOperators(rawQuery);
    StringBuilder query = new StringBuilder(rawQuery);
    log.info("Original PromQL Query: " + query);
    Map<Integer, Integer> bracketPairs = mapBracketPairIndexes(query.toString());
    // Find any brackets preceded by a operator without encountering another
    // bracket, if we find it then we get the closing operator
    StringBuilder pattern = createAggregationOperatorPattern();
    Pattern aggregationOperatorPattern = Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE);
    Matcher matchAggOperator = aggregationOperatorPattern.matcher(query.toString());
    int scannedTillPoint = -1;
    while (matchAggOperator.find()) {
      String operator = query.substring(matchAggOperator.start(), matchAggOperator.end() - 1);
      if (matchAggOperator.start() > scannedTillPoint) {
        int additionalMove = 0;
        log.debug("match : " + matchAggOperator.end() + " scan cursor : " + scannedTillPoint);
        int startBracketIdx = matchAggOperator.end() - 1;
        int endBracketIdx = bracketPairs.get(startBracketIdx);
        if (startBracketIdx - 2 >= 0
            && PROMQL_CLAUSE_BY.equals(query.substring(startBracketIdx - 2, startBracketIdx))) { // Operate at LHS
          appendInsideAByClause(SII, query, startBracketIdx);
          bracketPairs = mapBracketPairIndexes(query.toString());
          int nextBrk = bracketPairs.get(startBracketIdx) + 1;
          int endLHSBrk = bracketPairs.get(nextBrk);
          additionalMove = wrapWithSameOperator(query, matchAggOperator.start() - 1, SII, operator, endLHSBrk);
        } else { // Operate at RHS
          if (endBracketIdx + 4 <= query.length()
              && "by(".equals(query.substring(endBracketIdx + 1, endBracketIdx + 4))) {
            int startBracketOfByRHSClause = endBracketIdx + 3;
            appendInsideAByClause(SII, query, startBracketOfByRHSClause);
            bracketPairs = mapBracketPairIndexes(query.toString());
            int endBracketOfByRHSClause = bracketPairs.get(startBracketOfByRHSClause);
            additionalMove =
                wrapWithSameOperator(query, matchAggOperator.start() - 1, SII, operator, endBracketOfByRHSClause);
          } else {
            appendAtEndWithFullClause(SII, query, endBracketIdx);
          }
        }
        bracketPairs = mapBracketPairIndexes(query.toString());
        scannedTillPoint = matchAggOperator.end() - 1 + additionalMove;
        matchAggOperator = aggregationOperatorPattern.matcher(query.toString());
        log.debug(query.toString());
      }
    }
    log.info("New PromQL Query: " + query);
    return query.toString();
  }

  @NotNull
  private static String cleanUpWhitespacesOnAggregationOperators(String rawQuery) {
    rawQuery = rawQuery.trim().replaceAll("\\s+", " ");
    rawQuery = rawQuery.replaceAll(REGEX_CLOSING_PARENTHESIS + "\\s+" + REGEX_PARENTHESIS_OPEN, ")(");
    rawQuery = rawQuery.replaceAll(PROMQL_CLAUSE_BY + "\\s+" + REGEX_PARENTHESIS_OPEN, "by(");
    rawQuery = rawQuery.replaceAll(REGEX_CLOSING_PARENTHESIS + "\\s+" + PROMQL_CLAUSE_BY, ")by");
    for (String operator : aggregationOperatorList) {
      rawQuery = rawQuery.replaceAll(operator + "\\s+" + REGEX_PARENTHESIS_OPEN, operator + "(");
      rawQuery = rawQuery.replaceAll(operator + "\\s+" + PROMQL_CLAUSE_BY, operator + " by");
    }
    return rawQuery;
  }

  private static StringBuilder createAggregationOperatorPattern() {
    StringBuilder pattern = new StringBuilder();
    pattern.append("\\b(?:");
    for (String operator : aggregationOperatorList) {
      pattern.append(operator + REGEX_PARENTHESIS_OPEN + REGEX_OR);
      pattern.append(operator + " " + PROMQL_CLAUSE_BY + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    }
    pattern.deleteCharAt(pattern.length() - 1);
    pattern.append(")\\b");
    return pattern;
  }

  private static int wrapWithSameOperator(
      StringBuilder query, int startBracketIdx, String SII, String operator, int endBracketIdx) {
    String byClause = " by (" + SII + ")";
    String suffix = ")" + byClause; // edge case we have reached end
    query.insert(Math.min(endBracketIdx + 1, query.length()), suffix);
    String prefix =
        operator.replaceAll(PROMQL_CLAUSE_BY, "") + '('; // we always use one style sum() by() and not sum by()()
    query.insert(startBracketIdx + 1, prefix);
    return prefix.length();
  }

  private static void appendInsideAByClause(String SII, StringBuilder query, int startByBracket) {
    // TODO check if already present in this list, minor improvement
    query.insert(Math.max(0, startByBracket) + 1, SII + ",");
  }

  private static void appendAtEndWithFullClause(String SII, StringBuilder query, int closingBracketIndx) {
    query.insert(Math.min(query.length() - 1, closingBracketIndx) + 1, " by (" + SII + ")");
  }

  public static boolean areBracketsBalanced(String str) {
    Stack<Character> stack = new Stack<>();
    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);
      if (ch == '(' || ch == '[' || ch == '{') {
        stack.push(ch);
      } else if (ch == ')') {
        if (handleClosingAndCheck(stack, '(')) {
          return false;
        }
      } else if (ch == ']') {
        if (handleClosingAndCheck(stack, '[')) {
          return false;
        }
      } else if (ch == '}') {
        if (handleClosingAndCheck(stack, '{')) {
          return false;
        }
      }
    }
    return stack.size() == 0;
  }

  private static boolean handleClosingAndCheck(Stack<Character> st, char currentCharacter) {
    if (st.size() == 0) {
      return true;
    } else if (st.peek() != currentCharacter) {
      return true;
    } else {
      st.pop();
      return false;
    }
  }
  // Function to find index of closing
  // bracket for given opening bracket.
  private static Map<Integer, Integer> mapBracketPairIndexes(String expression) {
    Stack<Integer> st = new Stack<>();
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < expression.length(); i++) {
      if (expression.charAt(i) == '(') {
        st.push(i);
      } else if (expression.charAt(i) == ')') {
        Integer openBracketIdx = st.pop();
        map.put(openBracketIdx, i);
      }
    }
    return map;
  }
}
