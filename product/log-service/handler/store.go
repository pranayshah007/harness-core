// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"archive/zip"
	"bufio"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/log-service/stream"
	"golang.org/x/sync/errgroup"
)

// HandleUpload returns an http.HandlerFunc that uploads
// a blob to the datastore.
func HandleUpload(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()
		st := time.Now()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		if err := store.Upload(ctx, key, r.Body); err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot upload object")
			return
		}

		logger.FromRequest(r).
			WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully uploaded object")
		w.WriteHeader(http.StatusNoContent)
	}
}

// HandleUploadLink returns an http.HandlerFunc that generates
// a signed link to upload a blob to the datastore.
func HandleUploadLink(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()
		st := time.Now()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))
		expires := time.Hour

		link, err := store.UploadLink(ctx, key, expires)
		if err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot generate upload url")
			return
		}

		logger.FromRequest(r).
			WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully created upload link")
		WriteJSON(w, struct {
			Link    string        `json:"link"`
			Expires time.Duration `json:"expires"`
		}{link, expires}, 200)
	}
}

// HandleDownload returns an http.HandlerFunc that downloads
// a blob from the datastore and copies to the http.Response.
func HandleDownload(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		st := time.Now()
		h := w.Header()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		out, err := store.Download(ctx, key)
		if out != nil {
			defer out.Close()
		}
		if err != nil {
			WriteNotFound(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot download the object")
		} else {
			io.Copy(w, out)
			logger.FromRequest(r).
				WithField("key", key).
				WithField("latency", time.Since(st)).
				WithField("time", time.Now().Format(time.RFC3339)).
				Infoln("api: successfully downloaded object")
		}
	}
}

// HandleDownloadLink returns an http.HandlerFunc that generates
// a signed link to download a blob to the datastore.
func HandleDownloadLink(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		st := time.Now()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))
		expires := time.Hour

		link, err := store.DownloadLink(ctx, key, expires)
		if err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot generate download url")
			return
		}

		logger.FromRequest(r).
			WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully created download url")
		WriteJSON(w, struct {
			Link    string        `json:"link"`
			Expires time.Duration `json:"expires"`
		}{link, expires}, 200)
	}
}

// HandlePrefixDownloadLink returns an http.HandlerFunc that generates
// a signed link to download a zip containing the log files associated with that prefix.
func HandlePrefixDownloadLink(logStream stream.Stream, store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		st := time.Now()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		// accountID := r.FormValue(accountIDParam)
		prefix := r.FormValue(usePrefixParam)

		// key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))
		expires := time.Hour

		keys, err := store.ListBlobPrefix(ctx, prefix)
		if err != nil || len(keys) == 0 {
			err = fmt.Errorf("no keys found for prefix %s", prefix)
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("prefix", prefix).
				Errorln("api: unable to fetch prefixes")
			return
		}
		fmt.Println("prefix===", prefix)
		fmt.Println("keys====", keys)

		pr, pw := io.Pipe()
		defer pr.Close()
		br := bufio.NewReader(pr)
		// bwc := &BufioWriterCloser{pw, bufio.NewWriter(pw)}

		zipWriter := zip.NewWriter(pw)
		defer zipWriter.Close()

		fmt.Println("zipwriter created")
		g := new(errgroup.Group)
		g.Go(func() error {
			for _, key := range keys {
				fmt.Println("looping through keys")

				fmt.Println("Downloading start file===")

				//Download the file from store
				out, err := store.Download(ctx, key)
				if err != nil {
					return err
				}
				fmt.Println("Downloaded file===", out)

				filewriter, err := zipWriter.Create(key)
				if err != nil {
					return err
				}

				// // Add the current file to the zip archive
				io.Copy(filewriter, out)

			}
			zipWriter.Close()
			pw.Close()
			return nil
		})

		zipKey := prefix + "logs.zip"

		g.Go(func() error {
			//Upload thee zip archive to store
			fmt.Println("upload starting++++")

			return store.Upload(ctx, zipKey, br)
		})

		if err := g.Wait(); err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("prefix", prefix).
				Errorln("api: unable to upload zip to store")
			return
		}
		fmt.Println("Starting DownloadLink+++")

		link, err := store.DownloadLink(ctx, zipKey, expires)
		if err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("prefix", prefix).
				Errorln("api: cannot generate download url")
			return
		}

		logger.FromRequest(r).
			WithField("prefix", prefix).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully created download url")
		WriteJSON(w, struct {
			Link    string        `json:"link"`
			Expires time.Duration `json:"expires"`
		}{link, expires}, 200)
	}
}

// HandleDelete returns an http.HandlerFunc that deletes
// a blob from the datastore.
func HandleDelete(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		st := time.Now()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		if err := store.Delete(ctx, key); err != nil {
			WriteNotFound(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot delete object")
			return
		}

		logger.FromRequest(r).
			WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully deleted object")
		w.WriteHeader(http.StatusNoContent)
	}
}
