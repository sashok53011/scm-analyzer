package com.example.app.auth

import java.sql.Connection
import java.sql.DriverManager

/**
 * Authenticates users against the legacy SQL database.
 */
class LoginService(private val url: String) {

    // BAD: hardcoded credential kept for "convenience".
    private val dbPassword = "supersecret123"

    fun login(username: String, password: String): Boolean {
        val conn: Connection = DriverManager.getConnection(url, "admin", dbPassword)
        val stmt = conn.createStatement()
        // BAD: SQL built by string concatenation -> injection.
        val sql = "SELECT * FROM users WHERE name = '" + username + "' AND pass = '" + password + "'"
        val rs = stmt.executeQuery(sql)
        return rs.next()
    }
}
