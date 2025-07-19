"""Import allowlisted business records from the AFS CMS ZIP into a local H2 database."""

import argparse
import hashlib
import json
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
import re
import shutil
import subprocess
from tempfile import TemporaryDirectory
from zipfile import ZipFile

DB_MEMBER = "afs-cms/dev_db.mv.db"
CATEGORY_MEMBER = "afs-cms/sql/category.sql"
ZONE = timezone(timedelta(hours=8))
JAVA_ARGS = ("-Dh2.allowedClasses=",)
VALUE = re.compile(
    r"""\s*(?:(?P<prefix>U&|TIMESTAMP\s+)?'(?P<quoted>(?:[^']|'')*)'|"(?P<mysql>(?:[^"]|"")*)"|"""
    r"""(?P<scalar>NULL|[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?))\s*(?P<separator>,|$)""")
UNICODE = re.compile(r"\\(\\|\+[0-9a-fA-F]{6}|[0-9a-fA-F]{4})")

SCHEMA = """
CREATE TABLE IF NOT EXISTS archive_app (
    id BIGINT PRIMARY KEY, app_id VARCHAR(128), name VARCHAR(256), os VARCHAR(64), ratio DECIMAL(28,12), status INT
);
CREATE TABLE IF NOT EXISTS archive_ad (
    id BIGINT PRIMARY KEY, ad_id VARCHAR(128), app_id VARCHAR(128), name VARCHAR(256),
    ratio DECIMAL(28,12), ad_type INT, on_off INT, status INT
);
CREATE TABLE IF NOT EXISTS archive_ad_metric (
    id BIGINT PRIMARY KEY, ad_id VARCHAR(128), app_id VARCHAR(128), report_date DATE, day_time BIGINT,
    country VARCHAR(64), pv DECIMAL(28,12), total DECIMAL(28,12), ecpm DECIMAL(28,12), status INT
);
CREATE TABLE IF NOT EXISTS archive_user (
    id BIGINT PRIMARY KEY, name VARCHAR(256), root INT, status INT, created_at TIMESTAMP, updated_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS archive_user_access (
    id BIGINT PRIMARY KEY, user_name VARCHAR(256), app_id VARCHAR(128), access_level INT,
    created_at TIMESTAMP, updated_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS archive_category (
    site VARCHAR(128), code VARCHAR(256), label VARCHAR(256), PRIMARY KEY (site, code)
);
CREATE TABLE IF NOT EXISTS archive_category_child (
    site VARCHAR(128), parent_code VARCHAR(256), code VARCHAR(256), label VARCHAR(256),
    PRIMARY KEY (site, parent_code, code)
);
CREATE TABLE IF NOT EXISTS archive_api_app (
    id BIGINT PRIMARY KEY, app_id VARCHAR(128), name VARCHAR(256), os VARCHAR(64), ratio DECIMAL(28,12), status INT
);
CREATE TABLE IF NOT EXISTS archive_article (
    id VARCHAR(64) PRIMARY KEY, title VARCHAR(2048), category VARCHAR(512), lang VARCHAR(32),
    terms CLOB, first_part CLOB, body CLOB, online INT
);
SET AUTOCOMMIT FALSE;
"""


def parse_values(text, expected):
    """Accept only literal tuples, never expressions from the source archive."""
    values = []
    position = 0
    while position < len(text):
        match = VALUE.match(text, position)
        if not match:
            raise ValueError("Unsupported literal in archive")
        position = match.end()
        if match["quoted"] is not None:
            value = match["quoted"].replace("''", "'")
            if match["prefix"] == "U&":
                value = UNICODE.sub(lambda code: "\\" if code[1] == "\\" else chr(int(code[1].lstrip("+"), 16)), value)
        elif match["mysql"] is not None:
            value = match["mysql"].replace('""', '"')
        else:
            value = None if match["scalar"] == "NULL" else Decimal(match["scalar"])
        values.append(value)
        if not match["separator"]:
            break
    if position != len(text) or len(values) != expected:
        raise ValueError("Unexpected number of archive columns")
    return values


def restored_rows(sql, table, columns):
    mapping = re.search(r'^INSERT INTO "PUBLIC"\."' + table + r'"\s+SELECT \* FROM (O_\d+)\s*;', sql, re.M)
    if not mapping:
        raise ValueError("Missing table in snapshot: " + table)
    rows = re.findall(r"^INSERT INTO " + mapping[1] + r" VALUES\((.*)\);$", sql, re.M)
    return [parse_values(row, columns) for row in rows]


def seed_rows(sql, table, columns):
    pattern = r"^\s*INSERT INTO " + table + r"\s*\([^\r\n]*?\)\s*VALUES\s*\((.*)\);\s*$"
    return [parse_values(row, columns) for row in re.findall(pattern, sql, re.M | re.I)]


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, Decimal):
        return format(value, "f")
    return "'" + str(value).replace("'", "''") + "'"


def merge(script, table, keys, rows):
    for row in rows:
        script.write(f"MERGE INTO {table} KEY ({keys}) VALUES ({', '.join(map(literal, row))});\n")


def import_archive(archive, target, java, h2, api_archive=None):
    if ";" in target.as_posix():
        raise ValueError("The destination path cannot contain a semicolon")
    target.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix="afs-archive-db-") as directory:
        temporary = Path(directory)
        with ZipFile(archive) as bundle:
            with bundle.open(DB_MEMBER) as source, (temporary / "snapshot.mv.db").open("wb") as output:
                shutil.copyfileobj(source, output)
            seeds = bundle.read(CATEGORY_MEMBER).decode("utf-8-sig")
            articles = json.loads(bundle.read("afs-cms/src/main/resources/data/article-data.json"))
        common = [java, *JAVA_ARGS, "-cp", str(h2)]
        subprocess.run([*common, "org.h2.tools.Recover", "-dir", directory, "-db", "snapshot"], check=True)
        recovered = (temporary / "snapshot.h2.sql").read_text(encoding="utf-8-sig")

        apps = [(row[0], row[1], row[2], row[3], row[4], row[8]) for row in restored_rows(recovered, "APP_INFO", 11)]
        ads = [tuple(row[:8]) for row in restored_rows(recovered, "AD_INFO", 10)]
        users = [(row[0], row[1], *row[3:]) for row in restored_rows(recovered, "USER", 7)]
        access = [tuple(row) for row in restored_rows(recovered, "USER_ACCESS", 6)]
        metrics = []
        for row in restored_rows(recovered, "AD_DATA", 12):
            stamp = int(row[3])
            day = datetime.fromtimestamp(stamp / 1000, ZONE).date().isoformat()
            metrics.append((row[0], row[1], row[2], day, row[3], row[4].strip(),
                            row[5], row[6], row[7], row[9]))
        categories = [(site, code, label) for label, code, site in seed_rows(seeds, "category", 3)]
        children = [(site, parent, code, label) for parent, label, code, site in seed_rows(seeds, "category_child", 4)]
        article_rows = []
        for index, article in enumerate(articles):
            source = json.dumps(article, ensure_ascii=False, sort_keys=True)
            article_id = hashlib.sha256((str(index) + ":" + source).encode("utf-8")).hexdigest()
            body = article.get("textList")
            article_rows.append((article_id, article.get("title"), article.get("category"), article.get("lang"),
                                 article.get("terms"), article.get("firstP"),
                                 body if isinstance(body, str) else json.dumps(body, ensure_ascii=False),
                                 Decimal(article.get("online") or 0)))
        api_apps = []
        if api_archive:
            subprocess.run(["tar", "-xf", str(api_archive.resolve()), "-C", directory, "afs-api/dev_db.mv.db"], check=True)
            api_dir = temporary / "afs-api"
            subprocess.run([*common, "org.h2.tools.Recover", "-dir", str(api_dir), "-db", "dev_db"], check=True)
            api_rows = (api_dir / "dev_db.h2.sql").read_text(encoding="utf-8-sig")
            api_apps = [(row[0], row[1], row[2], row[3], row[4], row[8])
                        for row in restored_rows(api_rows, "APP_INFO", 11)]
        if not metrics or not categories:
            raise ValueError("The archive did not contain the expected ad records and categories")

        script = temporary / "allowlisted-import.sql"
        with script.open("w", encoding="utf-8") as output:
            output.write(SCHEMA)
            merge(output, "archive_app", "id", apps)
            merge(output, "archive_ad", "id", ads)
            merge(output, "archive_ad_metric", "id", metrics)
            merge(output, "archive_user", "id", users)
            merge(output, "archive_user_access", "id", access)
            merge(output, "archive_category", "site, code", categories)
            merge(output, "archive_category_child", "site, parent_code, code", children)
            merge(output, "archive_api_app", "id", api_apps)
            merge(output, "archive_article", "id", article_rows)
            output.write("COMMIT;\n")
        subprocess.run([*common, "org.h2.tools.RunScript", "-url",
                        f"jdbc:h2:file:{target.as_posix()};TRACE_LEVEL_FILE=0;WRITE_DELAY=0",
                        "-user", "sa", "-password", "", "-script", str(script), "-options", "CHARSET", "'UTF-8'"],
                       check=True)
    print(f"Imported {len(metrics)} ad records, {len(apps)} applications, {len(ads)} ad units, "
          f"{len(users)} user records, {len(access)} access records, "
          f"{len(categories)} categories and {len(children)} child categories.")
    print(f"Imported {len(article_rows)} original article records and {len(api_apps)} afs-api application records.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--api-archive", type=Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--h2", type=Path, default=Path.home() / ".m2/repository/com/h2database/h2/2.1.212/h2-2.1.212.jar")
    parser.add_argument("--target", type=Path, default=Path(__file__).resolve().parents[1] / "data/imported-business")
    args = parser.parse_args()
    if not args.h2.is_file():
        parser.error("H2 JAR missing; build afs-cms or pass --h2")
    import_archive(args.archive, args.target.resolve(), args.java, args.h2.resolve(), args.api_archive)


if __name__ == "__main__":
    main()

