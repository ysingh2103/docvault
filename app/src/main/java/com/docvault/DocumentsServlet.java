package com.docvault;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@WebServlet("/documents")
@MultipartConfig(maxFileSize = 20L * 1024 * 1024, maxRequestSize = 25L * 1024 * 1024)
public class DocumentsServlet extends HttpServlet {

    private static final Path STORAGE = Paths.get("/var/lib/docvault/files");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String TOP = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>DocVault</title>
            <link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E%3Crect width='32' height='32' rx='7' fill='%232b7fff'/%3E%3Ctext x='16' y='14' font-family='monospace' font-size='12' font-weight='700' text-anchor='middle' fill='%2302050b'%3E0%3C/text%3E%3Ctext x='16' y='27' font-family='monospace' font-size='12' font-weight='700' text-anchor='middle' fill='%2302050b'%3E1%3C/text%3E%3C/svg%3E">
            <link rel="stylesheet" href="static/style.css">
            </head>
            <body>
            <header class="top">
              <div class="wrap top-in">
                <a class="brand" href="documents">
                  <span class="mark" aria-hidden="true"><i>0</i><i>1</i></span>
                  <span class="word">DocVault<b class="cur">_</b></span>
                </a>
                <div class="tools">
                  <div class="status {{statusClass}}" role="status"><span class="dot"></span>{{statusText}}</div>
                  {{userBar}}
                </div>
              </div>
            </header>
            <main class="wrap">
            <h1 class="sr">DocVault documents</h1>
            """;

    private static final String UPLOAD = """
            <section class="card" aria-labelledby="up-h">
              <h2 id="up-h">Upload a document</h2>
              <form class="upload" method="post" action="documents" enctype="multipart/form-data">
                <div>
                  <label class="lbl" for="title">Title</label>
                  <input class="txt" id="title" name="title" maxlength="200" placeholder="Quarterly report" required>
                </div>
                <div>
                  <span class="lbl">File</span>
                  <label class="drop" id="drop">
                    <input id="file" type="file" name="file" required>
                    <span id="dropText">Drop a file here or choose one. Maximum 20 MB.</span>
                  </label>
                </div>
                <button class="btn primary" type="submit">Upload</button>
              </form>
            </section>
            """;

    private static final String LIST_HEAD = """
            <section aria-labelledby="list-h">
              <div class="list-head">
                <h2 id="list-h">Documents</h2>
                <p class="sum">{{summary}}</p>
                <form class="searchbar" method="get" action="documents" role="search">
                  <input class="txt filter" id="q" name="q" type="search" value="{{q}}" maxlength="100" placeholder="Search title or file name" aria-label="Search documents">
                  <button class="btn" type="submit">Search</button>{{clear}}
                </form>
              </div>
            """;

    private static final String BOTTOM = """
            </section>
            </main>
            <footer class="wrap foot">DocVault 1.0.0 on Apache Tomcat and PostgreSQL</footer>
            <script src="static/app.js"></script>
            </body>
            </html>
            """;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        StringBuilder rows = new StringBuilder();
        int count = 0;
        long total = 0;
        boolean dbOk = true;
        String q = req.getParameter("q");
        q = q == null ? "" : q.trim();
        boolean searching = !q.isEmpty();
        // Search uses the GIN index on search_vector and ranks by relevance;
        // without a query the list is newest first.
        Auth.User user = Auth.current(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        // Admins see everything. Everyone else sees documents they own plus ones shared with them.
        boolean everything = user.isAdmin();
        String visible = "(d.owner_id = ? OR EXISTS (SELECT 1 FROM document_access a "
                + "WHERE a.document_id = d.document_id AND a.user_id = ?))";
        String cols = "SELECT d.document_id, d.title, d.file_name, d.size_bytes, d.uploaded_at FROM documents d ";
        String sql = searching
                ? cols + "WHERE d.search_vector @@ websearch_to_tsquery('english', ?) "
                + (everything ? "" : "AND " + visible + " ")
                + "ORDER BY ts_rank(d.search_vector, websearch_to_tsquery('english', ?)) DESC, d.uploaded_at DESC"
                : cols + (everything ? "" : "WHERE " + visible + " ") + "ORDER BY d.uploaded_at DESC";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (searching) {
                ps.setString(i++, q);
            }
            if (!everything) {
                ps.setInt(i++, user.id());
                ps.setInt(i++, user.id());
            }
            if (searching) {
                ps.setString(i, q);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt(1);
                String title = rs.getString(2);
                String fileName = rs.getString(3);
                long size = rs.getLong(4);
                Timestamp up = rs.getTimestamp(5);
                count++;
                total += size;
                rows.append("<tr data-q=\"").append(esc((title + " " + fileName).toLowerCase(Locale.ROOT)))
                    .append("\"><td class=\"num\">").append(id)
                    .append("</td><td class=\"ttl\">").append(esc(title))
                    .append("</td><td><span class=\"ext\">").append(esc(ext(fileName)))
                    .append("</span><span class=\"fname\">").append(esc(fileName))
                    .append("</span></td><td class=\"num r\">").append(formatBytes(size))
                    .append("</td><td class=\"num\">")
                    .append(up == null ? "" : TIME.format(up.toLocalDateTime()))
                    .append("</td><td class=\"r\"><a class=\"btn\" href=\"download?id=").append(id)
                    .append("\">Download</a></td></tr>");
            }
        } catch (Exception e) {
            dbOk = false;
            getServletContext().log("DocVault: could not list documents", e);
        }

        String toast = "";
        String msg = req.getParameter("msg");
        if (msg != null) {
            String kind = "error".equals(req.getParameter("status")) ? "toast err" : "toast";
            toast = "<div class=\"" + kind + "\" role=\"status\">" + esc(msg) + "</div>\n";
        }

        String summary = !dbOk
                ? "The list is unavailable right now"
                : searching
                ? count + (count == 1 ? " match" : " matches") + " for “" + esc(q) + "”"
                : count + (count == 1 ? " document, " : " documents, ") + formatBytes(total) + " stored";

        String table;
        if (!dbOk) {
            table = "<div class=\"empty\"><p>Documents can't be loaded.</p>"
                  + "<p>The database isn't reachable. Try again in a minute, or contact support.</p></div>\n";
        } else if (count == 0 && searching) {
            table = "<div class=\"empty\"><p>No documents match that search.</p>"
                  + "<p>Try a different word, or clear the search to see everything.</p></div>\n";
        } else if (count == 0 && !user.canUpload()) {
            table = "<div class=\"empty\"><p>Nothing has been shared with you yet.</p>"
                  + "<p>Ask an editor or an administrator to give you access to a document.</p></div>\n";
        } else if (count == 0) {
            table = "<div class=\"empty\"><p>No documents yet.</p>"
                  + "<p>Choose a file above to store your first one.</p></div>\n";
        } else {
            table = "<div class=\"tablewrap\"><table class=\"docs\"><thead><tr>"
                  + "<th>ID</th><th>Title</th><th>File</th><th class=\"r\">Size</th><th>Uploaded</th>"
                  + "<th><span class=\"sr\">Actions</span></th></tr></thead><tbody id=\"rows\">"
                  + rows + "</tbody></table></div>\n";
        }

        resp.setStatus(dbOk ? 200 : 503);
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        String userBar = "<span class=\"who\">" + esc(user.username()) + " <em>" + esc(user.role()) + "</em></span>"
                + "<form method=\"post\" action=\"logout\"><button class=\"btn\" type=\"submit\">Sign out</button></form>";
        out.print(TOP.replace("{{statusClass}}", dbOk ? "up" : "down")
                     .replace("{{statusText}}", dbOk ? "Database online" : "Database unreachable")
                     .replace("{{userBar}}", userBar));
        out.print(toast);
        if (user.canUpload()) {
            out.print(UPLOAD);
        }
        out.print(LIST_HEAD.replace("{{summary}}", summary)
                        .replace("{{q}}", esc(q))
                        .replace("{{clear}}", searching ? " <a class=\"btn\" href=\"documents\">Clear</a>" : ""));
        out.print(table);
        out.print(BOTTOM);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Auth.User user = Auth.current(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        if (!user.canUpload()) {
            getServletContext().log("DocVault: upload refused for " + user.username() + " (" + user.role() + ")");
            resp.sendError(403, "Your role cannot upload documents");
            return;
        }
        Part part;
        String title;
        try {
            part = req.getPart("file");
            title = req.getParameter("title");
        } catch (IllegalStateException | ServletException e) {
            getServletContext().log("DocVault: rejected upload request", e);
            redirect(resp, "error", "That file is larger than 20 MB, or the upload was interrupted.");
            return;
        }
        if (part == null || part.getSize() == 0 || part.getSubmittedFileName() == null
                || title == null || title.isBlank()) {
            redirect(resp, "error", "Add a title and choose a file that isn't empty.");
            return;
        }

        String original = "file";
        Path target = null;
        try {
            original = Paths.get(part.getSubmittedFileName()).getFileName().toString();
            target = STORAGE.resolve(UUID.randomUUID() + "_" + original);
            Files.createDirectories(STORAGE);
            try (InputStream in = part.getInputStream()) {
                Files.copy(in, target);
            }
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO documents (title, file_name, content_type, size_bytes, "
                       + "storage_path, owner_id, search_vector) "
                       + "VALUES (?, ?, ?, ?, ?, ?, to_tsvector('english', ?))")) {
                ps.setString(1, title.trim());
                ps.setString(2, original);
                ps.setString(3, part.getContentType());
                ps.setLong(4, part.getSize());
                ps.setString(5, target.toString());
                ps.setInt(6, user.id());
                ps.setString(7, title.trim() + " " + original);
                ps.executeUpdate();
            }
            redirect(resp, "ok", "Uploaded " + original + ".");
        } catch (Exception e) {
            getServletContext().log("DocVault: upload failed for " + original, e);
            if (target != null) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                    // best-effort cleanup of a half-finished upload
                }
            }
            redirect(resp, "error", "Upload failed. Try again, or contact support if it keeps happening.");
        }
    }

    private static void redirect(HttpServletResponse resp, String status, String message) throws IOException {
        resp.sendRedirect("documents?status=" + status + "&msg="
                + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private static String ext(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) {
            return "file";
        }
        String e = name.substring(i + 1).toLowerCase(Locale.ROOT);
        return e.length() > 5 ? e.substring(0, 5) : e;
    }

    private static String formatBytes(long n) {
        if (n < 1024) {
            return n + " B";
        }
        if (n < 1024L * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", n / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", n / (1024.0 * 1024.0));
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }
}
