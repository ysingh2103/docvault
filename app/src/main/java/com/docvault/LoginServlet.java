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
import java.sql.SQLException;
import java.util.Locale;

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

    /** Wrong passwords allowed for one user from one address, inside the window, before sign-in is refused. */
    private static final int MAX_FAILURES = 5;
    private static final int WINDOW_MINUTES = 10;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        if (username == null || password == null || username.isBlank() || password.isEmpty()
                || username.trim().length() > 50) {
            write(resp, 400, errorToast("Enter a username and a password."));
            return;
        }
        String name = username.trim();
        String key = name.toLowerCase(Locale.ROOT);   // the counter ignores capital letters
        String ip = ClientIp.of(req);
        String shown = logSafe(name);

        Auth.User user = null;
        boolean ok = false;
        try (Connection c = Db.get()) {
            // 1. Locked? Look at the 5th most recent failure inside the window: if there is one, the pair
            //    is locked until that failure ages out. The database clock is used for every time check.
            int wait = lockedSeconds(c, key, ip);
            if (wait > 0) {
                Log.log("DocVault: sign-in refused, locked: user '" + shown + "' from " + ip
                        + " (" + wait + " s left)");
                resp.setHeader("Retry-After", String.valueOf(wait));
                write(resp, 429, errorToast("Too many failed sign-in attempts. Try again in "
                        + ((wait + 59) / 60) + " minute(s)."));
                return;
            }

            // 2. Check the password, as before.
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT user_id, username, role, password_hash FROM users WHERE username = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ok = Auth.verify(password, rs.getString(4));
                        user = new Auth.User(rs.getInt(1), rs.getString(2), rs.getString(3));
                    } else {
                        Auth.verify(password, Auth.DUMMY_HASH); // same work whether or not the user exists
                    }
                }
            }

            // 3. Remember a failure, or forget the earlier ones after a success.
            if (ok) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM login_attempts WHERE username = ? AND client_ip = ?")) {
                    ps.setString(1, key);
                    ps.setString(2, ip);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO login_attempts (username, client_ip) VALUES (?, ?)")) {
                    ps.setString(1, key);
                    ps.setString(2, ip);
                    ps.executeUpdate();
                }
                // Keep the table small: nothing older than a day matters.
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM login_attempts WHERE attempted_at < now() - interval '1 day'")) {
                    ps.executeUpdate();
                }
                if (lockedSeconds(c, key, ip) > 0) {
                    Log.log("DocVault: user '" + shown + "' from " + ip + " locked after "
                            + MAX_FAILURES + " failed sign-ins");
                }
            }
        } catch (Exception e) {
            Log.log("DocVault: sign-in lookup failed", e);
            write(resp, 503, errorToast("Sign-in is unavailable right now. The database can't be reached."));
            return;
        }

        if (!ok) {
            Log.log("DocVault: failed sign-in for user '" + shown + "' from " + ip);
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
        Log.log("DocVault: " + user.username() + " (" + user.role() + ") signed in from " + ip);
        resp.sendRedirect(req.getContextPath() + "/documents");
    }

    /** Seconds until this user+address may try again, or 0 when it is not locked. */
    private static int lockedSeconds(Connection c, String key, String ip) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT GREATEST(1, CEIL(EXTRACT(EPOCH FROM (attempted_at + interval '" + WINDOW_MINUTES
                        + " minutes' - now()))))::int "
              + "FROM login_attempts "
              + "WHERE username = ? AND client_ip = ? "
              + "AND attempted_at > now() - interval '" + WINDOW_MINUTES + " minutes' "
              + "ORDER BY attempted_at DESC LIMIT 1 OFFSET " + (MAX_FAILURES - 1))) {
            ps.setString(1, key);
            ps.setString(2, ip);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** A username goes into the log, so strip anything that could fake a new log line. */
    private static String logSafe(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            b.append(ch < 32 || ch == 127 ? '_' : ch);
        }
        return b.toString();
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
