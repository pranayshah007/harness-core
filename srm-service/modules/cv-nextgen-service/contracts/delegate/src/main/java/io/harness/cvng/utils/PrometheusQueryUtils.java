/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
@Slf4j
public class PrometheusQueryUtils {
  private static final String REGEX_PARENTHESIS_OPEN = "\\(";
  private static final String REGEX_OR = "|";
  /*  private static final String NOT_PARENTHESIS_OPEN_RANGE = "[^(]+";
    private static final String CLOSING_PARENTHESIS = "\\)";
    private static final String NOT_CLOSE_PARENTHESIS_RANGE = "[^)]+";*/

  public static String formGroupByQuery(String rawQuery, String SII) {
    if (StringUtils.isEmpty(rawQuery)) {
      return rawQuery;
    }
    if (!areBracketsBalanced(rawQuery)) {
      throw new RuntimeException("Bad Query");
    }
    // TODO throw exception for unknown by / agg operator
    StringBuilder query = new StringBuilder(rawQuery.trim().replaceAll(" ", ""));
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
            && "by".equals(query.substring(startBracketIdx - 2, startBracketIdx))) { // Operate at LHS
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
        log.debug(restoreQuery(query.toString()));
      }
    }
    String finalQuery = restoreQuery(query.toString());
    log.info("New PromQL Query: " + finalQuery);
    return finalQuery;
  }

  private static String restoreQuery(String query) {
    return query.replaceAll("sumby", " sum by ")
        .replaceAll("minby", " min by ")
        .replaceAll("maxby", " max by ")
        .replaceAll("avgby", " avg by ")
        .replaceAll("groupby", " group by ")
        .replaceAll("stddevby", " stddev by ")
        .replaceAll("stdvarby", " stdvar by")
        .replaceAll("countby", " count by ")
        .replaceAll("count_valuesby", " count_values by ")
        .replaceAll("bottomkby", " bottomk by ")
        .replaceAll("topkby", " topk by ")
        .replaceAll("quantileby", " quantile by ")
        .replaceAll("group_left", " group_left ")
        .replaceAll("group_right", " group_right "); ////and,or,unless,on,ignoring

    /*    List<String> binaryOperators = new ArrayList<>();
        String mydata = "some string with 'the data i want' inside";
        Pattern pattern = Pattern.compile("'(.*?)'");
        Matcher matcher = pattern.matcher(mydata);
        if (matcher.find())
        {
          System.out.println(matcher.group(1));
        }
            .replaceAll(
                CLOSING_PARENTHESIS + NOT_PARENTHESIS_OPEN_RANGE + "or" + NOT_CLOSE_PARENTHESIS_RANGE +
       PARENTHESIS_OPEN, " or ") ////and,or,unless,on,ignoring .replaceAll(CLOSING_PARENTHESIS +
       NOT_PARENTHESIS_OPEN_RANGE + "unless" + NOT_CLOSE_PARENTHESIS_RANGE
                    + PARENTHESIS_OPEN,
                " unless ")
            .replaceAll(
                CLOSING_PARENTHESIS + NOT_PARENTHESIS_OPEN_RANGE + "on" + NOT_CLOSE_PARENTHESIS_RANGE +
       PARENTHESIS_OPEN, " on ") ////and,or,unless,on,ignoring .replaceAll(CLOSING_PARENTHESIS +
       NOT_PARENTHESIS_OPEN_RANGE + "ignoring" + NOT_CLOSE_PARENTHESIS_RANGE
                    + PARENTHESIS_OPEN,
                " ignoring ")
            .replaceAll(
                CLOSING_PARENTHESIS + NOT_PARENTHESIS_OPEN_RANGE + "and" + NOT_CLOSE_PARENTHESIS_RANGE +
       PARENTHESIS_OPEN, " and ");*/
  }

  private static StringBuilder createAggregationOperatorPattern() {
    StringBuilder pattern = new StringBuilder();
    pattern.append("\\b(?:");
    pattern.append("sum" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("min" + REGEX_PARENTHESIS_OPEN);
    pattern.append("max" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("avg" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("group" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("stddev" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("stdvar" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("count" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("count_values" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("bottomk" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("topk" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("quantile" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("sumby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("minby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("maxby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("avgby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("groupby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("stddevby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("stdvarby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("countby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("count_valuesby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("bottomkby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("topkby" + REGEX_PARENTHESIS_OPEN + REGEX_OR);
    pattern.append("quantileby" + REGEX_PARENTHESIS_OPEN);
    pattern.append(")\\b");
    return pattern;
  }

  private static int wrapWithSameOperator(
      StringBuilder query, int startBracketIdx, String SII, String operator, int endBracketIdx) {
    String byClause = " by (" + SII + ")";
    String suffix = ")" + byClause; // edge case we have reached end
    query.insert(Math.min(endBracketIdx + 1, query.length()), suffix);
    String prefix = operator.replaceAll("by", "") + '('; // we always use one style sum() by() and not sum by()()
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
