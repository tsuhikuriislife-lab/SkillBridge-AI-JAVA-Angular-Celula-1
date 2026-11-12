import argparse
import os
import random
import struct
import sys
import uuid

import redis
from pyspark.sql import SparkSession


DEFAULT_REDIS_URL = "redis://redis:6379"
DEFAULT_BATCH_SIZE = 500
DEFAULT_EMBEDDING_DIMENSION = 384


def _positive_int(name, default):
    value = os.getenv(name, str(default))
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ValueError(f"{name} debe ser un entero positivo; valor recibido: {value!r}") from exc
    if parsed <= 0:
        raise ValueError(f"{name} debe ser un entero positivo; valor recibido: {value!r}")
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
    return f"jdbc:postgresql://{db_host}:{db_port}/{db_name}", db_user, db_password


def _jdbc_connection(spark, jdbc_url, db_user, db_password):
    return spark._sc._gateway.jvm.java.sql.DriverManager.getConnection(
        jdbc_url, db_user, db_password
    )


def _source_counts(connection):
    statement = connection.createStatement()
    try:
        users = statement.executeQuery("SELECT COUNT(*) FROM app_users")
        try:
            users.next()
            user_count = int(users.getLong(1))
        finally:
            users.close()
        providers = statement.executeQuery(
            "SELECT COUNT(*) FROM app_users WHERE role = 'PROVIDER'"
        )
        try:
            providers.next()
            provider_count = int(providers.getLong(1))
        finally:
            providers.close()
        services = statement.executeQuery("SELECT COUNT(*) FROM services")
        try:
            services.next()
            service_count = int(services.getLong(1))
        finally:
            services.close()
        return user_count, provider_count, service_count
    finally:
        statement.close()


def _flush(statement):
    statement.executeBatch()


def _insert_batch(connection, batch_id, batch_size, embedding_dimension, include_invalid):
    mentor_sql = (
        "INSERT INTO mentor_features "
        "(mentor_id, compatibility_score, expertise_score, availability_score, "
        "popularity_score, batch_id) "
        "VALUES (?::uuid, ?, ?, ?, ?, ?::uuid)"
    )
    embedding_sql = (
        "INSERT INTO service_embeddings (service_id, embedding, dimension, batch_id) "
        "VALUES (?::uuid, decode(?, 'hex'), ?, ?::uuid)"
    )
    create_batch = connection.createStatement()
    mentor_statement = connection.prepareStatement(mentor_sql)
    embedding_statement = connection.prepareStatement(embedding_sql)
    mentors_created = 0
    embeddings_created = 0
    random_source = random.Random()

    try:
        create_batch.executeUpdate(
            "INSERT INTO feature_batches (batch_id, estado) "
            f"VALUES ('{batch_id}'::uuid, 'RUNNING')"
        )
        connection.setAutoCommit(False)

        provider_query = connection.createStatement()
        provider_query.setFetchSize(batch_size)
        providers = provider_query.executeQuery(
            "SELECT id::text FROM app_users WHERE role = 'PROVIDER' ORDER BY id"
        )
        try:
            while providers.next():
                mentor_id = providers.getString(1)
                mentor_statement.setString(1, mentor_id)
                for parameter in range(2, 6):
                    mentor_statement.setDouble(parameter, random_source.random())
                mentor_statement.setString(6, batch_id)
                mentor_statement.addBatch()
                mentors_created += 1
                if mentors_created % batch_size == 0:
                    _flush(mentor_statement)
            if mentors_created % batch_size:
                _flush(mentor_statement)
        finally:
            providers.close()
            provider_query.close()

        service_query = connection.createStatement()
        service_query.setFetchSize(batch_size)
        services = service_query.executeQuery("SELECT id::text FROM services ORDER BY id")
        try:
            while services.next():
                service_id = services.getString(1)
                dimension = embedding_dimension
                if include_invalid and embeddings_created == 0:
                    dimension += 1
                values = [random_source.uniform(-1.0, 1.0) for _ in range(dimension)]
                embedding_hex = struct.pack(f"<{dimension}f", *values).hex()
                embedding_statement.setString(1, service_id)
                embedding_statement.setString(2, embedding_hex)
                embedding_statement.setInt(3, dimension)
                embedding_statement.setString(4, batch_id)
                embedding_statement.addBatch()
                embeddings_created += 1
                if embeddings_created % batch_size == 0:
                    _flush(embedding_statement)
            if embeddings_created % batch_size:
                _flush(embedding_statement)
        finally:
            services.close()
            service_query.close()

        if include_invalid and embeddings_created == 0:
            raise RuntimeError(
                "--include-invalid-dimension requiere al menos un registro en services."
            )
        connection.commit()
        return mentors_created, embeddings_created
    except Exception:
        connection.rollback()
        raise
    finally:
        embedding_statement.close()
        mentor_statement.close()
        create_batch.close()


def _insert_load_test_batch(connection, batch_id, count, batch_size, embedding_dimension):
    batch_hex = batch_id.replace("-", "")
    batch_statement = connection.createStatement()
    category_statement = connection.createStatement()
    user_statement = connection.prepareStatement(
        "INSERT INTO app_users (id, name, email, password, role, status) "
        "VALUES (?::uuid, ?, ?, ?, 'PROVIDER', 'INACTIVE')"
    )
    service_statement = connection.prepareStatement(
        "INSERT INTO services "
        "(id, name, category_id, price, detail, short_description, capacity, code, status, created_by) "
        "VALUES (?::uuid, ? , ?::uuid, 1, 'Synthetic load-test row', "
        "'Synthetic load-test row', 1, ?, 'INACTIVE', ?::uuid)"
    )
    mentor_statement = connection.prepareStatement(
        "INSERT INTO mentor_features "
        "(mentor_id, compatibility_score, expertise_score, availability_score, "
        "popularity_score, batch_id) VALUES (?::uuid, ?, ?, ?, ?, ?::uuid)"
    )
    embedding_statement = connection.prepareStatement(
        "INSERT INTO service_embeddings (service_id, embedding, dimension, batch_id) "
        "VALUES (?::uuid, decode(?, 'hex'), ?, ?::uuid)"
    )
    random_source = random.Random()
    inserted = 0

    try:
        batch_statement.executeUpdate(
            "INSERT INTO feature_batches (batch_id, estado) "
            f"VALUES ('{batch_id}'::uuid, 'RUNNING')"
        )
        category_result = category_statement.executeQuery(
            "SELECT category_id::text FROM services ORDER BY id LIMIT 1"
        )
        try:
            if not category_result.next():
                raise RuntimeError("No existe una categoría de servicio para el seed de carga.")
            category_id = category_result.getString(1)
        finally:
            category_result.close()

        connection.setAutoCommit(False)
        for index in range(count):
            user_id = str(uuid.uuid4())
            service_id = str(uuid.uuid4())
            marker = f"{batch_hex}-{index:08d}"

            user_statement.setString(1, user_id)
            user_statement.setString(2, f"loadtest-{marker}")
            user_statement.setString(3, f"loadtest-{marker}@users.invalid")
            user_statement.setString(4, "$2a$10$synthetic-load-test-password-hash-not-for-login")
            user_statement.addBatch()

            service_statement.setString(1, service_id)
            service_statement.setString(2, f"loadtest-{marker}")
            service_statement.setString(3, category_id)
            service_statement.setString(4, f"LT{batch_hex}-{index:08d}")
            service_statement.setString(5, user_id)
            service_statement.addBatch()

            mentor_statement.setString(1, user_id)
            for parameter in range(2, 6):
                mentor_statement.setDouble(parameter, random_source.random())
            mentor_statement.setString(6, batch_id)
            mentor_statement.addBatch()

            values = [random_source.uniform(-1.0, 1.0) for _ in range(embedding_dimension)]
            embedding_statement.setString(1, service_id)
            embedding_statement.setString(
                2, struct.pack(f"<{embedding_dimension}f", *values).hex()
            )
            embedding_statement.setInt(3, embedding_dimension)
            embedding_statement.setString(4, batch_id)
            embedding_statement.addBatch()
            inserted += 1

            if inserted % batch_size == 0:
                user_statement.executeBatch()
                service_statement.executeBatch()
                mentor_statement.executeBatch()
                embedding_statement.executeBatch()

        if inserted % batch_size:
            user_statement.executeBatch()
            service_statement.executeBatch()
            mentor_statement.executeBatch()
            embedding_statement.executeBatch()
        connection.commit()
        return inserted, inserted
    except Exception:
        connection.rollback()
        raise
    finally:
        embedding_statement.close()
        mentor_statement.close()
        service_statement.close()
        user_statement.close()
        category_statement.close()
        batch_statement.close()


def _redis_delete_for_batch(client, connection, batch_id, batch_size):
    deleted_keys = 0
    mentor_query = connection.createStatement()
    try:
        mentors = mentor_query.executeQuery(
            "SELECT mentor_id::text FROM mentor_features "
            f"WHERE batch_id = '{batch_id}'::uuid ORDER BY mentor_id"
        )
        while mentors.next():
            mentor_ids = [mentors.getString(1)]
            while len(mentor_ids) < batch_size and mentors.next():
                mentor_ids.append(mentors.getString(1))
            pipeline = client.pipeline(transaction=False)
            for mentor_id in mentor_ids:
                pipeline.hget(f"mentor:feat:{mentor_id}", "batch_id")
            values = pipeline.execute()
            keys_to_delete = [
                f"mentor:feat:{mentor_id}"
                for mentor_id, value in zip(mentor_ids, values)
                if value == batch_id.encode("utf-8")
            ]
            if keys_to_delete:
                deleted_keys += client.delete(*keys_to_delete)
        mentors.close()
    finally:
        mentor_query.close()

    service_query = connection.createStatement()
    try:
        services = service_query.executeQuery(
            "SELECT service_id::text FROM service_embeddings "
            f"WHERE batch_id = '{batch_id}'::uuid ORDER BY service_id"
        )
        while services.next():
            service_ids = [services.getString(1)]
            while len(service_ids) < batch_size and services.next():
                service_ids.append(services.getString(1))
            pipeline = client.pipeline(transaction=False)
            for service_id in service_ids:
                pipeline.hget(f"service:emb:{service_id}:meta", "batch_id")
            values = pipeline.execute()
            keys_to_delete = []
            for service_id, value in zip(service_ids, values):
                if value == batch_id.encode("utf-8"):
                    keys_to_delete.extend(
                        [f"service:emb:{service_id}", f"service:emb:{service_id}:meta"]
                    )
            if keys_to_delete:
                deleted_keys += client.delete(*keys_to_delete)
        services.close()
    finally:
        service_query.close()

    if client.hget("featurestore:last_batch", "batch_id") == batch_id.encode("utf-8"):
        deleted_keys += client.delete("featurestore:last_batch")
    return deleted_keys


def _cleanup_batch(connection, client, batch_id, batch_size):
    deleted_keys = _redis_delete_for_batch(client, connection, batch_id, batch_size)
    statement = connection.createStatement()
    try:
        batch_hex = batch_id.replace("-", "")
        user_marker = f"loadtest-{batch_hex}-%@users.invalid"
        service_marker = f"LT{batch_hex}-%"
        mentors = statement.executeUpdate(
            "DELETE FROM mentor_features "
            "WHERE batch_id = "
            f"'{batch_id}'::uuid OR mentor_id IN "
            f"(SELECT id FROM app_users WHERE email LIKE '{user_marker}')"
        )
        embeddings = statement.executeUpdate(
            "DELETE FROM service_embeddings "
            "WHERE batch_id = "
            f"'{batch_id}'::uuid OR service_id IN "
            f"(SELECT id FROM services WHERE code LIKE '{service_marker}')"
        )
        schedules = statement.executeUpdate(
            "DELETE FROM service_schedules "
            f"WHERE service_id IN (SELECT id FROM services WHERE code LIKE '{service_marker}')"
        )
        enrollments = statement.executeUpdate(
            "DELETE FROM user_services "
            f"WHERE service_id IN (SELECT id FROM services WHERE code LIKE '{service_marker}') "
            f"OR user_id IN (SELECT id FROM app_users WHERE email LIKE '{user_marker}')"
        )
        services = statement.executeUpdate(
            "DELETE FROM services "
            f"WHERE code LIKE '{service_marker}'"
        )
        users = statement.executeUpdate(
            "DELETE FROM app_users "
            f"WHERE email LIKE '{user_marker}'"
        )
        batches = statement.executeUpdate(
            "DELETE FROM feature_batches "
            f"WHERE batch_id = '{batch_id}'::uuid"
        )
        return mentors, embeddings, batches, deleted_keys, users, services, schedules, enrollments
    finally:
        statement.close()


def _parse_args():
    parser = argparse.ArgumentParser(
        description="Genera y limpia datos sintéticos para probar el Feature Store."
    )
    parser.add_argument("--batch-id", help="UUID específico; obligatorio junto con --cleanup.")
    parser.add_argument(
        "--include-invalid-dimension",
        action="store_true",
        help="Genera el primer embedding con dimensión EMBEDDING_DIMENSION + 1.",
    )
    parser.add_argument(
        "--cleanup",
        action="store_true",
        help="Borra los registros y claves Redis pertenecientes al batch indicado.",
    )
    parser.add_argument(
        "--load-test",
        type=int,
        metavar="N",
        help="Crea N mentores y N servicios sintéticos INACTIVE para una prueba de carga.",
    )
    args = parser.parse_args()
    if args.batch_id:
        try:
            args.batch_id = str(uuid.UUID(args.batch_id))
        except ValueError as exc:
            parser.error(f"--batch-id debe ser un UUID válido: {exc}")
    if args.cleanup and not args.batch_id:
        parser.error("--cleanup requiere --batch-id para limitar la limpieza a un batch.")
    if args.cleanup and args.include_invalid_dimension:
        parser.error("--include-invalid-dimension no se combina con --cleanup.")
    if args.load_test is not None and args.load_test <= 0:
        parser.error("--load-test debe ser un entero mayor que cero.")
    if args.load_test is not None and (args.cleanup or args.include_invalid_dimension):
        parser.error("--load-test no se combina con --cleanup ni --include-invalid-dimension.")
    return args


def main():
    args = _parse_args()
    try:
        embedding_dimension = _positive_int("EMBEDDING_DIMENSION", DEFAULT_EMBEDDING_DIMENSION)
        batch_size = _positive_int("FEATURE_STORE_BATCH_SIZE", DEFAULT_BATCH_SIZE)
        redis_url = os.getenv("REDIS_URL", DEFAULT_REDIS_URL)
        if not redis_url.strip():
            raise ValueError("REDIS_URL no puede estar vacío")
        jdbc_url, db_user, db_password = _jdbc_settings()
    except ValueError as exc:
        print(f"[ERROR] Configuración inválida: {exc}", file=sys.stderr)
        return 2

    spark = SparkSession.builder.appName("SkillBridge-SeedFeatureData").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    read_connection = None
    write_connection = None
    redis_client = None
    try:
        read_connection = _jdbc_connection(spark, jdbc_url, db_user, db_password)
        write_connection = _jdbc_connection(spark, jdbc_url, db_user, db_password)
        user_count, provider_count, service_count = _source_counts(read_connection)
        print(
            f"IDs existentes: app_users={user_count}, "
            f"mentores(PROVIDER)={provider_count}, services={service_count}"
        )

        if args.cleanup:
            redis_client = redis.Redis.from_url(redis_url, decode_responses=False)
            redis_client.ping()
            write_connection.setAutoCommit(False)
            mentors, embeddings, batches, keys, users, services, schedules, enrollments = _cleanup_batch(
                write_connection, redis_client, args.batch_id, batch_size
            )
            write_connection.commit()
            print(
                f"Limpieza batch_id={args.batch_id}: mentores={mentors}, "
                f"embeddings={embeddings}, batches={batches}, claves_redis={keys}, "
                f"usuarios_loadtest={users}, servicios_loadtest={services}, "
                f"horarios={schedules}, inscripciones={enrollments}"
            )
            return 0

        batch_id = args.batch_id or str(uuid.uuid4())
        if args.load_test is not None:
            print(
                f"Load-test seed iniciado batch_id={batch_id} N={args.load_test}; "
                f"cleanup: --cleanup --batch-id {batch_id}",
                flush=True,
            )
            write_connection.setAutoCommit(False)
            mentors_created, embeddings_created = _insert_load_test_batch(
                write_connection,
                batch_id,
                args.load_test,
                batch_size,
                embedding_dimension,
            )
            print(
                f"Load-test seed creado batch_id={batch_id} estado=RUNNING "
                f"mentores={mentors_created} embeddings={embeddings_created}"
            )
            return 0

        write_connection.setAutoCommit(False)
        mentors_created, embeddings_created = _insert_batch(
            write_connection,
            batch_id,
            batch_size,
            embedding_dimension,
            args.include_invalid_dimension,
        )
        print(
            f"Seed creado batch_id={batch_id} estado=RUNNING "
            f"mentores={mentors_created} embeddings={embeddings_created}"
            + (
                f" dimensión_incorrecta={embedding_dimension + 1}"
                if args.include_invalid_dimension
                else ""
            )
        )
        print(
            "Para limpiar los datos y claves de este batch: "
            f"spark-submit /app/jobs/seed_feature_data.py --cleanup --batch-id {batch_id}"
        )
        return 0
    except Exception as exc:
        print(f"[ERROR] No se pudo procesar el seed: {exc}", file=sys.stderr)
        return 1
    finally:
        if redis_client is not None:
            redis_client.close()
        if write_connection is not None:
            write_connection.close()
        if read_connection is not None:
            read_connection.close()
        spark.stop()


if __name__ == "__main__":
    sys.exit(main())
