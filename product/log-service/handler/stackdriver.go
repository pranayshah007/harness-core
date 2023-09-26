// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"cloud.google.com/go/logging"
	"encoding/json"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/stackdriver"
	"google.golang.org/genproto/googleapis/api/monitoredres"
	"net/http"
)

// HandlePost returns a http.HandlerFunc that writes log lines from payload to stackdriver.
func HandlePost(s *stackdriver.Stackdriver) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		logKey := r.FormValue(keyParam)

		in := make([]stackdriver.Line, 0, 1000)
		if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
			WriteBadRequest(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("body", r.Body).
				Errorln("api: cannot unmarshal input")
			return
		}

		globalResource := monitoredres.MonitoredResource{
			Type: "global",
		}
		for _, v := range in {
			entry := logging.Entry{Payload: v.Payload, Resource: &globalResource, Severity: logging.Severity(v.Severity), Labels: v.Labels}
			s.Write(logKey, entry)
		}
	}
}

// HandleStackdriverPing returns a http.HandlerFunc that pings stackdriver to check connectivity.
func HandleStackdriverPing(stackdriver *stackdriver.Stackdriver) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()
		err := stackdriver.Ping(ctx)
		if err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				Errorln("Failed to ping stackdriver")
			return
		}
	}
}
