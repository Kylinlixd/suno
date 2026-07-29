# 可执行仓库红色基线

于 `2026-07-29T14:56:16+0800` 在分支
`codex/professional-rearchitecture` 采集。本基线刻意保持红色，用于 Phase 0
模块化改造。不得将这些失败视为通过，也不得削弱仓库校验脚本以隐藏它们。

## 工具链

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

## `mvn test` 结果

退出码：`1`（编译失败）。测试数量：`0`；Maven 在测试阶段开始前的编译阶段停止。

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

## 仓库校验结果

在清理已有的受跟踪编译产物、不安全配置默认值和过时 README 脚本引用之前，
`./scripts/verify-repository.sh` 应当失败。脚本会在一次运行中报告当前每类违规。

采集时间：`2026-07-29T14:57:31+0800`；退出码：`1`。

```text
src/main/java/com/suno/mall/dto/response/RecycleOrderVO.class
Tracked build artifact detected
src/main/resources/application-mysql.yml:51:    secret: demo-payment-secret
src/main/resources/application.yml:63:    secret: demo-payment-secret
Unsafe configuration default detected
README references missing local script: ./scripts/run-tests-ci.sh
README references missing local script: ./scripts/run-tests.sh
```
