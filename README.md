# WordGame

Cooperative wordgame @ [hotbling.wtf](https://hotbling.wtf).

Status: WIP

## Technology
This is a Kotlin Multiplatform project targeting Android, Web, Desktop, Server.
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [Kotlin/Wasm](https://kotl.in/wasm/)

## Code
- [commonMain](composeApp/src/commonMain/kotlin/wtf/hotbling/wordgame/)
- [server](server/src/main/kotlin/wtf/hotbling/wordgame/)

## Commands
- wasmJs (dev): `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
- desktop (dev jar): `./gradlew :composeApp:packageUberJarForCurrentOS && java -jar ./composeApp/build/compose/jars/wtf.hotbling.wordgame-linux-x64-1.0.0.jar`
- server: `./gradlew :server:run`
- server (dev jar): `./gradlew :server:buildFatJar && (cd server && java -jar -Dio.ktor.development=true ./build/libs/server-all.jar)`
- prod run: `./gradlew clean wasmJsBrowserDevelopmentExecutableDistribution :server:publishImageToLocalRegistry && docker compose up -d`
- prod push: `rsync -a --exclude build . root@hotbling.wtf:~/code/wordgame`

## TODO
- dev mode: file (maybe .nowords) to prevent reloading words when a single entry exists / disable 30 words limit
- don't return `word` via api until solved
- server-side error handling, in routes & services
- server: no busy loop
- full Android app (deeplink...)
- auth :)

## Bash Client

### Dev
```shell
export host=http://localhost:8080
export ws_host=ws://localhost:8080/ws
```

### Prod
```shell
export host=https://hotbling.wtf
export ws_host=wss://hotbling.wtf/ws
```

### Words Api
#### Random
```shell
curl "$host/api/v1/words/random"
```

#### Solution
```shell
curl "$host/api/v1/words/solution"
```

### Accounts Api
#### Create
```shell
output=$(curl -s -X PUT -d '{"name":"Meow"}' -H "Content-Type: application/json" "$host/api/v1/accounts")
echo "$output"
export account_id=$(jq -r '.data.id' <<< "$output")
echo "account_id: $account_id"
```

#### Update
```shell
output=$(curl -s -X PUT -d '{"id":"'$account_id'","name":"Cat"}' -H "Content-Type: application/json" "$host/api/v1/accounts")
echo "$output"
export account_id=$(jq -r '.data.id' <<< "$output")
echo "account_id: $account_id"
```

### Sessions Api
#### List
```shell
curl "$host/api/v1/sessions/by-account/$account_id"
```

#### Create
```shell
output=$(curl -s -d '{"account_id":"'$account_id'"}' -H "Content-Type: application/json" "$host/api/v1/sessions")
echo "$output"
export session_id=$(jq -r '.data.id' <<< "$output")
echo "session_id: $session_id"
```

### Guesses Api
#### Create
```shell
output=$(curl -s -d '{"session_id":"'$session_id'","account_id":"'$account_id'","txt":"hello"}' -H "Content-Type: application/json" "$host/api/v1/guesses")
echo "$output"
export guess_id=$(jq -r '.data.id' <<< "$output")
echo "guess_id: $guess_id"
```
