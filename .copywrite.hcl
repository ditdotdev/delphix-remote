schema_version = 1

project {
  license          = "BUSL-1.1"
  copyright_holder = "Dit"
  copyright_year   = 2026

  # Gradle wrapper files are Gradle's own (Apache-2.0); build artifacts and
  # coverage output must not carry license headers either.
  header_ignore = [
    "gradlew",
    "gradlew.bat",
    "gradle/**",
    "build/**",
    "**/build/**",
    ".health/**",
    "**/*.out",
    # Vendored Delphix SDK: Copyright (c) 2019 by Delphix, Apache-2.0.
    # These files keep their original headers (see licenses/Apache-2.0.txt).
    "engine/src/main/kotlin/com/delphix/**",
    "engine/src/test/kotlin/com/delphix/**",
    "licenses/**",
  ]
}
