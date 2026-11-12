import argparse
import logging
import os
import random
import sys
import time
import uuid
from datetime import datetime, timezone
from urllib.parse import urlsplit

import redis
from pyspark.sql import SparkSession


LOGGER = logging.getLogger("redis_batch_loader")
DEFAULT_REDIS_URL = "redis://redis:6379"
DEFAULT_BATCH_SIZE = 500
DEFAULT_BATCH_PAUSE_MS = 50
DEFAULT_TTL_SECONDS = 259200
DEFAULT_EMBEDDING_DIMENSION = 384
MAX_REDIS_ATTEMPTS = 3


def _positive_int(name, default):
    value = os.getenv(name, str(default))
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ValueError(f"{name} debe ser un entero positivo; valor recibido: {value!r}") from exc
    if parsed <= 0:
        raise ValueError(f"{name} debe ser un entero positivo; valor recibido: {value!r}")
    return parsed


def _nonnegative_int(name, default):
    value = os.getenv(name, str(default))
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ValueError(f"{name} debe ser un entero no negativo; valor recibido: {value!r}") from exc
    if parsed < 0:
        raise ValueError(f"{name} debe ser un entero no negativo; valor recibido: {value!r}")
    return parsed


def _jdbc_settings():
    db_host = os.getenv("POSTGRES_HOST", "postgres")
    db_port = os.getenv("POSTGRES_PORT", "5432")
    db_name = os.getenv("POSTGRES_DB")
    db_user = os.getenv("POSTGRES_USER")
    db_password = os.getenv("POSTGRES_PASSWORD")
    missing = [
        name
        for name, value in (
            ("POSTGRES_DB", db_name),
            ("POSTGRES_USER", db_user),
            ("POSTGRES_PASSWORD", db_password),
        )
        if not value
    ]
    if missing:
        raise ValueError(f"Faltan variables de entorno para PostgreSQL: {', '.join(missing)}")
    return (
        f"jdbc:postgresql://{db_host}:{db_port}/{db_name}",
        db_user,
        db_password,
    )


def _jdbc_connection(spark, jdbc_url, db_user, db_password):
    return spark._sc._gateway.jvm.java.sql.DriverManager.getConnection(
        jdbc_url, db_user, db_password
    )


def _update_batch(connection, batch_id, state, mentor_count=0, embedding_count=0, finished_at=None):
    statement = connection.createStatement()
    try:
        if state == "RUNNING":
            sql = (
                "UPDATE feature_batches SET estado = 'RUNNING', mentores_cargados = 0, "
                "embeddings_cargados = 0, finished_at = NULL "
                f"WHERE batch_id = '{batch_id}'::uuid"
            )
        else:
            finished_sql = (
                f"'{finished_at.isoformat()}'::timestamptz"
                if finished_at is not None
                else "NOW()"
            )
            sql = (
                f"UPDATE feature_batches SET estado = '{state}', "
                f"mentores_cargados = {mentor_count}, "
                f"embeddings_cargados = {embedding_count}, "
                f"finished_at = {finished_sql} "
                f"WHERE batch_id = '{batch_id}'::uuid"
            )
        changed = statement.executeUpdate(sql)
        if changed != 1:
            raise RuntimeError(f"No se pudo actualizar feature_batches para batch_id={batch_id}")
    finally:
        statement.close()


def _select_batch(connection, requested_batch_id):
    statement = connection.createStatement()
    try:
        if requested_batch_id:
            query = (
                "SELECT batch_id::text FROM feature_batches "
                f"WHERE batch_id = '{requested_batch_id}'::uuid"
            )
        else:
            query = (
                "SELECT batch_id::text FROM feature_batches "
                "WHERE estado = 'RUNNING' ORDER BY started_at DESC LIMIT 1"
            )
        result = statement.executeQuery(query)
        try:
            if not result.next():
                return None
            return str(result.getString(1))
        finally:
            result.close()
    finally:
        statement.close()


def _read_page(spark, jdbc_url, db_user, db_password, table, columns, id_column,
               batch_id, cursor_id, limit):
    cursor_filter = (
        f" AND {id_column} > '{cursor_id}'::uuid"
        if cursor_id is not None
        else ""
    )
    query = (
        f"(SELECT {columns} FROM {table} "
        f"WHERE batch_id = '{batch_id}'::uuid{cursor_filter} "
        f"ORDER BY {id_column} LIMIT {limit}) AS feature_page"
    )
    return (
        spark.read.format("jdbc")
        .option("url", jdbc_url)
        .option("driver", "org.postgresql.Driver")
        .option("dbtable", query)
        .option("user", db_user)
        .option("password", db_password)
        .option("fetchsize", str(limit))
        .load()
    )


def _jittered_ttl(ttl_seconds):
    return max(1, round(ttl_seconds * random.uniform(0.9, 1.1)))


def _execute_pipeline(client, queue_commands, ttl_seconds, label, transactional=False):
    for attempt in range(1, MAX_REDIS_ATTEMPTS + 1):
        pipeline = client.pipeline(transaction=transactional)
        try:
            queue_commands(pipeline, ttl_seconds)
            pipeline.execute()
            return
        except redis.exceptions.RedisError as exc:
            LOGGER.warning(
                "Lote %s: error Redis en intento %s/%s: %s",
                label,
                attempt,
                MAX_REDIS_ATTEMPTS,
                exc,
            )
            if attempt == MAX_REDIS_ATTEMPTS:
                raise
            delay_seconds = 2 ** (attempt - 1)
            LOGGER.info("Reintentando lote %s en %s s", label, delay_seconds)
            time.sleep(delay_seconds)
        finally:
            pipeline.reset()


def _redis_target(url):
    try:
        parsed = urlsplit(url)
        host = parsed.hostname
        if not parsed.scheme or not host:
            return "<REDIS_URL inválida>"
        formatted_host = f"[{host}]" if ":" in host else host
        port = f":{parsed.port}" if parsed.port is not None else ""
        return f"{parsed.scheme}://{formatted_host}{port}"
    except ValueError:
        return "<REDIS_URL inválida>"


def _write_mentor_rows(client, rows, ttl_seconds, label):
    def queue(pipeline, ttl):
        for row in rows:
            key = f"mentor:feat:{row['mentor_id']}"
            pipeline.hset(
                key,
                mapping={
                    "compatibility_score": str(float(row["compatibility_score"])),
                    "expertise_score": str(float(row["expertise_score"])),
                    "availability_score": str(float(row["availability_score"])),
                    "popularity_score": str(float(row["popularity_score"])),
                    "batch_id": str(row["batch_id"]),
                },
            )
            pipeline.expire(key, _jittered_ttl(ttl))

    _execute_pipeline(client, queue, ttl_seconds, label)


def _write_embedding_rows(client, rows, ttl_seconds, label):
    def queue(pipeline, ttl):
        for row in rows:
            service_id = row["service_id"]
            key = f"service:emb:{service_id}"
            meta_key = f"{key}:meta"
            pipeline.set(key, row["embedding_bytes"])
            pipeline.expire(key, _jittered_ttl(ttl))
            pipeline.hset(
                meta_key,
                mapping={
                    "dimension": str(row["dimension"]),
                    "batch_id": str(row["batch_id"]),
                },
            )
            pipeline.expire(meta_key, _jittered_ttl(ttl))

    _execute_pipeline(client, queue, ttl_seconds, label)


def _validated_embedding(row, expected_dimension):
    service_id = row["service_id"]
    try:
        dimension = int(row["dimension"])
        embedding_bytes = bytes(row["embedding"])
    except (TypeError, ValueError, OverflowError) as exc:
        LOGGER.warning("Embedding rechazado service_id=%s: datos inválidos (%s)", service_id, exc)
        return None
    if dimension != expected_dimension:
        LOGGER.warning(
            "Embedding rechazado service_id=%s: dimension=%s, esperada=%s",
            service_id,
            dimension,
            expected_dimension,
        )
        return None
    expected_bytes = dimension * 4
    if len(embedding_bytes) != expected_bytes:
        LOGGER.warning(
            "Embedding rechazado service_id=%s: bytes=%s, esperados=%s",
            service_id,
            len(embedding_bytes),
            expected_bytes,
        )
        return None
    return {
        "service_id": service_id,
        "embedding_bytes": embedding_bytes,
        "dimension": dimension,
        "batch_id": row["batch_id"],
    }


def _load_table(spark, jdbc_url, db_user, db_password, client, batch_id,
                table, columns, id_column, row_type, batch_size, ttl_seconds,
                embedding_dimension, pause_ms, start_lot, stats, count_key):
    cursor_id = None
    lot_number = start_lot
    page_number = 0

    while True:
        dataframe = _read_page(
            spark,
            jdbc_url,
            db_user,
            db_password,
            table,
            columns,
            id_column,
            batch_id,
            cursor_id,
            batch_size,
        )
        page = list(dataframe.toLocalIterator())
        if not page:
            break
        page_number += 1
        cursor_id = str(page[-1][id_column])
        rows = []

        if row_type == "mentor":
            rows = page
        else:
            for row in page:
                valid_row = _validated_embedding(row, embedding_dimension)
                if valid_row is None:
                    stats["rejected_embeddings"] += 1
                else:
                    rows.append(valid_row)

        if rows:
            lot_number += 1
            if row_type == "mentor":
                _write_mentor_rows(client, rows, ttl_seconds, lot_number)
            else:
                _write_embedding_rows(client, rows, ttl_seconds, lot_number)
            stats[count_key] += len(rows)
            LOGGER.info(
                "Avance %s: lote=%s, filas_leídas=%s, cargadas=%s, rechazadas=%s",
                table,
                lot_number,
                page_number * batch_size if len(page) == batch_size else (page_number - 1) * batch_size + len(page),
                stats[count_key],
                stats["rejected_embeddings"],
            )
        elif row_type == "embedding":
            LOGGER.info(
                "Avance %s: página=%s sin filas válidas; rechazadas acumuladas=%s",
                table,
                page_number,
                stats["rejected_embeddings"],
            )

        if len(page) < batch_size:
            break
        if pause_ms > 0:
            time.sleep(pause_ms / 1000.0)

    return lot_number


def _parse_args():
    parser = argparse.ArgumentParser(
        description="Carga a Redis las features del batch de PostgreSQL seleccionado."
    )
    parser.add_argument("--batch-id", help="UUID del batch a cargar; por omisión usa el RUNNING más reciente.")
    args = parser.parse_args()
    if args.batch_id:
        try:
            args.batch_id = str(uuid.UUID(args.batch_id))
        except ValueError as exc:
            parser.error(f"--batch-id debe ser un UUID válido: {exc}")
    return args


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    args = _parse_args()

    try:
        embedding_dimension = _positive_int("EMBEDDING_DIMENSION", DEFAULT_EMBEDDING_DIMENSION)
        batch_size = _positive_int("FEATURE_STORE_BATCH_SIZE", DEFAULT_BATCH_SIZE)
        pause_ms = _nonnegative_int("FEATURE_STORE_BATCH_PAUSE_MS", DEFAULT_BATCH_PAUSE_MS)
        ttl_seconds = _positive_int("FEATURE_STORE_TTL_SECONDS", DEFAULT_TTL_SECONDS)
        redis_url = os.getenv("REDIS_URL", DEFAULT_REDIS_URL)
        if not redis_url.strip():
            raise ValueError("REDIS_URL no puede estar vacío")
        jdbc_url, db_user, db_password = _jdbc_settings()
    except ValueError as exc:
        LOGGER.error("Configuración inválida: %s", exc)
        return 2

    spark = SparkSession.builder.appName("SkillBridge-RedisBatchLoader").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    connection = None
    redis_client = None
    batch_id = None
    started = time.monotonic()
    stats = {
        "mentors_loaded": 0,
        "embeddings_loaded": 0,
        "rejected_embeddings": 0,
    }

    try:
        connection = _jdbc_connection(spark, jdbc_url, db_user, db_password)
        batch_id = _select_batch(connection, args.batch_id)
        if batch_id is None:
            if args.batch_id:
                raise RuntimeError(f"No existe feature_batches para batch_id={args.batch_id}")
            raise RuntimeError(
                "No hay batches en estado RUNNING en feature_batches; no hay nada para cargar."
            )

        _update_batch(connection, batch_id, "RUNNING")
        redis_client = redis.Redis.from_url(redis_url, decode_responses=False)
        LOGGER.info(
            "Inicio batch_id=%s redis=%s batch_size=%s pausa_ms=%s ttl=%s dimension=%s",
            batch_id,
            _redis_target(redis_url),
            batch_size,
            pause_ms,
            ttl_seconds,
            embedding_dimension,
        )

        next_lot = _load_table(
            spark,
            jdbc_url,
            db_user,
            db_password,
            redis_client,
            batch_id,
            "mentor_features",
            "mentor_id, compatibility_score, expertise_score, availability_score, "
            "popularity_score, batch_id",
            "mentor_id",
            "mentor",
            batch_size,
            ttl_seconds,
            embedding_dimension,
            pause_ms,
            0,
            stats,
            "mentors_loaded",
        )
        _load_table(
            spark,
            jdbc_url,
            db_user,
            db_password,
            redis_client,
            batch_id,
            "service_embeddings",
            "service_id, embedding, dimension, batch_id",
            "service_id",
            "embedding",
            batch_size,
            ttl_seconds,
            embedding_dimension,
            pause_ms,
            next_lot,
            stats,
            "embeddings_loaded",
        )
        mentors_loaded = stats["mentors_loaded"]
        embeddings_loaded = stats["embeddings_loaded"]
        rejected_embeddings = stats["rejected_embeddings"]

        finished_at = datetime.now(timezone.utc)

        def queue_last_batch(pipeline, ttl):
            pipeline.hset(
                "featurestore:last_batch",
                mapping={
                    "batch_id": batch_id,
                    "finished_at": finished_at.isoformat(),
                },
            )
            pipeline.expire("featurestore:last_batch", _jittered_ttl(ttl))

        _execute_pipeline(
            redis_client,
            queue_last_batch,
            ttl_seconds,
            "featurestore:last_batch",
            transactional=True,
        )
        _update_batch(
            connection,
            batch_id,
            "COMPLETED",
            mentors_loaded,
            embeddings_loaded,
            finished_at,
        )
        LOGGER.info(
            "Batch completado batch_id=%s mentores_cargados=%s embeddings_cargados=%s "
            "embeddings_rechazados=%s duración_s=%.2f",
            batch_id,
            mentors_loaded,
            embeddings_loaded,
            rejected_embeddings,
            time.monotonic() - started,
        )
        return 0
    except Exception as exc:
        mentors_loaded = stats["mentors_loaded"]
        embeddings_loaded = stats["embeddings_loaded"]
        rejected_embeddings = stats["rejected_embeddings"]
        LOGGER.error(
            "Carga fallida%s: %s",
            f" batch_id={batch_id}" if batch_id else "",
            exc,
        )
        if connection is not None and batch_id is not None:
            try:
                _update_batch(
                    connection,
                    batch_id,
                    "FAILED",
                    mentors_loaded,
                    embeddings_loaded,
                )
                LOGGER.error("Batch %s marcado FAILED", batch_id)
            except Exception as update_exc:
                LOGGER.error("No se pudo marcar FAILED el batch %s: %s", batch_id, update_exc)
        LOGGER.error(
            "Totales parciales: mentores=%s embeddings=%s rechazados=%s duración_s=%.2f",
            mentors_loaded,
            embeddings_loaded,
            rejected_embeddings,
            time.monotonic() - started,
        )
        return 1
    finally:
        if redis_client is not None:
            redis_client.close()
        if connection is not None:
            connection.close()
        spark.stop()


if __name__ == "__main__":
    sys.exit(main())
