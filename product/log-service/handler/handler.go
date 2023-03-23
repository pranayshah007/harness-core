// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"io"
	"net/http"
	"net/http/pprof"

	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/log-service/stream"
	"github.com/harness/harness-core/product/platform/client"

	"github.com/go-chi/chi"
)

// Handler returns an http.Handler that exposes the
// service resources.
func Handler(stream stream.Stream, store store.Store, config config.Config, ngClient *client.HTTPClient) http.Handler {
	r := chi.NewRouter()
	r.Use(logger.Middleware)

	// Token generation endpoints
	// Format: /token?accountID=
	r.Mount("/token", func() http.Handler {
		sr := chi.NewRouter()
		// Validate the incoming request with a global secret and return back a token
		// for the given account ID if the match is successful (if auth is enabled).
		if !config.Auth.DisableAuth {
			sr.Use(TokenGenerationMiddleware(config, true, ngClient))
		}

		sr.Get("/", HandleToken(config))
		return sr
	}()) // Validates against global token

	if config.Debug {
		// Log service info endpoints
		// Only accessible from Harness side (admin privileges) if debug mode in on
		// Format: /info
		r.Mount("/info", func() http.Handler {
			sr := chi.NewRouter()
			// Validate the incoming request with a global secret and return info only if the
			// match is successful. This endpoint should be only accessible from the Harness side.
			// if !config.Auth.DisableAuth {
			// 	sr.Use(TokenGenerationMiddleware(config, false, ngClient))
			// }

			sr.Get("/stream", HandleInfo(stream))

			// Debug endpoints
			sr.HandleFunc("/debug/pprof/", pprof.Index)
			sr.HandleFunc("/debug/pprof/heap", pprof.Index)
			sr.HandleFunc("/debug/pprof/cmdline", pprof.Cmdline)
			sr.HandleFunc("/debug/pprof/profile", pprof.Profile)
			sr.HandleFunc("/debug/pprof/symbol", pprof.Symbol)
			sr.HandleFunc("/debug/pprof/trace", pprof.Trace)
			sr.HandleFunc("/debug/pprof/block", pprof.Index)
			sr.HandleFunc("/debug/pprof/goroutine", pprof.Index)
			sr.HandleFunc("/debug/pprof/threadcreate", pprof.Index)

			return sr
		}())
		r.Mount("/info/debug/getheap", pprof.Handler("heap"))
	}

	// Log stream endpoints
	// Format: /token?accountID=&key=
	r.Mount("/stream", func() http.Handler {
		sr := chi.NewRouter()
		// Validate the accountID in URL with the token generated above and authorize the request
		if !config.Auth.DisableAuth {
			sr.Use(AuthMiddleware(config, ngClient))
		}

		sr.Post("/", HandleOpen(stream))
		sr.Delete("/", HandleClose(stream, store))
		sr.Put("/", HandleWrite(stream))
		sr.Get("/", HandleTail(stream))
		sr.Get("/info", HandleInfo(stream))

		return sr
	}()) // Validates accountID with incoming token

	// Blob store endpoints
	// Format: /blob?accountID=&key=
	r.Mount("/blob", func() http.Handler {
		sr := chi.NewRouter()
		if !config.Auth.DisableAuth {
			sr.Use(AuthMiddleware(config, ngClient))
		}

		sr.Post("/", HandleUpload(store))
		sr.Delete("/", HandleDelete(store))
		sr.Get("/", HandleDownload(store))
		sr.Post("/link/upload", HandleUploadLink(store))
		sr.Post("/link/download", HandleDownloadLink(store))

		return sr
	}())

	// Liveness check
	r.Get("/healthz", func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, "OK")
	})

	// Readiness check
	r.Mount("/ready/healthz", func() http.Handler {
		sr := chi.NewRouter()
		sr.Get("/", HandlePing(stream))

		return sr
	}())

	return r
}
