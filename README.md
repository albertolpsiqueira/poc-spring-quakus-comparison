# POC de performance: Spring Boot vs Spring no Quarkus vs Quarkus

Comparação de performance entre três implementações da **mesma API CRUD de usuários**, todas executadas em **Oracle GraalVM (JDK 25) em modo JVM**, dentro de containers Docker com os mesmos limites de CPU e memória.

| # | Projeto | O que é | Porta | Imagem Docker |
|---|---------|---------|-------|---------------|
| 1 | `spring-boot-crud` | Spring Boot 4 (Spring MVC + Spring Data JPA) | 8081 | `spring-boot-crud` |
| 2 | `quarkus-spring-crud` | API do Spring (`@RestController`, `JpaRepository`) sobre o runtime Quarkus, via extensões `quarkus-spring-*` | 8082 | `quarkus-spring-crud` |
| 3 | `quarkus-crud` | Quarkus nativo de API (Jakarta REST, CDI, Hibernate ORM com Panache) | 8083 | `quarkus-crud` |

> **Importante:** o cenário 2 **não é Spring Boot rodando dentro do Quarkus**. É a API e as anotações do Spring sobre a camada de compatibilidade do Quarkus, que executa tudo por baixo (Arc/CDI, Quarkus REST, Hibernate ORM). Ao divulgar resultados, descreva-o como "API Spring sobre o runtime Quarkus".

Métricas coletadas: **tempo de startup**, **memória (início e após a carga)**, **throughput (req/s)** e **latência (média, p95, p99)** em três cenários de carga: leitura por id, escrita e listagem.

## Sumário

1. [Estrutura do repositório](#1-estrutura-do-repositório)
2. [Ambiente de referência](#2-ambiente-de-referência)
3. [Pré-requisitos](#3-pré-requisitos)
4. [A API](#4-a-api)
5. [Gerar os artefatos](#5-gerar-os-artefatos)
6. [Construir as imagens Docker](#6-construir-as-imagens-docker)
7. [Validar a equivalência das APIs](#7-validar-a-equivalência-das-apis)
8. [Rodar o benchmark](#8-rodar-o-benchmark)
9. [Ler os resultados](#9-ler-os-resultados)
10. [Regras para uma comparação justa](#10-regras-para-uma-comparação-justa)
11. [Configuração dos projetos](#11-configuração-dos-projetos)
12. [Problemas comuns](#12-problemas-comuns)
13. [Limitações](#13-limitações)
14. [Resultados](#14-resultados)
15. [Resultados do Quarkus nativo](#15-resultados-do-quarkus-nativo)

---

## 1. Estrutura do repositório

```
.
├── README.md
├── perf.js                  # script de carga do k6
├── run-all.sh               # automatiza todas as rodadas e gera o CSV
├── results/                 # criado pelo run-all.sh
├── spring-boot-crud/             # cenário 1
│   ├── Dockerfile
│   └── src/...
├── quarkus-spring-crud/     # cenário 2
│   ├── Dockerfile
│   └── src/...
└── quarkus-crud/            # cenário 3
    ├── Dockerfile
    └── src/...
```

O nome da **pasta** e o nome da **imagem Docker** são coisas separadas. Só o nome da imagem precisa bater com a lista `APPS` do `run-all.sh`.

## 2. Ambiente de referência

Os números deste projeto foram obtidos com o runtime abaixo. Todos os containers usam a mesma imagem base.

**Runtime de execução (dentro dos containers, idêntico nos três apps):**

```
java version "25.0.4.1.1" 2026-08-18 LTS
Java(TM) SE Runtime Environment Oracle GraalVM 25.0.4.1.1+1.1 (build 25.0.4.1.1+1-LTS-jvmci-b01)
Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 25.0.4.1.1+1.1 (build 25.0.4.1.1+1-LTS-jvmci-b01, mixed mode, sharing)
```

- Imagem base: `container-registry.oracle.com/graalvm/jdk:25`
- Edição: Oracle GraalVM (licença GFTC, gratuita para uso em produção)
- Modo: JVM comum (JAR), com o compilador Graal no lugar do C2 do HotSpot
- Limites por container: `--cpus=2 --memory=1g`, `-Xms512m -Xmx512m`
- Banco: H2 em memória, pool de 10 conexões

**JDK usado para compilar (host):**

```
java version "25.0.4.1.1" 2026-08-18 LTS
Java(TM) SE Runtime Environment Oracle GraalVM 25.4.4.1.1+1.1 (build 25.0.4.1.1+1-LTS-jvmci-25.4-b23)
Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 25.4.4.1.1+1.1 (build 25.0.4.1.1+1-LTS-jvmci-25.4-b23, mixed mode, sharing)
```

O JDK do host só compila. Quem executa o benchmark é o JDK do container. Os dois são Java 25 (mesmo formato de bytecode), por isso a diferença de numeração interna do GraalVM não afeta os resultados.

## 3. Pré-requisitos

| Ferramenta | Uso |
|---|---|
| **Oracle GraalVM JDK 25** | compilar os projetos |
| **Maven** (cada projeto tem o `./mvnw`) | compilar |
| **Docker Engine** | rodar os apps com limites iguais |
| **k6** | gerar carga (https://k6.io/docs/get-started/installation) |
| **jq** | extrair métricas dos JSONs do k6 |
| **curl** | testes de equivalência |

### Oracle GraalVM com SDKMAN

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

sdk install java 25.4.4+1-graal
sdk use java 25.4.4+1-graal
java -version
```

O sufixo `-graal` é o Oracle GraalVM. O sufixo `-graalce` é a edição Community, outro produto. Se a versão acima não estiver mais disponível, liste as opções com `sdk list java | grep -i graal` e use uma build Java 25 do Oracle GraalVM.

### Docker Engine

Use o **Docker Engine nativo**. O Docker Desktop no Linux roda numa VM (QEMU) que pode cair durante testes longos, o que interrompe o benchmark.

**Fedora:**

```bash
sudo dnf -y install dnf-plugins-core
sudo dnf config-manager addrepo --from-repofile=https://download.docker.com/linux/fedora/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER && newgrp docker
docker context use default
docker run --rm hello-world
```

Em versões mais antigas do Fedora, troque o segundo comando por `sudo dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo`.

**Ubuntu/Debian:** siga https://docs.docker.com/engine/install/ubuntu/ e depois execute `sudo usermod -aG docker $USER`, `newgrp docker` e `docker context use default`.

Se o Docker Desktop estiver instalado, desative-o para não haver conflito de contexto:

```bash
systemctl --user stop docker-desktop
systemctl --user disable docker-desktop
unset DOCKER_HOST
docker context use default
```

### jq e k6

```bash
sudo dnf install -y jq        # Fedora
sudo apt install -y jq        # Ubuntu/Debian
```

Para o k6, siga as instruções oficiais de instalação do link da tabela.

## 4. A API

Os três projetos expõem o mesmo contrato em `/users`:

| Método | Rota | Sucesso | Erros |
|---|---|---|---|
| `POST` | `/users` | 201 | 400 (validação), 409 (e-mail duplicado) |
| `GET` | `/users` | 200 | |
| `GET` | `/users/{id}` | 200 | 404 |
| `PUT` | `/users/{id}` | 200 | 400, 404, 409 |
| `DELETE` | `/users/{id}` | 204 | 404 |

Corpo de criação:

```json
{ "name": "Ana", "email": "ana@x.com" }
```

Resposta de validação (400), idêntica nos três projetos (a ordem das chaves pode variar):

```json
{ "name": "The name cannot be blank.", "email": "Invalid email format" }
```

## 5. Gerar os artefatos

Em **cada** projeto, com o GraalVM ativo:

```bash
cd spring-boot-crud && ./mvnw clean package -DskipTests && cd ..
cd quarkus-spring-crud && ./mvnw clean package -DskipTests && cd ..
cd quarkus-crud && ./mvnw clean package -DskipTests && cd ..
```

Confira:

- Spring: `spring-boot-crud/target/*.jar` (ignore o `.jar.original`, se existir).
- Quarkus (os dois): `target/quarkus-app/quarkus-run.jar`, junto das pastas `lib`, `app` e `quarkus`. A pasta `quarkus-app` inteira é necessária; o `quarkus-run.jar` sozinho não roda.

## 6. Construir as imagens Docker

Os três Dockerfiles usam a mesma imagem base.

**`spring-boot-crud/Dockerfile`:**

```dockerfile
FROM container-registry.oracle.com/graalvm/jdk:25
COPY target/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**`quarkus-spring-crud/Dockerfile`:**

```dockerfile
FROM container-registry.oracle.com/graalvm/jdk:25
COPY target/quarkus-app/ /app/
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
```

**`quarkus-crud/Dockerfile`:**

```dockerfile
FROM container-registry.oracle.com/graalvm/jdk:25
COPY target/quarkus-app/ /app/
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
```

Construa as imagens (os nomes precisam ser exatamente estes, pois o `run-all.sh` os usa):

```bash
docker build --no-cache -t spring-boot-crud ./spring-boot-crud
docker build --no-cache -t quarkus-spring-crud ./quarkus-spring-crud
docker build --no-cache -t quarkus-crud ./quarkus-crud
docker images | grep crud
```

Confirme que os três containers rodam o mesmo runtime:

```bash
for i in spring-boot-crud quarkus-spring-crud quarkus-crud; do
  docker run --rm --entrypoint java $i -version
done
```

A saída precisa ser idêntica nos três e igual à da seção 2.

### Rodar um app manualmente (opcional)

Com os mesmos limites do benchmark:

```bash
docker run -d --name spring-boot-crud \
  --cpus=2 --memory=1g -p 8081:8081 \
  -e JAVA_TOOL_OPTIONS="-Xms512m -Xmx512m" \
  spring-boot-crud

docker logs -f spring-boot-crud      # Ctrl+C para sair
docker rm -f spring-boot-crud        # remover ao terminar
```

Para os outros, troque o nome, a imagem e a porta (8082 ou 8083).

## 7. Validar a equivalência das APIs

Antes de medir, confirme que as três respondem **igual**. Suba um app por vez (seção 6) e rode, trocando a porta (8081, 8082 ou 8083):

```bash
PORT=8081
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:$PORT/users -H "Content-Type: application/json" -d '{"name":"Ana","email":"ana@x.com"}'   # 201
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:$PORT/users -H "Content-Type: application/json" -d '{"name":"Ana","email":"ana@x.com"}'   # 409
curl -s -w "\n%{http_code}\n" -X POST localhost:$PORT/users -H "Content-Type: application/json" -d '{"name":"","email":"invalido"}'                    # 400 + corpo
curl -s -o /dev/null -w "%{http_code}\n" localhost:$PORT/users/999                                                                                     # 404
```

Resultado esperado nos três: **201, 409, 400 (com o corpo da seção 4) e 404**. Só siga para a medição se forem iguais.

## 8. Rodar o benchmark

### 8.1 Preparação

- Nenhum container da POC rodando: `docker ps`
- Portas livres: `sudo ss -ltnp | grep -E "8081|8082|8083"` (sem saída = livres)
- `perf.js` e `run-all.sh` na raiz do repositório, com `chmod +x run-all.sh`
- Feche navegador pesado, IDE e qualquer outro consumidor de CPU
- Rode apenas **um app por vez**; o `run-all.sh` já faz isso

### 8.2 O script de carga (`perf.js`)

Para cada app e cenário o k6 executa:

1. **Seed:** cria usuários iniciais (1000 para `read` e `write`, 100 para `list`).
2. **Warm-up:** 20 VUs por 60 s. Aquece o JIT e não entra nas métricas.
3. **Medição:** 50 VUs por 120 s (fase `measure`).

| `TEST` | Operação |
|---|---|
| `read` | `GET /users/{id}` com id aleatório entre os criados |
| `write` | `POST /users` com e-mail único |
| `list` | `GET /users` (100 registros, para o JSON não dominar o teste) |

O bloco `thresholds` do `perf.js` precisa conter `'http_reqs{phase:measure}': ['count>0']`. Sem ele o k6 não exporta o req/s da fase medida e a coluna sai como `n/a`.

Para rodar manualmente um único teste:

```bash
k6 run -e BASE_URL=http://127.0.0.1:8081 -e TEST=read --summary-export=teste.json perf.js
jq '.metrics | keys' teste.json     # deve listar http_reqs{phase:measure}
```

### 8.3 O script de automação (`run-all.sh`)

Para cada rodada, app e cenário, o script:

1. remove qualquer container anterior e sobe um novo (o H2 sempre começa vazio);
2. mede o **startup** (do `docker run` até a primeira resposta 200);
3. registra a **memória inicial** (`docker stats`);
4. executa o k6;
5. registra a **memória após a carga**;
6. extrai req/s, média, p95, p99 e taxa de falha;
7. grava uma linha em `results/summary.csv` e espera 10 s antes do próximo teste.

A lista de apps no início do script usa os nomes das imagens:

```bash
APPS=("spring-boot-crud:8081" "quarkus-spring-crud:8082" "quarkus-crud:8083")
```

### 8.4 Executando

Rodada única (9 testes de ~3,5 min cada, cerca de **30 a 35 minutos**):

```bash
rm -rf results
ROUNDS=1 ./run-all.sh
```

Benchmark completo (3 rodadas, cerca de **1h30 a 1h45**):

```bash
rm -rf results
ROUNDS=3 ./run-all.sh
```

O terminal fica quieto durante cada teste, porque a saída do k6 vai para `results/*.log`. Para acompanhar, em outro terminal:

```bash
tail -f results/*-r1.log
docker stats
```

Para validar o script rapidamente, reduza temporariamente as durações no `perf.js` (`60s` para `10s`, `120s` para `20s`, `startTime: '65s'` para `'15s'`) e volte aos valores originais para a medição real.

## 9. Ler os resultados

```bash
column -s, -t results/summary.csv
```

| Coluna | Significado |
|---|---|
| `app`, `round`, `test` | identificação da execução |
| `startup_ms` | tempo até a primeira resposta 200 |
| `mem_start`, `mem_end` | memória do container no início e após a carga |
| `req_per_s` | throughput na fase medida (maior é melhor) |
| `avg_ms`, `p95_ms`, `p99_ms` | latência (menor é melhor) |
| `fail_rate` | deve ser `0`; se não for, a linha não é comparável |

Abra o CSV em uma planilha e use a **mediana** (`MEDIAN`) das rodadas para cada app e teste. Tamanho dos artefatos:

```bash
ls -lh spring-boot-crud/target/*.jar
du -sh quarkus-spring-crud/target/quarkus-app quarkus-crud/target/quarkus-app
```

### Comparando JDKs (opcional)

Para comparar o mesmo benchmark em dois JDKs (por exemplo Oracle GraalVM vs Temurin), rode o benchmark completo para um JDK, guarde a pasta e repita com o outro:

```bash
ROUNDS=3 ./run-all.sh
mv results results-graalvm

# troque o FROM nos 3 Dockerfiles (ex.: eclipse-temurin:25-jre),
# reconstrua as imagens com --no-cache e rode de novo
ROUNDS=3 ./run-all.sh
mv results results-temurin
```

Nunca misture linhas de JDKs diferentes na mesma tabela de medianas.

### Como interpretar

- Olhe throughput, latência p95/p99, startup e memória **em conjunto**. A média esconde picos.
- Variação entre rodadas acima de ~5% indica interferência (outro processo, máquina esquentando). Repita.
- Se os três empatarem em req/s e a CPU do container não estiver no limite (`docker stats` durante a carga), o gargalo provavelmente é o H2 ou o k6. Aumente os VUs (100 ou 200) no `perf.js`.

## 10. Regras para uma comparação justa

- **Um app por vez**, máquina ociosa. Nunca rode os três juntos.
- Mesma imagem base (Oracle GraalVM 25) e mesmo `java -version` nos três containers.
- Mesmos limites: `--cpus=2 --memory=1g` e `-Xms512m -Xmx512m`.
- Mesmo banco (H2 em memória), mesmo pool (10) e mesma estratégia de schema.
- Rode o JAR de produção, nunca `quarkus:dev` nem `spring-boot:run`.
- Warm-up antes de medir, 3 a 5 rodadas e mediana.
- Reinicie o container entre os testes.
- Confirme a equivalência das APIs (seção 7) antes de medir.
- O k6 roda fora dos containers; idealmente em outra máquina.

## 11. Configuração dos projetos

**Spring Boot (`application.properties`):**

```properties
server.port=8081
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.hikari.maximum-pool-size=10
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.open-in-view=false
spring.jpa.show-sql=false
```

**Projetos Quarkus (mudando só a porta: 8082 e 8083):**

```properties
quarkus.http.port=8082
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb
quarkus.datasource.username=sa
quarkus.datasource.jdbc.max-size=10
quarkus.hibernate-orm.schema-management.strategy=drop-and-create
quarkus.hibernate-orm.log.sql=false
```

### Diferenças de código entre os projetos

- **Validação:** `jakarta.validation` nos três (não `javax.validation`). No Spring Boot, a dependência é `spring-boot-starter-validation`; nos projetos Quarkus, a extensão `quarkus-hibernate-validator`.
- **Tratamento de erros:** no Spring, `@RestControllerAdvice` com `MethodArgumentNotValidException`. No Quarkus, a validação lança `ConstraintViolationException`. No Quarkus puro, os handlers usam `@ServerExceptionMapper`.
- **Transações:** no Spring, `@Transactional(readOnly = true)` nas leituras. No Quarkus, `jakarta.transaction.Transactional`, que **não tem `readOnly`**; as leituras ficam sem transação explícita. É uma diferença real de comportamento.
- **Persistência:** no Quarkus puro não há `save()`. Usa-se `persist`, e a entidade gerenciada é atualizada no commit.
- **Repositório:** Spring Data JPA (`JpaRepository`) nos cenários 1 e 2; Panache (`PanacheRepository`) no cenário 3.

## 12. Problemas comuns

| Sintoma | Causa e solução |
|---|---|
| `Unable to find image 'xxx:latest' locally` / `pull access denied` | O nome da imagem não bate com o `APPS` do `run-all.sh`. Confira `docker images \| grep crud` |
| `pull access denied` ao construir com `container-registry.oracle.com/...` | Teste `docker pull container-registry.oracle.com/graalvm/jdk:25`. Se persistir, use a Community (`ghcr.io/graalvm/jdk-community:25`) nos três Dockerfiles |
| `bind: address already in use` | Porta ocupada. `sudo ss -ltnp \| grep 8081`; remova containers antigos com `docker rm -f spring-boot-crud quarkus-spring-crud quarkus-crud` ou encerre o processo Java |
| `the body is null so we can't transform it to JSON` (k6, no `setup`) | O app não está acessível no `BASE_URL`. Confira `docker ps`, a porta e use `127.0.0.1` em vez de `localhost` |
| `failed to connect to the docker API at ...desktop/docker.sock` | O CLI aponta para o Docker Desktop. `unset DOCKER_HOST` e `docker context use default` |
| `qemu: process terminated unexpectedly` | A VM do Docker Desktop caiu. Use o Docker Engine nativo (seção 3) |
| `permission denied ... docker.sock` | `sudo usermod -aG docker $USER` e novo login (ou `newgrp docker`) |
| `COPY failed: no source files` | Faltou `./mvnw clean package` no projeto |
| `Unsupported class file version` | O Java da imagem é mais antigo que o do build |
| `java -version` diferente entre as imagens | Reconstrua as três com `--no-cache` e confira a linha `FROM` de cada Dockerfile |
| `UnsatisfiedResolutionException` (Quarkus) | `UserRepository` sem `@ApplicationScoped`, extensão ausente ou import de outro pacote |
| `@Valid` não valida nada | Falta `spring-boot-starter-validation` (Spring) ou `quarkus-hibernate-validator` (Quarkus), ou ainda há imports `javax.validation` |
| Corpo do 400 no formato padrão do Quarkus (`title`, `violations`) | Use um `ExceptionMapper<ConstraintViolationException>` com `@Provider` que devolva `{campo: mensagem}` |
| Colunas `n/a` no CSV | Falta a linha `http_reqs{phase:measure}` nos thresholds do `perf.js`, ou o `jq` não está instalado |
| `cd: pasta: No such file or directory` | Rode `ls` na raiz do repositório e use o nome real da pasta |

## 13. Limitações

- O teste é um **CRUD simples com H2 em memória**. Em escrita, o H2 pode virar o gargalo e aproximar os três resultados. Não generalize para bancos reais ou lógica pesada.
- O cenário 2 é "API Spring sobre o runtime Quarkus", não Spring Boot dentro do Quarkus. A camada de compatibilidade cobre apenas um subconjunto do Spring.
- Os testes usam **Oracle GraalVM em modo JVM**, com o compilador Graal. O aquecimento e o pico de throughput podem diferir de outros JDKs (HotSpot/C2), para melhor ou para pior. Os resultados valem para o runtime da seção 2.
- Esta POC não inclui **native image**. Imagens nativas têm startup e memória muito melhores, mas build lento e, às vezes, menor throughput de pico; deveriam entrar como uma quarta coluna, separada.
- O `findAll` não tem paginação, por isso o cenário `list` usa só 100 registros.
- Os números absolutos dependem do hardware, do kernel, do Docker e das versões do Spring Boot e do Quarkus. A comparação relativa é válida quando todas as condições da seção 10 são respeitadas.

## 14. Resultados

Execução de referência: **3 rodadas** do `run-all.sh`, com 50 VUs na fase de medição (120 s) após 60 s de warm-up, no ambiente da seção 2. Os valores abaixo são a **mediana** das rodadas. Os dados brutos de cada rodada estão em `results/summary.csv`. A taxa de falha (`fail_rate`) foi **0** em todas as execuções.

### Startup e memória

| Métrica | Spring Boot | Spring no Quarkus | Quarkus |
|---|---:|---:|---:|
| Startup até a 1ª resposta 200 (ms) | 5616 | 2216 | 2132 |
| Memória logo após subir (MiB) | 349,6 | 233,6 | 204,1 |
| Memória após a carga (MiB) | 427,6 | 386,8 | 377,9 |

Startup e memória são a mediana das 9 execuções de cada app (3 cenários × 3 rodadas).

### Throughput e latência

**Leitura por id (`GET /users/{id}`)**

| Métrica | Spring Boot | Spring no Quarkus | Quarkus |
|---|---:|---:|---:|
| req/s | 12071 | 15997 | 21944 |
| Latência média (ms) | 2,55 | 1,91 | 1,37 |
| p95 (ms) | 5,29 | 3,98 | 2,86 |
| p99 (ms) | 23,17 | 12,85 | 4,41 |

**Escrita (`POST /users`)**

| Métrica | Spring Boot | Spring no Quarkus | Quarkus |
|---|---:|---:|---:|
| req/s | 1460 | 757 | 697 |
| Latência média (ms) | 21,56 | 41,81 | 37,16 |
| p95 (ms) | 82,32 | 91,85 | 88,06 |
| p99 (ms) | 96,97 | 101,63 | 97,34 |

**Listagem (`GET /users`, 100 registros)**

| Métrica | Spring Boot | Spring no Quarkus | Quarkus |
|---|---:|---:|---:|
| req/s | 941 | 1458 | 2800 |
| Latência média (ms) | 34,04 | 22,00 | 11,37 |
| p95 (ms) | 88,24 | 76,75 | 61,43 |
| p99 (ms) | 101,39 | 83,91 | 70,70 |

### Leitura dos resultados

- **Startup:** os dois cenários Quarkus sobem em cerca de 2,1 a 2,2 s, contra 5,6 s do Spring Boot (aproximadamente 2,5 vezes mais rápido).
- **Memória:** o Quarkus usa menos memória logo após subir (204 MiB contra 350 MiB do Spring Boot). Após a carga a diferença diminui (378 a 428 MiB), porque os três containers rodam com `-Xms512m -Xmx512m`.
- **Leitura:** o Quarkus entregou cerca de 1,8 vez o throughput do Spring Boot, com p99 bem menor (4,4 ms contra 23,2 ms). O Spring no Quarkus ficou no meio (cerca de 1,3 vez).
- **Listagem:** o Quarkus entregou cerca de 3 vezes o throughput do Spring Boot. O Spring no Quarkus ficou novamente no meio (cerca de 1,5 vez).
- **Escrita:** o Spring Boot teve a maior mediana de throughput. Esse resultado **não é conclusivo**: a variação entre rodadas foi grande (o Spring Boot oscilou entre 895 e 2099 req/s) e os três apps tiveram p95 parecido (82 a 92 ms), o que sugere um limite comum, provavelmente o H2 em memória.

### Notas sobre os dados

- **req/s absoluto:** o valor vem do campo `rate` do k6. Em todas as execuções válidas ele ficou em 60% a 64% do máximo teórico para 50 VUs (50 dividido pela latência média), o que indica que o k6 divide a contagem de requisições por uma duração maior que os 120 s da fase de medição. O fator é semelhante nos três apps, então a **comparação relativa vale**, mas os valores absolutos de req/s estão subestimados e não devem ser comparados com outros benchmarks.
- **Execução anômala:** na rodada 3 do `quarkus-crud` em escrita, o k6 reportou 7,09 req/s com latência média normal (33,6 ms), o que é inconsistente. A mediana absorve esse valor, mas ele deve ser tratado como inválido. Repita esse teste isolado para confirmar.
- Os resultados valem para o runtime da seção 2 (Oracle GraalVM em modo JVM) e para o ambiente em que foram medidos.

## 15. Resultados do Quarkus nativo

Execução do `quarkus-crud` compilado como **executável nativo**, em 3 rodadas do `run-native.sh`, com o mesmo `perf.js` (60 s de warm-up e 50 VUs por 120 s) e os mesmos limites de container (`--cpus=2 --memory=1g`). Os valores são a **mediana** das rodadas. Os dados brutos estão em `results-native/summary.csv`. A taxa de falha foi **0** em todas as execuções.

### Como reproduzir

1. Anote com `@RegisterForReflection` os DTOs de saída `UserResponseDTO` e `ErrorResponseDTO` (importe `io.quarkus.runtime.annotations.RegisterForReflection`), porque eles são serializados dentro de `Response.entity(...)`.
2. Gere o executável nativo dentro de `quarkus-crud`:
   ```bash
   ./mvnw clean package -Dnative -Dquarkus.native.container-build=true -DskipTests
   ls -lh target/*-runner
   ```
3. Crie o `quarkus-crud/Dockerfile.native`. A imagem base precisa ser compatível com a glibc do builder; use a mesma indicada em `src/main/docker/Dockerfile.native-micro` do projeto (na versão do Quarkus usada aqui, UBI 9):
   ```dockerfile
   FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0
   WORKDIR /work/
   COPY target/*-runner /work/application
   RUN chmod 775 /work/application
   EXPOSE 8083
   ENTRYPOINT ["./application", "-Xms512m", "-Xmx512m", "-Dquarkus.http.host=0.0.0.0"]
   ```
4. Construa a imagem, valide a equivalência (seção 7, porta 8083) e rode o benchmark:
   ```bash
   docker build -f quarkus-crud/Dockerfile.native -t quarkus-crud-native ./quarkus-crud
   ROUNDS=3 ./run-native.sh
   ```

Observações sobre o build:

- O `container-build` usa por padrão o **Mandrel** (distribuição da Red Hat do GraalVM), e não o Oracle GraalVM das seções anteriores.
- O heap foi igualado ao dos testes em JVM com `-Xms512m -Xmx512m` no `ENTRYPOINT`, porque o `JAVA_TOOL_OPTIONS` não vale para executável nativo.
- O erro `GLIBC_2.3x not found` ao subir o container indica imagem base mais antiga que o builder; troque o `FROM`.

### Comparação com os testes em JVM

Os valores de JVM são os da seção 14. O Quarkus nativo foi medido em outra sessão, com o mesmo procedimento.

| Métrica | Spring Boot (JVM) | Spring no Quarkus (JVM) | Quarkus (JVM) | Quarkus (nativo) |
|---|---:|---:|---:|---:|
| Startup até a 1ª resposta 200 (ms) | 5616 | 2216 | 2132 | 414 |
| Memória logo após subir (MiB) | 349,6 | 233,6 | 204,1 | 12,4 |
| Memória após a carga (MiB) | 427,6 | 386,8 | 377,9 | 70,8 |

**Leitura por id (`GET /users/{id}`)**

| Métrica | Spring Boot (JVM) | Spring no Quarkus (JVM) | Quarkus (JVM) | Quarkus (nativo) |
|---|---:|---:|---:|---:|
| req/s | 12071 | 15997 | 21944 | 4427 |
| Latência média (ms) | 2,55 | 1,91 | 1,37 | 7,11 |
| p95 (ms) | 5,29 | 3,98 | 2,86 | 42,00 |
| p99 (ms) | 23,17 | 12,85 | 4,41 | 49,91 |

**Escrita (`POST /users`)**

| Métrica | Spring Boot (JVM) | Spring no Quarkus (JVM) | Quarkus (JVM) | Quarkus (nativo) |
|---|---:|---:|---:|---:|
| req/s | 1460 | 757 | 697 | 1377 |
| Latência média (ms) | 21,56 | 41,81 | 37,16 | 23,17 |
| p95 (ms) | 82,32 | 91,85 | 88,06 | 73,81 |
| p99 (ms) | 96,97 | 101,63 | 97,34 | 81,06 |

**Listagem (`GET /users`, 100 registros)**

| Métrica | Spring Boot (JVM) | Spring no Quarkus (JVM) | Quarkus (JVM) | Quarkus (nativo) |
|---|---:|---:|---:|---:|
| req/s | 941 | 1458 | 2800 | 983 |
| Latência média (ms) | 34,04 | 22,00 | 11,37 | 32,79 |
| p95 (ms) | 88,24 | 76,75 | 61,43 | 85,88 |
| p99 (ms) | 101,39 | 83,91 | 70,70 | 96,73 |

### Leitura dos resultados

- **Startup:** o nativo respondeu a primeira requisição em cerca de 0,4 s, contra 2,1 s do Quarkus em JVM (aproximadamente 5 vezes mais rápido) e 5,6 s do Spring Boot.
- **Memória:** logo após subir, o nativo usou cerca de 12 MiB, contra 204 MiB do Quarkus em JVM. Após a carga, a mediana foi 70,8 MiB (de 35 a 39 MiB em leitura, de 50 a 82 MiB em listagem e de 206 a 237 MiB em escrita), contra 378 MiB em JVM.
- **Leitura e listagem:** o nativo teve throughput menor que o Quarkus em JVM (cerca de 20% em leitura e 35% em listagem) e latências p95 e p99 bem maiores. Em listagem ficou próximo do Spring Boot em JVM. Os testes não investigaram a causa; o nativo não tem o compilador JIT, que otimiza o código durante a execução.
- **Escrita:** o nativo teve throughput próximo ao do Spring Boot e acima dos outros dois, com o menor p95. A escrita é limitada pelo H2 em memória e teve muita variação entre apps e rodadas na seção 14, então esse resultado não deve ser tratado como conclusivo.
- **Trade-off:** o nativo troca throughput de pico por startup e consumo de memória muito menores. A escolha depende do cenário (por exemplo, serverless e scale-to-zero favorecem o nativo; serviços de longa duração com carga alta favorecem a JVM).

### Notas sobre os dados

- **req/s absoluto:** como na seção 14, o valor do k6 ficou entre 63% e 65% do máximo teórico para 50 VUs, de forma consistente. Os valores absolutos estão subestimados, mas a comparação relativa vale.
- **Estabilidade:** a variação entre as 3 rodadas do nativo foi de até cerca de 6% em throughput, e não houve execução anômala.
- **Sessões diferentes:** os números em JVM e o do nativo foram medidos em sessões distintas. Para uma comparação direta, repita o `run-all.sh` e o `run-native.sh` em sequência, na mesma máquina e nas mesmas condições.
- Os resultados valem para o executável gerado com Mandrel, em Linux x86_64, no ambiente em que foram medidos.
