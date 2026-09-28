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

- Java 21；Spring Boot 4.1.1；PostgreSQL 18.6；Flyway 13.8.0；Maven Wrapper 3.3.4 和 Maven 3.9.16。
- 新增 `pom.xml`、Windows / POSIX Wrapper、`Dockerfile`、`compose.yaml`、`.env.example`、忽略规则、MIT License、Dependabot、构建及 CodeQL 工作流。
- 构建工作流在 `main` 和 Pull Request 上运行 `./mvnw -B -ntp verify`；CodeQL 工作流面向 Java，Dependabot 每周检查 Maven 与 GitHub Actions。
- Dockerfile 使用多阶段构建并以非 root 用户运行；Compose 只将 Web 端口绑定到本机回环地址，PostgreSQL 使用具名卷。

## 验证记录

运行环境为 Eclipse Temurin Java 21.0.11、Windows PowerShell 7；Maven 3.9.16 由 Wrapper 下载。验证结果如下：

| 检查 | 结果 |
| --- | --- |
| `./mvnw -B -ntp verify` | 通过；编译 32 个主源码文件、1 个测试文件；集成测试 4 项通过，0 失败、0 错误；可执行 Spring Boot JAR 打包成功 |
| Flyway + H2 集成测试 | 通过；V1 成功执行，Hibernate schema validation 成功 |
| 本机 Web 烟测 | 通过；`/login` 返回 200、`/health` 返回 `UP`；经真实 HTTP 表单注册客户和提交工单，重启服务后重新登录仍能看到同一工单号，确认文件型 H2 数据持久化 |
| 权限与隐私用例 | 通过；检查 CSRF、BCrypt 哈希、客户越权读取、错误角色访问客服分派、完整状态流转、评论 / 历史和客服 / 维修人员联系方式差异 |
| Docker Compose 启动 | 未执行：本机没有 Docker CLI / Compose。Compose 文件已加入仓库，但 PostgreSQL 容器启动和 PostgreSQL 实际迁移尚未在本机验证 |
| GitHub Actions / CodeQL 远端运行 | 已在公开仓库运行；`Build and test`、`CodeQL security analysis` 以及 Dependabot 的 Maven / Actions 更新检查均完成，结论为 success |
| GitHub 仓库与推送 | 已完成；创建公开仓库 [java-repair-ticket-system](https://github.com/2370495869/java-repair-ticket-system)，`main` 已推送并跟踪 `origin/main`；初始发布提交为 `11ef79dd0e802d96d8dd116cf8ce40b59f93a17e` |

测试使用 H2 内存库隔离；本机烟测使用位于临时目录的文件型 H2 与虚构资料，验证结束后移除。构建生成物位于忽略的 `target/` 下，不加入仓库。

## 安全检查与边界

- 使用 BCrypt cost 12；服务端表单校验；Spring Security 默认 CSRF；Session Cookie 配置 HttpOnly、SameSite；启用 CSP、Referrer-Policy 和禁止 iframe 嵌入。
- 对角色入口与工单拥有权 / 分派关系在控制器和服务层实施限制；Hibernate 参数化持久化；工单版本字段支持乐观并发控制。
- `.gitignore` 与 `.dockerignore` 排除 `.env`、数据库文件、上传目录、日志和构建输出；仓库仅含密码字段为空的 `.env.example`。已检查推送前的 70 个跟踪文件，没有令牌 / 私钥特征、所检索的个人标识、`.env`、数据库或构建输出。
- CodeQL 工作流在 GitHub Actions 上成功完成；这不等同于全面的依赖漏洞审计。本次没有声称通过独立外部依赖漏洞扫描。

## 当前限制

- Docker / PostgreSQL 运行路径没有在当前机器实测；本机及集成验证使用 H2。
- 尚未实现登录限流、找回密码、多因素认证、邮件 / 短信通知、备份策略或公网 TLS；公开演示不应录入真实个人资料，正式部署需设置 HTTPS、Secure Cookie、访问控制和数据保留策略。
- 客服和维修账号由演示初始化配置管理；本项目没有提供组织后台、邀请流程或多租户能力。
