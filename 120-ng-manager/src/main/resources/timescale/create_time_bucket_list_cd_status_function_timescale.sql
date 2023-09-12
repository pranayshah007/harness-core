-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

SET SEARCH_PATH = "public";

DROP ROUTINE IF EXISTS time_bucket_list_cd_status;

CREATE OR REPLACE FUNCTION time_bucket_list_cd_status(
p_interval bigint,
p_startts_begin bigint,
p_startts_end bigint,
p_status_list TEXT[],
p_debug bool DEFAULT false
)
RETURNS TABLE (status TEXT, startts bigint) AS $$
    DECLARE v_startts_begin_trunc bigint;
    v_startts_end_trunc bigint;
    v_dtm_stop_num int;
BEGIN
    v_startts_begin_trunc := p_startts_begin - (p_startts_begin % p_interval);
    v_startts_end_trunc := p_startts_end - (p_startts_end % p_interval);

    v_dtm_stop_num := (ceiling(v_startts_end_trunc - v_startts_begin_trunc)/p_interval)::bigint;

    IF p_debug THEN
        RAISE INFO 'v_startts_begin_trunc = %, v_startts_end_trunc = %, v_dtm_stop_num = %',
            v_startts_begin_trunc, v_startts_end_trunc, v_dtm_stop_num;
    END IF;

    RETURN QUERY (SELECT c_status, v_startts_begin_trunc + (p_interval * t1.series) as c_startts
        FROM (SELECT *
        FROM generate_series(0, v_dtm_stop_num) series) t1
            cross join unnest(p_status_list) c_status);
END;
$$
LANGUAGE PLPGSQL;
