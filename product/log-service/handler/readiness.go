// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"io"
	"net/http"

	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/log-service/stream"
)

// HandlePing returns an http.HandlerFunc that pings
// the live stream and store.
func HandlePing(s stream.Stream, store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()

		var err error
		if err = s.Ping(ctx); err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				Errorln("api: cannot ping the stream")
			return

		}

		if err = store.Ping(); err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				Errorln("api: cannot ping the store")
			return
		}

		io.WriteString(w, "OK")
	}
}
