package com.docvault;

import java.sql.Connection;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

final class Db {
    private static volatile DataSource ds;

    private Db() {}

    static Connection get() throws NamingException, SQLException {
        if (ds == null) {
            ds = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/docvault");
        }
        return ds.getConnection();
    }
}
