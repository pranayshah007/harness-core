-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

-- harness_date_bin_graphql is the postgres replacement for time_bucket in timescale
-- there are three overloads for this function, with second parameter as
-- timestamp
-- timestamptz
-- date

CREATE OR REPLACE FUNCTION harness_date_bin_graphql(
p_bucket_width INTERVAL,
p_timestamp timestamptz)
RETURNS timestamptz
LANGUAGE PLPGSQL IMMUTABLE PARALLEL SAFE AS $$
    DECLARE v_bucket_width_bigint bigint;
    v_timestamp_bigint bigint;
BEGIN
    v_bucket_width_bigint := extract(epoch from p_bucket_width);
    v_timestamp_bigint := extract(epoch from p_timestamp)::bigint;

    RETURN to_timestamp((v_timestamp_bigint / v_bucket_width_bigint)::bigint * v_bucket_width_bigint);
END;
$$ ;

CREATE OR REPLACE FUNCTION harness_date_bin_graphql(
p_bucket_width INTERVAL,
p_timestamp timestamp)
RETURNS timestamp
LANGUAGE PLPGSQL IMMUTABLE PARALLEL SAFE AS $$
    DECLARE v_bucket_width_bigint bigint;
    v_timestamp_bigint bigint;
BEGIN
    v_bucket_width_bigint := extract(epoch from p_bucket_width);
    v_timestamp_bigint := extract(epoch from p_timestamp)::bigint;

    RETURN to_timestamp((v_timestamp_bigint / v_bucket_width_bigint)::bigint * v_bucket_width_bigint);
END;
$$ ;

CREATE OR REPLACE FUNCTION harness_date_bin_graphql(
p_bucket_width INTERVAL,
p_timestamp date)
RETURNS date
LANGUAGE PLPGSQL IMMUTABLE PARALLEL SAFE AS $$
    DECLARE v_bucket_width_bigint bigint;
    v_timestamp_bigint bigint;
BEGIN
    v_bucket_width_bigint := extract(epoch from p_bucket_width);
    v_timestamp_bigint := extract(epoch from p_timestamp)::bigint;

    RETURN to_timestamp((v_timestamp_bigint / v_bucket_width_bigint)::bigint * v_bucket_width_bigint);
END;
$$ ;