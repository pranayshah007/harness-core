-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

CREATE OR REPLACE FUNCTION harness_time_bucket_list(
p_started_at_epoch int,
p_ended_at_epoch int,
p_interval TEXT
)
RETURNS TABLE (t_seq_datetime timestamptz)
    LANGUAGE PLPGSQL IMMUTABLE PARALLEL SAFE AS $$
    DECLARE v_started_at_epoch_dtm timestamptz;
    v_ended_at_epoch_dtm timestamptz;
    v_dtm_stop_num int;
BEGIN
    v_started_at_epoch_dtm := DATE_TRUNC(p_interval, TO_TIMESTAMP(p_started_at_epoch));
    v_ended_at_epoch_dtm := DATE_TRUNC(p_interval, TO_TIMESTAMP(p_ended_at_epoch));

    v_dtm_stop_num := EXTRACT(EPOCH FROM (v_ended_at_epoch_dtm - v_started_at_epoch_dtm)) / extract(epoch from concat('1 ', p_interval)::interval)::integer;

    RETURN QUERY (SELECT (v_started_at_epoch_dtm + (concat(t.series, p_interval)::interval)) as t_seq_datetime
        FROM (SELECT *
        FROM generate_series(0, v_dtm_stop_num) series) t);
END;
$$;