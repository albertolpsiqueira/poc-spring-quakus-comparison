#!/bin/bash
# Benchmark do quarkus-crud em modo nativo.
# Resultado em results-native/summary.csv
# Uso: ROUNDS=1 ./run-native.sh

APPS=("quarkus-crud-native:8083")
TESTS=("read" "write" "list")
ROUNDS=${ROUNDS:-3}
HOST=127.0.0.1

mkdir -p results-native
echo "app,round,test,startup_ms,mem_start,mem_end,req_per_s,avg_ms,p95_ms,p99_ms,fail_rate" > results-native/summary.csv

docker rm -f quarkus-crud-native >/dev/null 2>&1

for round in $(seq 1 $ROUNDS); do
  for entry in "${APPS[@]}"; do
    APP=${entry%%:*}
    PORT=${entry##*:}

    for TEST in "${TESTS[@]}"; do
      echo ">>> rodada $round | $APP | $TEST"
      docker rm -f $APP >/dev/null 2>&1

      # startup: do docker run ate a primeira resposta 200
      start=$(date +%s%N)
      docker run -d --name $APP --cpus=2 --memory=1g -p $PORT:$PORT $APP >/dev/null

      tries=0
      until curl -sf -o /dev/null http://$HOST:$PORT/users; do
        sleep 0.05
        tries=$((tries+1))
        if [ $tries -gt 1200 ]; then
          echo "ERRO: $APP nao subiu em 60s"
          docker logs $APP | tail -20
          exit 1
        fi
      done
      end=$(date +%s%N)
      startup=$(( (end-start)/1000000 ))

      mem_start=$(docker stats --no-stream --format "{{.MemUsage}}" $APP | awk '{print $1}')

      OUT=results-native/${APP}-${TEST}-r${round}.json
      k6 run -e BASE_URL=http://$HOST:$PORT -e TEST=$TEST \
        --summary-trend-stats="avg,med,p(90),p(95),p(99),max" \
        --summary-export=$OUT perf.js > results-native/${APP}-${TEST}-r${round}.log 2>&1

      mem_end=$(docker stats --no-stream --format "{{.MemUsage}}" $APP | awk '{print $1}')

      rps=$(jq -r '.metrics["http_reqs{phase:measure}"].rate // "n/a"' $OUT)
      avg=$(jq -r '.metrics["http_req_duration{phase:measure}"].avg // "n/a"' $OUT)
      p95=$(jq -r '.metrics["http_req_duration{phase:measure}"]["p(95)"] // "n/a"' $OUT)
      p99=$(jq -r '.metrics["http_req_duration{phase:measure}"]["p(99)"] // "n/a"' $OUT)
      fail=$(jq -r '.metrics["http_req_failed{phase:measure}"].value // "n/a"' $OUT)

      echo "$APP,$round,$TEST,$startup,$mem_start,$mem_end,$rps,$avg,$p95,$p99,$fail" >> results-native/summary.csv

      docker rm -f $APP >/dev/null 2>&1
      sleep 10   # deixa a maquina esfriar antes do proximo
    done
  done
done

echo "Pronto: results-native/summary.csv"
