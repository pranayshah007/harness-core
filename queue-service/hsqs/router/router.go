// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package router

import (
	"errors"
	"fmt"
	"strings"

	"github.com/golang-jwt/jwt"
	"github.com/harness/harness-core/queue-service/hsqs/config"
	appdynamics "github.com/harness/harness-core/queue-service/hsqs/middleware"
	"github.com/labstack/echo-contrib/prometheus"
	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
	"github.com/labstack/gommon/log"
)

var secret string

func New(config *config.Config) *echo.Echo {
	e := echo.New()
	lvl := log.INFO
	secret = config.Secret
	envConfig := config
	if envConfig.Debug {
		lvl = log.DEBUG
	}

	jwtConfig := middleware.JWTConfig{
		ParseTokenFunc: getParseTokenFunc,
		Skipper:        skipperFunc,
	}

	e.Logger.SetLevel(lvl)
	e.Pre(middleware.RemoveTrailingSlash())
	e.Use(middleware.CORSWithConfig(middleware.CORSConfig{
		AllowOrigins: []string{"*"},
		AllowHeaders: []string{echo.HeaderOrigin, echo.HeaderContentType, echo.HeaderAccept, echo.HeaderAuthorization},
		AllowMethods: []string{echo.GET, echo.HEAD, echo.PUT, echo.PATCH, echo.POST, echo.DELETE},
	}))

	if envConfig.EnableHttpLogging {
		e.Use(middleware.Logger())
	}

	if envConfig.AppDynamicsConfig.Enabled {
		e.Use(AppDynamics())
	}

	// Disable auth when flag enabled
	if !envConfig.DisableAuth {
		e.Use(middleware.JWTWithConfig(jwtConfig))
	}

	p := prometheus.NewPrometheus("echo", urlSkipperFunc)

	p.Use(e)

	return e
}

func getParseTokenFunc(token string, c echo.Context) (interface{}, error) {
	tkn, err := jwt.Parse(token, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return []byte(secret), nil
	})

	if err != nil {
		return nil, err
	}

	if !tkn.Valid {
		return nil, errors.New("invalid Token")
	}

	claims, ok := tkn.Claims.(jwt.MapClaims)
	if ok {
		return claims, nil
	}

	return nil, errors.New("invalid token")
}

// skip url's from JWT Auth
func skipperFunc(c echo.Context) bool {
	if strings.Contains(c.Request().URL.Path, "swagger") {
		return true
	} else if strings.Contains(c.Request().URL.Path, "healthz") {
		return true
	} else if strings.Contains(c.Request().URL.Path, "metrics") {
		return true
	} else if strings.Contains(c.Request().URL.Path, "pprof") {
		return true
	} else if strings.Contains(c.Request().URL.Path, "graph") {
		return true
	} else if strings.Contains(c.Request().URL.Path, "version") {
		return true
	}
	return false
}

// skip url from prometheus metrics
func urlSkipperFunc(c echo.Context) bool {
	if strings.Contains(c.Request().URL.Path, "swagger") {
		return true
	} else if strings.Contains(c.Request().URL.Path, "healthz") {
		return true
	} else if strings.Contains(c.Request().URL.Path, "pprof") {
		return true
	}
	return false
}

// AppDynamics is tracing middleware for AppDynamics
func AppDynamics() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			// Don't want to trace health checks since this could get a bit spammy
			if strings.Contains(c.Request().URL.Path, "swagger") {
				return next(c)
			} else if strings.Contains(c.Request().URL.Path, "healthz") {
				return next(c)
			} else if strings.Contains(c.Request().URL.Path, "metrics") {
				return next(c)
			} else if strings.Contains(c.Request().URL.Path, "pprof") {
				return next(c)
			}

			c.SetRequest(appdynamics.StartBTForRequest(c.Request()))
			defer appdynamics.EndBTForRequest(c.Request())

			if err := next(c); err != nil {
				appdynamics.ReportBTError(c.Request(), err.Error())
				return err
			}

			return nil
		}
	}

}
