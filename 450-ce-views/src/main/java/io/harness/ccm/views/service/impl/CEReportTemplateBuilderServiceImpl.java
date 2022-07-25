/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewTimeRangeType;
import io.harness.ccm.views.graphql.QLCESortOrder;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewDataPoint;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewSortType;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewTimeGroupType;
import io.harness.ccm.views.graphql.QLCEViewTimeSeriesData;
import io.harness.ccm.views.graphql.QLCEViewTimeTruncGroupBy;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.service.CEReportTemplateBuilderService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.InvalidRequestException;

import com.google.cloud.bigquery.BigQuery;
import com.google.inject.Inject;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;

@Slf4j
public class CEReportTemplateBuilderServiceImpl implements CEReportTemplateBuilderService {
  @Inject CEViewService ceViewService;
  @Inject ViewsBillingServiceImpl viewsBillingService;
  @Inject ViewsQueryHelper viewsQueryHelper;

  // For table construction
  private static final String TABLE_START =
      "<table style=\"border-collapse: collapse;width: 80%;font-family: arial, sans-serif;margin-left: 7%; min-width: 550px;max-width: 550px;\">";
  private static final String TABLE_END = "</table>";
  private static final String COLUMN1_HEADER =
      "<th style=\"border: 1px solid #dddddd;text-align: left;padding: 8px;background-color: #dddddd;\">%s</th>";
  private static final String COLUMN2_HEADER =
      "<th style=\"border: 1px solid #dddddd;text-align: left;padding: 8px;background-color: #dddddd;\">Total Cost</th>";
  private static final String COLUMN3_HEADER =
      "<th style=\"border: 1px solid #dddddd;text-align: left;padding: 8px;background-color: #dddddd;\">Cost Trend</th>";
  private static final String LEFT_COLUMN =
      "<td style=\"border: 1px solid #dddddd;border-right: 0px;text-align: left;padding: 8px;color: #777777;\">%s</td>";
  private static final String MIDDLE_COLUMN =
      "<td style=\"border: 1px solid #dddddd;border-right: 0px;border-left: 0px;text-align: left;padding: 8px;color: #777777;\">%s</td>";
  private static final String RIGHT_COLUMN =
      "<td style=\"border: 1px solid #dddddd;border-left: 0px;text-align: left;padding: 8px;color: %s;\">%s %s</td>";
  private static final String ROW_START = "<tr>";
  private static final String ROW_END = "</tr>";
  private static final String COST_TREND = "<span style=\"font-size: 15px; color: %s\">( %s | %s )</span>";
  private static final String PERSPECTIVE_URL_TEMPLATE =
      "/account/%s/continuous-efficiency/perspective-explorer/%s/%s?defaultGroupBy=fieldId=%s%%26fieldName=%s%%26identifier=%s%%26identifierName=%s&defaultTimeRange=%s";
  private static final String PERSPECTIVE_DEFAULT_URL_TEMPLATE =
      "/account/%s/continuous-efficiency/perspective-explorer/%s/%s";

  // Template keys
  private static final String VIEW_NAME = "VIEW_NAME";
  private static final String DATE = "DATE";
  private static final String TOTAL_COST = "TOTAL_COST";
  private static final String TOTAL_COST_TREND = "TOTAL_COST_TREND";
  private static final String PERIOD = "PERIOD";
  private static final String TOTAL_COST_LAST_WEEK = "TOTAL_COST_LAST_WEEK";
  private static final String TABLE = "TABLE";
  private static final String CHART = "CHART";
  private static final String UNSUBSCRIBE_URL = "UNSUBSCRIBE_URL";
  private static final String PERSPECTIVE_URL = "PERSPECTIVE_URL";

  // Constants
  private static final String ZERO = "0";
  private static final String WEEK = "WEEK";
  private static final String DAY = "DAY";
  private static final String THIRTY_DAYS = "30 DAYS";
  private static final String MONTH = "MONTH";
  private static final long DAY_IN_MILLISECONDS = 86400000L;
  private static final String DEFAULT_TIMEZONE = "GMT";
  private static final String DATE_PATTERN = "MM/dd";
  private static final String GREEN_COLOR = "green";
  private static final String RED_COLOR = "red";
  private static final String GREY_COLOR = "#777777";
  private static final String UPWARD_ARROW = "&uarr;";
  private static final String DOWNWARD_ARROW = "&darr;";
  private static final String PERCENT = "%";
  private static final String DOLLAR = "$";
  private static final Integer DEFAULT_LIMIT = Integer.MAX_VALUE - 1;
  private static final Integer DEFAULT_OFFSET = 0;
  private static final Color[] COLORS = {new Color(72, 165, 243), new Color(147, 133, 241), new Color(83, 205, 124),
      new Color(255, 188, 9), new Color(243, 92, 97), new Color(55, 214, 203), new Color(236, 97, 181),
      new Color(255, 142, 60), new Color(178, 96, 9), new Color(25, 88, 173)};
  private static final String[] COLOR_HEX_CODES = {
      "#48A5F3", "#9385F1", "#53CD7C", "#FFBC09", "#F35C61", "#37D6CB", "#EC61B5", "#FF8E3C", "#B26009", "#1958AD"};
  private static final Color WHITE = new Color(255, 255, 255);
  private static final Color GRAY = new Color(112, 113, 117);
  private static final int REPEAT_FREQUENCY = 10;

  @Override
  public Map<String, String> getTemplatePlaceholders(
      String accountId, String viewId, BigQuery bigQuery, String cloudProviderTableName) {
    return getTemplatePlaceholders(accountId, viewId, null, bigQuery, cloudProviderTableName);
  }

  @Override
  public Map<String, String> getTemplatePlaceholders(
      String accountId, String viewId, String reportId, BigQuery bigQuery, String cloudProviderTableName) {
    Map<String, String> templatePlaceholders = new HashMap<>();

    // Get cloud provider table name here
    CEView view = ceViewService.get(viewId);
    if (view == null) {
      throw new InvalidRequestException("Exception while generating report. View doesn't exist.");
    }
    String entity = getEntity(view);
    ViewTimeRangeType rangeType = view.getViewTimeRange().getViewTimeRangeType();
    String period = getPeriod(rangeType);
    long startOfDay = getStartOfDayTimestamp(1);
    List<QLCEViewAggregation> aggregationFunction = getTotalCostAggregation();
    List<QLCEViewSortCriteria> sortCriteria = getSortCriteria();
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getViewFilter(viewId));
    if (!period.equals(MONTH)) {
      filters.add(getTimeFilter(
          getStartTime(startOfDay, getTimePeriod(period)).toEpochMilli(), QLCEViewTimeFilterOperator.AFTER));
      filters.add(getTimeFilter(getEndTime(startOfDay).toEpochMilli(), QLCEViewTimeFilterOperator.BEFORE));
    } else {
      filters.add(getTimeFilter(getStartOfMonth(true), QLCEViewTimeFilterOperator.AFTER));
      filters.add(getTimeFilter(getStartOfMonth(false) - 1000, QLCEViewTimeFilterOperator.BEFORE));
    }
    List<QLCEViewGroupBy> groupBy = new ArrayList<>();

    // Generating Trend data
    QLCEViewTrendInfo trendData = viewsBillingService
                                      .getTrendStatsDataNg(bigQuery, filters, aggregationFunction,
                                          cloudProviderTableName, viewsQueryHelper.buildQueryParams(accountId, false))
                                      .getTotalCost();
    if (trendData == null) {
      throw new InvalidRequestException("Exception while generating report. No data to for cost trend");
    }

    // Generating table data
    List<QLCEViewEntityStatsDataPoint> tableData =
        viewsBillingService
            .getEntityStatsDataPointsNg(bigQuery, filters, groupBy, aggregationFunction, sortCriteria,
                cloudProviderTableName, DEFAULT_LIMIT, DEFAULT_OFFSET,
                viewsQueryHelper.buildQueryParams(accountId, false, false))
            .getData();
    if (isEmpty(tableData)) {
      throw new InvalidRequestException("Exception while generating report. No data to for table");
    }
    List<String> entities = tableData.stream().map(QLCEViewEntityStatsDataPoint::getName).collect(Collectors.toList());

    // Generating chart data
    groupBy.add(QLCEViewGroupBy.builder()
                    .timeTruncGroupBy(QLCEViewTimeTruncGroupBy.builder().resolution(QLCEViewTimeGroupType.DAY).build())
                    .build());
    List<QLCEViewTimeSeriesData> chartData = viewsBillingService.convertToQLViewTimeSeriesData(
        viewsBillingService.getTimeSeriesStatsNg(bigQuery, filters, groupBy, aggregationFunction, sortCriteria,
            cloudProviderTableName, false, DEFAULT_LIMIT,
            viewsQueryHelper.buildQueryParams(accountId, true, false, false, false)),
        accountId, groupBy);
    if (chartData == null) {
      throw new InvalidRequestException("Exception while generating report. No data to for chart");
    }

    // Filling template placeholders
    templatePlaceholders.put(VIEW_NAME, view.getName());
    templatePlaceholders.put(PERIOD, period);
    templatePlaceholders.put(DATE, getReportDateRange(period));

    // Generating chart for report
    templatePlaceholders.put(CHART, getEncodedImage(createChart(chartData, entities, !period.equals(WEEK))));

    // Trend bar for report
    templatePlaceholders.put(TOTAL_COST, trendData.getStatsValue());
    if (trendData.getStatsTrend().doubleValue() < 0) {
      templatePlaceholders.put(TOTAL_COST_TREND,
          String.format(
              COST_TREND, GREEN_COLOR, trendData.getStatsTrend().toString() + PERCENT, getTotalCostDiff(trendData)));
    } else if (trendData.getStatsTrend().doubleValue() > 0) {
      templatePlaceholders.put(TOTAL_COST_TREND,
          String.format(
              COST_TREND, RED_COLOR, trendData.getStatsTrend().toString() + PERCENT, getTotalCostDiff(trendData)));
    } else {
      templatePlaceholders.put(TOTAL_COST_TREND, String.format(COST_TREND, GREY_COLOR, "-", "-"));
    }

    templatePlaceholders.put(
        TOTAL_COST_LAST_WEEK, DOLLAR + viewsQueryHelper.formatNumber(getTotalCostLastWeek(trendData)));

    // Generating table for report
    templatePlaceholders.put(TABLE, generateTable(tableData, entity));
    templatePlaceholders.put(PERSPECTIVE_URL, getPerspectiveUrl(view));

    return templatePlaceholders;
  }

  private List<QLCEViewAggregation> getTotalCostAggregation() {
    return Collections.singletonList(
        QLCEViewAggregation.builder().columnName("cost").operationType(QLCEViewAggregateOperation.SUM).build());
  }

  private List<QLCEViewSortCriteria> getSortCriteria() {
    return Collections.singletonList(
        QLCEViewSortCriteria.builder().sortOrder(QLCESortOrder.DESCENDING).sortType(QLCEViewSortType.COST).build());
  }

  private QLCEViewFilterWrapper getViewFilter(String viewId) {
    return QLCEViewFilterWrapper.builder()
        .viewMetadataFilter(QLCEViewMetadataFilter.builder().viewId(viewId).isPreview(false).build())
        .build();
  }

  private QLCEViewFilterWrapper getTimeFilter(long timestamp, QLCEViewTimeFilterOperator operator) {
    return QLCEViewFilterWrapper.builder()
        .timeFilter(QLCEViewTimeFilter.builder()
                        .field(QLCEViewFieldInput.builder()
                                   .fieldId("startTime")
                                   .fieldName("startTime")
                                   .identifier(ViewFieldIdentifier.COMMON)
                                   .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                                   .build())
                        .operator(operator)
                        .value(timestamp)
                        .build())
        .build();
  }

  private String getEntity(CEView view) {
    return view.getViewVisualization().getGroupBy().getFieldName();
  }

  // Todo: Update this when time filters from view are used
  private String getPeriod(ViewTimeRangeType rangeType) {
    switch (rangeType) {
      case LAST_30:
        return THIRTY_DAYS;
      case LAST_MONTH:
        return MONTH;
      case LAST_7:
      default:
        return WEEK;
    }
  }

  private String getTotalCostDiff(QLCEViewTrendInfo trendInfo) {
    return String.valueOf(
        Math.round((trendInfo.getValue().doubleValue() - getTotalCostLastWeek(trendInfo)) * 100D) / 100D);
  }

  private double getTotalCostLastWeek(QLCEViewTrendInfo trendInfo) {
    return Math.round(
               (100 * trendInfo.getValue().doubleValue() / (100 + trendInfo.getStatsTrend().doubleValue())) * 100D)
        / 100D;
  }

  private String generateTable(List<QLCEViewEntityStatsDataPoint> tableData, String entity) {
    String table = "";
    if (isNotEmpty(tableData)) {
      StringJoiner joiner = new StringJoiner(" ");
      joiner.add(TABLE_START);
      joiner.add(ROW_START);
      joiner.add(String.format(COLUMN1_HEADER, entity));
      joiner.add(COLUMN2_HEADER);
      joiner.add(COLUMN3_HEADER);
      joiner.add(ROW_END);

      tableData.forEach(data -> {
        joiner.add(ROW_START);
        joiner.add(String.format(LEFT_COLUMN, data.getName()));
        joiner.add(String.format(MIDDLE_COLUMN, DOLLAR + viewsQueryHelper.formatNumber(data.getCost().doubleValue())));
        if (data.getCostTrend().doubleValue() < 0) {
          joiner.add(String.format(
              RIGHT_COLUMN, GREEN_COLOR, DOWNWARD_ARROW, Math.abs(data.getCostTrend().doubleValue()) + PERCENT));
        } else if (data.getCostTrend().doubleValue() > 0) {
          joiner.add(String.format(RIGHT_COLUMN, RED_COLOR, UPWARD_ARROW, data.getCostTrend().doubleValue() + PERCENT));
        } else {
          joiner.add(String.format(RIGHT_COLUMN, GREY_COLOR, "", "-"));
        }
        joiner.add(ROW_END);
      });

      joiner.add(TABLE_END);
      table = joiner.toString();
    }
    return table;
  }

  private String getReportDateRange(String period) {
    long timePeriod = getTimePeriod(period);
    Instant startInstant;
    Instant endInstant;
    if (!period.equals(MONTH)) {
      startInstant = getStartTime(getStartOfDayTimestamp(0), timePeriod);
      endInstant = getEndTime(getStartOfDayTimestamp(0));
    } else {
      startInstant = getStartTime(getStartOfMonth(true), 0);
      endInstant = getEndTime(getStartOfMonth(false));
    }
    return startInstant.atZone(ZoneId.of(DEFAULT_TIMEZONE)).format(DateTimeFormatter.ofPattern(DATE_PATTERN)) + " to "
        + endInstant.atZone(ZoneId.of(DEFAULT_TIMEZONE)).format(DateTimeFormatter.ofPattern(DATE_PATTERN));
  }

  private long getTimePeriod(String period) {
    switch (period) {
      case DAY:
        return DAY_IN_MILLISECONDS;
      case THIRTY_DAYS:
        return 30 * DAY_IN_MILLISECONDS;
      case WEEK:
      default:
        return 7 * DAY_IN_MILLISECONDS;
    }
  }

  private Instant getStartTime(long timestamp, long period) {
    return Instant.ofEpochMilli(timestamp - period);
  }

  private Instant getEndTime(long timestamp) {
    return Instant.ofEpochMilli(timestamp - 1000);
  }

  private long getStartOfDayTimestamp(long offset) {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
    return (zdtStart.toEpochSecond() * 1000) - offset;
  }

  // Returns start of current month, if prev month is true, returns start of previous month
  private long getStartOfMonth(boolean prevMonth) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    if (prevMonth) {
      c.add(Calendar.MONTH, -1);
    }
    return c.getTimeInMillis();
  }

  private String getEncodedImage(byte[] bytes) {
    String encodedfile = null;
    try {
      encodedfile = Base64.getEncoder().encodeToString(bytes);
    } catch (Exception e) {
      log.error("Error in encoding chart: ", e);
    }
    return encodedfile;
  }

  private DefaultCategoryDataset getDataset(List<QLCEViewTimeSeriesData> data) {
    DefaultCategoryDataset dataset = new DefaultCategoryDataset();
    for (QLCEViewTimeSeriesData entry : data) {
      for (QLCEViewDataPoint dataPoint : entry.getValues()) {
        dataset.addValue(dataPoint.getValue().doubleValue(), dataPoint.getName(), entry.getDate());
      }
    }
    return dataset;
  }

  private Map<String, String> getEntityColorMapping(List<QLCEViewTimeSeriesData> data, List<String> entities) {
    Map<String, String> entityToColorMapping = new HashMap<>();
    DefaultCategoryDataset dataset = getDataset(data);
    for (String entity : entities) {
      entityToColorMapping.put(entity, COLOR_HEX_CODES[dataset.getColumnIndex(entity) % REPEAT_FREQUENCY]);
    }
    log.info("Color mapping for legend: {}", entityToColorMapping);
    return entityToColorMapping;
  }

  private byte[] createChart(List<QLCEViewTimeSeriesData> data, List<String> entities, boolean adjustFont) {
    DefaultCategoryDataset dataset = getDataset(data);
    int index = 0;

    // Creating stacked bar chart
    JFreeChart chart =
        ChartFactory.createStackedBarChart("", "", "", dataset, PlotOrientation.VERTICAL, true, false, false);
    chart.setBackgroundPaint(WHITE);
    chart.setBorderVisible(false);
    chart.setBorderPaint(WHITE);

    // Color and alignment settings of plot area
    CategoryPlot plot = chart.getCategoryPlot();
    plot.setBackgroundPaint(WHITE);
    plot.setRangeGridlinesVisible(true);
    plot.setAxisOffset(RectangleInsets.ZERO_INSETS);
    plot.getDomainAxis().setCategoryMargin(0.1);
    plot.getRangeAxis().setTickLabelPaint(GRAY);
    plot.getDomainAxis().setTickLabelPaint(GRAY);
    ((BarRenderer) plot.getRenderer()).setBarPainter(new StandardBarPainter());
    plot.getRangeAxis().setMinorTickCount(5);
    plot.setOutlinePaint(WHITE);
    plot.setRangeGridlinePaint(GRAY);

    if (adjustFont) {
      plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
      plot.getDomainAxis().setUpperMargin(0.01);
      plot.getDomainAxis().setLowerMargin(0.01);
    }

    // Setting bar colors
    while (index < entities.size()) {
      plot.getRenderer().setSeriesPaint(index, COLORS[index % REPEAT_FREQUENCY]);
      index++;
    }

    BufferedImage bufferedImage = chart.createBufferedImage(800, 600);
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try {
      ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
    } catch (IOException e) {
      log.error("Error in generating chart: ", e);
    }

    return byteArrayOutputStream.toByteArray();
  }

  public String getPerspectiveUrl(CEView view) {
    String defaultUrl =
        String.format(PERSPECTIVE_DEFAULT_URL_TEMPLATE, view.getAccountId(), view.getUuid(), view.getName());
    try {
      return String.format(PERSPECTIVE_URL_TEMPLATE, view.getAccountId(), view.getUuid(), view.getName(),
          view.getViewVisualization().getGroupBy().getFieldId(),
          view.getViewVisualization().getGroupBy().getFieldName(),
          view.getViewVisualization().getGroupBy().getIdentifier(),
          view.getViewVisualization().getGroupBy().getIdentifierName(), view.getViewTimeRange().getViewTimeRangeType());
    } catch (Exception e) {
      log.info("Can't create explorer Url for perspective : {}", view.getUuid());
    }
    return defaultUrl;
  }
}
