// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"github.com/harness/harness-core/queue-service/hsqs/store"
	"github.com/labstack/echo/v4"
	"net/http"
)

type Handler struct {
	s store.Store
}

func NewHandler(s store.Store) *Handler {
	return &Handler{s: s}
}

func (h *Handler) Register(g *echo.Group) {
	g.POST("/queue", h.handleQueue())
	g.POST("/dequeue", h.handleDequeue())
}

// handleQueue godoc
// @Summary     Enqueue
// @Description Enqueue the request
// @Accept      json
// @Produce     json
// @Param       request body store.EnqueueRequest true "query params"
// @Success     200 {object} store.EnqueueResponse
// @Router      /v1/queue [POST]
func (h *Handler) handleQueue() echo.HandlerFunc {
	return func(c echo.Context) error {

		// bind request body to enqueue request
		p := &store.EnqueueRequest{}

		if err := c.Bind(p); err != nil {
			return c.JSON(http.StatusBadRequest, err)
		}

		enqueue, err := h.s.Enqueue(c.Request().Context(), *p)
		if err != nil {
			return c.JSON(http.StatusBadRequest, nil)
		}
		return c.JSON(http.StatusOK, enqueue)
	}
}

// handleDequeue godoc
// @Summary     Dequeue in Redis
// @Description Dequeue a request
// @Accept      json
// @Produce     json
// @Param        request body store.DequeueRequest true "query params"
// @Success     200 {object} store.DequeueResponse
// @Router      /v1/dequeue [POST]
func (h *Handler) handleDequeue() echo.HandlerFunc {
	return func(c echo.Context) error {
		dequeue, err := h.s.Dequeue(c.Request().Context(), store.DequeueRequest{})
		if err != nil {
			return c.JSON(http.StatusBadRequest, nil)
		}
		return c.JSON(http.StatusOK, dequeue)
	}
}
