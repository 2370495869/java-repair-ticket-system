# 在线报修工单平台目标报告

> 本文记录目标与验收口径，不代表功能已实现。当前源码在本报告形成时仍是使用 JDK `HttpServer` 和内存 Map 的实验表单；完成情况以实现后的 `docs/implementation-report.md` 为准。

## 项目目标

将现有报修实验扩展为可独立运行、可公开展示的中文在线工单平台，覆盖客户报修、客服分派、维修处理、评论和状态审计的完整闭环。系统应使用持久化数据库并实施角色权限与联系信息保护。

## 目标功能与验收标准

1. 未登录访客可注册客户账号、登录和退出；公开注册只能创建客户角色。
2. 客户可创建工单、查看自己的工单详情和进度，并在工单内留言；无法读取其他客户的工单。
3. 客服可查看待处理队列并把工单分派给维修人员；不能以客户身份访问管理操作。
4. 维修人员只能查看分派给自己的工单，可按允许的状态流转更新进度并留言。
5. 工单详情展示按时间排列的评论和只追加的状态历史，记录操作者、变更前后状态、时间及说明。
6. 联系方式不通过未认证页面或仅凭工单号暴露；客户只看自己的完整资料，维修人员只看已分派工单的必要联系资料，队列中的联系方式按需脱敏。
7. 密码使用自适应哈希保存；输入经过服务端验证；修改请求启用 CSRF 防护；数据库操作具备事务边界。
8. 工单、评论、分派和历史写入 PostgreSQL，并由 Flyway 管理迁移；本机可用文件型 H2 独立启动，重启后数据仍保留。
9. Maven Wrapper、Docker Compose、容器构建、自动化测试及 GitHub Actions 均纳入仓库；仓库中不包含实际凭据、`.env`、运行数据或构建产物。
10. 项目不接收、不存储、不展示照片或附件。

## 目标工单流转

```text
客户提交
   ↓
客服分派维修人员
   ↓
维修人员处理中 ↔ 等待客户 / 等待配件
   ↓
维修人员标记已解决
   ↓
客户确认完成
```

取消和无效状态跳转应由后端拒绝。每次有效状态变更在同一事务内写入状态历史。

## 角色与资料访问

| 角色 | 允许操作 | 联系资料范围 |
| --- | --- | --- |
| 客户 | 注册、创建工单、查看和评论自己的工单、确认解决 | 仅自己的完整资料 |
| 客服 | 查看全部队列、分派或改派维修人员、回复客户 | 队列默认脱敏；处理工单时只显示必要信息 |
| 维修人员 | 查看分派给自己的工单、更新允许的状态、评论 | 仅当前分派工单的必要联系资料 |

## 技术方案与选择理由

- **Java 21 LTS / Eclipse Temurin**：本机已有 Temurin 21，可直接验证；Spring Boot 4.1.1 支持 Java 17 至 26，Flyway 13 要求 Java 21 及以上。
- **Spring Boot 4.1.1**：截至 2026-09-28 的最新稳定线，提供 MVC、Thymeleaf、Security、Validation、JPA 和测试集成；采用服务端页面，避免增加 Node 前端构建链。
- **PostgreSQL 18.6**：当前受支持的 PostgreSQL 主版本，适合作为 Compose 和发布部署的持久化数据库。
- **Flyway 13.8.0**：采用 PostgreSQL 数据库模块管理 SQL 迁移，避免由 Hibernate 自动改写生产 schema。
- **文件型 H2（本机模式）**：允许仅安装 JDK 即运行演示版本，并保留本机数据；Compose 环境通过配置切换至 PostgreSQL。
- **Maven Wrapper 3.3.4 + Maven 3.9.16**：固定构建工具下载版本，免去要求用户预装 Maven。
- **Docker Compose**：编排应用与 PostgreSQL，并使用具名 volume 保留数据库文件。
- **服务端 HTML/CSS 页面**：以中文客户台、客服队列和维修工作台展示完整流程，不引入附件或独立 SPA。

截至上述日期，Spring Boot 官方文档列出 4.1.1 为最新稳定版本并明确其 Java 兼容区间；PostgreSQL 官方版本策略列出 18.6 仍受支持至 2030-11-14；Flyway 13.8.0 的 PostgreSQL 模块可从 Maven Central 获取。相关依据见 [Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[Oracle Java 支持路线图](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)、[PostgreSQL 版本策略](https://www.postgresql.org/support/versioning/)、[Flyway PostgreSQL 模块](https://central.sonatype.com/artifact/org.flywaydb/flyway-database-postgresql) 和 [Maven Wrapper 发布页](https://github.com/apache/maven-wrapper/releases)。

## 代码复用调研

已用当前 GitHub 代码搜索检索 Spring 工单、状态迁移、Spring Security 和 JPA/Flyway 的现成实现。发现一个较大的字段服务平台示例使用 React、Spring Boot、MySQL，并包含库存、SLA 与附件等本项目不需要的模块；其结构和技术栈与本项目不匹配，因此不移植其业务代码。实现将复用 Spring Boot、Spring Security、Spring Data JPA、Thymeleaf、Flyway 等成熟库，状态权限和数据边界由本项目代码明确实现。

## 验证目标

- `./mvnw -B -ntp verify`：编译并运行单元及 MVC 集成测试。
- H2 本机模式：实际启动应用，验证注册、登录、客户隔离及至少一次完整派单与完工流程；重启后检查工单仍存在。
- Docker Compose：检查配置并尝试启动 PostgreSQL 与应用，确认迁移完成及健康端点可用。
- 安全检查：核对角色/所有权边界、密码哈希、CSRF、脱敏策略、SQL 参数化、依赖漏洞及待提交内容中的秘密和生成物。

## 不在本次范围

图片或文件附件、在线支付、短信/邮件通知、地图派单、复杂 SLA、跨企业多租户、面向公网的生产部署和 TLS 证书配置。
