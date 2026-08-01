# 本地脚本引用检测夹具

以下代码块包含一个可用 Maven Wrapper 入口，以及三条应被检测到的、缺失的本地可执行脚本引用：

```sh
./mvnw verify
bin/reconcile --all
tools/reindex --all
./tools/release.custom
```

以下内容不是需要检测的本地脚本引用：

```sh
ls ./tools/
cat ./docs/guide.md
curl https://example.com/scripts/release.sh
```
