package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"sync/atomic"
	"time"
)

type probeState struct {
	up       atomic.Int64
	checks   atomic.Uint64
	failures atomic.Uint64
}

func (p *probeState) check(ctx context.Context, client *http.Client, target string) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	var response *http.Response
	if err == nil {
		response, err = client.Do(req)
	}
	p.checks.Add(1)
	if response != nil {
		defer response.Body.Close()
	}
	if err != nil || response == nil || response.StatusCode != http.StatusOK {
		p.up.Store(0)
		p.failures.Add(1)
	} else {
		p.up.Store(1)
	}
}

func (p *probeState) handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		if p.up.Load() != 1 {
			http.Error(w, "upstream unavailable", http.StatusServiceUnavailable)
			return
		}
		w.Write([]byte("ok\n"))
	})
	mux.HandleFunc("/metrics", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		w.Header().Set("Content-Type", "text/plain; version=0.0.4")
		fmt.Fprintf(w, "# TYPE payguard_sidecar_up gauge\npayguard_sidecar_up %d\n# TYPE payguard_sidecar_checks_total counter\npayguard_sidecar_checks_total %d\n# TYPE payguard_sidecar_failures_total counter\npayguard_sidecar_failures_total %d\n", p.up.Load(), p.checks.Load(), p.failures.Load())
	})
	return mux
}

func main() {
	target := os.Getenv("PAYGUARD_HEALTH_URL")
	if target == "" {
		target = "http://127.0.0.1:8081/actuator/health"
	}
	client := &http.Client{Timeout: 2 * time.Second}
	state := &probeState{}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		ticker := time.NewTicker(5 * time.Second)
		defer ticker.Stop()
		for {
			state.check(ctx, client, target)
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
			}
		}
	}()
	server := &http.Server{Addr: ":9101", Handler: state.handler(), ReadHeaderTimeout: 5 * time.Second}
	log.Fatal(server.ListenAndServe())
}
