// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package router

import (
	"errors"
	"fmt"
	"github.com/golang-jwt/jwt"
	"github.com/harness/harness-core/queue-service/hsqs/config"
	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
	"github.com/labstack/gommon/log"
	"strings"
)

var envConfig config.Config

func New(config *config.Config) *echo.Echo {
	e := echo.New()
	lvl := log.INFO
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
	e.Use(middleware.Logger())
	e.Use(middleware.CORSWithConfig(middleware.CORSConfig{
		AllowOrigins: []string{"*"},
		AllowHeaders: []string{echo.HeaderOrigin, echo.HeaderContentType, echo.HeaderAccept, echo.HeaderAuthorization},
		AllowMethods: []string{echo.GET, echo.HEAD, echo.PUT, echo.PATCH, echo.POST, echo.DELETE},
	}))
	e.Use(middleware.JWTWithConfig(jwtConfig))

	return e
}

func getParseTokenFunc(token string, c echo.Context) (interface{}, error) {
	tkn, err := jwt.Parse(token, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return []byte(envConfig.Secret), nil
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

// skip swagger url's from JWT Auth
func skipperFunc(c echo.Context) bool {
	if strings.Contains(c.Request().URL.Path, "swagger") {
		return true
	} else if strings.Contains(c.Request().URL.Path, "healthz") {
		return true
	}
	return false
}
