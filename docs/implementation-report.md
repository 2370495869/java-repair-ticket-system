# 实现报告：在线报修工单平台

## 实施范围

本次在原 `JAVAshiyan2` 项目内扩展完整的中文工单平台。原项目是 JDK `HttpServer` 单文件实验，数据仅保存在内存中。旧的 `RepairServer.java` 保留作实验参考，新应用入口为 `io.github.repairticket.RepairTicketApplication`。实现前将源项目包名改为中性命名，避免公开仓库暴露本机用户标识。没有加入照片或附件上传功能。

## 已实现功能

- **账号与角色**：客户注册、登录和退出；公开注册只能创建客户。客服和维修人员账号由本地 / Compose 演示初始化器创建。
- **客户工作台**：提交报修、查看自己的工单、查看流转轨迹、留言，并确认已解决工单。
- **客服工作台**：查看全局工单队列，并将待分派工单分给有效的维修人员。
- **维修工作台**：只显示当前维修人员接手的工单；支持接单后进入维修、等待客户 / 配件、继续维修和标记已解决。
- **评论与历史**：评论和状态历史单独存储；初始提交、分派和所有状态变更均记录操作者、变更前后状态、时间与说明；更新工单及写历史在事务中完成。
- **联系方式保护**：客户只能查看自己的完整联系方式；维修人员只能查看分派给自己的工单资料；客服视图将电话、地址和客户姓名脱敏；不提供匿名工单号查询。
- **界面和存储**：新增中文 Thymeleaf 页面与响应式 CSS。Flyway 创建 PostgreSQL / H2 共用的数据结构；默认本机配置使用文件型 H2，容器配置切换为 PostgreSQL。

## 技术与工程配置

- Java 21；Spring Boot 4.1.1；PostgreSQL 18.6；Flyway 13.8.0；Maven Wrapper 3.3.4 和 Maven 3.9.16。PostgreSQL 18 的具名卷挂载到 `/var/lib/postgresql`；Compose 通过 `APP_PORT` 配置仅监听回环地址的宿主机端口（默认 8080）。
- 新增 `pom.xml`、Windows / POSIX Wrapper、`Dockerfile`、`compose.yaml`、`.env.example`、忽略规则、MIT License、Dependabot、构建及 CodeQL 工作流。
- 构建工作流在 `main` 和 Pull Request 上运行 `./mvnw -B -ntp verify`；CodeQL 工作流面向 Java，Dependabot 每周检查 Maven 与 GitHub Actions。
- Dockerfile 使用多阶段构建并以非 root 用户运行；Compose 只将 Web 端口绑定到本机回环地址，PostgreSQL 使用具名卷。

## 验证记录

运行环境为 Eclipse Temurin Java 21.0.11、Windows PowerShell 7；Maven 3.9.16 由 Wrapper 下载。验证结果如下：

| 检查 | 结果 |
| --- | --- |
| `./mvnw -B -ntp verify` | 通过；编译 32 个主源码文件、1 个测试文件；集成测试 4 项通过，0 失败、0 错误；可执行 Spring Boot JAR 打包成功。Dockerfile 构建时也再次运行同一命令并成功 |
| Flyway + H2 集成测试 | 通过；V1 成功执行，Hibernate schema validation 成功 |
| 本机 Web 烟测 | 通过；`/login` 返回 200、`/health` 返回 `UP`；经真实 HTTP 表单注册客户和提交工单，重启服务后重新登录仍能看到同一工单号，确认文件型 H2 数据持久化 |
| 权限与隐私用例 | 通过；检查 CSRF、BCrypt 哈希、客户越权读取、错误角色访问客服分派、完整状态流转、评论 / 历史和客服 / 维修人员联系方式差异 |
| Docker / Compose / PostgreSQL | Docker Desktop 4.93.0.240920（官方 SHA-256 与 Authenticode 签名校验通过）、Docker CLI / Engine 29.8.1、Compose v5.5.1、WSL 2.7.14.0。`docker compose config --quiet` 通过；`docker compose up --build --detach` 完成镜像构建并在构建容器中通过 4 项集成测试。真实启动中发现 PostgreSQL 18 要求把卷挂载到 `/var/lib/postgresql`，已修正 Compose；数据库健康检查通过，Flyway 成功迁移 1 次。Windows 排除了 8080（7998–8097 为系统排除端口），因此将宿主机端口设为可配置 `APP_PORT`，本机用 18080 运行；应用容器和数据库容器均正常启动。由于原用户 AppData 开启 EFS，本机 Docker Desktop 使用独立的未加密目录和进程级环境变量启动，未修改原用户 Docker 配置或清理既有数据。 |
| 容器内 HTTP 工单实测与持久化 | `/health` 返回 `UP`，登录页返回 200。以演示客户、客服、维修人员分别登录，使用虚构测试资料完成提交、客户留言、客服分派、维修状态更新与留言、客户确认关闭；状态历史包含提交、分派、维修中、已解决、已完成。客服详情页不含虚构联系姓名、电话或地址；工单所有者与已分派维修人员可查看联系方式。重启应用容器后重新登录仍可读取同一工单、评论和历史；数据库核对为 1 张工单、2 条评论、5 条状态历史，Flyway 成功记录 1 次。 |
| HTTP 安全检查 | 登录页响应包含 CSP、`X-Frame-Options: DENY` 和 Referrer-Policy；缺少 CSRF 令牌的登录 POST 返回 403。角色与工单访问控制、联系方式脱敏亦通过上述真实 HTTP 流程验证。 |
| GitHub Actions / CodeQL 远端运行 | 已配置 `Build and test`、`CodeQL security analysis` 与 Dependabot 的 Maven / Actions 检查。公开仓库此前的对应运行已成功；本轮 `de7e19f` 推送后，Build 和 CodeQL 新运行已启动，记录时仍在执行中，不把它们写作已通过。 |
| GitHub 仓库与推送 | GitHub 元信息确认仓库为公开、默认分支为 `main`。本轮 Compose 修复和验证报告已通过普通非强制推送同步；该次提交为 `de7e19f`。 |

测试使用 H2 内存库隔离；本机烟测使用位于临时目录的文件型 H2 与虚构资料，验证结束后移除。构建生成物位于忽略的 `target/` 下，不加入仓库。

## 安全检查与边界

- 使用 BCrypt cost 12；服务端表单校验；Spring Security 默认 CSRF；Session Cookie 配置 HttpOnly、SameSite；启用 CSP、Referrer-Policy 和禁止 iframe 嵌入。
- 对角色入口与工单拥有权 / 分派关系在控制器和服务层实施限制；Hibernate 参数化持久化；工单版本字段支持乐观并发控制。
- `.gitignore` 与 `.dockerignore` 排除 `.env`、数据库文件、上传目录、日志和构建输出；仓库仅含密码字段为空的 `.env.example`。本轮本机演示账号密码为随机值，保存在 Git 忽略的 `.env`，未输出到报告或构建日志。推送前检查待提交差异和文件清单，确认不含密码、令牌、真实个人信息、`.env`、本机数据库或构建产物。
- CodeQL 工作流在 GitHub Actions 上成功完成；这不等同于全面的依赖漏洞审计。本次没有声称通过独立外部依赖漏洞扫描。

## 当前限制

- 本机 Docker Desktop 的用户 AppData 使用 EFS，Docker 启动时需使用独立未加密数据目录；该主机的 8080 也位于 Windows 排除端口范围，应按需通过 `.env` 中的 `APP_PORT` 选取可用端口。项目默认仍为 8080。`.env` 中的数据库和演示密码是本机随机值，受 Git 忽略规则保护，未提交。
- 尚未实现登录限流、找回密码、多因素认证、邮件 / 短信通知、备份策略或公网 TLS；公开演示不应录入真实个人资料，正式部署需设置 HTTPS、Secure Cookie、访问控制和数据保留策略。
- 客服和维修账号由演示初始化配置管理；本项目没有提供组织后台、邀请流程或多租户能力。
