package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestFailureAndRecovery(t *testing.T) {
	var status atomic.Int64
	status.Store(200)
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(int(status.Load())) }))
	defer upstream.Close()
	p := &probeState{}
	assertHealth := func(expected int) {
		t.Helper()
		w := httptest.NewRecorder()
		p.handler().ServeHTTP(w, httptest.NewRequest("GET", "/healthz", nil))
		if w.Code != expected {
			t.Fatalf("health status = %d, want %d", w.Code, expected)
		}
	}
	assertHealth(503)
	p.check(context.Background(), upstream.Client(), upstream.URL)
	assertHealth(200)
	status.Store(503)
	p.check(context.Background(), upstream.Client(), upstream.URL)
	assertHealth(503)
	status.Store(200)
	p.check(context.Background(), upstream.Client(), upstream.URL)
	assertHealth(200)
	w := httptest.NewRecorder()
	p.handler().ServeHTTP(w, httptest.NewRequest("GET", "/metrics", nil))
	for _, metric := range []string{"payguard_sidecar_up 1\n", "payguard_sidecar_checks_total 3\n", "payguard_sidecar_failures_total 1\n"} {
		if !strings.Contains(w.Body.String(), metric) {
			t.Fatalf("missing metric %q: %s", metric, w.Body.String())
		}
	}
}

func TestInvalidTargetAndTimeout(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { <-r.Context().Done() }))
	defer upstream.Close()
	for _, target := range []string{":invalid", upstream.URL} {
		p := &probeState{}
		p.check(context.Background(), &http.Client{Timeout: 20 * time.Millisecond}, target)
		if p.up.Load() != 0 || p.failures.Load() != 1 {
			t.Fatalf("failure not recorded for %q", target)
		}
	}
}

func TestMethodsAndUnknownRoutes(t *testing.T) {
	p := &probeState{}
	for _, path := range []string{"/healthz", "/metrics"} {
		w := httptest.NewRecorder()
		p.handler().ServeHTTP(w, httptest.NewRequest("POST", path, nil))
		if w.Code != 405 {
			t.Fatalf("POST %s = %d", path, w.Code)
		}
	}
	w := httptest.NewRecorder()
	p.handler().ServeHTTP(w, httptest.NewRequest("GET", "/unknown", nil))
	if w.Code != 404 {
		t.Fatalf("unknown route = %d", w.Code)
	}
}
