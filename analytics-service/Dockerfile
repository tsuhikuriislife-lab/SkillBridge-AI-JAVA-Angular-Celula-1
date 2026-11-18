FROM apache/spark:3.5.1-python3

USER root

# Descargar el driver JDBC de PostgreSQL compatible y colocarlo en los jars de Spark
ADD --chown=spark:spark https://jdbc.postgresql.org/download/postgresql-42.7.3.jar /opt/spark/jars/postgresql-42.7.3.jar

# Instalar dependencias adicionales de Python si se necesitan
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# tiktoken descarga la codificación la primera vez que se usa.
# La dejamos en la imagen para que el chunker y los tests funcionen sin internet.
ENV TIKTOKEN_CACHE_DIR=/opt/tiktoken-cache
RUN python3 -c "import tiktoken; tiktoken.get_encoding('cl100k_base')" \
    && chown -R spark:spark /opt/tiktoken-cache

# Configuración de pytest (los tests se montan como volumen en docker-compose)
COPY pytest.ini .

# Copiar la carpeta donde residirán los scripts de trabajo
COPY jobs/ /app/jobs/

USER spark
