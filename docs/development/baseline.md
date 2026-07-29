# Executable repository baseline

Captured on `2026-07-29T14:56:16+0800` on branch
`codex/professional-rearchitecture`. This is intentionally a red baseline for
the Phase 0 modularization work. Do not treat these failures as passing checks
or weaken the repository verification script to hide them.

## Toolchain

```text
Apache Maven 3.9.15 (98b2cdbfdb5f1ac8781f537ea9acccaed7922349)
Maven home: /Users/leexd/.maven/maven-3.9.15
Java version: 25.0.3, vendor: Microsoft, runtime: /Library/Java/JavaVirtualMachines/microsoft-25.jdk/Contents/Home
Default locale: zh_CN_#Hans, platform encoding: UTF-8
OS name: "mac os x", version: "26.5.2", arch: "aarch64", family: "mac"

openjdk version "25.0.3" 2026-04-21 LTS
OpenJDK Runtime Environment Microsoft-13877172 (build 25.0.3+9-LTS)
OpenJDK 64-Bit Server VM Microsoft-13877172 (build 25.0.3+9-LTS, mixed mode, sharing)
```

## `mvn test` result

Exit code: `1` (compilation failure). Test count: `0`; Maven stopped in the
compile phase before the test phase could run.

```text
[INFO] --- compiler:3.14.0:compile (default-compile) @ suno-mall ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 135 source files with javac [debug parameters release 25] to target/classes
[INFO] -------------------------------------------------------------
[WARNING] COMPILATION WARNING :
[INFO] -------------------------------------------------------------
[WARNING] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/SecurityConfig.java:[91,46] org.springframework.security.authentication.dao.DaoAuthenticationProvider中的DaoAuthenticationProvider()已过时
[WARNING] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/SecurityConfig.java:[92,17] org.springframework.security.authentication.dao.DaoAuthenticationProvider中的setUserDetailsService(org.springframework.security.core.userdetails.UserDetailsService)已过时
[INFO] 2 warnings
[INFO] -------------------------------------------------------------
[INFO] -------------------------------------------------------------
[ERROR] COMPILATION ERROR :
[INFO] -------------------------------------------------------------
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/TransactionConfig.java:[19,8] com.suno.mall.config.TransactionConfig不是抽象的, 并且未覆盖org.springframework.transaction.annotation.TransactionManagementConfigurer中的抽象方法annotationDrivenTransactionManager()
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/TransactionConfig.java:[24,5] 方法不会覆盖或实现超类型的方法
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/dao/RecycleRepositoryTransactional.java:[29,52] 不兼容的类型: java.util.Optional<com.suno.mall.entity.RecycleOrderEntity>无法转换为com.suno.mall.entity.RecycleOrderEntity
[INFO] 3 errors
[INFO] -------------------------------------------------------------
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.720 s
[INFO] Finished at: 2026-07-29T14:56:23+08:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.14.0:compile (default-compile) on project suno-mall: Compilation failure: Compilation failure:
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/TransactionConfig.java:[19,8] com.suno.mall.config.TransactionConfig不是抽象的, 并且未覆盖org.springframework.transaction.annotation.TransactionManagementConfigurer中的抽象方法annotationDrivenTransactionManager()
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/TransactionConfig.java:[24,5] 方法不会覆盖或实现超类型的方法
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/dao/RecycleRepositoryTransactional.java:[29,52] 不兼容的类型: java.util.Optional<com.suno.mall.entity.RecycleOrderEntity>无法转换为com.suno.mall.entity.RecycleOrderEntity
[ERROR] -> [Help 1]
```

## Repository verification result

`./scripts/verify-repository.sh` is expected to fail until the existing
tracked compiled class, unsafe configuration defaults, and stale README script
references are cleaned up. The script reports every current category of
violation in one run.

Captured on `2026-07-29T14:57:31+0800`; exit code: `1`.

```text
src/main/java/com/suno/mall/dto/response/RecycleOrderVO.class
Tracked build artifact detected
src/main/resources/application-mysql.yml:51:    secret: demo-payment-secret
src/main/resources/application.yml:63:    secret: demo-payment-secret
Unsafe configuration default detected
README references missing local script: ./scripts/run-tests-ci.sh
README references missing local script: ./scripts/run-tests.sh
```
