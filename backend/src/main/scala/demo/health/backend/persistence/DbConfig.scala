package demo.health.backend.persistence

final case class DbConfig(host: String, port: Int, database: String, user: String, password: String)
