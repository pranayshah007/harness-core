-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

SET SEARCH_PATH = "public";

DROP FUNCTION IF EXISTS get_service_instances_by_date;

CREATE OR REPLACE FUNCTION get_service_instances_by_date(
  p_accountid TEXT
, p_begin_date DATE
, p_end_date DATE
, p_sum_by_month BOOL DEFAULT FALSE
, p_debug BOOL DEFAULT FALSE
)
RETURNS TABLE (
-- column names need a prefix or Postgres will error because of conflict with other columns and variables
t_report_day_or_month_date DATE,
t_service_instance_count INT
) AS $$
DECLARE v_rows_affected INT default 0;
        v_query_start_time TIMESTAMPTZ;
        v_query_elapsed_secs_decimal DECIMAL(8, 3);
        v_interim_begin_date DATE;
        v_interim_end_date DATE;
        v_loop_count INT;
BEGIN
    DROP TABLE IF EXISTS _tmp_tbl_service_instances_by_date_range;
    CREATE TEMPORARY TABLE _tmp_tbl_service_instances_by_date_range (
    report_date DATE,
    service_instance_count INT
    );

    v_interim_begin_date := '1900-01-01';
    v_loop_count := -1;  -- so the first increment will be 0
    -- function generates reports, including reports for p_begin_date and p_end_date days
    WHILE v_interim_begin_date < p_end_date LOOP
            v_query_start_time := clock_timestamp();
            v_loop_count := v_loop_count + 1;

            IF p_sum_by_month THEN
                v_interim_begin_date := p_begin_date + concat(v_loop_count, ' MONTH')::interval;
                v_interim_end_date := p_begin_date + concat((v_loop_count + 1), ' MONTH')::interval;

                -- by month will go past the end date so handling it here
                IF v_interim_begin_date >= p_end_date THEN
                    EXIT;
                END IF;
            ELSE
                v_interim_begin_date := p_begin_date + concat(v_loop_count, ' DAY')::interval;
                v_interim_end_date := p_begin_date + concat((v_loop_count + 1), ' DAY')::interval;
            END IF;

            INSERT INTO _tmp_tbl_service_instances_by_date_range(report_date, service_instance_count)
            SELECT v_interim_begin_date as report_day_or_month_date, sum(instanceCount) as service_instance_count
            FROM
                (SELECT percentile_disc(0.95) WITHIN GROUP (ORDER BY instancesPerServicePerReportedat.instancecount) AS instanceCount,
                        orgid, projectid, serviceid
                 FROM (SELECT date_trunc('minute', reportedat) AS reportedat, orgid, projectid, serviceid, SUM(instancecount) AS instancecount
                        FROM ng_instance_stats
                        WHERE accountid = p_accountid
                            -- always follow pattern of >= begin date and < end date
                            -- Report has to be generated for last 30 days including the current reported day(v_interim_begin_date).
                            -- For example report for 2023-06-05 day, time range interval
                            -- will be from: 2023-05-07 00:00:00 including the whole May 07, to: 2023-06-06 00:00:00 including the whole Jun 05
                            -- It's overall 30 days.
                            AND reportedat >= v_interim_begin_date - INTERVAL '29 day'
                            AND reportedat < v_interim_end_date
                        GROUP BY orgid, projectid, serviceid, date_trunc('minute', reportedat)
                        ORDER BY reportedat DESC
                      ) instancesPerServicePerReportedat
                 GROUP BY orgid, projectid, serviceid) instancesPerAllServiceAtDay;

            GET DIAGNOSTICS v_rows_affected = ROW_COUNT ;

            IF p_debug THEN
                v_query_elapsed_secs_decimal := EXTRACT(epoch from (clock_timestamp() - v_query_start_time))::decimal(8, 3);
                RAISE INFO '%: v_query_elapsed_secs_decimal = %, v_rows_affected = %, v_interim_begin_date = %, v_interim_end_date = %', clock_timestamp(), v_query_elapsed_secs_decimal, v_rows_affected, v_interim_begin_date, v_interim_end_date;
            END IF;
        END LOOP;

    RETURN QUERY SELECT report_date, service_instance_count
                 FROM _tmp_tbl_service_instances_by_date_range
                 ORDER BY report_date NULLS LAST;
END;
$$ LANGUAGE PLPGSQL;