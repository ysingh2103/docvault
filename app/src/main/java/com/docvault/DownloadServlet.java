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

        String path = null;
        String name = null;
        String type = null;
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT storage_path, file_name, content_type FROM documents WHERE document_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    path = rs.getString(1);
                    name = rs.getString(2);
                    type = rs.getString(3);
                }
            }
        } catch (Exception e) {
            getServletContext().log("DocVault: download lookup failed", e);
            resp.sendError(503, "Document database unavailable");
            return;
        }

        if (path == null) {
            resp.sendError(404, "No such document");
            return;
        }
        Path file = Paths.get(path);
        if (!Files.isReadable(file)) {
            getServletContext().log("DocVault: record exists but file is missing: " + path);
            resp.sendError(404, "The document record exists but its file is missing");
            return;
        }
        resp.setContentType(type != null ? type : "application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + name.replace("\"", "") + "\"");
        resp.setContentLengthLong(Files.size(file));
        Files.copy(file, resp.getOutputStream());
    }
}
