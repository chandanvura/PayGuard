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

func main() {
 target := os.Getenv("PAYGUARD_HEALTH_URL")
 if target == "" { target = "http://127.0.0.1:8081/actuator/health" }
 client := &http.Client{Timeout: 2 * time.Second}
 var up atomic.Int64
 var checks atomic.Uint64
 var failures atomic.Uint64
 ctx, cancel := context.WithCancel(context.Background())
 defer cancel()
 go func() {
  ticker := time.NewTicker(5 * time.Second)
  defer ticker.Stop()
  for {
   req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
   if err != nil { log.Printf("invalid target: %v", err); return }
   response, err := client.Do(req)
   checks.Add(1)
   if err == nil {
    response.Body.Close()
   }
   if err != nil || response.StatusCode != http.StatusOK {
    up.Store(0)
    failures.Add(1)
   } else { up.Store(1) }
   select { case <-ctx.Done(): return; case <-ticker.C: }
  }
 }()
 mux := http.NewServeMux()
 mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
  if r.Method != http.MethodGet { http.Error(w, "method not allowed", http.StatusMethodNotAllowed); return }
  if up.Load() != 1 { http.Error(w, "upstream unavailable", http.StatusServiceUnavailable); return }
  w.Write([]byte("ok\n"))
 })
 mux.HandleFunc("/metrics", func(w http.ResponseWriter, r *http.Request) {
  if r.Method != http.MethodGet { http.Error(w, "method not allowed", http.StatusMethodNotAllowed); return }
  w.Header().Set("Content-Type", "text/plain; version=0.0.4")
  fmt.Fprintf(w, "# TYPE payguard_sidecar_up gauge\npayguard_sidecar_up %d\n# TYPE payguard_sidecar_checks_total counter\npayguard_sidecar_checks_total %d\n# TYPE payguard_sidecar_failures_total counter\npayguard_sidecar_failures_total %d\n", up.Load(), checks.Load(), failures.Load())
 })
 log.Fatal(http.ListenAndServe(":9101", mux))
}
