// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"archive/zip"
	"bufio"
	"context"
	"fmt"
	"github.com/harness/harness-core/product/log-service/stream"
	"io"
	"net/http"
	"os"
	"time"

	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/store"
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

// HandleDownload returns an http.HandlerFunc that downloads
// a blob from the datastore and copies to the http.Response.
func HandleListBlobWithPrefix(s store.Store, stream stream.Stream) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		st := time.Now()
		h := w.Header()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.FormValue(accountIDParam)
		prefix := ""
		if r.URL.Query().Get("prefix") != "" {
			prefix = r.URL.Query().Get("prefix")
		}

		out, _ := s.ListBlobPrefix(ctx, CreateAccountSeparatedKey(accountID, prefix))

		zipPrefix := prefix + "_logs.zip"

		go func(s store.Store) {
			fmt.Println("entrou na go routine")
			pipeRead, pipeWrite := io.Pipe()
			defer pipeRead.Close()
			//archive, err := os.Create("archive.zip")
			//if err != nil {
			//	panic(err)
			//}
			//defer archive.Close()
			//zipWriter := zip.NewWriter(archive)

			zipWriter := zip.NewWriter(pipeWrite)
			defer zipWriter.Close()

			fmt.Println("starting loop through keys")
			for batch, keys := range out {
				keys := keys
				batch := batch
				fmt.Println("starting download batch ", batch)
				for _, key := range keys {
					fileDownloaded, err := s.Download(context.Background(), key)
					if err != nil {
						fmt.Println("erro ao baixar arquivo")
						return
					}
					zipFile, err := zipWriter.Create(key)
					//if err != nil {
					//	fmt.Println("erro ao zipar arquivo")
					//	return
					//}
					_, err = io.Copy(zipFile, fileDownloaded)
					if err != nil {
						fmt.Println("erro ao copiar arquivo para o zip")
						return
					}
				}
			}
			//pipeWrite.Close()

			zipWriter.Close()
			pipeWrite.Close()
			//go func() {
			//br := bufio.NewReader(pipeRead)
			bufr := bufio.NewReader(pipeRead)
			//str, err := bufr.ReadString('\n')
			//fmt.Println("ReadString.err", err)
			//fmt.Println("ReadString str", str)
			_ = s.Upload(context.Background(), zipPrefix, bufr)
			//}()

			fmt.Println("terminou a go routine")

			os.Create("archive.zip")

			//zipWriter.Close()
			//pipeWrite.Close()
			zipWriter.Close()
			return
		}(s)

		link, err := s.DownloadLink(ctx, zipPrefix, time.Hour)
		if err != nil {
			WriteNotFound(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("prefix", prefix).
				Errorln("api: cannot generate the download url")
		} else {
			WriteJSON(w, struct {
				Link    string        `json:"link"`
				Expires time.Duration `json:"expires"`
			}{
				link, time.Hour,
			}, 200)
			//WriteJSON(w, struct {
			//	ListKeys map[int][]string `json:"keys"`
			//}{out}, 200)

			logger.FromRequest(r).
				WithField("prefix", prefix).
				WithField("latency", time.Since(st)).
				WithField("time", time.Now().Format(time.RFC3339)).
				Infoln("api: successfully downloaded object")
		}
	}
}
