# ADR 0001：运行时与构建基线

- 状态：已接受
- 日期：2026-07-29

## 背景

模块化单体迁移需要一个可复现、可执行且不会在迁移期间扩大框架升级面的构建基线。此前工程使用 Spring Boot 3.5.0，且依赖开发机上的 Maven；这会使补丁版本、构建器版本和依赖解析结果随环境漂移。

## 决策

1. 根父 POM 固定为 Spring Boot **3.5.16**，并让 `spring-boot-maven-plugin` 使用相同版本；不在本次模块化迁移中升级到 Spring Boot 4。
2. 以 Java **25** 为编译与执行基线，Maven 允许范围为 **[3.9.15,)**，并将 Java 执行范围限定为 **[25,26)**。根 POM 的 Enforcer 在每个 reactor 模块执行这些约束以及依赖收敛检查。
3. 通过 Maven Wrapper Plugin **3.3.4** 提交 wrapper 脚本。Wrapper 固定 Maven **3.9.15** 二进制 URL，并以 `distributionSha256Sum` 校验下载的 ZIP。
4. 在 `pluginManagement` 显式管理 Surefire/Failsafe **3.5.5**、JaCoCo **0.8.15**、Checkstyle **3.6.0**、Enforcer **3.6.3** 与 Spring Boot Maven Plugin **3.5.16**。编译器插件保留既有的明确版本 **3.14.0**；其有效 POM 已证明该版本正在生效。
5. Enforcer 使用 `dependencyConvergence`，并由 extra-enforcer-rules **1.12.0** 在 compile/runtime 范围执行 `banDuplicateClasses`；仅允许忽略字节码完全相同的重复类，不建立宽泛忽略清单。

## 理由与兼容性

Spring Boot 3.5.16 是现有 3.5 维护线的补丁升级：它保留当前 Spring Boot 3/Java 生态和模块边界，避免把 Spring Framework 7 / Boot 4 的兼容性迁移与模块化工作混在同一变更中。Java 25 是本仓库当前的 `release`，而上界 26 用来防止未经评估的下一代 JDK 被悄然采用。Maven 的下界与 wrapper 发行版一致，使 CI 和开发机以同一 Maven 版本运行。

`banDuplicateClasses` 会阻止非相同字节码的类冲突进入运行时 classpath；如果未来规则报告冲突，必须先确认责任依赖，再采用最小的排除或版本对齐修复，不能以全局 ignore 掩盖问题。

## 升级与回退

- 补丁升级先在独立变更中更新父 POM、wrapper、插件版本和 checksum，并运行 `./mvnw -q validate` 与受影响模块测试。
- Boot 4 是单独的兼容性项目：先完成 Framework/API、依赖和运行时兼容性评估，再修改此 ADR；在此之前维持 3.5.16。
- 如需回退，回退同一提交中的 POM、wrapper 属性和 ADR；不得仅回退发行版 URL 而保留不匹配的 checksum 或插件版本。

## 官方资料

- [Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring Boot 3.5 发布说明](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes)
- [Apache Maven Wrapper](https://maven.apache.org/wrapper/)
- [Maven 3.9.15 二进制发行版](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.15/apache-maven-3.9.15-bin.zip)
- [Maven Enforcer Plugin](https://maven.apache.org/enforcer/maven-enforcer-plugin/)
- [extra-enforcer-rules：Ban Duplicate Classes](https://www.mojohaus.org/extra-enforcer-rules/banDuplicateClasses.html)
- [OpenJDK 25](https://openjdk.org/projects/jdk/25/)
