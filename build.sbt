// =============================================================================================
// Health care demo: LangChain4j + LangGraph4j bằng Scala, tổ chức theo Hexagonal Architecture.
//
//                 ┌──────────────┐
//   frontend ───► │   backend    │  (adapter vào: REST API; adapter ra: Postgres; composition root)
//  (HTML + JS)    └──────┬───────┘
//                        │ dùng
//          ┌─────────────┼──────────────┐
//          ▼             ▼              ▼
//     langgraph      langchain        domain   ◄── lõi: model + port, không phụ thuộc thư viện nào
//   (điều phối)     (adapter AI)
//          └──────► domain ◄──────┘
//
// langgraph KHÔNG phụ thuộc langchain: graph chỉ gọi các port AI khai báo trong domain.
// =============================================================================================

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "demo.health"
ThisBuild / version      := "0.1.0"
ThisBuild / scalacOptions ++= Seq("-encoding", "utf8", "-deprecation", "-feature", "-Wunused:imports")
ThisBuild / javacOptions ++= Seq("-encoding", "UTF-8", "--release", "21")
ThisBuild / csrConfiguration := csrConfiguration.value.withParallelDownloads(2)

val lc4j     = "1.23.0"
val lc4jBeta = "1.23.0-beta33"
val lg4j     = "1.9.8"
val http4s   = "0.23.38"
val circe    = "0.14.15"

val munit = "org.scalameta" %% "munit" % "1.1.1" % Test

/** Lõi nghiệp vụ: entity, quy tắc (red flag, tương tác thuốc), port vào/ra. Không có thư viện ngoài. */
lazy val domain = project
  .settings(libraryDependencies += munit)

/** Adapter AI: cài đặt các port AI của domain bằng LangChain4j. */
lazy val langchain = project
  .dependsOn(domain)
  .settings(libraryDependencies ++= Seq(
    "dev.langchain4j" % "langchain4j"                               % lc4j,
    "dev.langchain4j" % "langchain4j-anthropic"                     % lc4j,
    "dev.langchain4j" % "langchain4j-ollama"                        % lc4j,
    "dev.langchain4j" % "langchain4j-open-ai"                       % lc4j,
    "dev.langchain4j" % "langchain4j-pgvector"                      % lc4jBeta,
    "dev.langchain4j" % "langchain4j-embeddings-all-minilm-l6-v2-q" % lc4jBeta,
    munit
  ),
  // Model embedding ONNX nạp thư viện native (DJL tokenizers): mỗi JVM chỉ nạp được một lần,
  // nên test của từng module chạy trong JVM riêng.
  Test / fork := true,
  Test / javaOptions ++= Seq("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8"))

/** Điều phối: cài đặt use case hội thoại bằng LangGraph4j, chỉ dựa trên port của domain. */
lazy val langgraph = project
  .dependsOn(domain)
  .settings(libraryDependencies ++= Seq(
    "org.bsc.langgraph4j" % "langgraph4j-core"           % lg4j,
    "org.bsc.langgraph4j" % "langgraph4j-postgres-saver" % lg4j,
    munit
  ))

/** Giao diện tĩnh (HTML + JS + Tailwind CDN). Không có code Scala; backend phục vụ các file này. */
lazy val frontend = project

/** Backend: REST API (http4s), adapter Postgres, lắp ráp toàn bộ ứng dụng. */
lazy val backend = project
  .dependsOn(domain, langchain % "compile->compile;test->test", langgraph, frontend)
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s"     %% "http4s-ember-server" % http4s,
      "org.http4s"     %% "http4s-dsl"          % http4s,
      "org.http4s"     %% "http4s-circe"        % http4s,
      "io.circe"       %% "circe-generic"       % circe,
      "io.circe"       %% "circe-parser"        % circe,
      "org.postgresql"  % "postgresql"          % "42.7.8",
      "ch.qos.logback"  % "logback-classic"     % "1.5.18",
      munit
    ),
    Compile / run / fork       := true,
    Test / fork                := true, // xem ghi chú ở module langchain
    Test / javaOptions ++= Seq("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8"),
    assembly / mainClass       := Some("demo.health.backend.Main"),
    assembly / assemblyJarName := "healthcare-demo.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", "versions", _*) => MergeStrategy.first
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case "module-info.class"                  => MergeStrategy.discard
      case x if x.endsWith(".properties") || x.endsWith(".proto") => MergeStrategy.first
      case x => (ThisBuild / assemblyMergeStrategy).value(x)
    }
  )

lazy val root = (project in file("."))
  .aggregate(domain, langchain, langgraph, frontend, backend)
  .settings(name := "healthcare-demo", publish / skip := true)
