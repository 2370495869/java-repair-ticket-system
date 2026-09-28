package org.example.repairlab;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实验二：在线报修系统表单处理
 *
 * 纯JDK实现，无任何第三方依赖。
 * 运行后浏览器访问 http://localhost:8080
 */
public class RepairServer {

    // ============ 数据存储 ============
    private static final ConcurrentMap<String, Order> orders = new ConcurrentHashMap<>();
    private static final AtomicInteger orderNo = new AtomicInteger(1);
    // 重复提交检测：记录"手机号+地址+故障类型"的最后提交时间
    private static final ConcurrentMap<String, Long> dupCheck = new ConcurrentHashMap<>();
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_FORM_BYTES = 16 * 1024;
    private static final long DUPLICATE_WINDOW_MS = 10_000;
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> FAULT_TYPES = Set.of(
            "水管漏水", "电路故障", "门窗损坏", "电梯故障", "墙面开裂", "下水道堵塞", "其他");

    // ============ 工单类 ============
    static class Order {
        final String id;
        final String name;
        final String phone;
        final String address;
        final String faultType;
        final String desc;
        final String status = "已提交";
        final String submitTime;

        Order(String id, String name, String phone, String address,
              String faultType, String desc) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.address = address;
            this.faultType = faultType;
            this.desc = desc;
            this.submitTime = LocalDateTime.now().format(DATE_TIME);
        }
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", new FormPage());     // 表单页面
        server.createContext("/submit", new Submit()); // 处理提交
        server.createContext("/query", new Query());   // 工单查询
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())));
        server.setExecutor(executor);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdown();
        }));
        System.out.println("报修系统启动成功！打开浏览器访问: http://localhost:" + port);
    }

    // ==================== 表单页面 ====================
    static class FormPage implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            // 读取URL参数（用于显示错误信息）
            String query = t.getRequestURI().getQuery();
            String errorMsg = "";
            if (query != null) {
                Map<String, String> params = parseQuery(query);
                errorMsg = params.getOrDefault("error", "");
            }

            // 读取最新工单号（用于显示提交成功信息）
            String successMsg = "";
            String lastId = "";
            if (query != null) {
                Map<String, String> params = parseQuery(query);
                successMsg = params.getOrDefault("ok", "");
                lastId = params.getOrDefault("id", "");
            }

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            html.append("<title>在线报修系统</title>");
            html.append("<style>");
            html.append("*{margin:0;padding:0;box-sizing:border-box;}");
            html.append("body{font-family:'Segoe UI','Microsoft YaHei',sans-serif;");
            html.append("background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);min-height:100vh;padding:40px 20px;}");
            html.append(".container{max-width:500px;margin:0 auto;}");
            html.append(".card{background:white;border-radius:16px;padding:32px;box-shadow:0 20px 60px rgba(0,0,0,0.15);margin-bottom:24px;}");
            html.append(".card h2{text-align:center;color:#333;margin-bottom:24px;font-size:22px;}");
            html.append(".card h3{color:#555;margin-bottom:16px;font-size:17px;}");
            html.append("label{display:block;margin-top:14px;font-weight:600;color:#444;font-size:14px;}");
            html.append("input,select,textarea{width:100%;padding:10px 14px;margin-top:5px;");
            html.append("border:2px solid #e8e8e8;border-radius:10px;font-size:14px;transition:border 0.3s;outline:none;font-family:inherit;}");
            html.append("input:focus,select:focus,textarea:focus{border-color:#667eea;}");
            html.append("textarea{resize:vertical;min-height:80px;}");
            html.append(".btn{width:100%;padding:12px;margin-top:18px;background:linear-gradient(135deg,#667eea,#764ba2);");
            html.append("color:white;border:none;border-radius:10px;font-size:16px;font-weight:600;cursor:pointer;transition:transform 0.2s,box-shadow 0.2s;}");
            html.append(".btn:hover{transform:translateY(-1px);box-shadow:0 6px 20px rgba(102,126,234,0.4);}");
            html.append(".btn2{width:100%;padding:12px;margin-top:12px;background:#10b981;");
            html.append("color:white;border:none;border-radius:10px;font-size:16px;font-weight:600;cursor:pointer;transition:transform 0.2s;}");
            html.append(".btn2:hover{transform:translateY(-1px);}");
            html.append(".alert-error{color:#b91c1c;background:#fef2f2;padding:12px 16px;");
            html.append("border:1px solid #fecaca;border-radius:10px;margin-bottom:16px;font-size:14px;}");
            html.append(".alert-success{color:#065f46;background:#ecfdf5;padding:12px 16px;");
            html.append("border:1px solid #a7f3d0;border-radius:10px;margin-bottom:16px;font-size:14px;}");
            html.append("</style></head><body><div class='container'>");

            // 报修表单卡片
            html.append("<div class='card'><h2>🔧 在线报修</h2>");

            if (!errorMsg.isEmpty()) {
                html.append("<div class='alert-error'>⚠ ").append(esc(errorMsg)).append("</div>");
            }
            if (!successMsg.isEmpty()) {
                html.append("<div class='alert-success'>✅ 提交成功！工单号：<b>")
                        .append(esc(lastId)).append("</b></div>");
            }

            html.append("<form method='post' action='/submit'>");
            html.append("<label>👤 姓名</label>");
            html.append("<input name='name' required placeholder='请输入姓名'>");
            html.append("<label>📱 手机号</label>");
            html.append("<input name='phone' required placeholder='请输入11位手机号'>");
            html.append("<label>📍 报修地址</label>");
            html.append("<input name='address' required placeholder='请输入地址'>");
            html.append("<label>🔩 故障类型</label>");
            html.append("<select name='faultType'>");
            html.append("<option>水管漏水</option><option>电路故障</option>");
            html.append("<option>门窗损坏</option><option>电梯故障</option>");
            html.append("<option>墙面开裂</option><option>下水道堵塞</option>");
            html.append("<option>其他</option>");
            html.append("</select>");
            html.append("<label>📝 故障描述</label>");
            html.append("<textarea name='desc' rows='3' placeholder='请描述故障情况'></textarea>");
            html.append("<button class='btn' type='submit'>提交报修</button>");
            html.append("</form></div>");

            // 工单查询卡片
            html.append("<div class='card'><h3>🔍 查询工单状态</h3>");
            html.append("<form method='get' action='/query'>");
            html.append("<input name='id' placeholder='输入工单号' required>");
            html.append("<button class='btn2' type='submit'>查询</button>");
            html.append("</form></div>");

            html.append("</div></body></html>");

            // 返回HTML
            byte[] bytes = html.toString().getBytes("UTF-8");
            t.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // ==================== 处理表单提交 ====================
    static class Submit implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (!"POST".equalsIgnoreCase(t.getRequestMethod())) {
                sendHtml(t, 405, "仅支持 POST 请求");
                return;
            }

            String body;
            try {
                body = new String(readLimited(t.getRequestBody(), MAX_FORM_BYTES), StandardCharsets.UTF_8);
            } catch (IOException e) {
                sendHtml(t, 413, "表单内容超过限制");
                return;
            }
            Map<String, String> params = parseQuery(body);

            String name = params.getOrDefault("name", "").trim();
            String phone = params.getOrDefault("phone", "").trim();
            String address = params.getOrDefault("address", "").trim();
            String faultType = params.getOrDefault("faultType", "").trim();
            String desc = params.getOrDefault("desc", "").trim();

            if (name.isEmpty() || name.length() > 40
                    || address.isEmpty() || address.length() > 120
                    || desc.length() > 500 || !FAULT_TYPES.contains(faultType)) {
                redirect(t, "/?error=" + urlEncode("请检查必填信息和字段长度。"));
                return;
            }

            if (!phone.matches("1[3-9][0-9]{9}")) {
                redirect(t, "/?error=" + urlEncode("手机号格式不正确，请重新输入！"));
                return;
            }

            String dupKey = phone + "|" + address + "|" + faultType;
            long now = System.currentTimeMillis();
            dupCheck.entrySet().removeIf(entry -> now - entry.getValue() >= DUPLICATE_WINDOW_MS);
            AtomicBoolean accepted = new AtomicBoolean();
            dupCheck.compute(dupKey, (key, lastTime) -> {
                if (lastTime == null || now - lastTime >= DUPLICATE_WINDOW_MS) {
                    accepted.set(true);
                    return now;
                }
                return lastTime;
            });
            if (!accepted.get()) {
                redirect(t, "/?error=" + urlEncode("请勿重复提交！10秒内已有相同报修。"));
                return;
            }

            String id = "GD" + String.format("%04d", orderNo.getAndIncrement());
            Order order = new Order(id, name, phone, address, faultType, desc);
            orders.put(id, order);

            redirect(t, "/?ok=success&id=" + urlEncode(id));
        }
    }

    // ==================== 工单查询 ====================
    static class Query implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (!"GET".equalsIgnoreCase(t.getRequestMethod())) {
                sendHtml(t, 405, "仅支持 GET 请求");
                return;
            }
            // 解析查询参数
            String queryStr = t.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(queryStr != null ? queryStr : "");
            String id = params.getOrDefault("id", "").trim();

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            html.append("<title>工单查询结果</title>");
            html.append("<style>");
            html.append("*{margin:0;padding:0;box-sizing:border-box;}");
            html.append("body{font-family:'Segoe UI','Microsoft YaHei',sans-serif;");
            html.append("background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);min-height:100vh;padding:40px 20px;}");
            html.append(".card{background:white;border-radius:16px;padding:32px;max-width:500px;margin:0 auto;box-shadow:0 20px 60px rgba(0,0,0,0.15);}");
            html.append(".card h2{text-align:center;color:#333;margin-bottom:24px;font-size:22px;}");
            html.append("table{border-collapse:collapse;width:100%;margin-top:10px;}");
            html.append("td,th{border-bottom:1px solid #eee;padding:12px 8px;}");
            html.append("th{color:#666;text-align:right;width:30%;font-size:14px;}");
            html.append("td{color:#333;}");
            html.append(".status{display:inline-block;padding:4px 12px;border-radius:20px;font-weight:600;font-size:13px;}");
            html.append(".status-submitted{background:#fef3c7;color:#92400e;}");
            html.append(".btn-back{display:block;margin-top:24px;text-align:center;padding:10px;");
            html.append("background:linear-gradient(135deg,#667eea,#764ba2);color:white;text-decoration:none;border-radius:10px;font-weight:600;transition:transform 0.2s;}");
            html.append(".btn-back:hover{transform:translateY(-1px);}");
            html.append(".empty-state{text-align:center;padding:40px 0;color:#999;}");
            html.append(".empty-state .icon{font-size:48px;margin-bottom:12px;}");
            html.append("</style></head><body><div class='card'>");
            html.append("<h2>🔍 工单查询结果</h2>");

            if (id.isEmpty()) {
                html.append("<div class='empty-state'><div class='icon'>📭</div>请输入工单号</div>");
            } else {
                Order order = orders.get(id);
                if (order == null) {
                    html.append("<div class='empty-state'><div class='icon'>🔎</div>未找到工单号：<b>")
                            .append(esc(id)).append("</b></div>");
                } else {
                    String statusClass = "已提交".equals(order.status) ? "status-submitted" : "status-processing";
                    html.append("<table>");
                    html.append("<tr><th>工单号</th><td>").append(esc(order.id)).append("</td></tr>");
                    html.append("<tr><th>姓名</th><td>").append(esc(order.name)).append("</td></tr>");
                    html.append("<tr><th>手机号</th><td>").append(esc(order.phone)).append("</td></tr>");
                    html.append("<tr><th>报修地址</th><td>").append(esc(order.address)).append("</td></tr>");
                    html.append("<tr><th>故障类型</th><td>").append(esc(order.faultType)).append("</td></tr>");
                    html.append("<tr><th>故障描述</th><td>").append(esc(order.desc)).append("</td></tr>");
                    html.append("<tr><th>状态</th><td><span class='status ").append(statusClass)
                            .append("'>").append(esc(order.status)).append("</span></td></tr>");
                    html.append("<tr><th>提交时间</th><td>").append(esc(order.submitTime)).append("</td></tr>");
                    html.append("</table>");
                }
            }

            html.append("<a class='btn-back' href='/'>← 返回报修页面</a>");
            html.append("</div></body></html>");

            sendHtml(t, html.toString());
        }
    }

    // ==================== 工具方法 ====================

    private static int parsePort(String[] args) {
        String configuredPort = args.length > 0
                ? args[0]
                : System.getenv().getOrDefault("PORT", Integer.toString(DEFAULT_PORT));
        int port = Integer.parseInt(configuredPort);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口必须在 1 到 65535 之间");
        }
        return port;
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        byte[] body = input.readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            throw new IOException("请求体过大");
        }
        return body;
    }

    private static Map<String, String> parseQuery(String str) {
        Map<String, String> map = new LinkedHashMap<>();
        if (str == null || str.isEmpty()) return map;
        for (String pair : str.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (IllegalArgumentException ignored) {
                    // 跳过不完整的百分号编码，避免损坏的输入中断整个请求。
                }
            }
        }
        return map;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static void redirect(HttpExchange t, String path) throws IOException {
        t.getResponseHeaders().set("Location", path);
        t.sendResponseHeaders(302, -1);
        t.close();
    }

    private static void sendHtml(HttpExchange t, String html) throws IOException {
        sendHtml(t, 200, html);
    }

    private static void sendHtml(HttpExchange t, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        t.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        t.getResponseHeaders().set("Cache-Control", "no-store");
        t.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = t.getResponseBody()) {
            output.write(bytes);
        }
    }
}
