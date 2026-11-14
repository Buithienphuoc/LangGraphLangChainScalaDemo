package demo.health.backend.persistence

import java.sql.Connection
import javax.sql.DataSource
import org.postgresql.ds.PGSimpleDataSource
import scala.util.Using

object Db:
  def dataSource(c: DbConfig): DataSource =
    val ds = PGSimpleDataSource()
    ds.setServerNames(Array(c.host))
    ds.setPortNumbers(Array(c.port))
    ds.setDatabaseName(c.database)
    ds.setUser(c.user)
    ds.setPassword(c.password)
    ds

  def withConnection[A](ds: DataSource)(f: Connection => A): A =
    Using.resource(ds.getConnection)(f)

  /** Chạy schema.sql (idempotent). */
  def migrate(ds: DataSource): Unit =
    val sql = scala.io.Source.fromResource("schema.sql", getClass.getClassLoader).mkString
    withConnection(ds)(c => Using.resource(c.createStatement())(_.execute(sql)))
