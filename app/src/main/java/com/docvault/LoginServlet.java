package com.docvault;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Sign in - DocVault</title>
            <link rel="stylesheet" href="static/style.css">
            </head>
            <body>
            <header class="top">
              <div class="wrap top-in">
                <a class="brand" href="login">
                  <span class="mark" aria-hidden="true"><i>0</i><i>1</i></span>
                  <span class="word">DocVault<b class="cur">_</b></span>
                </a>
              </div>
            </header>
            <main class="wrap">
            {{toast}}
            <section class="card" aria-labelledby="in-h">
              <h2 id="in-h">Sign in</h2>
              <form class="upload" method="post" action="login">
                <div>
                  <label class="lbl" for="u">Username</label>
                  <input class="txt" id="u" name="username" maxlength="50" autocomplete="username" required autofocus>
                </div>
                <div>
                  <label class="lbl" for="p">Password</label>
                  <input class="txt" id="p" name="password" type="password" maxlength="200" autocomplete="current-password" required>
                </div>
                <button class="btn primary" type="submit">Sign in</button>
              </form>
            </section>
            </main>
            </body>
            </html>
            """;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (Auth.current(req) != null) {
            resp.sendRedirect(req.getContextPath() + "/documents");
            return;
        }
        String toast = "";
        if ("1".equals(req.getParameter("out"))) {
            toast = "<div class=\"toast\" role=\"status\">You have been signed out.</div>";
        }
        write(resp, 200, toast);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        if (username == null || password == null || username.isBlank() || password.isEmpty()) {
            write(resp, 400, errorToast("Enter a username and a password."));
            return;
        }

        Auth.User user = null;
        boolean ok = false;
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id, username, role, password_hash FROM users WHERE username = ?")) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ok = Auth.verify(password, rs.getString(4));
                    user = new Auth.User(rs.getInt(1), rs.getString(2), rs.getString(3));
                } else {
                    Auth.verify(password, Auth.DUMMY_HASH); // same work whether or not the user exists
                }
            }
        } catch (Exception e) {
            Log.log("DocVault: sign-in lookup failed", e);
            write(resp, 503, errorToast("Sign-in is unavailable right now. The database can't be reached."));
            return;
        }

        if (!ok) {
            Log.log("DocVault: failed sign-in for user '" + username.trim() + "' from "
                    + req.getRemoteAddr());
            write(resp, 401, errorToast("Wrong username or password."));
            return;
        }

        // A fresh session after sign-in, so a session id set earlier cannot be reused (session fixation).
        HttpSession old = req.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = req.getSession(true);
        session.setMaxInactiveInterval(30 * 60);
        session.setAttribute(Auth.SESSION_KEY, user);
        Log.log("DocVault: " + user.username() + " (" + user.role() + ") signed in");
        resp.sendRedirect(req.getContextPath() + "/documents");
    }

    private static String errorToast(String message) {
        return "<div class=\"toast err\" role=\"alert\">" + message + "</div>";
    }

    private static void write(HttpServletResponse resp, int status, String toast) throws IOException {
        resp.setStatus(status);
        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().print(PAGE.replace("{{toast}}", toast));
    }
}
