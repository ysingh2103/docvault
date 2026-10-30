package com.docvault;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet("/download")
public class DownloadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int id;
        try {
            id = Integer.parseInt(req.getParameter("id"));
        } catch (NumberFormatException e) {
            resp.sendError(400, "Invalid document id");
            return;
        }

        Auth.User user = Auth.current(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        String path = null;
        String name = null;
        String type = null;
        boolean allowed = false;
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT d.storage_path, d.file_name, d.content_type, d.owner_id, "
                   + "EXISTS (SELECT 1 FROM document_access a "
                   + "WHERE a.document_id = d.document_id AND a.user_id = ?) "
                   + "FROM documents d WHERE d.document_id = ?")) {
            ps.setInt(1, user.id());
            ps.setInt(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    path = rs.getString(1);
                    name = rs.getString(2);
                    type = rs.getString(3);
                    allowed = user.isAdmin() || rs.getInt(4) == user.id() || rs.getBoolean(5);
                }
            }
        } catch (Exception e) {
            Log.log("DocVault: download lookup failed", e);
            resp.sendError(503, "Document database unavailable");
            return;
        }

        if (path == null) {
            resp.sendError(404, "No such document");
            return;
        }
        if (!allowed) {
            Log.log("DocVault: " + user.username() + " was refused document " + id);
            resp.sendError(403, "You don't have access to this document");
            return;
        }
        Path file = Paths.get(path);
        if (!Files.isReadable(file)) {
            Log.log("DocVault: record exists but file is missing: " + path);
            resp.sendError(404, "The document record exists but its file is missing");
            return;
        }
        resp.setContentType(type != null ? type : "application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + name.replace("\"", "") + "\"");
        resp.setContentLengthLong(Files.size(file));
        Files.copy(file, resp.getOutputStream());
    }
}
