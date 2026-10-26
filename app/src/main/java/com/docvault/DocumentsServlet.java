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
import java.util.UUID;

@WebServlet("/documents")
@MultipartConfig(maxFileSize = 20L * 1024 * 1024, maxRequestSize = 25L * 1024 * 1024)
public class DocumentsServlet extends HttpServlet {

    private static final Path STORAGE = Paths.get("/var/lib/docvault/files");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        StringBuilder rows = new StringBuilder();
        boolean dbOk = true;
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT document_id, title, file_name, size_bytes, uploaded_at "
                   + "FROM documents ORDER BY uploaded_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.append("<tr><td>").append(rs.getInt(1)).append("</td><td>")
                    .append(esc(rs.getString(2))).append("</td><td><a href='download?id=")
                    .append(rs.getInt(1)).append("'>").append(esc(rs.getString(3)))
                    .append("</a></td><td>").append(rs.getLong(4)).append(" B</td><td>")
                    .append(rs.getTimestamp(5)).append("</td></tr>");
            }
        } catch (Exception e) {
            dbOk = false;
            getServletContext().log("DocVault: could not list documents", e);
        }

        resp.setStatus(dbOk ? 200 : 503);
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!doctype html><html><head><meta charset='utf-8'><title>DocVault</title><style>"
                + "body{font-family:sans-serif;max-width:900px;margin:2rem auto;padding:0 1rem}"
                + "table{border-collapse:collapse;width:100%}"
                + "th,td{border-bottom:1px solid #ddd;padding:.5rem;text-align:left}"
                + ".msg{padding:.6rem;background:#eef;margin-bottom:1rem}"
                + ".err{background:#fee}</style></head><body><h1>DocVault</h1>");
        String msg = req.getParameter("msg");
        if (msg != null) {
            out.println("<div class='msg'>" + esc(msg) + "</div>");
        }
        if (!dbOk) {
            out.println("<div class='msg err'>The document database is unavailable. "
                    + "Please contact support.</div>");
        }
        out.println("<form method='post' action='documents' enctype='multipart/form-data'>"
                + "<input name='title' placeholder='Title' required> "
                + "<input type='file' name='file' required> "
                + "<button type='submit'>Upload</button></form><hr>");
        out.println("<table><tr><th>ID</th><th>Title</th><th>File</th><th>Size</th>"
                + "<th>Uploaded</th></tr>" + rows + "</table></body></html>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
        String title = req.getParameter("title");
        Part part = req.getPart("file");
        String original = Paths.get(part.getSubmittedFileName()).getFileName().toString();
        Path target = STORAGE.resolve(UUID.randomUUID() + "_" + original);
        String message;
        try {
            Files.createDirectories(STORAGE);
            try (InputStream in = part.getInputStream()) {
                Files.copy(in, target);
            }
            try (Connection c = Db.get();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO documents (title, file_name, content_type, size_bytes, "
                       + "storage_path, owner_id, search_vector) "
                       + "VALUES (?, ?, ?, ?, ?, 1, to_tsvector('english', ?))")) {
                ps.setString(1, title);
                ps.setString(2, original);
                ps.setString(3, part.getContentType());
                ps.setLong(4, part.getSize());
                ps.setString(5, target.toString());
                ps.setString(6, title + " " + original);
                ps.executeUpdate();
            }
            message = "Uploaded: " + original;
        } catch (Exception e) {
            getServletContext().log("DocVault: upload failed for " + original, e);
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // best-effort cleanup of a half-finished upload
            }
            message = "Upload failed. Please contact support.";
        }
        resp.sendRedirect("documents?msg=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }
}
