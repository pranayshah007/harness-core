// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"bufio"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/log-service/stream"

	"golang.org/x/sync/errgroup"
)

var pingInterval = time.Second * 30
var tailMaxTime = time.Hour * 1

// BufioWriterCloser combines a bufio Writer with a Closer
type BufioWriterCloser struct {
	wc io.Closer
	*bufio.Writer
}

func (bwc *BufioWriterCloser) Close() error {
	if err := bwc.Flush(); err != nil {
		return err
	}
	return bwc.wc.Close()
}

// HandleOpen returns an http.HandlerFunc that opens
// the live stream.
func HandleOpen(stream stream.Stream) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()
		st := time.Now()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		if err := stream.Create(ctx, key); err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot create stream")
			return
		}

		logger.FromRequest(r).WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully created stream")
		w.WriteHeader(http.StatusNoContent)
	}
}

// HandleClose returns an http.HandlerFunc that closes
// the live stream and optionally snapshots the stream.
func HandleClose(logStream stream.Stream, store store.Store, scanBatch int64) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()
		st := time.Now()
		var keys []string

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		// If prefix is set as true, perform close operation on all the keys
		// with that prefix. If no keys are found with that prefix, it's not
		// an error.
		usePrefix := r.FormValue(usePrefixParam)
		keyList := r.FormValue(keyListParam)

		logger.FromRequest(r).WithField("key", key).
			WithField(usePrefixParam, usePrefix).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: initiating close request on log service")

		if usePrefix == "true" {
			// Use the provided key as a prefix
			var err error
			keys, err = logStream.ListPrefix(ctx, key, scanBatch)
			if err != nil {
				WriteInternalError(w, err)
				logger.FromRequest(r).
					WithError(err).
					WithField("key", key).
					WithField(usePrefixParam, "true").
					Errorln("api: unable to fetch prefixes")
				return
			}
		} else if keyList != "" {
			keys = strings.Split(keyList, ",")
			for i := range keys {
				keys[i] = CreateAccountSeparatedKey(accountID, keys[i])
			}
		} else {
			keys = []string{key}
		}

		snapshot := r.FormValue(snapshotParam)
		if snapshot == "true" {
			for _, k := range keys {
				if err := logStream.Exists(ctx, k); err != nil {
					WriteInternalError(w, err)
					logger.FromRequest(r).
						WithError(err).
						WithField("key", k).
						Errorln("api: key does not exist")
					return
				}
				pr, pw := io.Pipe()
				defer pr.Close()
				br := bufio.NewReader(pr)
				bwc := &BufioWriterCloser{pw, bufio.NewWriter(pw)}

				g := new(errgroup.Group)
				g.Go(func() error {
					return logStream.CopyTo(ctx, k, bwc)
				})

				g.Go(func() error {
					return store.Upload(ctx, k, br)
				})

				if err := g.Wait(); err != nil {
					WriteInternalError(w, err)
					logger.FromRequest(r).
						WithError(err).
						WithField("key", k).
						Errorln("api: unable to snapshot stream to store")
					return // don't delete the stream if snapshotting failed
				}
			}
			logger.FromRequest(r).WithField("keys", keys).
				WithField("num_keys", len(keys)).
				Infoln("api: successfully snapshotted all keys")
		}

		for _, k := range keys {

			if err := logStream.Delete(ctx, k); err != nil {
				WriteInternalError(w, err)
				logger.FromRequest(r).
					WithError(err).
					WithField("key", k).
					Warnln("api: cannot close stream")
				return
			}
		}

		logger.FromRequest(r).WithField("keys", keys).
			WithField("snapshot", snapshot).
			WithField(usePrefixParam, usePrefix).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			WithField("num_keys", len(keys)).
			Infoln("api: successfully completed closing of streams")
		w.WriteHeader(http.StatusNoContent)
	}
}

// HandleWrite returns an http.HandlerFunc that writes
// to the live stream.
func HandleWrite(s stream.Stream) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()
		st := time.Now()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		in := []*stream.Line{}
		if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
			WriteBadRequest(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot unmarshal input")
			return
		}

		// write to stream only if it exists
		if err := s.Exists(ctx, key); err != nil {
			return
		}
		if err := s.Write(ctx, key, in...); err != nil {
			if err == stream.ErrNotFound {
				WriteBadRequest(w, err)
				return
			}
			if err != nil {
				WriteInternalError(w, err)
				logger.FromRequest(r).
					WithError(err).
					WithField("key", key).
					Warnln("api: cannot write to stream")
				return
			}
		}

		logger.FromRequest(r).WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			WithField("num_lines", len(in)).
			Infoln("api: successfully wrote to stream")
		w.WriteHeader(http.StatusNoContent)
	}
}

// HandleTail returns an http.HandlerFunc that tails
// the live stream.
func HandleTail(s stream.Stream) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		logger.FromRequest(r).WithField("key", r.FormValue(key)).
			Infoln("api: Starting to tail stream")

		h := w.Header()
		h.Set("Content-Type", "text/event-stream")
		h.Set("Cache-Control", "no-cache")
		h.Set("Connection", "keep-alive")
		h.Set("X-Accel-Buffering", "no")
		h.Set("Access-Control-Allow-Origin", "*")

		f, ok := w.(http.Flusher)
		if !ok {
			logger.FromRequest(r).
				Warnln("stream: request does not implement http.Flusher")
			return
		}

		io.WriteString(w, ": ping\n")
		f.Flush()

		ctx, cancel := context.WithCancel(r.Context())
		defer cancel()

		enc := json.NewEncoder(w)
		linec, errc := s.Tail(ctx, key)
		if errc == nil {
			io.WriteString(w, "event: error\ndata: eof\n")
			return
		}

		tailMaxTimeTimer := time.After(tailMaxTime)
		msgDelayTimer := time.NewTimer(pingInterval) // if time b/w messages takes longer, send a ping
		defer msgDelayTimer.Stop()

	L:
		for {
			msgDelayTimer.Reset(pingInterval)
			select {
			case <-ctx.Done():
				break L
			case <-errc:
				break L
			case <-tailMaxTimeTimer:
				break L
			case <-msgDelayTimer.C:
				io.WriteString(w, ": ping\n")
				f.Flush()
			case line := <-linec:
				io.WriteString(w, "data: ")
				enc.Encode(line)
				io.WriteString(w, "\n")
				f.Flush()
			}
		}

		io.WriteString(w, "event: error\ndata: eof\n")
		f.Flush()

		logger.FromRequest(r).WithField("key", key).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully tailed stream")
	}
}

// HandleInfo returns an http.HandlerFunc that writes the
// stream information to the http.Response.
func HandleInfo(stream stream.Stream) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := context.Background()
		inf := stream.Info(ctx)
		enc := json.NewEncoder(w)
		enc.SetIndent("", "  ")
		enc.Encode(inf)
	}
}
